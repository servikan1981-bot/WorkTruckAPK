from pathlib import Path

html_path = Path('duoapp/src/main/assets/index.html')
main_path = Path('duoapp/src/main/java/com/sergey/duochat/MainActivity.java')
service_path = Path('duoapp/src/main/java/com/sergey/duochat/MessagingService.java')
build_path = Path('duoapp/build.gradle')
manifest_path = Path('duoapp/src/main/AndroidManifest.xml')
h = html_path.read_text(encoding='utf-8')
main = main_path.read_text(encoding='utf-8')
service = service_path.read_text(encoding='utf-8')
build = build_path.read_text(encoding='utf-8')
manifest = manifest_path.read_text(encoding='utf-8')

def one(src, old, new, label):
    n = src.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {n}')
    return src.replace(old, new)

build = one(build, 'versionCode 6022', 'versionCode 6023', 'version code')
build = one(build, "versionName '6.0.22'", "versionName '6.0.23'", 'version name')
manifest = one(manifest, 'android:label="Наша семья 6.0.22"', 'android:label="Наша семья 6.0.23"', 'manifest label')
h = one(h, '<title>Наша семья 6.0.22</title>', '<title>Наша семья 6.0.23</title>', 'page title')
h = one(h, "var APP_VERSION='6.0.22'", "var APP_VERSION='6.0.23'", 'app version')

# A saved invitation is sent again when the inviter chooses the same person.
# The reliable native outbox persists a wire before reporting OK to JavaScript.
old_invite = """async function inviteDurak(peer){
 var old=Object.values(durakGames).find(function(g){return (g.status==='active'||g.status==='invited')&&durakPeer(g)===peer&&g.state.winner===null;});if(old){openDurak(old.id);return;}
 var id=randomId(),seed=randomSeed(),g={id:id,players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),state:DurakRules.initial(seed),target:0};
 durakGames[id]=g;saveDurakGames();openDurak(id);
 try{await publishEnvelope(peer,'game_invite',{gameType:'durak',gameId:id,seed:seed,state:g.state},4,'durak_invite',id,true);}catch(e){toast('Приглашение сохранено. Повторите отправку позже.');}
}"""
new_invite = """async function sendDurakInvite(g){
 try{
  await publishEnvelope(durakPeer(g),'game_invite',{gameType:'durak',gameId:g.id,seed:g.state.seed,state:g.state},5,'durak_invite',g.id,true);
  toast('Приглашение отправляется. Света увидит его при получении.');
  if(activeDurakId===g.id)renderDurak();
 }catch(e){toast('Не удалось поставить приглашение в очередь. Повторите отправку.');}
}
async function inviteDurak(peer){
 var old=Object.values(durakGames).find(function(g){return (g.status==='active'||g.status==='invited')&&durakPeer(g)===peer&&g.state.winner===null;});
 if(old){openDurak(old.id);if(old.status==='invited'&&old.inviter===role)sendDurakInvite(old);return;}
 var id=randomId(),seed=randomSeed(),g={id:id,players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),state:DurakRules.initial(seed),target:0};
 durakGames[id]=g;saveDurakGames();openDurak(id);sendDurakInvite(g);
}"""
h = one(h, old_invite, new_invite, 'Durak invitation')

h = one(h, "if(dg){if(accept&&dg.status==='invited'&&dg.invitee===role)acceptDurak(dg.id);else openDurak(dg.id);return true;}",
        "if(dg){if(dg.status==='invited'&&dg.invitee===role){if(accept)acceptDurak(dg.id);else{showScreen('home');showDurakRequest();}}else openDurak(dg.id);return true;}",
        'Durak notification navigation')

h = one(h, "var status=g.status==='invited'?(role===g.inviter?'Ждём ответа':'Приглашение'):g.status==='declined'?",
        "var status=g.status==='invited'?(role===g.inviter?(AndroidBridge.pendingCritical(g.id)>0?'Отправляем приглашение…':'Ждём ответа'):'Приглашение'):g.status==='declined'?",
        'Durak pending status')
h = one(h, "$('durakResignBtn').classList.toggle('hidden',!active);",
        "$('durakResignBtn').classList.toggle('hidden',!active);$('durakRetryInviteBtn').classList.toggle('hidden',g.status!=='invited'||g.inviter!==role);",
        'Durak retry button visibility')
h = one(h, '<button id="durakResignBtn" class="resign">Сдаться</button>',
        '<button id="durakRetryInviteBtn" class="take hidden">Повторить приглашение</button><button id="durakResignBtn" class="resign">Сдаться</button>',
        'Durak retry button markup')
