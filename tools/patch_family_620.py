from pathlib import Path

p=Path('duoapp/src/main/assets/index.html')
h=p.read_text()

assert "APP_VERSION='6.0.19'" in h
assert '<script src="checkers.js"></script>' in h
assert 'id="inviteCheckersBtn"' in h

h=h.replace('<title>Наша семья 6.0.19</title>','<title>Наша семья 6.0.20</title>')
h=h.replace('<h1>Наша семья 6.0.9</h1>','<h1>Наша семья 6.0.20</h1>')
h=h.replace('Наша семья · v6.0.19','Наша семья · v6.0.20')
h=h.replace('<div id="aboutVersion" class="about-version">v6.0.9</div>','<div id="aboutVersion" class="about-version">v6.0.20</div>')
h=h.replace("APP_VERSION='6.0.19'","APP_VERSION='6.0.20'")
h=h.replace('<script src="checkers.js"></script>','<script src="checkers.js"></script>\n<script src="durak.js"></script>')
h=h.replace('<button id="inviteCheckersBtn" class="game-invite hidden">♟ Пригласить в шашки</button>','<button id="inviteCheckersBtn" class="game-invite hidden">🎮 Пригласить в игру</button>')

css='''
/* v6.0.20 games */
#games,#durak{display:flex;flex-direction:column}.games-body{flex:1;overflow:auto;padding:14px 12px 92px}.game-hero{background:linear-gradient(135deg,#203a72,#2f6fed);color:#fff;border-radius:22px;padding:17px;margin-bottom:13px;box-shadow:var(--shadow)}.game-score{font-size:30px;font-weight:950}.game-rank{font-size:14px;font-weight:850;opacity:.9}.game-grid{display:grid;grid-template-columns:1fr 1fr;gap:11px}.game-card{border:1px solid var(--line);background:var(--card);color:var(--ink);border-radius:19px;padding:18px 12px;text-align:center;font-weight:900;box-shadow:0 4px 12px rgba(25,35,55,.06)}.game-card .gi{font-size:38px;display:block;margin-bottom:7px}.active-games{margin-top:16px}.active-game-row{width:100%;border:1px solid var(--line);background:var(--card);color:var(--ink);border-radius:15px;padding:12px;margin-bottom:8px;text-align:left;font-weight:850}.bottom-nav{position:fixed;left:0;right:0;bottom:0;z-index:55;background:var(--card);border-top:1px solid var(--line);padding:7px 8px max(7px,env(safe-area-inset-bottom));display:grid;grid-template-columns:repeat(4,1fr);gap:4px}.bottom-nav button{border:0;background:transparent;color:var(--muted);padding:6px 2px;border-radius:12px;font-size:11px;font-weight:850}.bottom-nav button span{display:block;font-size:21px;line-height:1.1;margin-bottom:2px}.bottom-nav button.active{color:var(--blue);background:color-mix(in srgb,var(--card) 84%,var(--blue) 16%)}#home .body,#news .news-feed{padding-bottom:94px}
.durak-felt{flex:1;overflow:auto;padding:12px 10px 105px;background:radial-gradient(circle at 50% 42%,#258a50,#126538 75%);color:#fff;min-height:0}.durak-opponent{text-align:center;font-weight:850;margin:3px 0 9px}.durak-back-row{display:flex;justify-content:center;min-height:76px}.playing-card{width:54px;height:76px;border:1px solid #d9d9d9;border-radius:8px;background:#fff;color:#171717;box-shadow:0 3px 8px rgba(0,0,0,.25);display:inline-flex;flex-direction:column;align-items:flex-start;justify-content:space-between;padding:5px;font-weight:900;font-size:15px;margin:2px;user-select:none}.playing-card.red{color:#d12e36}.playing-card.back{background:repeating-linear-gradient(45deg,#254c9b 0 5px,#f4f6ff 5px 7px);border:3px solid #fff}.playing-card.playable{transform:translateY(-5px);box-shadow:0 7px 13px rgba(0,0,0,.35),0 0 0 3px rgba(255,221,82,.72)}button.playing-card{cursor:pointer}.durak-center{min-height:180px;position:relative;padding:8px;text-align:center}.durak-stock{display:flex;justify-content:center;align-items:center;gap:8px;margin:4px auto 10px}.durak-stock .playing-card{transform:rotate(90deg);margin:8px 15px}.durak-table{display:flex;flex-wrap:wrap;justify-content:center;gap:8px}.durak-pair{position:relative;width:82px;height:108px}.durak-pair .playing-card{position:absolute;left:0;top:0}.durak-pair .playing-card:nth-child(2){left:25px;top:27px}.durak-hand{display:flex;overflow-x:auto;justify-content:flex-start;padding:8px 2px;min-height:92px}.durak-hand .playing-card{flex:0 0 auto}.durak-actions{position:fixed;left:8px;right:8px;bottom:76px;z-index:56;display:flex;gap:7px;justify-content:center}.durak-actions button{border:0;border-radius:13px;padding:11px 14px;font-weight:900}.durak-actions .beat{background:#fff;color:#14643a}.durak-actions .take{background:#ffd166;color:#5c4300}.durak-actions .resign{background:#e85b61;color:#fff}.game-choice-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.game-choice-grid button{min-height:100px;text-align:center;font-size:16px}.opponent-list{display:grid;gap:7px;max-height:55vh;overflow:auto}.rank-progress{height:7px;background:rgba(255,255,255,.25);border-radius:5px;margin-top:9px;overflow:hidden}.rank-progress>div{height:100%;background:#fff;border-radius:5px}
'''
h=h.replace('</style>',css+'\n</style>')

