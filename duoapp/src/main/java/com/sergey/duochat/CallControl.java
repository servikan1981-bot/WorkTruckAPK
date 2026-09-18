package com.sergey.duochat;

import android.content.Context;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CallControl {
    private CallControl() {}

    public static void sendDecline(Context context, String callerRole, String callId) {
        new Thread(() -> {
            try {
                String ownRole = SecureStore.role(context);
                String code = SecureStore.familyCode(context);
                String relay = SecureStore.relay(context);
                if (ownRole.isEmpty() || code.isEmpty() || callerRole.isEmpty() || callId.isEmpty()) return;

                String fromTag = FamilyDirectory.tag(code, ownRole);
                String toTag = FamilyDirectory.tag(code, callerRole);
                String token = FamilyDirectory.controlToken(code, callId, "decline", ownRole, callerRole);
                String wire = "of5ctl|" + fromTag + "|" + toTag + "|" + callId + "|decline|" + token;

                JSONObject body = new JSONObject();
                body.put("topic", FamilyDirectory.inboxTopic(code, callerRole));
                body.put("message", wire);
                body.put("priority", 4);

                HttpURLConnection c = (HttpURLConnection) new URL(relay + "/").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) {}
        }, "OurFamilyDecline").start();
    }
}
