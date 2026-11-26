from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
html_path=ROOT/'duoapp/src/main/assets/index.html'
build_path=ROOT/'duoapp/build.gradle'
manifest_path=ROOT/'duoapp/src/main/AndroidManifest.xml'
h=html_path.read_text(encoding='utf-8')

if "APP_VERSION='6.0.37'" not in h:
    raise SystemExit('Expected 6.0.37 base not found')
if 'Семейная гонка' not in h or 'hubLudoBtn' not in h:
    raise SystemExit('Ludo base markers not found')
if 'attachmentDownloadPending' not in h or 'getAttachmentObjectUrl' not in h or 'rememberMediaState' not in h:
    raise SystemExit('6.0.37 persistent media cache markers missing; refusing to patch')

# Version only; preserve 6.0.37 media/video code byte-for-byte outside targeted game areas.
h=h.replace('<title>Наша семья 6.0.37</title>','<title>Наша семья 6.0.38</title>',1)
h=h.replace("APP_VERSION='6.0.37'","APP_VERSION='6.0.38'",1)

pool_css=r'''/* v6.0.38 — American Pool 8-ball */
.pool-card{grid-column:1/-1;background:linear-gradient(135deg,#07291e 0%,#0b593c 45%,#17233c 100%)!important;border:1px solid #26755b!important;color:#fff!important}.pool-card .emoji{font-size:38px!important}.pool-card span{color:#d8f5e9!important}
#pool{display:flex;flex-direction:column;background:radial-gradient(circle at 50% 15%,#173a2d 0,#07120f 68%,#040806 100%);color:#f4fff9}.pool-body{flex:1;overflow:auto;padding:9px 8px 18px}.pool-score{display:grid;grid-template-columns:1fr 1fr;gap:8px;max-width:760px;margin:0 auto 8px}.pool-player{border:1px solid rgba(255,255,255,.15);background:rgba(0,0,0,.28);border-radius:15px;padding:9px 11px;min-width:0}.pool-player.current{box-shadow:0 0 0 2px #efc85b,0 0 24px rgba(239,200,91,.22)}.pool-player strong{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.pool-group{font-size:11px;opacity:.76;margin-top:3px}.pool-table-shell{max-width:760px;margin:0 auto;padding:7px;border-radius:22px;background:linear-gradient(145deg,#9b6736,#4e2c15 48%,#b47b43);box-shadow:0 18px 40px rgba(0,0,0,.43),inset 0 1px 0 rgba(255,255,255,.25)}.pool-table-shell canvas{display:block;width:100%;aspect-ratio:2/1;border-radius:16px;touch-action:none;background:#07563a}.pool-hint{max-width:760px;min-height:46px;margin:8px auto 3px;text-align:center;font-weight:800;line-height:1.35;color:#eaf8f2}.pool-power{max-width:760px;margin:6px auto;background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.12);border-radius:15px;padding:10px 12px;display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:10px}.pool-power label{font-size:12px;font-weight:850}.pool-power input{width:100%;accent-color:#efc85b}.pool-power strong{min-width:42px;text-align:right}.pool-actions{display:flex;gap:9px;max-width:760px;margin:8px auto}.pool-actions button{flex:1;border:0;border-radius:14px;padding:13px;font-weight:900}.pool-hit{background:linear-gradient(135deg,#f4d56f,#d9a52f);color:#302006;box-shadow:0 7px 16px rgba(218,166,48,.25)}.pool-hit:disabled{opacity:.38}.pool-secondary{background:rgba(255,255,255,.10);color:#fff;border:1px solid rgba(255,255,255,.14)!important}.pool-danger{background:#7f2730;color:#fff}.pool-balls{max-width:760px;margin:4px auto 2px;display:flex;justify-content:center;gap:4px;flex-wrap:wrap}.pool-mini-ball{width:19px;height:19px;border-radius:50%;display:grid;place-items:center;font-size:8px;font-weight:950;border:1px solid rgba(255,255,255,.72);box-shadow:0 2px 4px rgba(0,0,0,.3);color:#111}.pool-mini-ball.done{opacity:.18;filter:grayscale(1)}
body[data-theme="dark"] #pool{background:radial-gradient(circle at 50% 10%,#183b2e,#050908 72%)}
'''
h,n=re.subn(r'/\* v6\.0\.37 — Семейная гонка \(Ludo\) \*/.*?(?=/\* v5\.1 themes \*/)',pool_css+'\n',h,flags=re.S)
if n!=1: raise SystemExit(f'CSS ludo block replacements={n}')