insert='''
<section id="games" class="screen hidden">
 <div class="topbar"><button id="gamesBackBtn" class="iconbtn">‹</button><div class="avatar">🎮</div><div class="who"><div class="name">Игры</div><div class="status">Шашки и Дурак</div></div></div>
 <div class="games-body"><div id="gameHero" class="game-hero"></div><div class="section-title">Выберите игру</div><div class="game-grid"><button id="gamesCheckersBtn" class="game-card"><span class="gi">♟</span>Шашки</button><button id="gamesDurakBtn" class="game-card"><span class="gi">🃏</span>Дурак</button></div><div class="active-games"><div class="section-title">Текущие партии</div><div id="activeGamesList"></div></div></div>
</section>
<section id="durak" class="screen hidden">
 <div class="topbar"><button id="durakBackBtn" class="iconbtn">‹</button><div class="who"><div id="durakTitle" class="name">Дурак</div><div id="durakStatus" class="status"></div></div></div>
 <div class="durak-felt"><div id="durakOpponent" class="durak-opponent"></div><div id="durakOpponentCards" class="durak-back-row"></div><div class="durak-center"><div id="durakStock" class="durak-stock"></div><div id="durakTable" class="durak-table"></div></div><div id="durakHint" style="text-align:center;font-weight:800;min-height:22px"></div><div id="durakHand" class="durak-hand"></div></div>
 <div id="durakActions" class="durak-actions"><button id="durakBeatBtn" class="beat">Бито</button><button id="durakTakeBtn" class="take">Беру</button><button id="durakResignBtn" class="resign">Сдаться</button></div>
</section>
<div id="gameChoiceModal" class="modal-backdrop hidden"><div class="modal-card"><div class="modal-title">Во что сыграем?</div><div class="game-choice-grid"><button id="chooseCheckersBtn" class="option-btn">♟<br>Шашки</button><button id="chooseDurakBtn" class="option-btn">🃏<br>Дурак</button></div><button id="closeGameChoiceBtn" class="option-btn" style="width:100%;margin-top:10px">Отмена</button></div></div>
<div id="gameOpponentModal" class="modal-backdrop hidden"><div class="modal-card"><div id="gameOpponentTitle" class="modal-title">С кем играть?</div><div id="gameOpponentList" class="opponent-list"></div><button id="closeGameOpponentBtn" class="option-btn" style="width:100%;margin-top:10px">Отмена</button></div></div>
'''
anchor='<section id="news" class="screen hidden">'
assert anchor in h
h=h.replace(anchor,insert+'\n'+anchor,1)

