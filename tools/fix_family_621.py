from pathlib import Path


def replace_one(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 marker, found {count}')
    return text.replace(old, new)

html_path = Path('duoapp/src/main/assets/index.html')
main_path = Path('duoapp/src/main/java/com/sergey/duochat/MainActivity.java')
msg_path = Path('duoapp/src/main/java/com/sergey/duochat/MessagingService.java')
dir_path = Path('duoapp/src/main/java/com/sergey/duochat/FamilyDirectory.java')
manifest_path = Path('duoapp/src/main/AndroidManifest.xml')
gradle_path = Path('duoapp/build.gradle')

html = html_path.read_text(encoding='utf-8')
main = main_path.read_text(encoding='utf-8')
msg = msg_path.read_text(encoding='utf-8')
directory = dir_path.read_text(encoding='utf-8')
manifest = manifest_path.read_text(encoding='utf-8')
gradle = gradle_path.read_text(encoding='utf-8')

# Version identity.
gradle = replace_one(gradle, "versionCode 6020", "versionCode 6021", 'versionCode')
gradle = replace_one(gradle, "versionName '6.0.20'", "versionName '6.0.21'", 'versionName')
manifest = replace_one(manifest, 'android:label="Наша семья 6.0.20"', 'android:label="Наша семья 6.0.21"', 'manifest label')
html = replace_one(html, '<title>Наша семья 6.0.20</title>', '<title>Наша семья 6.0.21</title>', 'html title')
html = replace_one(html, "var APP_VERSION='6.0.20'", "var APP_VERSION='6.0.21'", 'APP_VERSION')
html = html.replace('<div id="aboutVersion" class="about-version">v6.0.9</div>', '<div id="aboutVersion" class="about-version">v6.0.21</div>')
main = main.replace('return "6.0.18";', 'return "6.0.21";')

# Register the native urgent-message screen.
activity_marker = '''        <activity
            android:name=".IncomingCallActivity"
            android:excludeFromRecents="true"
            android:launchMode="singleTop"
            android:screenOrientation="portrait"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:exported="false" />
'''
activity_new = activity_marker + '''
        <activity
            android:name=".UrgentMessageActivity"
            android:excludeFromRecents="true"
            android:launchMode="singleTop"
            android:screenOrientation="portrait"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:exported="false" />
'''
manifest = replace_one(manifest, activity_marker, activity_new, 'urgent activity manifest')

# Sergey-only urgent broadcast composer in the existing menu.
settings_old = '''  <button id="updateMenuBtn">⬆️ Проверить обновления</button>
  <button id="relayMenuBtn">🌐 Сервер сообщений</button>'''
settings_new = '''  <button id="updateMenuBtn">⬆️ Проверить обновления</button>
  <button id="urgentMenuBtn" class="hidden" style="color:#b91c1c;font-weight:900" onclick="window.openUrgentComposer&&window.openUrgentComposer()">🚨 Срочное сообщение всем</button>
  <button id="relayMenuBtn">🌐 Сервер сообщений</button>'''
html = replace_one(html, settings_old, settings_new, 'urgent menu button')

modal_marker = '''<div id="aboutModal" class="modal-backdrop hidden">'''
urgent_modal = '''<div id="urgentModal" class="modal-backdrop hidden">
  <div class="modal-card">
    <div class="modal-title" style="color:#b91c1c">🚨 Срочное сообщение всем</div>
    <p class="sub">Сообщение получат все участники семьи. У них появится приоритетное уведомление, а при открытии приложения — полноэкранная карточка.</p>
    <textarea id="urgentText" class="code" rows="6" maxlength="600" placeholder="Например: Срочно всем обновить приложение"></textarea>
    <button id="urgentSendBtn" class="primary wide" style="margin-top:12px;background:#b91c1c" onclick="window.sendUrgentAll&&window.sendUrgentAll()">Отправить всем</button>
    <button class="option-btn" style="margin-top:8px;width:100%" onclick="window.closeUrgentComposer&&window.closeUrgentComposer()">Отмена</button>
  </div>
</div>

'''
html = replace_one(html, modal_marker, urgent_modal + modal_marker, 'urgent modal')

# Hide/open behavior and Sergey-only visibility.
html = replace_one(
    html,
    "['settingsPop','attachMenu','emojiPanel','newsCaptureMenu','relayModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer']",
    "['settingsPop','attachMenu','emojiPanel','newsCaptureMenu','relayModal','urgentModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer']",
    'hide urgent modal')
html = replace_one(
    html,
    " $('adminArchiveBtn').classList.toggle('hidden',role!=='sergey');",
    " $('adminArchiveBtn').classList.toggle('hidden',role!=='sergey');\n $('urgentMenuBtn').classList.toggle('hidden',role!=='sergey');",
    'Sergey urgent visibility')

urgent_js_marker = '''async function publishEnvelope(toRole,kind,data,priority,wireKind,callId,nonBlocking){'''
urgent_js = '''function openUrgentComposer(){
 if(role!=='sergey'){toast('Доступ только Сергею');return;}
 $('settingsPop').classList.add('hidden');$('urgentModal').classList.remove('hidden');setTimeout(function(){$('urgentText').focus();},60);
}
function closeUrgentComposer(){$('urgentModal').classList.add('hidden');}
async function sendUrgentAll(){
 if(role!=='sergey'){toast('Доступ только Сергею');return;}
 var text=String($('urgentText').value||'').trim();
 if(!text){toast('Введите текст сообщения');return;}
 if(text.length>600){toast('Максимум 600 символов');return;}
 if(!confirm('Отправить срочное сообщение всем участникам семьи?'))return;
 var result='';try{result=String(AndroidBridge.sendUrgentToAll(text)||'');}catch(e){result='ERR:bridge';}
 if(!result.startsWith('OK')){toast('Не удалось начать отправку');return;}
 $('urgentText').value='';closeUrgentComposer();toast('Срочное сообщение отправляется всем');
}
window.openUrgentComposer=openUrgentComposer;
window.closeUrgentComposer=closeUrgentComposer;
window.sendUrgentAll=sendUrgentAll;

'''
html = replace_one(html, urgent_js_marker, urgent_js + urgent_js_marker, 'urgent JS functions')

# Direct WebRTC hardening without paid TURN: more STUN discovery, lighter video,
# quicker ICE recovery and a second restart attempt. Protocol remains compatible
# with 6.0.20 during rolling upgrades.
ice_old = '''function iceServers(){
 var list=[{urls:'stun:stun.l.google.com:19302'},{urls:'stun:stun1.l.google.com:19302'},{urls:'stun:stun.cloudflare.com:3478'}];
 if(turnUrl&&turnUser&&turnPass)list.unshift({urls:turnUrl,username:turnUser,credential:turnPass});
 return list;
}'''
ice_new = '''function iceServers(){
 var list=[
  {urls:'stun:stun.cloudflare.com:3478'},
  {urls:'stun:stun.l.google.com:19302'},
  {urls:'stun:stun1.l.google.com:19302'},
  {urls:'stun:stun2.l.google.com:19302'},
  {urls:'stun:stun3.l.google.com:19302'}
 ];
 if(turnUrl&&turnUser&&turnPass)list.unshift({urls:turnUrl,username:turnUser,credential:turnPass});
 return list;
}'''
html = replace_one(html, ice_old, ice_new, 'STUN list')
html = replace_one(
    html,
    "video:kind==='video'?{facingMode:{ideal:currentFacing},width:{ideal:640},height:{ideal:480}}:false",
    "video:kind==='video'?{facingMode:{ideal:currentFacing},width:{ideal:640,max:960},height:{ideal:360,max:540},frameRate:{ideal:20,max:24}}:false",
    'video constraints')

# Sender bandwidth cap helps weak mobile links and reduces stalls.
tune_marker = '''function callSignalKind(base){return activeCall&&activeCall.type==='group'?'group_'+base:'direct_'+base;}
function createPeer(peer){'''
tune_new = '''function callSignalKind(base){return activeCall&&activeCall.type==='group'?'group_'+base:'direct_'+base;}
function tunePeerSenders(pc){
 try{pc.getSenders().forEach(function(sender){
  if(!sender.track||!sender.getParameters||!sender.setParameters)return;
  var p=sender.getParameters()||{};if(!p.encodings||!p.encodings.length)p.encodings=[{}];
  if(sender.track.kind==='video'){p.encodings[0].maxBitrate=650000;p.encodings[0].maxFramerate=20;}
  else if(sender.track.kind==='audio')p.encodings[0].maxBitrate=48000;
  var promise=sender.setParameters(p);if(promise&&promise.catch)promise.catch(function(){});
 });}catch(e){}
}
function createPeer(peer){'''
html = replace_one(html, tune_marker, tune_new, 'sender bitrate helper')
html = replace_one(
    html,
    " if(localStream)localStream.getTracks().forEach(function(t){pc.addTrack(t,localStream);});",
    " if(localStream){localStream.getTracks().forEach(function(t){pc.addTrack(t,localStream);});tunePeerSenders(pc);}",
    'tune initial senders')
html = replace_one(
    html,
    "       if(!already)try{pc.addTrack(track,stream);}catch(e){}\n     });",
    "       if(!already)try{pc.addTrack(track,stream);}catch(e){}\n     });\n     tunePeerSenders(pc);",
    'tune late senders')

html = replace_one(
    html,
    "var ps={pc:pc,pending:[],restart:0,connectTimer:null,failureTimer:null,sdpPublishing:false,candidateQueue:[],candidateSending:false,answering:false};callPeers[peer]=ps;",
    "var ps={pc:pc,pending:[],restart:0,connectTimer:null,recoveryTimer:null,failureTimer:null,sdpPublishing:false,candidateQueue:[],candidateSending:false,answering:false};callPeers[peer]=ps;",
    'peer timers')
html = replace_one(
    html,
    ''' ps.connectTimer=setTimeout(function(){
   if(!activeCall||!callPeers[peer]||pc.connectionState==='connected'||pc.connectionState==='closed')return;
   if(ps.restart<1&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
 },20000);
 ps.failureTimer=setTimeout(function(){
   if(activeCall&&callPeers[peer]&&pc.connectionState!=='connected')$('callState').textContent='Нет соединения. Проверьте сеть.';
 },25000);''',
    ''' ps.connectTimer=setTimeout(function(){
   if(!activeCall||!callPeers[peer]||pc.connectionState==='connected'||pc.connectionState==='closed')return;
   if(ps.restart<2&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
 },9000);
 ps.recoveryTimer=setTimeout(function(){
   if(!activeCall||!callPeers[peer]||pc.connectionState==='connected'||pc.connectionState==='closed')return;
   if(ps.restart<2&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
 },20000);
 ps.failureTimer=setTimeout(function(){
   if(activeCall&&callPeers[peer]&&pc.connectionState!=='connected')$('callState').textContent='Прямое соединение не удалось. Попробуйте сменить Wi‑Fi / мобильную сеть и перезвонить.';
 },34000);''',
    'ICE recovery timers')
html = replace_one(
    html,
    "     if(ps.connectTimer){clearTimeout(ps.connectTimer);ps.connectTimer=null;}\n     if(ps.failureTimer){clearTimeout(ps.failureTimer);ps.failureTimer=null;}\n     ps.restart=0;markCall('connected');updateCallState();",
    "     if(ps.connectTimer){clearTimeout(ps.connectTimer);ps.connectTimer=null;}\n     if(ps.recoveryTimer){clearTimeout(ps.recoveryTimer);ps.recoveryTimer=null;}\n     if(ps.failureTimer){clearTimeout(ps.failureTimer);ps.failureTimer=null;}\n     ps.restart=0;tunePeerSenders(pc);markCall('connected');updateCallState();",
    'clear recovery timers')
html = replace_one(
    html,
    "   }else if(pc.connectionState==='failed'){\n     if(ps.restart<1&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}",
    "   }else if(pc.connectionState==='failed'){\n     if(ps.restart<2&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}",
    'second failed restart')
html = replace_one(
    html,
    " var ps=callPeers[peer];if(ps){if(ps.connectTimer)clearTimeout(ps.connectTimer);if(ps.failureTimer)clearTimeout(ps.failureTimer);try{ps.pc.close();}catch(e){}delete callPeers[peer];}",
    " var ps=callPeers[peer];if(ps){if(ps.connectTimer)clearTimeout(ps.connectTimer);if(ps.recoveryTimer)clearTimeout(ps.recoveryTimer);if(ps.failureTimer)clearTimeout(ps.failureTimer);try{ps.pc.close();}catch(e){}delete callPeers[peer];}",
    'remove peer timers')
html = replace_one(html, "setTimeout(sendInvite,1600);\n   setTimeout(sendInvite,4200);", "setTimeout(sendInvite,1200);\n   setTimeout(sendInvite,3200);\n   setTimeout(sendInvite,7000);", 'direct invite retries')
html = replace_one(html, "setTimeout(sendGroupInvites,1600);\n setTimeout(sendGroupInvites,4200);", "setTimeout(sendGroupInvites,1200);\n setTimeout(sendGroupInvites,3200);\n setTimeout(sendGroupInvites,7000);", 'group invite retries')

# FamilyDirectory authenticator for urgent plaintext wire. Text is readable by Android
# notifications, while the token prevents unrelated publishers from forging it.
dir_marker = '''    public static String controlToken(String code, String callId, String action, String fromRole, String toRole) {
        return sha256("OurFamily-v5-control|" + code + "|" + callId + "|" + action + "|" + fromRole + "|" + toRole).substring(0, 32);
    }
'''
dir_new = dir_marker + '''
    public static String urgentToken(String code, String id, long timestamp, String textBase64, String toRole) {
        return sha256("OurFamily-v621-urgent|" + code + "|" + id + "|" + timestamp + "|" + textBase64 + "|sergey|" + toRole).substring(0, 32);
    }
'''
directory = replace_one(directory, dir_marker, dir_new, 'urgent token helper')

# Native bridge sends one authenticated urgent wire to every other family member.
bridge_marker = '''        @JavascriptInterface
        public String getVersion() {
            return "6.0.21";
        }
'''
bridge_new = '''        @JavascriptInterface
        public String sendUrgentToAll(String text) {
            try {
                if (!"sergey".equals(SecureStore.role(MainActivity.this))) return "ERR:forbidden";
                text = text == null ? "" : text.trim();
                if (text.isEmpty() || text.length() > 600) return "ERR:text";
                String code = SecureStore.familyCode(MainActivity.this);
                if (code.isEmpty()) return "ERR:profile";
                final String safeText = text;
                final String id = java.util.UUID.randomUUID().toString().replace("-", "");
                final long ts = System.currentTimeMillis();
                final String encoded = android.util.Base64.encodeToString(
                        safeText.getBytes(StandardCharsets.UTF_8),
                        android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
                final String senderTag = FamilyDirectory.tag(code, "sergey");
                int queued = 0;
                for (String targetRole : FamilyDirectory.MEMBERS.keySet()) {
                    if ("sergey".equals(targetRole)) continue;
                    final String recipient = targetRole;
                    final String recipientTag = FamilyDirectory.tag(code, recipient);
                    final String token = FamilyDirectory.urgentToken(code, id, ts, encoded, recipient);
                    final String wire = "of5urgent|" + senderTag + "|" + recipientTag + "|" + id + "|" + ts + "|" + encoded + "|" + token;
                    final String topic = FamilyDirectory.inboxTopic(code, recipient);
                    try {
                        SIGNAL_POSTER.execute(() -> NativeRelayTransport.post(getApplicationContext(), topic, wire, 5));
                        queued++;
                    } catch (RejectedExecutionException ignored) {}
                }
                return queued > 0 ? "OK:" + queued : "ERR:queue";
            } catch (Exception e) {
                return "ERR:" + e.getClass().getSimpleName();
            }
        }

''' + bridge_marker
main = replace_one(main, bridge_marker, bridge_new, 'urgent bridge')

# Show pending urgent card whenever the app comes to foreground.
resume_old = '''        UpdateManager.resumePendingInstall(this);
        UpdateManager.checkAsync(this, false);
    }
'''
resume_new = '''        UpdateManager.resumePendingInstall(this);
        UpdateManager.checkAsync(this, false);
        UrgentMessageActivity.showPendingIfNeeded(this);
    }
'''
main = replace_one(main, resume_old, resume_new, 'urgent foreground check')

# Native listener verifies/stores urgent messages before putting regular encrypted
# traffic into the WebView inbox.
queue_marker = '''            RelayInbox.enqueue(this, id, eventTime, msg);
'''
queue_new = '''            if (msg.startsWith("of5urgent|")) {
                handleUrgentWire(msg, myTag, eventTime, id);
                return;
            }

''' + queue_marker
msg = replace_one(msg, queue_marker, queue_new, 'urgent wire dispatch')

method_marker = '''    private void sendNativeDelivered(
'''
urgent_method = '''    private void handleUrgentWire(String wire, String myTag, long eventTime, String eventId) {
        try {
            String[] p = wire.split("\\\\|", 7);
            if (p.length != 7 || !"of5urgent".equals(p[0])) return;
            String senderTag = p[1], recipientTag = p[2], urgentId = p[3], encoded = p[5], token = p[6];
            if (!recipientTag.equals(myTag) || !urgentId.matches("[a-f0-9]{32}")) return;
            long ts = Long.parseLong(p[4]);
            long now = System.currentTimeMillis();
            if (ts > now + 300000L || now - ts > 7L * 86400000L) return;
            String code = SecureStore.familyCode(this);
            String myRole = SecureStore.role(this);
            if (code.isEmpty() || myRole.isEmpty() || "sergey".equals(myRole)) return;
            if (!"sergey".equals(FamilyDirectory.roleFromTag(code, senderTag))) return;
            String expected = FamilyDirectory.urgentToken(code, urgentId, ts, encoded, myRole);
            if (!expected.equals(token)) return;
            byte[] bytes = android.util.Base64.decode(encoded,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (text.isEmpty() || text.length() > 600) return;
            SharedPreferences prefs = SecureStore.prefs(this);
            if (urgentId.equals(prefs.getString("urgent_dismissed_id", ""))) return;
            prefs.edit().putString("urgent_active_id", urgentId)
                    .putString("urgent_active_text", text)
                    .putLong("urgent_active_ts", ts).apply();
            UrgentMessageActivity.present(this, urgentId, text, appVisible);
        } catch (Exception ignored) {}
    }

'''
msg = replace_one(msg, method_marker, urgent_method + method_marker, 'urgent handler method')

html_path.write_text(html, encoding='utf-8')
main_path.write_text(main, encoding='utf-8')
msg_path.write_text(msg, encoding='utf-8')
dir_path.write_text(directory, encoding='utf-8')
manifest_path.write_text(manifest, encoding='utf-8')
gradle_path.write_text(gradle, encoding='utf-8')
print('Applied OurFamily 6.0.21: direct-video hardening + Sergey urgent broadcast')
