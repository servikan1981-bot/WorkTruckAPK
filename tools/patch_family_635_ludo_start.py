from pathlib import Path
import re

H=Path('duoapp/src/main/assets/index.html')
G=Path('duoapp/build.gradle')
M=Path('duoapp/src/main/AndroidManifest.xml')
h=H.read_text(encoding='utf-8')
g=G.read_text(encoding='utf-8')
m=M.read_text(encoding='utf-8')

if "APP_VERSION='6.0.35'" in h:
    print('6.0.35 hotfix already applied')
    raise SystemExit(0)

# Version bump.
g=g.replace('versionCode 6034','versionCode 6035',1).replace("versionName '6.0.34'","versionName '6.0.35'",1)
m=m.replace('6.0.34','6.0.35')
h=h.replace('6.0.34','6.0.35')

# Some phones reach the Ludo picker but fail before it can close. The only pre-close
# dependency is the external LudoRules object (plus stale saved game data). Embed a
# full fallback rules engine and validate saved games before reuse.
anchor='async function inviteLudo(peers)'
if anchor not in h:
    raise SystemExit('inviteLudo anchor not found')

fallback=r'''function ensureLudoRules(){
 if(window.LudoRules&&typeof window.LudoRules.initial==='function'&&typeof window.LudoRules.roll==='function'&&typeof window.LudoRules.move==='function'&&typeof window.LudoRules.resign==='function')return window.LudoRules;
 var SAFE=[0,8,13,21,26,34,39,47];
 function clone(x){return JSON.parse(JSON.stringify(x));}
 function initial(players){var n=Math.max(2,Math.min(4,players|0)),pieces=[],out=[];for(var i=0;i<n;i++){pieces.push([-1,-1,-1,-1]);out.push(false);}return {players:n,pieces:pieces,out:out,turn:0,dice:null,seq:0,winner:null,last:null};}
 function absPos(player,progress){if(progress<0||progress>51)return -1;return ((player*13)+progress)%52;}
 function active(s,p){return !!s&&p>=0&&p<s.players&&!(s.out&&s.out[p]);}
 function nextPlayer(s,p){for(var i=1;i<=s.players;i++){var q=(p+i)%s.players;if(active(s,q))return q;}return p;}
 function activePlayers(s){var a=[];for(var i=0;i<s.players;i++)if(active(s,i))a.push(i);return a;}
 function movable(s,p,d){var out=[];if(!s||s.winner!==null||s.turn!==p||!active(s,p)||!d)return out;for(var i=0;i<4;i++){var q=s.pieces[p][i];if(q===58)continue;if(q<0){if(d===6)out.push(i);}else if(q+d<=58)out.push(i);}return out;}
 function roll(s,p,d){if(!s||s.winner!==null||s.turn!==p||!active(s,p)||s.dice!==null||d<1||d>6)return null;var n=clone(s);n.dice=d;n.seq++;n.last={kind:'roll',player:p,dice:d};if(!movable(n,p,d).length){n.dice=null;n.turn=nextPlayer(n,p);}return n;}
 function move(s,p,piece){if(!s||s.winner!==null||s.turn!==p||!active(s,p)||s.dice===null)return null;var d=s.dice,legal=movable(s,p,d);if(legal.indexOf(piece)<0)return null;var n=clone(s),from=n.pieces[p][piece],to=from<0?0:from+d;n.pieces[p][piece]=to;var captured=[];if(to>=0&&to<=51){var a=absPos(p,to);if(SAFE.indexOf(a)<0){for(var op=0;op<n.players;op++)if(op!==p&&active(n,op)){for(var j=0;j<4;j++){var oq=n.pieces[op][j];if(oq>=0&&oq<=51&&absPos(op,oq)===a){n.pieces[op][j]=-1;captured.push([op,j]);}}}}}var won=n.pieces[p].every(function(x){return x===58;});n.seq++;n.last={kind:'move',player:p,piece:piece,dice:d,from:from,to:to,captured:captured};n.dice=null;if(won)n.winner=p;else if(d!==6&&captured.length===0)n.turn=nextPlayer(n,p);return n;}
 function resign(s,p){if(!s||s.winner!==null||!active(s,p))return null;var n=clone(s);if(!n.out)n.out=Array(n.players).fill(false);n.out[p]=true;n.pieces[p]=[-1,-1,-1,-1];n.dice=null;n.seq++;n.last={kind:'resign',player:p};var alive=activePlayers(n);if(alive.length===1)n.winner=alive[0];else if(n.turn===p)n.turn=nextPlayer(n,p);return n;}
 window.LudoRules={initial:initial,absPos:absPos,movable:movable,roll:roll,move:move,resign:resign,nextPlayer:nextPlayer,active:active,SAFE:SAFE};
 return window.LudoRules;
}
function validReusableLudoGame(g,players){
 try{return !!g&&(g.status==='active'||g.status==='invited')&&Array.isArray(g.players)&&g.players.length===players.length&&g.players.slice().sort().join('|')===players.slice().sort().join('|')&&g.state&&Array.isArray(g.state.pieces)&&g.state.pieces.length===g.players.length&&(g.state.winner===null||typeof g.state.winner==='undefined');}catch(e){return false;}
}
function ludoSettled(tasks){
 if(Promise.allSettled)return Promise.allSettled(tasks);
 return Promise.all(tasks.map(function(p){return Promise.resolve(p).then(function(value){return {status:'fulfilled',value:value};},function(reason){return {status:'rejected',reason:reason};});}));
}
'''
h=h.replace(anchor,fallback+anchor,1)

