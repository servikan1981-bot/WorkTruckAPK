package com.sergey.duochat;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_ACCEPT = "com.sergey.ourfamily.ACCEPT_CALL";
    public static final String ACTION_DECLINE = "com.sergey.ourfamily.DECLINE_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String callerRole = intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_ROLE);
        String callId = intent.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID);
        String kind = intent.getStringExtra(IncomingCallActivity.EXTRA_KIND);
        int notificationId = intent.getIntExtra(IncomingCallActivity.EXTRA_NOTIFICATION_ID, 0);

        if (ACTION_ACCEPT.equals(action)) {
            if (callerRole == null || callId == null) return;
            try {
                JSONObject o = new JSONObject();
                o.put("action", "accept");
                o.put("callId", callId);
                o.put("callerRole", callerRole);
                o.put("kind", kind == null ? "video" : kind);
                SecureStore.prefs(context).edit()
                        .putString("pending_call_action", o.toString())
                        .apply();
            } catch (Exception ignored) {}

            cancel(context, notificationId);

            Intent open = new Intent(context, MainActivity.class);
            open.putExtra("call_action", "accept");
            open.putExtra("call_id", callId);
            open.putExtra("caller_role", callerRole);
            open.putExtra("call_kind", kind == null ? "video" : kind);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(open);
            return;
        }

        if (ACTION_DECLINE.equals(action)) {
            if (callerRole != null && callId != null) {
                CallControl.sendDecline(context, callerRole, callId);
            }
            cancel(context, notificationId);
        }
    }

    private static void cancel(Context context, int notificationId) {
        if (notificationId == 0) return;
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId);
    }
}
