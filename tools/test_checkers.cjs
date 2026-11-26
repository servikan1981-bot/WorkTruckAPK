const assert = require('node:assert/strict');
const rules = require('../duoapp/src/main/assets/checkers.js');
function state(board,turn='b',forced=null,seq=0){return {board,turn,forced,winner:null,seq};}
let b=rules.initial();
assert.equal(b.filter(Boolean).length,24);
assert.equal(rules.moves(b,'b',null).length,7);
assert.equal(rules.play(state(b),41,33),null);
let s=rules.play(state(b),41,32); // black moves toward the top
assert.equal(s.turn,'w');assert.equal(s.seq,1);
b=Array(64).fill(null);b[41]='b';b[34]='w';b[20]='w';b[6]='w';
assert.deepEqual(rules.moves(b,'b',null),[{from:41,to:27,capture:34}]);
s=rules.play(state(b),41,27);
assert.equal(s.turn,'b');assert.equal(s.forced,27);assert.equal(s.board[34],null);
assert.equal(rules.play(s,27,18),null); // must continue capture
s=rules.play(s,27,13);
assert.equal(s.turn,'w');assert.equal(s.board[20],null);
b=Array(64).fill(null);b[17]='w';b[53]='b';
assert.equal(rules.play(state(b,'w'),17,26).turn,'b');
b=Array(64).fill(null);b[49]='w';b[58]='b';
s=rules.play(state(b,'w'),49,56);assert.equal(s.board[56],'W');
b=Array(64).fill(null);b[44]='B';b[26]='w';
assert(rules.moves(b,'b',null).some(m=>m.from===44&&m.capture===26&&m.to===17));
b=Array(64).fill(null);b[9]='b';b[55]='w';
s=rules.play(state(b),9,0);assert.equal(s.board[0],'B');assert.equal(s.winner,null);
console.log('Checkers rules passed');
