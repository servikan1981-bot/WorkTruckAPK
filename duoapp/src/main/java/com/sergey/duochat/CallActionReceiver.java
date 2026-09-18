package com.sergey.duochat;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallActionReceiver extends BroadcastReceiver {
    public static final String ACTION_DECLINE = "com.sergey.duochat.v4.DECLINE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DECLINE.equals(intent.getAction())) return;

        String callerRole = intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_ROLE);
        String callId = intent.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID);
        int notificationId = intent.getIntExtra(IncomingCallActivity.EXTRA_NOTIFICATION_ID, 0);

        if (callerRole != null && callId != null) {
            CallControl.sendDecline(context, callerRole, callId);
        }
        if (notificationId != 0) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notificationId);
        }
    }
}
