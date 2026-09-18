package com.sergey.duochat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
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
    private static final String CH_SERVICE = "family_service_v4";
    private static final String CH_MESSAGES = "family_messages_v4";
    private static final String CH_CALLS = "family_calls_v4";
    private static final int FG_ID = 7401;

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
        worker = new Thread(this::listenLoop, "OurFamilyV4Relay");
        worker.start();
    }

    private void listenLoop() {
        while (running) {
            SharedPreferences prefs = SecureStore.prefs(this);
            String topic = prefs.getString("topic", "");
            String myTag = prefs.getString("sender_tag", "");
            String relay = prefs.getString("relay_base", "https://ntfy.sh");

            if (topic.isEmpty() || myTag.isEmpty()) {
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
                while (running && (line = reader.readLine()) != null) handleLine(line, myTag, prefs);
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
            if (msg.startsWith("of4ctl|")) return;

            String[] p = msg.split("\\|", 9);
            if (p.length < 9 || !"of4".equals(p[0])) return;

            String senderTag = p[1];
            String recipientTag = p[2];
            String kind = p[3];
            String callId = p[4];
            String chunkIndex = p[6];

            if (!recipientTag.equals(myTag) || senderTag.equals(myTag) || !"0".equals(chunkIndex)) return;

            String code = SecureStore.familyCode(this);
            if (code.isEmpty()) return;
            String senderRole = FamilyDirectory.roleFromTag(code, senderTag);
            if (senderRole.isEmpty()) return;

            long eventTime = obj.optLong("time", 0L) * 1000L;
            long age = eventTime > 0 ? System.currentTimeMillis() - eventTime : 0L;

            if ("call_offer".equals(kind)) {
                if (age > 120000L) return;
                notifyIncomingCall(senderRole, callId, "video", id);
            } else if ("call_audio_offer".equals(kind)) {
                if (age > 120000L) return;
                notifyIncomingCall(senderRole, callId, "audio", id);
            } else if ("chat".equals(kind)) {
                notifyMessage(senderRole, id);
            }
        } catch (Exception ignored) {}
    }

    private void notifyMessage(String senderRole, String eventId) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, eventId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_MESSAGES)
                : new Notification.Builder(this);

        Notification n = b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(FamilyDirectory.name(senderRole))
                .setContentText("Новое защищённое сообщение")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(8600 + Math.abs(eventId.hashCode() % 1000), n);
    }

    private void notifyIncomingCall(String callerRole, String callId, String kind, String eventId) {
        int notificationId = 9000 + Math.abs(callId.hashCode() % 900);

        Intent full = new Intent(this, IncomingCallActivity.class);
        full.putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId);
        full.putExtra(IncomingCallActivity.EXTRA_CALLER_ROLE, callerRole);
        full.putExtra(IncomingCallActivity.EXTRA_KIND, kind);
        full.putExtra(IncomingCallActivity.EXTRA_NOTIFICATION_ID, notificationId);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullPi = PendingIntent.getActivity(
                this, notificationId, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent accept = new Intent(this, IncomingCallActivity.class);
        accept.setAction(IncomingCallActivity.ACTION_ACCEPT);
        accept.putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId);
        accept.putExtra(IncomingCallActivity.EXTRA_CALLER_ROLE, callerRole);
        accept.putExtra(IncomingCallActivity.EXTRA_KIND, kind);
        accept.putExtra(IncomingCallActivity.EXTRA_NOTIFICATION_ID, notificationId);

        PendingIntent acceptPi = PendingIntent.getActivity(
                this, notificationId + 10000, accept,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent decline = new Intent(this, CallActionReceiver.class);
        decline.setAction(CallActionReceiver.ACTION_DECLINE);
        decline.putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId);
        decline.putExtra(IncomingCallActivity.EXTRA_CALLER_ROLE, callerRole);
        decline.putExtra(IncomingCallActivity.EXTRA_KIND, kind);
        decline.putExtra(IncomingCallActivity.EXTRA_NOTIFICATION_ID, notificationId);

        PendingIntent declinePi = PendingIntent.getBroadcast(
                this, notificationId + 20000, decline,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_CALLS)
                : new Notification.Builder(this);

        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(FamilyDirectory.name(callerRole) + " звонит")
                .setContentText("audio".equals(kind) ? "Аудиозвонок" : "Видеозвонок")
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_MAX)
                .setTimeoutAfter(70000);

        if (Build.VERSION.SDK_INT >= 31) {
            Person caller = new Person.Builder()
                    .setName(FamilyDirectory.name(callerRole))
                    .setImportant(true)
                    .build();
            b.setStyle(Notification.CallStyle.forIncomingCall(caller, declinePi, acceptPi));
        } else {
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отклонить", declinePi);
            b.addAction(android.R.drawable.ic_menu_call, "Принять", acceptPi);
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(notificationId, b.build());
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel service = new NotificationChannel(
                CH_SERVICE, "Наша семья — фоновая связь", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("Поддерживает получение сообщений и звонков");
        nm.createNotificationChannel(service);

        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription("Новые сообщения от членов семьи");
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel calls = new NotificationChannel(
                CH_CALLS, "Входящие семейные звонки", NotificationManager.IMPORTANCE_MAX);
        calls.setDescription("Полноэкранные уведомления о входящих звонках");
        calls.enableVibration(true);
        calls.enableLights(true);
        calls.setLightColor(Color.GREEN);
        calls.setSound(ringtone, attrs);
        calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(calls);
    }

    private void startAsForeground() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_SERVICE)
                : new Notification.Builder(this);

        Notification n = b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Наша семья")
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
    public IBinder onBind(Intent intent) { return null; }
}