nav='''<nav id="bottomNav" class="bottom-nav hidden"><button id="navFamily"><span>👨‍👩‍👧‍👦</span>Семья</button><button id="navGames"><span>🎮</span>Игры</button><button id="navNews"><span>📰</span>Новости</button><button id="navMore"><span>•••</span>Ещё</button></nav>\n'''
h=h.replace('<div id="adminOnce"',nav+'<div id="adminOnce"',1)

h=h.replace("['setup','home','picker','chat','news','adminArchive','adminChat','checkers'].forEach(function(x){$(x).classList.toggle('hidden',x!==id);});\n hideTransientPanels();","['setup','home','picker','chat','news','adminArchive','adminChat','checkers','games','durak'].forEach(function(x){$(x).classList.toggle('hidden',x!==id);});\n hideTransientPanels();\n var nav=$('bottomNav');if(nav){var show=['home','games','news'].includes(id);nav.classList.toggle('hidden',!show);['navFamily','navGames','navNews'].forEach(function(n){$(n).classList.remove('active');});if(id==='home')$('navFamily').classList.add('active');if(id==='games')$('navGames').classList.add('active');if(id==='news')$('navNews').classList.add('active');}")

h=h.replace("$('resumeCheckersBtn').textContent='♟ Продолжить партию с '+NAMES[running[0].players.find(function(x){return x!==role;})];","$('resumeCheckersBtn').textContent=(running[0].type==='durak'?'🃏':'♟')+' Продолжить партию с '+NAMES[running[0].players.find(function(x){return x!==role;})];")
h=h.replace("$('inviteCheckersBtn').addEventListener('click',inviteCheckers);","$('inviteCheckersBtn').addEventListener('click',function(){openGameChoice(currentThread&&currentThread.type==='direct'?currentThread.peer:'');});")
h=h.replace("$('resumeCheckersBtn').addEventListener('click',function(){var running=Object.values(games).filter(function(g){return g.status==='active'&&g.players.includes(role);}).sort(function(a,b){return b.updated-a.updated;});if(running.length)openCheckers(running[0].id);});","$('resumeCheckersBtn').addEventListener('click',function(){var running=Object.values(games).filter(function(g){return g.status==='active'&&g.players.includes(role);}).sort(function(a,b){return b.updated-a.updated;});if(running.length)openGame(running[0].id);});")

