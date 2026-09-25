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
        String first = postAttempt(context, topic, message, priority);
        if ("OK".equals(first)) return first;

        if (first.startsWith("ERR:http:5") || first.startsWith("ERR:Socket") ||
                first.startsWith("ERR:Connect") || first.startsWith("ERR:UnknownHost")) {
            sleepQuietly(900L);
            return postAttempt(context, topic, message, priority);
        }
        return first;
    }

    public static String postOnce(Context context, String topic, String message, int priority) {
        return postAttempt(context, topic, message, priority);
    }

    // One explicit user-initiated publish verifies that the chosen relay can accept messages.
    public static String probe(Context context, String relay, String topic) {
        if (relay == null || !relay.matches("https://[^\\s/]+(?:/[^\\s]*)?")) return "ERR:url";
        return postAttempt(context, topic, "of5probe|" + System.currentTimeMillis(), 1, relay);
    }

    private static String postAttempt(Context context, String topic, String message, int priority) {
        return postAttempt(context, topic, message, priority, null);
    }

    private static String postAttempt(Context context, String topic, String message, int priority, String relayOverride) {
        if (context == null) return "ERR:context";
        if (topic == null || !topic.matches("[a-zA-Z0-9_-]{12,100}")) return "ERR:topic";
        if (message == null || message.isEmpty() || message.length() > 8000) return "ERR:message";
        priority = Math.max(1, Math.min(5, priority));

        SharedPreferences prefs = SecureStore.prefs(context);
        String relay = relayOverride != null ? relayOverride : SecureStore.relay(context);
        if (relay == null || !relay.startsWith("https://")) relay = SecureStore.DEFAULT_RELAY;
        relay = relay.replaceAll("/+$", "");

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
            c.setRequestProperty("User-Agent", "OurFamily/6.0.5 Android");

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }

            int code = c.getResponseCode();
            if (code >= 200 && code < 300) return "OK";
            return "ERR:http:" + code;
        } catch (Exception e) {
            return "ERR:" + e.getClass().getSimpleName();
        } finally {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