h=h.replace('<button id="hubLudoBtn" class="game-card ludo-card"><span class="emoji">🎲</span><strong>Семейная гонка</strong><span>Красочное Лудо · 2–4 игрока</span></button>',
'''<button id="hubPoolBtn" class="game-card pool-card"><span class="emoji">🎱</span><strong>Бильярд 8-ball</strong><span>Американский пул · 1 на 1 · 3D-стол</span></button>''',1)

pool_section=r'''<section id="pool" class="screen hidden">
  <div class="topbar"><button id="poolBackBtn" class="iconbtn">‹</button><div class="who"><div id="poolTitle" class="name">Бильярд · 8-ball</div><div id="poolStatus" class="status"></div></div></div>
  <div class="pool-body">
    <div id="poolPlayers" class="pool-score"></div>
    <div class="pool-table-shell"><canvas id="poolCanvas" width="1000" height="500" aria-label="Стол для американского бильярда"></canvas></div>
    <div id="poolBalls" class="pool-balls"></div>
    <div id="poolHint" class="pool-hint">Коснитесь стола, чтобы выбрать направление.</div>
    <div class="pool-power"><label for="poolPower">Сила</label><input id="poolPower" type="range" min="12" max="100" value="62"><strong id="poolPowerValue">62%</strong></div>
    <div class="pool-actions"><button id="poolHitBtn" class="pool-hit" type="button">🎱 УДАР</button><button id="poolRetryInviteBtn" class="pool-secondary hidden" type="button">Повторить приглашение</button><button id="poolResignBtn" class="pool-danger" type="button">Сдаться</button></div>
  </div>
</section>

<section id="durak"'''
h,n=re.subn(r'<section id="ludo".*?</section>\s*\n\s*<section id="durak"',pool_section,h,count=1,flags=re.S)
if n!=1: raise SystemExit(f'Ludo section replacements={n}')

h=h.replace('<button id="pickLudoBtn" class="ludo-choice">🎲 Семейная гонка</button>',
            '<button id="pickPoolBtn" class="pool-choice">🎱 Бильярд 8-ball</button>',1)
h,n=re.subn(r'\n<div id="ludoPicker".*?(?=\n<div id="checkersRequest")','\n',h,count=1,flags=re.S)
if n!=1: raise SystemExit(f'Ludo picker removals={n}')
h=h.replace('<script src="ludo.js"></script>','<script src="billiards.js"></script>',1)

h=h.replace("var ludoGames={},activeLudoId='',pendingLudoAcceptId='',ludoSending=false,ludoAnimating=false;",
            "var poolGames={},activePoolId='',pendingPoolAcceptId='',poolSending=false,poolAnimating=false,poolAim=-0.12,poolPower=.62;",1)
h=h.replace("['attachMenu','emojiPanel','newsCaptureMenu','relayModal','turnModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer','ludoPicker','gameChoice','opponentChoice']",
            "['attachMenu','emojiPanel','newsCaptureMenu','relayModal','turnModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer','gameChoice','opponentChoice']",1)
h=h.replace("['setup','home','picker','chat','news','more','adminArchive','adminChat','checkers','gamesHub','durak','ludo']",
            "['setup','home','picker','chat','news','more','adminArchive','adminChat','checkers','gamesHub','durak','pool']",1)
h=h.replace("if(!$('ludo').classList.contains('hidden')){activeLudoId='';openGamesHub();return true;}",
            "if(!$('pool').classList.contains('hidden')){activePoolId='';openGamesHub();return true;}",1)
