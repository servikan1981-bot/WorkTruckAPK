from pathlib import Path


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, text):
    Path(path).write_text(text, encoding='utf-8')


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one match, got {count}: {old[:110]!r}')
    write(path, text.replace(old, new, 1))


# Fast call signaling remains end-to-end encrypted exactly like the home relay traffic.
# Only ephemeral WebRTC/control packets use the fast relay as the primary route. The same
# encrypted packet is mirrored to the owner-controlled home relay in the background.
transport = 'duoapp/src/main/java/com/sergey/duochat/NativeRelayTransport.java'
text = read(transport)
text = text.replace('import java.nio.charset.StandardCharsets;\n',
                    'import java.nio.charset.StandardCharsets;\nimport java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n', 1)
text = text.replace('''public final class NativeRelayTransport {
    private NativeRelayTransport() {}
''', '''public final class NativeRelayTransport {
    public static final String FAST_CALL_RELAY = "https://our-family-relay.family-860c7981b2d4.workers.dev";
    private static final ExecutorService CALL_MIRROR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "OurFamilyCallHomeMirror");
        t.setDaemon(true);
        return t;
    });

    private NativeRelayTransport() {}
''', 1)

old = '''    public static String post(Context context, String topic, String message, int priority) {
        String first = postAttempt(context, topic, message, priority);'''
new = '''    public static String post(Context context, String topic, String message, int priority) {
        if (priority >= 4 && isCallWire(message)) {
            String fast = postAttempt(context, topic, message, priority, FAST_CALL_RELAY);
            if ("OK".equals(fast)) {
                mirrorSingleToHome(context, topic, message, priority);
                return "OK";
            }
        }
        String first = postAttempt(context, topic, message, priority);'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: public post marker not found')
text = text.replace(old, new, 1)

old = '''    private static String postBatch(Context context, String topic, JSONArray messages, int priority) {
        String first = postBatchAttempt(context, topic, messages, priority);'''
new = '''    private static String postBatch(Context context, String topic, JSONArray messages, int priority) {
        if (priority >= 4 && messages.length() > 0 && isCallWire(messages.optString(0, ""))) {
            String fast = postBatchAttempt(context, topic, messages, priority, FAST_CALL_RELAY);
            if ("OK".equals(fast)) {
                mirrorBatchToHome(context, topic, messages, priority);
                return "OK";
            }
        }
        String first = postBatchAttempt(context, topic, messages, priority);'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: postBatch marker not found')
text = text.replace(old, new, 1)

old = '''    private static String postBatchAttempt(Context context, String topic, JSONArray messages, int priority) {
        if (context == null || topic == null || !topic.matches("[a-zA-Z0-9_-]{12,100}")) return "ERR:topic";
        String relay = SecureStore.relay(context);
        if (relay == null || !relay.startsWith("https://")) relay = SecureStore.DEFAULT_RELAY;'''
new = '''    private static String postBatchAttempt(Context context, String topic, JSONArray messages, int priority) {
        return postBatchAttempt(context, topic, messages, priority, null);
    }

    private static String postBatchAttempt(Context context, String topic, JSONArray messages, int priority,
                                           String relayOverride) {
        if (context == null || topic == null || !topic.matches("[a-zA-Z0-9_-]{12,100}")) return "ERR:topic";
        String relay = relayOverride != null ? relayOverride : SecureStore.relay(context);
        if (relay == null || !relay.startsWith("https://")) relay = SecureStore.DEFAULT_RELAY;'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: batch attempt overload marker not found')
text = text.replace(old, new, 1)

marker = '''    private static boolean retryable(String result) {
'''
helpers = '''    private static boolean isCallWire(String message) {
        if (message == null || message.isEmpty()) return false;
        if (message.startsWith("of5ctl|")) return true;
        if (!message.startsWith("of5|")) return false;
        String[] parts = message.split("\\\\|", 9);
        if (parts.length < 4) return false;
        String kind = parts[3];
        return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                (kind.startsWith("group_") && !"group_chat".equals(kind));
    }

    private static void mirrorSingleToHome(Context context, String topic, String message, int priority) {
        Context app = context.getApplicationContext();
        CALL_MIRROR.execute(() -> postAttempt(app, topic, message, priority, null));
    }

    private static void mirrorBatchToHome(Context context, String topic, JSONArray messages, int priority) {
        Context app = context.getApplicationContext();
        JSONArray copy;
        try { copy = new JSONArray(messages.toString()); }
        catch (Exception e) { return; }
        CALL_MIRROR.execute(() -> postBatchAttempt(app, topic, copy, priority, null));
    }

'''
if text.count(marker) != 1:
    raise SystemExit('NativeRelayTransport: retry helper marker not found')
text = text.replace(marker, helpers + marker, 1)
write(transport, text)


# Dedicated listener for the fast relay. It deliberately accepts only call signaling,
# so chats/news/files remain on the owner-controlled home relay. Relay cursor includes
# the relay URL, therefore the two streams cannot corrupt each other's sequence position.
svc = 'duoapp/src/main/java/com/sergey/duochat/MessagingService.java'
text = read(svc)
text = text.replace('''    private volatile HttpURLConnection activeConnection;
    private volatile HttpURLConnection activePresenceConnection;
    private Thread worker;''', '''    private volatile HttpURLConnection activeConnection;
    private volatile HttpURLConnection activeFastCallConnection;
    private volatile HttpURLConnection activePresenceConnection;
    private Thread worker;
    private Thread fastCallWorker;''', 1)
text = text.replace('''        startWorker();
        startNewsWorker();''', '''        startWorker();
        startFastCallWorker();
        startNewsWorker();''', 1)
text = text.replace('''            try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
            try { if (activePresenceConnection != null) activePresenceConnection.disconnect(); } catch (Exception ignored) {}''', '''            try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
            try { if (activeFastCallConnection != null) activeFastCallConnection.disconnect(); } catch (Exception ignored) {}
            try { if (activePresenceConnection != null) activePresenceConnection.disconnect(); } catch (Exception ignored) {}''', 1)
text = text.replace('''        if (worker == null || !worker.isAlive()) startWorker();
        if (newsWorker == null || !newsWorker.isAlive()) startNewsWorker();''', '''        if (worker == null || !worker.isAlive()) startWorker();
        if (fastCallWorker == null || !fastCallWorker.isAlive()) startFastCallWorker();
        if (newsWorker == null || !newsWorker.isAlive()) startNewsWorker();''', 1)

insert_after = '''    private void startWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::listenLoop, "OurFamilyV5Relay");
        worker.start();
    }
