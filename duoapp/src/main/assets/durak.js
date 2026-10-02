(function(root,factory){
  var api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.DurakRules=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
var SUITS=['c','d','h','s'];
var RANKS=[6,7,8,9,10,11,12,13,14];
function card(suit,rank){return suit+String(rank);}
function suit(c){return String(c||'').charAt(0);}
function rank(c){return parseInt(String(c||'').slice(1),10)||0;}
function deck(){var out=[];SUITS.forEach(function(s){RANKS.forEach(function(r){out.push(card(s,r));});});return out;}
function xorshift(seed){var x=(seed>>>0)||0x6d2b79f5;return function(){x^=x<<13;x^=x>>>17;x^=x<<5;return (x>>>0)/4294967296;};}
function shuffled(seed){var a=deck(),rnd=xorshift(seed>>>0);for(var i=a.length-1;i>0;i--){var j=Math.floor(rnd()*(i+1)),t=a[i];a[i]=a[j];a[j]=t;}return a;}
function clone(s){return JSON.parse(JSON.stringify(s));}
function sortHand(h,trump){return h.slice().sort(function(a,b){var at=suit(a)===trump,bt=suit(b)===trump;if(at!==bt)return at?1:-1;if(suit(a)!==suit(b))return suit(a).localeCompare(suit(b));return rank(a)-rank(b);});}
function beats(def,atk,trump){if(suit(def)===suit(atk))return rank(def)>rank(atk);return suit(def)===trump&&suit(atk)!==trump;}
function tableRanks(t){var m={};(t||[]).forEach(function(p){m[rank(p.a)]=1;if(p.d)m[rank(p.d)]=1;});return m;}
function remove(h,c){var i=h.indexOf(c);if(i<0)return false;h.splice(i,1);return true;}
function refill(s,first,second){while(s.deck.length&&s.hands[first].length<6)s.hands[first].push(s.deck.shift());while(s.deck.length&&s.hands[second].length<6)s.hands[second].push(s.deck.shift());s.hands[0]=sortHand(s.hands[0],s.trump);s.hands[1]=sortHand(s.hands[1],s.trump);}
function outcome(s){if(s.deck.length)return null;var a=s.hands[0].length,b=s.hands[1].length;if(a===0&&b===0)return 'draw';if(a===0)return 0;if(b===0)return 1;return null;}
function lowestTrump(h,trump){var v=h.filter(function(c){return suit(c)===trump;}).map(rank);return v.length?Math.min.apply(null,v):99;}
function initial(seed){
 var d=shuffled(seed>>>0),trumpCard=d[d.length-1],tr=suit(trumpCard),hands=[[],[]];
 for(var i=0;i<6;i++){hands[0].push(d.shift());hands[1].push(d.shift());}
 hands[0]=sortHand(hands[0],tr);hands[1]=sortHand(hands[1],tr);
 var l0=lowestTrump(hands[0],tr),l1=lowestTrump(hands[1],tr),attacker=l0===l1?0:(l0<l1?0:1);
 return {v:1,seed:seed>>>0,trump:tr,trumpCard:trumpCard,deck:d,hands:hands,attacker:attacker,defender:1-attacker,table:[],phase:'attack',attackLimit:Math.min(6,hands[1-attacker].length),seq:0,winner:null,last:null};
}
function legalAttack(s,c){if(s.winner!==null||s.phase!=='attack'||s.hands[s.attacker].indexOf(c)<0)return false;if(s.table.length>=s.attackLimit)return false;if(!s.table.length)return true;return !!tableRanks(s.table)[rank(c)];}
function legalDefense(s,index,c){if(s.winner!==null||s.phase!=='defend'||s.hands[s.defender].indexOf(c)<0)return false;var p=s.table[index];return !!p&&!p.d&&beats(c,p.a,s.trump);}
function attack(state,c){var s=clone(state);if(!legalAttack(s,c))return null;remove(s.hands[s.attacker],c);s.table.push({a:c,d:null});s.phase='defend';s.seq++;s.last={type:'attack',card:c};return s;}
function defend(state,index,c){var s=clone(state);if(!legalDefense(s,index,c))return null;remove(s.hands[s.defender],c);s.table[index].d=c;s.phase=s.table.every(function(p){return !!p.d;})?'attack':'defend';s.seq++;s.last={type:'defend',card:c,index:index};return s;}
function canBeatAll(s){return s.table.length>0&&s.table.every(function(p){return !!p.d;});}
function canThrow(s){if(!canBeatAll(s)||s.table.length>=s.attackLimit)return false;var ranks=tableRanks(s.table);return s.hands[s.attacker].some(function(c){return !!ranks[rank(c)];});}
function done(state){
 var s=clone(state);if(!canBeatAll(s))return null;
 var oldA=s.attacker,oldD=s.defender;refill(s,oldA,oldD);s.table=[];s.attacker=oldD;s.defender=oldA;s.attackLimit=Math.min(6,s.hands[s.defender].length);s.phase='attack';s.seq++;s.last={type:'bito'};s.winner=outcome(s);return s;
}
function pickup(state){
 var s=clone(state);if(!s.table.length||s.winner!==null)return null;
 s.table.forEach(function(p){s.hands[s.defender].push(p.a);if(p.d)s.hands[s.defender].push(p.d);});
 var oldA=s.attacker,oldD=s.defender;refill(s,oldA,oldD);s.hands[oldD]=sortHand(s.hands[oldD],s.trump);s.table=[];s.attacker=oldA;s.defender=oldD;s.attackLimit=Math.min(6,s.hands[s.defender].length);s.phase='attack';s.seq++;s.last={type:'pickup'};s.winner=outcome(s);return s;
}
function view(s,side){return {trump:s.trump,trumpCard:s.trumpCard,deckCount:s.deck.length,hand:s.hands[side].slice(),opponentCount:s.hands[1-side].length,attacker:s.attacker,defender:s.defender,table:clone(s.table),phase:s.phase,attackLimit:s.attackLimit,seq:s.seq,winner:s.winner,last:s.last};}
return {SUITS:SUITS,RANKS:RANKS,deck:deck,initial:initial,suit:suit,rank:rank,beats:beats,sortHand:sortHand,legalAttack:legalAttack,legalDefense:legalDefense,canBeatAll:canBeatAll,canThrow:canThrow,attack:attack,defend:defend,done:done,pickup:pickup,outcome:outcome,view:view};
});

