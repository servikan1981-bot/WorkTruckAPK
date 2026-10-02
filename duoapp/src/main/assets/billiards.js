(function(root){
'use strict';
var W=1000,H=500,R=15,LEFT=46,RIGHT=954,TOP=46,BOTTOM=454;
var POCKETS=[[46,46],[500,38],[954,46],[46,454],[500,462],[954,454]];
var BALL_COLORS=['#f7f7f7','#f5d328','#2358c7','#dc3131','#6f3da8','#ef7b1a','#16864b','#7b231f','#111318','#f5d328','#2358c7','#dc3131','#6f3da8','#ef7b1a','#16864b','#7b231f'];
var poolFxCtx=null;
function fxContext(){
 try{
  var C=root.AudioContext||root.webkitAudioContext;
  if(!C)return null;
  if(!poolFxCtx)poolFxCtx=new C();
  return poolFxCtx;
 }catch(e){return null;}
}
function fxNative(kind,strength){
 try{
  if(root.AndroidBridge&&root.AndroidBridge.playPoolSound){
   root.AndroidBridge.playPoolSound(kind,Math.round(Math.max(.1,Math.min(1,Number(strength)||.5))*100));
   return true;
  }
 }catch(e){}
 return false;
}
function fxSchedule(c,kind,strength){
 try{
  var p=Math.max(.1,Math.min(1,Number(strength)||.5)),t=c.currentTime+.002;
  var dur=kind==='cue'?.105:(kind==='rail'?.065:.052);
  var len=Math.max(128,Math.floor(c.sampleRate*dur)),buf=c.createBuffer(1,len,c.sampleRate),data=buf.getChannelData(0);
  for(var i=0;i<len;i++){
   var x=i/len,env=Math.pow(1-x,kind==='cue'?2.0:3.1);
   data[i]=(Math.random()*2-1)*env;
  }
  var src=c.createBufferSource(),filter=c.createBiquadFilter(),ng=c.createGain();
  filter.type='bandpass';filter.frequency.value=kind==='cue'?620:(kind==='rail'?920:2300);filter.Q.value=kind==='cue'?.75:1.15;
  ng.gain.setValueAtTime((kind==='cue'?.30:(kind==='rail'?.22:.24))*(.45+.55*p),t);
  ng.gain.exponentialRampToValueAtTime(.001,t+dur);
  src.buffer=buf;src.connect(filter);filter.connect(ng);ng.connect(c.destination);src.start(t);src.stop(t+dur+.01);
  var osc=c.createOscillator(),og=c.createGain();
  osc.type=kind==='cue'?'triangle':'sine';
  var f0=kind==='cue'?290:(kind==='rail'?680:(1850+700*p)),f1=kind==='cue'?105:(kind==='rail'?310:820);
  osc.frequency.setValueAtTime(f0,t);osc.frequency.exponentialRampToValueAtTime(f1,t+dur*.9);
  og.gain.setValueAtTime((kind==='cue'?.25:(kind==='rail'?.12:.16))*(.40+.60*p),t);
  og.gain.exponentialRampToValueAtTime(.001,t+dur);
  osc.connect(og);og.connect(c.destination);osc.start(t);osc.stop(t+dur+.01);
  return true;
 }catch(e){return false;}
}
function fxPlay(kind,strength){
 var c=fxContext();if(!c)return fxNative(kind,strength);
 try{
  if(c.state==='suspended'){
   c.resume().then(function(){if(!fxSchedule(c,kind,strength))fxNative(kind,strength);}).catch(function(){fxNative(kind,strength);});
   return true;
  }
  if(fxSchedule(c,kind,strength))return true;
 }catch(e){}
 return fxNative(kind,strength);
}
function fxUnlock(){
 var c=fxContext();if(!c)return;
 try{if(c.state==='suspended')c.resume().catch(function(){});}catch(e){}
}
try{
 if(root.document&&root.document.addEventListener){
  ['pointerdown','touchstart','mousedown','keydown'].forEach(function(n){root.document.addEventListener(n,fxUnlock,true);});
 }
}catch(e){}
function clone(x){return JSON.parse(JSON.stringify(x));}
function rack(){
 var balls=[{n:0,x:255,y:250,pocketed:false}];
 var nums=[1,10,2,11,8,3,12,4,13,5,14,6,15,7,9],idx=0,dx=Math.sqrt(3)*R*1.04;
 for(var row=0;row<5;row++){
  var x=690+row*dx,y0=250-row*R*1.04;
  for(var k=0;k<=row;k++)balls.push({n:nums[idx++],x:x,y:y0+k*2*R*1.04,pocketed:false});
 }
 return balls;
}
function initial(){return {seq:0,turn:0,winner:null,groups:[null,null],balls:rack(),breakShot:true,ballInHand:false,last:null};}
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
 var s=clone(state),balls=s.balls.map(function(b){return {n:b.n,x:b.x,y:b.y,pocketed:!!b.pocketed,vx:0,vy:0};});
 var cue=balls.find(function(b){return b.n===0;});if(!cue||cue.pocketed)return {balls:balls,pocketed:[],firstContact:null,frames:[]};
 power=Math.max(0.08,Math.min(1,Number(power)||0.5));angle=Number(angle)||0;var speed=14+power*28;cue.vx=Math.cos(angle)*speed;cue.vy=Math.sin(angle)*speed;
 var frames=[],pocketed=[],firstContact=null,lastFrame=-9,pendingSound=0,pendingSoundKind='ball';
 function queueImpact(kind,strength){var v=Math.max(0,Math.min(1,Number(strength)||0));if(v>pendingSound){pendingSound=v;pendingSoundKind=kind;}}
 function snap(){
  if(!keepFrames)return;
  var fr=balls.map(function(b){return {n:b.n,x:+b.x.toFixed(2),y:+b.y.toFixed(2),pocketed:b.pocketed};});
  if(pendingSound>0){
   var snd=Math.min(1,pendingSound),kind=pendingSoundKind,fired=false,fallback=0;
   try{
    Object.defineProperty(fr,'_sound',{configurable:true,enumerable:false,get:function(){if(!fired){fired=true;fallback=fxPlay(kind,snd)?0:snd;}return fallback;}});
   }catch(e){fr._sound=snd;}
   pendingSound=0;pendingSoundKind='ball';
  }
  frames.push(fr);
 }
 snap();
 for(var step=0;step<3200;step++){
  var moving=false;
  for(var i=0;i<balls.length;i++){
   var b=balls[i];if(b.pocketed)continue;
   if(Math.hypot(b.vx,b.vy)>.0045)moving=true;
   b.x+=b.vx*.34;b.y+=b.vy*.34;
   for(var p=0;p<POCKETS.length;p++){
    var pk=POCKETS[p],pr=(p===1||p===4)?24:27;
    if(Math.hypot(b.x-pk[0],b.y-pk[1])<pr){b.pocketed=true;b.vx=b.vy=0;pocketed.push(b.n);break;}
   }
   if(b.pocketed)continue;
   if(b.x-R<LEFT){var sx=Math.abs(b.vx);b.x=LEFT+R;b.vx=sx*.94;if(sx>1.2)queueImpact('rail',Math.min(.82,sx/24));}else if(b.x+R>RIGHT){var sx2=Math.abs(b.vx);b.x=RIGHT-R;b.vx=-sx2*.94;if(sx2>1.2)queueImpact('rail',Math.min(.82,sx2/24));}
   if(b.y-R<TOP){var sy=Math.abs(b.vy);b.y=TOP+R;b.vy=sy*.94;if(sy>1.2)queueImpact('rail',Math.min(.82,sy/24));}else if(b.y+R>BOTTOM){var sy2=Math.abs(b.vy);b.y=BOTTOM-R;b.vy=-sy2*.94;if(sy2>1.2)queueImpact('rail',Math.min(.82,sy2/24));}
  }
  for(var a=0;a<balls.length;a++)for(var j=a+1;j<balls.length;j++){
   var A=balls[a],B=balls[j];if(A.pocketed||B.pocketed)continue;var dx=B.x-A.x,dy=B.y-A.y,d2=dx*dx+dy*dy,min=R*2;if(d2<=0||d2>=min*min)continue;
   var d=Math.sqrt(d2),nx=dx/d,ny=dy/d,over=min-d;A.x-=nx*over/2;A.y-=ny*over/2;B.x+=nx*over/2;B.y+=ny*over/2;
   var rvx=B.vx-A.vx,rvy=B.vy-A.vy,sep=rvx*nx+rvy*ny;if(sep<0){var hitStrength=Math.min(1,Math.abs(sep)/22);if(hitStrength>.055)queueImpact('ball',hitStrength);var imp=-(1.92)*sep/2;A.vx-=imp*nx;A.vy-=imp*ny;B.vx+=imp*nx;B.vy+=imp*ny;if(firstContact===null&&(A.n===0||B.n===0))firstContact=A.n===0?B.n:A.n;}
  }
  for(var q=0;q<balls.length;q++){
   var bb=balls[q];if(bb.pocketed)continue;
   bb.vx*=.99735;bb.vy*=.99735;
   if(Math.hypot(bb.vx,bb.vy)<.0045){bb.vx=0;bb.vy=0;}
  }
  if(keepFrames&&step-lastFrame>=8){snap();lastFrame=step;}
  if(!moving&&step>8)break;
 }
 if(keepFrames)snap();
 return {balls:balls,pocketed:pocketed,firstContact:firstContact,frames:frames};
}
function shot(state,player,angle,power){
 if(!state||state.winner!==null||state.turn!==player)return null;
 fxPlay('cue',power);
 var s=clone(state),sim=simulate(s,angle,power,true),beforeGroups=s.groups.slice(),potted=sim.pocketed.slice(),scratch=potted.indexOf(0)>=0;
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
function resign(state,player){if(!state||state.winner!==null)return null;var s=clone(state);s.winner=1-player;s.seq=(Number(s.seq)||0)+1;s.last={kind:'resign',player:player};return s;}
root.PoolRules={W:W,H:H,R:R,POCKETS:POCKETS,BALL_COLORS:BALL_COLORS,initial:initial,shot:shot,simulate:simulate,resign:resign,groupOf:groupOf,remaining:remaining,clone:clone};
})(typeof window!=='undefined'?window:globalThis);
