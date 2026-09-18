package com.sergey.duochat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
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
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int PERMISSION_REQUEST = 2001;
    private static final String PREFS = "duo_native_v3";
    private static final String KEY_ALIAS = "OurChatV3ProfileKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getString("topic", "").isEmpty()) startMessagingService(false);
    }

    private void startMessagingService(boolean restart) {
        Intent i = new Intent(this, MessagingService.class);
        if (restart) i.setAction(MessagingService.ACTION_RESTART);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();

        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return kg.generateKey();
    }

    private String encryptLocal(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    private String decryptLocal(String encoded) throws Exception {
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
        if (all.length < 29) throw new IllegalArgumentException("ciphertext");
        byte[] iv = new byte[12];
        byte[] ct = new byte[all.length - 12];
        System.arraycopy(all, 0, iv, 0, 12);
        System.arraycopy(all, 12, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String loadProfile() {
            try {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                String role = p.getString("role", "");
                String enc = p.getString("pair_secret", "");
                String relay = p.getString("relay_base", "https://ntfy.sh");
                if (role.isEmpty() || enc.isEmpty()) return "";
                JSONObject o = new JSONObject();
                o.put("role", role);
                o.put("code", decryptLocal(enc));
                o.put("relay", relay);
                o.put("turnUrl", p.getString("turn_url", ""));
                String tu = p.getString("turn_user", "");
                String tp = p.getString("turn_pass", "");
                o.put("turnUser", tu.isEmpty() ? "" : decryptLocal(tu));
                o.put("turnPass", tp.isEmpty() ? "" : decryptLocal(tp));
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveProfile(String role, String code, String relayBase, String turnUrl, String turnUser, String turnPass) {
            try {
                if (!("sergey".equals(role) || "wife".equals(role))) return false;
                if (code == null || code.length() < 14) return false;
                if (relayBase == null || !relayBase.startsWith("https://")) return false;
                SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("role", role)
                        .putString("pair_secret", encryptLocal(code))
                        .putString("relay_base", relayBase.replaceAll("/+$", ""))
                        .putString("turn_url", turnUrl == null ? "" : turnUrl.trim());
                if (turnUser != null && !turnUser.isEmpty()) ed.putString("turn_user", encryptLocal(turnUser)); else ed.remove("turn_user");
                if (turnPass != null && !turnPass.isEmpty()) ed.putString("turn_pass", encryptLocal(turnPass)); else ed.remove("turn_pass");
                ed.apply();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String loadHistory() {
            try {
                String enc = getSharedPreferences(PREFS, MODE_PRIVATE).getString("history", "");
                return enc.isEmpty() ? "[]" : decryptLocal(enc);
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveHistory(String json) {
            try {
                if (json == null || json.length() > 1000000) return;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("history", encryptLocal(json))
                        .apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void clearProfile() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        }

        @JavascriptInterface
        public void configure(String topic, String role, String relayBase, String senderTag) {
            if (topic == null || role == null || relayBase == null || senderTag == null) return;
            if (!topic.matches("[a-zA-Z0-9_-]{12,100}")) return;
            if (!senderTag.matches("[a-f0-9]{8,32}")) return;
            if (!relayBase.startsWith("https://")) return;

            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("topic", topic)
                    .putString("role", role)
                    .putString("relay_base", relayBase.replaceAll("/+$", ""))
                    .putString("sender_tag", senderTag)
                    .apply();

            runOnUiThread(() -> startMessagingService(true));
        }

        @JavascriptInterface
        public String deviceId() {
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return id == null ? "" : id;
        }

        @JavascriptInterface
        public String getVersion() {
            return "3.0";
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