h=h.replace("if(['game_invite','game_accept','game_decline','game_move','game_resign'].includes(p.kind)){if((d.gameType||'checkers')==='durak')receiveDurak(p);else if(d.gameType==='ludo')receiveLudo(p);else receiveCheckers(p);return;}",
            "if(['game_invite','game_accept','game_decline','game_move','game_resign'].includes(p.kind)){if((d.gameType||'checkers')==='durak')receiveDurak(p);else if(d.gameType==='pool')receivePool(p);else receiveCheckers(p);return;}",1)
h=h.replace("loadGames();loadDurakGames();loadLudoGames();renderHome();","loadGames();loadDurakGames();loadPoolGames();renderHome();",1)

pool_code=r'''function poolSide(g){return g.players.indexOf(role);}
function poolPeer(g){return g.players.find(function(x){return x!==role;});}
function poolStateOk(s){return !!s&&Array.isArray(s.balls)&&s.balls.length===16&&Array.isArray(s.groups)&&s.groups.length===2&&Number.isInteger(Number(s.seq))&&Number(s.seq)>=0;}
function savePoolGames(){try{localStorage.setItem('of638_pool_'+ownTag,JSON.stringify(Object.values(poolGames).slice(-30)));}catch(e){toast('Не удалось сохранить партию в бильярд');}}
function loadPoolGames(){poolGames={};try{var a=JSON.parse(localStorage.getItem('of638_pool_'+ownTag)||'[]');if(Array.isArray(a))a.forEach(function(g){if(g&&/^[a-f0-9]{32}$/.test(g.id)&&Array.isArray(g.players)&&g.players.length===2&&g.players.includes(role)&&poolStateOk(g.state))poolGames[g.id]=g;});}catch(e){}showPoolRequest();}
function validReusablePool(g,peer){return !!g&&(g.status==='active'||g.status==='invited')&&g.players&&g.players.length===2&&g.players.includes(role)&&g.players.includes(peer)&&g.state&&g.state.winner===null;}
async function invitePool(peer){
 if(!peer||peer===role||!NAMES[peer])return false;
 var same=Object.values(poolGames).find(function(g){return validReusablePool(g,peer);});
 if(same){openPool(same.id);if(same.status==='invited'&&same.inviter===role)sendPoolInvite(same);return true;}
 var id=randomId(),g={id:id,players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),state:PoolRules.initial()};
 poolGames[id]=g;savePoolGames();openPool(id);sendPoolInvite(g).catch(function(){toast('Приглашение сохранено и отправится при восстановлении связи.');});return true;
}
async function sendPoolInvite(g){await publishEnvelope(poolPeer(g),'game_invite',{gameType:'pool',gameId:g.id,state:g.state},5,'pool_invite',g.id,true);toast('Приглашение в бильярд отправлено');if(activePoolId===g.id)renderPool();}
function showPoolRequest(){var waiting=Object.values(poolGames).filter(function(g){return g.status==='invited'&&g.invitee===role&&g.inviter!==role&&Date.now()-g.updated<86400000;}).sort(function(a,b){return b.updated-a.updated;})[0];pendingPoolAcceptId=waiting?waiting.id:'';if(!waiting)return;$('checkersRequestText').textContent='🎱 '+NAMES[waiting.inviter]+' приглашает вас в американский бильярд';$('checkersRequest').classList.remove('hidden');}
async function acceptPool(id){var g=poolGames[id];if(!g||g.status!=='invited'||g.invitee!==role)return;g.status='active';g.updated=Date.now();pendingPoolAcceptId='';$('checkersRequest').classList.add('hidden');savePoolGames();openPool(id);try{await publishEnvelope(g.inviter,'game_accept',{gameType:'pool',gameId:id,state:g.state},5,'pool_accept',id,true);}catch(e){toast('Ответ сохранён и будет отправлен при восстановлении связи.');}}
async function declinePool(id){var g=poolGames[id];if(!g)return;g.status='declined';g.updated=Date.now();pendingPoolAcceptId='';$('checkersRequest').classList.add('hidden');savePoolGames();try{await publishEnvelope(g.inviter,'game_decline',{gameType:'pool',gameId:id},5,'pool_decline',id,true);}catch(e){}}
function receivePool(p){var d=p.data||{},id=d.gameId,peer=p.from;if(!/^[a-f0-9]{32}$/.test(id||''))return;var g=poolGames[id];
 if(p.kind==='game_invite'){
  if(Date.now()-p.ts>86400000||!poolStateOk(d.state)||peer===role)return;
  if(g){if(g.status==='active'&&g.invitee===role)publishEnvelope(peer,'game_accept',{gameType:'pool',gameId:id,state:g.state},5,'pool_accept_retry',id,true).catch(function(){});return;}
  g={id:id,players:[peer,role],inviter:peer,invitee:role,status:'invited',updated:Date.now(),state:d.state};poolGames[id]=g;savePoolGames();showPoolRequest();return;
 }
 if(!g||poolPeer(g)!==peer)return;
 if(p.kind==='game_accept'&&g.inviter===role){g.status='active';g.updated=Date.now();savePoolGames();if(activePoolId===id)renderPool();return;}
 if(p.kind==='game_decline'){g.status='declined';g.updated=Date.now();savePoolGames();if(activePoolId===id)renderPool();return;}
 if((p.kind==='game_move'||p.kind==='game_resign')&&poolStateOk(d.state)&&Number(d.state.seq)>Number(g.state.seq)){
  var old=PoolRules.clone(g.state),last=d.state.last||{};if(Number(last.player)!==g.players.indexOf(peer))return;
  g.state=d.state;g.status=g.state.winner===null?'active':'ended';g.updated=Date.now();savePoolGames();
  if(activePoolId===id&&last.kind==='shot'){poolAnimating=true;var sim=PoolRules.simulate(old,last.angle,last.power,true);poolAnimateFrames(sim.frames).then(function(){poolAnimating=false;if(activePoolId===id)renderPool();});}else if(activePoolId===id)renderPool();return;
 }
}
function poolGroupLabel(g){return g==='solids'?'Сплошные 1–7':g==='stripes'?'Полосатые 9–15':'Группа не определена';}
function poolBallColor(n){return PoolRules.BALL_COLORS[n]||'#eee';}
function poolRoundRect(ctx,x,y,w,h,r){var q=Math.min(r,w/2,h/2);ctx.beginPath();ctx.moveTo(x+q,y);ctx.arcTo(x+w,y,x+w,y+h,q);ctx.arcTo(x+w,y+h,x,y+h,q);ctx.arcTo(x,y+h,x,y,q);ctx.arcTo(x,y,x+w,y,q);ctx.closePath();}
function poolDrawBall(ctx,b){
 if(b.pocketed)return;var r=PoolRules.R,x=b.x,y=b.y,n=b.n,col=poolBallColor(n);ctx.save();ctx.shadowColor='rgba(0,0,0,.45)';ctx.shadowBlur=7;ctx.shadowOffsetY=5;
 var g=ctx.createRadialGradient(x-r*.38,y-r*.45,2,x,y,r);g.addColorStop(0,'#fff');g.addColorStop(.23,n===0?'#fefefe':col);g.addColorStop(1,n===0?'#cfd3d4':col);ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();ctx.shadowColor='transparent';
 if(n>=9){ctx.save();ctx.beginPath();ctx.arc(x,y,r-1,0,Math.PI*2);ctx.clip();ctx.fillStyle='#f6f3e9';ctx.fillRect(x-r,y-r,r*2,r*2);ctx.fillStyle=col;ctx.fillRect(x-r,y-r*.48,r*2,r*.96);ctx.restore();}
 if(n>0){ctx.fillStyle='#f8f6ed';ctx.beginPath();ctx.arc(x,y,r*.48,0,Math.PI*2);ctx.fill();ctx.fillStyle='#111';ctx.font='bold 10px system-ui';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(String(n),x,y+.5);}
 ctx.fillStyle='rgba(255,255,255,.55)';ctx.beginPath();ctx.arc(x-r*.34,y-r*.38,r*.18,0,Math.PI*2);ctx.fill();ctx.restore();
}
function poolDrawBalls(balls,showAim){
 var c=$('poolCanvas');if(!c)return;var d=Math.min(2,window.devicePixelRatio||1);if(c.width!==1000*d||c.height!==500*d){c.width=1000*d;c.height=500*d;}var ctx=c.getContext('2d');ctx.setTransform(d,0,0,d,0,0);ctx.clearRect(0,0,1000,500);
 var wood=ctx.createLinearGradient(0,0,1000,500);wood.addColorStop(0,'#9d6a37');wood.addColorStop(.45,'#4b2a14');wood.addColorStop(1,'#b27a42');ctx.fillStyle=wood;poolRoundRect(ctx,4,4,992,492,34);ctx.fill();
 var rail=ctx.createLinearGradient(0,30,0,470);rail.addColorStop(0,'#168663');rail.addColorStop(.5,'#07583e');rail.addColorStop(1,'#03402d');ctx.fillStyle=rail;poolRoundRect(ctx,25,25,950,450,26);ctx.fill();
 var felt=ctx.createRadialGradient(500,220,50,500,250,520);felt.addColorStop(0,'#0e825b');felt.addColorStop(1,'#055038');ctx.fillStyle=felt;poolRoundRect(ctx,46,46,908,408,16);ctx.fill();
 ctx.strokeStyle='rgba(255,255,255,.08)';ctx.lineWidth=1;for(var y=70;y<450;y+=28){ctx.beginPath();ctx.moveTo(55,y);ctx.lineTo(945,y);ctx.stroke();}
 PoolRules.POCKETS.forEach(function(p,i){ctx.fillStyle='#060807';ctx.beginPath();ctx.arc(p[0],p[1],i===1||i===4?25:29,0,Math.PI*2);ctx.fill();var pg=ctx.createRadialGradient(p[0]-5,p[1]-5,2,p[0],p[1],28);pg.addColorStop(0,'#1d2420');pg.addColorStop(1,'#000');ctx.fillStyle=pg;ctx.beginPath();ctx.arc(p[0],p[1],i===1||i===4?21:25,0,Math.PI*2);ctx.fill();});
 var cue=balls.find(function(b){return b.n===0&&!b.pocketed;});var g=poolGames[activePoolId],canAim=g&&g.status==='active'&&g.state.winner===null&&g.state.turn===poolSide(g)&&!poolAnimating&&!poolSending;
 if(showAim&&canAim&&cue){ctx.save();ctx.setLineDash([11,9]);ctx.strokeStyle='rgba(255,255,255,.76)';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(cue.x,cue.y);ctx.lineTo(cue.x+Math.cos(poolAim)*720,cue.y+Math.sin(poolAim)*720);ctx.stroke();ctx.setLineDash([]);ctx.strokeStyle='#d7a65a';ctx.lineWidth=9;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(cue.x-Math.cos(poolAim)*33,cue.y-Math.sin(poolAim)*33);ctx.lineTo(cue.x-Math.cos(poolAim)*210,cue.y-Math.sin(poolAim)*210);ctx.stroke();ctx.strokeStyle='#f3e1ad';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(cue.x-Math.cos(poolAim)*33,cue.y-Math.sin(poolAim)*33);ctx.lineTo(cue.x-Math.cos(poolAim)*208,cue.y-Math.sin(poolAim)*208);ctx.stroke();ctx.restore();}
 balls.slice().sort(function(a,b){return a.n===0?1:b.n===0?-1:a.n-b.n;}).forEach(function(b){poolDrawBall(ctx,b);});
}
function poolAnimateFrames(frames){return new Promise(function(resolve){if(!frames||!frames.length){resolve();return;}var stride=Math.max(1,Math.ceil(frames.length/150)),i=0;function tick(){poolDrawBalls(frames[Math.min(i,frames.length-1)],false);i+=stride;if(i<frames.length)requestAnimationFrame(tick);else{poolDrawBalls(frames[frames.length-1],false);setTimeout(resolve,70);}}tick();});}
function poolRenderMiniBalls(s){var box=$('poolBalls');box.innerHTML='';for(var n=1;n<=15;n++){var b=s.balls.find(function(x){return x.n===n;}),e=document.createElement('span');e.className='pool-mini-ball'+(b&&b.pocketed?' done':'');e.textContent=String(n);e.style.background=poolBallColor(n);box.appendChild(e);}}
function renderPool(){var g=poolGames[activePoolId];if(!g)return;var s=g.state,side=poolSide(g),peer=poolPeer(g),pb=$('poolPlayers');pb.innerHTML='';g.players.forEach(function(p,i){var d=document.createElement('div');d.className='pool-player'+(g.status==='active'&&s.turn===i&&s.winner===null?' current':'');var st=document.createElement('strong');st.textContent=(i===side?'Вы · ':'')+NAMES[p];var gr=document.createElement('div');gr.className='pool-group';gr.textContent=poolGroupLabel(s.groups[i]);d.appendChild(st);d.appendChild(gr);pb.appendChild(d);});
 poolDrawBalls(s.balls,true);poolRenderMiniBalls(s);var status='',hint='';
 if(g.status==='invited'){status='Ожидаем соперника';hint=g.inviter===role?'Ждём, когда '+NAMES[peer]+' примет приглашение.':'Примите приглашение, чтобы начать.';}
 else if(g.status==='declined'){status='Приглашение отклонено';hint='Можно выбрать другого соперника или отправить приглашение снова.';}
 else if(s.winner!==null){g.status='ended';status='🏆 Победитель: '+NAMES[g.players[s.winner]];hint=s.winner===side?'🎉 Победа! Восьмёрка забита правильно.':'Партия окончена. Можно взять реванш.';awardGameOnce(g.id,s.winner===side?'win':'loss');savePoolGames();}
 else if(poolAnimating){status='Шары движутся…';hint='Ждём полной остановки шаров.';}
 else if(s.turn===side){status='Ваш ход';hint=s.ballInHand?'Фол соперника: биток автоматически выставлен. Выберите направление и силу.':'Коснитесь стола для прицеливания, настройте силу и нажмите «УДАР».';}
 else{status='Ход: '+NAMES[g.players[s.turn]];var l=s.last||{};hint=l.foul?'Фол. Теперь ваш соперник выполняет следующий удар.':'Смотрите удар соперника.';}
 $('poolTitle').textContent='Бильярд · 8-ball';$('poolStatus').textContent=status;$('poolHint').textContent=hint;$('poolPower').value=String(Math.round(poolPower*100));$('poolPowerValue').textContent=Math.round(poolPower*100)+'%';$('poolHitBtn').disabled=!(g.status==='active'&&s.winner===null&&s.turn===side&&!poolAnimating&&!poolSending);$('poolResignBtn').classList.toggle('hidden',!(g.status==='active'&&s.winner===null));$('poolRetryInviteBtn').classList.toggle('hidden',!(g.status==='invited'&&g.inviter===role));}
function openPool(id){var g=poolGames[id];if(!g)return;activePoolId=id;$('checkersRequest').classList.add('hidden');showScreen('pool');setTimeout(renderPool,0);}
function poolSetAimFromEvent(e){var g=poolGames[activePoolId];if(!g||g.status!=='active'||g.state.turn!==poolSide(g)||poolAnimating||poolSending)return;var cue=g.state.balls.find(function(b){return b.n===0&&!b.pocketed;});if(!cue)return;var r=$('poolCanvas').getBoundingClientRect(),x=(e.clientX-r.left)/r.width*1000,y=(e.clientY-r.top)/r.height*500;poolAim=Math.atan2(y-cue.y,x-cue.x);poolDrawBalls(g.state.balls,true);}
async function poolShoot(){var g=poolGames[activePoolId];if(!g||g.status!=='active'||poolSending||poolAnimating)return;var side=poolSide(g);if(g.state.turn!==side||g.state.winner!==null)return;var result=PoolRules.shot(g.state,side,poolAim,poolPower);if(!result)return;poolSending=true;poolAnimating=true;var oldId=g.id;g.state=result.state;g.status=g.state.winner===null?'active':'ended';g.updated=Date.now();savePoolGames();try{var send=publishEnvelope(poolPeer(g),'game_move',{gameType:'pool',gameId:g.id,state:g.state},4,'pool_move',g.id+'_'+g.state.seq,true);await poolAnimateFrames(result.frames);await send;}catch(e){toast('Удар сохранён. Отправка продолжится в фоне.');}finally{poolSending=false;poolAnimating=false;if(activePoolId===oldId)renderPool();}}
async function resignPool(){var g=poolGames[activePoolId];if(!g||g.status!=='active'||g.state.winner!==null||!confirm('Сдаться в партии?'))return;var side=poolSide(g),next=PoolRules.resign(g.state,side);if(!next)return;g.state=next;g.status='ended';g.updated=Date.now();savePoolGames();awardGameOnce(g.id,'surrender');renderPool();try{await publishEnvelope(poolPeer(g),'game_resign',{gameType:'pool',gameId:g.id,state:g.state},4,'pool_resign',g.id+'_'+g.state.seq,true);}catch(e){toast('Результат сохранён. Отправка продолжится в фоне.');}}
'''
h,n=re.subn(r'var LUDO_COLORS=.*?(?=function openOpponentChoice\(kind\))',pool_code+'\n',h,count=1,flags=re.S)
if n!=1: raise SystemExit(f'Ludo code block replacements={n}')

