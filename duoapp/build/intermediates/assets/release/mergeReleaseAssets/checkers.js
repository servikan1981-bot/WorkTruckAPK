(function(root){
 'use strict';
 var dirs=[[-1,-1],[-1,1],[1,-1],[1,1]];
 function initial(){
  var b=Array(64).fill(null);
  for(var r=0;r<8;r++)for(var c=0;c<8;c++)if((r+c)%2===0){
   if(r<3)b[r*8+c]='w';else if(r>4)b[r*8+c]='b';
  }
  return b;
 }
 function sideOf(p){return p&&p.toLowerCase();}
 function inside(r,c){return r>=0&&r<8&&c>=0&&c<8;}
 function captures(b,side,from){
  var p=b[from];if(sideOf(p)!==side)return [];
  var r=Math.floor(from/8),c=from%8,out=[];
  for(var d of dirs){
   var rr=r+d[0],cc=c+d[1],taken=-1;
   while(inside(rr,cc)){
    var idx=rr*8+cc,q=b[idx];
    if(q){
     if(sideOf(q)===side||taken>=0)break;
     taken=idx;
    }else if(taken>=0){out.push({from:from,to:idx,capture:taken});if(p===p.toLowerCase())break;}
    else if(p===p.toLowerCase())break;
    rr+=d[0];cc+=d[1];
   }
  }
  return out;
 }
 function moves(b,side,forced){
  if(!Array.isArray(b)||b.length!==64||!['w','b'].includes(side))return [];
  var capture=[];
  for(var i=0;i<64;i++)if(sideOf(b[i])===side&&(forced==null||i===forced))capture.push.apply(capture,captures(b,side,i));
  if(capture.length||forced!=null)return capture;
  var out=[];
  for(var from=0;from<64;from++)if(sideOf(b[from])===side){
   var p=b[from],r=Math.floor(from/8),c=from%8;
   for(var d of dirs){
    if(p===p.toLowerCase()&&d[0]!== (side==='w'?1:-1))continue;
    var rr=r+d[0],cc=c+d[1];
    while(inside(rr,cc)&&!b[rr*8+cc]){
     out.push({from:from,to:rr*8+cc,capture:-1});
     if(p===p.toLowerCase())break;
     rr+=d[0];cc+=d[1];
    }
   }
  }
  return out;
 }
 function play(state,from,to){
  if(!state||state.winner||!Number.isInteger(from)||!Number.isInteger(to))return null;
  var legal=moves(state.board,state.turn,state.forced);
  var m=legal.find(function(x){return x.from===from&&x.to===to;});if(!m)return null;
  var b=state.board.slice(),p=b[from];b[from]=null;b[to]=p;
  if(m.capture>=0)b[m.capture]=null;
  if((p==='w'&&Math.floor(to/8)===7)||(p==='b'&&Math.floor(to/8)===0))b[to]=p.toUpperCase();
  var next=state.turn,forced=null;
  if(m.capture>=0&&captures(b,next,to).length)forced=to;
  else next=next==='w'?'b':'w';
  var winner=null;
  if(!moves(b,next,forced).length)winner=state.turn;
  return {board:b,turn:next,forced:forced,winner:winner,seq:(state.seq||0)+1,last:{from:from,to:to,capture:m.capture}};
 }
 var api={initial:initial,moves:moves,play:play};
 if(typeof module!=='undefined'&&module.exports)module.exports=api;
 root.CheckersRules=api;
})(typeof window!=='undefined'?window:globalThis);
