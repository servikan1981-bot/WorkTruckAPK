from pathlib import Path

A=Path('duoapp/src/main/assets/arkanoid.js')
s=A.read_text(encoding='utf-8')
if '__ourFamilyArkanoidJoystickInline' in s:
    raise SystemExit('6.0.49 inline joystick already applied')

s=s.replace('var canvas=null,ctx=null,audioCtx=null;\n','var canvas=null,ctx=null,audioCtx=null;\nvar joyActive=false,joyPointerId=null,joyDir=0,joyRaf=0,joyLastTs=0;\n',1)

old="';doc.head.appendChild(s);\n}"
css="""';doc.head.appendChild(s);
 var j=doc.createElement('style');j.id='arkanoidInlineJoystickStyle';j.textContent='\\
.ark-joy-dock{display:flex;justify-content:center;align-items:center;min-height:82px;width:100%;max-width:960px;margin:0 auto;touch-action:none}.ark-joy-wrap{display:flex;align-items:center;gap:12px;padding:8px 15px;border-radius:24px;background:#09132f;border:1px solid #536fb8}.ark-joy-arrow{font-size:25px;font-weight:900;color:#8db0ff}.ark-joy-label{font-size:12px;font-weight:900;color:#d8e1ff}.ark-joy-track{position:relative;width:155px;height:62px;border-radius:40px;background:#152b60;border:2px solid #668cff;touch-action:none;box-shadow:inset 0 3px 12px rgba(0,0,0,.45)}.ark-joy-track:before{content:\"\";position:absolute;left:50%;top:8px;bottom:8px;width:2px;background:rgba(255,255,255,.25)}.ark-joy-knob{position:absolute;left:50%;top:50%;width:48px;height:48px;margin:-24px 0 0 -24px;border-radius:50%;background:radial-gradient(circle at 35% 30%,#dbe8ff,#4d7cff 58%,#173b9b);border:2px solid #fff;box-shadow:0 4px 16px #245be0;transform:translateX(0)}\\
.arkanoid-fullscreen-mode #arkanoid .ark-body{position:relative;padding-right:145px!important}.arkanoid-fullscreen-mode .ark-joy-dock{position:absolute;right:8px;top:50%;transform:translateY(-50%);width:125px;min-height:0;z-index:20}.arkanoid-fullscreen-mode .ark-joy-wrap{width:112px;flex-direction:column;padding:10px 5px;gap:7px}.arkanoid-fullscreen-mode .ark-joy-track{width:105px;height:60px}.arkanoid-fullscreen-mode .ark-joy-arrow{display:none}\\
';doc.head.appendChild(j);
}"""
assert old in s, 'style anchor missing'
s=s.replace(old,css,1)

old=' <div class="ark-hint">Ведите пальцем по игровому полю, чтобы двигать платформу. Коснитесь поля, чтобы запустить шар.</div>\\\n'
new=' <div id="arkJoyDock" class="ark-joy-dock"><div class="ark-joy-wrap"><span class="ark-joy-arrow">◀</span><div id="arkJoyTrack" class="ark-joy-track" aria-label="Джойстик управления платформой"><div id="arkJoyKnob" class="ark-joy-knob"></div></div><span class="ark-joy-arrow">▶</span><span class="ark-joy-label">Платформа</span></div></div>\\\n <div class="ark-hint">Управляйте платформой джойстиком. «Старт» запускает игру и шар.</div>\\\n'
assert old in s, 'UI anchor missing'
s=s.replace(old,new,1)

old=" }\n try{state.high=Number(root.localStorage.getItem('ourfamily_arkanoid_high')||0)||0;}catch(e){}"
assert old in s, 'bind anchor missing'
s=s.replace(old," }\n bindJoystick();\n try{state.high=Number(root.localStorage.getItem('ourfamily_arkanoid_high')||0)||0;}catch(e){}",1)