h=h.replace("function launchGame(kind,peer){if(kind==='checkers'){var prev=currentThread;currentThread={type:'direct',peer:peer};inviteCheckers().finally(function(){currentThread=prev;});}else if(kind==='durak')inviteDurak(peer);else if(kind==='ludo')openLudoPicker(peer);}",
"function launchGame(kind,peer){if(kind==='checkers'){var prev=currentThread;currentThread={type:'direct',peer:peer};inviteCheckers().finally(function(){currentThread=prev;});}else if(kind==='durak')inviteDurak(peer);else if(kind==='pool')invitePool(peer);}",1)

new_hub=r'''function openGamesHub(){renderGameProfile();var box=$('gameSessions');box.innerHTML='';var items=Object.values(games).map(function(g){return {g:g,type:'checkers'};}).concat(Object.values(durakGames).map(function(g){return {g:g,type:'durak'};})).concat(Object.values(poolGames).map(function(g){return {g:g,type:'pool'};}));items.filter(function(item){return item.g.status==='active'||item.g.status==='invited';}).sort(function(a,b){return b.g.updated-a.g.updated;}).forEach(function(item){var b=document.createElement('button');b.className='game-entry';var other=item.g.players.find(function(x){return x!==role;});b.textContent=(item.type==='durak'?'🃏 Дурак':item.type==='pool'?'🎱 Бильярд 8-ball':'♟ Шашки')+' · '+NAMES[other]+' · '+(item.g.status==='active'?'Продолжить':'Ожидание');b.onclick=function(){item.type==='durak'?openDurak(item.g.id):item.type==='pool'?openPool(item.g.id):openCheckers(item.g.id);};box.appendChild(b);});if(!box.children.length)box.textContent='Пока нет активных партий';showScreen('gamesHub');}
'''
h,n=re.subn(r'function openGamesHub\(\)\{.*?(?=async function sendDurakInvite)',new_hub,h,count=1,flags=re.S)
if n!=1: raise SystemExit(f'Games hub replacements={n}')