h = one(h, "$('durakResignBtn').addEventListener('click',resignDurak);",
        "$('durakResignBtn').addEventListener('click',resignDurak);$('durakRetryInviteBtn').addEventListener('click',function(){var g=durakGames[activeDurakId];if(g&&g.status==='invited'&&g.inviter===role)sendDurakInvite(g);});",
        'Durak retry button listener')

# The original nonblocking bridge returned OK before the HTTP POST ran. Its
# native outbox now commits critical wires to disk, retries failures and wakes
# an open WebView when an incoming event is enqueued.
main = one(main, '''                SIGNAL_POSTER.execute(() -> NativeRelayTransport.post(app, topic, message, priority));
                return "OK";''',
           '''                if (ReliableRelayOutbox.isCritical(message))
                    return ReliableRelayOutbox.enqueue(app, topic, message, priority) ? "OK" : "ERR:outbox_full";
                SIGNAL_POSTER.execute(() -> NativeRelayTransport.post(app, topic, message, priority));
                return "OK";''', 'durable critical queue')
main = one(main, '''        @JavascriptInterface
        public String probeRelay''',
           '''        @JavascriptInterface
        public int pendingCritical(String callId) {
            return ReliableRelayOutbox.pendingFor(MainActivity.this, callId);
        }

        @JavascriptInterface
        public String probeRelay''', 'pending outbox bridge')

main = one(main, 'import java.io.ByteArrayOutputStream;',
           'import java.lang.ref.WeakReference;\nimport java.io.ByteArrayOutputStream;', 'weak reference import')
main = one(main, '    private WebView webView;',
           '    private static volatile WeakReference<MainActivity> visibleActivity = new WeakReference<>(null);\n    private WebView webView;', 'active WebView')
main = one(main, '''    @Override
    protected void onResume() {
        super.onResume();
        MessagingService.setAppVisible(true);''',
           '''    public static void notifyRelayArrived() {
        MainActivity activity = visibleActivity.get();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.webView != null) activity.webView.evaluateJavascript(
                    "window.__ourFamilyConsumeNativeAction && window.__ourFamilyConsumeNativeAction();", null);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        visibleActivity = new WeakReference<>(this);
        MessagingService.setAppVisible(true);''', 'native foreground wake')
main = one(main, '''    protected void onPause() {
        MessagingService.setAppVisible(false);''',
           '''    protected void onPause() {
        visibleActivity = new WeakReference<>(null);
        MessagingService.setAppVisible(false);''', 'native foreground pause')
main = one(main, '"checkers".equals(messageKind) && intent.getBooleanExtra("open_game_accept", false)',
           '("checkers".equals(messageKind) || "durak".equals(messageKind)) && intent.getBooleanExtra("open_game_accept", false)',
           'Durak notification accept')

service = one(service, '        startWorker();\n        startNewsWorker();',
              '        ReliableRelayOutbox.kick(this);\n        startWorker();\n        startNewsWorker();', 'resume pending sends on service start')
service = one(service, '            RelayInbox.enqueue(this, id, eventTime, msg);',
              '            RelayInbox.enqueue(this, id, eventTime, msg);\n            MainActivity.notifyRelayArrived();',
              'wake open app after incoming relay event')

# Faster visual feedback for urgent messages already sent to every family role.
old_send = """ $('urgentSendBtn').disabled=false;
 if(failed.length){toast('Не доставлено: '+failed.join(', '));return;}
 $('urgentInput').value='';$('urgentComposeModal').classList.add('hidden');toast('Срочное сообщение отправлено всем');"""
new_send = """ $('urgentSendBtn').disabled=false;
 if(failed.length){toast('Не поставлено в очередь: '+failed.join(', '));return;}
 $('urgentInput').value='';$('urgentComposeModal').classList.add('hidden');toast('Срочное сообщение отправляется всем');"""
h = one(h, old_send, new_send, 'urgent sender status')
h = one(h, "window.__ourFamilyConsumeNativeAction=function(){consumeNativeAction();consumeNativeNavigation();pollInbox();};",
        "window.__ourFamilyConsumeNativeAction=function(){consumeNativeAction();consumeNativeNavigation();pollInbox();};\nsetInterval(function(){if(activeDurakId&&durakGames[activeDurakId]&&durakGames[activeDurakId].status==='invited'&&!$('durak').classList.contains('hidden'))renderDurak();},2000);",
        'refresh invitation status')

html_path.write_text(h, encoding='utf-8')
main_path.write_text(main, encoding='utf-8')
service_path.write_text(service, encoding='utf-8')
build_path.write_text(build, encoding='utf-8')
manifest_path.write_text(manifest, encoding='utf-8')
print('Applied 6.0.23 reliable game invites and immediate urgent delivery')
