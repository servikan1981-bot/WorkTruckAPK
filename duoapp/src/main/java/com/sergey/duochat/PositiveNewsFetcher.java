package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Every phone displays the same server-selected list of positive news. */
public final class PositiveNewsFetcher {
    private static final String ITEMS = "positive_news_shared_v610";
    private static final String LAST = "positive_news_last_sync_v610";
    private static final long INTERVAL_MS = 15L * 60L * 1000L;

    private PositiveNewsFetcher() {}

    public static String load(Context context) {
        return SecureStore.prefs(context).getString(ITEMS, "[]");
    }

    public static synchronized void checkAndStore(Context context) {
        SharedPreferences p = SecureStore.prefs(context);
        long now = System.currentTimeMillis();
        if (now - p.getLong(LAST, 0L) < INTERVAL_MS) return;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SecureStore.relay(context) + "/news/auto").openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) return;
            StringBuilder body = new StringBuilder();
            try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 50_000) return;
                }
            }
            JSONArray entries = new JSONArray(body.toString());
            if (entries.length() > 20) return;
            p.edit().putString(ITEMS, entries.toString()).putLong(LAST, now).apply();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