events=r'''$('gamesBackBtn').addEventListener('click',function(){showScreen('home');renderHome();});$('hubCheckersBtn').addEventListener('click',function(){openOpponentChoice('checkers');});$('hubDurakBtn').addEventListener('click',function(){openOpponentChoice('durak');});$('hubPoolBtn').addEventListener('click',function(){openOpponentChoice('pool');});
$('pickCheckersBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('checkers',p);else openOpponentChoice('checkers');});$('pickDurakBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('durak',p);else openOpponentChoice('durak');});$('pickPoolBtn').addEventListener('click',function(){var p=gamePickPeer;$('gameChoice').classList.add('hidden');if(p)launchGame('pool',p);else openOpponentChoice('pool');});$('gameChoiceCancel').addEventListener('click',function(){$('gameChoice').classList.add('hidden');});$('opponentCancel').addEventListener('click',function(){$('opponentChoice').classList.add('hidden');});
$('poolBackBtn').addEventListener('click',function(){activePoolId='';openGamesHub();});$('poolHitBtn').addEventListener('click',poolShoot);$('poolResignBtn').addEventListener('click',resignPool);$('poolRetryInviteBtn').addEventListener('click',function(){var g=poolGames[activePoolId];if(g&&g.status==='invited'&&g.inviter===role)sendPoolInvite(g);});
$('poolPower').addEventListener('input',function(){poolPower=Math.max(.12,Math.min(1,Number(this.value)/100));$('poolPowerValue').textContent=Math.round(poolPower*100)+'%';var g=poolGames[activePoolId];if(g)poolDrawBalls(g.state.balls,true);});$('poolCanvas').addEventListener('pointerdown',function(e){this.setPointerCapture&&this.setPointerCapture(e.pointerId);poolSetAimFromEvent(e);});$('poolCanvas').addEventListener('pointermove',function(e){if(e.buttons)poolSetAimFromEvent(e);});
'''
h,n=re.subn(r"\$\('gamesBackBtn'\).*?(?=\$\('durakBackBtn'\))",events,h,count=1,flags=re.S)
if n!=1: raise SystemExit(f'Game event block replacements={n}')

