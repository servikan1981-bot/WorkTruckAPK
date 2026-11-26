package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;

public final class PresenceStore {
    private static final String KEY = "presence_native_v602";
    private static final String LIVE_KEY = "presence_live_v609";
    private static final long LIVE_MS = 70_000L;
    private static final long KEEP_MS = 10L * 60L * 1000L;

    private PresenceStore() {}

    public static synchronized void update(Context context, String role, long ts) {
        if (context == null || !FamilyDirectory.validRole(role) || ts <= 0L) return;
        SharedPreferences p = SecureStore.prefs(context);
        try {
            JSONObject o = new JSONObject(p.getString(KEY, "{}"));
            long previous = o.optLong(role, 0L);
            if (ts > previous) o.put(role, ts);
            prune(o);
            p.edit().putString(KEY, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized String read(Context context) {
        if (context == null) return "{}";
        SharedPreferences p = SecureStore.prefs(context);
        try {
            JSONObject o = new JSONObject(p.getString(KEY, "{}"));
            prune(o);
            p.edit().putString(KEY, o.toString()).apply();
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // Live presence is separate from message activity: receiving a message must
    // never make a backgrounded sender appear online.
    public static synchronized void updateLive(Context context, String role, long serverTime) {
        if (context == null || !FamilyDirectory.validRole(role)) return;
        try {
            SharedPreferences p = SecureStore.prefs(context);
            JSONObject o = new JSONObject(p.getString(LIVE_KEY, "{}"));
            o.put(role, serverTime > 0L ? serverTime : 0L);
            p.edit().putString(LIVE_KEY, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static synchronized String readLive(Context context) {
        if (context == null) return "{}";
        try {
            SharedPreferences p = SecureStore.prefs(context);
            JSONObject o = new JSONObject(p.getString(LIVE_KEY, "{}"));
            java.util.ArrayList<String> remove = new java.util.ArrayList<>();
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String role = it.next();
                if (!FamilyDirectory.validRole(role) ||
                        (o.optLong(role) != 0L && System.currentTimeMillis() - o.optLong(role) > LIVE_MS)) {
                    remove.add(role);
                }
            }
            for (String role : remove) o.remove(role);
            p.edit().putString(LIVE_KEY, o.toString()).apply();
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static synchronized void clearLive(Context context) {
        SecureStore.prefs(context).edit().remove(LIVE_KEY).apply();
    }

    private static void prune(JSONObject o) {
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        java.util.ArrayList<String> remove = new java.util.ArrayList<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!FamilyDirectory.validRole(k) || o.optLong(k, 0L) < cutoff) remove.add(k);
        }
        for (String k : remove) o.remove(k);
    }
}
