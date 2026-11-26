(function(root){
'use strict';

var doc=root.document;
var W=960,H=540;
var state={level:1,score:0,lives:3,high:0,running:false,paused:false,started:false,fullscreen:false,lastTs:0,raf:0,paddle:null,balls:[],bricks:[],bonuses:[],particles:[],wideUntil:0,message:'',messageUntil:0};
var canvas=null,ctx=null,audioCtx=null;
var joyActive=false,joyPointerId=null,joyDir=0,joyRaf=0,joyLastTs=0;

function clamp(v,a,b){return Math.max(a,Math.min(b,v));}
function speedForLevel(level){return Math.min(430,320+(Math.max(1,level)-1)*12);}
function brickRowsForLevel(level){return Math.min(8,5+Math.floor((Math.max(1,level)-1)/2));}
function randSeed(n){var x=Math.sin(n*12.9898+78.233)*43758.5453;return x-Math.floor(x);}
function buildLevel(level){
 var rows=brickRowsForLevel(level),cols=10,out=[],bw=82,bh=26,gap=8,left=31,top=58;
 for(var r=0;r<rows;r++)for(var c=0;c<cols;c++){
  var skip=(level>2)&&((r+c+level)%9===0)&&(r>0);
  if(skip)continue;
  var hp=1;
  if(level>=4&&((r*cols+c+level)%11===0))hp=2;
  out.push({x:left+c*(bw+gap),y:top+r*(bh+gap),w:bw,h:bh,hp:hp,maxHp:hp,row:r,col:c});
 }
 return out;
}

function ensureStyle(){
 if(!doc||doc.getElementById('arkanoidStyle'))return;
 var s=doc.createElement('style');s.id='arkanoidStyle';s.textContent='\
.arkanoid-card{background:linear-gradient(135deg,#141d48,#253d83 50%,#461874)!important;color:#fff!important;border:1px solid #6379ff!important}.arkanoid-card span{color:#e8edff!important}.arkanoid-card .emoji{font-size:38px!important}\
#arkanoid{display:flex;flex-direction:column;background:#050817;color:#fff}.ark-body{flex:1;min-height:0;display:flex;flex-direction:column;padding:8px;gap:8px;background:radial-gradient(circle at 50% 15%,#142754 0,#09132d 42%,#040713 100%)}\
.ark-hud{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;max-width:960px;width:100%;margin:0 auto}.ark-stat{background:rgba(3,8,24,.78);border:1px solid rgba(130,160,255,.28);border-radius:12px;padding:7px 9px;text-align:center;font-weight:850;font-size:13px}.ark-stat strong{display:block;color:#ffd85e;font-size:18px;margin-top:1px}\
.ark-shell{position:relative;width:min(100%,960px);aspect-ratio:16/9;margin:0 auto;border-radius:16px;overflow:hidden;border:1px solid rgba(122,159,255,.45);box-shadow:0 12px 36px rgba(0,0,0,.45);background:#02040d}.ark-shell canvas{width:100%;height:100%;display:block;touch-action:none;user-select:none;-webkit-user-select:none}.ark-overlay{position:absolute;inset:0;display:grid;place-items:center;pointer-events:none}.ark-overlay span{background:rgba(2,6,20,.72);border:1px solid rgba(255,255,255,.18);padding:12px 18px;border-radius:16px;font-weight:900;font-size:18px;text-align:center;max-width:80%}\
.ark-controls{display:flex;gap:8px;max-width:960px;width:100%;margin:0 auto}.ark-controls button{flex:1;border:0;border-radius:13px;padding:11px 8px;font-weight:900}.ark-start{background:#25a66a;color:#fff}.ark-pause{background:#e9eefc;color:#1a2850}.ark-full{background:#355ff0;color:#fff}.ark-hint{text-align:center;color:#b9c7ec;font-size:12px;max-width:960px;margin:0 auto}.arkanoid-fullscreen-mode #arkanoid{position:fixed;inset:0;z-index:230;min-height:100dvh}.arkanoid-fullscreen-mode #arkanoid .topbar,.arkanoid-fullscreen-mode #bottomNav{display:none!important}.arkanoid-fullscreen-mode .ark-body{padding:4px}.arkanoid-fullscreen-mode .ark-hud{grid-template-columns:repeat(4,1fr);gap:4px}.arkanoid-fullscreen-mode .ark-stat{padding:4px 6px;font-size:11px}.arkanoid-fullscreen-mode .ark-stat strong{display:inline;font-size:14px;margin-left:4px}.arkanoid-fullscreen-mode .ark-shell{width:min(100vw,calc(100vh * 16 / 9));max-height:calc(100vh - 82px)}.arkanoid-fullscreen-mode .ark-controls{max-width:min(100vw,960px)}\
';doc.head.appendChild(s);
 var j=doc.createElement('style');j.id='arkanoidInlineJoystickStyle';j.textContent='\
.ark-joy-dock{display:flex;justify-content:center;align-items:center;min-height:82px;width:100%;max-width:960px;margin:0 auto;touch-action:none}.ark-joy-wrap{display:flex;align-items:center;gap:12px;padding:8px 15px;border-radius:24px;background:#09132f;border:1px solid #536fb8}.ark-joy-arrow{font-size:25px;font-weight:900;color:#8db0ff}.ark-joy-label{font-size:12px;font-weight:900;color:#d8e1ff}.ark-joy-track{position:relative;width:155px;height:62px;border-radius:40px;background:#152b60;border:2px solid #668cff;touch-action:none;box-shadow:inset 0 3px 12px rgba(0,0,0,.45)}.ark-joy-track:before{content:"";position:absolute;left:50%;top:8px;bottom:8px;width:2px;background:rgba(255,255,255,.25)}.ark-joy-knob{position:absolute;left:50%;top:50%;width:48px;height:48px;margin:-24px 0 0 -24px;border-radius:50%;background:radial-gradient(circle at 35% 30%,#dbe8ff,#4d7cff 58%,#173b9b);border:2px solid #fff;box-shadow:0 4px 16px #245be0;transform:translateX(0)}\
.arkanoid-fullscreen-mode #arkanoid .ark-body{position:relative;padding-right:145px!important}.arkanoid-fullscreen-mode .ark-joy-dock{position:absolute;right:8px;top:50%;transform:translateY(-50%);width:125px;min-height:0;z-index:20}.arkanoid-fullscreen-mode .ark-joy-wrap{width:112px;flex-direction:column;padding:10px 5px;gap:7px}.arkanoid-fullscreen-mode .ark-joy-track{width:105px;height:60px}.arkanoid-fullscreen-mode .ark-joy-arrow{display:none}\
';doc.head.appendChild(j);
}

function ensureUi(){
 if(!doc)return false;
 ensureStyle();
 var grid=doc.querySelector('#gamesHub .game-grid'),hub=doc.getElementById('hubArkanoidBtn');
 if(grid&&!hub){
  hub=doc.createElement('button');hub.id='hubArkanoidBtn';hub.className='game-card arkanoid-card';hub.innerHTML='<span class="emoji">🧱</span><strong>Арканоид</strong><span>Одиночная игра · уровни · бонусы</span>';grid.appendChild(hub);
 }
 if(hub&&hub.dataset.arkanoidBound!=='1'){hub.dataset.arkanoidBound='1';hub.addEventListener('click',openGame);}
 if(!doc.getElementById('arkanoid')){
  var section=doc.createElement('section');section.id='arkanoid';section.className='screen hidden';section.innerHTML='\
<div class="topbar"><button id="arkBackBtn" class="iconbtn">‹</button><div class="who"><div class="name">Арканоид</div><div id="arkTopStatus" class="status">Одиночная игра</div></div><button id="arkFullscreenBtn" class="iconbtn" type="button" title="На весь экран">⛶</button></div>\
<div class="ark-body">\
 <div class="ark-hud"><div class="ark-stat">Уровень<strong id="arkLevel">1</strong></div><div class="ark-stat">Очки<strong id="arkScore">0</strong></div><div class="ark-stat">Жизни<strong id="arkLives">3</strong></div><div class="ark-stat">Рекорд<strong id="arkHigh">0</strong></div></div>\
 <div class="ark-shell"><canvas id="arkCanvas" width="960" height="540" aria-label="Арканоид"></canvas><div id="arkOverlay" class="ark-overlay"><span>Нажмите «Старт»</span></div></div>\
 <div class="ark-controls"><button id="arkStartBtn" class="ark-start">▶ Старт</button><button id="arkPauseBtn" class="ark-pause">⏸ Пауза</button><button id="arkFullBtn" class="ark-full">⛶ Полный экран</button></div>\
 <div id="arkJoyDock" class="ark-joy-dock"><div class="ark-joy-wrap"><span class="ark-joy-arrow">◀</span><div id="arkJoyTrack" class="ark-joy-track" aria-label="Джойстик управления платформой"><div id="arkJoyKnob" class="ark-joy-knob"></div></div><span class="ark-joy-arrow">▶</span><span class="ark-joy-label">Платформа</span></div></div>\
 <div class="ark-hint">Управляйте платформой джойстиком. «Старт» запускает игру и шар.</div>\
</div>';
  var nav=doc.getElementById('bottomNav');if(nav&&nav.parentNode)nav.parentNode.insertBefore(section,nav);else doc.body.appendChild(section);
  bindUi();
 }
 return true;
}

function bindUi(){
 canvas=doc.getElementById('arkCanvas');ctx=canvas&&canvas.getContext('2d');
 var back=doc.getElementById('arkBackBtn'),fs=doc.getElementById('arkFullscreenBtn'),fs2=doc.getElementById('arkFullBtn'),start=doc.getElementById('arkStartBtn'),pause=doc.getElementById('arkPauseBtn');
 if(back)back.addEventListener('click',closeGame);
 if(fs)fs.addEventListener('click',function(){setFullscreen(!state.fullscreen);});
 if(fs2)fs2.addEventListener('click',function(){setFullscreen(!state.fullscreen);});
 if(start)start.addEventListener('click',function(){unlockAudio();if(!state.started||state.lives<=0)newGame();else if(state.balls.some(function(b){return b.stuck;}))launchStuckBalls();else newGame();updateUi();});
 if(pause)pause.addEventListener('click',togglePause);
 if(canvas){
  canvas.addEventListener('pointerdown',function(e){unlockAudio();movePaddle(e);if(state.started&&state.balls.some(function(b){return b.stuck;}))launchStuckBalls();try{canvas.setPointerCapture(e.pointerId);}catch(_e){};});
  canvas.addEventListener('pointermove',function(e){if(e.buttons||e.pointerType==='touch'||e.pointerType==='pen')movePaddle(e);});
 }
 bindJoystick();
 try{state.high=Number(root.localStorage.getItem('ourfamily_arkanoid_high')||0)||0;}catch(e){}
 initScene();draw();updateUi();
}

function bindJoystick(){
 var track=doc&&doc.getElementById('arkJoyTrack');if(!track||track.dataset.directBound==='1')return;track.dataset.directBound='1';
 function pos(e){var r=track.getBoundingClientRect(),half=Math.max(1,r.width/2-25),shift=Math.max(0,r.width/2-29),dx=e.clientX-(r.left+r.width/2);joyDir=clamp(dx/half,-1,1);var k=doc.getElementById('arkJoyKnob');if(k)k.style.transform='translateX('+(joyDir*shift).toFixed(1)+'px)';}
 function down(e){e.preventDefault();unlockAudio();joyActive=true;joyPointerId=e.pointerId;pos(e);try{track.setPointerCapture(e.pointerId);}catch(_e){}joyLastTs=0;if(!joyRaf)joyRaf=root.requestAnimationFrame(joyStep);}
 function move(e){if(!joyActive||e.pointerId!==joyPointerId)return;e.preventDefault();pos(e);}
 function up(e){if(joyPointerId!==null&&e.pointerId!==joyPointerId)return;stopJoystick();}
 track.addEventListener('pointerdown',down,{passive:false});track.addEventListener('pointermove',move,{passive:false});track.addEventListener('pointerup',up,{passive:false});track.addEventListener('pointercancel',up,{passive:false});track.addEventListener('lostpointercapture',up,{passive:false});
}
function stopJoystick(){joyActive=false;joyPointerId=null;joyDir=0;joyLastTs=0;var k=doc&&doc.getElementById('arkJoyKnob');if(k)k.style.transform='translateX(0px)';}
function joyStep(ts){joyRaf=0;if(!joyActive){joyLastTs=0;return;}if(!joyLastTs)joyLastTs=ts;var dt=Math.min(.04,Math.max(0,(ts-joyLastTs)/1000));joyLastTs=ts;if(Math.abs(joyDir)>.07)movePaddleDirect(joyDir*780*dt);joyRaf=root.requestAnimationFrame(joyStep);}
function movePaddleDirect(dx){if(!state.paddle)return;state.paddle.x=clamp(state.paddle.x+dx,8,W-state.paddle.w-8);state.balls.forEach(function(b){if(b.stuck)b.x=state.paddle.x+state.paddle.w/2;});if(!state.running||state.paused)draw();}

function initScene(){
 state.paddle={x:W/2-70,y:H-42,w:140,h:16};
 state.bricks=buildLevel(state.level);state.bonuses=[];state.particles=[];state.balls=[makeBall(true)];
}
function makeBall(stuck){var s=speedForLevel(state.level);return {x:W/2,y:H-60,r:9,vx:s*.58,vy:-Math.sqrt(Math.max(1,s*s-(s*.58)*(s*.58))),stuck:!!stuck,trail:[]};}
function newGame(){state.level=1;state.score=0;state.lives=3;state.started=true;state.running=true;state.paused=false;state.lastTs=0;state.wideUntil=0;initScene();state.message='Используйте джойстик и нажмите «Старт»';state.messageUntil=performance.now()+1600;draw();updateUi();loop();}
function nextLevel(){state.level++;state.running=true;state.paused=false;state.lastTs=0;state.wideUntil=0;initScene();state.message='Уровень '+state.level;state.messageUntil=performance.now()+1500;sound('clear',1);updateUi();loop();}
function launchStuckBalls(){var speed=speedForLevel(state.level);state.balls.forEach(function(b,i){if(!b.stuck)return;b.stuck=false;var a=(-Math.PI/2)+((i%3)-1)*.18;b.vx=Math.cos(a)*speed;b.vy=Math.sin(a)*speed;});state.running=true;state.paused=false;state.lastTs=0;sound('paddle',.55);loop();updateUi();}
function movePaddle(e){if(!canvas||!state.paddle)return;var r=canvas.getBoundingClientRect();var x=(e.clientX-r.left)/Math.max(1,r.width)*W;state.paddle.x=clamp(x-state.paddle.w/2,8,W-state.paddle.w-8);state.balls.forEach(function(b){if(b.stuck)b.x=state.paddle.x+state.paddle.w/2;});if(!state.running)draw();}
function togglePause(){if(!state.started)return;state.paused=!state.paused;state.running=!state.paused;state.lastTs=0;if(state.running)loop();updateUi();}

function circleRectHit(b,r){var nx=clamp(b.x,r.x,r.x+r.w),ny=clamp(b.y,r.y,r.y+r.h);var dx=b.x-nx,dy=b.y-ny;return dx*dx+dy*dy<=b.r*b.r;}
function normalizeBall(b,target){var s=Math.hypot(b.vx,b.vy)||target;b.vx=b.vx/s*target;b.vy=b.vy/s*target;}
function update(dt,now){
 if(state.wideUntil&&now>state.wideUntil){state.paddle.w=140;state.wideUntil=0;}
 var p=state.paddle;
 for(var bi=state.balls.length-1;bi>=0;bi--){var b=state.balls[bi];if(b.stuck){b.x=p.x+p.w/2;b.y=p.y-b.r-2;continue;}var prevX=b.x,prevY=b.y;b.x+=b.vx*dt;b.y+=b.vy*dt;
  b.trail.push({x:b.x,y:b.y,a:1});if(b.trail.length>7)b.trail.shift();
  if(b.x-b.r<0){b.x=b.r;b.vx=Math.abs(b.vx);sound('wall',.25);}else if(b.x+b.r>W){b.x=W-b.r;b.vx=-Math.abs(b.vx);sound('wall',.25);}if(b.y-b.r<0){b.y=b.r;b.vy=Math.abs(b.vy);sound('wall',.25);}
  if(b.y-b.r>H){state.balls.splice(bi,1);continue;}
  if(b.vy>0&&circleRectHit(b,p)){b.y=p.y-b.r-1;var rel=clamp((b.x-(p.x+p.w/2))/(p.w/2),-1,1);var speed=clamp(Math.hypot(b.vx,b.vy)*1.012,speedForLevel(state.level),465);var angle=(-Math.PI/2)+(rel*.92);b.vx=Math.cos(angle)*speed;b.vy=Math.sin(angle)*speed;sound('paddle',clamp(speed/430,.4,1));}
  for(var i=0;i<state.bricks.length;i++){var br=state.bricks[i];if(br.hp<=0||!circleRectHit(b,br))continue;
   var wasX=prevX< br.x-b.r || prevX>br.x+br.w+b.r;if(wasX)b.vx*=-1;else b.vy*=-1;br.hp--;state.score+=br.hp<=0?12:4;makeParticles(br.x+br.w/2,br.y+br.h/2,br.row);sound('brick',clamp(Math.hypot(b.vx,b.vy)/430,.35,1));if(br.hp<=0&&Math.random()<.13)spawnBonus(br);break;}
  normalizeBall(b,clamp(Math.hypot(b.vx,b.vy),280,465));
 }
 if(!state.balls.length){state.lives--;sound('lose',.8);if(state.lives<=0){state.running=false;state.started=false;state.message='Игра окончена · '+state.score+' очков';state.messageUntil=Infinity;saveHigh();updateUi();return;}state.balls=[makeBall(true)];state.message='Жизнь потеряна';state.messageUntil=now+1100;}
 for(var k=state.bonuses.length-1;k>=0;k--){var bo=state.bonuses[k];bo.y+=bo.vy*dt;bo.spin+=dt*4;if(bo.y>H+30){state.bonuses.splice(k,1);continue;}if(bo.y+14>=p.y&&bo.y-14<=p.y+p.h&&bo.x>=p.x-12&&bo.x<=p.x+p.w+12){applyBonus(bo.type);state.bonuses.splice(k,1);}}
 state.particles.forEach(function(q){q.x+=q.vx*dt;q.y+=q.vy*dt;q.vy+=90*dt;q.life-=dt;});state.particles=state.particles.filter(function(q){return q.life>0;});
 if(state.bricks.every(function(br){return br.hp<=0;})){state.running=false;saveHigh();setTimeout(function(){if(state.started)nextLevel();},650);}
 saveHigh();
}

function spawnBonus(br){var types=['wide','multi','life'];var type=types[Math.floor(Math.random()*types.length)];state.bonuses.push({x:br.x+br.w/2,y:br.y+br.h/2,vy:125,type:type,spin:0});}
function applyBonus(type){if(type==='wide'){state.paddle.w=210;state.paddle.x=clamp(state.paddle.x,8,W-state.paddle.w-8);state.wideUntil=performance.now()+12000;state.message='Бонус: широкая платформа';}
 else if(type==='multi'){var base=state.balls[0]||makeBall(false),speed=speedForLevel(state.level);[-.38,.38].forEach(function(off){var ang=Math.atan2(base.vy,base.vx)+off;state.balls.push({x:base.x,y:base.y,r:9,vx:Math.cos(ang)*speed,vy:Math.sin(ang)*speed,stuck:false,trail:[]});});state.message='Бонус: три шара';}
 else if(type==='life'){state.lives=Math.min(5,state.lives+1);state.message='Бонус: +1 жизнь';}
 state.messageUntil=performance.now()+1100;sound('bonus',.9);updateUi();}
function makeParticles(x,y,row){var cols=['#ff4d54','#ff9f35','#ffd94a','#2bd88f','#35a6ff','#9b5cff'];for(var i=0;i<6;i++){var a=(Math.PI*2*i/6)+Math.random()*.4,s=55+Math.random()*85;state.particles.push({x:x,y:y,vx:Math.cos(a)*s,vy:Math.sin(a)*s,life:.35+Math.random()*.2,c:cols[row%cols.length]});}}

function drawRoundRect(c,x,y,w,h,r){r=Math.min(r,w/2,h/2);c.beginPath();c.moveTo(x+r,y);c.arcTo(x+w,y,x+w,y+h,r);c.arcTo(x+w,y+h,x,y+h,r);c.arcTo(x,y+h,x,y,r);c.arcTo(x,y,x+w,y,r);c.closePath();}
function draw(){if(!ctx||!canvas)return;var g=ctx.createLinearGradient(0,0,0,H);g.addColorStop(0,'#071437');g.addColorStop(.6,'#0a1030');g.addColorStop(1,'#020511');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
 for(var s=0;s<70;s++){var x=(s*137)%W,y=(s*83)%H,tw=.25+.55*randSeed(s+state.level);ctx.globalAlpha=tw;ctx.fillStyle='#fff';ctx.fillRect(x,y,1.5,1.5);}ctx.globalAlpha=1;
 var colors=[['#ff333d','#b7192c'],['#ff941f','#c75511'],['#ffd736','#b58b08'],['#22cf7f','#0b8f55'],['#18a6ff','#0b65c7'],['#9b4dff','#6120bd'],['#ff4fc8','#ad247d'],['#56e7e2','#178d98']];
 state.bricks.forEach(function(br){if(br.hp<=0)return;var cc=colors[br.row%colors.length],bg=ctx.createLinearGradient(br.x,br.y,br.x,br.y+br.h);bg.addColorStop(0,br.hp>1?'#d9e4ff':cc[0]);bg.addColorStop(1,br.hp>1?'#78849f':cc[1]);ctx.fillStyle=bg;drawRoundRect(ctx,br.x,br.y,br.w,br.h,5);ctx.fill();ctx.strokeStyle='rgba(255,255,255,.42)';ctx.lineWidth=1;ctx.stroke();ctx.fillStyle='rgba(255,255,255,.34)';drawRoundRect(ctx,br.x+4,br.y+3,br.w-8,4,2);ctx.fill();});
 state.bonuses.forEach(function(b){ctx.save();ctx.translate(b.x,b.y);ctx.rotate(b.spin);var col=b.type==='wide'?'#55ff83':(b.type==='life'?'#ff4d67':'#4cb5ff');ctx.shadowColor=col;ctx.shadowBlur=16;ctx.fillStyle=col;ctx.beginPath();ctx.arc(0,0,14,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;ctx.fillStyle='#07122a';ctx.font='900 13px system-ui';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(b.type==='wide'?'↔':(b.type==='life'?'♥':'3'),0,1);ctx.restore();});
 state.particles.forEach(function(q){ctx.globalAlpha=clamp(q.life/.45,0,1);ctx.fillStyle=q.c;ctx.fillRect(q.x-2,q.y-2,4,4);});ctx.globalAlpha=1;
 var p=state.paddle,pg=ctx.createLinearGradient(p.x,p.y,p.x,p.y+p.h);pg.addColorStop(0,'#72d8ff');pg.addColorStop(.45,'#2c72ff');pg.addColorStop(1,'#15296b');ctx.shadowColor='#2f80ff';ctx.shadowBlur=18;ctx.fillStyle=pg;drawRoundRect(ctx,p.x,p.y,p.w,p.h,8);ctx.fill();ctx.shadowBlur=0;ctx.strokeStyle='rgba(255,255,255,.7)';ctx.stroke();
 state.balls.forEach(function(b){b.trail.forEach(function(t,i){ctx.globalAlpha=(i+1)/b.trail.length*.12;ctx.fillStyle='#55b8ff';ctx.beginPath();ctx.arc(t.x,t.y,b.r*(.5+i/b.trail.length*.4),0,Math.PI*2);ctx.fill();});ctx.globalAlpha=1;var bg=ctx.createRadialGradient(b.x-3,b.y-4,1,b.x,b.y,b.r);bg.addColorStop(0,'#fff');bg.addColorStop(.3,'#dff5ff');bg.addColorStop(.72,'#6fc7ff');bg.addColorStop(1,'#2d62a8');ctx.shadowColor='#4db8ff';ctx.shadowBlur=14;ctx.fillStyle=bg;ctx.beginPath();ctx.arc(b.x,b.y,b.r,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;});
 var ov=doc.getElementById('arkOverlay');if(ov){if(state.message&&(state.messageUntil===Infinity||performance.now()<state.messageUntil)){ov.classList.remove('hidden');ov.firstElementChild.textContent=state.message;}else ov.classList.add('hidden');}
}

function loop(ts){if(state.raf||!state.running||state.paused)return;function frame(t){state.raf=0;if(!state.running||state.paused){draw();return;}if(!state.lastTs)state.lastTs=t;var dt=clamp((t-state.lastTs)/1000,0,.033);state.lastTs=t;update(dt,t);draw();updateUi();if(state.running&&!state.paused)state.raf=root.requestAnimationFrame(frame);}state.raf=root.requestAnimationFrame(frame);}
function cancelLoop(){if(state.raf){root.cancelAnimationFrame(state.raf);state.raf=0;}state.lastTs=0;}
function saveHigh(){if(state.score<=state.high)return;state.high=state.score;try{root.localStorage.setItem('ourfamily_arkanoid_high',String(state.high));}catch(e){}}
function updateUi(){var map={arkLevel:state.level,arkScore:state.score,arkLives:state.lives,arkHigh:state.high};Object.keys(map).forEach(function(id){var el=doc&&doc.getElementById(id);if(el)el.textContent=String(map[id]);});var p=doc&&doc.getElementById('arkPauseBtn');if(p)p.textContent=state.paused?'▶ Продолжить':'⏸ Пауза';var s=doc&&doc.getElementById('arkStartBtn');if(s)s.textContent=!state.started?'▶ Старт':(state.balls.some(function(b){return b.stuck;})?'● Запустить шар':'↻ Новая игра');var st=doc&&doc.getElementById('arkTopStatus');if(st)st.textContent=state.paused?'Пауза':('Уровень '+state.level+' · '+state.score+' очков');}

function audio(){try{var C=root.AudioContext||root.webkitAudioContext;if(!C)return null;if(!audioCtx)audioCtx=new C();return audioCtx;}catch(e){return null;}}
function unlockAudio(){var c=audio();if(c&&c.state==='suspended')try{c.resume().catch(function(){});}catch(e){}}
function tone(c,t,f1,f2,d,g,type){var o=c.createOscillator(),a=c.createGain();o.type=type||'sine';o.frequency.setValueAtTime(f1,t);o.frequency.exponentialRampToValueAtTime(Math.max(40,f2),t+d);a.gain.setValueAtTime(g,t);a.gain.exponentialRampToValueAtTime(.001,t+d);o.connect(a);a.connect(c.destination);o.start(t);o.stop(t+d+.01);}
function sound(kind,strength){var p=clamp(Number(strength)||.5,.1,1),c=audio();if(c&&c.state==='suspended')try{c.resume().catch(function(){});}catch(e){}if(c&&c.state==='running'){try{var t=c.currentTime+.001;if(kind==='brick'){tone(c,t,1120+240*p,620,.045,.12+.08*p,'triangle');tone(c,t,2050,1100,.025,.035+.025*p,'sine');}else if(kind==='paddle'){tone(c,t,360,170,.07,.14+.08*p,'triangle');tone(c,t,780,330,.038,.05,'sine');}else if(kind==='wall'){tone(c,t,650,420,.035,.035+.025*p,'sine');}else if(kind==='bonus'){tone(c,t,520,900,.08,.11,'sine');tone(c,t+.055,820,1280,.09,.09,'sine');}else if(kind==='lose'){tone(c,t,230,90,.18,.15,'sawtooth');}else if(kind==='clear'){tone(c,t,520,660,.10,.09,'sine');tone(c,t+.08,660,880,.11,.09,'sine');tone(c,t+.16,880,1180,.14,.10,'sine');}return;}catch(e){}}
 try{if(root.AndroidBridge&&root.AndroidBridge.playPoolSound)root.AndroidBridge.playPoolSound(kind==='paddle'?'cue':'ball',Math.round(p*100));}catch(e){}
}

function setFullscreen(on){state.fullscreen=!!on;if(doc)doc.body.classList.toggle('arkanoid-fullscreen-mode',state.fullscreen);var b=doc&&doc.getElementById('arkFullscreenBtn'),b2=doc&&doc.getElementById('arkFullBtn');if(b)b.textContent=state.fullscreen?'✕':'⛶';if(b2)b2.textContent=state.fullscreen?'✕ Выйти из полного экрана':'⛶ Полный экран';var nativeDone=false;try{if(root.AndroidBridge&&root.AndroidBridge.setPoolGameFullscreen){root.AndroidBridge.setPoolGameFullscreen(state.fullscreen);nativeDone=true;}if(root.AndroidBridge&&root.AndroidBridge.setPoolGameActive)root.AndroidBridge.setPoolGameActive(state.fullscreen||!(doc.getElementById('arkanoid').classList.contains('hidden')));if(state.fullscreen&&root.AndroidBridge&&root.AndroidBridge.requestPoolLandscape)root.AndroidBridge.requestPoolLandscape();}catch(e){}
 if(!nativeDone){try{if(state.fullscreen){var el=doc.documentElement;if(el.requestFullscreen)el.requestFullscreen().catch(function(){});}else if(doc.fullscreenElement&&doc.exitFullscreen)doc.exitFullscreen().catch(function(){});}catch(e){}}
 setTimeout(draw,220);
}
function openGame(){if(!ensureUi())return;try{if(typeof root.showScreen==='function')root.showScreen('arkanoid');else{doc.querySelectorAll('.screen').forEach(function(x){x.classList.add('hidden');});doc.getElementById('arkanoid').classList.remove('hidden');}}catch(e){}try{if(root.AndroidBridge&&root.AndroidBridge.setPoolGameActive)root.AndroidBridge.setPoolGameActive(true);}catch(e){}if(!state.started)newGame();else{state.paused=false;state.running=true;state.lastTs=0;loop();updateUi();draw();}}
function closeGame(){stopJoystick();state.paused=true;state.running=false;cancelLoop();setFullscreen(false);try{if(root.AndroidBridge&&root.AndroidBridge.setPoolGameActive)root.AndroidBridge.setPoolGameActive(false);}catch(e){}try{if(typeof root.showScreen==='function')root.showScreen('gamesHub');else{doc.getElementById('arkanoid').classList.add('hidden');doc.getElementById('gamesHub').classList.remove('hidden');}}catch(e){}}

root.ArkanoidGame={speedForLevel:speedForLevel,brickRowsForLevel:brickRowsForLevel,buildLevel:buildLevel,open:openGame,newGame:newGame};
root.__ourFamilyArkanoidJoystickInline=true;
if(doc){if(doc.readyState==='loading')doc.addEventListener('DOMContentLoaded',function(){ensureUi();});else ensureUi();['pointerdown','touchstart','keydown'].forEach(function(n){doc.addEventListener(n,unlockAudio,true);});}

})(typeof window!=='undefined'?window:globalThis);
