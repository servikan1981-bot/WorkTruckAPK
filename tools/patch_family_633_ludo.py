from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
H=ROOT/'duoapp/src/main/assets/index.html'
G=ROOT/'duoapp/build.gradle'
M=ROOT/'duoapp/src/main/AndroidManifest.xml'
h=H.read_text(encoding='utf-8')

if "APP_VERSION='6.0.33'" in h and 'id="hubLudoBtn"' in h:
    print('6.0.33 Ludo patch already applied')
    raise SystemExit(0)

def rep(old,new,label):
    global h
    if old not in h:
        raise SystemExit('Missing anchor: '+label)
    h=h.replace(old,new,1)

# Version text only; no existing behavior is removed.
h=h.replace('6.0.32','6.0.33')

LUDO_CSS=r'''
/* v6.0.33 — Семейная гонка (Ludo) */
.ludo-card{grid-column:1/-1;background:linear-gradient(135deg,#fff0f0 0%,#effdf5 34%,#eef5ff 68%,#fff7df 100%)!important;border:1px solid #dce5f2!important}.ludo-card .emoji{font-size:37px!important}
#ludo{display:flex;flex-direction:column;background:linear-gradient(160deg,#e8f2ff,#fff9e8 45%,#ecfff4)}.ludo-body{flex:1;overflow:auto;padding:10px 9px 18px;text-align:center}.ludo-players{display:flex;gap:7px;overflow:auto;padding:3px 1px 8px}.ludo-player-chip{min-width:102px;border-radius:14px;padding:8px 9px;background:var(--card);box-shadow:0 4px 12px rgba(25,40,70,.08);font-size:12px;font-weight:850;border:2px solid transparent}.ludo-player-chip.current{box-shadow:0 0 0 3px rgba(47,111,237,.16)}.ludo-player-chip.out{opacity:.45;text-decoration:line-through}.ludo-dot{display:inline-block;width:11px;height:11px;border-radius:50%;margin-right:5px;vertical-align:-1px}.ludo-board-wrap{width:min(100%,560px);margin:3px auto 8px;background:rgba(255,255,255,.83);border-radius:26px;padding:7px;box-shadow:0 15px 34px rgba(30,55,90,.16);border:1px solid rgba(255,255,255,.9)}.ludo-board-wrap svg{display:block;width:100%;height:auto;border-radius:20px}.ludo-token{cursor:default}.ludo-token.legal{cursor:pointer;animation:ludoPulse 1s infinite ease-in-out}.ludo-token.legal circle:first-child{stroke:#fff;stroke-width:6}.ludo-controls{display:flex;align-items:center;justify-content:center;gap:10px;max-width:560px;margin:8px auto}.ludo-dice{width:76px;height:76px;border:0;border-radius:22px;background:linear-gradient(145deg,#fff,#eaf0fa);box-shadow:0 10px 22px rgba(30,50,80,.18);font-size:45px;line-height:1}.ludo-dice:disabled{opacity:.46}.ludo-dice.rolling{animation:ludoRoll .38s ease}.ludo-side-btn{border:0;border-radius:15px;padding:13px 15px;background:var(--card);color:var(--ink);font-weight:850;box-shadow:0 5px 14px rgba(30,50,80,.10)}.ludo-side-btn.danger{color:#c93642}.ludo-hint{min-height:42px;font-weight:800;line-height:1.35;padding:3px 10px}.ludo-picklist{display:grid;gap:8px;margin:11px 0}.ludo-pickrow{display:flex;align-items:center;gap:10px;border:1px solid var(--line);background:var(--card);border-radius:13px;padding:10px 11px;font-weight:800}.ludo-pickrow input{width:21px;height:21px}.ludo-choice{grid-column:1/-1;background:linear-gradient(120deg,#e24a4a,#2f6fed)!important}.ludo-confetti{font-size:24px;letter-spacing:7px;margin:4px 0}
@keyframes ludoPulse{0%,100%{transform:scale(1)}50%{transform:scale(1.12)}}@keyframes ludoRoll{0%{transform:rotate(0) scale(1)}35%{transform:rotate(22deg) scale(1.12)}70%{transform:rotate(-18deg) scale(.96)}100%{transform:rotate(0) scale(1)}}
body[data-theme="dark"] #ludo{background:linear-gradient(160deg,#111d31,#282316 50%,#11271e)}body[data-theme="dark"] .ludo-board-wrap{background:#172130;border-color:#2b3748}
'''
rep('/* v5.1 themes */',LUDO_CSS+'\n/* v5.1 themes */','theme css marker')

