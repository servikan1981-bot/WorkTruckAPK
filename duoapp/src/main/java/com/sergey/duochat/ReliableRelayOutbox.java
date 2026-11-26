package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Durable delivery for game and urgent events. A queued event survives process restarts. */
public final class ReliableRelayOutbox {
    private static final String KEY = "reliable_relay_outbox_v623";
    private static final int MAX_PENDING = 200;
    private static final Object LOCK = new Object();
    private static final Set<String> ACTIVE = new HashSet<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "OurFamilyReliableSend");
        t.setDaemon(true);
        return t;
    });
    private static final ScheduledExecutorService RETRY = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "OurFamilyReliableRetry");
        t.setDaemon(true);
        return t;
    });
    private static boolean retryScheduled;

    private ReliableRelayOutbox() {}

    public static boolean isCritical(String wire) {
        if (wire == null || !wire.startsWith("of5|")) return false;
        String[] parts = wire.split("\\|", 9);
        if (parts.length < 9) return false;
        String kind = parts[3];
        return kind.startsWith("durak_") || kind.startsWith("game_") ||
                "urgent_broadcast".equals(kind);
    }

    public static boolean enqueue(Context context, String topic, String wire, int priority) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            SharedPreferences prefs = SecureStore.prefs(app);
            JSONArray pending = read(prefs);
            if (pending.length() >= MAX_PENDING) return false;
            JSONObject entry = new JSONObject();
            try {
                entry.put("id", UUID.randomUUID().toString());
                entry.put("topic", topic);
                entry.put("wire", wire);
                entry.put("priority", priority);
                pending.put(entry);
                if (!prefs.edit().putString(KEY, pending.toString()).commit()) return false;
            } catch (Exception e) { return false; }
        }
        kick(app);
        return true;
    }

    public static void kick(Context context) {
        Context app = context.getApplicationContext();
        for (int i = 0; i < 4; i++) WORKERS.execute(() -> drain(app));
    }

    public static int pendingFor(Context context, String callId) {
        if (callId == null || callId.isEmpty()) return 0;
        synchronized (LOCK) {
            JSONArray items = read(SecureStore.prefs(context));
            int count = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject entry = items.optJSONObject(i);
                if (entry != null && entry.optString("wire", "").contains("|" + callId + "|")) count++;
            }
            return count;
        }
    }

    private static JSONArray read(SharedPreferences prefs) {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private static JSONObject claim(Context app) {
        synchronized (LOCK) {
            JSONArray items = read(SecureStore.prefs(app));
            JSONObject best = null;
            long now = System.currentTimeMillis();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty() || ACTIVE.contains(id) || RETRY_AFTER.getOrDefault(id, 0L) > now) continue;
                if (best == null || item.optInt("priority", 0) > best.optInt("priority", 0)) best = item;
            }
            if (best != null) ACTIVE.add(best.optString("id"));
            return best;
        }
    }

    private static void finish(Context app, String id, boolean sent) {
        synchronized (LOCK) {
            ACTIVE.remove(id);
            if (!sent) {
                RETRY_AFTER.put(id, System.currentTimeMillis() + 4000L);
                scheduleRetry(app);
                return;
            }
            SharedPreferences prefs = SecureStore.prefs(app);
            JSONArray items = read(prefs), keep = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id"))) keep.put(item);
            }
            if (prefs.edit().putString(KEY, keep.toString()).commit()) RETRY_AFTER.remove(id);
            else scheduleRetry(app);
        }
    }

    private static void scheduleRetry(Context app) {
        if (retryScheduled) return;
        retryScheduled = true;
        RETRY.schedule(() -> {
            synchronized (LOCK) { retryScheduled = false; }
            kick(app);
        }, 4, TimeUnit.SECONDS);
    }

    private static void drain(Context app) {
        JSONObject item;
        while ((item = claim(app)) != null) {
            String id = item.optString("id");
            String result = NativeRelayTransport.post(app, item.optString("topic"),
                    item.optString("wire"), item.optInt("priority", 4));
            finish(app, id, "OK".equals(result));
        }
    }
}
