package com.sergey.duochat;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

public class IncomingCallActivity extends Activity {
    public static final String ACTION_ACCEPT = "com.sergey.duochat.v4.ACCEPT";
    public static final String EXTRA_CALL_ID = "call_id";
    public static final String EXTRA_CALLER_ROLE = "caller_role";
    public static final String EXTRA_KIND = "call_kind";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    private String callId = "";
    private String callerRole = "";
    private String kind = "video";
    private int notificationId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        readExtras(getIntent());
        if (ACTION_ACCEPT.equals(getIntent().getAction())) { accept(); return; }
        buildUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readExtras(intent);
        if (ACTION_ACCEPT.equals(intent.getAction())) { accept(); return; }
        buildUi();
    }

    private void readExtras(Intent i) {
        if (i == null) return;
        callId = i.getStringExtra(EXTRA_CALL_ID);
        callerRole = i.getStringExtra(EXTRA_CALLER_ROLE);
        kind = i.getStringExtra(EXTRA_KIND);
        notificationId = i.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        if (callId == null) callId = "";
        if (callerRole == null) callerRole = "";
        if (kind == null) kind = "video";
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 48, 32, 48);
        root.setBackgroundColor(Color.rgb(10, 16, 32));

        TextView heart = new TextView(this);
        heart.setText("❤");
        heart.setTextSize(64);
        heart.setGravity(Gravity.CENTER);
        root.addView(heart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(this);
        name.setText(FamilyDirectory.name(callerRole));
        name.setTextColor(Color.WHITE);
        name.setTextSize(34);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = 20;
        root.addView(name, nameLp);

        TextView subtitle = new TextView(this);
        if ("group_audio".equals(kind)) subtitle.setText("Входящий групповой аудиозвонок");
        else if ("group_video".equals(kind)) subtitle.setText("Входящий групповой видеозвонок");
        else subtitle.setText("audio".equals(kind) ? "Входящий аудиозвонок" : "Входящий видеозвонок");
        subtitle.setTextColor(Color.rgb(190, 202, 220));
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = 10;
        root.addView(subtitle, subLp);

        LinearLayout spacer = new LinearLayout(this);
        root.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, 20, 0, 12);

        Button decline = new Button(this);
        decline.setText("Отклонить");
        decline.setTextColor(Color.WHITE);
        decline.setTextSize(17);
        decline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        decline.setBackgroundColor(Color.rgb(226, 74, 74));

        Button accept = new Button(this);
        accept.setText("Принять");
        accept.setTextColor(Color.WHITE);
        accept.setTextSize(17);
        accept.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        accept.setBackgroundColor(Color.rgb(24, 166, 106));

        LinearLayout.LayoutParams btn = new LinearLayout.LayoutParams(0, 64, 1f);
        btn.setMargins(8, 0, 8, 0);
        actions.addView(decline, btn);
        actions.addView(accept, btn);
        root.addView(actions);

        decline.setOnClickListener(v -> decline());
        accept.setOnClickListener(v -> accept());

        setContentView(root);
    }

    private void accept() {
        cancelNotification();
        try {
            JSONObject action = new JSONObject();
            action.put("action", "accept");
            action.put("callId", callId);
            action.put("callerRole", callerRole);
            action.put("kind", kind);
            SecureStore.prefs(this).edit()
                    .putString("pending_call_action", action.toString())
                    .apply();
        } catch (Exception ignored) {}

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("call_action", "accept");
        i.putExtra("call_id", callId);
        i.putExtra("caller_role", callerRole);
        i.putExtra("call_kind", kind);
        startActivity(i);
        finish();
    }

    private void decline() {
        cancelNotification();
        CallControl.sendDecline(this, callerRole, callId);
        finish();
    }

    private void cancelNotification() {
        if (notificationId != 0) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(notificationId);
        }
    }
}
