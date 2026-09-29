from pathlib import Path
import re


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)

# Version identity.
p = Path('duoapp/build.gradle')
s = p.read_text()
s = must_replace(s, "versionCode 6029", "versionCode 6030", 'versionCode')
s = must_replace(s, "versionName '6.0.29'", "versionName '6.0.30'", 'versionName')
p.write_text(s)

p = Path('duoapp/src/main/AndroidManifest.xml')
s = p.read_text()
s = re.sub(r'android:label="Наша семья 6\.0\.(?:20|29)"', 'android:label="Наша семья 6.0.30"', s, count=1)
p.write_text(s)

# Preserve strict order for all durable game events. Four workers could deliver
# seq N+1 before seq N; the receiver intentionally rejects gaps.
p = Path('duoapp/src/main/java/com/sergey/duochat/ReliableRelayOutbox.java')
s = p.read_text()
s = must_replace(
    s,
    'private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, r -> {',
    'private static final ExecutorService WORKERS = Executors.newSingleThreadExecutor(r -> {',
    'single ordered reliable sender')
s = must_replace(
    s,
    'for (int i = 0; i < 4; i++) WORKERS.execute(() -> drain(app));',
    'WORKERS.execute(() -> drain(app));',
    'single drain')
p.write_text(s)

# Native game notifications get their own high-importance channel and pending
# durable events are resumed as soon as the foreground service starts.
p = Path('duoapp/src/main/java/com/sergey/duochat/MessagingService.java')
s = p.read_text()
s = must_replace(
    s,
    'private static final String CH_MESSAGES = "family_messages_v6";\n    static final String CH_CALLS = "family_calls_v6";',
    'private static final String CH_MESSAGES = "family_messages_v6";\n    private static final String CH_GAMES = "family_games_v630";\n    static final String CH_CALLS = "family_calls_v6";',
    'game channel constant')
s = must_replace(
    s,
    'running = true;\n        startWorker();',
    'running = true;\n        ReliableRelayOutbox.kick(this);\n        startWorker();',
    'resume reliable outbox')

start = s.index('    private void notifyCheckers(')
end = s.index('    private void notifyIncomingCall(', start)
segment = s[start:end]
segment = must_replace(
    segment,
    'new Notification.Builder(this, CH_MESSAGES) : new Notification.Builder(this);',
    'new Notification.Builder(this, CH_GAMES) : new Notification.Builder(this);',
    'game notification channel')
segment = must_replace(
    segment,
    '.setContentIntent(view).setAutoCancel(true)\n                .setCategory(Notification.CATEGORY_MESSAGE).setPriority(Notification.PRIORITY_HIGH);',
    '.setContentIntent(view).setAutoCancel(true)\n                .setCategory(Notification.CATEGORY_MESSAGE)\n                .setVisibility(Notification.VISIBILITY_PUBLIC)\n                .setPriority(Notification.PRIORITY_MAX);',
    'game heads-up priority')
s = s[:start] + segment + s[end:]

channel_anchor = '''        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);'''
channel_replacement = '''        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

        Uri gameSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes gameAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        NotificationChannel games = new NotificationChannel(
                CH_GAMES, "Семейные игры", NotificationManager.IMPORTANCE_HIGH);
        games.setDescription("Приглашения в игры и уведомления о ходе");
        games.enableVibration(true);
        games.enableLights(true);
        games.setLightColor(Color.GREEN);
        games.setSound(gameSound, gameAttrs);
        games.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(games);

        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);'''
s = must_replace(s, channel_anchor, channel_replacement, 'create game channel')
s = s.replace('setContentTitle("Наша семья 6.0.20")', 'setContentTitle("Наша семья 6.0.30")')
p.write_text(s)

# When a user taps a Durak invite notification before WebView has decrypted the
# invite, consumeNativeNavigation waits for the game object. Checkers already
# wakes that navigation path when the invite arrives; Durak must do the same.
p = Path('duoapp/src/main/assets/index.html')
s = p.read_text()
s = must_replace(s, '<title>Наша семья 6.0.29</title>', '<title>Наша семья 6.0.30</title>', 'html title')
s = must_replace(s, "var APP_VERSION='6.0.29'", "var APP_VERSION='6.0.30'", 'app version')
s = must_replace(
    s,
    'pendingDurakAcceptId=id;showDurakRequest();return;',
    'pendingDurakAcceptId=id;showDurakRequest();tryOpenPendingMessage();return;',
    'Durak notification navigation wakeup')
p.write_text(s)

print('6.0.30 games patch applied')
