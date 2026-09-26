package com.sergey.duochat;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int PERMISSION_REQUEST = 2001;
    private static final int FILE_CHOOSER_REQUEST = 2002;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;
    private boolean telecomPromptShownThisRun = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyCallWindowFlags(getIntent());
        captureCallAction(getIntent());
        captureMessageNavigation(getIntent());

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (hasMediaPermissions()) request.grant(request.getResources());
                    else {
                        request.deny();
                        requestPermissionsIfNeeded();
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallbackNew,
                    FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePathCallbackNew;
                try {
                    if (fileChooserParams.isCaptureEnabled()) {
                        StringBuilder joinedBuilder = new StringBuilder();
                        for (String type : fileChooserParams.getAcceptTypes()) {
                            if (type != null) joinedBuilder.append(type).append(",");
                        }
                        String joined = joinedBuilder.toString().toLowerCase();
                        boolean video = joined.contains("video");
                        Intent intent = new Intent(video ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "OurFamily_" + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
                        values.put(MediaStore.MediaColumns.MIME_TYPE, video ? "video/mp4" : "image/jpeg");
                        cameraOutputUri = getContentResolver().insert(
                                video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values);
                        if (cameraOutputUri != null) {
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        if (video) intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 12);
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                        return true;
                    }
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    cameraOutputUri = null;
                    return false;
                }
            }
        });

        requestPermissionsIfNeeded();
        TelecomCallManager.register(this);
        maybeStartMessagingService();
        loadApp();
        UpdateManager.checkAsync(this, getIntent() != null && getIntent().getBooleanExtra("force_update_check", false));
        webView.postDelayed(this::maybePromptTelecomSetup, 900);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyCallWindowFlags(intent);
        captureCallAction(intent);
        captureMessageNavigation(intent);
        if (intent != null && intent.getBooleanExtra("force_update_check", false)) {
            UpdateManager.checkAsync(this, true);
        }
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                    "window.__ourFamilyConsumeNativeAction && window.__ourFamilyConsumeNativeAction();", null));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                if ((result == null || result.length == 0) && resultCode == RESULT_OK && cameraOutputUri != null) {
                    result = new Uri[] { cameraOutputUri };
                }
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
                cameraOutputUri = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void applyCallWindowFlags(Intent intent) {
        if (intent == null || intent.getStringExtra("call_action") == null) return;
        try {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            }
        } catch (Exception ignored) {}
    }

    private void captureCallAction(Intent intent) {
        if (intent == null) return;
        String actionName = intent.getStringExtra("call_action");
        String callId = intent.getStringExtra("call_id");
        String callerRole = intent.getStringExtra("caller_role");
        String kind = intent.getStringExtra("call_kind");
        if (actionName == null || callId == null || callerRole == null) return;

        try {
            JSONObject o = new JSONObject();
            o.put("action", actionName);
            o.put("callId", callId);
            o.put("callerRole", callerRole);
            o.put("kind", kind == null ? "video" : kind);
            SecureStore.prefs(this).edit()
                    .putString("pending_call_action", o.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void captureMessageNavigation(Intent intent) {
        if (intent == null) return;
        String messageId = intent.getStringExtra("open_message_id");
        String senderRole = intent.getStringExtra("open_sender_role");
        String messageKind = intent.getStringExtra("open_message_kind");
        if (messageId == null || messageId.isEmpty()) return;

        try {
            JSONObject o = new JSONObject();
            o.put("messageId", messageId);
            o.put("senderRole", senderRole == null ? "" : senderRole);
            o.put("kind", messageKind == null ? "" : messageKind);
            o.put("accept", "checkers".equals(messageKind) && intent.getBooleanExtra("open_game_accept", false));
            SecureStore.prefs(this).edit()
                    .putString("pending_message_navigation", o.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void maybePromptTelecomSetup() {
        if (Build.VERSION.SDK_INT < 23 || telecomPromptShownThisRun || isFinishing()) return;
        if (TelecomCallManager.isEnabled(this)) return;
        telecomPromptShownThisRun = true;

        new android.app.AlertDialog.Builder(this)
                .setTitle("Разрешите системные входящие звонки")
                .setMessage("Чтобы принимать звонки «Наша семья» прямо на заблокированном экране, один раз включите аккаунт «Наша семья» в системных настройках звонков.")
                .setPositiveButton("Включить", (d, w) -> TelecomCallManager.openSettings(this))
                .setNegativeButton("Позже", null)
                .show();
    }

    private void loadApp() {
        try (InputStream in = getAssets().open("index.html")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String html = new String(out.toByteArray(), StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL("https://app.local/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            webView.loadData("<h2>Не удалось запустить приложение</h2>", "text/html", "UTF-8");
        }
    }

    private boolean hasMediaPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!list.isEmpty()) requestPermissions(list.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private void maybeStartMessagingService() {
        SharedPreferences prefs = SecureStore.prefs(this);
        if (!prefs.getString("topic", "").isEmpty()) startMessagingService(false);
    }

    private void startMessagingService(boolean restart) {
        Intent i = new Intent(this, MessagingService.class);
        if (restart) i.setAction(MessagingService.ACTION_RESTART);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String loadProfile() {
            try {
                SharedPreferences p = SecureStore.prefs(MainActivity.this);
                String role = p.getString("role", "");
                String enc = p.getString("family_secret", "");
                String relay = SecureStore.relay(MainActivity.this);
                if (role.isEmpty() || enc.isEmpty()) return "";

                JSONObject o = new JSONObject();
                o.put("role", role);
                o.put("code", SecureStore.decrypt(enc));
                o.put("relay", relay);
                o.put("turnUrl", p.getString("turn_url", ""));
                String tu = p.getString("turn_user", "");
                String tp = p.getString("turn_pass", "");
                o.put("turnUser", tu.isEmpty() ? "" : SecureStore.decrypt(tu));
                o.put("turnPass", tp.isEmpty() ? "" : SecureStore.decrypt(tp));
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveProfile(String role, String code, String relayBase, String turnUrl, String turnUser, String turnPass) {
            try {
                if (!FamilyDirectory.validRole(role)) return false;
                if (code == null || code.length() < 14) return false;
                if (relayBase == null || !relayBase.startsWith("https://")) return false;
                if (!relayBase.replaceAll("/+$", "").equals(SecureStore.relay(MainActivity.this)))
                    PresenceStore.clearLive(MainActivity.this);

                SharedPreferences.Editor ed = SecureStore.prefs(MainActivity.this).edit()
                        .putString("role", role)
                        .putString("family_secret", SecureStore.encrypt(code))
                        .putString("relay_base", relayBase.replaceAll("/+$", ""))
                        .putString("turn_url", turnUrl == null ? "" : turnUrl.trim());

                if (turnUser != null && !turnUser.isEmpty()) ed.putString("turn_user", SecureStore.encrypt(turnUser)); else ed.remove("turn_user");
                if (turnPass != null && !turnPass.isEmpty()) ed.putString("turn_pass", SecureStore.encrypt(turnPass)); else ed.remove("turn_pass");
                ed.apply();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String loadHistory() {
            SharedPreferences p = SecureStore.prefs(MainActivity.this);
            String[] keys = {"history_v51", "history", "history_v51_backup"};
            for (String key : keys) {
                try {
                    String enc = p.getString(key, "");
                    if (!enc.isEmpty()) {
                        String value = SecureStore.decrypt(enc);
                        if (value.startsWith("[")) return value;
                    }
                } catch (Exception ignored) {}
            }
            return "[]";
        }

        @JavascriptInterface
        public boolean saveHistory(String json) {
            try {
                if (json == null || json.length() > 2500000) return false;
                SharedPreferences p = SecureStore.prefs(MainActivity.this);
                String previous = p.getString("history_v51", "");
                String encrypted = SecureStore.encrypt(json);
                SharedPreferences.Editor ed = p.edit().putString("history_v51", encrypted);
                if (!previous.isEmpty()) ed.putString("history_v51_backup", previous);
                ed.apply();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        @JavascriptInterface
        public String loadUiSettings() {
            try {
                String enc = SecureStore.prefs(MainActivity.this).getString("ui_settings_v51", "");
                return enc.isEmpty() ? "{}" : SecureStore.decrypt(enc);
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void saveUiSettings(String json) {
            try {
                if (json == null || json.length() > 20000) return;
                SecureStore.prefs(MainActivity.this).edit()
                        .putString("ui_settings_v51", SecureStore.encrypt(json))
                        .apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void clearProfile() {
            SecureStore.prefs(MainActivity.this).edit().clear().apply();
        }

        @JavascriptInterface
        public void configure(String topic, String role, String relayBase, String senderTag) {
            if (topic == null || role == null || relayBase == null || senderTag == null) return;
            if (!topic.matches("[a-zA-Z0-9_-]{12,100}")) return;
            if (!FamilyDirectory.validRole(role)) return;
            if (!senderTag.matches("[a-f0-9]{8,32}")) return;
            if (!relayBase.startsWith("https://")) return;

            SecureStore.prefs(MainActivity.this).edit()
                    .putString("topic", topic)
                    .putString("role", role)
                    .putString("relay_base", relayBase.replaceAll("/+$", ""))
                    .putString("sender_tag", senderTag)
                    .apply();

            runOnUiThread(() -> {
                startMessagingService(true);
                if (MessagingService.isAppVisible()) sendVisibility(true);
            });
        }

        @JavascriptInterface
        public void logCallMetric(String phase) {
            if (phase != null && phase.matches("invite_start|accepted|offer_sent|answer_sent|video_track|connected|remote_frame")) {
                android.util.Log.i("OurFamilyCall", phase + " " + System.currentTimeMillis());
            }
        }

        @JavascriptInterface
        public String sendRelay(String topic, String message, int priority) {
            return NativeRelayTransport.postBlocking(MainActivity.this, topic, message, priority);
        }

        @JavascriptInterface
        public String probeRelay(String relayBase, String topic) {
            final String[] result = {"ERR:timeout"};
            Thread thread = new Thread(() -> result[0] = NativeRelayTransport.probe(
                    MainActivity.this, relayBase, topic), "OurFamilyRelayProbe");
            thread.start();
            try { thread.join(16000L); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERR:interrupted";
            }
            if (thread.isAlive()) { thread.interrupt(); return "ERR:timeout"; }
            return result[0];
        }

        @JavascriptInterface
        public String uploadAttachment(String base64) {
            return NativeAttachmentTransport.uploadBase64(MainActivity.this, base64);
        }

        @JavascriptInterface
        public String downloadAttachment(String url) {
            return NativeAttachmentTransport.downloadBase64(MainActivity.this, url);
        }

        @JavascriptInterface
        public String readRelayInbox() {
            return RelayInbox.read(MainActivity.this);
        }

        @JavascriptInterface
        public void ackRelayInbox(String idsJson) {
            RelayInbox.ack(MainActivity.this, idsJson);
        }

        @JavascriptInterface
        public String consumePendingAction() {
            SharedPreferences p = SecureStore.prefs(MainActivity.this);
            String value = p.getString("pending_call_action", "");
            if (!value.isEmpty()) p.edit().remove("pending_call_action").apply();
            return value;
        }

        @JavascriptInterface
        public String consumePendingNavigation() {
            SharedPreferences p = SecureStore.prefs(MainActivity.this);
            String value = p.getString("pending_message_navigation", "");
            if (!value.isEmpty()) p.edit().remove("pending_message_navigation").apply();
            return value;
        }

        @JavascriptInterface
        public boolean canUseFullScreenCall() {
            if (Build.VERSION.SDK_INT < 34) return true;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        }

        @JavascriptInterface
        public void requestFullScreenCallPermission() {
            if (Build.VERSION.SDK_INT < 34) return;
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String deviceId() {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return id == null ? "" : id;
        }

        @JavascriptInterface
        public boolean setSpeakerphone(boolean enabled) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am == null) return false;
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                if (Build.VERSION.SDK_INT >= 31) {
                    AudioDeviceInfo target = null;
                    for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                        if (enabled && d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { target = d; break; }
                        if (!enabled && d.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) { target = d; break; }
                    }
                    if (target != null) return am.setCommunicationDevice(target);
                    if (!enabled) { am.clearCommunicationDevice(); return true; }
                    return false;
                } else {
                    am.setSpeakerphoneOn(enabled);
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean isSpeakerphoneOn() {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am == null) return false;
                if (Build.VERSION.SDK_INT >= 31) {
                    AudioDeviceInfo d = am.getCommunicationDevice();
                    return d != null && d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
                }
                return am.isSpeakerphoneOn();
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void resetAudioRoute() {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am == null) return;
                if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
                else am.setSpeakerphoneOn(false);
                am.setMode(AudioManager.MODE_NORMAL);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String loadPresence() {
            return PresenceStore.readLive(MainActivity.this);
        }

        @JavascriptInterface
        public String loadAutoNews() {
            return PositiveNewsFetcher.load(MainActivity.this);
        }

        @JavascriptInterface
        public String loadDailyQuote() {
            return DailyQuoteFetcher.load(MainActivity.this);
        }

        @JavascriptInterface
        public void refreshDailyQuote() {
            new Thread(() -> {
                DailyQuoteFetcher.checkAndStore(MainActivity.this);
                if (webView != null) webView.post(() -> webView.evaluateJavascript(
                        "window.__ourFamilyDailyQuoteUpdated && window.__ourFamilyDailyQuoteUpdated();", null));
            }, "OurFamilyDailyQuote").start();
        }

        @JavascriptInterface
        public String loadFamilyNews() {
            return FamilyNewsStore.load(MainActivity.this);
        }

        @JavascriptInterface
        public String publishFamilyNews(String id, String ciphertext) {
            return FamilyNewsStore.publish(MainActivity.this, id, ciphertext);
        }

        @JavascriptInterface
        public void refreshFamilyNews() {
            new Thread(() -> {
                FamilyNewsStore.sync(MainActivity.this);
                PositiveNewsFetcher.checkAndStore(MainActivity.this);
            }, "OurFamilyNewsRefresh").start();
        }

        @JavascriptInterface
        public void checkForUpdates() {
            runOnUiThread(() -> UpdateManager.checkAsync(MainActivity.this, true));
        }

        @JavascriptInterface
        public boolean isSystemCallingEnabled() {
            return TelecomCallManager.isEnabled(MainActivity.this);
        }

        @JavascriptInterface
        public void openSystemCallingSettings() {
            runOnUiThread(() -> TelecomCallManager.openSettings(MainActivity.this));
        }

        @JavascriptInterface
        public String getVersion() {
            return "6.0.16";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(window.__ourFamilyHandleBack && window.__ourFamilyHandleBack()) ? 'handled' : 'pass';",
                value -> runOnUiThread(() -> {
                    if (!"\"handled\"".equals(value)) {
                        if (webView.canGoBack()) webView.goBack();
                        else MainActivity.super.onBackPressed();
                    }
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        MessagingService.setAppVisible(true);
        sendVisibility(true);
        TelecomCallManager.register(this);
        UpdateManager.resumePendingInstall(this);
        UpdateManager.checkAsync(this, false);
    }

    @Override
    protected void onPause() {
        MessagingService.setAppVisible(false);
        sendVisibility(false);
        super.onPause();
    }

    private void sendVisibility(boolean visible) {
        if (SecureStore.familyCode(this).isEmpty()) return;
        try {
            Intent i = new Intent(this, MessagingService.class);
            i.setAction(MessagingService.ACTION_PRESENCE_CHANGE);
            i.putExtra("visible", visible);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (RuntimeException ignored) {}
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
