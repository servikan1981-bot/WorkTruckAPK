package com.sergey.duochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateManager {
    private static final String META_URL =
            "https://raw.githubusercontent.com/servikan1981-bot/WorkTruckAPK/family-stable-6/updates/latest.json";
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String PREF_LAST_CHECK = "update_last_check_v6";
    private static final String PREF_PENDING_INSTALL = "update_pending_install_v6";
    private static final String PREF_NOTIFIED_VERSION = "update_notified_version_v6";
    private static final String CH_UPDATES = "family_updates_v6";
    private static final int UPDATE_NOTIFICATION_ID = 8601;
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean DIALOG_OPEN = new AtomicBoolean(false);

    private UpdateManager() {}

    public static void checkAsync(Activity activity, boolean force) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences p = SecureStore.prefs(activity);
        long now = System.currentTimeMillis();
        if (!force && now - p.getLong(PREF_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return;
        if (!CHECKING.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                p.edit().putLong(PREF_LAST_CHECK, now).apply();
                UpdateInfo info = fetchInfo();
                if (info == null || info.versionCode <= currentVersionCode(activity)) return;
                activity.runOnUiThread(() -> showUpdateDialog(activity, info));
            } catch (Exception ignored) {
            } finally {
                CHECKING.set(false);
            }
        }, "OurFamilyUpdateCheck").start();
    }

    public static void checkBackground(Context context) {
        if (context == null) return;
        try {
            UpdateInfo info = fetchInfo();
            long current = currentVersionCode(context);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (info == null || info.versionCode <= current) {
                if (nm != null) nm.cancel(UPDATE_NOTIFICATION_ID);
                return;
            }

            SharedPreferences p = SecureStore.prefs(context);
            long already = p.getLong(PREF_NOTIFIED_VERSION, 0L);
            if (already == info.versionCode) return;

            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        CH_UPDATES, "Обновления приложения", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Новые версии «Наша семья»");
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }

            Intent open = new Intent(context, MainActivity.class);
            open.putExtra("force_update_check", true);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context, UPDATE_NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CH_UPDATES)
                    : new Notification.Builder(context);
            Notification n = b.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("🔔 Доступно обновление " + info.versionName)
                    .setContentText("Нажмите, чтобы обновить «Наша семья»")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            if (nm != null) nm.notify(UPDATE_NOTIFICATION_ID, n);
            p.edit().putLong(PREF_NOTIFIED_VERSION, info.versionCode).apply();
        } catch (Exception ignored) {}
    }

    public static void resumePendingInstall(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences p = SecureStore.prefs(activity);
        if (!p.getBoolean(PREF_PENDING_INSTALL, false)) return;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) return;
        p.edit().remove(PREF_PENDING_INSTALL).apply();
        installDownloadedApk(activity);
    }

    private static UpdateInfo fetchInfo() throws Exception {
        HttpURLConnection c = open(META_URL);
        String text;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) out.append(line);
            text = out.toString();
        } finally {
            c.disconnect();
        }

        JSONObject o = new JSONObject(text);
        UpdateInfo i = new UpdateInfo();
        i.versionCode = o.optLong("versionCode", 0L);
        i.versionName = o.optString("versionName", "");
        JSONArray parts = o.optJSONArray("apkBase64Parts");
        if (parts != null) {
            for (int n = 0; n < parts.length(); n++) {
                String url = parts.optString(n, "");
                if (url.startsWith("https://")) i.apkBase64Parts.add(url);
            }
        }
        i.sha256 = o.optString("sha256", "").toLowerCase(Locale.US);
        i.notes = o.optString("notes", "");
        if (i.versionCode <= 0 || i.apkBase64Parts.isEmpty() || i.sha256.length() != 64) return null;
        return i;
    }

    private static void showUpdateDialog(Activity activity, UpdateInfo info) {
        if (activity.isFinishing() || !DIALOG_OPEN.compareAndSet(false, true)) return;
        String message = "Доступно обновление " + info.versionName + ".";
        if (!info.notes.isEmpty()) message += "\n\n" + info.notes;

        AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle("Обновление «Наша семья»")
                .setMessage(message)
                .setPositiveButton("Обновить", (dialog, which) -> downloadAndInstall(activity, info))
                .setNegativeButton("Позже", null)
                .create();
        d.setOnDismissListener(x -> DIALOG_OPEN.set(false));
        d.show();
    }

    private static void downloadAndInstall(Activity activity, UpdateInfo info) {
        Toast.makeText(activity, "Загружаю обновление…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] apk = downloadBase64Apk(info.apkBase64Parts);
                if (!sha256(apk).equalsIgnoreCase(info.sha256)) {
                    throw new IllegalStateException("SHA-256 mismatch");
                }

                File dir = new File(activity.getFilesDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create update dir");
                File out = new File(dir, "OurFamily_update.apk");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(apk);
                    fos.flush();
                }

                if (!validateApk(activity, out, info.versionCode)) {
                    throw new IllegalStateException("Downloaded APK validation failed");
                }

                activity.runOnUiThread(() -> requestInstallOrOpen(activity));
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Не удалось загрузить обновление", Toast.LENGTH_LONG).show());
            }
        }, "OurFamilyUpdateDownload").start();
    }

    private static byte[] downloadBase64Apk(java.util.List<String> urls) throws Exception {
        StringBuilder b64 = new StringBuilder();
        for (String url : urls) {
            HttpURLConnection c = open(url);
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b64.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
                    if (b64.length() > 5_000_000) throw new IllegalStateException("Update payload too large");
                }
            } finally {
                c.disconnect();
            }
        }
        String clean = b64.toString().replace("\r", "").replace("\n", "").trim();
        return Base64.decode(clean, Base64.DEFAULT);
    }

    private static boolean validateApk(Activity activity, File apk, long expectedVersionCode) {
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= 33) {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            } else {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            }
            if (pi == null || !activity.getPackageName().equals(pi.packageName)) return false;
            long vc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            return vc == expectedVersionCode && vc > currentVersionCode(activity);
        } catch (Exception e) {
            return false;
        }
    }

    private static void requestInstallOrOpen(Activity activity) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            SecureStore.prefs(activity).edit().putBoolean(PREF_PENDING_INSTALL, true).apply();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
            } catch (Exception e) {
                Toast.makeText(activity, "Разрешите установку обновлений для этого приложения", Toast.LENGTH_LONG).show();
            }
            return;
        }
        installDownloadedApk(activity);
    }

    private static void installDownloadedApk(Activity activity) {
        try {
            Uri uri = Uri.parse("content://" + activity.getPackageName() + ".updateprovider/update.apk");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(activity, "Не удалось открыть установщик Android", Toast.LENGTH_LONG).show();
        }
    }

    private static long currentVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        byte[] b = d.digest(data);
        StringBuilder out = new StringBuilder();
        for (byte x : b) out.append(String.format(Locale.US, "%02x", x & 0xff));
        return out.toString();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("Pragma", "no-cache");
        c.setRequestProperty("User-Agent", "OurFamily/6.0.2 Android");
        c.setRequestProperty("Accept", "application/json,text/plain,*/*");
        c.connect();
        if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) {
            throw new IllegalStateException("HTTP " + c.getResponseCode());
        }
        return c;
    }

    private static final class UpdateInfo {
        long versionCode;
        String versionName;
        java.util.ArrayList<String> apkBase64Parts = new java.util.ArrayList<>();
        String sha256;
        String notes;
    }
}
