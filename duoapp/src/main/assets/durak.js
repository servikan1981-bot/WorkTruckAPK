(function(root){
 'use strict';
 var SUITS=['c','d','h','s'];
 var RANKS=['6','7','8','9','10','J','Q','K','A'];
 function value(card){return RANKS.indexOf(card.slice(0,-1));}
 function suit(card){return card.slice(-1);}
 function rank(card){return card.slice(0,-1);}
 function deck(){var out=[];for(var s of SUITS)for(var r of RANKS)out.push(r+s);return out;}
 function shuffle(cards,seed){
  var out=cards.slice(),x=(Number(seed)||Date.now())>>>0;
  function rnd(){x=(x*1664525+1013904223)>>>0;return x/4294967296;}
  for(var i=out.length-1;i>0;i--){var j=Math.floor(rnd()*(i+1)),t=out[i];out[i]=out[j];out[j]=t;}
  return out;
 }
 function canBeat(def,atk,trump){
  if(!def||!atk)return false;
  var ds=suit(def),as=suit(atk);
  if(ds===as)return value(def)>value(atk);
  return ds===trump&&as!==trump;
 }
 function tableRanks(table){var set={};for(var p of table||[]){if(p.a)set[rank(p.a)]=1;if(p.d)set[rank(p.d)]=1;}return set;}
 function allCovered(table){return !!(table&&table.length)&&table.every(function(p){return !!p.d;});}
 function firstAttacker(hands,trump,preferred){
  var best=null;
  [0,1].forEach(function(side){(hands[side]||[]).forEach(function(c){if(suit(c)!==trump)return;var v=value(c);if(best===null||v<best.v)best={side:side,v:v};});});
  return best?best.side:(preferred===1?1:0);
 }
 function drawToSix(state,side){while(state.hands[side].length<6&&state.stock.length)state.hands[side].push(state.stock.shift());}
 function outcome(state){
  if(state.stock.length)return null;
  var a=state.hands[0].length===0,b=state.hands[1].length===0;
  if(a&&b)return {winner:null,draw:true};
  if(a)return {winner:0,draw:false};
  if(b)return {winner:1,draw:false};
  return null;
 }
 function initial(seed,preferred){
  var cards=shuffle(deck(),seed),trump=suit(cards[cards.length-1]);
  var hands=[[],[]];for(var i=0;i<6;i++){hands[0].push(cards.shift());hands[1].push(cards.shift());}
  var attacker=firstAttacker(hands,trump,preferred);
  return {type:'durak',hands:hands,stock:cards,trump:trump,attacker:attacker,defender:1-attacker,table:[],discard:[],phase:'attack',attackLimit:6,seq:0,winner:null,draw:false,last:null};
 }
 function clone(s){return JSON.parse(JSON.stringify(s));}
 function playAttack(state,side,card){
  if(!state||state.winner!==null||state.draw||side!==state.attacker||state.phase!=='attack')return null;
  var idx=state.hands[side].indexOf(card);if(idx<0)return null;
  if(state.table.length>=state.attackLimit)return null;
  if(state.table.length){var ranks=tableRanks(state.table);if(!ranks[rank(card)])return null;}
  var n=clone(state);n.hands[side].splice(idx,1);n.table.push({a:card,d:null});n.phase='defend';n.seq++;n.last={kind:'attack',side:side,card:card};return n;
 }
 function playDefense(state,side,card){
  if(!state||state.winner!==null||state.draw||side!==state.defender||state.phase!=='defend')return null;
  var pair=state.table.find(function(p){return !p.d;});if(!pair||!canBeat(card,pair.a,state.trump))return null;
  var idx=state.hands[side].indexOf(card);if(idx<0)return null;
  var n=clone(state);n.hands[side].splice(idx,1);var target=n.table.find(function(p){return !p.d;});target.d=card;
  n.phase=allCovered(n.table)?'attack':'defend';n.seq++;n.last={kind:'defend',side:side,card:card};return n;
 }
 function beat(state,side){
  if(!state||state.winner!==null||state.draw||side!==state.attacker||!allCovered(state.table))return null;
  var n=clone(state),oldA=n.attacker,oldD=n.defender;
  for(var p of n.table){n.discard.push(p.a);if(p.d)n.discard.push(p.d);}n.table=[];
  drawToSix(n,oldA);drawToSix(n,oldD);
  n.attacker=oldD;n.defender=oldA;n.phase='attack';n.attackLimit=Math.min(6,n.hands[n.defender].length||6);n.seq++;n.last={kind:'beat',side:side};
  var o=outcome(n);if(o){n.winner=o.winner;n.draw=o.draw;}return n;
 }
 function take(state,side){
  if(!state||state.winner!==null||state.draw||side!==state.defender||!state.table.length)return null;
  var n=clone(state),a=n.attacker,d=n.defender;
  for(var p of n.table){n.hands[d].push(p.a);if(p.d)n.hands[d].push(p.d);}n.table=[];
  drawToSix(n,a);drawToSix(n,d);n.phase='attack';n.attackLimit=Math.min(6,n.hands[d].length||6);n.seq++;n.last={kind:'take',side:side};
  var o=outcome(n);if(o){n.winner=o.winner;n.draw=o.draw;}return n;
 }
 function resign(state,side){if(!state||state.winner!==null||state.draw||![0,1].includes(side))return null;var n=clone(state);n.winner=1-side;n.resigned=side;n.seq++;n.last={kind:'resign',side:side};return n;}
 function valid(state){return !!state&&state.type==='durak'&&Array.isArray(state.hands)&&state.hands.length===2&&Array.isArray(state.stock)&&Array.isArray(state.table)&&SUITS.includes(state.trump);}
 var api={SUITS:SUITS,RANKS:RANKS,deck:deck,rank:rank,suit:suit,value:value,canBeat:canBeat,initial:initial,playAttack:playAttack,playDefense:playDefense,beat:beat,take:take,resign:resign,allCovered:allCovered,valid:valid};
 if(typeof module!=='undefined'&&module.exports)module.exports=api;
 root.DurakRules=api;
})(typeof window!=='undefined'?window:globalThis);