# Insert v6.0.20 override/game functions after the legacy checkers receiver.
marker='function replySnapshot(m)'
pos=h.index(marker)
extra=r'''
function gameType(g){return (g&&g.type)||'checkers';}
function ratingKey(){return 'of620_rating_'+ownTag;}
function loadRating(){try{var r=JSON.parse(localStorage.getItem(ratingKey())||'{}');return {points:Number(r.points)||0,wins:Number(r.wins)||0,losses:Number(r.losses)||0,draws:Number(r.draws)||0,applied:Array.isArray(r.applied)?r.applied:[]};}catch(e){return {points:0,wins:0,losses:0,draws:0,applied:[]};}}
function saveRating(r){try{r.applied=r.applied.slice(-200);localStorage.setItem(ratingKey(),JSON.stringify(r));}catch(e){}}
function rankInfo(points){if(points>=600)return {name:'Профи',from:600,to:600};if(points>=300)return {name:'Крутяга',from:300,to:600};if(points>=150)return {name:'Мастер',from:150,to:300};if(points>=50)return {name:'Опытный',from:50,to:150};return {name:'Новичок',from:0,to:50};}
function applyGameResult(g){
 if(!g||g.status!=='ended')return;var r=loadRating();if(r.applied.includes(g.id))return;
 var won=false,draw=false,resigned=g.resigned===role;
 if(gameType(g)==='durak'){draw=!!g.state.draw;won=g.state.winner===durakSide(g);}else{won=g.state.winner===gameSide(g);}
 if(draw){r.points+=5;r.draws++;toast('Ничья · +5 очков');}
 else if(won){r.points+=10;r.wins++;toast('Победа · +10 очков');}
 else{r.points+=resigned?0:3;r.losses++;toast(resigned?'Сдача · 0 очков':'Партия завершена · +3 очка');}
 r.applied.push(g.id);saveRating(r);
}
function saveGames(){try{Object.values(games).forEach(function(g){applyGameResult(g);});localStorage.setItem('of620_games_'+ownTag,JSON.stringify(Object.values(games).slice(-80)));}catch(e){toast('Не удалось сохранить партию');}}
function loadGames(){
 games={};try{
  var arr=JSON.parse(localStorage.getItem('of620_games_'+ownTag)||'null');
  if(!Array.isArray(arr)){arr=JSON.parse(localStorage.getItem('of5_checkers_'+ownTag)||'[]');arr.forEach(function(g){if(g)g.type='checkers';});}
  if(Array.isArray(arr))arr.forEach(function(g){if(!g||!g.id||!g.players||!g.players.includes(role))return;if(!g.type)g.type='checkers';if(g.type==='checkers'&&Array.isArray(g.state&&g.state.board)&&g.state.board.length===64)games[g.id]=g;if(g.type==='durak'&&DurakRules.valid(g.state))games[g.id]=g;});
 }catch(e){}
 showCheckersRequest();
}
function showCheckersRequest(){
 var candidates=Object.values(games).filter(function(g){return g.status==='invited'&&g.invitee===role&&Date.now()-g.updated<86400000;}).sort(function(a,b){return b.updated-a.updated;});
 var g=candidates[0];pendingGameAcceptId=g?g.id:'';$('checkersRequest').classList.toggle('hidden',!g);
 if(g)$('checkersRequestText').textContent=NAMES[g.inviter]+' приглашает вас в '+(gameType(g)==='durak'?'Дурака':'шашки');
}
function openGame(id){var g=games[id];if(!g)return;if(gameType(g)==='durak')openDurak(id);else openCheckers(id);}
function openGameChoice(peer){window.__gameChoicePeer=peer||'';$('gameChoiceModal').classList.remove('hidden');}
function closeGameChoice(){$('gameChoiceModal').classList.add('hidden');}
function chooseGameOpponent(type){
 closeGameChoice();window.__chosenGameType=type;var preset=window.__gameChoicePeer||'';
 if(preset){startChosenGame(type,preset);return;}
 var box=$('gameOpponentList');box.innerHTML='';$('gameOpponentTitle').textContent='С кем играть в '+(type==='durak'?'Дурака':'шашки')+'?';
 MEMBERS.filter(function(m){return m.id!==role;}).forEach(function(m){var b=document.createElement('button');b.className='option-btn';b.textContent=(onlineNow(m.id)?'🟢 ':'⚪ ')+m.name;b.addEventListener('click',function(){$('gameOpponentModal').classList.add('hidden');startChosenGame(type,m.id);});box.appendChild(b);});
 $('gameOpponentModal').classList.remove('hidden');
}
function startChosenGame(type,peer){if(type==='durak')inviteDurak(peer);else{var previous=currentThread;currentThread={type:'direct',peer:peer};inviteCheckers();currentThread=previous;}}
function renderGamesHub(){
 var r=loadRating(),ri=rankInfo(r.points),pct=ri.to===ri.from?100:Math.max(0,Math.min(100,Math.round((r.points-ri.from)*100/(ri.to-ri.from))));
 $('gameHero').innerHTML='<div class="game-score">'+r.points+' очков</div><div class="game-rank">'+ri.name+' · побед '+r.wins+' · поражений '+r.losses+' · ничьих '+r.draws+'</div><div class="rank-progress"><div style="width:'+pct+'%"></div></div>';
 var box=$('activeGamesList');box.innerHTML='';var active=Object.values(games).filter(function(g){return ['active','invited'].includes(g.status)&&g.players.includes(role);}).sort(function(a,b){return b.updated-a.updated;});
 active.forEach(function(g){var b=document.createElement('button');b.className='active-game-row';b.textContent=(gameType(g)==='durak'?'🃏 Дурак':'♟ Шашки')+' · '+NAMES[gamePeer(g)]+(g.status==='invited'?' · приглашение':'');b.addEventListener('click',function(){if(g.status==='invited'&&g.invitee===role){pendingGameAcceptId=g.id;showCheckersRequest();}else openGame(g.id);});box.appendChild(b);});
 if(!active.length){var e=document.createElement('div');e.className='muted';e.textContent='Активных партий пока нет';box.appendChild(e);}
}
function openGames(){renderGamesHub();showScreen('games');}
function durakSide(g){return role===g.inviter?0:1;}
function cardFace(c){var suits={c:'♣',d:'♦',h:'♥',s:'♠'},r=DurakRules.rank(c);return '<span>'+r+'</span><span style="font-size:22px">'+suits[DurakRules.suit(c)]+'</span>';}
function cardClass(c){return 'playing-card '+(['d','h'].includes(DurakRules.suit(c))?'red':'');}
function playableDurak(g,c){var side=durakSide(g),s=g.state;if(g.status!=='active'||s.winner!==null||s.draw)return false;if(side===s.attacker&&s.phase==='attack'){if(!s.table.length)return true;var ranks={};s.table.forEach(function(p){ranks[DurakRules.rank(p.a)]=1;if(p.d)ranks[DurakRules.rank(p.d)]=1;});return !!ranks[DurakRules.rank(c)]&&s.table.length<s.attackLimit;}if(side===s.defender&&s.phase==='defend'){var pair=s.table.find(function(p){return !p.d;});return pair&&DurakRules.canBeat(c,pair.a,s.trump);}return false;}
function makeCard(c,playable,cb){var b=document.createElement(cb?'button':'div');b.className=cardClass(c)+(playable?' playable':'');b.innerHTML=cardFace(c);if(cb)b.addEventListener('click',cb);return b;}
function openDurak(id){var g=games[id];if(!g||gameType(g)!=='durak')return;activeGameId=id;$('checkersRequest').classList.add('hidden');$('durakTitle').textContent='Дурак · '+NAMES[gamePeer(g)];showScreen('durak');renderDurak();}
function renderDurak(){
 var g=games[activeGameId];if(!g||gameType(g)!=='durak')return;var s=g.state,me=durakSide(g),opp=1-me,finished=g.status==='ended'||s.winner!==null||s.draw;
 var status=g.status==='invited'?(role===g.inviter?'Ждём ответа':'Приглашение'):g.status==='declined'?'Приглашение отклонено':s.draw?'Ничья':s.winner!==null?(s.winner===me?'Вы победили':'Победил '+NAMES[gamePeer(g)]):(me===s.attacker?'Вы атакуете':'Вы отбиваетесь');$('durakStatus').textContent=status;
 $('durakOpponent').textContent=NAMES[gamePeer(g)]+' · карт: '+s.hands[opp].length;var ob=$('durakOpponentCards');ob.innerHTML='';for(var i=0;i<Math.min(8,s.hands[opp].length);i++){var back=document.createElement('div');back.className='playing-card back';ob.appendChild(back);}
 var stock=$('durakStock');stock.innerHTML='';if(s.stock.length){var back=document.createElement('div');back.className='playing-card back';stock.appendChild(back);var tr=document.createElement('div');tr.className=cardClass('6'+s.trump);tr.innerHTML='<span>Козырь</span><span style="font-size:24px">'+({c:'♣',d:'♦',h:'♥',s:'♠'}[s.trump])+'</span>';stock.appendChild(tr);var n=document.createElement('strong');n.textContent=s.stock.length+' карт';stock.appendChild(n);}else stock.textContent='Колода закончилась';
 var table=$('durakTable');table.innerHTML='';s.table.forEach(function(pair){var wrap=document.createElement('div');wrap.className='durak-pair';wrap.appendChild(makeCard(pair.a,false));if(pair.d)wrap.appendChild(makeCard(pair.d,false));table.appendChild(wrap);});
 var hand=$('durakHand');hand.innerHTML='';s.hands[me].forEach(function(c){var can=playableDurak(g,c);hand.appendChild(makeCard(c,can,can?function(){playDurakCard(c);}:null));});
 var ownAttack=me===s.attacker,ownDef=me===s.defender;$('durakBeatBtn').classList.toggle('hidden',finished||!ownAttack||!DurakRules.allCovered(s.table));$('durakTakeBtn').classList.toggle('hidden',finished||!ownDef||!s.table.length);$('durakResignBtn').classList.toggle('hidden',finished||g.status!=='active');
 $('durakHint').textContent=gameSending?'Отправляю ход…':finished?'Результат сохранён':g.status!=='active'?'Партия ещё не началась':ownAttack?(s.table.length?'Подкиньте карту того же достоинства или нажмите «Бито»':'Ходите любой картой'):'Отбейте карту или нажмите «Беру»';
}
async function inviteDurak(peer){
 if(!peer)return;var old=Object.values(games).find(function(g){return gameType(g)==='durak'&&g.status==='active'&&gamePeer(g)===peer&&g.state.winner===null&&!g.state.draw;});if(old){openDurak(old.id);return;}
 var seed=crypto.getRandomValues(new Uint32Array(1))[0],id=randomId(),g={id:id,type:'durak',players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),seed:seed,state:DurakRules.initial(seed,0)};
 try{await publishEnvelope(peer,'game_invite',{gameId:id,gameType:'durak',seed:seed},4,'game_invite',id);games[id]=g;saveGames();openDurak(id);}catch(e){toast(relayErrorText(e&&e.message||e));}
}
async function acceptGame(id){var g=games[id];if(!g||g.invitee!==role||g.status!=='invited')return;try{await publishEnvelope(g.inviter,'game_accept',{gameId:id,gameType:gameType(g)},4,'game_accept',id);g.status='active';g.updated=Date.now();saveGames();showCheckersRequest();openGame(id);}catch(e){toast(relayErrorText(e&&e.message||e));}}
async function declineGame(id){var g=games[id];if(!g||g.invitee!==role||g.status!=='invited')return;try{await publishEnvelope(g.inviter,'game_decline',{gameId:id,gameType:gameType(g)},3,'game_decline',id);g.status='declined';g.updated=Date.now();saveGames();showCheckersRequest();renderHome();}catch(e){toast(relayErrorText(e&&e.message||e));}}
async function pushDurak(next){var g=games[activeGameId];if(!g||gameSending)return;var prev=g.state,prevStatus=g.status;gameSending=true;g.state=next;if(next.winner!==null||next.draw)g.status='ended';g.updated=Date.now();saveGames();renderDurak();renderHome();try{await publishEnvelope(gamePeer(g),'game_move',{gameId:g.id,gameType:'durak',seq:next.seq,state:next},4,'game_move',g.id);}catch(e){g.state=prev;g.status=prevStatus;saveGames();toast('Ход не отправлен. '+relayErrorText(e&&e.message||e));}finally{gameSending=false;renderDurak();renderHome();}}
function playDurakCard(c){var g=games[activeGameId];if(!g||gameSending)return;var side=durakSide(g),s=g.state,next=side===s.attacker?DurakRules.playAttack(s,side,c):DurakRules.playDefense(s,side,c);if(next)pushDurak(next);}
function durakBeat(){var g=games[activeGameId];if(!g)return;var next=DurakRules.beat(g.state,durakSide(g));if(next)pushDurak(next);}
function durakTake(){var g=games[activeGameId];if(!g)return;var next=DurakRules.take(g.state,durakSide(g));if(next)pushDurak(next);}
async function resignDurak(){var g=games[activeGameId];if(!g||!confirm('Сдаться в этой партии?'))return;var side=durakSide(g),next=DurakRules.resign(g.state,side);if(!next)return;g.resigned=role;await pushDurak(next);}
async function resignCheckers(){var g=games[activeGameId];if(!g||g.status!=='active'||g.state.winner||!confirm('Сдаться в этой партии?'))return;try{await publishEnvelope(gamePeer(g),'game_resign',{gameId:g.id,gameType:'checkers',seq:g.state.seq,resigned:true},4,'game_resign',g.id);g.state.winner=gameSide(g)==='b'?'w':'b';g.resigned=role;g.status='ended';g.updated=Date.now();saveGames();renderCheckers();renderHome();}catch(e){toast(relayErrorText(e&&e.message||e));}}
async function inviteCheckers(){
 if(!currentThread||currentThread.type!=='direct')return;var peer=currentThread.peer;var old=Object.values(games).find(function(g){return gameType(g)==='checkers'&&g.status==='active'&&gamePeer(g)===peer&&!g.state.winner;});if(old){openCheckers(old.id);return;}
 var id=randomId(),g={id:id,type:'checkers',players:[role,peer],inviter:role,invitee:peer,status:'invited',updated:Date.now(),state:{board:CheckersRules.initial(),turn:'b',forced:null,winner:null,seq:0,last:null}};
 try{await publishEnvelope(peer,'game_invite',{gameId:id,gameType:'checkers'},4,'game_invite',id);games[id]=g;saveGames();openCheckers(id);}catch(e){toast(relayErrorText(e&&e.message||e));}
}
function receiveCheckers(p){
 var d=p.data||{},id=d.gameId,peer=p.from;if(!/^[a-f0-9]{32}$/.test(id||''))return;var g=games[id],type=d.gameType||(g&&gameType(g))||'checkers';
 if(p.kind==='game_invite'){
  if(g||Date.now()-p.ts>86400000)return;
  if(type==='durak'){var seed=Number(d.seed)||0;games[id]={id:id,type:'durak',players:[peer,role],inviter:peer,invitee:role,status:'invited',updated:p.ts||Date.now(),seed:seed,state:DurakRules.initial(seed,0)};}
  else games[id]={id:id,type:'checkers',players:[peer,role],inviter:peer,invitee:role,status:'invited',updated:p.ts||Date.now(),state:{board:CheckersRules.initial(),turn:'b',forced:null,winner:null,seq:0,last:null}};
  saveGames();showCheckersRequest();tryOpenPendingMessage();return;
 }
 if(!g||gamePeer(g)!==peer)return;
 if(p.kind==='game_accept'&&g.inviter===role&&g.status==='invited'){g.status='active';toast(NAMES[peer]+' принял приглашение');}
 else if(p.kind==='game_decline'&&g.inviter===role&&g.status==='invited'){g.status='declined';toast(NAMES[peer]+' отклонил приглашение');}
 else if(p.kind==='game_move'&&g.status==='active'){
   if(gameType(g)==='durak'){if(!d.state||!DurakRules.valid(d.state)||d.seq!==g.state.seq+1)return;g.state=d.state;if(g.state.winner!==null||g.state.draw)g.status='ended';}
   else{if(g.state.turn===gameSide(g)||d.seq!==g.state.seq+1)return;var next=CheckersRules.play(g.state,d.from,d.to);if(!next)return;g.state=next;if(next.winner)g.status='ended';}
 }else if(p.kind==='game_resign'&&g.status==='active'){
   g.resigned=peer;if(gameType(g)==='durak'){g.state=DurakRules.resign(g.state,1-durakSide(g));g.status='ended';}else{g.state.winner=gameSide(g);g.status='ended';}
 }else return;
 g.updated=Date.now();saveGames();renderHome();if(activeGameId===id){if(gameType(g)==='durak')renderDurak();else renderCheckers();}
}
'''
h=h[:pos]+extra+'\n'+h[pos:]

