package com.sergey.duochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Base64InputStream;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class UpdateManager {
    public static final String UPDATE_MANIFEST =
            "https://raw.githubusercontent.com/servikan1981-bot/WorkTruckAPK/family-app-updates/updates/latest.json";
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String KEY_LAST_CHECK = "stable_update_last_check";
    private static final String KEY_PENDING = "stable_update_pending";

    private static volatile boolean checking = false;

    private UpdateManager() {}

    public static void onResume(Activity activity) {
        maybeContinuePending(activity);
        check(activity, false);
    }

    public static void check(Activity activity, boolean userInitiated) {
        if (checking || activity == null || activity.isFinishing()) return;

        long now = System.currentTimeMillis();
        long last = SecureStore.prefs(activity).getLong(KEY_LAST_CHECK, 0L);
        if (!userInitiated && last > 0 && now - last < CHECK_INTERVAL_MS) return;

        checking = true;
        new Thread(() -> {
            UpdateInfo info = null;
            String error = "";
            try {
                info = fetchInfo();
                SecureStore.prefs(activity).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                error = e.getMessage() == null ? "Ошибка сети" : e.getMessage();
            }

            final UpdateInfo result = info;
            final String err = error;
            checking = false;

            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (result == null) {
                    if (userInitiated) Toast.makeText(activity,
                            err.isEmpty() ? "Не удалось проверить обновления" : "Не удалось проверить обновления: " + err,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                long current = currentVersionCode(activity);
                if (result.versionCode <= current) {
                    if (userInitiated) Toast.makeText(activity,
                            "Установлена последняя версия " + currentVersionName(activity),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                showUpdateDialog(activity, result);
            });
        }, "OurFamilyUpdateCheck").start();
    }

    private static UpdateInfo fetchInfo() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(
                UPDATE_MANIFEST + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Cache-Control", "no-cache");
        try (InputStream in = c.getInputStream()) {
            byte[] data = readAll(in, 128 * 1024);
            JSONObject o = new JSONObject(new String(data, "UTF-8"));
            UpdateInfo i = new UpdateInfo();
            i.versionCode = o.getLong("versionCode");
            i.versionName = o.optString("versionName", "");
            i.notes = o.optString("notes", "");
            i.apkBase64Url = o.getString("apkBase64Url");
            i.sha256 = o.getString("sha256").toLowerCase(Locale.US).replace(":", "").trim();
            return i;
        } finally {
            c.disconnect();
        }
    }

    private static void showUpdateDialog(Activity activity, UpdateInfo info) {
        StringBuilder message = new StringBuilder();
        message.append("Доступна версия ").append(info.versionName).append(".");
        if (!info.notes.isEmpty()) message.append("\n\n").append(info.notes);

        new AlertDialog.Builder(activity)
                .setTitle("Обновление «Наша семья»")
                .setMessage(message.toString())
                .setPositiveButton("Обновить", (d, which) -> beginUpdate(activity, info))
                .setNegativeButton("Позже", null)
                .show();
    }

    private static void beginUpdate(Activity activity, UpdateInfo info) {
        try {
            SecureStore.prefs(activity).edit().putString(KEY_PENDING, info.toJson().toString()).apply();
        } catch (Exception ignored) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                Toast.makeText(activity,
                        "Разрешите установку обновлений для «Наша семья», затем вернитесь в приложение.",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(activity,
                        "Не удалось открыть разрешение установки приложений.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        downloadAndInstall(activity, info);
    }

    private static void maybeContinuePending(Activity activity) {
        String raw = SecureStore.prefs(activity).getString(KEY_PENDING, "");
        if (raw.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.getPackageManager().canRequestPackageInstalls()) return;
        try {
            UpdateInfo i = UpdateInfo.fromJson(new JSONObject(raw));
            if (i.versionCode > currentVersionCode(activity)) downloadAndInstall(activity, i);
            else SecureStore.prefs(activity).edit().remove(KEY_PENDING).apply();
        } catch (Exception e) {
            SecureStore.prefs(activity).edit().remove(KEY_PENDING).apply();
        }
    }

    private static void downloadAndInstall(Activity activity, UpdateInfo info) {
        Toast.makeText(activity, "Скачиваю обновление…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Не удалось создать папку обновления");
                File apk = new File(dir, "OurFamily_update.apk");

                HttpURLConnection c = (HttpURLConnection) new URL(
                        info.apkBase64Url + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("Cache-Control", "no-cache");

                try (InputStream raw = new BufferedInputStream(c.getInputStream());
                     Base64InputStream decoded = new Base64InputStream(raw, Base64.DEFAULT);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(apk))) {
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    long total = 0;
                    while ((n = decoded.read(buf)) > 0) {
                        total += n;
                        if (total > 100L * 1024L * 1024L) throw new Exception("Файл обновления слишком большой");
                        out.write(buf, 0, n);
                    }
                } finally {
                    c.disconnect();
                }

                String actual = sha256(apk);
                if (!actual.equalsIgnoreCase(info.sha256)) {
                    apk.delete();
                    throw new Exception("Контрольная сумма обновления не совпала");
                }

                install(activity, apk);
            } catch (Exception e) {
                SecureStore.prefs(activity).edit().remove(KEY_PENDING).apply();
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Обновление не установлено: " +
                                (e.getMessage() == null ? "ошибка загрузки" : e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }, "OurFamilyUpdateDownload").start();
    }

    private static void install(Activity activity, File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= 21) params.setAppPackageName(activity.getPackageName());

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        try (InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            session.fsync(out);
        }

        Intent status = new Intent(activity, UpdateInstallReceiver.class);
        status.setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS);
        PendingIntent pi = PendingIntent.getBroadcast(
                activity,
                sessionId,
                status,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));

        session.commit(pi.getIntentSender());
        session.close();
    }

    public static long currentVersionCode(Activity a) {
        try {
            PackageInfo p = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentVersionName(Activity a) {
        try {
            PackageInfo p = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return p.versionName == null ? "" : p.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte b : d.digest()) s.append(String.format(Locale.US, "%02x", b & 0xff));
        return s.toString();
    }

    private static byte[] readAll(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) throw new Exception("Слишком большой ответ сервера");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static final class UpdateInfo {
        long versionCode;
        String versionName = "";
        String notes = "";
        String apkBase64Url = "";
        String sha256 = "";

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("versionCode", versionCode);
            o.put("versionName", versionName);
            o.put("notes", notes);
            o.put("apkBase64Url", apkBase64Url);
            o.put("sha256", sha256);
            return o;
        }

        static UpdateInfo fromJson(JSONObject o) throws Exception {
            UpdateInfo i = new UpdateInfo();
            i.versionCode = o.getLong("versionCode");
            i.versionName = o.optString("versionName", "");
            i.notes = o.optString("notes", "");
            i.apkBase64Url = o.getString("apkBase64Url");
            i.sha256 = o.getString("sha256");
            return i;
        }
    }
}
