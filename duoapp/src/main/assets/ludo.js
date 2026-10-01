(function(g){'use strict';
var SAFE=[0,8,13,21,26,34,39,47];
function clone(x){return JSON.parse(JSON.stringify(x));}
function initial(players){var n=Math.max(2,Math.min(4,players|0)),pieces=[];for(var i=0;i<n;i++)pieces.push([-1,-1,-1,-1]);return {players:n,pieces:pieces,turn:0,dice:null,seq:0,winner:null,last:null};}
function absPos(player,progress){if(progress<0||progress>51)return -1;return ((player*13)+progress)%52;}
function movable(s,p,d){var out=[];if(!s||s.winner!==null||s.turn!==p||!d)return out;for(var i=0;i<4;i++){var q=s.pieces[p][i];if(q===58)continue;if(q<0){if(d===6)out.push(i);}else if(q+d<=58)out.push(i);}return out;}
function roll(s,p,d){if(!s||s.winner!==null||s.turn!==p||s.dice!==null||d<1||d>6)return null;var n=clone(s);n.dice=d;n.seq++;n.last={kind:'roll',player:p,dice:d};if(!movable(n,p,d).length){n.dice=null;n.turn=(p+1)%n.players;}return n;}
function move(s,p,piece){if(!s||s.winner!==null||s.turn!==p||s.dice===null)return null;var d=s.dice,legal=movable(s,p,d);if(legal.indexOf(piece)<0)return null;var n=clone(s),from=n.pieces[p][piece],to=from<0?0:from+d;n.pieces[p][piece]=to;var captured=[];if(to>=0&&to<=51){var a=absPos(p,to);if(SAFE.indexOf(a)<0){for(var op=0;op<n.players;op++)if(op!==p){for(var j=0;j<4;j++){var oq=n.pieces[op][j];if(oq>=0&&oq<=51&&absPos(op,oq)===a){n.pieces[op][j]=-1;captured.push([op,j]);}}}}}var won=n.pieces[p].every(function(x){return x===58;});n.seq++;n.last={kind:'move',player:p,piece:piece,dice:d,from:from,to:to,captured:captured};n.dice=null;if(won)n.winner=p;else if(d!==6&&captured.length===0)n.turn=(p+1)%n.players;return n;}
function resign(s,p){if(!s||s.winner!==null)return null;var n=clone(s),alive=[];for(var i=0;i<n.players;i++)if(i!==p)alive.push(i);n.winner=alive.length===1?alive[0]:null;n.seq++;n.last={kind:'resign',player:p};return n;}
g.LudoRules={initial:initial,absPos:absPos,movable:movable,roll:roll,move:move,resign:resign,SAFE:SAFE};
})(window);
