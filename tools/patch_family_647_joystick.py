from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing marker: {label}")
    return text.replace(old, new, 1)

# Version 6.0.47
version_replacements = {
    'duoapp/build.gradle': [('versionCode 6046', 'versionCode 6047'), ("versionName '6.0.46'", "versionName '6.0.47'")],
    'duoapp/src/main/AndroidManifest.xml': [('Наша семья 6.0.46', 'Наша семья 6.0.47')],
    'duoapp/src/main/assets/index.html': [('6.0.46', '6.0.47')],
}
for name, pairs in version_replacements.items():
    p = Path(name)
    text = p.read_text(encoding='utf-8')
    for old, new in pairs:
        if old not in text:
            raise SystemExit(f"missing version marker {old!r} in {name}")
        text = text.replace(old, new)
    p.write_text(text, encoding='utf-8')

p = Path('duoapp/src/main/assets/arkanoid.js')
text = p.read_text(encoding='utf-8')

text = replace_once(
    text,
    "var state={level:1,score:0,lives:3,high:0,running:false,paused:false,started:false,fullscreen:false,lastTs:0,raf:0,paddle:null,balls:[],bricks:[],bonuses:[],particles:[],wideUntil:0,message:'',messageUntil:0};",
    "var state={level:1,score:0,lives:3,high:0,running:false,paused:false,started:false,fullscreen:false,lastTs:0,raf:0,paddle:null,balls:[],bricks:[],bonuses:[],particles:[],wideUntil:0,message:'',messageUntil:0,joyAxis:0,joyPointer:null};",
    'state joystick fields',
)

style_marker = "';doc.head.appendChild(s);"
extra_css = """\\
.ark-stage{display:flex;flex-direction:column;align-items:center;gap:8px;width:100%;max-width:960px;margin:0 auto;min-height:0}\\
.ark-joystick{width:100%;display:flex;align-items:center;justify-content:center;gap:8px;flex:0 0 auto}.ark-joystick-title{font-size:11px;font-weight:900;letter-spacing:.08em;color:#91a8dc;white-space:nowrap}.ark-joystick-track{position:relative;width:min(72vw,520px);height:58px;border-radius:30px;border:1px solid rgba(123,157,255,.48);background:linear-gradient(180deg,rgba(25,47,99,.92),rgba(8,18,48,.96));box-shadow:inset 0 4px 15px rgba(0,0,0,.38),0 6px 18px rgba(0,0,0,.25);touch-action:none;user-select:none;-webkit-user-select:none;overflow:hidden}.ark-joystick-track:before{content:'◀';position:absolute;left:17px;top:50%;transform:translateY(-50%);font-size:20px;color:#89a8ff}.ark-joystick-track:after{content:'▶';position:absolute;right:17px;top:50%;transform:translateY(-50%);font-size:20px;color:#89a8ff}.ark-joystick-knob{position:absolute;left:50%;top:50%;width:50px;height:50px;border-radius:50%;transform:translate(-50%,-50%);background:radial-gradient(circle at 35% 28%,#e9f4ff 0,#75bcff 28%,#326df2 63%,#17317e 100%);border:2px solid rgba(255,255,255,.72);box-shadow:0 5px 18px rgba(19,77,220,.55),inset 0 2px 5px rgba(255,255,255,.5);pointer-events:none;transition:left .08s ease}.ark-joystick.active .ark-joystick-knob{transition:none;box-shadow:0 4px 22px rgba(80,145,255,.8),inset 0 2px 5px rgba(255,255,255,.5)}\\
.arkanoid-fullscreen-mode .ark-stage{flex:1;flex-direction:row;justify-content:center;align-items:center;gap:8px;max-width:100vw}.arkanoid-fullscreen-mode .ark-shell{width:min(calc(100vw - 142px),calc((100vh - 92px) * 16 / 9));max-width:calc(100vw - 142px);max-height:calc(100vh - 92px);flex:0 1 auto}.arkanoid-fullscreen-mode .ark-joystick{width:126px;height:126px;flex:0 0 126px;flex-direction:column;gap:4px}.arkanoid-fullscreen-mode .ark-joystick-title{font-size:10px}.arkanoid-fullscreen-mode .ark-joystick-track{width:112px;height:112px;border-radius:56px}.arkanoid-fullscreen-mode .ark-joystick-track:before{left:9px;font-size:17px}.arkanoid-fullscreen-mode .ark-joystick-track:after{right:9px;font-size:17px}.arkanoid-fullscreen-mode .ark-joystick-knob{width:52px;height:52px}\\
@media(max-width:520px){.ark-body{gap:6px}.ark-hud{gap:4px}.ark-stat{padding:5px 4px;font-size:11px}.ark-stat strong{font-size:16px}.ark-joystick{gap:5px}.ark-joystick-title{font-size:9px}.ark-joystick-track{height:54px;width:min(76vw,390px)}.ark-joystick-knob{width:46px;height:46px}.ark-controls button{padding:9px 5px;font-size:12px}}\\
"""
text = replace_once(text, style_marker, extra_css + style_marker, 'style insertion')

