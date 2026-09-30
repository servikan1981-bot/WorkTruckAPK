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
        raise SystemExit(f'{path}: expected exactly one match, got {count}: {old[:100]!r}')
    write(path, text.replace(old, new, 1))


# 1) Keep SDP offer/answer off the invitation/control executor. A slow duplicated invite
# must never leave the WebRTC peer stuck in have-local-offer behind CALL_POSTER work.
main = 'duoapp/src/main/java/com/sergey/duochat/MainActivity.java'
replace_once(main,
'''    private static final ThreadPoolExecutor SIGNAL_POSTER = new ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), r -> {
                Thread t = new Thread(r, "OurFamilyIceSignal");
                t.setDaemon(true);
                return t;
            });''',
'''    private static final ThreadPoolExecutor SDP_POSTER = new ThreadPoolExecutor(
            3, 3, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24), r -> {
                Thread t = new Thread(r, "OurFamilySdpSignal");
                t.setDaemon(true);
                return t;
            });
    private static final ThreadPoolExecutor SIGNAL_POSTER = new ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), r -> {
                Thread t = new Thread(r, "OurFamilyIceSignal");
                t.setDaemon(true);
                return t;
            });''')
replace_once(main,
'''                CALL_POSTER.execute(() -> reportRelayResult(requestId,
                        NativeRelayTransport.postBatchJson(app, topic, messagesJson, priority)));''',
'''                SDP_POSTER.execute(() -> reportRelayResult(requestId,
                        NativeRelayTransport.postBatchJson(app, topic, messagesJson, priority)));''')

# 2) Make relay requests fail fast enough to hedge/retry, retry TLS failures too, and
# consume successful responses so Android can reuse the HTTPS/TLS connection instead of
# paying a fresh KeenDNS TLS handshake for every invite/offer/answer/candidate.
transport = 'duoapp/src/main/java/com/sergey/duochat/NativeRelayTransport.java'
text = read(transport)
text = text.replace('import java.io.OutputStream;\n', 'import java.io.InputStream;\nimport java.io.OutputStream;\n', 1)

old = '''        if (first.startsWith("ERR:http:5") || first.startsWith("ERR:Socket") ||
                first.startsWith("ERR:Connect") || first.startsWith("ERR:UnknownHost")) {
            sleepQuietly(900L);
            return postAttempt(context, topic, message, priority);
        }'''
new = '''        if (retryable(first)) {
            sleepQuietly(priority >= 4 ? 250L : 900L);
            return postAttempt(context, topic, message, priority);
        }'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: single-post retry block not found')
text = text.replace(old, new, 1)

old = '''        if (first.startsWith("ERR:http:5") || first.startsWith("ERR:Socket") ||
                first.startsWith("ERR:Connect") || first.startsWith("ERR:UnknownHost")) {
            sleepQuietly(450L);
            return postBatchAttempt(context, topic, messages, priority);
        }'''
new = '''        if (retryable(first)) {
            sleepQuietly(priority >= 4 ? 250L : 450L);
            return postBatchAttempt(context, topic, messages, priority);
        }'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: batch retry block not found')
text = text.replace(old, new, 1)

old = '            c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setUseCaches(false);'
new = '''            c.setConnectTimeout(priority >= 4 ? 4500 : 10000);
            c.setReadTimeout(priority >= 4 ? 5000 : 12000);c.setUseCaches(false);'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: batch timeout block not found')
text = text.replace(old, new, 1)

old = '''            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);'''
new = '''            c.setConnectTimeout(priority >= 4 ? 4500 : 10000);
            c.setReadTimeout(priority >= 4 ? 5000 : 12000);'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: single timeout block not found')
text = text.replace(old, new, 1)

text = text.replace('c.setRequestProperty("User-Agent", "OurFamily/6.0.27 Android");',
                    'c.setRequestProperty("User-Agent", "OurFamily/6.0.30 Android");')