invite_re=re.compile(r"async function inviteLudo\(peers\)\{.*?\}\nasync function sendLudoInvites",re.S)
invite_new=r'''async function inviteLudo(peers){
 peers=Array.from(new Set((peers||[]).filter(function(p){return p&&p!==role&&NAMES[p];}))).slice(0,3);
 if(!peers.length){toast('Выберите хотя бы одного участника');return false;}
 var players=[role].concat(peers),same=Object.values(ludoGames).find(function(g){return validReusableLudoGame(g,players);});
 ensureLudoRules();
 if(same){$('ludoPicker').classList.add('hidden');openLudo(same.id);if(same.status==='invited'&&same.inviter===role)sendLudoInvites(same).catch(function(e){console.error('Ludo re-invite failed',e);});return true;}
 var id=randomId(),accepted={};players.forEach(function(p){accepted[p]=p===role;});
 var rules=ensureLudoRules(),state=rules.initial(players.length);
 if(!state||!Array.isArray(state.pieces)||state.pieces.length!==players.length)throw new Error('ludo-state-init');
 var g={id:id,players:players,inviter:role,status:'invited',accepted:accepted,updated:Date.now(),state:state};
 ludoGames[id]=g;saveLudoGames();$('ludoPicker').classList.add('hidden');openLudo(id);
 sendLudoInvites(g).catch(function(err){console.error('Ludo invite send failed',err);toast('Игра запущена. Приглашение будет отправлено при восстановлении связи.');});
 return true;
}
async function sendLudoInvites'''
h,n=invite_re.subn(invite_new,h,count=1)
if n!=1: raise SystemExit('inviteLudo replacement failed')

send_re=re.compile(r"async function sendLudoInvites\(g\)\{.*?\}\nfunction showLudoRequest",re.S)
send_new=r'''async function sendLudoInvites(g){
 var data={gameType:'ludo',gameId:g.id,players:g.players,accepted:g.accepted,state:g.state};
 var pending=g.players.filter(function(p){return p!==role&&!(g.accepted&&g.accepted[p]);});if(!pending.length)pending=ludoPeers(g);
 var tasks=pending.map(function(peer){return Promise.resolve().then(function(){return publishEnvelope(peer,'game_invite',data,5,'ludo_invite',g.id+'_'+peer,true);});});
 await ludoSettled(tasks);toast('Приглашение в Семейную гонку отправлено');if(activeLudoId===g.id)renderLudo();
}
function showLudoRequest'''
h,n=send_re.subn(send_new,h,count=1)
if n!=1: raise SystemExit('sendLudoInvites replacement failed')

broadcast_re=re.compile(r"async function broadcastLudo\(g,kind,extra\)\{.*?\}\nfunction ludoStateOk",re.S)
broadcast_new=r'''async function broadcastLudo(g,kind,extra){var data=Object.assign({gameType:'ludo',gameId:g.id,state:g.state,seq:g.state.seq,accepted:g.accepted},extra||{});var tasks=ludoPeers(g).map(function(peer){return Promise.resolve().then(function(){return publishEnvelope(peer,kind,data,4,'ludo_'+kind,g.id+'_'+g.state.seq+'_'+peer,true);});});return ludoSettled(tasks);}
function ludoStateOk'''
h,n=broadcast_re.subn(broadcast_new,h,count=1)
if n!=1: raise SystemExit('broadcastLudo replacement failed')

old="function openLudo(id){var g=ludoGames[id];if(!g)return;activeLudoId=id;$('checkersRequest').classList.add('hidden');showScreen('ludo');renderLudo();}"
new="function openLudo(id){var g=ludoGames[id];if(!g)return;ensureLudoRules();activeLudoId=id;$('checkersRequest').classList.add('hidden');showScreen('ludo');renderLudo();}"
if old not in h: raise SystemExit('openLudo anchor not found')
h=h.replace(old,new,1)

# Keep exact error visible if anything unexpected remains on a real phone.
old="try{await inviteLudo(peers);}catch(err){console.error('Ludo start failed',err);toast('Не удалось запустить Семейную гонку. Повторите ещё раз.');}"
new="try{await inviteLudo(peers);}catch(err){console.error('Ludo start failed',err);var msg=String((err&&err.message)||err||'ошибка');toast('Не удалось запустить: '+msg.slice(0,80));}"
if old not in h: raise SystemExit('Ludo start catch anchor not found')
h=h.replace(old,new,1)

H.write_text(h,encoding='utf-8')
G.write_text(g,encoding='utf-8')
M.write_text(m,encoding='utf-8')
print('Applied 6.0.35 robust Ludo startup hotfix')
