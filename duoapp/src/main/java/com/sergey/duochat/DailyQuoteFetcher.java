package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Reads the one quote selected and cached by the family relay for each Moscow day. */
public final class DailyQuoteFetcher {
    private static final String ITEM = "daily_quote_shared_v615";
    private static final String LAST = "daily_quote_last_try_v615";
    private static final long RETRY_MS = 60_000L;

    private DailyQuoteFetcher() {}

    public static String load(Context context) {
        return SecureStore.prefs(context).getString(ITEM, "{}");
    }

    public static synchronized void checkAndStore(Context context) {
        SharedPreferences prefs = SecureStore.prefs(context);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        String today = format.format(new Date());
        try {
            if (today.equals(new JSONObject(load(context)).optString("date"))) return;
        } catch (Exception ignored) {}

        long now = System.currentTimeMillis();
        if (now - prefs.getLong(LAST, 0L) < RETRY_MS) return;
        prefs.edit().putLong(LAST, now).apply();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SecureStore.relay(context) + "/quote/today").openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) return;
            StringBuilder body = new StringBuilder();
            try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 4000) return;
                }
            }
            JSONObject quote = new JSONObject(body.toString());
            if (!today.equals(quote.optString("date"))) return;
            String text = quote.optString("text").trim();
            String author = quote.optString("author").trim();
            if (text.length() < 20 || text.length() > 350 || author.length() < 2 || author.length() > 90) return;
            prefs.edit().putString(ITEM, quote.toString()).apply();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
