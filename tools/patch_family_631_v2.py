from pathlib import Path


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, text):
    Path(path).write_text(text, encoding='utf-8')


def one(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    return text.replace(old, new, 1)


# Applied after patch_family_630.py + patch_family_630_call.py + patch_family_630_fastrelay.py.
# Preserve package/signing identity; only advance version.
p = 'duoapp/build.gradle'
t = read(p)
t = one(t, "versionCode 6030\n        versionName '6.0.30'", "versionCode 6031\n        versionName '6.0.31'", 'gradle version')
write(p, t)

p = 'duoapp/src/main/AndroidManifest.xml'
t = read(p)
t = one(t, 'android:label="Наша семья 6.0.30"', 'android:label="Наша семья 6.0.31"', 'manifest label')
write(p, t)

# WebRTC signaling: retry the exact SDP packet once instead of leaving the UI stuck at have-local-offer.
html_path = 'duoapp/src/main/assets/index.html'
h = read(html_path)
h = h.replace("APP_VERSION='6.0.30'", "APP_VERSION='6.0.31'")
h = h.replace('Наша семья · v6.0.30', 'Наша семья · v6.0.31')

old = """   await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);
   markCall('offer_sent');noteCall('offer отправлен');"""
new = """   try{
     await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);
     markCall('offer_sent');noteCall('offer отправлен');
   }catch(e){
     noteCall('сигнал: повтор');await new Promise(function(r){setTimeout(r,400);});
     try{
       await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);
       markCall('offer_sent');noteCall('offer отправлен повторно');
     }catch(e2){
       noteCall('сигнал недоступен');toast('Нет связи с сервером. Проверьте интернет или VPN и повторите вызов.');throw e2;
     }
   }"""
h = one(h, old, new, 'offer retry')

old = """   await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);
   markCall('answer_sent');noteCall('answer отправлен');"""
new = """   try{
     await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);
     markCall('answer_sent');noteCall('answer отправлен');
   }catch(e){
     noteCall('answer: повтор');await new Promise(function(r){setTimeout(r,400);});
     await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);
     markCall('answer_sent');noteCall('answer отправлен повторно');
   }"""
h = one(h, old, new, 'answer retry')
write(html_path, h)


# Realtime transport: calls and games go to BOTH relays immediately.
# This removes the 6.0.30 failure mode where a blocked/slow Cloudflare endpoint delayed the home relay.
transport = 'duoapp/src/main/java/com/sergey/duochat/NativeRelayTransport.java'
t = read(transport)
t = one(t,
        'import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n',
        'import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\nimport java.util.concurrent.Future;\n',
        'future import')
t = one(t, 'Executors.newFixedThreadPool(2, r -> {', 'Executors.newFixedThreadPool(4, r -> {', 'realtime pool')

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
            return postHedgedRealtime(context, topic, message, priority);
        }
        String first = postAttempt(context, topic, message, priority);'''
t = one(t, old, new, 'single realtime path')

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
            return postBatchHedgedRealtime(context, topic, messages, priority);
        }
        String first = postBatchAttempt(context, topic, messages, priority);'''
t = one(t, old, new, 'batch realtime path')

t = one(t, '    private static boolean isCallWire(String message) {',
        '    private static boolean isRealtimeWire(String message) {', 'realtime method name')
old = '''        return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                (kind.startsWith("group_") && !"group_chat".equals(kind));'''
new = '''        return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                (kind.startsWith("group_") && !"group_chat".equals(kind)) ||
                kind.startsWith("durak_") || kind.startsWith("game_");'''
t = one(t, old, new, 'realtime kinds')

