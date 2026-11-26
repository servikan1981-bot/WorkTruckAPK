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
