from pathlib import Path
import re


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, text):
    Path(path).write_text(text, encoding='utf-8')


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one match, got {count}: {old[:90]!r}')
    write(path, text.replace(old, new, 1))


def sub_once(path, pattern, repl, flags=0):
    text = read(path)
    out, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one regex match, got {count}: {pattern!r}')
    write(path, out)


# Release identity: build strictly on top of the published 6.0.29 branch.
replace_once('duoapp/build.gradle', "versionCode 6029", "versionCode 6030")
replace_once('duoapp/build.gradle', "versionName '6.0.29'", "versionName '6.0.30'")
sub_once('duoapp/src/main/AndroidManifest.xml', r'android:label="Наша семья 6\.0\.[0-9]+"', 'android:label="Наша семья 6.0.30"')

# Keep all visible/internal web version markers consistent and make the 1s poll only a fallback.
index = 'duoapp/src/main/assets/index.html'
text = read(index)
text = text.replace('6.0.29', '6.0.30')
text, n = re.subn(r"var APP_VERSION='6\.0\.[0-9]+'", "var APP_VERSION='6.0.30'", text, count=1)
if n != 1:
    raise SystemExit(f'{index}: APP_VERSION marker not found')
if 'setInterval(poll,1000)' in text:
    text = text.replace('setInterval(poll,1000)', 'setInterval(poll,350)', 1)
write(index, text)

# Critical game traffic must retain order. The old four-worker pool could deliver move N+1
# before move N (and could even reorder encrypted chunks), while receivers intentionally
# reject a state whose seq is not exactly previous+1.
outbox = 'duoapp/src/main/java/com/sergey/duochat/ReliableRelayOutbox.java'
replace_once(outbox,
'''    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, r -> {\n        Thread t = new Thread(r, "OurFamilyReliableSend");\n        t.setDaemon(true);\n        return t;\n    });''',
'''    private static final ExecutorService WORKERS = Executors.newSingleThreadExecutor(r -> {\n        Thread t = new Thread(r, "OurFamilyReliableSendOrdered");\n        t.setDaemon(true);\n        return t;\n    });''')
replace_once(outbox,
'''    public static void kick(Context context) {\n        Context app = context.getApplicationContext();\n        for (int i = 0; i < 4; i++) WORKERS.execute(() -> drain(app));\n    }''',
'''    public static void kick(Context context) {\n        Context app = context.getApplicationContext();\n        WORKERS.execute(() -> drain(app));\n    }''')
sub_once(outbox,
    r'    private static JSONObject claim\(Context app\) \{.*?\n    \}\n\n(?=    private static void finish)',
'''    private static JSONObject claim(Context app) {\n        synchronized (LOCK) {\n            JSONArray items = read(SecureStore.prefs(app));\n            long now = System.currentTimeMillis();\n            // Strict FIFO for critical events. If the head is waiting for a retry, do not\n            // allow a later move/chunk to overtake it.\n            for (int i = 0; i < items.length(); i++) {\n                JSONObject item = items.optJSONObject(i);\n                if (item == null) continue;\n                String id = item.optString("id", "");\n                if (id.isEmpty()) continue;\n                if (ACTIVE.contains(id)) return null;\n                if (RETRY_AFTER.getOrDefault(id, 0L) > now) return null;\n                ACTIVE.add(id);\n                return item;\n            }\n            return null;\n        }\n    }\n\n''', flags=re.S)
replace_once(outbox, 'System.currentTimeMillis() + 4000L', 'System.currentTimeMillis() + 1200L')
replace_once(outbox, '}, 4, TimeUnit.SECONDS);', '}, 1200, TimeUnit.MILLISECONDS);')
replace_once(outbox,
'''            finish(app, id, "OK".equals(result));''',
'''            boolean sent = "OK".equals(result);\n            finish(app, id, sent);\n            if (!sent) return;''')

# Wake the visible WebView immediately after native persistence. Polling remains as a safety net.
inbox = 'duoapp/src/main/java/com/sergey/duochat/RelayInbox.java'
replace_once(inbox,
'''            p.edit().putString(KEY_QUEUE, q.toString()).apply();\n        }\n    }\n\n    public static String read(Context c) {''',
'''            p.edit().putString(KEY_QUEUE, q.toString()).apply();\n        }\n        MainActivity.notifyRelayArrived();\n    }\n\n    public static String read(Context c) {''')

# Games get a fresh, dedicated high-importance channel. This avoids inherited/muted settings
# on the long-lived ordinary-message channel and makes invitations/turns visible when minimized.
svc = 'duoapp/src/main/java/com/sergey/duochat/MessagingService.java'
replace_once(svc,
'''    private static final String CH_MESSAGES = "family_messages_v6";\n    static final String CH_CALLS = "family_calls_v6";''',
'''    private static final String CH_MESSAGES = "family_messages_v6";\n    private static final String CH_GAMES = "family_games_v630";\n    static final String CH_CALLS = "family_calls_v6";''')
replace_once(svc,
'''        Notification.Builder b = Build.VERSION.SDK_INT >= 26\n                ? new Notification.Builder(this, CH_MESSAGES) : new Notification.Builder(this);\n        b.setSmallIcon(R.drawable.ic_launcher)\n                .setContentTitle(FamilyDirectory.name(senderRole))\n                .setContentText(durak ?''',
'''        Notification.Builder b = Build.VERSION.SDK_INT >= 26\n                ? new Notification.Builder(this, CH_GAMES) : new Notification.Builder(this);\n        b.setSmallIcon(R.drawable.ic_launcher)\n                .setContentTitle(FamilyDirectory.name(senderRole))\n                .setContentText(durak ?''')
replace_once(svc,
'''                .setContentIntent(view).setAutoCancel(true)\n                .setCategory(Notification.CATEGORY_MESSAGE).setPriority(Notification.PRIORITY_HIGH);''',
'''                .setContentIntent(view).setAutoCancel(true)\n                .setCategory(Notification.CATEGORY_MESSAGE)\n                .setVisibility(Notification.VISIBILITY_PUBLIC)\n                .setPriority(Notification.PRIORITY_MAX);''')
replace_once(svc,
'''        messages.enableVibration(true);\n        nm.createNotificationChannel(messages);\n\n        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);''',
'''        messages.enableVibration(true);\n        nm.createNotificationChannel(messages);\n\n        Uri gameSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);\n        AudioAttributes gameAttrs = new AudioAttributes.Builder()\n                .setUsage(AudioAttributes.USAGE_NOTIFICATION)\n                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)\n                .build();\n        NotificationChannel games = new NotificationChannel(\n                CH_GAMES, "Игры — приглашения и ходы", NotificationManager.IMPORTANCE_HIGH);\n        games.setDescription("Приглашения в семейные игры и уведомления о вашем ходе");\n        games.enableVibration(true);\n        games.enableLights(true);\n        games.setLightColor(Color.GREEN);\n        games.setSound(gameSound, gameAttrs);\n        games.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);\n        nm.createNotificationChannel(games);\n\n        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);''')
sub_once(svc, r'\.setContentTitle\("Наша семья 6\.0\.[0-9]+"\)', '.setContentTitle("Наша семья 6.0.30")')

print('Applied OurFamily 6.0.30 ordered-game-delivery and game-notification patch')
