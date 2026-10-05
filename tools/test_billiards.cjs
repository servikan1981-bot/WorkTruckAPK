const fs=require('fs'),vm=require('vm'),assert=require('assert');
const code=fs.readFileSync('duoapp/src/main/assets/billiards.js','utf8');
const box={console,Math,JSON};box.globalThis=box;vm.createContext(box);vm.runInContext(code,box);
const P=box.PoolRules;assert(P&&typeof P.initial==='function'&&typeof P.shot==='function');
const a=P.initial();
assert.equal(a.balls.length,16);assert.equal(a.turn,0);assert.equal(a.winner,null);assert.deepEqual(Array.from(a.groups),[null,null]);
assert.equal(a.balls.filter(b=>b.n===0).length,1);assert.equal(a.balls.filter(b=>b.n===8).length,1);
const r1=P.shot(a,0,0,0.82),r2=P.shot(a,0,0,0.82);assert(r1&&r2);assert.equal(JSON.stringify(r1.state),JSON.stringify(r2.state),'physics must be deterministic');
assert(r1.frames.length>240,'shot should have realistically long rolling animation');assert.equal(r1.state.seq,1);assert(r1.state.last&&r1.state.last.kind==='shot');
for(const b of r1.state.balls){assert(Number.isFinite(b.x)&&Number.isFinite(b.y));if(!b.pocketed){assert(b.x>=P.R&&b.x<=P.W-P.R);assert(b.y>=P.R&&b.y<=P.H-P.R);}}
const wrong=P.shot(a,1,0,.5);assert.equal(wrong,null,'only current player may shoot');
const res=P.resign(a,0);assert.equal(res.winner,1);assert.equal(res.seq,1);
console.log('BILLIARDS_TEST_OK frames='+r1.frames.length+' first='+r1.state.last.firstContact+' potted='+r1.state.last.pocketed.join(','));