/* 6.0.39: PoolRules is bundled into durak.js because MainActivity always inlines this asset. */
(function(root){
'use strict';
var W=1000,H=500,R=15,LEFT=46,RIGHT=954,TOP=46,BOTTOM=454;
var POCKETS=[[46,46],[500,38],[954,46],[46,454],[500,462],[954,454]];
var BALL_COLORS=['#f7f7f7','#f5d328','#2358c7','#dc3131','#6f3da8','#ef7b1a','#16864b','#7b231f','#111318','#f5d328','#2358c7','#dc3131','#6f3da8','#ef7b1a','#16864b','#7b231f'];
function clonePool(x){return JSON.parse(JSON.stringify(x));}
function rack(){
 var balls=[{n:0,x:255,y:250,pocketed:false}];
 var nums=[1,10,2,11,8,3,12,4,13,5,14,6,15,7,9],idx=0,dx=Math.sqrt(3)*R*1.04;
 for(var row=0;row<5;row++){
  var x=690+row*dx,y0=250-row*R*1.04;
  for(var k=0;k<=row;k++)balls.push({n:nums[idx++],x:x,y:y0+k*2*R*1.04,pocketed:false});
 }
 return balls;
}
function initialPool(){return {seq:0,turn:0,winner:null,groups:[null,null],balls:rack(),breakShot:true,ballInHand:false,last:null};}
function ballByNumber(s,n){return s.balls.find(function(b){return b.n===n;});}
function groupOf(n){return n>=1&&n<=7?'solids':n>=9&&n<=15?'stripes':null;}
function remaining(s,group){return s.balls.filter(function(b){return !b.pocketed&&groupOf(b.n)===group;}).length;}
function legalFirst(s,player,n){var g=s.groups[player];if(!g)return n!==8&&n!==0;return remaining(s,g)===0?n===8:groupOf(n)===g;}
function cueSpot(s){
 var spots=[[250,250],[220,250],[280,250],[250,220],[250,280],[190,250]];
 for(var i=0;i<spots.length;i++){
  var p=spots[i],ok=s.balls.every(function(b){return b.pocketed||b.n===0||Math.hypot(b.x-p[0],b.y-p[1])>R*2.15;});
  if(ok)return p;
 }
 return [180,250];
}
function simulate(state,angle,power,keepFrames){
 var s=clonePool(state),balls=s.balls.map(function(b){return {n:b.n,x:b.x,y:b.y,pocketed:!!b.pocketed,vx:0,vy:0};});
 var cue=balls.find(function(b){return b.n===0;});if(!cue||cue.pocketed)return {balls:balls,pocketed:[],firstContact:null,frames:[]};
 power=Math.max(0.08,Math.min(1,Number(power)||0.5));angle=Number(angle)||0;var speed=14+power*28;cue.vx=Math.cos(angle)*speed;cue.vy=Math.sin(angle)*speed;
 var frames=[],pocketed=[],firstContact=null,lastFrame=-9;
 function snap(){if(!keepFrames)return;frames.push(balls.map(function(b){return {n:b.n,x:+b.x.toFixed(2),y:+b.y.toFixed(2),pocketed:b.pocketed};}));}
 snap();
 for(var step=0;step<1900;step++){
  var moving=false;
  for(var i=0;i<balls.length;i++){
   var b=balls[i];if(b.pocketed)continue;
   if(Math.abs(b.vx)+Math.abs(b.vy)>.015)moving=true;
   b.x+=b.vx*.34;b.y+=b.vy*.34;
   for(var p=0;p<POCKETS.length;p++){
    var pk=POCKETS[p],pr=(p===1||p===4)?24:27;
    if(Math.hypot(b.x-pk[0],b.y-pk[1])<pr){b.pocketed=true;b.vx=b.vy=0;pocketed.push(b.n);break;}
   }
   if(b.pocketed)continue;
   if(b.x-R<LEFT){b.x=LEFT+R;b.vx=Math.abs(b.vx)*.91;}else if(b.x+R>RIGHT){b.x=RIGHT-R;b.vx=-Math.abs(b.vx)*.91;}
   if(b.y-R<TOP){b.y=TOP+R;b.vy=Math.abs(b.vy)*.91;}else if(b.y+R>BOTTOM){b.y=BOTTOM-R;b.vy=-Math.abs(b.vy)*.91;}
  }
  for(var a=0;a<balls.length;a++)for(var j=a+1;j<balls.length;j++){
   var A=balls[a],B=balls[j];if(A.pocketed||B.pocketed)continue;var dx=B.x-A.x,dy=B.y-A.y,d2=dx*dx+dy*dy,min=R*2;if(d2<=0||d2>=min*min)continue;
   var d=Math.sqrt(d2),nx=dx/d,ny=dy/d,over=min-d;A.x-=nx*over/2;A.y-=ny*over/2;B.x+=nx*over/2;B.y+=ny*over/2;
   var rvx=B.vx-A.vx,rvy=B.vy-A.vy,sep=rvx*nx+rvy*ny;if(sep<0){var imp=-(1.92)*sep/2;A.vx-=imp*nx;A.vy-=imp*ny;B.vx+=imp*nx;B.vy+=imp*ny;if(firstContact===null&&(A.n===0||B.n===0))firstContact=A.n===0?B.n:A.n;}
  }
  for(var q=0;q<balls.length;q++){var bb=balls[q];if(bb.pocketed)continue;bb.vx*=.987;bb.vy*=.987;if(Math.abs(bb.vx)<.018)bb.vx=0;if(Math.abs(bb.vy)<.018)bb.vy=0;}
  if(keepFrames&&step-lastFrame>=7){snap();lastFrame=step;}
  if(!moving&&step>8)break;
 }
 if(keepFrames)snap();
 return {balls:balls,pocketed:pocketed,firstContact:firstContact,frames:frames};
}
function shot(state,player,angle,power){
 if(!state||state.winner!==null||state.turn!==player)return null;
 var s=clonePool(state),sim=simulate(s,angle,power,true),beforeGroups=s.groups.slice(),potted=sim.pocketed.slice(),scratch=potted.indexOf(0)>=0;
 s.balls=sim.balls.map(function(b){return {n:b.n,x:+b.x.toFixed(2),y:+b.y.toFixed(2),pocketed:b.pocketed};});
 var first=sim.firstContact,legal=first!==null&&legalFirst(s,player,first),foul=scratch||!legal;
 var firstGroup=null;
 for(var i=0;i<potted.length;i++){var g=groupOf(potted[i]);if(g){firstGroup=g;break;}}
 if(!s.groups[player]&&firstGroup&&!foul){s.groups[player]=firstGroup;s.groups[1-player]=firstGroup==='solids'?'stripes':'solids';}
 var eight=potted.indexOf(8)>=0;
 if(eight){var pg=s.groups[player],cleared=pg&&remaining(state,pg)===0;if(cleared&&!foul)s.winner=player;else s.winner=1-player;}
 var own=s.groups[player],madeOwn=potted.some(function(n){return own?groupOf(n)===own:!!groupOf(n);});
 if(s.winner===null){if(foul){s.turn=1-player;s.ballInHand=true;}else if(madeOwn){s.turn=player;s.ballInHand=false;}else{s.turn=1-player;s.ballInHand=false;}}
 if(s.ballInHand&&s.winner===null){var cb=ballByNumber(s,0),spot=cueSpot(s);cb.pocketed=false;cb.x=spot[0];cb.y=spot[1];}
 s.breakShot=false;s.seq=(Number(s.seq)||0)+1;s.last={kind:'shot',player:player,angle:+angle.toFixed(6),power:+power.toFixed(4),pocketed:potted,firstContact:first,foul:foul,scratch:scratch,groupsBefore:beforeGroups};
 return {state:s,frames:sim.frames};
}
function resignPoolRule(state,player){if(!state||state.winner!==null)return null;var s=clonePool(state);s.winner=1-player;s.seq=(Number(s.seq)||0)+1;s.last={kind:'resign',player:player};return s;}
root.PoolRules={W:W,H:H,R:R,POCKETS:POCKETS,BALL_COLORS:BALL_COLORS,initial:initialPool,shot:shot,simulate:simulate,resign:resignPoolRule,groupOf:groupOf,remaining:remaining,clone:clonePool};
})(typeof window!=='undefined'?window:globalThis);

