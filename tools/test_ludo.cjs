const fs=require('fs'),vm=require('vm');
const ctx={window:{}};vm.createContext(ctx);vm.runInContext(fs.readFileSync('duoapp/src/main/assets/ludo.js','utf8'),ctx);const R=ctx.window.LudoRules;
let s=R.initial(3);if(s.players!==3||s.turn!==0)throw Error('init');if(R.movable(s,0,5).length)throw Error('home');
s=R.roll(s,0,6);if(R.movable(s,0,6).length!==4)throw Error('roll6');s=R.move(s,0,0);if(s.pieces[0][0]!==0||s.turn!==0)throw Error('enter');
s=R.roll(s,0,3);s=R.move(s,0,0);if(s.pieces[0][0]!==3||s.turn!==1)throw Error('move');
let f=R.initial(2);f.pieces[0]=[57,58,58,58];f=R.roll(f,0,1);f=R.move(f,0,0);if(f.winner!==0)throw Error('finish');
console.log('Ludo rules OK');
