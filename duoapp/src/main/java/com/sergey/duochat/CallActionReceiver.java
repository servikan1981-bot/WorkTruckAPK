package com.sergey.duochat;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_ACCEPT = "com.sergey.ourfamily.ACCEPT_CALL";
    public static final String ACTION_DECLINE = "com.sergey.ourfamily.DECLINE_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ACTION_ACCEPT.equals(action) && !ACTION_DECLINE.equals(action)) return;

        String callerRole = intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_ROLE);
        String callId = intent.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID);
        String kind = intent.getStringExtra(IncomingCallActivity.EXTRA_KIND);
        int notificationId = intent.getIntExtra(IncomingCallActivity.EXTRA_NOTIFICATION_ID, 0);

        if (notificationId != 0) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notificationId);
        }

        if (ACTION_DECLINE.equals(action)) {
            if (callerRole != null && callId != null) CallControl.sendDecline(context, callerRole, callId);
            return;
        }

        if (callerRole == null || callId == null) return;
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("action", "accept");
            o.put("callId", callId);
            o.put("callerRole", callerRole);
            o.put("kind", kind == null ? "video" : kind);
            SecureStore.prefs(context).edit().putString("pending_call_action", o.toString()).apply();
        } catch (Exception ignored) {}

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("call_action", "accept");
        open.putExtra("call_id", callId);
        open.putExtra("caller_role", callerRole);
        open.putExtra("call_kind", kind == null ? "video" : kind);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { context.startActivity(open); } catch (Exception ignored) {}
    }
}
