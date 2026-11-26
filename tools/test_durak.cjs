const D=require('../duoapp/src/main/assets/durak.js');
function ok(x,m){if(!x)throw new Error(m)}
ok(D.deck().length===36,'36 cards');
ok(new Set(D.deck()).size===36,'unique cards');
ok(D.beats('h7','h6','s'),'same suit higher');
ok(D.beats('s6','h14','s'),'trump beats ace');
ok(!D.beats('h14','s6','s'),'nontrump does not beat trump');
let s=D.initial(123456);ok(s.hands[0].length===6&&s.hands[1].length===6,'deal 6');ok(s.deck.length===24,'talon 24');
let a=s.hands[s.attacker][0],s2=D.attack(s,a);ok(s2&&s2.table.length===1,'attack');
let choices=s2.hands[s2.defender].filter(c=>D.beats(c,a,s2.trump));
if(choices.length){let s3=D.defend(s2,0,choices[0]);ok(s3&&s3.table[0].d,'defend');let s4=D.done(s3);ok(s4&&s4.table.length===0,'bito');}
let p=D.pickup(s2);ok(p&&p.table.length===0,'pickup');ok(p.attacker===s.attacker,'attacker continues after pickup');
console.log('PASS durak rules');