h=h.replace("$('checkersAcceptBtn').addEventListener('click',function(){if(pendingLudoAcceptId)acceptLudo(pendingLudoAcceptId);else if(pendingDurakAcceptId)acceptDurak(pendingDurakAcceptId);else acceptCheckers(pendingGameAcceptId);});",
            "$('checkersAcceptBtn').addEventListener('click',function(){if(pendingPoolAcceptId)acceptPool(pendingPoolAcceptId);else if(pendingDurakAcceptId)acceptDurak(pendingDurakAcceptId);else acceptCheckers(pendingGameAcceptId);});",1)
h=h.replace("$('checkersDeclineBtn').addEventListener('click',function(){if(pendingLudoAcceptId)declineLudo(pendingLudoAcceptId);else if(pendingDurakAcceptId)declineDurak(pendingDurakAcceptId);else declineCheckers(pendingGameAcceptId);});",
            "$('checkersDeclineBtn').addEventListener('click',function(){if(pendingPoolAcceptId)declinePool(pendingPoolAcceptId);else if(pendingDurakAcceptId)declineDurak(pendingDurakAcceptId);else declineCheckers(pendingGameAcceptId);});",1)

# Visual class for game-choice button.
h=h.replace('.ludo-choice{grid-column:1/-1;background:linear-gradient(120deg,#e24a4a,#2f6fed)!important}', '.pool-choice{grid-column:1/-1;background:linear-gradient(120deg,#0c6d4b,#17233c)!important;color:#fff!important}')

