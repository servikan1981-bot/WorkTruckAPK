package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RelayInbox {
    private static final String KEY_QUEUE = "relay_inbox_v5";
    private static final String KEY_SEEN = "relay_seen_v5";
    private static final Object LOCK = new Object();
    private static final int MAX_QUEUE = 700;
    private static final int MAX_SEEN = 900;

    private RelayInbox() {}

    public static boolean seenAndMark(Context c, String id) {
        if (id == null || id.isEmpty()) return true;
        synchronized (LOCK) {
            SharedPreferences p = SecureStore.prefs(c);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            try {
                JSONArray a = new JSONArray(p.getString(KEY_SEEN, "[]"));
                for (int i = 0; i < a.length(); i++) {
                    String s = a.optString(i, "");
                    if (!s.isEmpty()) seen.add(s);
                }
            } catch (Exception ignored) {}
            if (seen.contains(id)) return true;
            seen.add(id);
            while (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
            JSONArray out = new JSONArray();
            for (String s : seen) out.put(s);
            p.edit().putString(KEY_SEEN, out.toString()).apply();
            return false;
        }
    }

    public static void enqueue(Context c, String eventId, long timeMs, String message) {
        if (message == null || message.isEmpty()) return;
        synchronized (LOCK) {
            SharedPreferences p = SecureStore.prefs(c);
            JSONArray q;
            try { q = new JSONArray(p.getString(KEY_QUEUE, "[]")); }
            catch (Exception e) { q = new JSONArray(); }

            try {
                JSONObject item = new JSONObject();
                item.put("id", eventId == null ? "" : eventId);
                item.put("time", timeMs);
                item.put("message", message);
                q.put(item);
            } catch (Exception ignored) {}

            if (q.length() > MAX_QUEUE) {
                JSONArray trimmed = new JSONArray();
                int start = Math.max(0, q.length() - MAX_QUEUE);
                for (int i = start; i < q.length(); i++) {
                    Object item = q.opt(i);
                    if (item != null) trimmed.put(item);
                }
                q = trimmed;
            }
            p.edit().putString(KEY_QUEUE, q.toString()).apply();
        }
    }

    public static String read(Context c) {
        synchronized (LOCK) {
            String value = SecureStore.prefs(c).getString(KEY_QUEUE, "[]");
            return value == null || value.isEmpty() ? "[]" : value;
        }
    }

    public static void ack(Context c, String idsJson) {
        synchronized (LOCK) {
            Set<String> ackIds = new LinkedHashSet<>();
            try {
                JSONArray ids = new JSONArray(idsJson == null ? "[]" : idsJson);
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i, "");
                    if (!id.isEmpty()) ackIds.add(id);
                }
            } catch (Exception ignored) {}
            if (ackIds.isEmpty()) return;

            SharedPreferences p = SecureStore.prefs(c);
            JSONArray q;
            try { q = new JSONArray(p.getString(KEY_QUEUE, "[]")); }
            catch (Exception e) { q = new JSONArray(); }

            JSONArray keep = new JSONArray();
            for (int i = 0; i < q.length(); i++) {
                JSONObject item = q.optJSONObject(i);
                if (item == null) continue;
                if (!ackIds.contains(item.optString("id", ""))) keep.put(item);
            }
            p.edit().putString(KEY_QUEUE, keep.toString()).apply();
        }
    }
}
