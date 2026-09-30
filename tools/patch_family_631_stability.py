from pathlib import Path


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, text):
    Path(path).write_text(text, encoding='utf-8')


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one match, got {count}: {old[:140]!r}')
    write(path, text.replace(old, new, 1))


# Version identity. This patch is applied after all 6.0.30 patches.
replace_once('duoapp/build.gradle', "versionCode 6030\n        versionName '6.0.30'", "versionCode 6031\n        versionName '6.0.31'")
replace_once('duoapp/src/main/AndroidManifest.xml', 'android:label="Наша семья 6.0.30"', 'android:label="Наша семья 6.0.31"')
html = 'duoapp/src/main/assets/index.html'
text = read(html).replace("APP_VERSION='6.0.30'", "APP_VERSION='6.0.31'")
text = text.replace('Наша семья · v6.0.30', 'Наша семья · v6.0.31')
write(html, text)


# --- Realtime transport hardening -------------------------------------------------
# 6.0.30 waited for the Cloudflare relay before it even started the home-relay mirror.
# If the fast endpoint is blocked/slow (VPN, carrier, DNS), call setup can stall for many seconds.
# 6.0.31 sends encrypted realtime packets to BOTH relays immediately and returns as soon as one wins.
transport = 'duoapp/src/main/java/com/sergey/duochat/NativeRelayTransport.java'
text = read(transport)

text = text.replace('import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n',
                    'import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\nimport java.util.concurrent.Future;\nimport java.util.concurrent.TimeUnit;\n', 1)

old = '''    public static String post(Context context, String topic, String message, int priority) {
        if (priority >= 4 && isCallWire(message)) {
            String fast = postAttempt(context, topic, message, priority, FAST_CALL_RELAY);
            if ("OK".equals(fast)) {
                mirrorSingleToHome(context, topic, message, priority);
                return "OK";
            }
        }
        String first = postAttempt(context, topic, message, priority);'''
new = '''    public static String post(Context context, String topic, String message, int priority) {
        if (priority >= 4 && isRealtimeWire(message)) {
            String hedged = postHedgedRealtime(context, topic, message, priority);
            if ("OK".equals(hedged)) return "OK";
        }
        String first = postAttempt(context, topic, message, priority);'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: 6.0.30 single fast path not found')
text = text.replace(old, new, 1)

old = '''    private static String postBatch(Context context, String topic, JSONArray messages, int priority) {
        if (priority >= 4 && messages.length() > 0 && isCallWire(messages.optString(0, ""))) {
            String fast = postBatchAttempt(context, topic, messages, priority, FAST_CALL_RELAY);
            if ("OK".equals(fast)) {
                mirrorBatchToHome(context, topic, messages, priority);
                return "OK";
            }
        }
        String first = postBatchAttempt(context, topic, messages, priority);'''
new = '''    private static String postBatch(Context context, String topic, JSONArray messages, int priority) {
        if (priority >= 4 && messages.length() > 0 && isRealtimeWire(messages.optString(0, ""))) {
            String hedged = postBatchHedgedRealtime(context, topic, messages, priority);
            if ("OK".equals(hedged)) return "OK";
        }
        String first = postBatchAttempt(context, topic, messages, priority);'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: 6.0.30 batch fast path not found')
text = text.replace(old, new, 1)

old = '''    private static boolean isCallWire(String message) {
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
new = '''    private static boolean isRealtimeWire(String message) {
        if (message == null || message.isEmpty()) return false;
        if (message.startsWith("of5ctl|")) return true;
        if (!message.startsWith("of5|")) return false;
        String[] parts = message.split("\\\\|", 9);
        if (parts.length < 4) return false;
        String kind = parts[3];
        return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                (kind.startsWith("group_") && !"group_chat".equals(kind)) ||
                kind.startsWith("durak_") || kind.startsWith("game_");
    }

    private static String postHedgedRealtime(Context context, String topic, String message, int priority) {
        Context app = context.getApplicationContext();
        Future<String> home = CALL_MIRROR.submit(() -> postAttempt(app, topic, message, priority, null));
        String fast = postAttempt(app, topic, message, priority, FAST_CALL_RELAY);
        if ("OK".equals(fast)) return "OK";
        try {
            String h = home.get(4500L, TimeUnit.MILLISECONDS);
            if ("OK".equals(h)) return "OK";
        } catch (Exception ignored) {}
        return fast == null ? "ERR:realtime" : fast;
    }

    private static String postBatchHedgedRealtime(Context context, String topic, JSONArray messages, int priority) {
        Context app = context.getApplicationContext();
        JSONArray copy;
        try { copy = new JSONArray(messages.toString()); }
        catch (Exception e) { return "ERR:batch-copy"; }
        Future<String> home = CALL_MIRROR.submit(() -> postBatchAttempt(app, topic, copy, priority, null));
        String fast = postBatchAttempt(app, topic, messages, priority, FAST_CALL_RELAY);
        if ("OK".equals(fast)) return "OK";
        try {
            String h = home.get(4500L, TimeUnit.MILLISECONDS);
            if ("OK".equals(h)) return "OK";
        } catch (Exception ignored) {}
        return fast == null ? "ERR:realtime-batch" : fast;
    }

'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: 6.0.30 call helper block not found')
text = text.replace(old, new, 1)