marker='function initScene(){\n'
joy=r'''function bindJoystick(){
 var track=doc&&doc.getElementById('arkJoyTrack');if(!track||track.dataset.directBound==='1')return;track.dataset.directBound='1';
 function pos(e){var r=track.getBoundingClientRect(),half=Math.max(1,r.width/2-25),shift=Math.max(0,r.width/2-29),dx=e.clientX-(r.left+r.width/2);joyDir=clamp(dx/half,-1,1);var k=doc.getElementById('arkJoyKnob');if(k)k.style.transform='translateX('+(joyDir*shift).toFixed(1)+'px)';}
 function down(e){e.preventDefault();unlockAudio();joyActive=true;joyPointerId=e.pointerId;pos(e);try{track.setPointerCapture(e.pointerId);}catch(_e){}joyLastTs=0;if(!joyRaf)joyRaf=root.requestAnimationFrame(joyStep);}
 function move(e){if(!joyActive||e.pointerId!==joyPointerId)return;e.preventDefault();pos(e);}
 function up(e){if(joyPointerId!==null&&e.pointerId!==joyPointerId)return;stopJoystick();}
 track.addEventListener('pointerdown',down,{passive:false});track.addEventListener('pointermove',move,{passive:false});track.addEventListener('pointerup',up,{passive:false});track.addEventListener('pointercancel',up,{passive:false});track.addEventListener('lostpointercapture',up,{passive:false});
}
function stopJoystick(){joyActive=false;joyPointerId=null;joyDir=0;joyLastTs=0;var k=doc&&doc.getElementById('arkJoyKnob');if(k)k.style.transform='translateX(0px)';}
function joyStep(ts){joyRaf=0;if(!joyActive){joyLastTs=0;return;}if(!joyLastTs)joyLastTs=ts;var dt=Math.min(.04,Math.max(0,(ts-joyLastTs)/1000));joyLastTs=ts;if(Math.abs(joyDir)>.07)movePaddleDirect(joyDir*780*dt);joyRaf=root.requestAnimationFrame(joyStep);}
function movePaddleDirect(dx){if(!state.paddle)return;state.paddle.x=clamp(state.paddle.x+dx,8,W-state.paddle.w-8);state.balls.forEach(function(b){if(b.stuck)b.x=state.paddle.x+state.paddle.w/2;});if(!state.running||state.paused)draw();}

'''
assert marker in s, 'function anchor missing'
s=s.replace(marker,joy+marker,1)
s=s.replace("state.message='Коснитесь поля или нажмите «Старт»';","state.message='Используйте джойстик и нажмите «Старт»';",1)
s=s.replace('function closeGame(){state.paused=true;','function closeGame(){stopJoystick();state.paused=true;',1)
rootline='root.ArkanoidGame={speedForLevel:speedForLevel,brickRowsForLevel:brickRowsForLevel,buildLevel:buildLevel,open:openGame,newGame:newGame};'
assert rootline in s, 'export anchor missing'
s=s.replace(rootline,rootline+'\nroot.__ourFamilyArkanoidJoystickInline=true;',1)
A.write_text(s,encoding='utf-8')

H=Path('duoapp/src/main/assets/index.html')
t=H.read_text(encoding='utf-8')
assert "APP_VERSION='6.0.48'" in t
t=t.replace("APP_VERSION='6.0.48'","APP_VERSION='6.0.49'")
t=t.replace('<script src="arkanoid_joystick.js"></script>\n','')
H.write_text(t,encoding='utf-8')

G=Path('duoapp/build.gradle')
t=G.read_text(encoding='utf-8')
assert 'versionCode 6048' in t and "versionName '6.0.48'" in t
t=t.replace('versionCode 6048','versionCode 6049').replace("versionName '6.0.48'","versionName '6.0.49'")
G.write_text(t,encoding='utf-8')

M=Path('duoapp/src/main/AndroidManifest.xml')
t=M.read_text(encoding='utf-8')
assert 'android:label="Наша семья 6.0.48"' in t
t=t.replace('android:label="Наша семья 6.0.48"','android:label="Наша семья 6.0.49"')
M.write_text(t,encoding='utf-8')

print('PREPARED_6049_INLINE_JOYSTICK')
