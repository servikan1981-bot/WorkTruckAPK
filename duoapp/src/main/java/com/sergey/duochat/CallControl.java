package com.sergey.duochat;

import android.content.Context;

public final class CallControl {
    private CallControl() {}

    public static void sendDecline(Context context, String callerRole, String callId) {
        new Thread(() -> {
            try {
                String ownRole = SecureStore.role(context);
                String code = SecureStore.familyCode(context);
                if (ownRole.isEmpty() || code.isEmpty() || callerRole.isEmpty() || callId.isEmpty()) return;

                String fromTag = FamilyDirectory.tag(code, ownRole);
                String toTag = FamilyDirectory.tag(code, callerRole);
                String token = FamilyDirectory.controlToken(code, callId, "decline", ownRole, callerRole);
                String wire = "of5ctl|" + fromTag + "|" + toTag + "|" + callId + "|decline|" + token;
                String topic = FamilyDirectory.inboxTopic(code, callerRole);
                NativeRelayTransport.post(context, topic, wire, 4);
            } catch (Exception ignored) {}
        }, "OurFamilyDecline").start();
    }
}