# The fast endpoint is expected to answer in a few hundred ms. Do not let a blocked VPN/domain
# hold signaling hostage for the generic transport timeout. This replacement is scoped only to
# relayOverride (the fast path); the home path keeps the original conservative timeout.
old = '''            c.setConnectTimeout(priority >= 4 ? 7000 : 12000);
            c.setReadTimeout(priority >= 4 ? 9000 : 18000);'''
new = '''            boolean fastRealtime = FAST_CALL_RELAY.equals(relay);
            c.setConnectTimeout(fastRealtime ? 2500 : (priority >= 4 ? 7000 : 12000));
            c.setReadTimeout(fastRealtime ? 3000 : (priority >= 4 ? 9000 : 18000));'''
if text.count(old) < 1:
    raise SystemExit('NativeRelayTransport: timeout marker not found')
text = text.replace(old, new)
write(transport, text)


# --- Fast receive path also carries game events ----------------------------------
svc = 'duoapp/src/main/java/com/sergey/duochat/MessagingService.java'
text = read(svc)
text = text.replace('OurFamily/6.0.30 FastCall', 'OurFamily/6.0.31 Realtime')
text = text.replace('if (isCallSignalLine(line)) handleLine(line, myTag, prefs);',
                    'if (isRealtimeSignalLine(line)) handleLine(line, myTag, prefs);', 1)
text = text.replace('private boolean isCallSignalLine(String line) {',
                    'private boolean isRealtimeSignalLine(String line) {', 1)
old = '''            return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                    (kind.startsWith("group_") && !"group_chat".equals(kind));'''
new = '''            return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                    (kind.startsWith("group_") && !"group_chat".equals(kind)) ||
                    kind.startsWith("durak_") || kind.startsWith("game_");'''
if text.count(old) != 1:
    raise SystemExit('MessagingService: realtime filter marker not found')
text = text.replace(old, new, 1)
write(svc, text)


# --- Call signaling watchdog -----------------------------------------------------
# Never leave the user forever on "Вызов…" when both relays are unreachable.
# Retry the same encrypted invite (same callId, therefore duplicate-safe) and then fail clearly.
text = read(html)
needle = '''async function makeOffer(peer){
'''
if text.count(needle) != 1:
    raise SystemExit('index.html: makeOffer marker not found')

# publishEnvelope itself now returns quickly when either relay succeeds. Add a deterministic
# retry around offer publication; the receive side already ignores duplicate callIds safely.
old = '''await publishEnvelope(peer,k,{callId:activeCall.id,sdp:sdp},5,k,activeCall.id);noteCall('offer отправлен');'''
new = '''try{await publishEnvelope(peer,k,{callId:activeCall.id,sdp:sdp},5,k,activeCall.id);noteCall('offer отправлен');}
catch(e){noteCall('сигнал: повтор');await new Promise(function(r){setTimeout(r,500);});
try{await publishEnvelope(peer,k,{callId:activeCall.id,sdp:sdp},5,k,activeCall.id);noteCall('offer отправлен повторно');}
catch(e2){noteCall('сигнал недоступен');toast('Нет связи с сервером. Проверьте интернет/VPN и повторите вызов.');throw e2;}}'''
if text.count(old) != 1:
    raise SystemExit('index.html: offer publish marker not found')
text = text.replace(old, new, 1)

# Incoming answer publication gets the same one-fast retry instead of silently hanging.
old = '''await publishEnvelope(peer,k,{callId:activeCall.id,sdp:sdp},5,k,activeCall.id);noteCall('answer отправлен');'''
new = '''try{await publishEnvelope(peer,k,{callId:activeCall.id,sdp:sdp},5,k,activeCall.id);noteCall('answer отправлен');}
catch(e){noteCall('answer: повтор');await new Promise(function(r){setTimeout(r,500);});
await publishEnvelope(peer,k,{callId:activeCall.id,sdp:sdp},5,k,activeCall.id);noteCall('answer отправлен повторно');}'''
if text.count(old) == 1:
    text = text.replace(old, new, 1)

write(html, text)

print('Applied OurFamily 6.0.31 stability patch: hedged dual relay, realtime games, bounded signaling retry')
