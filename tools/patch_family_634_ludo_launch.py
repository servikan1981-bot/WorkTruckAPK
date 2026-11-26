from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
H = ROOT / 'duoapp/src/main/assets/index.html'
G = ROOT / 'duoapp/build.gradle'
M = ROOT / 'duoapp/src/main/AndroidManifest.xml'

h = H.read_text(encoding='utf-8')
g = G.read_text(encoding='utf-8')
m = M.read_text(encoding='utf-8')

if "APP_VERSION='6.0.34'" in h:
    print('6.0.34 hotfix already applied')
    raise SystemExit(0)

# Version bump.
if 'versionCode 6033' not in g or "versionName '6.0.33'" not in g:
    raise SystemExit('Expected 6.0.33 Gradle version not found')
g = g.replace('versionCode 6033', 'versionCode 6034', 1)
g = g.replace("versionName '6.0.33'", "versionName '6.0.34'", 1)
m = m.replace('6.0.33', '6.0.34')
h = h.replace('6.0.33', '6.0.34')

# ROOT CAUSE: 6.0.33 omitted the Ludo section from showScreen().
old_screens = "['setup','home','picker','chat','news','more','adminArchive','adminChat','checkers','gamesHub','durak'].forEach(function(x){$(x).classList.toggle('hidden',x!==id);});"
new_screens = "['setup','home','picker','chat','news','more','adminArchive','adminChat','checkers','gamesHub','durak','ludo'].forEach(function(x){$(x).classList.toggle('hidden',x!==id);});"
if old_screens not in h:
    raise SystemExit('showScreen 6.0.33 anchor not found')
h = h.replace(old_screens, new_screens, 1)

# Game overlays must close when a real screen opens.
old_panels = "['attachMenu','emojiPanel','newsCaptureMenu','relayModal','turnModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer'].forEach(function(id){"
new_panels = "['attachMenu','emojiPanel','newsCaptureMenu','relayModal','turnModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer','ludoPicker','gameChoice','opponentChoice'].forEach(function(id){"
if old_panels not in h:
    raise SystemExit('hideTransientPanels anchor not found')
h = h.replace(old_panels, new_panels, 1)

# Live player count in the picker.
old_hint = '<div class="muted">Выберите от 1 до 3 участников. Вместе с вами получится 2–4 игрока.</div>'
new_hint = '<div id="ludoPickerHint" class="muted">Выберите от 1 до 3 участников. Вместе с вами получится 2–4 игрока.</div>'
if old_hint not in h:
    raise SystemExit('Ludo picker hint anchor not found')
h = h.replace(old_hint, new_hint, 1)

# Enforce 1-3 invitees in the UI instead of allowing arbitrary checks.
picker_pattern = re.compile(
    r"function openLudoPicker\(seedPeer\)\{.*?\}\nasync function inviteLudo",
    re.S,
)
picker_replacement = r'''function openLudoPicker(seedPeer){
 $('gameChoice').classList.add('hidden');$('opponentChoice').classList.add('hidden');
 var box=$('ludoPlayerList');box.innerHTML='';
 function selectedCount(){return box.querySelectorAll('input[type=checkbox]:checked').length;}
 function updateHint(){var n=selectedCount(),hint=$('ludoPickerHint');if(hint)hint.textContent=n?'Выбрано: '+n+' из 3. Вместе с вами: '+(n+1)+' игрока.':'Выберите от 1 до 3 участников. Вместе с вами получится 2–4 игрока.';}
 MEMBERS.filter(function(m){return m.id!==role;}).forEach(function(m){
   var row=document.createElement('label');row.className='ludo-pickrow';
   var cb=document.createElement('input');cb.type='checkbox';cb.dataset.peer=m.id;cb.checked=!!seedPeer&&m.id===seedPeer;
   cb.addEventListener('change',function(){if(cb.checked&&selectedCount()>3){cb.checked=false;toast('Для Семейной гонки можно выбрать максимум 3 участников');}updateHint();});
   var text=document.createElement('span');text.textContent=(onlineNow(m.id)?'🟢 ':'⚪ ')+NAMES[m.id];
   row.appendChild(cb);row.appendChild(text);box.appendChild(row);
 });
 updateHint();$('ludoPicker').classList.remove('hidden');
}
async function inviteLudo'''
h, count = picker_pattern.subn(picker_replacement, h, count=1)
if count != 1:
    raise SystemExit('openLudoPicker replacement failed')

# Make Invite explicitly validate, show progress and surface runtime errors.
start_pattern = re.compile(
    r"\$\('ludoStartBtn'\)\.addEventListener\('click',function\(\)\{var peers=.*?inviteLudo\(peers\);\}\);",
    re.S,
)
start_replacement = r'''$('ludoStartBtn').addEventListener('click',async function(e){
 e.preventDefault();e.stopPropagation();
 var btn=this,peers=Array.from($('ludoPlayerList').querySelectorAll('input[type=checkbox]:checked')).map(function(x){return x.dataset.peer;});
 if(!peers.length){toast('Выберите хотя бы одного участника');return;}
 if(peers.length>3){toast('Для Семейной гонки можно выбрать максимум 3 участников');return;}
 btn.disabled=true;btn.textContent='Запускаем…';
 try{await inviteLudo(peers);}catch(err){console.error('Ludo start failed',err);toast('Не удалось запустить Семейную гонку. Повторите ещё раз.');}
 finally{btn.disabled=false;btn.textContent='Пригласить';}
});'''
h, count = start_pattern.subn(start_replacement, h, count=1)
if count != 1:
    raise SystemExit('Ludo start button replacement failed')

# Hardware Back must leave Ludo normally.
back_anchor = " if(!$('picker').classList.contains('hidden')){showScreen(currentThread?'chat':'home');return true;}"
if back_anchor not in h:
    raise SystemExit('Back navigation anchor not found')
h = h.replace(
    back_anchor,
    " if(!$('ludo').classList.contains('hidden')){activeLudoId='';openGamesHub();return true;}\n" + back_anchor,
    1,
)

H.write_text(h, encoding='utf-8')
G.write_text(g, encoding='utf-8')
M.write_text(m, encoding='utf-8')
print('Patched OurFamily 6.0.34: Ludo screen launch + player limit + visible errors')