old_hub='<div class="game-grid"><button id="hubCheckersBtn" class="game-card"><span class="emoji">♟</span><strong>Шашки</strong><span>Выберите соперника и начните партию</span></button><button id="hubDurakBtn" class="game-card durak-card"><span class="emoji">🃏</span><strong>Дурак</strong><span>Подкидной · 36 карт · 1 на 1</span></button></div>'
new_hub='<div class="game-grid"><button id="hubCheckersBtn" class="game-card"><span class="emoji">♟</span><strong>Шашки</strong><span>Выберите соперника и начните партию</span></button><button id="hubDurakBtn" class="game-card durak-card"><span class="emoji">🃏</span><strong>Дурак</strong><span>Подкидной · 36 карт · 1 на 1</span></button><button id="hubLudoBtn" class="game-card ludo-card"><span class="emoji">🎲</span><strong>Семейная гонка</strong><span>Красочное Лудо · 2–4 игрока</span></button></div>'
rep(old_hub,new_hub,'games hub')

LUDO_SCREEN=r'''
<section id="ludo" class="screen hidden">
  <div class="topbar"><button id="ludoBackBtn" class="iconbtn">‹</button><div class="who"><div id="ludoTitle" class="name">Семейная гонка</div><div id="ludoStatus" class="status"></div></div></div>
  <div class="ludo-body">
    <div id="ludoPlayers" class="ludo-players"></div>
    <div class="ludo-board-wrap"><div id="ludoBoard" aria-label="Игровое поле Семейной гонки"></div></div>
    <div id="ludoHint" class="ludo-hint"></div>
    <div class="ludo-controls"><button id="ludoDiceBtn" class="ludo-dice" type="button">🎲</button><button id="ludoRetryInviteBtn" class="ludo-side-btn hidden" type="button">Повторить приглашение</button><button id="ludoResignBtn" class="ludo-side-btn danger" type="button">Сдаться</button></div>
  </div>
</section>
'''
rep('<section id="durak" class="screen hidden">',LUDO_SCREEN+'\n<section id="durak" class="screen hidden">','ludo screen')

rep('<button id="pickDurakBtn" class="accept">Дурак</button>','<button id="pickDurakBtn" class="accept">Дурак</button><button id="pickLudoBtn" class="accept ludo-choice">🎲 Семейная гонка</button>','game choice ludo')

LUDO_PICKER=r'''
<div id="ludoPicker" class="game-request hidden">
  <strong>🎲 Семейная гонка</strong>
  <div class="muted">Выберите от 1 до 3 участников. Вместе с вами получится 2–4 игрока.</div>
  <div id="ludoPlayerList" class="ludo-picklist"></div>
  <div class="incoming-actions"><button id="ludoPickerCancel" class="decline">Отмена</button><button id="ludoStartBtn" class="accept">Пригласить</button></div>
</div>
'''
rep('<div id="checkersRequest" class="game-request hidden">',LUDO_PICKER+'\n<div id="checkersRequest" class="game-request hidden">','ludo picker')
rep('<script src="durak.js"></script>','<script src="durak.js"></script>\n<script src="ludo.js"></script>','ludo script')
rep("var durakGames={},activeDurakId='',pendingDurakAcceptId='',durakSending=false,gamePickKind='',gamePickPeer='';","var durakGames={},activeDurakId='',pendingDurakAcceptId='',durakSending=false,gamePickKind='',gamePickPeer='';\nvar ludoGames={},activeLudoId='',pendingLudoAcceptId='',ludoSending=false;",'ludo globals')

