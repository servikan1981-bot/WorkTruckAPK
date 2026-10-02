(function(root){
'use strict';
var W=1000,H=500,R=15,LEFT=46,RIGHT=954,TOP=46,BOTTOM=454;
var POCKETS=[[46,46],[500,38],[954,46],[46,454],[500,462],[954,454]];
var BALL_COLORS=['#f7f7f7','#f5d328','#2358c7','#dc3131','#6f3da8','#ef7b1a','#16864b','#7b231f','#111318','#f5d328','#2358c7','#dc3131','#6f3da8','#ef7b1a','#16864b','#7b231f'];
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
 var frames=[],pocketed=[],firstContact=null,lastFrame=-9,pendingSound=0;
 function snap(){if(!keepFrames)return;var fr=balls.map(function(b){return {n:b.n,x:+b.x.toFixed(2),y:+b.y.toFixed(2),pocketed:b.pocketed};});if(pendingSound>0){fr._sound=Math.min(1,pendingSound);pendingSound=0;}frames.push(fr);}
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
   if(b.x-R<LEFT){b.x=LEFT+R;b.vx=Math.abs(b.vx)*.94;}else if(b.x+R>RIGHT){b.x=RIGHT-R;b.vx=-Math.abs(b.vx)*.94;}
   if(b.y-R<TOP){b.y=TOP+R;b.vy=Math.abs(b.vy)*.94;}else if(b.y+R>BOTTOM){b.y=BOTTOM-R;b.vy=-Math.abs(b.vy)*.94;}
  }
  for(var a=0;a<balls.length;a++)for(var j=a+1;j<balls.length;j++){
   var A=balls[a],B=balls[j];if(A.pocketed||B.pocketed)continue;var dx=B.x-A.x,dy=B.y-A.y,d2=dx*dx+dy*dy,min=R*2;if(d2<=0||d2>=min*min)continue;
   var d=Math.sqrt(d2),nx=dx/d,ny=dy/d,over=min-d;A.x-=nx*over/2;A.y-=ny*over/2;B.x+=nx*over/2;B.y+=ny*over/2;
   var rvx=B.vx-A.vx,rvy=B.vy-A.vy,sep=rvx*nx+rvy*ny;if(sep<0){var hitStrength=Math.min(1,Math.abs(sep)/22);if(hitStrength>.055)pendingSound=Math.max(pendingSound,hitStrength);var imp=-(1.92)*sep/2;A.vx-=imp*nx;A.vy-=imp*ny;B.vx+=imp*nx;B.vy+=imp*ny;if(firstContact===null&&(A.n===0||B.n===0))firstContact=A.n===0?B.n:A.n;}
  }
  for(var q=0;q<balls.length;q++){var bb=balls[q];if(bb.pocketed)continue;bb.vx*=.993;bb.vy*=.993;if(Math.abs(bb.vx)<.010)bb.vx=0;if(Math.abs(bb.vy)<.010)bb.vy=0;}
  if(keepFrames&&step-lastFrame>=7){snap();lastFrame=step;}
  if(!moving&&step>8)break;
 }
 if(keepFrames)snap();
 return {balls:balls,pocketed:pocketed,firstContact:firstContact,frames:frames};
}
function shot(state,player,angle,power){
 if(!state||state.winner!==null||state.turn!==player)return null;
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
