package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;

public final class PresenceStore {
    private static final String KEY = "presence_native_v602";
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