marker = '''    private static void mirrorSingleToHome(Context context, String topic, String message, int priority) {
'''
helpers = '''    private static String postHedgedRealtime(Context context, String topic, String message, int priority) {
        Context app = context.getApplicationContext();
        Future<String> home = CALL_MIRROR.submit(() -> postAttempt(app, topic, message, priority, null));
        Future<String> fast = CALL_MIRROR.submit(() -> postAttempt(app, topic, message, priority, FAST_CALL_RELAY));
        return firstSuccessful(home, fast, 4500L);
    }

    private static String postBatchHedgedRealtime(Context context, String topic, JSONArray messages, int priority) {
        Context app = context.getApplicationContext();
        JSONArray homeCopy;
        JSONArray fastCopy;
        try {
            homeCopy = new JSONArray(messages.toString());
            fastCopy = new JSONArray(messages.toString());
        } catch (Exception e) { return "ERR:batch-copy"; }
        Future<String> home = CALL_MIRROR.submit(() -> postBatchAttempt(app, topic, homeCopy, priority, null));
        Future<String> fast = CALL_MIRROR.submit(() -> postBatchAttempt(app, topic, fastCopy, priority, FAST_CALL_RELAY));
        return firstSuccessful(home, fast, 4500L);
    }

    private static String firstSuccessful(Future<String> home, Future<String> fast, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String homeResult = null;
        String fastResult = null;
        while (System.currentTimeMillis() < deadline) {
            if (homeResult == null && home.isDone()) {
                try { homeResult = home.get(); } catch (Exception e) { homeResult = "ERR:home"; }
                if ("OK".equals(homeResult)) return "OK";
            }
            if (fastResult == null && fast.isDone()) {
                try { fastResult = fast.get(); } catch (Exception e) { fastResult = "ERR:fast"; }
                if ("OK".equals(fastResult)) return "OK";
            }
            if (homeResult != null && fastResult != null) break;
            sleepQuietly(40L);
        }
        if ("OK".equals(homeResult) || "OK".equals(fastResult)) return "OK";
        return "ERR:realtime-unavailable";
    }

''' + marker
t = one(t, marker, helpers, 'insert hedged helpers')

# Fast endpoint gets a short budget; home relay retains the already-tuned 6.0.30 budget.
old = '''            c.setConnectTimeout(priority >= 4 ? 4500 : 10000);
            c.setReadTimeout(priority >= 4 ? 5000 : 12000);c.setUseCaches(false);'''
new = '''            boolean fastRealtime = FAST_CALL_RELAY.equals(relay.replaceAll("/+$", ""));
            c.setConnectTimeout(fastRealtime ? 2200 : (priority >= 4 ? 4500 : 10000));
            c.setReadTimeout(fastRealtime ? 2800 : (priority >= 4 ? 5000 : 12000));c.setUseCaches(false);'''
t = one(t, old, new, 'batch timeout')
old = '''            c.setConnectTimeout(priority >= 4 ? 4500 : 10000);
            c.setReadTimeout(priority >= 4 ? 5000 : 12000);'''
new = '''            boolean fastRealtime = FAST_CALL_RELAY.equals(relay);
            c.setConnectTimeout(fastRealtime ? 2200 : (priority >= 4 ? 4500 : 10000));
            c.setReadTimeout(fastRealtime ? 2800 : (priority >= 4 ? 5000 : 12000));'''
t = one(t, old, new, 'single timeout')
write(transport, t)


# Fast receive path now handles games as well as calls, improving invitations/moves in background.
svc = 'duoapp/src/main/java/com/sergey/duochat/MessagingService.java'
s = read(svc)
s = s.replace('OurFamily/6.0.30 FastCall', 'OurFamily/6.0.31 Realtime')
s = one(s, 'if (isCallSignalLine(line)) handleLine(line, myTag, prefs);',
        'if (isRealtimeSignalLine(line)) handleLine(line, myTag, prefs);', 'fast listener call')
s = one(s, 'private boolean isCallSignalLine(String line) {',
        'private boolean isRealtimeSignalLine(String line) {', 'fast listener method')
old = '''            return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                    (kind.startsWith("group_") && !"group_chat".equals(kind));'''
new = '''            return (kind.startsWith("direct_") && !"direct_chat".equals(kind)) ||
                    (kind.startsWith("group_") && !"group_chat".equals(kind)) ||
                    kind.startsWith("durak_") || kind.startsWith("game_");'''
s = one(s, old, new, 'fast listener filter')
write(svc, s)

print('Applied OurFamily 6.0.31 reliability patch')