old = '''            int status = c.getResponseCode();
            return status >= 200 && status < 300 ? "OK" : "ERR:http:" + status;
        } catch (Exception e) { return "ERR:" + e.getClass().getSimpleName(); }
        finally { if (c != null) c.disconnect(); }'''
new = '''            int status = c.getResponseCode();
            drainResponse(c, status);
            return status >= 200 && status < 300 ? "OK" : "ERR:http:" + status;
        } catch (Exception e) {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
            return "ERR:" + e.getClass().getSimpleName();
        }'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: batch response block not found')
text = text.replace(old, new, 1)

old = '''            int code = c.getResponseCode();
            if (code >= 200 && code < 300) return "OK";
            return "ERR:http:" + code;
        } catch (Exception e) {
            return "ERR:" + e.getClass().getSimpleName();
        } finally {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }'''
new = '''            int code = c.getResponseCode();
            drainResponse(c, code);
            if (code >= 200 && code < 300) return "OK";
            return "ERR:http:" + code;
        } catch (Exception e) {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
            return "ERR:" + e.getClass().getSimpleName();
        }'''
if text.count(old) != 1:
    raise SystemExit('NativeRelayTransport: single response block not found')
text = text.replace(old, new, 1)

marker = '''    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
'''
insert = '''    private static boolean retryable(String result) {
        if (result == null) return true;
        return result.startsWith("ERR:http:5") || result.startsWith("ERR:Socket") ||
                result.startsWith("ERR:Connect") || result.startsWith("ERR:UnknownHost") ||
                result.startsWith("ERR:SSL") || result.startsWith("ERR:EOF");
    }

    private static void drainResponse(HttpURLConnection c, int code) {
        InputStream source = null;
        try {
            source = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (source == null) return;
            byte[] buffer = new byte[512];
            while (source.read(buffer) != -1) { /* fully consume for HTTPS keep-alive */ }
        } catch (Exception ignored) {
        } finally {
            try { if (source != null) source.close(); } catch (Exception ignored) {}
        }
    }