# Replace dynamic HTML lines by semantic markers instead of exact escaping.
lines = text.splitlines(keepends=True)
stage_found = False
hint_found = False
for i, line in enumerate(lines):
    if (not stage_found) and 'id=\\"arkCanvas\\"' in line and 'class=\\"ark-shell\\"' in line:
        stage_html = ' <div class=\\"ark-stage\\"><div class=\\"ark-shell\\"><canvas id=\\"arkCanvas\\" width=\\"960\\" height=\\"540\\" aria-label=\\"Арканоид\\"></canvas><div id=\\"arkOverlay\\" class=\\"ark-overlay\\"><span>Нажмите «Старт»</span></div></div><div id=\\"arkJoystick\\" class=\\"ark-joystick\\"><span class=\\"ark-joystick-title\\">УПРАВЛЕНИЕ</span><div id=\\"arkJoystickTrack\\" class=\\"ark-joystick-track\\" role=\\"slider\\" aria-label=\\"Управление платформой\\" aria-valuemin=\\"-100\\" aria-valuemax=\\"100\\" aria-valuenow=\\"0\\"><div id=\\"arkJoystickKnob\\" class=\\"ark-joystick-knob\\"></div></div></div></div>'
        lines[i] = stage_html + '\\' + '\n'
        stage_found = True
    elif (not hint_found) and 'Ведите пальцем по игровому полю' in line:
        lines[i] = ' <div class=\\"ark-hint\\">Управляйте платформой джойстиком. Игровое поле остаётся свободным; коснитесь его только для запуска шара.</div>' + '\\' + '\n'
        hint_found = True
if not stage_found:
    raise SystemExit('missing marker: stage HTML')
if not hint_found:
    raise SystemExit('missing marker: joystick hint')
text = ''.join(lines)

old_canvas = """ if(canvas){
  canvas.addEventListener('pointerdown',function(e){unlockAudio();movePaddle(e);if(state.started&&state.balls.some(function(b){return b.stuck;}))launchStuckBalls();try{canvas.setPointerCapture(e.pointerId);}catch(_e){};});
  canvas.addEventListener('pointermove',function(e){if(e.buttons||e.pointerType==='touch'||e.pointerType==='pen')movePaddle(e);});
 }
"""
new_canvas = """ if(canvas){
  canvas.addEventListener('pointerdown',function(){unlockAudio();if(state.started&&state.balls.some(function(b){return b.stuck;}))launchStuckBalls();});
 }
 bindJoystick();
"""
text = replace_once(text, old_canvas, new_canvas, 'canvas input replacement')

# Replace movePaddle by line marker for resilience.
lines = text.splitlines(keepends=True)
move_found = False
joy_code = """function joystickAxisFromClientX(clientX,left,width){var raw=((clientX-left)/Math.max(1,width)-.5)*2;raw=clamp(raw,-1,1);var a=Math.abs(raw);if(a<.08)return 0;return Math.sign(raw)*((a-.08)/.92);}
function syncStuckBalls(){if(!state.paddle)return;state.balls.forEach(function(b){if(b.stuck)b.x=state.paddle.x+state.paddle.w/2;});}
function updateJoystickVisual(){if(!doc)return;var knob=doc.getElementById('arkJoystickKnob'),track=doc.getElementById('arkJoystickTrack');if(knob)knob.style.left=(50+state.joyAxis*31)+'%';if(track)track.setAttribute('aria-valuenow',String(Math.round(state.joyAxis*100)));}
function resetJoystick(){state.joyAxis=0;state.joyPointer=null;if(doc){var box=doc.getElementById('arkJoystick');if(box)box.classList.remove('active');}updateJoystickVisual();}
function bindJoystick(){if(!doc)return;var track=doc.getElementById('arkJoystickTrack');if(!track||track.dataset.bound==='1')return;track.dataset.bound='1';function apply(e){var r=track.getBoundingClientRect();state.joyAxis=joystickAxisFromClientX(e.clientX,r.left,r.width);updateJoystickVisual();}function end(e){if(state.joyPointer!==null&&e&&e.pointerId!==state.joyPointer)return;resetJoystick();}track.addEventListener('pointerdown',function(e){unlockAudio();state.joyPointer=e.pointerId;var box=doc.getElementById('arkJoystick');if(box)box.classList.add('active');try{track.setPointerCapture(e.pointerId);}catch(_e){}apply(e);e.preventDefault();});track.addEventListener('pointermove',function(e){if(state.joyPointer===e.pointerId){apply(e);e.preventDefault();}});track.addEventListener('pointerup',end);track.addEventListener('pointercancel',end);track.addEventListener('lostpointercapture',function(){resetJoystick();});updateJoystickVisual();}
"""
for i, line in enumerate(lines):
    if line.startswith('function movePaddle(e)'):
        lines[i] = joy_code
        move_found = True
        break
if not move_found:
    raise SystemExit('missing marker: movePaddle')
text = ''.join(lines)

text = replace_once(
    text,
    " var p=state.paddle;\n",
    " var p=state.paddle;if(p&&Math.abs(state.joyAxis)>.001){var paddleSpeed=820;p.x=clamp(p.x+state.joyAxis*paddleSpeed*dt,8,W-p.w-8);syncStuckBalls();}\n",
    'joystick motion integration',
)

text = replace_once(
    text,
    " setTimeout(draw,220);\n}",
    " resetJoystick();setTimeout(function(){updateJoystickVisual();draw();},220);\n}",
    'fullscreen joystick reset',
)

text = replace_once(
    text,
    "root.ArkanoidGame={speedForLevel:speedForLevel,brickRowsForLevel:brickRowsForLevel,buildLevel:buildLevel,open:openGame,newGame:newGame};",
    "root.ArkanoidGame={speedForLevel:speedForLevel,brickRowsForLevel:brickRowsForLevel,buildLevel:buildLevel,joystickAxisFromClientX:joystickAxisFromClientX,open:openGame,newGame:newGame};",
    'Arkanoid test API',
)

p.write_text(text, encoding='utf-8')
print('PATCH_647_OK')