LUDO_JS=r'''
var LUDO_COLORS=['#e84b55','#20a96b','#3478e5','#e7a51d'];
var LUDO_PALE=['#ffe1e4','#dcf8e9','#dfeaff','#fff0c9'];
function saveLudoGames(){try{localStorage.setItem('of633_ludo_'+ownTag,JSON.stringify(Object.values(ludoGames).slice(-30)));}catch(e){toast('Не удалось сохранить Семейную гонку');}}
function loadLudoGames(){ludoGames={};try{var a=JSON.parse(localStorage.getItem('of633_ludo_'+ownTag)||'[]');if(Array.isArray(a))a.forEach(function(g){if(g&&/^[a-f0-9]{32}$/.test(g.id)&&Array.isArray(g.players)&&g.players.includes(role)&&g.state){if(!Array.isArray(g.state.out))g.state.out=Array(g.players.length).fill(false);ludoGames[g.id]=g;}});}catch(e){}showLudoRequest();}
function ludoSide(g){return g.players.indexOf(role);}
function ludoPeers(g){return g.players.filter(function(x){return x!==role;});}
function ludoAllAccepted(g){return g.players.every(function(p){return g.accepted&&g.accepted[p]===true;});}
function ludoDie(d){return ['🎲','⚀','⚁','⚂','⚃','⚄','⚅'][Number(d)||0]||'🎲';}
function ludoRandomDie(){try{var a=new Uint32Array(1);crypto.getRandomValues(a);return (a[0]%6)+1;}catch(e){return Math.floor(Math.random()*6)+1;}}
function openLudoPicker(seedPeer){$('gameChoice').classList.add('hidden');$('opponentChoice').classList.add('hidden');var box=$('ludoPlayerList');box.innerHTML='';MEMBERS.filter(function(m){return m.id!==role;}).forEach(function(m){var row=document.createElement('label');row.className='ludo-pickrow';var cb=document.createElement('input');cb.type='checkbox';cb.dataset.peer=m.id;cb.checked=!!seedPeer&&m.id===seedPeer;var text=document.createElement('span');text.textContent=(onlineNow(m.id)?'🟢 ':'⚪ ')+NAMES[m.id];row.appendChild(cb);row.appendChild(text);box.appendChild(row);});$('ludoPicker').classList.remove('hidden');}
async function inviteLudo(peers){peers=Array.from(new Set((peers||[]).filter(function(p){return p&&p!==role&&NAMES[p];}))).slice(0,3);if(!peers.length){toast('Выберите хотя бы одного участника');return;}var players=[role].concat(peers),same=Object.values(ludoGames).find(function(g){return (g.status==='active'||g.status==='invited')&&g.players.slice().sort().join('|')===players.slice().sort().join('|')&&g.state.winner===null;});if(same){$('ludoPicker').classList.add('hidden');openLudo(same.id);if(same.status==='invited'&&same.inviter===role)sendLudoInvites(same);return;}var id=randomId(),accepted={};players.forEach(function(p){accepted[p]=p===role;});var g={id:id,players:players,inviter:role,status:'invited',accepted:accepted,updated:Date.now(),state:LudoRules.initial(players.length)};ludoGames[id]=g;saveLudoGames();$('ludoPicker').classList.add('hidden');openLudo(id);await sendLudoInvites(g);}
async function sendLudoInvites(g){var data={gameType:'ludo',gameId:g.id,players:g.players,accepted:g.accepted,state:g.state};var pending=g.players.filter(function(p){return p!==role&&!(g.accepted&&g.accepted[p]);});if(!pending.length)pending=ludoPeers(g);await Promise.allSettled(pending.map(function(peer){return publishEnvelope(peer,'game_invite',data,5,'ludo_invite',g.id+'_'+peer,true);}));toast('Приглашение в Семейную гонку отправлено');if(activeLudoId===g.id)renderLudo();}
function showLudoRequest(){var waiting=Object.values(ludoGames).filter(function(g){return g.status==='invited'&&g.inviter!==role&&g.players.includes(role)&&!(g.accepted&&g.accepted[role])&&Date.now()-g.updated<86400000;}).sort(function(a,b){return b.updated-a.updated;})[0];pendingLudoAcceptId=waiting?waiting.id:'';if(!waiting)return;$('checkersRequestText').textContent=NAMES[waiting.inviter]+' приглашает в Семейную гонку · '+waiting.players.length+' игрока';$('checkersRequest').classList.remove('hidden');}
async function acceptLudo(id){var g=ludoGames[id];if(!g||g.status!=='invited'||g.inviter===role)return;if(!g.accepted)g.accepted={};g.accepted[role]=true;g.updated=Date.now();pendingLudoAcceptId='';$('checkersRequest').classList.add('hidden');saveLudoGames();openLudo(id);try{await publishEnvelope(g.inviter,'game_accept',{gameType:'ludo',gameId:id,player:role},5,'ludo_accept',id+'_'+role,true);}catch(e){toast('Ответ сохранён. Он отправится при восстановлении связи.');}}
async function declineLudo(id){var g=ludoGames[id];if(!g)return;g.status='declined';g.updated=Date.now();pendingLudoAcceptId='';$('checkersRequest').classList.add('hidden');saveLudoGames();try{await publishEnvelope(g.inviter,'game_decline',{gameType:'ludo',gameId:id,player:role},5,'ludo_decline',id+'_'+role,true);}catch(e){}}
async function broadcastLudo(g,kind,extra){var data=Object.assign({gameType:'ludo',gameId:g.id,state:g.state,seq:g.state.seq,accepted:g.accepted},extra||{});return Promise.allSettled(ludoPeers(g).map(function(peer){return publishEnvelope(peer,kind,data,4,'ludo_'+kind,g.id+'_'+g.state.seq+'_'+peer,true);}));}
function ludoStateOk(g,s){return !!s&&Array.isArray(s.pieces)&&s.pieces.length===g.players.length&&Number.isInteger(Number(s.seq))&&Number(s.seq)>=0;}
function receiveLudo(p){var d=p.data||{},id=d.gameId,peer=p.from;if(!/^[a-f0-9]{32}$/.test(id||''))return;var g=ludoGames[id];
 if(p.kind==='game_invite'){
  if(Date.now()-p.ts>86400000||!Array.isArray(d.players)||d.players.length<2||d.players.length>4||d.players.indexOf(role)<0||d.players[0]!==peer||!ludoStateOk({players:d.players},d.state))return;
  if(g){if(g.status==='invited'&&g.accepted&&g.accepted[role])publishEnvelope(g.inviter,'game_accept',{gameType:'ludo',gameId:id,player:role},5,'ludo_accept_retry',id+'_'+role,true).catch(function(){});return;}
  var accepted=d.accepted&&typeof d.accepted==='object'?d.accepted:{};accepted[peer]=true;accepted[role]=false;g={id:id,players:d.players.slice(),inviter:peer,status:'invited',accepted:accepted,updated:Date.now(),state:d.state};if(!Array.isArray(g.state.out))g.state.out=Array(g.players.length).fill(false);ludoGames[id]=g;saveLudoGames();showLudoRequest();return;
 }
 if(!g)return;
 if(p.kind==='game_accept'){
  if(role===g.inviter&&!d.allAccepted&&g.players.indexOf(peer)>0){if(!g.accepted)g.accepted={};g.accepted[peer]=true;g.updated=Date.now();if(ludoAllAccepted(g)){g.status='active';saveLudoGames();broadcastLudo(g,'game_accept',{allAccepted:true}).catch(function(){});}else saveLudoGames();if(activeLudoId===id)renderLudo();return;}
  if(peer===g.inviter&&d.allAccepted){if(d.accepted)g.accepted=d.accepted;g.status='active';g.updated=Date.now();saveLudoGames();if(activeLudoId===id)renderLudo();return;}
 }
 if(p.kind==='game_decline'){g.status='declined';g.updated=Date.now();saveLudoGames();if(role===g.inviter)broadcastLudo(g,'game_decline',{by:peer}).catch(function(){});if(activeLudoId===id)renderLudo();return;}
 if((p.kind==='game_move'||p.kind==='game_resign')&&ludoStateOk(g,d.state)&&Number(d.state.seq)>Number(g.state.seq)){var peerSide=g.players.indexOf(peer),last=d.state.last||{};if(peerSide<0||Number(last.player)!==peerSide)return;g.state=d.state;if(!Array.isArray(g.state.out))g.state.out=Array(g.players.length).fill(false);g.status=g.state.winner===null?'active':'ended';g.updated=Date.now();saveLudoGames();if(activeLudoId===id)renderLudo();return;}
}
function ludoTrackPoint(i){var a=-Math.PI/2+(i/52)*Math.PI*2,r=216;return {x:300+Math.cos(a)*r,y:300+Math.sin(a)*r};}
function ludoBasePoint(p,i){var c=[[105,105],[495,105],[495,495],[105,495]][p]||[105,105],o=[[-26,-26],[26,-26],[-26,26],[26,26]][i]||[0,0];return{x:c[0]+o[0],y:c[1]+o[1]};}
function ludoTokenPoint(p,progress,piece){if(progress<0)return ludoBasePoint(p,piece);var start=ludoTrackPoint(p*13);if(progress<=51){var q=ludoTrackPoint(LudoRules.absPos(p,progress)),ox=(piece%2?5:-5),oy=(piece<2?-5:5);return{x:q.x+ox,y:q.y+oy};}if(progress<58){var t=(progress-51)/7;return{x:start.x+(300-start.x)*t,y:start.y+(300-start.y)*t};}var bases=[[252,252],[348,252],[348,348],[252,348]],b=bases[p]||bases[0],oo=[[-10,-10],[10,-10],[-10,10],[10,10]][piece];return{x:b[0]+oo[0],y:b[1]+oo[1]};}
function renderLudoBoard(g){var s=g.state,side=ludoSide(g),legal=(g.status==='active'&&s.winner===null&&s.turn===side&&s.dice!==null)?LudoRules.movable(s,side,s.dice):[],z=[];z.push('<svg viewBox="0 0 600 600" role="img" aria-label="Поле Семейной гонки"><defs><filter id="ltShadow" x="-40%" y="-40%" width="180%" height="180%"><feDropShadow dx="0" dy="5" stdDeviation="5" flood-opacity=".28"/></filter><radialGradient id="lbg"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#eef4fb"/></radialGradient></defs><rect x="8" y="8" width="584" height="584" rx="34" fill="url(#lbg)" stroke="#dce5ef" stroke-width="4"/>');var bases=[[105,105],[495,105],[495,495],[105,495]];for(var p=0;p<s.players;p++){var bc=bases[p];z.push('<circle cx="'+bc[0]+'" cy="'+bc[1]+'" r="74" fill="'+LUDO_PALE[p]+'" stroke="'+LUDO_COLORS[p]+'" stroke-width="5" opacity=".92"/>');var st=ludoTrackPoint(p*13);for(var k=1;k<=6;k++){var t=k/7,x=st.x+(300-st.x)*t,y=st.y+(300-st.y)*t;z.push('<circle cx="'+x.toFixed(1)+'" cy="'+y.toFixed(1)+'" r="13" fill="'+LUDO_PALE[p]+'" stroke="'+LUDO_COLORS[p]+'" stroke-width="3"/>');}}
 for(var i=0;i<52;i++){var q=ludoTrackPoint(i),owner=[0,13,26,39].indexOf(i),fill=owner>=0&&owner<s.players?LUDO_PALE[owner]:'#fff',stroke=owner>=0&&owner<s.players?LUDO_COLORS[owner]:(LudoRules.SAFE.indexOf(i)>=0?'#7b8aa0':'#c9d3df');z.push('<circle cx="'+q.x.toFixed(1)+'" cy="'+q.y.toFixed(1)+'" r="12.5" fill="'+fill+'" stroke="'+stroke+'" stroke-width="'+(owner>=0?4:2)+'"/>');if(LudoRules.SAFE.indexOf(i)>=0)z.push('<text x="'+q.x.toFixed(1)+'" y="'+(q.y+4).toFixed(1)+'" text-anchor="middle" font-size="10" fill="#65758a">★</text>');}
 z.push('<circle cx="300" cy="300" r="66" fill="#fff" stroke="#d8e1ec" stroke-width="4"/><text x="300" y="289" text-anchor="middle" font-size="25">🏆</text><text x="300" y="318" text-anchor="middle" font-size="17" font-weight="800" fill="#32445c">ФИНИШ</text>');
 for(var pp=0;pp<s.players;pp++)for(var j=0;j<4;j++){var pos=ludoTokenPoint(pp,s.pieces[pp][j],j),isLegal=pp===side&&legal.indexOf(j)>=0,cls='ludo-token'+(isLegal?' legal':'');z.push('<g class="'+cls+'" '+(isLegal?'data-piece="'+j+'"':'')+'><circle cx="'+pos.x.toFixed(1)+'" cy="'+pos.y.toFixed(1)+'" r="18" fill="'+LUDO_COLORS[pp]+'" stroke="#fff" stroke-width="4" filter="url(#ltShadow)"/><circle cx="'+(pos.x-6).toFixed(1)+'" cy="'+(pos.y-7).toFixed(1)+'" r="5" fill="#fff" opacity=".34"/><text x="'+pos.x.toFixed(1)+'" y="'+(pos.y+6).toFixed(1)+'" text-anchor="middle" font-size="15" font-weight="900" fill="#fff">'+(j+1)+'</text></g>');}
 z.push('</svg>');$('ludoBoard').innerHTML=z.join('');Array.from($('ludoBoard').querySelectorAll('.ludo-token.legal')).forEach(function(el){el.addEventListener('click',function(){ludoMove(Number(el.dataset.piece));});});}
function renderLudo(){var g=ludoGames[activeLudoId];if(!g)return;var s=g.state,side=ludoSide(g);if(!Array.isArray(s.out))s.out=Array(g.players.length).fill(false);$('ludoTitle').textContent='Семейная гонка · '+g.players.length+' игрока';var pb=$('ludoPlayers');pb.innerHTML='';g.players.forEach(function(p,i){var chip=document.createElement('div');chip.className='ludo-player-chip'+(s.turn===i&&g.status==='active'&&s.winner===null?' current':'')+(s.out[i]?' out':'');chip.style.borderColor=LUDO_COLORS[i];var dot=document.createElement('span');dot.className='ludo-dot';dot.style.background=LUDO_COLORS[i];chip.appendChild(dot);chip.appendChild(document.createTextNode(NAMES[p]+(g.status==='invited'?' '+((g.accepted&&g.accepted[p])?'✓':'…'):'')));pb.appendChild(chip);});renderLudoBoard(g);var status='',hint='';if(g.status==='invited'){var wait=g.players.filter(function(p){return !(g.accepted&&g.accepted[p]);}).map(function(p){return NAMES[p];});status='Ожидаем игроков';hint=wait.length?'Ждём: '+wait.join(', '):'Запускаем игру…';}else if(g.status==='declined'){status='Игра отменена';hint='Один из участников отклонил приглашение.';}else if(s.winner!==null){g.status='ended';var winner=g.players[s.winner];status='🏆 Победитель: '+NAMES[winner];hint=(s.winner===side?'🎉 Поздравляем! Вы выиграли Семейную гонку!':'Победил '+NAMES[winner]+'. Можно сыграть ещё раз.');awardGameOnce(g.id,s.winner===side?'win':'loss');saveLudoGames();}else if(s.out[side]){status='Вы вышли из партии';hint='Партия продолжается у остальных участников.';}else if(s.turn===side){status='Ваш ход';hint=s.dice===null?'Нажмите на кубик, чтобы бросить.':'Выпало '+s.dice+' — выберите подсвеченную фишку.';}else{status='Ход: '+NAMES[g.players[s.turn]];hint='Ждём ход '+NAMES[g.players[s.turn]]+'.';}$('ludoStatus').textContent=status;$('ludoHint').textContent=hint;$('ludoDiceBtn').textContent=ludoDie(s.dice);$('ludoDiceBtn').disabled=!(g.status==='active'&&s.winner===null&&!s.out[side]&&s.turn===side&&s.dice===null);$('ludoResignBtn').classList.toggle('hidden',!(g.status==='active'&&s.winner===null&&!s.out[side]));$('ludoRetryInviteBtn').classList.toggle('hidden',!(g.status==='invited'&&g.inviter===role));}
function openLudo(id){var g=ludoGames[id];if(!g)return;activeLudoId=id;$('checkersRequest').classList.add('hidden');showScreen('ludo');renderLudo();}
async function sendLudoState(g,next,kind){if(!next||ludoSending)return;ludoSending=true;g.state=next;g.status=next.winner===null?'active':'ended';g.updated=Date.now();saveLudoGames();renderLudo();try{await broadcastLudo(g,kind||'game_move');}catch(e){toast('Ход сохранён. Отправка продолжится в фоне.');}finally{ludoSending=false;}}
function ludoRoll(){var g=ludoGames[activeLudoId];if(!g||g.status!=='active')return;var side=ludoSide(g);if(g.state.turn!==side||g.state.dice!==null||g.state.out[side])return;var btn=$('ludoDiceBtn');btn.classList.remove('rolling');void btn.offsetWidth;btn.classList.add('rolling');var d=ludoRandomDie(),next=LudoRules.roll(g.state,side,d);sendLudoState(g,next,'game_move');}
function ludoMove(piece){var g=ludoGames[activeLudoId];if(!g||g.status!=='active')return;var side=ludoSide(g),next=LudoRules.move(g.state,side,piece);if(next)sendLudoState(g,next,'game_move');else toast('Этой фишкой сейчас ходить нельзя');}
async function resignLudo(){var g=ludoGames[activeLudoId];if(!g||g.status!=='active'||g.state.winner!==null)return;var side=ludoSide(g);if(g.state.out[side]||!confirm('Сдаться в Семейной гонке?'))return;awardGameOnce(g.id,'surrender');var next=LudoRules.resign(g.state,side);await sendLudoState(g,next,'game_resign');}
'''
rep('function openOpponentChoice(kind){',LUDO_JS+'\nfunction openOpponentChoice(kind){','ludo javascript')
rep("function launchGame(kind,peer){if(kind==='checkers'){var prev=currentThread;currentThread={type:'direct',peer:peer};inviteCheckers().finally(function(){currentThread=prev;});}else inviteDurak(peer);}","function launchGame(kind,peer){if(kind==='checkers'){var prev=currentThread;currentThread={type:'direct',peer:peer};inviteCheckers().finally(function(){currentThread=prev;});}else if(kind==='durak')inviteDurak(peer);else if(kind==='ludo')openLudoPicker(peer);}",'launch game')

