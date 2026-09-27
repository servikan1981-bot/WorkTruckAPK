package com.sergey.duochat;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class UrgentMessageActivity extends Activity {
    public static final String EXTRA_ID = "urgent_id";
    public static final String EXTRA_TEXT = "urgent_text";
    private static final String CH_URGENT = "family_urgent_v621";
    private static final int BASE_NOTIFICATION_ID = 12100;

    public static void present(Context context, String id, String text, boolean appVisible) {
        if (context == null || id == null || id.isEmpty() || text == null || text.trim().isEmpty()) return;
        ensureChannel(context);

        SharedPreferences prefs = SecureStore.prefs(context);
        if (id.equals(prefs.getString("urgent_dismissed_id", ""))) return;

        Intent open = new Intent(context, UrgentMessageActivity.class);
        open.putExtra(EXTRA_ID, id);
        open.putExtra(EXTRA_TEXT, text);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notificationId = BASE_NOTIFICATION_ID + Math.abs(id.hashCode() % 700);
        PendingIntent pi = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CH_URGENT)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("СРОЧНО ОТ СЕРГЕЯ")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(false)
                .setOngoing(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notificationId, b.build());

        if (appVisible) {
            try { context.startActivity(open); } catch (Exception ignored) {}
        }
    }

    public static void showPendingIfNeeded(Activity activity) {
        if (activity == null || activity instanceof UrgentMessageActivity) return;
        SharedPreferences p = SecureStore.prefs(activity);
        String id = p.getString("urgent_active_id", "");
        String dismissed = p.getString("urgent_dismissed_id", "");
        String text = p.getString("urgent_active_text", "");
        if (id.isEmpty() || id.equals(dismissed) || text.isEmpty()) return;
        Intent i = new Intent(activity, UrgentMessageActivity.class);
        i.putExtra(EXTRA_ID, id);
        i.putExtra(EXTRA_TEXT, text);
        try { activity.startActivity(i); } catch (Exception ignored) {}
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CH_URGENT) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CH_URGENT, "Срочные сообщения Сергея", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Срочные сообщения для всей семьи");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setLightColor(Color.RED);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attrs);
        nm.createNotificationChannel(channel);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            }
        } catch (Exception ignored) {}
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        render();
    }

    private void render() {
        SharedPreferences p = SecureStore.prefs(this);
        String id = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_ID);
        String text = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_TEXT);
        if (id == null || id.isEmpty()) id = p.getString("urgent_active_id", "");
        if (text == null || text.isEmpty()) text = p.getString("urgent_active_text", "");
        if (id.isEmpty() || text.isEmpty() || id.equals(p.getString("urgent_dismissed_id", ""))) {
            finish();
            return;
        }
        final String finalId = id;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(153, 27, 27));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("СРОЧНОЕ СООБЩЕНИЕ\nОТ СЕРГЕЯ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button close = new Button(this);
        close.setText("✕");
        close.setTextSize(24);
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription("Скрыть срочное сообщение");
        close.setOnClickListener(v -> dismiss(finalId));
        header.addView(close, new LinearLayout.LayoutParams(dp(58), dp(58)));
        root.addView(header);

        TextView note = new TextView(this);
        note.setText("Сообщение для всей семьи");
        note.setTextColor(Color.rgb(254, 202, 202));
        note.setTextSize(14);
        note.setPadding(0, dp(8), 0, dp(18));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(Color.WHITE);
        body.setTextSize(24);
        body.setLineSpacing(0f, 1.12f);
        body.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("Нажмите ✕, чтобы больше не показывать это сообщение.");
        hint.setTextColor(Color.rgb(254, 202, 202));
        hint.setTextSize(13);
        hint.setPadding(0, dp(18), 0, 0);
        root.addView(hint);

        setContentView(root);
    }

    private void dismiss(String id) {
        SharedPreferences prefs = SecureStore.prefs(this);
        SharedPreferences.Editor editor = prefs.edit().putString("urgent_dismissed_id", id);
        if (id.equals(prefs.getString("urgent_active_id", ""))) {
            editor.remove("urgent_active_id").remove("urgent_active_text").remove("urgent_active_ts");
        }
        editor.commit();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(BASE_NOTIFICATION_ID + Math.abs(id.hashCode() % 700));
        finish();
    }

    @Override
    public void onBackPressed() {
        String id = SecureStore.prefs(this).getString("urgent_active_id", "");
        if (!id.isEmpty()) dismiss(id); else super.onBackPressed();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
