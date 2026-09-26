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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MessagingService extends Service {
    public static final String ACTION_RESTART = "com.sergey.duochat.RESTART_LISTENER";
    public static final String ACTION_PRESENCE_CHANGE = "com.sergey.duochat.PRESENCE_CHANGE";
    private static volatile boolean appVisible = false;

    public static void setAppVisible(boolean visible) { appVisible = visible; }
    public static boolean isAppVisible() { return appVisible; }
    private static final String CH_SERVICE = "family_service_v6";
    private static final String CH_MESSAGES = "family_messages_v6";
    private static final String CH_CALLS = "family_calls_v6";
    private static final int FG_ID = 7501;

    private volatile boolean running = false;
    private volatile HttpURLConnection activeConnection;
    private volatile HttpURLConnection activePresenceConnection;
    private Thread worker;
    private Thread newsWorker;
    private Thread familyNewsWorker;
    private Thread presenceSenderWorker;
    private Thread presenceListenerWorker;
    private Thread updateWorker;
    private final Map<String, Set<Integer>> chatChunks = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startAsForeground();
        running = true;
        startWorker();
        startNewsWorker();
        startFamilyNewsWorker();
        startPresenceSender();
        startPresenceListener();
        startUpdateWorker();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_PRESENCE_CHANGE.equals(intent.getAction())) {
            final boolean visible = intent.getBooleanExtra("visible", false);
            new Thread(() -> sendPresenceHeartbeat(visible), "OurFamilyPresenceChange").start();
        }
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
            try { if (activePresenceConnection != null) activePresenceConnection.disconnect(); } catch (Exception ignored) {}
        }
        if (worker == null || !worker.isAlive()) startWorker();
        if (newsWorker == null || !newsWorker.isAlive()) startNewsWorker();
        if (familyNewsWorker == null || !familyNewsWorker.isAlive()) startFamilyNewsWorker();
        if (presenceSenderWorker == null || !presenceSenderWorker.isAlive()) startPresenceSender();
        if (presenceListenerWorker == null || !presenceListenerWorker.isAlive()) startPresenceListener();
        if (updateWorker == null || !updateWorker.isAlive()) startUpdateWorker();
        return START_STICKY;
    }

    private void startWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::listenLoop, "OurFamilyV5Relay");
        worker.start();
    }

    private void startPresenceSender() {
        if (presenceSenderWorker != null && presenceSenderWorker.isAlive()) return;
        presenceSenderWorker = new Thread(() -> {
            while (running) {
                if (appVisible) sendPresenceHeartbeat(true);
                sleep(30_000L);
            }
        }, "OurFamilyPresenceSender");
        presenceSenderWorker.start();
    }

    private void startPresenceListener() {
        if (presenceListenerWorker != null && presenceListenerWorker.isAlive()) return;
        presenceListenerWorker = new Thread(this::presenceListenLoop, "OurFamilyPresenceListener");
        presenceListenerWorker.start();
    }

    private void presenceListenLoop() {
        while (running) {
            SharedPreferences prefs = SecureStore.prefs(this);
            String code = SecureStore.familyCode(this);
            String relay = SecureStore.relay(this);
            if (code.isEmpty() || relay.isEmpty()) {
                sleep(500L);
                continue;
            }
            if ("ntfy.sh".equalsIgnoreCase(Uri.parse(relay).getHost())) {
                sleep(30_000L);
                continue;
            }

            String topic = FamilyDirectory.presenceTopic(code);
            String cursorKey = relayCursorKey("presence", relay, topic);
            long cursor = prefs.getLong(cursorKey, 0L);
            long nextCursor = cursor;
            BufferedReader reader = null;
            HttpURLConnection c = null;
            try {
                URL url = new URL(relay + "/" + topic + "/json?since=2m&after=" + cursor + "&wait=25");
                c = (HttpURLConnection) url.openConnection();
                activePresenceConnection = c;
                c.setConnectTimeout(12000);
                c.setReadTimeout(32000);
                c.setUseCaches(false);
                c.setRequestProperty("Accept", "application/x-ndjson");
                c.connect();

                reader = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    try {
                        JSONObject o = new JSONObject(line);
                        nextCursor = Math.max(nextCursor, o.optLong("seq", 0L));
                        if (!"message".equals(o.optString("event"))) continue;
                        long serverTime = o.optLong("time", 0L) * 1000L;
                        if (serverTime <= 0L || Math.abs(System.currentTimeMillis() - serverTime) > 70_000L) continue;
                        String msg = o.optString("message", "");
                        if (!msg.startsWith("of5presence|")) continue;
                        String[] p = msg.split("\\|", 4);
                        if (p.length < 4) continue;
                        String member = FamilyDirectory.roleFromTag(code, p[1]);
                        if (!member.isEmpty()) PresenceStore.updateLive(this, member,
                                "on".equals(p[3]) ? serverTime : 0L);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
            } finally {
                if (nextCursor > cursor) prefs.edit().putLong(cursorKey, nextCursor).apply();
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
                activePresenceConnection = null;
            }
            sleep(80L);
        }
    }

    private void startUpdateWorker() {
        if (updateWorker != null && updateWorker.isAlive()) return;
        updateWorker = new Thread(() -> {
            while (running) {
                try { UpdateManager.checkBackground(MessagingService.this); } catch (Exception ignored) {}
                sleep(15L * 60L * 1000L);
            }
        }, "OurFamilyUpdateWatch");
        updateWorker.start();
    }

    private void sendPresenceHeartbeat(boolean visible) {
        String relay = SecureStore.relay(this);
        // The public service limits publishers to 250 messages/day. Even one
        // heartbeat every two minutes would exhaust that quota on its own.
        try {
            Uri uri = Uri.parse(relay);
            if ("ntfy.sh".equalsIgnoreCase(uri.getHost())) return;
            String code = SecureStore.familyCode(this);
            String role = SecureStore.role(this);
            if (code.isEmpty() || !FamilyDirectory.validRole(role)) return;
            String topic = FamilyDirectory.presenceTopic(code);
            String wire = "of5presence|" + FamilyDirectory.tag(code, role) + "|" +
                    System.currentTimeMillis() + "|" + (visible ? "on" : "off");
            if ("OK".equals(NativeRelayTransport.postOnce(this, topic, wire, 1)))
                PresenceStore.updateLive(this, role, visible ? System.currentTimeMillis() : 0L);
        } catch (Exception ignored) {}
    }

    private void startNewsWorker() {
        if (newsWorker != null && newsWorker.isAlive()) return;
        newsWorker = new Thread(() -> {
            while (running) {
                try { PositiveNewsFetcher.checkAndStore(MessagingService.this); } catch (Exception ignored) {}
                sleep(5L * 60L * 1000L);
            }
        }, "OurFamilyPositiveNews");
        newsWorker.start();
    }

    private void startFamilyNewsWorker() {
        if (familyNewsWorker != null && familyNewsWorker.isAlive()) return;
        familyNewsWorker = new Thread(() -> {
            while (running) {
                FamilyNewsStore.sync(MessagingService.this);
                sleep(25_000L);
            }
        }, "OurFamilySharedNews");
        familyNewsWorker.start();
    }

    private void listenLoop() {
        while (running) {
            SharedPreferences prefs = SecureStore.prefs(this);
            String topic = prefs.getString("topic", "");
            String myTag = prefs.getString("sender_tag", "");
            String relay = SecureStore.relay(this);

            if (topic.isEmpty() || myTag.isEmpty()) {
                sleep(500L);
                continue;
            }

            boolean instantRelay = isInstantRelay(relay);
            String cursorKey = relayCursorKey("inbox", relay, topic);
            long cursor = instantRelay ? prefs.getLong(cursorKey, 0L) : 0L;
            long nextCursor = cursor;
            BufferedReader reader = null;
            try {
                String query = instantRelay
                        ? "/json?since=10m&after=" + cursor + "&wait=25"
                        : "/json?since=10m";
                URL url = new URL(relay + "/" + topic + query);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                activeConnection = c;
                c.setConnectTimeout(12000);
                c.setReadTimeout(instantRelay ? 32000 : 0);
                c.setUseCaches(false);
                c.setRequestProperty("Accept", "application/x-ndjson");
                c.connect();

                reader = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (instantRelay) {
                        try {
                            JSONObject event = new JSONObject(line);
                            nextCursor = Math.max(nextCursor, event.optLong("seq", 0L));
                        } catch (Exception ignored) {}
                    }
                    handleLine(line, myTag, prefs);
                }
            } catch (Exception ignored) {
            } finally {
                if (instantRelay && nextCursor > cursor) prefs.edit().putLong(cursorKey, nextCursor).apply();
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
                activeConnection = null;
            }
            sleep(instantRelay ? 80L : 1200L);
        }
    }

    private boolean isInstantRelay(String relay) {
        try {
            String host = Uri.parse(relay).getHost();
            return host != null && host.endsWith(".workers.dev");
        } catch (Exception e) {
            return false;
        }
    }

    private String relayCursorKey(String type, String relay, String topic) {
        String digest = FamilyDirectory.sha256(relay + "|" + topic);
        if (digest.length() > 24) digest = digest.substring(0, 24);
        return "relay_cursor_v611_" + type + "_" + digest;
    }

    private void handleLine(String line, String myTag, SharedPreferences prefs) {
        try {
            JSONObject obj = new JSONObject(line);
            if (!"message".equals(obj.optString("event"))) return;

            String id = obj.optString("id", "");
            if (RelayInbox.seenAndMark(this, id)) return;

            String msg = obj.optString("message", "");
            if (msg.isEmpty()) return;

            long eventTime = obj.optLong("time", 0L) * 1000L;

            if (msg.startsWith("of5presence|")) {
                try {
                    String[] pp = msg.split("\\|", 4);
                    if (pp.length == 4 && pp[2].equals(myTag)) {
                        long age = eventTime > 0L ? System.currentTimeMillis() - eventTime : 0L;
                        if (age <= 180_000L) {
                            String code = SecureStore.familyCode(this);
                            String senderRole = FamilyDirectory.roleFromTag(code, pp[1]);
                            if (!senderRole.isEmpty()) PresenceStore.update(this, senderRole, System.currentTimeMillis());
                        }
                    }
                } catch (Exception ignored) {}
                return;
            }

            RelayInbox.enqueue(this, id, eventTime, msg);

            if (msg.startsWith("of5ctl|") || msg.startsWith("of5ack|")) {
                try {
                    String[] cp = msg.split("\\|");
                    if (cp.length > 1) {
                        String code = SecureStore.familyCode(this);
                        String senderRole = FamilyDirectory.roleFromTag(code, cp[1]);
                        if (!senderRole.isEmpty()) PresenceStore.update(this, senderRole, System.currentTimeMillis());
                    }
                } catch (Exception ignored) {}
                return;
            }

            String[] p = msg.split("\\|", 9);
            if (p.length < 9 || !"of5".equals(p[0])) return;

            String senderTag = p[1];
            String recipientTag = p[2];
            String kind = p[3];
            String callId = p[4];
            String groupId = p[5];
            String chunkIndex = p[6];
            String totalChunks = p[7];

            if (!recipientTag.equals(myTag) || senderTag.equals(myTag)) return;

            String code = SecureStore.familyCode(this);
            if (code.isEmpty()) return;
            String senderRole = FamilyDirectory.roleFromTag(code, senderTag);
            if (senderRole.isEmpty()) return;
            PresenceStore.update(this, senderRole, System.currentTimeMillis());

            long age = eventTime > 0 ? System.currentTimeMillis() - eventTime : 0L;

            if ("direct_chat".equals(kind) || "group_chat".equals(kind)) {
                try {
                    int idx = Integer.parseInt(chunkIndex);
                    int total = Integer.parseInt(totalChunks);
                    String chunkKey = senderTag + "|" + groupId;
                    Set<Integer> got = chatChunks.get(chunkKey);
                    if (got == null) {
                        got = new HashSet<>();
                        chatChunks.put(chunkKey, got);
                    }
                    got.add(idx);
                    if (total > 0 && got.size() >= total) {
                        chatChunks.remove(chunkKey);
                        String myRole = SecureStore.role(this);
                        if (!callId.isEmpty() && !"-".equals(callId) && !myRole.isEmpty()) {
                            sendNativeDelivered(code, senderRole, myRole, senderTag, myTag, callId);
                        }
                    }
                } catch (Exception ignored) {}
                if ("0".equals(chunkIndex)) notifyMessage(senderRole, id, "Новое семейное сообщение", callId, kind);
            } else if ("direct_invite".equals(kind) || "group_invite".equals(kind) ||
                    "direct_audio_invite".equals(kind) || "group_audio_invite".equals(kind)) {
                if (!"0".equals(chunkIndex) || age > 120000L || callId.isEmpty()) return;
                String callSeenKey = "v5_call_notified_" + callId;
                if (prefs.getBoolean(callSeenKey, false)) return;
                prefs.edit().putBoolean(callSeenKey, true).apply();

                boolean group = kind.startsWith("group_");
                boolean audio = kind.contains("audio");
                notifyIncomingCall(senderRole, callId,
                        group ? (audio ? "group_audio" : "group_video") : (audio ? "audio" : "video"),
                        id);
            } else if ("news_post".equals(kind)) {
                if ("0".equals(chunkIndex)) notifyMessage(senderRole, id, "Новая семейная новость", "", "news");
            } else if ("admin_copy".equals(kind) && "sergey".equals(SecureStore.role(this))) {
                if ("0".equals(chunkIndex)) notifyMessage(senderRole, id, "Новое сообщение в семейном архиве", "", "archive");
            }
        } catch (Exception ignored) {}
    }

    private void sendNativeDelivered(
            String code, String senderRole, String myRole,
            String senderTag, String myTag, String messageId) {
        try {
            String token = FamilyDirectory.controlToken(code, messageId, "delivered", myRole, senderRole);
            String wire = "of5ack|" + myTag + "|" + senderTag + "|" + messageId + "|delivered|" + token;
            String topic = FamilyDirectory.inboxTopic(code, senderRole);
            NativeRelayTransport.post(this, topic, wire, 2);
        } catch (Exception ignored) {}
    }

    private void notifyMessage(String senderRole, String eventId, String text, String messageId, String messageKind) {
        Intent open = new Intent(this, MainActivity.class);
        if (messageId != null && !messageId.isEmpty() && !"-".equals(messageId)) {
            open.putExtra("open_message_id", messageId);
            open.putExtra("open_sender_role", senderRole);
            open.putExtra("open_message_kind", messageKind == null ? "" : messageKind);
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, eventId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_MESSAGES)
                : new Notification.Builder(this);

        Notification n = b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(FamilyDirectory.name(senderRole))
                .setContentText(text)
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
        // Video calls use our own visible, lock-screen-safe answer UI.
        if ((kind == null || !kind.contains("video")) &&
                TelecomCallManager.reportIncomingCall(this, callerRole, callId, kind)) return;
        int notificationId = 9000 + Math.abs(callId.hashCode() % 900);
        boolean group = kind.startsWith("group_");
        boolean audio = kind.contains("audio");

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
        accept.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

        String label = group
                ? (audio ? "Групповой аудиозвонок" : "Групповой видеозвонок")
                : (audio ? "Аудиозвонок" : "Видеозвонок");

        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(FamilyDirectory.name(callerRole) + " звонит")
                .setContentText(label)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setTimeoutAfter(70000);

        if (Build.VERSION.SDK_INT >= 31) {
            Person caller = new Person.Builder()
                    .setName(FamilyDirectory.name(callerRole))
                    .setImportant(true)
                    .build();
            b.setStyle(Notification.CallStyle.forIncomingCall(caller, declinePi, acceptPi));
            b.addPerson(caller);
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
        service.setDescription("Получение семейных сообщений и звонков");
        nm.createNotificationChannel(service);

        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel calls = new NotificationChannel(
                CH_CALLS, "Семейные звонки", NotificationManager.IMPORTANCE_MAX);
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
                .setContentTitle("Наша семья 6.0.15")
                .setContentText("Фоновая связь и статус в сети включены")
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
        try { if (activePresenceConnection != null) activePresenceConnection.disconnect(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
