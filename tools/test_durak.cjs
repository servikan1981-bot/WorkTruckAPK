const assert=require('assert');
const D=require('../duoapp/src/main/assets/durak.js');
assert.equal(D.deck().length,36);
assert.equal(new Set(D.deck()).size,36);
assert(D.canBeat('7h','6h','s'));
assert(!D.canBeat('6h','7h','s'));
assert(D.canBeat('6s','Ah','s'));
assert(D.canBeat('As','6s','s'));
assert(!D.canBeat('6s','As','s'));
let s=D.initial(12345,0);
assert(D.valid(s));
assert.equal(s.hands[0].length,6);
assert.equal(s.hands[1].length,6);
assert.equal(s.stock.length,24);
assert([0,1].includes(s.attacker));
let a=s.attacker,d=s.defender;
let attacked=null;
for(const c of s.hands[a]){const n=D.playAttack(s,a,c);if(n){attacked=n;break;}}
assert(attacked&&attacked.table.length===1&&attacked.phase==='defend');
let defended=null;
for(const c of attacked.hands[d]){const n=D.playDefense(attacked,d,c);if(n){defended=n;break;}}
if(defended){
 assert(D.allCovered(defended.table));
 const beaten=D.beat(defended,a);
 assert(beaten&&beaten.table.length===0&&beaten.attacker===d);
}else{
 const taken=D.take(attacked,d);
 assert(taken&&taken.table.length===0&&taken.attacker===a);
}
let r=D.resign(s,0);assert(r&&r.winner===1&&r.resigned===0);
console.log('PASS: 36-card two-player Durak rules');