'''
fast_methods = insert_after + '''
    private void startFastCallWorker() {
        if (fastCallWorker != null && fastCallWorker.isAlive()) return;
        fastCallWorker = new Thread(this::fastCallListenLoop, "OurFamilyFastCallRelay");
        fastCallWorker.start();
    }

    private void fastCallListenLoop() {
        final String relay = NativeRelayTransport.FAST_CALL_RELAY;
        while (running) {
            SharedPreferences prefs = SecureStore.prefs(this);
            String topic = prefs.getString("topic", "");
            String myTag = prefs.getString("sender_tag", "");
            String selected = SecureStore.relay(this);
            if (topic.isEmpty() || myTag.isEmpty()) { sleep(400L); continue; }
            if (relay.equalsIgnoreCase(selected == null ? "" : selected.replaceAll("/+$", ""))) {
                sleep(1000L); continue;
            }

            String cursorKey = relayCursorKey("fast_call", relay, topic);
            long cursor = prefs.getLong(cursorKey, 0L);
            long nextCursor = cursor;
            BufferedReader reader = null;
            HttpURLConnection c = null;
            try {
                URL url = new URL(relay + "/" + topic + "/json?since=2m&after=" + cursor + "&wait=25");
                c = (HttpURLConnection) url.openConnection();
                activeFastCallConnection = c;
                c.setConnectTimeout(6000);
                c.setReadTimeout(32000);
                c.setUseCaches(false);
                c.setRequestProperty("Accept", "application/x-ndjson");
                c.setRequestProperty("User-Agent", "OurFamily/6.0.30 FastCall");
                c.connect();

                reader = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while (running && (line = reader.readLine()) != null) {
                    try {
                        JSONObject event = new JSONObject(line);
                        nextCursor = Math.max(nextCursor, event.optLong("seq", 0L));
                    } catch (Exception ignored) {}
                    if (isCallSignalLine(line)) handleLine(line, myTag, prefs);
                }
            } catch (Exception ignored) {
            } finally {
                if (nextCursor > cursor) prefs.edit().putLong(cursorKey, nextCursor).apply();
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
                activeFastCallConnection = null;
            }
            sleep(60L);
        }
    }

    private boolean isCallSignalLine(String line) {
        try {
            JSONObject event = new JSONObject(line);
            if (!"message".equals(event.optString("event"))) return false;
            String msg = event.optString("message", "");
            if (msg.startsWith("of5ctl|")) return true;
            if (!msg.startsWith("of5|")) return false;
            String[] p = msg.split("\\\\|", 9);
            if (p.length < 4) return false;
            String kind = p[3];
            return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                    (kind.startsWith("group_") && !"group_chat".equals(kind));
        } catch (Exception ignored) { return false; }
    }
'''
if text.count(insert_after) != 1:
    raise SystemExit('MessagingService: startWorker insertion marker not found')
text = text.replace(insert_after, fast_methods, 1)

old = '''        try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
        try { if (activePresenceConnection != null) activePresenceConnection.disconnect(); } catch (Exception ignored) {}
        super.onDestroy();'''
new = '''        try { if (activeConnection != null) activeConnection.disconnect(); } catch (Exception ignored) {}
        try { if (activeFastCallConnection != null) activeFastCallConnection.disconnect(); } catch (Exception ignored) {}
        try { if (activePresenceConnection != null) activePresenceConnection.disconnect(); } catch (Exception ignored) {}
        super.onDestroy();'''
if text.count(old) != 1:
    raise SystemExit('MessagingService: onDestroy marker not found')
text = text.replace(old, new, 1)
write(svc, text)

print('Applied OurFamily 6.0.30 dual fast-call relay patch')
