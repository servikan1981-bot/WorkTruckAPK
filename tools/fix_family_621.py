from pathlib import Path

html_path = Path('duoapp/src/main/assets/index.html')
java_path = Path('duoapp/src/main/java/com/sergey/duochat/MessagingService.java')
build_path = Path('duoapp/build.gradle')
manifest_path = Path('duoapp/src/main/AndroidManifest.xml')

html = html_path.read_text(encoding='utf-8')
java = java_path.read_text(encoding='utf-8')
build = build_path.read_text(encoding='utf-8')
manifest = manifest_path.read_text(encoding='utf-8')


def one(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, got {count}')
    return text.replace(old, new)

# Version identity.
build = one(build, "versionCode 6020", "versionCode 6021", 'versionCode')
build = one(build, "versionName '6.0.20'", "versionName '6.0.21'", 'versionName')
manifest = one(manifest, 'android:label="Наша семья 6.0.20"', 'android:label="Наша семья 6.0.21"', 'manifest label')
html = one(html, '<title>Наша семья 6.0.20</title>', '<title>Наша семья 6.0.21</title>', 'html title')
html = one(html, "var APP_VERSION='6.0.20'", "var APP_VERSION='6.0.21'", 'APP_VERSION')

# Sergey-only emergency composer.
menu_old = '  <button id="updateMenuBtn">⬆️ Проверить обновления</button>\n'
menu_new = menu_old + '  <button id="urgentMenuBtn" class="hidden">🚨 Срочное сообщение всем</button>\n'
html = one(html, menu_old, menu_new, 'urgent menu button')

admin_once = '<div id="adminOnce" class="admin-once hidden">Сергей — админ, имеет доступ ко всему приложению.</div>\n'
urgent_markup = '''<div id="urgentComposeModal" class="modal-backdrop hidden">
  <div class="modal-card urgent-compose-card">
    <div class="modal-title">🚨 Срочное сообщение всем</div>
    <p class="sub">Сообщение получат все участники семьи. У них оно откроется поверх приложения, а в фоне придёт приоритетное уведомление.</p>
    <textarea id="urgentInput" class="code urgent-input" rows="5" maxlength="1000" placeholder="Например: Срочно всем обновить приложение"></textarea>
    <button id="urgentSendBtn" class="primary wide" style="margin-top:12px;background:var(--red)">Отправить всем</button>
    <button id="urgentCancelBtn" class="option-btn" style="margin-top:8px;width:100%">Отмена</button>
  </div>
</div>
<div id="urgentOverlay" class="urgent-overlay hidden" role="dialog" aria-modal="true">
  <div class="urgent-card">
    <button id="urgentCloseBtn" class="urgent-close" aria-label="Закрыть">×</button>
    <div class="urgent-badge">СРОЧНО</div>
    <div class="urgent-title">Сообщение от Сергея</div>
    <div id="urgentText" class="urgent-text"></div>
    <div id="urgentTime" class="urgent-time"></div>
  </div>
</div>
''' + admin_once
html = one(html, admin_once, urgent_markup, 'urgent modal markup')

css_marker = '.admin-once{position:fixed;left:15px;right:15px;top:max(18px,env(safe-area-inset-top));background:#172033;color:white;border-radius:15px;padding:12px;text-align:center;font-size:13px;font-weight:800;z-index:160;box-shadow:var(--shadow)}\n'
css_add = css_marker + '''.urgent-compose-card{border-top:5px solid var(--red)}.urgent-input{min-height:120px;resize:vertical}
.urgent-overlay{position:fixed;inset:0;z-index:240;background:rgba(8,12,22,.84);display:grid;place-items:center;padding:20px}.urgent-card{position:relative;width:min(520px,100%);background:var(--card);color:var(--ink);border-radius:24px;padding:27px 21px 22px;box-shadow:0 24px 70px rgba(0,0,0,.38);border:3px solid var(--red)}.urgent-close{position:absolute;right:10px;top:8px;width:40px;height:40px;border:0;border-radius:50%;background:transparent;color:var(--ink);font-size:30px;line-height:36px}.urgent-badge{display:inline-block;background:var(--red);color:#fff;border-radius:999px;padding:6px 12px;font-size:12px;font-weight:950;letter-spacing:.08em}.urgent-title{font-size:22px;font-weight:950;margin:15px 0 10px}.urgent-text{font-size:18px;line-height:1.45;white-space:pre-wrap;word-break:break-word}.urgent-time{font-size:12px;color:var(--muted);margin-top:16px}
'''
html = one(html, css_marker, css_add, 'urgent CSS')

# Urgent message state and helpers. Payload remains pair-encrypted by publishEnvelope.
process_marker = 'async function processPayload(p){\n if(!p||p.v!==5||p.to!==role||p.from===role)return;\n var d=p.data||{},peer=p.from;\n\n'
urgent_helpers = '''function urgentDismissKey(id){return 'of621_urgent_dismissed_'+String(id||'');}
function showUrgentMessage(data){
 if(!data||!data.urgentId||!data.text||localStorage.getItem(urgentDismissKey(data.urgentId)))return;
 try{localStorage.setItem('of621_urgent_current',JSON.stringify(data));}catch(e){}
 $('urgentText').textContent=String(data.text).slice(0,1000);
 $('urgentTime').textContent=new Date(data.ts||Date.now()).toLocaleString('ru-RU');
 $('urgentOverlay').dataset.urgentId=data.urgentId;$('urgentOverlay').classList.remove('hidden');
}
function loadUrgentMessage(){try{var d=JSON.parse(localStorage.getItem('of621_urgent_current')||'null');if(d)showUrgentMessage(d);}catch(e){}}
function closeUrgentMessage(){var id=$('urgentOverlay').dataset.urgentId||'';if(id)try{localStorage.setItem(urgentDismissKey(id),'1');}catch(e){}try{localStorage.removeItem('of621_urgent_current');}catch(e){}$('urgentOverlay').classList.add('hidden');}
async function sendUrgentBroadcast(){
 if(role!=='sergey')return;
 var text=$('urgentInput').value.trim();if(!text){toast('Введите текст срочного сообщения');return;}
 var id=randomId(),data={urgentId:id,text:text.slice(0,1000),ts:Date.now()},peers=MEMBERS.filter(function(m){return m.id!==role;});
 $('urgentSendBtn').disabled=true;var failed=[];
 for(var i=0;i<peers.length;i++){
   try{await publishEnvelope(peers[i].id,'urgent_broadcast',data,5,'urgent_broadcast',id,true);}catch(e){failed.push(peers[i].name);}
 }
 $('urgentSendBtn').disabled=false;
 if(failed.length){toast('Не доставлено: '+failed.join(', '));return;}
 $('urgentInput').value='';$('urgentComposeModal').classList.add('hidden');toast('Срочное сообщение отправлено всем');
}

''' + process_marker
html = one(html, process_marker, urgent_helpers, 'urgent helpers')

payload_marker = " if(['game_invite','game_accept','game_decline','game_move','game_resign'].includes(p.kind)){if((d.gameType||'checkers')==='durak')receiveDurak(p);else receiveCheckers(p);return;}\n"
payload_new = " if(p.kind==='urgent_broadcast'){if(peer==='sergey'&&d.urgentId&&String(d.text||'').trim())showUrgentMessage({urgentId:d.urgentId,text:String(d.text).slice(0,1000),ts:d.ts||p.ts});return;}\n\n" + payload_marker
html = one(html, payload_marker, payload_new, 'urgent payload handler')

enter_old = "async function enter(){\n loadUiSettings();refreshAutoNews();\n $('myName').textContent='Наша семья · v'+APP_VERSION;"
enter_new = "async function enter(){\n loadUiSettings();refreshAutoNews();\n if($('urgentMenuBtn'))$('urgentMenuBtn').classList.toggle('hidden',role!=='sergey');\n $('myName').textContent='Наша семья · v'+APP_VERSION;"
html = one(html, enter_old, enter_new, 'urgent menu role visibility')
html = one(html, " await configure();\n refreshSharedNews();", " await configure();loadUrgentMessage();\n refreshSharedNews();", 'urgent load on enter')

listener_marker = "$('updateMenuBtn').addEventListener('click',function(){\n"
listener_add = '''$('urgentMenuBtn').addEventListener('click',function(){if(role!=='sergey')return;$('settingsPop').classList.add('hidden');$('urgentComposeModal').classList.remove('hidden');setTimeout(function(){$('urgentInput').focus();},80);});
$('urgentCancelBtn').addEventListener('click',function(){$('urgentComposeModal').classList.add('hidden');});
$('urgentSendBtn').addEventListener('click',sendUrgentBroadcast);
$('urgentCloseBtn').addEventListener('click',closeUrgentMessage);
$('urgentComposeModal').addEventListener('click',function(e){if(e.target===$('urgentComposeModal'))$('urgentComposeModal').classList.add('hidden');});
''' + listener_marker
html = one(html, listener_marker, listener_add, 'urgent listeners')

# Free-only WebRTC hardening: broader STUN discovery, quicker multi-stage ICE restart,
# non-blocking signalling, and recovery from ICE disconnected/failed states.
ice_old = "function iceServers(){\n var list=[{urls:'stun:stun.l.google.com:19302'},{urls:'stun:stun1.l.google.com:19302'},{urls:'stun:stun.cloudflare.com:3478'}];\n if(turnUrl&&turnUser&&turnPass)list.unshift({urls:turnUrl,username:turnUser,credential:turnPass});\n return list;\n}"
ice_new = "function iceServers(){\n var list=[{urls:['stun:stun.l.google.com:19302','stun:stun1.l.google.com:19302','stun:stun2.l.google.com:19302','stun:stun3.l.google.com:19302','stun:stun4.l.google.com:19302']},{urls:['stun:stun.cloudflare.com:3478','stun:stun.cloudflare.com:53']}];\n if(turnUrl&&turnUser&&turnPass)list.unshift({urls:turnUrl,username:turnUser,credential:turnPass});\n return list;\n}"
html = one(html, ice_old, ice_new, 'ICE servers')
html = one(html, "var pc=new RTCPeerConnection({iceServers:iceServers(),iceCandidatePoolSize:8,bundlePolicy:'max-bundle',iceTransportPolicy:'all'});",
           "var pc=new RTCPeerConnection({iceServers:iceServers(),iceCandidatePoolSize:12,bundlePolicy:'max-bundle',rtcpMuxPolicy:'require',iceTransportPolicy:'all'});", 'peer config')
html = one(html, "var ps={pc:pc,pending:[],restart:0,connectTimer:null,failureTimer:null,sdpPublishing:false,candidateQueue:[],candidateSending:false,answering:false};callPeers[peer]=ps;",
           "var ps={pc:pc,pending:[],restart:0,connectTimer:null,failureTimer:null,disconnectTimer:null,sdpPublishing:false,candidateQueue:[],candidateSending:false,answering:false};callPeers[peer]=ps;", 'peer state')

recovery_old = ''' ps.connectTimer=setTimeout(function(){
   if(!activeCall||!callPeers[peer]||pc.connectionState==='connected'||pc.connectionState==='closed')return;
   if(ps.restart<1&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
 },20000);
 ps.failureTimer=setTimeout(function(){
   if(activeCall&&callPeers[peer]&&pc.connectionState!=='connected')$('callState').textContent='Нет соединения. Проверьте сеть.';
 },25000);
 pc.onconnectionstatechange=function(){
   if(pc.connectionState==='connected'){
     if(ps.connectTimer){clearTimeout(ps.connectTimer);ps.connectTimer=null;}
     if(ps.failureTimer){clearTimeout(ps.failureTimer);ps.failureTimer=null;}
     ps.restart=0;markCall('connected');updateCallState();
   }else if(pc.connectionState==='failed'){
     if(ps.restart<1&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
     else updateCallState();
   }else updateCallState();
 };
'''
recovery_new = ''' function scheduleRecovery(delay){
   if(ps.connectTimer)clearTimeout(ps.connectTimer);
   ps.connectTimer=setTimeout(function(){
     if(!activeCall||!callPeers[peer]||pc.connectionState==='connected'||pc.connectionState==='closed')return;
     if(ps.restart<3&&shouldOffer(peer)){ps.restart++;restartPeer(peer);if(ps.restart<3)scheduleRecovery(8000);}
   },delay);
 }
 scheduleRecovery(8000);
 ps.failureTimer=setTimeout(function(){
   if(activeCall&&callPeers[peer]&&pc.connectionState!=='connected')$('callState').textContent='Прямое соединение не удалось. Попробуйте сменить Wi‑Fi/мобильную сеть.';
 },38000);
 pc.oniceconnectionstatechange=function(){
   if(pc.iceConnectionState==='disconnected'){
     if(ps.disconnectTimer)clearTimeout(ps.disconnectTimer);
     ps.disconnectTimer=setTimeout(function(){if(activeCall&&callPeers[peer]&&pc.iceConnectionState==='disconnected'&&ps.restart<3&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}},3500);
   }else if(pc.iceConnectionState==='failed'&&ps.restart<3&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
   else if(pc.iceConnectionState==='connected'||pc.iceConnectionState==='completed'){if(ps.disconnectTimer){clearTimeout(ps.disconnectTimer);ps.disconnectTimer=null;}}
 };
 pc.onconnectionstatechange=function(){
   if(pc.connectionState==='connected'){
     if(ps.connectTimer){clearTimeout(ps.connectTimer);ps.connectTimer=null;}
     if(ps.failureTimer){clearTimeout(ps.failureTimer);ps.failureTimer=null;}
     if(ps.disconnectTimer){clearTimeout(ps.disconnectTimer);ps.disconnectTimer=null;}
     markCall('connected');updateCallState();
   }else if(pc.connectionState==='failed'){
     if(ps.restart<3&&shouldOffer(peer)){ps.restart++;restartPeer(peer);}
     else updateCallState();
   }else updateCallState();
 };
'''
html = one(html, recovery_old, recovery_new, 'ICE recovery')
html = one(html, "var ps=callPeers[peer];if(ps){if(ps.connectTimer)clearTimeout(ps.connectTimer);if(ps.failureTimer)clearTimeout(ps.failureTimer);try{ps.pc.close();}catch(e){}delete callPeers[peer];}",
           "var ps=callPeers[peer];if(ps){if(ps.connectTimer)clearTimeout(ps.connectTimer);if(ps.failureTimer)clearTimeout(ps.failureTimer);if(ps.disconnectTimer)clearTimeout(ps.disconnectTimer);try{ps.pc.close();}catch(e){}delete callPeers[peer];}", 'peer cleanup')

sdp_signal = "await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id);"
if html.count(sdp_signal) < 2:
    raise SystemExit('SDP signal markers missing')
html = html.replace(sdp_signal, "await publishEnvelope(peer,k,{callId:activeCall.id,sdp:ps.pc.localDescription},5,k,activeCall.id,true);")

# Native high-priority notification/full-screen launch for an encrypted urgent event.
java = one(java, '    private static final String CH_CALLS = "family_calls_v6";\n',
           '    private static final String CH_CALLS = "family_calls_v6";\n    private static final String CH_URGENT = "family_urgent_v621";\n', 'urgent channel constant')

chat_branch = '''            if ("direct_chat".equals(kind) || "group_chat".equals(kind)) {
'''
urgent_branch = '''            if ("urgent_broadcast".equals(kind) && "sergey".equals(senderRole)) {
                if ("0".equals(chunkIndex) && age <= 7L * 24L * 60L * 60L * 1000L)
                    notifyUrgent(eventTime > 0L ? eventTime : System.currentTimeMillis(), eventId);
            } else if ("direct_chat".equals(kind) || "group_chat".equals(kind)) {
'''
java = one(java, chat_branch, urgent_branch, 'urgent native routing')

notify_marker = '    private void notifyMessage(String senderRole, String eventId, String text, String messageId, String messageKind) {\n'
notify_urgent = '''    private void notifyUrgent(long eventTime, String eventId) {
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("open_message_kind", "urgent");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 12000 + Math.abs(eventId.hashCode() % 1000), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_URGENT) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Срочное сообщение от Сергея")
                .setContentText("Откройте сообщение семьи")
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(12000 + Math.abs(eventId.hashCode() % 1000), n);
    }

''' + notify_marker
java = one(java, notify_marker, notify_urgent, 'urgent notification method')

channel_marker = '''        NotificationChannel messages = new NotificationChannel(
                CH_MESSAGES, "Семейные сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        nm.createNotificationChannel(messages);

'''
channel_new = channel_marker + '''        NotificationChannel urgent = new NotificationChannel(
                CH_URGENT, "Срочные семейные сообщения", NotificationManager.IMPORTANCE_MAX);
        urgent.setDescription("Срочные сообщения Сергея для всей семьи");
        urgent.enableVibration(true);
        urgent.enableLights(true);
        urgent.setLightColor(Color.RED);
        urgent.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(urgent);

'''
java = one(java, channel_marker, channel_new, 'urgent notification channel')

html_path.write_text(html, encoding='utf-8')
java_path.write_text(java, encoding='utf-8')
build_path.write_text(build, encoding='utf-8')
manifest_path.write_text(manifest, encoding='utf-8')
print('Applied OurFamily 6.0.21: free WebRTC recovery + Sergey urgent broadcast')
