package com.sergey.duochat;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        captureCallAction(getIntent());

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
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
        });

        requestPermissionsIfNeeded();
        maybeStartMessagingService();
        loadApp();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureCallAction(intent);
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                    "window.__ourFamilyConsumeNativeAction && window.__ourFamilyConsumeNativeAction();", null));
        }
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
                String relay = p.getString("relay_base", "https://ntfy.sh");
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
            try {
                String enc = SecureStore.prefs(MainActivity.this).getString("history", "");
                return enc.isEmpty() ? "[]" : SecureStore.decrypt(enc);
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveHistory(String json) {
            try {
                if (json == null || json.length() > 2000000) return;
                SecureStore.prefs(MainActivity.this).edit()
                        .putString("history", SecureStore.encrypt(json))
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

            runOnUiThread(() -> startMessagingService(true));
        }

        @JavascriptInterface
        public String consumePendingAction() {
            SharedPreferences p = SecureStore.prefs(MainActivity.this);
            String value = p.getString("pending_call_action", "");
            if (!value.isEmpty()) p.edit().remove("pending_call_action").apply();
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
        public String getVersion() {
            return "4.2";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