/* 6.0.39: local one-player American Pool practice mode. */
(function(root){
'use strict';
if(typeof document==='undefined')return;
var solo={active:false,state:null,aim:-0.12,power:.62,animating:false};
function byId(id){return document.getElementById(id);}
function roundRect(ctx,x,y,w,h,r){var q=Math.min(r,w/2,h/2);ctx.beginPath();ctx.moveTo(x+q,y);ctx.arcTo(x+w,y,x+w,y+h,q);ctx.arcTo(x+w,y+h,x,y+h,q);ctx.arcTo(x,y+h,x,y,q);ctx.arcTo(x,y,x+w,y,q);ctx.closePath();}
function ballColor(n){return root.PoolRules.BALL_COLORS[n]||'#eee';}
function drawBall(ctx,b){
 if(b.pocketed)return;var r=root.PoolRules.R,x=b.x,y=b.y,n=b.n,col=ballColor(n);ctx.save();ctx.shadowColor='rgba(0,0,0,.45)';ctx.shadowBlur=7;ctx.shadowOffsetY=5;
 var g=ctx.createRadialGradient(x-r*.38,y-r*.45,2,x,y,r);g.addColorStop(0,'#fff');g.addColorStop(.23,n===0?'#fefefe':col);g.addColorStop(1,n===0?'#cfd3d4':col);ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();ctx.shadowColor='transparent';
 if(n>=9){ctx.save();ctx.beginPath();ctx.arc(x,y,r-1,0,Math.PI*2);ctx.clip();ctx.fillStyle='#f6f3e9';ctx.fillRect(x-r,y-r,r*2,r*2);ctx.fillStyle=col;ctx.fillRect(x-r,y-r*.48,r*2,r*.96);ctx.restore();}
 if(n>0){ctx.fillStyle='#f8f6ed';ctx.beginPath();ctx.arc(x,y,r*.48,0,Math.PI*2);ctx.fill();ctx.fillStyle='#111';ctx.font='bold 10px system-ui';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(String(n),x,y+.5);}
 ctx.fillStyle='rgba(255,255,255,.55)';ctx.beginPath();ctx.arc(x-r*.34,y-r*.38,r*.18,0,Math.PI*2);ctx.fill();ctx.restore();
}
function drawTable(balls,showAim){
 var c=byId('poolCanvas');if(!c)return;var d=Math.min(2,root.devicePixelRatio||1);if(c.width!==1000*d||c.height!==500*d){c.width=1000*d;c.height=500*d;}var ctx=c.getContext('2d');ctx.setTransform(d,0,0,d,0,0);ctx.clearRect(0,0,1000,500);
 var wood=ctx.createLinearGradient(0,0,1000,500);wood.addColorStop(0,'#a8733f');wood.addColorStop(.45,'#4d2a14');wood.addColorStop(1,'#bc8249');ctx.fillStyle=wood;roundRect(ctx,4,4,992,492,34);ctx.fill();
 var rail=ctx.createLinearGradient(0,30,0,470);rail.addColorStop(0,'#1c936d');rail.addColorStop(.5,'#07583e');rail.addColorStop(1,'#033d2b');ctx.fillStyle=rail;roundRect(ctx,25,25,950,450,26);ctx.fill();
 var felt=ctx.createRadialGradient(500,210,40,500,250,530);felt.addColorStop(0,'#119168');felt.addColorStop(1,'#044b35');ctx.fillStyle=felt;roundRect(ctx,46,46,908,408,16);ctx.fill();
 ctx.strokeStyle='rgba(255,255,255,.07)';ctx.lineWidth=1;for(var y=70;y<450;y+=28){ctx.beginPath();ctx.moveTo(55,y);ctx.lineTo(945,y);ctx.stroke();}
 root.PoolRules.POCKETS.forEach(function(p,i){ctx.fillStyle='#050706';ctx.beginPath();ctx.arc(p[0],p[1],i===1||i===4?25:29,0,Math.PI*2);ctx.fill();});
 var cue=balls.find(function(b){return b.n===0&&!b.pocketed;});if(showAim&&cue&&solo.active&&!solo.animating&&solo.state&&solo.state.winner===null){ctx.save();ctx.setLineDash([11,9]);ctx.strokeStyle='rgba(255,255,255,.78)';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(cue.x,cue.y);ctx.lineTo(cue.x+Math.cos(solo.aim)*720,cue.y+Math.sin(solo.aim)*720);ctx.stroke();ctx.setLineDash([]);ctx.strokeStyle='#d7a65a';ctx.lineWidth=9;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(cue.x-Math.cos(solo.aim)*33,cue.y-Math.sin(solo.aim)*33);ctx.lineTo(cue.x-Math.cos(solo.aim)*210,cue.y-Math.sin(solo.aim)*210);ctx.stroke();ctx.strokeStyle='#f3e1ad';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(cue.x-Math.cos(solo.aim)*33,cue.y-Math.sin(solo.aim)*33);ctx.lineTo(cue.x-Math.cos(solo.aim)*208,cue.y-Math.sin(solo.aim)*208);ctx.stroke();ctx.restore();}
 balls.slice().sort(function(a,b){return a.n===0?1:b.n===0?-1:a.n-b.n;}).forEach(function(b){drawBall(ctx,b);});
}
function groupLabel(g){return g==='solids'?'Сплошные 1–7':g==='stripes'?'Полосатые 9–15':'Группа не определена';}
function renderMini(){var box=byId('poolBalls');if(!box||!solo.state)return;box.innerHTML='';for(var n=1;n<=15;n++){var b=solo.state.balls.find(function(x){return x.n===n;}),e=document.createElement('span');e.className='pool-mini-ball'+(b&&b.pocketed?' done':'');e.textContent=String(n);e.style.background=ballColor(n);box.appendChild(e);}}
function renderSolo(){
 if(!solo.active||!solo.state)return;var s=solo.state,pb=byId('poolPlayers');if(pb){pb.innerHTML='';var me=document.createElement('div');me.className='pool-player current';me.innerHTML='<strong>Вы · тренировка</strong><div class="pool-group">'+groupLabel(s.groups[0])+'</div>';var mode=document.createElement('div');mode.className='pool-player';mode.innerHTML='<strong>🎯 Один игрок</strong><div class="pool-group">Без сети и приглашений</div>';pb.appendChild(me);pb.appendChild(mode);}
 drawTable(s.balls,true);renderMini();var status='',hint='';
 if(s.winner!==null){status=s.winner===0?'🏆 Тренировка завершена':'🎱 Восьмёрка забита раньше времени';hint=s.winner===0?'Отличная партия. Нажмите «Новая партия», чтобы сыграть ещё раз.':'Нажмите «Новая партия» и попробуйте снова.';}
 else if(solo.animating){status='Шары движутся…';hint='Ждём полной остановки шаров.';}
 else{status='Ваш ход · тренировка';hint=s.last&&s.last.foul?'Был фол. Биток выставлен автоматически — можно продолжать.':'Коснитесь стола для прицеливания, настройте силу и нажмите «УДАР».';}
 if(byId('poolTitle'))byId('poolTitle').textContent='Бильярд · 8-ball · тренировка';if(byId('poolStatus'))byId('poolStatus').textContent=status;if(byId('poolHint'))byId('poolHint').textContent=hint;if(byId('poolPower'))byId('poolPower').value=String(Math.round(solo.power*100));if(byId('poolPowerValue'))byId('poolPowerValue').textContent=Math.round(solo.power*100)+'%';if(byId('poolHitBtn'))byId('poolHitBtn').disabled=solo.animating||s.winner!==null;if(byId('poolRetryInviteBtn'))byId('poolRetryInviteBtn').classList.add('hidden');if(byId('poolResignBtn')){byId('poolResignBtn').classList.remove('hidden');byId('poolResignBtn').textContent='Новая партия';}
}
function showPoolOnly(){['setup','home','picker','chat','news','more','adminArchive','adminChat','checkers','gamesHub','durak','pool'].forEach(function(id){var el=byId(id);if(el)el.classList.toggle('hidden',id!=='pool');});var nav=byId('bottomNav');if(nav)nav.classList.add('hidden');var req=byId('checkersRequest');if(req)req.classList.add('hidden');}
function resetSolo(){solo.state=root.PoolRules.initial();solo.state.turn=0;solo.aim=-0.12;solo.power=.62;solo.animating=false;renderSolo();}
function enterSolo(){solo.active=true;var modal=byId('opponentChoice');if(modal)modal.classList.add('hidden');showPoolOnly();resetSolo();}
function leaveSolo(){if(!solo.active)return;solo.active=false;solo.state=null;solo.animating=false;if(byId('poolResignBtn'))byId('poolResignBtn').textContent='Сдаться';}
function setAim(ev){if(!solo.active||solo.animating||!solo.state||solo.state.winner!==null)return;var cue=solo.state.balls.find(function(b){return b.n===0&&!b.pocketed;});if(!cue)return;var c=byId('poolCanvas'),r=c.getBoundingClientRect(),x=(ev.clientX-r.left)/r.width*1000,y=(ev.clientY-r.top)/r.height*500;solo.aim=Math.atan2(y-cue.y,x-cue.x);drawTable(solo.state.balls,true);}
function animate(frames,done){if(!frames||!frames.length){done();return;}var stride=Math.max(1,Math.ceil(frames.length/150)),i=0;function tick(){if(!solo.active){done();return;}drawTable(frames[Math.min(i,frames.length-1)],false);i+=stride;if(i<frames.length)root.requestAnimationFrame(tick);else{drawTable(frames[frames.length-1],false);setTimeout(done,60);}}tick();}
function shootSolo(){if(!solo.active||solo.animating||!solo.state||solo.state.winner!==null)return;solo.state.turn=0;var result=root.PoolRules.shot(solo.state,0,solo.aim,solo.power);if(!result)return;if(result.state.winner===null)result.state.turn=0;solo.state=result.state;solo.animating=true;renderSolo();animate(result.frames,function(){solo.animating=false;if(solo.active)renderSolo();});}
function addSoloButton(){var modal=byId('opponentChoice'),box=byId('opponentList');if(!modal||!box||modal.classList.contains('hidden')||box.querySelector('[data-pool-solo]'))return;var b=document.createElement('button');b.type='button';b.setAttribute('data-pool-solo','1');b.textContent='🎯 Играть одному · тренировка';b.style.cssText='font-weight:900;border:2px solid #19936a;background:#e8fff5;color:#076345;margin-bottom:8px';b.addEventListener('click',function(ev){ev.preventDefault();ev.stopPropagation();enterSolo();});box.insertBefore(b,box.firstChild);}
function refreshVersionLabel(){var el=byId('myName');if(el&&el.textContent.indexOf('Наша семья')>=0)el.textContent='Наша семья · v6.0.39';}
function install(){
 var hub=byId('hubPoolBtn'),pick=byId('pickPoolBtn');if(hub)hub.addEventListener('click',function(){setTimeout(addSoloButton,0);});if(pick)pick.addEventListener('click',function(){setTimeout(addSoloButton,0);});
 var canvas=byId('poolCanvas');if(canvas){canvas.addEventListener('pointerdown',function(e){if(!solo.active)return;e.preventDefault();e.stopImmediatePropagation();setAim(e);},true);canvas.addEventListener('pointermove',function(e){if(!solo.active||(!e.buttons&&!(e.pressure>0)))return;e.preventDefault();e.stopImmediatePropagation();setAim(e);},true);}
 var power=byId('poolPower');if(power)power.addEventListener('input',function(e){if(!solo.active)return;e.stopImmediatePropagation();solo.power=Math.max(.12,Math.min(1,Number(power.value)/100));if(byId('poolPowerValue'))byId('poolPowerValue').textContent=Math.round(solo.power*100)+'%';drawTable(solo.state.balls,true);},true);
 var hit=byId('poolHitBtn');if(hit)hit.addEventListener('click',function(e){if(!solo.active)return;e.preventDefault();e.stopImmediatePropagation();shootSolo();},true);
 var reset=byId('poolResignBtn');if(reset)reset.addEventListener('click',function(e){if(!solo.active)return;e.preventDefault();e.stopImmediatePropagation();resetSolo();},true);
 var back=byId('poolBackBtn');if(back)back.addEventListener('click',function(){if(solo.active)leaveSolo();},true);
 var originalBack=root.__ourFamilyHandleBack;if(typeof originalBack==='function'){root.__ourFamilyHandleBack=function(){if(solo.active)leaveSolo();return originalBack.apply(this,arguments);};}
 refreshVersionLabel();setTimeout(refreshVersionLabel,700);setTimeout(refreshVersionLabel,2200);
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install);else setTimeout(install,0);
})(typeof window!=='undefined'?window:globalThis);
