from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


# Version identity.
gradle = Path("duoapp/build.gradle")
s = gradle.read_text(encoding="utf-8")
s = replace_once(s, "versionCode 6029", "versionCode 6030", "versionCode")
s = replace_once(s, "versionName '6.0.29'", "versionName '6.0.30'", "versionName")
gradle.write_text(s, encoding="utf-8")

manifest = Path("duoapp/src/main/AndroidManifest.xml")
s = manifest.read_text(encoding="utf-8")
if 'android:label="Наша семья 6.0.20"' in s:
    s = replace_once(s, 'android:label="Наша семья 6.0.20"', 'android:label="Наша семья 6.0.30"', "manifest label")
elif 'android:label="Наша семья 6.0.29"' in s:
    s = replace_once(s, 'android:label="Наша семья 6.0.29"', 'android:label="Наша семья 6.0.30"', "manifest label")
else:
    raise SystemExit("manifest label anchor missing")
manifest.write_text(s, encoding="utf-8")

html = Path("duoapp/src/main/assets/index.html")
s = html.read_text(encoding="utf-8")
if s.count("6.0.29") < 1:
    raise SystemExit("index.html 6.0.29 anchor missing")
s = s.replace("6.0.29", "6.0.30")
if "APP_VERSION='6.0.27'" in s:
    s = replace_once(s, "APP_VERSION='6.0.27'", "APP_VERSION='6.0.30'", "APP_VERSION")
elif "APP_VERSION='6.0.29'" in s:
    s = replace_once(s, "APP_VERSION='6.0.29'", "APP_VERSION='6.0.30'", "APP_VERSION")
elif "APP_VERSION='6.0.30'" not in s:
    raise SystemExit("APP_VERSION anchor missing")
html.write_text(s, encoding="utf-8")

# All game packets must leave the phone in exactly the order in which the game
# generated them. The old MESSAGE_POSTER had three workers, so seq N+1 could
# reach the relay before seq N and the receiver would correctly reject N+1.
main = Path("duoapp/src/main/java/com/sergey/duochat/MainActivity.java")
s = main.read_text(encoding="utf-8")
executor_anchor = '''    private static final ThreadPoolExecutor SIGNAL_POSTER = new ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), r -> {
                Thread t = new Thread(r, "OurFamilyIceSignal");
                t.setDaemon(true);
                return t;
            });
'''
executor_new = executor_anchor + '''    // Games are state machines: preserve FIFO ordering across every invite,
    // accept and move, including multi-chunk encrypted Durak state packets.
    private static final ThreadPoolExecutor GAME_POSTER = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(192), r -> {
                Thread t = new Thread(r, "OurFamilyGameSignal");
                t.setDaemon(true);
                return t;
            });
'''
s = replace_once(s, executor_anchor, executor_new, "GAME_POSTER")

async_anchor = '''                android.content.Context app = getApplicationContext();
                ThreadPoolExecutor executor = priority >= 5 ? CALL_POSTER : MESSAGE_POSTER;
                executor.execute(() -> reportRelayResult(requestId,
                        NativeRelayTransport.post(app, topic, message, priority)));
'''
async_new = '''                android.content.Context app = getApplicationContext();
                String wireKind = relayWireKind(message);
                ThreadPoolExecutor executor = isGameWireKind(wireKind)
                        ? GAME_POSTER
                        : (priority >= 5 ? CALL_POSTER : MESSAGE_POSTER);
                executor.execute(() -> reportRelayResult(requestId,
                        NativeRelayTransport.post(app, topic, message, priority)));
'''
s = replace_once(s, async_anchor, async_new, "sendRelayAsync routing")

queue_anchor = '''                String[] parts = message.split("\\\\|", 5);
                String kind = parts.length > 3 ? parts[3] : "";
                ThreadPoolExecutor executor = (kind.endsWith("invite") || kind.endsWith("accept") ||
                        kind.endsWith("join")) ? CALL_POSTER : SIGNAL_POSTER;
'''
queue_new = '''                String[] parts = message.split("\\\\|", 5);
                String kind = parts.length > 3 ? parts[3] : "";
                ThreadPoolExecutor executor = isGameWireKind(kind)
                        ? GAME_POSTER
                        : ((kind.endsWith("invite") || kind.endsWith("accept") ||
                        kind.endsWith("join")) ? CALL_POSTER : SIGNAL_POSTER);
'''
s = replace_once(s, queue_anchor, queue_new, "queueRelay routing")

helper_anchor = '''        @JavascriptInterface
        public String sendRelay(String topic, String message, int priority) {
'''
helper_new = '''        private String relayWireKind(String message) {
            if (message == null || !message.startsWith("of5|")) return "";
            String[] parts = message.split("\\\\|", 5);
            return parts.length > 3 ? parts[3] : "";
        }

        private boolean isGameWireKind(String kind) {
            return kind != null && (kind.startsWith("game_") || kind.startsWith("durak_"));
        }

''' + helper_anchor
s = replace_once(s, helper_anchor, helper_new, "game wire helpers")
main.write_text(s, encoding="utf-8")

# Give games their own fresh high-importance Android channel. This avoids old
# message-channel settings suppressing invitations after an upgrade.
service = Path("duoapp/src/main/java/com/sergey/duochat/MessagingService.java")
s = service.read_text(encoding="utf-8")
s = replace_once(
    s,
    '    private static final String CH_MESSAGES = "family_messages_v6";\n    static final String CH_CALLS = "family_calls_v6";\n',
    '    private static final String CH_MESSAGES = "family_messages_v6";\n    private static final String CH_GAMES = "family_games_v630";\n    static final String CH_CALLS = "family_calls_v6";\n',
    "CH_GAMES",
)
s = replace_once(
    s,
    '''        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_MESSAGES) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(FamilyDirectory.name(senderRole))
''',
    '''        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_GAMES) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(FamilyDirectory.name(senderRole))
''',
    "game notification channel",
)
s = replace_once(
    s,
    '''                .setContentIntent(view).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE).setPriority(Notification.PRIORITY_HIGH);
''',
    '''                .setContentIntent(view).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX);
        if (Build.VERSION.SDK_INT < 26) b.setDefaults(Notification.DEFAULT_ALL);
''',
    "game notification priority",
)
channel_anchor = '''        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
'''
channel_new = '''        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

        Uri gameSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes gameAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel games = new NotificationChannel(
                CH_GAMES, "Игры — приглашения и ходы", NotificationManager.IMPORTANCE_HIGH);
        games.setDescription("Приглашения в игры и уведомления о вашем ходе");
        games.enableVibration(true);
        games.enableLights(true);
        games.setLightColor(Color.BLUE);
        games.setSound(gameSound, gameAttrs);
        games.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(games);

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
'''
s = replace_once(s, channel_anchor, channel_new, "game notification channel creation")
s = s.replace('setContentTitle("Наша семья 6.0.20")', 'setContentTitle("Наша семья 6.0.30")')
service.write_text(s, encoding="utf-8")

print("6.0.30 game transport/notification patch applied")
