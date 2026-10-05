package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NativeRelayTransport {
    private NativeRelayTransport() {}

    public static String post(Context context, String topic, String message, int priority) {
        String first = postAttempt(context, topic, message, priority);
        if ("OK".equals(first)) return first;

        if (isRetryable(first)) {
            // Calls/signaling must fail over quickly instead of freezing the setup
            // behind a 10-20 second HTTPS stall.
            sleepQuietly(priority >= 4 ? 180L : 600L);
            return postAttempt(context, topic, message, priority);
        }
        return first;
    }

    public static String postOnce(Context context, String topic, String message, int priority) {
        return postAttempt(context, topic, message, priority);
    }

    public static String postBatchBlocking(Context context, String topic, String messagesJson, int priority) {
        try {
            JSONArray messages = new JSONArray(messagesJson);
            if (messages.length() < 2 || messages.length() > 16) return "ERR:batch_size";
            for (int i = 0; i < messages.length(); i++) {
                String wire = messages.getString(i);
                if (wire.isEmpty() || wire.length() > 8000) return "ERR:message";
            }
            final String[] result = {"ERR:timeout"};
            Thread t = new Thread(() -> result[0] = postBatch(context, topic, messages, priority), "OurFamilyRelayBatch");
            t.start();
            t.join(priority >= 4 ? 14000L : 30000L);
            if (t.isAlive()) { t.interrupt(); return "ERR:timeout"; }
            return result[0];
        } catch (Exception e) { return "ERR:batch"; }
    }

    public static String postBatchJson(Context context, String topic, String messagesJson, int priority) {
        try {
            JSONArray messages = new JSONArray(messagesJson);
            if (messages.length() < 2 || messages.length() > 16) return "ERR:batch_size";
            for (int i = 0; i < messages.length(); i++) {
                String wire = messages.getString(i);
                if (wire.isEmpty() || wire.length() > 8000) return "ERR:message";
            }
            return postBatch(context, topic, messages, priority);
        } catch (Exception e) { return "ERR:batch"; }
    }

    private static String postBatch(Context context, String topic, JSONArray messages, int priority) {
        String first = postBatchAttempt(context, topic, messages, priority);
        if ("OK".equals(first)) return first;
        if (isRetryable(first)) {
            sleepQuietly(priority >= 4 ? 180L : 450L);
            return postBatchAttempt(context, topic, messages, priority);
        }
        return first;
    }

    private static String postBatchAttempt(Context context, String topic, JSONArray messages, int priority) {
        if (context == null || topic == null || !topic.matches("[a-zA-Z0-9_-]{12,100}")) return "ERR:topic";
        String relay = SecureStore.relay(context);
        if (relay == null || !relay.startsWith("https://")) relay = SecureStore.DEFAULT_RELAY;
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("topic", topic);
            body.put("messages", messages);
            body.put("priority", Math.max(1, Math.min(5, priority)));
            c = (HttpURLConnection) new URL(relay.replaceAll("/+$", "") + "/").openConnection();
            applyTimeouts(c, priority);
            c.setUseCaches(false);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "OurFamily/6.0.32 Android");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); out.flush(); }
            int status = c.getResponseCode();
            return status >= 200 && status < 300 ? "OK" : "ERR:http:" + status;
        } catch (Exception e) { return "ERR:" + e.getClass().getSimpleName(); }
        finally { if (c != null) c.disconnect(); }
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
            applyTimeouts(c, priority);
            c.setUseCaches(false);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "OurFamily/6.0.32 Android");

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

    private static void applyTimeouts(HttpURLConnection c, int priority) {
        // Critical call signaling gets short deadlines. Normal chat/news traffic can
        // tolerate a little more time without stalling call establishment.
        if (priority >= 4) {
            c.setConnectTimeout(3500);
            c.setReadTimeout(5000);
        } else {
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
        }
    }

    private static boolean isRetryable(String result) {
        return result != null && (result.startsWith("ERR:http:5") ||
                result.startsWith("ERR:Socket") || result.startsWith("ERR:Connect") ||
                result.startsWith("ERR:UnknownHost"));
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static String postBlocking(Context context, String topic, String message, int priority) {
        final String[] result = new String[] {"ERR:timeout"};
        Thread t = new Thread(() -> result[0] = post(context, topic, message, priority), "OurFamilyRelayPost");
        t.start();
        try {
            t.join(priority >= 4 ? 14000L : 30000L);
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
