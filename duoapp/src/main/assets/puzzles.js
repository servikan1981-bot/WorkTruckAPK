(function(root){
'use strict';
var doc=root.document, canvas=null, ctx=null, size=4, board=[], empty=15, moves=0, started=false, solved=false, seconds=0, timer=0, lastTap=0;

function $(id){return doc.getElementById(id);}
function ensureStyle(){
 if($('puzzleStyle'))return;
 var s=doc.createElement('style');s.id='puzzleStyle';s.textContent='.puzzle-card{grid-column:1/-1!important;min-height:112px!important;background:linear-gradient(145deg,#1d73d8,#18a9b1)!important;color:#fff!important;border:1px solid rgba(255,255,255,.65)!important}.puzzle-card .emoji{font-size:34px!important}.puzzle-card span:last-child{color:rgba(255,255,255,.9)!important}#puzzle{display:flex;flex-direction:column;background:linear-gradient(160deg,#e7f5ff,#f9fcff 55%,#e6f7f5);color:#15324f}#puzzle .topbar{background:#fff;border-bottom:1px solid #d7e8f7}.puzzle-body{flex:1;min-height:0;overflow:auto;padding:12px 12px 28px;display:flex;flex-direction:column;align-items:center;gap:10px}.puzzle-head{width:min(520px,100%);display:grid;grid-template-columns:1fr 1fr 1fr;gap:7px}.puzzle-stat{background:#fff;border:1px solid #d7e8f7;border-radius:15px;padding:8px 6px;text-align:center;box-shadow:0 4px 12px rgba(32,100,164,.06)}.puzzle-stat small{display:block;color:#6b849d;font-size:10px;font-weight:800}.puzzle-stat strong{display:block;color:#1768d6;font-size:18px;margin-top:2px}.puzzle-board-shell{width:min(92vw,520px);aspect-ratio:1;padding:8px;border-radius:25px;background:linear-gradient(145deg,#b8ddff,#5a9fe8 55%,#1a7896);box-shadow:0 16px 32px rgba(28,100,159,.22)}#puzzleCanvas{display:block;width:100%;height:100%;border-radius:18px;background:#dcefff;touch-action:none;user-select:none;-webkit-user-select:none}.puzzle-controls{width:min(520px,100%);display:grid;grid-template-columns:1fr 1fr;gap:8px}.puzzle-controls button{border:0;border-radius:15px;padding:12px;font-weight:900}.puzzle-new{background:linear-gradient(145deg,#398aff,#1768d6);color:#fff}.puzzle-mode{background:#fff;color:#1768d6;border:1px solid #cfe3f5!important}.puzzle-mode.active{background:#e5f2ff;box-shadow:inset 0 0 0 2px rgba(45,127,249,.16)}.puzzle-difficulty{width:min(520px,100%);display:flex;gap:7px}.puzzle-difficulty button{flex:1;border:1px solid #d2e5f4;background:#fff;color:#52708d;border-radius:13px;padding:9px 5px;font-weight:850}.puzzle-difficulty button.active{background:#dff1ff;color:#1768d6;border-color:#83b9f2}.puzzle-hint{width:min(520px,100%);text-align:center;color:#66839d;font-size:12px;line-height:1.35}.puzzle-win{position:fixed;inset:0;z-index:260;background:rgba(8,29,54,.72);display:grid;place-items:center;padding:22px}.puzzle-win-card{width:min(390px,100%);background:#fff;border-radius:26px;padding:24px;text-align:center;box-shadow:0 24px 60px rgba(0,0,0,.28)}.puzzle-win-emoji{font-size:52px}.puzzle-win-title{font-size:24px;font-weight:950;color:#15324f;margin:6px 0}.puzzle-win-text{color:#66839d;line-height:1.45;margin-bottom:15px}.puzzle-win button{width:100%;border:0;border-radius:15px;padding:13px;background:linear-gradient(145deg,#398aff,#1768d6);color:#fff;font-weight:900}.puzzle-preview{width:min(520px,100%);font-size:12px;color:#6b849d;text-align:center}.puzzle-preview b{color:#1768d6}@media(max-height:700px){.puzzle-body{padding-top:7px;gap:7px}.puzzle-board-shell{width:min(72vh,480px);padding:6px}.puzzle-stat{padding:5px}.puzzle-stat strong{font-size:15px}.puzzle-controls button{padding:9px}.puzzle-difficulty button{padding:7px}}';doc.head.appendChild(s);
}
function ensureUi(){
 ensureStyle();
 var grid=doc.querySelector('#gamesHub .game-grid');
 if(grid&&!$('hubPuzzleBtn')){
  var b=doc.createElement('button');b.id='hubPuzzleBtn';b.className='game-card puzzle-card';
  b.innerHTML='<span class="emoji">🧩</span><strong>Пазлы</strong><span>15-пазл · 3×3 или 4×4 · одиночная игра</span>';
  grid.appendChild(b);b.addEventListener('click',open);
 }
 if(!$('puzzle')){
  var sec=doc.createElement('section');sec.id='puzzle';sec.className='screen hidden';
  sec.innerHTML='<div class="topbar"><button id="puzzleBackBtn" class="iconbtn">‹</button><div class="who"><div class="name">Пазлы</div><div id="puzzleStatus" class="status">Соберите поле по порядку</div></div></div>'+
   '<div class="puzzle-body"><div class="puzzle-head"><div class="puzzle-stat"><small>Ходы</small><strong id="puzzleMoves">0</strong></div><div class="puzzle-stat"><small>Время</small><strong id="puzzleTime">0:00</strong></div><div class="puzzle-stat"><small>Рекорд</small><strong id="puzzleBest">—</strong></div></div>'+
   '<div class="puzzle-difficulty"><button data-puzzle-size="3">3 × 3</button><button data-puzzle-size="4" class="active">4 × 4</button></div>'+
   '<div class="puzzle-board-shell"><canvas id="puzzleCanvas" width="720" height="720" aria-label="Пазл"></canvas></div>'+
   '<div class="puzzle-controls"><button id="puzzleNewBtn" class="puzzle-new">🔄 Новый пазл</button><button id="puzzleModeBtn" class="puzzle-mode">🔢 Цифры</button></div>'+
   '<div class="puzzle-hint">Нажимайте на соседнюю плитку — она сдвинется в пустое место. Можно играть одной рукой.</div>'+
   '<div class="puzzle-preview">Цель: собрать <b>1 → 15</b> и оставить пустое место справа внизу.</div></div>';
  var nav=$('bottomNav');if(nav&&nav.parentNode)nav.parentNode.insertBefore(sec,nav);else doc.body.appendChild(sec);
  var win=doc.createElement('div');win.id='puzzleWin';win.className='puzzle-win hidden';win.innerHTML='<div class="puzzle-win-card"><div class="puzzle-win-emoji">🎉</div><div class="puzzle-win-title">Пазл собран!</div><div id="puzzleWinText" class="puzzle-win-text"></div><button id="puzzleAgainBtn">Собрать ещё один</button></div>';doc.body.appendChild(win);
  bind();
 }
}
function solvedBoard(){var a=[];for(var i=1;i<size*size;i++)a.push(i);a.push(0);return a;}
function shuffleBoard(){
 board=solvedBoard();empty=board.length-1;
 var prev=-1,total=size===3?80:170;
 for(var n=0;n<total;n++){
  var opts=neighbors(empty).filter(function(x){return x!==prev;});
  var next=opts[Math.floor(Math.random()*opts.length)];
  board[empty]=board[next];board[next]=0;prev=empty;empty=next;
 }
 if(isSolved())shuffleBoard();
}
function neighbors(idx){
 var r=Math.floor(idx/size),c=idx%size,out=[];
 if(r>0)out.push(idx-size);if(r<size-1)out.push(idx+size);if(c>0)out.push(idx-1);if(c<size-1)out.push(idx+1);
 return out;
}
function isSolved(){for(var i=0;i<board.length-1;i++)if(board[i]!==i+1)return false;return board[board.length-1]===0;}
function fmtTime(v){var m=Math.floor(v/60),s=v%60;return m+':'+String(s).padStart(2,'0');}
function bestKey(){return 'ourfamily_puzzle_best_'+size;}
function getBest(){try{var v=parseInt(root.localStorage.getItem(bestKey())||'',10);return Number.isFinite(v)&&v>0?v:0;}catch(e){return 0;}}
function saveBest(){var b=getBest();if(!b||seconds<b)try{root.localStorage.setItem(bestKey(),String(seconds));return true;}catch(e){}return false;}
function updateUi(){
 if($('puzzleMoves'))$('puzzleMoves').textContent=String(moves);
 if($('puzzleTime'))$('puzzleTime').textContent=fmtTime(seconds);
 var b=getBest();if($('puzzleBest'))$('puzzleBest').textContent=b?fmtTime(b):'—';
 if($('puzzleStatus'))$('puzzleStatus').textContent=solved?'Готово!':'Соберите поле по порядку';
}
function startTimer(){clearInterval(timer);timer=setInterval(function(){if(started&&!solved){seconds++;updateUi();}},1000);}
function newGame(){
 hideWin();moves=0;seconds=0;started=true;solved=false;shuffleBoard();updateUi();draw();startTimer();
}
function showOnly(id){
 doc.querySelectorAll('.screen').forEach(function(x){x.classList.toggle('hidden',x.id!==id);});
 var nav=$('bottomNav');if(nav)nav.classList.add('hidden');
}
function open(){showOnly('puzzle');newGame();}
function back(){clearInterval(timer);hideWin();var b=$('puzzleBackBtn');if(b)b.blur();var games=$('gamesHub');if(games){doc.querySelectorAll('.screen').forEach(function(x){x.classList.toggle('hidden',x!==games);});}var nav=$('bottomNav');if(nav)nav.classList.remove('hidden');}
function hideWin(){var w=$('puzzleWin');if(w)w.classList.add('hidden');}
function showWin(){
 solved=true;started=false;clearInterval(timer);var best=saveBest();
 $('puzzleWinText').textContent='Вы собрали '+size+'×'+size+' за '+moves+' ходов и '+fmtTime(seconds)+(best?' — новый рекорд!':'');
 $('puzzleWin').classList.remove('hidden');updateUi();
}
function tileAtEvent(e){
 var r=canvas.getBoundingClientRect(),x=(e.clientX-r.left)/r.width*canvas.width,y=(e.clientY-r.top)/r.height*canvas.height;
 var gap=canvas.width*0.035,inner=canvas.width-gap*2,cell=inner/size,c=Math.floor((x-gap)/cell),row=Math.floor((y-gap)/cell);
 if(c<0||c>=size||row<0||row>=size)return -1;return row*size+c;
}
function move(idx){
 if(!started||solved||idx<0)return false;
 if(neighbors(empty).indexOf(idx)<0)return false;
 board[empty]=board[idx];board[idx]=0;empty=idx;moves++;draw();updateUi();
 if(isSolved())showWin();return true;
}
function draw(){
 if(!ctx)return;
 var w=canvas.width,gap=w*.035,inner=w-gap*2,cell=inner/size;
 var bg=ctx.createLinearGradient(0,0,w,w);bg.addColorStop(0,'#e8f7ff');bg.addColorStop(.52,'#bde7ff');bg.addColorStop(1,'#7bd5cf');
 ctx.fillStyle=bg;ctx.fillRect(0,0,w,w);
 for(var i=0;i<board.length;i++){
  var v=board[i];if(!v)continue;var r=Math.floor(i/size),c=i%size,x=gap+c*cell+4,y=gap+r*cell+4,s=cell-8;
  var t=(v-1)/Math.max(1,size*size-2),g=ctx.createLinearGradient(x,y,x+s,y+s);g.addColorStop(0,'#3d9bff');g.addColorStop(1,t>.55?'#1768d6':'#168fa8');
  ctx.fillStyle=g;roundRect(ctx,x,y,s,s,Math.min(22,s*.16));ctx.fill();
  ctx.fillStyle='rgba(255,255,255,.18)';roundRect(ctx,x+8,y+7,s-16,Math.max(4,s*.08),Math.max(2,s*.04));ctx.fill();
  ctx.fillStyle='#fff';ctx.font='900 '+Math.max(24,Math.round(s*.28))+'px system-ui';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(String(v),x+s/2,y+s/2+2);
 }
}
function roundRect(x,y,w,h,r){r=Math.min(r,w/2,h/2);ctx.beginPath();ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r);ctx.closePath();}
function bind(){
 canvas=$('puzzleCanvas');ctx=canvas.getContext('2d');
 $('puzzleBackBtn').addEventListener('click',back);$('puzzleNewBtn').addEventListener('click',newGame);
 $('puzzleAgainBtn').addEventListener('click',newGame);
 $('puzzleModeBtn').addEventListener('click',function(){this.textContent=this.textContent.includes('Цифры')?'🌊 Океан':'🔢 Цифры';});
 doc.querySelectorAll('[data-puzzle-size]').forEach(function(b){b.addEventListener('click',function(){size=parseInt(this.dataset.puzzleSize,10);doc.querySelectorAll('[data-puzzle-size]').forEach(function(x){x.classList.toggle('active',x===b);});newGame();});});
 canvas.addEventListener('pointerdown',function(e){e.preventDefault();if(Date.now()-lastTap<80)return;lastTap=Date.now();try{canvas.setPointerCapture(e.pointerId);}catch(_e){}move(tileAtEvent(e));},{passive:false});
 canvas.addEventListener('pointerup',function(e){try{canvas.releasePointerCapture(e.pointerId);}catch(_e){}});
 root.addEventListener('keydown',function(e){if($('puzzle').classList.contains('hidden'))return;var d={ArrowUp:size,ArrowDown:-size,ArrowLeft:1,ArrowRight:-1}[e.key];if(d===undefined)return;var target=empty+d;if(target>=0&&target<board.length&&neighbors(empty).indexOf(target)>=0){e.preventDefault();move(target);}});
 draw();updateUi();
}
ensureUi();
})(window);