new_hub_fn="""function openGamesHub(){renderGameProfile();var box=$('gameSessions');box.innerHTML='';var items=Object.values(games).map(function(g){return {g:g,type:'checkers'};}).concat(Object.values(durakGames).map(function(g){return {g:g,type:'durak'};})).concat(Object.values(ludoGames).map(function(g){return {g:g,type:'ludo'};}));items.filter(function(item){return item.g.status==='active'||item.g.status==='invited';}).sort(function(a,b){return b.g.updated-a.g.updated;}).forEach(function(item){var b=document.createElement('button');b.className='game-entry';if(item.type==='ludo'){var others=item.g.players.filter(function(x){return x!==role;}).map(function(x){return NAMES[x];}).join(', ');b.textContent='🎲 Семейная гонка · '+others+' · '+(item.g.status==='active'?'Продолжить':'Ожидание');b.onclick=function(){openLudo(item.g.id);};}else{b.textContent=(item.type==='durak'?'🃏 Дурак':'♟ Шашки')+' · '+NAMES[item.g.players.find(function(x){return x!==role;})]+' · '+(item.g.status==='active'?'Продолжить':'Ожидание');b.onclick=function(){item.type==='durak'?openDurak(item.g.id):openCheckers(item.g.id);};}box.appendChild(b);});if(!box.children.length)box.textContent='Пока нет активных партий';showScreen('gamesHub');}"""
h,n=re.subn(r"function openGamesHub\(\)\{.*?showScreen\('gamesHub'\);\}",new_hub_fn,h,count=1,flags=re.S)
if n!=1: raise SystemExit('Missing anchor: openGamesHub')