''' + marker
if text.count(marker) != 1:
    raise SystemExit('NativeRelayTransport: helper insertion marker not found')
text = text.replace(marker, insert, 1)
write(transport, text)

# 3) JS call fast path: do not serialize offer behind invitation delivery. The recipient
# can buffer an early offer already, so sending both concurrently is safe and removes an
# entire slow network dependency before WebRTC negotiation starts.
index = 'duoapp/src/main/assets/index.html'
text = read(index)

old = '''  var id=randomId(),timer=setTimeout(function(){relayRequests.delete(id);resolve('ERR:timeout');},48000);'''
new = '''  var timeoutMs=(priority||0)>=4?18000:48000;
  var id=randomId(),timer=setTimeout(function(){relayRequests.delete(id);resolve('ERR:timeout');},timeoutMs);'''
if text.count(old) != 1:
    raise SystemExit('index: relay timeout marker not found')
text = text.replace(old, new, 1)

old = '''   if(!await waitForRelaySdp(ps.pc,10000))throw new Error('TURN_RELAY_MISSING');
   noteCall('offer TURN '+countRelay(ps.pc.localDescription&&ps.pc.localDescription.sdp));'''
new = '''   var offerRelayReady=await waitForRelaySdp(ps.pc,2500);
   noteCall((offerRelayReady?'offer TURN ':'offer, TURN догоняет ')+countRelay(ps.pc.localDescription&&ps.pc.localDescription.sdp));'''
if text.count(old) != 1:
    raise SystemExit('index: offer TURN wait marker not found')
text = text.replace(old, new, 1)

old = '''   if(!await waitForRelaySdp(ps.pc,10000))throw new Error('TURN_RELAY_MISSING');
   noteCall('answer TURN '+countRelay(ps.pc.localDescription&&ps.pc.localDescription.sdp));'''
new = '''   var answerRelayReady=await waitForRelaySdp(ps.pc,2500);
   noteCall((answerRelayReady?'answer TURN ':'answer, TURN догоняет ')+countRelay(ps.pc.localDescription&&ps.pc.localDescription.sdp));'''
if text.count(old) != 1:
    raise SystemExit('index: answer TURN wait marker not found')
text = text.replace(old, new, 1)

old = ''' async function sendInvite(){
   if(!activeCall||activeCall.id!==id||activeCall.accepted)return;
   try{await publishEnvelope(peer,'direct_invite',{callId:id,kind:kind},5,wireKind,id,true);}catch(e){}
 }
 try{
   setTimeout(sendInvite,1600);
   setTimeout(sendInvite,4200);
   clearTimeout(callTimeout);callTimeout=setTimeout(function(){if(activeCall&&activeCall.id===id&&!activeCall.accepted){toast('Нет ответа');endCall(true);}},65000);
   await sendInvite();
   await mediaPromise;
   if(activeCall&&activeCall.id===id)await ensureInitialOffer(peer);
 }catch(e){toast('Не удалось подготовить или отправить вызов');endCall(false);}'''
new = ''' var inviteStored=false;
 async function sendInvite(){
   if(!activeCall||activeCall.id!==id||activeCall.accepted||inviteStored)return;
   try{
     await publishEnvelope(peer,'direct_invite',{callId:id,kind:kind},5,wireKind,id,true);
     inviteStored=true;noteCall('приглашение на сервере');
   }catch(e){}
 }
 try{
   // Hedge a slow KeenDNS/TLS request, but never serialize SDP behind it.
   sendInvite();
   setTimeout(sendInvite,700);
   setTimeout(sendInvite,2500);
   clearTimeout(callTimeout);callTimeout=setTimeout(function(){if(activeCall&&activeCall.id===id&&!activeCall.accepted){toast('Нет ответа');endCall(true);}},65000);
   await mediaPromise;
   if(activeCall&&activeCall.id===id)await ensureInitialOffer(peer);
 }catch(e){toast('Не удалось подготовить или отправить вызов');endCall(false);}'''
if text.count(old) != 1:
    raise SystemExit('index: direct invite serialized flow not found')
text = text.replace(old, new, 1)

old = ''' if(inv.type==='direct'){
   try{
     var early=pendingEarlyOffers[inv.id];
     if(!early||early.peer!==inv.from){
       await publishEnvelope(inv.from,'direct_accept',{callId:inv.id},5,'direct_accept',inv.id);
       await mediaPromise;
     }else{
       var buffered=pendingEarlyCandidates[inv.id]||[];
       for(var i=0;i<buffered.length;i++)if(buffered[i].peer===inv.from)applyCandidate(inv.from,buffered[i].candidate);
       await answerOffer(inv.from,early.sdp,false);
     }
     delete pendingEarlyOffers[inv.id];delete pendingEarlyCandidates[inv.id];
   }catch(e){toast('Не удалось подготовить звонок');endCall(true);return;}
 }else{'''
new = ''' if(inv.type==='direct'){
   try{
     // Tell the caller immediately, but do not make camera/media or the SDP answer wait
     // for this HTTPS request. The caller is already generating its offer in parallel.
     publishEnvelope(inv.from,'direct_accept',{callId:inv.id},5,'direct_accept',inv.id).catch(function(){});
     await mediaPromise;
     var early=pendingEarlyOffers[inv.id];
     if(early&&early.peer===inv.from){
       var buffered=pendingEarlyCandidates[inv.id]||[];
       for(var i=0;i<buffered.length;i++)if(buffered[i].peer===inv.from)applyCandidate(inv.from,buffered[i].candidate);
       await answerOffer(inv.from,early.sdp,false);
     }
     delete pendingEarlyOffers[inv.id];delete pendingEarlyCandidates[inv.id];
   }catch(e){toast('Не удалось подготовить звонок');endCall(true);return;}
 }else{'''
if text.count(old) != 1:
    raise SystemExit('index: direct accept serialized flow not found')
text = text.replace(old, new, 1)

write(index, text)
print('Applied OurFamily 6.0.30 fast WebRTC signaling patch')