# Generic native game navigation.
old="""if(pendingMessageNavigation.kind==='checkers'){\n  var game=games[pendingMessageNavigation.messageId];if(!game)return false;\n  var accept=pendingMessageNavigation.accept;pendingMessageNavigation=null;\n  if(accept&&game.status==='invited'&&game.invitee===role)acceptCheckers(game.id);\n  else if(game.status==='invited'&&game.invitee===role)showCheckersRequest();\n  else openCheckers(game.id);\n  return true;\n }"""
new="""if(pendingMessageNavigation.kind==='checkers'||pendingMessageNavigation.kind==='game'){\n  var game=games[pendingMessageNavigation.messageId];if(!game)return false;\n  var accept=pendingMessageNavigation.accept;pendingMessageNavigation=null;\n  if(accept&&game.status==='invited'&&game.invitee===role)acceptGame(game.id);\n  else if(game.status==='invited'&&game.invitee===role)showCheckersRequest();\n  else openGame(game.id);\n  return true;\n }"""
assert old in h
h=h.replace(old,new,1)

# Back navigation for new screens.
needle="if(!$('news').classList.contains('hidden')){showScreen('home');renderHome();return true;}"
h=h.replace(needle,"if(!$('games').classList.contains('hidden')){showScreen('home');renderHome();return true;}\n if(!$('durak').classList.contains('hidden')){activeGameId='';openGames();return true;}\n "+needle,1)