# Safety: all old Ludo UI/runtime must be gone, but persistent media-cache fixes must still be present.
for marker in ['hubLudoBtn','pickLudoBtn','openLudo','ludoGames','pendingLudo','LudoRules','Семейная гонка','ludoPicker']:
    if marker in h: raise SystemExit('Old Ludo marker still present: '+marker)
for marker in ['attachmentDownloadPending','getAttachmentObjectUrl','keptAttachments','rememberMediaState',"video.preload='auto'"]:
    if marker not in h: raise SystemExit('Media-cache regression marker missing: '+marker)
for marker in ['hubPoolBtn','pickPoolBtn','poolCanvas','function receivePool','function poolShoot',"gameType:'pool'",'<script src="billiards.js"></script>']:
    if marker not in h: raise SystemExit('Billiards marker missing: '+marker)

html_path.write_text(h,encoding='utf-8')

b=build_path.read_text(encoding='utf-8')
b=b.replace('versionCode 6037','versionCode 6038',1).replace("versionName '6.0.37'","versionName '6.0.38'",1)
if 'versionCode 6038' not in b or "versionName '6.0.38'" not in b: raise SystemExit('build.gradle version patch failed')
build_path.write_text(b,encoding='utf-8')

m=manifest_path.read_text(encoding='utf-8').replace('android:label="Наша семья 6.0.37"','android:label="Наша семья 6.0.38"',1)
if 'android:label="Наша семья 6.0.38"' not in m: raise SystemExit('manifest version patch failed')
manifest_path.write_text(m,encoding='utf-8')

old=ROOT/'duoapp/src/main/assets/ludo.js'
if old.exists(): old.unlink()
print('Prepared OurFamily 6.0.38: removed Ludo, added American Pool 8-ball, preserved 6.0.37 media cache.')
