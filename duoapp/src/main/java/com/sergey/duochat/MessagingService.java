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
    private static final String PREFS = "duo_native";
    private static final String CH_SERVICE = "duo_service";
    private static final String CH_ALERTS = "duo_alerts";
    private static final int FG_ID = 7001;

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
            if (activeConnection != null) {
                try { activeConnection.disconnect(); } catch (Exception ignored) {}
            }
        }
        if (worker == null || !worker.isAlive()) startWorker();
        return START_STICKY;
    }

    private void startWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::listenLoop, "DuoNtfyListener");
        worker.start();
    }

    private void listenLoop() {
        while (running) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String topic = prefs.getString("topic", "");
            String myRole = prefs.getString("role", "");
            if (topic.isEmpty() || myRole.isEmpty()) {
                sleep(3000);
                continue;
            }

            BufferedReader reader = null;
            try {
                URL url = new URL("https://ntfy.sh/" + topic + "/json?since=10m");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                activeConnection = c;
                c.setConnectTimeout(15000);
                c.setReadTimeout(0);
                c.setRequestProperty("Accept", "application/x-ndjson");
                c.connect();

                reader = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleLine(line, myRole, prefs);
                }
            } catch (Exception ignored) {
            } finally {
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
                activeConnection = null;
            }
            sleep(2500);
        }
    }

    private void handleLine(String line, String myRole, SharedPreferences prefs) {
        try {
            JSONObject obj = new JSONObject(line);
            if (!"message".equals(obj.optString("event"))) return;
            String id = obj.optString("id");
            if (id.isEmpty()) return;
            if (prefs.getBoolean("seen_" + id, false)) return;

            String msg = obj.optString("message", "");
            String[] p = msg.split("\\|", 7);
            if (p.length < 7 || !"oc2".equals(p[0])) {
                prefs.edit().putBoolean("seen_" + id, true).apply();
                return;
            }

            String sender = p[1];
            String type = p[2];
            String chunkIndex = p[4];
            prefs.edit().putBoolean("seen_" + id, true).apply();

            if (sender.equals(myRole)) return;
            if (!"0".equals(chunkIndex)) return;

            if ("chat".equals(type)) {
                notifyAlert("Новое сообщение", displayName(sender) + " написал(а) вам", false, id);
            } else if ("call_offer".equals(type)) {
                notifyAlert("Входящий звонок", displayName(sender) + " звонит", true, id);
            }
        } catch (Exception ignored) {
        }
    }

    private String displayName(String role) {
        return "sergey".equals(role) ? "Сергей" : "Супруга";
    }

    private void notifyAlert(String title, String text, boolean call, String id) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri sound = RingtoneManager.getDefaultUri(
                call ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_NOTIFICATION
        );

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

        if (call) {
            b.setOngoing(false);
            b.setVibrate(new long[]{0, 600, 350, 600, 350, 900});
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(8000 + Math.abs(id.hashCode() % 1000), b.build());
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel service = new NotificationChannel(
                CH_SERVICE, "Наш чат — фоновая связь", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Поддерживает получение сообщений и звонков");
        nm.createNotificationChannel(service);

        NotificationChannel alerts = new NotificationChannel(
                CH_ALERTS, "Сообщения и звонки", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Уведомления о новых сообщениях и входящих звонках");
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
                .setContentTitle("Наш чат")
                .setContentText("Фоновая связь включена")
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        } else {
            startForeground(FG_ID, n);
        }
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
    public IBinder onBind(Intent intent) {
        return null;
    }
}
