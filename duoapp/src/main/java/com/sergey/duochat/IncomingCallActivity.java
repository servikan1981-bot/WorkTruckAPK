package com.sergey.duochat;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

public class IncomingCallActivity extends Activity {
    public static final String ACTION_ACCEPT = "com.sergey.ourfamily.ACCEPT_CALL";
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

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
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
        root.setPadding(dp(24), dp(36), dp(24), dp(36));
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
        nameLp.topMargin = dp(20);
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
        subLp.topMargin = dp(10);
        root.addView(subtitle, subLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, 0, 0, 0);

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

        decline.setAllCaps(false);
        accept.setAllCaps(false);
        actions.addView(accept, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        LinearLayout.LayoutParams declineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        declineLp.topMargin = dp(12);
        actions.addView(decline, declineLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(36);
        root.addView(actions, actionsLp);

        decline.setOnClickListener(v -> decline());
        accept.setOnClickListener(v -> accept());

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(Math.max(dp(24), safe.left + dp(16)),
                        Math.max(dp(36), safe.top + dp(16)),
                        Math.max(dp(24), safe.right + dp(16)),
                        Math.max(dp(36), safe.bottom + dp(16)));
                return insets;
            });
        }
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) root.requestApplyInsets();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
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