# Event bindings.
anchor="$('newsBackBtn').addEventListener('click',function(){showScreen('home');renderHome();});"
events=r'''$('gamesBackBtn').addEventListener('click',function(){showScreen('home');renderHome();});
$('gamesCheckersBtn').addEventListener('click',function(){window.__gameChoicePeer='';chooseGameOpponent('checkers');});
$('gamesDurakBtn').addEventListener('click',function(){window.__gameChoicePeer='';chooseGameOpponent('durak');});
$('chooseCheckersBtn').addEventListener('click',function(){chooseGameOpponent('checkers');});
$('chooseDurakBtn').addEventListener('click',function(){chooseGameOpponent('durak');});
$('closeGameChoiceBtn').addEventListener('click',closeGameChoice);
$('closeGameOpponentBtn').addEventListener('click',function(){$('gameOpponentModal').classList.add('hidden');});
$('gameChoiceModal').addEventListener('click',function(e){if(e.target===$('gameChoiceModal'))closeGameChoice();});
$('gameOpponentModal').addEventListener('click',function(e){if(e.target===$('gameOpponentModal'))$('gameOpponentModal').classList.add('hidden');});
$('durakBackBtn').addEventListener('click',function(){activeGameId='';openGames();});
$('durakBeatBtn').addEventListener('click',durakBeat);$('durakTakeBtn').addEventListener('click',durakTake);$('durakResignBtn').addEventListener('click',resignDurak);
$('checkersAcceptBtn').onclick=function(){acceptGame(pendingGameAcceptId);};$('checkersDeclineBtn').onclick=function(){declineGame(pendingGameAcceptId);};
$('navFamily').addEventListener('click',function(){currentThread=null;showScreen('home');renderHome();});
$('navGames').addEventListener('click',openGames);$('navNews').addEventListener('click',openNews);$('navMore').addEventListener('click',function(){$('settingsPop').classList.toggle('hidden');});
'''
assert anchor in h
h=h.replace(anchor,events+'\n'+anchor,1)

