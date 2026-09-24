package com.sergey.duochat;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NativeAttachmentTransport {
    private static final int MAX_BYTES = 13_000_000;

    private NativeAttachmentTransport() {}

    public static String uploadBase64(Context context, String encoded) {
        if (context == null || encoded == null || encoded.isEmpty()) return "ERR:input";
        try {
            byte[] data = Base64.decode(encoded, Base64.DEFAULT);
            if (data.length <= 0 || data.length > MAX_BYTES) return "ERR:size";

            String relay = SecureStore.relay(context);
            if (relay == null || !relay.startsWith("https://")) relay = "https://ntfy.sh";
            relay = relay.replaceAll("/+$", "");

            String topic = "of5file-" + java.util.UUID.randomUUID().toString().replace("-", "");
            HttpURLConnection c = (HttpURLConnection) new URL(relay + "/" + topic).openConnection();
            try {
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setUseCaches(false);
                c.setDoOutput(true);
                c.setRequestMethod("PUT");
                c.setRequestProperty("Filename", "encrypted.bin");
                c.setRequestProperty("Content-Type", "application/octet-stream");
                c.setRequestProperty("User-Agent", "OurFamily/6.0.4 Android");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(data);
                    out.flush();
                }
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) return "ERR:http:" + code;

                String text;
                try (InputStream in = c.getInputStream()) {
                    text = new String(readLimited(in, 1_000_000), StandardCharsets.UTF_8).trim();
                }
                String[] lines = text.split("\\r?\\n");
                JSONObject obj = new JSONObject(lines[lines.length - 1]);
                JSONObject att = obj.optJSONObject("attachment");
                String url = att == null ? "" : att.optString("url", "");
                if (!url.startsWith("https://")) return "ERR:url";
                return "OK:" + url;
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return "ERR:" + e.getClass().getSimpleName();
        }
    }

    public static String downloadBase64(Context context, String url) {
        if (context == null || url == null || !url.startsWith("https://")) return "ERR:url";
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            URL relay = new URL(SecureStore.relay(context));
            if (relay.getHost() != null && !relay.getHost().isEmpty() &&
                    u.getHost() != null && !u.getHost().equalsIgnoreCase(relay.getHost())) {
                return "ERR:host";
            }
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent", "OurFamily/6.0.4 Android");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return "ERR:http:" + code;
            byte[] data;
            try (InputStream in = c.getInputStream()) {
                data = readLimited(in, MAX_BYTES);
            }
            return "OK:" + Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return "ERR:" + e.getClass().getSimpleName();
        } finally {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
    }

    private static byte[] readLimited(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > limit) throw new IllegalStateException("too_large");
        }
        return out.toByteArray();
    }
}
