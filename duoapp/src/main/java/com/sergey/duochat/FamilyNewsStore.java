package com.sergey.duochat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Stores only encrypted family news; the decryption key stays in the WebView. */
public final class FamilyNewsStore {
    private static final String CACHE = "family_news_encrypted_v610";

    private FamilyNewsStore() {}

    public static String load(Context context) {
        return SecureStore.prefs(context).getString(CACHE, "{\"ready\":false,\"events\":[]}");
    }

    public static String publish(Context context, String id, String ciphertext) {
        String code = SecureStore.familyCode(context);
        if (code.isEmpty() || id == null || !id.matches("[a-f0-9]{32}") ||
                ciphertext == null || !ciphertext.matches("[A-Za-z0-9+/=]{32,7500}")) return "ERR:news";
        return NativeRelayTransport.postBlocking(context, FamilyDirectory.newsTopic(code),
                "of6news|" + id + "|" + ciphertext, 3);
    }

    public static void sync(Context context) {
        String code = SecureStore.familyCode(context);
        if (code.isEmpty()) return;
        HttpURLConnection c = null;
        try {
            URL url = new URL(SecureStore.relay(context) + "/" + FamilyDirectory.newsTopic(code) + "/json?since=90d");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/x-ndjson");
            if (c.getResponseCode() != 200) return;
            JSONArray news = new JSONArray();
            int bytes = 0;
            try (BufferedReader input = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    bytes += line.length();
                    if (bytes > 3_000_000) return;
                    JSONObject event = new JSONObject(line);
                    String wire = event.optString("message", "");
                    if (!wire.startsWith("of6news|")) continue;
                    if (news.length() < 300) news.put(event);
                }
            }
            JSONObject cache = new JSONObject();
            cache.put("ready", true);
            cache.put("events", news);
            SecureStore.prefs(context).edit().putString(CACHE, cache.toString()).apply();
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
