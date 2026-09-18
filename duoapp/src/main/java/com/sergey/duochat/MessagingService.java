package com.sergey.duochat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MessagingService extends Service {
    public static final String ACTION_RESTART = "com.sergey.duochat.RESTART_LISTENER";
    private static final String PREFS = "duo_native_v3";
    private static final String CH_SERVICE = "duo_service_v3";
    private static final String CH_ALERTS = "duo_alerts_v3";
    private static final int FG_ID = 7301;

    private volatile boolean running = false;
    private volatile HttpURLConnection activeConnection;
    private Thread worker;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startAsForeground();
        running = true;
        startWorker();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
        }
        if (worker == null || !worker.isAlive()) startWorker();
        return START_STICKY;
    }

    private void startWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::listenLoop, "OurChatV3Relay");
        worker.start();
    }

    private void listenLoop() {
        while (running) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String topic = prefs.getString("topic", "");
            String senderTag = prefs.getString("sender_tag", "");
            String relay = prefs.getString("relay_base", "https://ntfy.sh");
            if (topic.isEmpty() || senderTag.isEmpty()) {
                sleep(3000);
                continue;
            }

            BufferedReader reader = null;
            try {
                URL url = new URL(relay + "/" + topic + "/json?since=10m");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                activeConnection = c;
                c.setConnectTimeout(15000);
                c.setReadTimeout(0);
                c.setRequestProperty("Accept", "application/x-ndjson");
                c.connect();

                reader = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = reader.readLine()) != null) handleLine(line, senderTag, prefs);
            } catch (Exception ignored) {
            } finally {
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
                activeConnection = null;
            }
            sleep(2500);
        }
    }

    private void handleLine(String line, String myTag, SharedPreferences prefs) {
        try {
            JSONObject obj = new JSONObject(line);
            if (!"message".equals(obj.optString("event"))) return;
            String id = obj.optString("id");
            if (id.isEmpty() || prefs.getBoolean("seen_" + id, false)) return;
            prefs.edit().putBoolean("seen_" + id, true).apply();

            String msg = obj.optString("message", "");
            String[] p = msg.split("\\|", 6);
            if (p.length < 6 || !"oc3".equals(p[0])) return;

            String senderTag = p[1];
            String chunkIndex = p[3];
            if (senderTag.equals(myTag) || !"0".equals(chunkIndex)) return;

            int priority = obj.optInt("priority", 3);
            if (priority >= 5) {
                notifyAlert("Входящий звонок", "Откройте «Наш чат v3», чтобы ответить", true, id);
            } else if (priority >= 3) {
                notifyAlert("Новое сообщение", "Вам пришло защищённое сообщение", false, id);
            }
        } catch (Exception ignored) {}
    }

    private void notifyAlert(String title, String text, boolean call, String id) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri sound = RingtoneManager.getDefaultUri(call ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_NOTIFICATION);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_ALERTS)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setSound(sound)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(call ? Notification.CATEGORY_CALL : Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);

        if (call) b.setVibrate(new long[]{0, 650, 300, 650, 300, 1000});

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(8300 + Math.abs(id.hashCode() % 1000), b.build());
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel service = new NotificationChannel(
                CH_SERVICE, "Наш чат v3 — фоновая связь", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Поддерживает защищённый канал сообщений и звонков");
        nm.createNotificationChannel(service);

        NotificationChannel alerts = new NotificationChannel(
                CH_ALERTS, "Наш чат v3 — сообщения и звонки", NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);
        alerts.enableLights(true);
        alerts.setLightColor(Color.BLUE);
        nm.createNotificationChannel(alerts);
    }

    private void startAsForeground() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_SERVICE)
                : new Notification.Builder(this);

        Notification n = b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Наш чат v3")
                .setContentText("Защищённая фоновая связь включена")
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build();

        if (Build.VERSION.SDK_INT >= 34) startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        else startForeground(FG_ID, n);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