old_route="if(['game_invite','game_accept','game_decline','game_move','game_resign'].includes(p.kind)){if((d.gameType||'checkers')==='durak')receiveDurak(p);else receiveCheckers(p);return;}"
new_route="if(['game_invite','game_accept','game_decline','game_move','game_resign'].includes(p.kind)){if((d.gameType||'checkers')==='durak')receiveDurak(p);else if(d.gameType==='ludo')receiveLudo(p);else receiveCheckers(p);return;}"
rep(old_route,new_route,'game envelope router')
rep('loadGames();loadDurakGames();renderHome();','loadGames();loadDurakGames();loadLudoGames();renderHome();','load Ludo games')
rep("$('gamesBackBtn').addEventListener('click',function(){showScreen('home');renderHome();});$('hubCheckersBtn').addEventListener('click',function(){openOpponentChoice('checkers');});$('hubDurakBtn').addEventListener('click',function(){openOpponentChoice('durak');});","$('gamesBackBtn').addEventListener('click',function(){showScreen('home');renderHome();});$('hubCheckersBtn').addEventListener('click',function(){openOpponentChoice('checkers');});$('hubDurakBtn').addEventListener('click',function(){openOpponentChoice('durak');});$('hubLudoBtn').addEventListener('click',function(){openLudoPicker('');});",'hub ludo listener')
rep("$('pickCheckersBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('checkers',p);else openOpponentChoice('checkers');});$('pickDurakBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('durak',p);else openOpponentChoice('durak');});$('gameChoiceCancel').addEventListener('click',function(){$('gameChoice').classList.add('hidden');});$('opponentCancel').addEventListener('click',function(){$('opponentChoice').classList.add('hidden');});","$('pickCheckersBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('checkers',p);else openOpponentChoice('checkers');});$('pickDurakBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('durak',p);else openOpponentChoice('durak');});$('pickLudoBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');openLudoPicker(p);});$('gameChoiceCancel').addEventListener('click',function(){$('gameChoice').classList.add('hidden');});$('opponentCancel').addEventListener('click',function(){$('opponentChoice').classList.add('hidden');});$('ludoPickerCancel').addEventListener('click',function(){$('ludoPicker').classList.add('hidden');});$('ludoStartBtn').addEventListener('click',function(){var peers=Array.from($('ludoPlayerList').querySelectorAll('input[type=checkbox]:checked')).map(function(x){return x.dataset.peer;});inviteLudo(peers);});",'choice listeners')
rep("$('durakBackBtn').addEventListener('click',function(){activeDurakId='';openGamesHub();});","$('ludoBackBtn').addEventListener('click',function(){activeLudoId='';openGamesHub();});$('ludoDiceBtn').addEventListener('click',ludoRoll);$('ludoResignBtn').addEventListener('click',resignLudo);$('ludoRetryInviteBtn').addEventListener('click',function(){var g=ludoGames[activeLudoId];if(g&&g.status==='invited'&&g.inviter===role)sendLudoInvites(g);});\n$('durakBackBtn').addEventListener('click',function(){activeDurakId='';openGamesHub();});",'ludo screen listeners')
rep("$('checkersAcceptBtn').addEventListener('click',function(){if(pendingDurakAcceptId)acceptDurak(pendingDurakAcceptId);else acceptCheckers(pendingGameAcceptId);});","$('checkersAcceptBtn').addEventListener('click',function(){if(pendingLudoAcceptId)acceptLudo(pendingLudoAcceptId);else if(pendingDurakAcceptId)acceptDurak(pendingDurakAcceptId);else acceptCheckers(pendingGameAcceptId);});",'accept ludo')
rep("$('checkersDeclineBtn').addEventListener('click',function(){if(pendingDurakAcceptId)declineDurak(pendingDurakAcceptId);else declineCheckers(pendingGameAcceptId);});","$('checkersDeclineBtn').addEventListener('click',function(){if(pendingLudoAcceptId)declineLudo(pendingLudoAcceptId);else if(pendingDurakAcceptId)declineDurak(pendingDurakAcceptId);else declineCheckers(pendingGameAcceptId);});",'decline ludo')

H.write_text(h,encoding='utf-8')

g=G.read_text(encoding='utf-8')
if "versionCode 6032" not in g or "versionName '6.0.32'" not in g: raise SystemExit('Unexpected build.gradle version')
g=g.replace('versionCode 6032','versionCode 6033',1).replace("versionName '6.0.32'","versionName '6.0.33'",1)
G.write_text(g,encoding='utf-8')

m=M.read_text(encoding='utf-8')
if 'Наша семья 6.0.32' in m:m=m.replace('Наша семья 6.0.32','Наша семья 6.0.33')
elif 'Наша семья 6.0.33' not in m: raise SystemExit('Unexpected manifest label')
M.write_text(m,encoding='utf-8')

print('Patched OurFamily 6.0.33 with Family Race Ludo only')