# Hide new transient modals when switching screens.
h=h.replace("'settingsPop','attachMenu','emojiPanel','newsCaptureMenu','relayModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer'","'settingsPop','attachMenu','emojiPanel','newsCaptureMenu','relayModal','aboutModal','videoLayoutModal','themeModal','reactionPicker','photoViewer','gameChoiceModal','gameOpponentModal'",1)

p.write_text(h)

# Native notifications become game-neutral (payload is end-to-end encrypted here).
mp=Path('duoapp/src/main/java/com/sergey/duochat/MessagingService.java')
m=mp.read_text()
m=m.replace('Приглашает играть в шашки','Приглашает в игру').replace('Принял(а) приглашение в шашки','Принял(а) приглашение в игру').replace('Ваш ход в шашках','Ваш ход в игре')
mp.write_text(m)

# Version metadata.
bp=Path('duoapp/build.gradle');b=bp.read_text().replace('versionCode 6019','versionCode 6020').replace("versionName '6.0.19'","versionName '6.0.20'");bp.write_text(b)
manifest=Path('duoapp/src/main/AndroidManifest.xml');x=manifest.read_text().replace('Наша семья 6.0.19','Наша семья 6.0.20');manifest.write_text(x)

print('Patched OurFamily 6.0.19 -> 6.0.20 games')
