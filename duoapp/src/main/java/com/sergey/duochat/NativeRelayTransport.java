package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NativeRelayTransport {
    private NativeRelayTransport() {}

    public static String post(Context context, String topic, String message, int priority) {
        if (context == null) return "ERR:context";
        if (topic == null || !topic.matches("[a-zA-Z0-9_-]{12,100}")) return "ERR:topic";
        if (message == null || message.isEmpty() || message.length() > 8000) return "ERR:message";
        priority = Math.max(1, Math.min(5, priority));

        SharedPreferences prefs = SecureStore.prefs(context);
        String relay = prefs.getString("relay_base", "https://ntfy.sh");
        if (relay == null || !relay.startsWith("https://")) relay = "https://ntfy.sh";
        relay = relay.replaceAll("/+$", "");

        String last = "ERR:network";
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection c = null;
            try {
                JSONObject body = new JSONObject();
                body.put("topic", topic);
                body.put("message", message);
                body.put("priority", priority);

                c = (HttpURLConnection) new URL(relay + "/").openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setUseCaches(false);
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("User-Agent", "OurFamily/6.0.4 Android");

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                    out.flush();
                }

                int code = c.getResponseCode();
                if (code >= 200 && code < 300) return "OK";
                last = "ERR:http:" + code;
            } catch (Exception e) {
                last = "ERR:" + e.getClass().getSimpleName();
            } finally {
                try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
            }

            try { Thread.sleep(350L * (attempt + 1)); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return last;
    }

    public static String postBlocking(Context context, String topic, String message, int priority) {
        final String[] result = new String[] {"ERR:timeout"};
        Thread t = new Thread(() -> result[0] = post(context, topic, message, priority), "OurFamilyRelayPost");
        t.start();
        try {
            t.join(42000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERR:interrupted";
        }
        if (t.isAlive()) {
            t.interrupt();
            return "ERR:timeout";
        }
        return result[0];
    }
}
