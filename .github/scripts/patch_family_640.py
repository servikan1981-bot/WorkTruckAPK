from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'missing marker {label} in {path}')
    s = s.replace(old, new, 1)
    p.write_text(s, encoding='utf-8')


# Version bump.
replace_once('duoapp/build.gradle', 'versionCode 6039', 'versionCode 6040', 'versionCode')
replace_once('duoapp/build.gradle', "versionName '6.0.39'", "versionName '6.0.40'", 'versionName')
replace_once('duoapp/src/main/AndroidManifest.xml', 'android:label="Наша семья 6.0.39"', 'android:label="Наша семья 6.0.40"', 'manifest label')

# Keep MainActivity alive during portrait/landscape changes so the active game is not recreated.
manifest = Path('duoapp/src/main/AndroidManifest.xml')
ms = manifest.read_text(encoding='utf-8')
old = '''        <activity
            android:name=".MainActivity"
            android:screenOrientation="portrait"
            android:launchMode="singleTop"
            android:exported="true" />'''
new = '''        <activity
            android:name=".MainActivity"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:launchMode="singleTop"
            android:exported="true" />'''
if old not in ms:
    raise SystemExit('MainActivity manifest marker missing')
manifest.write_text(ms.replace(old, new, 1), encoding='utf-8')

# Native Android fullscreen + forced landscape bridge.
main = Path('duoapp/src/main/java/com/sergey/duochat/MainActivity.java')
s = main.read_text(encoding='utf-8')
if 'import android.content.pm.ActivityInfo;' not in s:
    s = s.replace('import android.content.pm.PackageManager;', 'import android.content.pm.PackageManager;\nimport android.content.pm.ActivityInfo;', 1)
if 'import android.view.View;' not in s:
    s = s.replace('import android.view.WindowManager;', 'import android.view.WindowManager;\nimport android.view.View;', 1)
bridge = '''    public class AndroidBridge {
        @JavascriptInterface
        public String loadProfile() {'''
bridge_new = '''    public class AndroidBridge {
        @JavascriptInterface
        public void setPoolGameFullscreen(boolean enabled) {
            runOnUiThread(() -> {
                try {
                    if (enabled) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public String loadProfile() {'''
if 'setPoolGameFullscreen(boolean enabled)' not in s:
    if bridge not in s:
        raise SystemExit('AndroidBridge marker missing')
    s = s.replace(bridge, bridge_new, 1)
main.write_text(s, encoding='utf-8')


def patch_pool_engine(path):
    p = Path(path)
    t = p.read_text(encoding='utf-8')
    t = t.replace('b.vx=Math.abs(b.vx)*.91;', 'b.vx=Math.abs(b.vx)*.94;')
    t = t.replace('b.vx=-Math.abs(b.vx)*.91;', 'b.vx=-Math.abs(b.vx)*.94;')
    t = t.replace('b.vy=Math.abs(b.vy)*.91;', 'b.vy=Math.abs(b.vy)*.94;')
    t = t.replace('b.vy=-Math.abs(b.vy)*.91;', 'b.vy=-Math.abs(b.vy)*.94;')
    old = 'var frames=[],pocketed=[],firstContact=null,lastFrame=-9;'
    new = 'var frames=[],pocketed=[],firstContact=null,lastFrame=-9,pendingSound=0;'
    if old not in t:
        raise SystemExit(f'frames marker missing in {path}')
    t = t.replace(old, new, 1)
    old = "function snap(){if(!keepFrames)return;frames.push(balls.map(function(b){return {n:b.n,x:+b.x.toFixed(2),y:+b.y.toFixed(2),pocketed:b.pocketed};}));}"
    new = "function snap(){if(!keepFrames)return;var fr=balls.map(function(b){return {n:b.n,x:+b.x.toFixed(2),y:+b.y.toFixed(2),pocketed:b.pocketed};});if(pendingSound>0){fr._sound=Math.min(1,pendingSound);pendingSound=0;}frames.push(fr);}"
    if old not in t:
        raise SystemExit(f'snap marker missing in {path}')
    t = t.replace(old, new, 1)
    old = "var rvx=B.vx-A.vx,rvy=B.vy-A.vy,sep=rvx*nx+rvy*ny;if(sep<0){var imp=-(1.92)*sep/2;"
    new = "var rvx=B.vx-A.vx,rvy=B.vy-A.vy,sep=rvx*nx+rvy*ny;if(sep<0){var hitStrength=Math.min(1,Math.abs(sep)/22);if(hitStrength>.055)pendingSound=Math.max(pendingSound,hitStrength);var imp=-(1.92)*sep/2;"
    if old not in t:
        raise SystemExit(f'collision marker missing in {path}')
    t = t.replace(old, new, 1)
    old = 'bb.vx*=.987;bb.vy*=.987;if(Math.abs(bb.vx)<.018)bb.vx=0;if(Math.abs(bb.vy)<.018)bb.vy=0;'
    new = 'bb.vx*=.993;bb.vy*=.993;if(Math.abs(bb.vx)<.010)bb.vx=0;if(Math.abs(bb.vy)<.010)bb.vy=0;'
    if old not in t:
        raise SystemExit(f'friction marker missing in {path}')
    t = t.replace(old, new, 1)
    p.write_text(t, encoding='utf-8')


patch_pool_engine('duoapp/src/main/assets/billiards.js')
patch_pool_engine('duoapp/src/main/assets/durak.js')

# Solo mode animation: slower playback and the same cue/ball sounds.
durak = Path('duoapp/src/main/assets/durak.js')
ds = durak.read_text(encoding='utf-8')
old = "function animate(frames,done){if(!frames||!frames.length){done();return;}var stride=Math.max(1,Math.ceil(frames.length/150)),i=0;function tick(){if(!solo.active){done();return;}drawTable(frames[Math.min(i,frames.length-1)],false);i+=stride;if(i<frames.length)root.requestAnimationFrame(tick);else{drawTable(frames[frames.length-1],false);setTimeout(done,60);}}tick();}"
new = "function animate(frames,done){if(!frames||!frames.length){done();return;}if(root.poolPlayCueSound)root.poolPlayCueSound(solo.power);var stride=Math.max(1,Math.ceil(frames.length/220)),i=0,lastClack=0;function tick(){if(!solo.active){done();return;}var fr=frames[Math.min(i,frames.length-1)];drawTable(fr,false);if(fr&&fr._sound&&root.poolPlayBallSound){var now=Date.now();if(now-lastClack>34){lastClack=now;root.poolPlayBallSound(fr._sound);}}i+=stride;if(i<frames.length)root.requestAnimationFrame(tick);else{drawTable(frames[frames.length-1],false);setTimeout(done,90);}}tick();}"
if old not in ds:
    raise SystemExit('solo animate marker missing')
ds = ds.replace(old, new, 1)
ds = ds.replace("'Наша семья · v6.0.39'", "'Наша семья · v6.0.40'")
durak.write_text(ds, encoding='utf-8')

# UI, audio and fullscreen controls.
idx = Path('duoapp/src/main/assets/index.html')
h = idx.read_text(encoding='utf-8')
h = h.replace('<title>Наша семья 6.0.38</title>', '<title>Наша семья 6.0.40</title>', 1)
h = h.replace("var APP_VERSION='6.0.38'", "var APP_VERSION='6.0.40'", 1)
top = '''  <div class="topbar"><button id="poolBackBtn" class="iconbtn">‹</button><div class="who"><div id="poolTitle" class="name">Бильярд · 8-ball</div><div id="poolStatus" class="status"></div></div></div>'''
top_new = '''  <div class="topbar"><button id="poolBackBtn" class="iconbtn">‹</button><div class="who"><div id="poolTitle" class="name">Бильярд · 8-ball</div><div id="poolStatus" class="status"></div></div><button id="poolFullscreenBtn" class="iconbtn" type="button" title="На весь экран">⛶</button></div>'''
if top not in h:
    raise SystemExit('pool topbar marker missing')
h = h.replace(top, top_new, 1)

css_anchor = 'body[data-theme="dark"] #pool{background:radial-gradient(circle at 50% 10%,#183b2e,#050908 72%)}'
css_extra = '''body[data-theme="dark"] #pool{background:radial-gradient(circle at 50% 10%,#183b2e,#050908 72%)}
#poolFullscreenBtn{font-size:24px;font-weight:900;color:#f5fff9;background:rgba(255,255,255,.11)}
body.pool-fullscreen-mode{overflow:hidden;background:#020504}
body.pool-fullscreen-mode #pool{position:fixed!important;inset:0!important;z-index:500!important;width:100vw!important;height:100dvh!important;min-height:100dvh!important}
body.pool-fullscreen-mode #pool .topbar{min-height:44px;padding:4px 7px;background:rgba(3,15,11,.94);border-bottom-color:rgba(255,255,255,.10)}
body.pool-fullscreen-mode #poolBackBtn{display:none!important}
body.pool-fullscreen-mode #pool .pool-body{overflow:hidden;display:grid;grid-template-columns:minmax(0,1fr) minmax(155px,24vw);grid-template-rows:auto auto auto 1fr;gap:5px;padding:4px 7px 6px}
body.pool-fullscreen-mode #pool .pool-table-shell{grid-column:1;grid-row:1/5;align-self:center;justify-self:center;max-width:none;margin:0;width:min(100%,calc((100dvh - 54px)*2));padding:5px;border-radius:18px}
body.pool-fullscreen-mode #pool .pool-score{grid-column:2;grid-row:1;display:grid;grid-template-columns:1fr;gap:3px;width:100%;margin:0;max-width:none}
body.pool-fullscreen-mode #pool .pool-player{padding:5px 7px;border-radius:10px;font-size:11px}
body.pool-fullscreen-mode #pool .pool-group{font-size:9px;margin-top:1px}
body.pool-fullscreen-mode #pool .pool-balls{display:none}
body.pool-fullscreen-mode #pool .pool-hint{grid-column:2;grid-row:2;width:100%;min-height:0;margin:0;padding:3px;font-size:10px;line-height:1.2;align-self:center}
body.pool-fullscreen-mode #pool .pool-power{grid-column:2;grid-row:3;width:100%;max-width:none;margin:0;padding:7px;grid-template-columns:1fr auto;gap:4px}
body.pool-fullscreen-mode #pool .pool-power label{grid-column:1/3;font-size:10px}
body.pool-fullscreen-mode #pool .pool-actions{grid-column:2;grid-row:4;display:flex;flex-direction:column;width:100%;max-width:none;margin:0;gap:4px;align-self:start}
body.pool-fullscreen-mode #pool .pool-actions button{padding:8px 6px;border-radius:10px;font-size:11px}'''
if css_anchor not in h:
    raise SystemExit('pool css anchor missing')
h = h.replace(css_anchor, css_extra, 1)

audio_code = '''
var poolAudioCtx=null,poolLastBallSound=0,poolFullscreenActive=false;
function poolGetAudio(){try{var C=window.AudioContext||window.webkitAudioContext;if(!C)return null;if(!poolAudioCtx)poolAudioCtx=new C();if(poolAudioCtx.state==='suspended')poolAudioCtx.resume().catch(function(){});return poolAudioCtx;}catch(e){return null;}}
function poolNoiseClick(freq,duration,gain){var c=poolGetAudio();if(!c)return;try{var len=Math.max(64,Math.floor(c.sampleRate*duration)),buf=c.createBuffer(1,len,c.sampleRate),d=buf.getChannelData(0);for(var i=0;i<len;i++){var env=1-i/len;d[i]=(Math.random()*2-1)*env*env;}var src=c.createBufferSource(),filter=c.createBiquadFilter(),g=c.createGain();filter.type='bandpass';filter.frequency.value=freq;filter.Q.value=1.5;g.gain.setValueAtTime(Math.max(.015,gain),c.currentTime);g.gain.exponentialRampToValueAtTime(.001,c.currentTime+duration);src.buffer=buf;src.connect(filter);filter.connect(g);g.connect(c.destination);src.start();src.stop(c.currentTime+duration+.01);}catch(e){}}
function poolPlayCueSound(strength){var p=Math.max(.2,Math.min(1,Number(strength)||.6));poolNoiseClick(420,.055,.16*p+.04);var c=poolGetAudio();if(!c)return;try{var o=c.createOscillator(),g=c.createGain();o.type='triangle';o.frequency.setValueAtTime(165,c.currentTime);o.frequency.exponentialRampToValueAtTime(85,c.currentTime+.055);g.gain.setValueAtTime(.09*p,c.currentTime);g.gain.exponentialRampToValueAtTime(.001,c.currentTime+.065);o.connect(g);g.connect(c.destination);o.start();o.stop(c.currentTime+.07);}catch(e){}}
function poolPlayBallSound(strength){var now=Date.now();if(now-poolLastBallSound<28)return;poolLastBallSound=now;var p=Math.max(.12,Math.min(1,Number(strength)||.45));poolNoiseClick(1750,.038,.11*p+.018);var c=poolGetAudio();if(!c)return;try{var o=c.createOscillator(),g=c.createGain();o.type='sine';o.frequency.setValueAtTime(1150+520*p,c.currentTime);o.frequency.exponentialRampToValueAtTime(720,c.currentTime+.03);g.gain.setValueAtTime(.055*p,c.currentTime);g.gain.exponentialRampToValueAtTime(.001,c.currentTime+.04);o.connect(g);g.connect(c.destination);o.start();o.stop(c.currentTime+.045);}catch(e){}}
function poolSetFullscreen(on){poolFullscreenActive=!!on;document.body.classList.toggle('pool-fullscreen-mode',poolFullscreenActive);var b=$('poolFullscreenBtn');if(b){b.textContent=poolFullscreenActive?'✕':'⛶';b.title=poolFullscreenActive?'Выйти из полноэкранного режима':'На весь экран';}var nativeDone=false;try{if(window.AndroidBridge&&AndroidBridge.setPoolGameFullscreen){AndroidBridge.setPoolGameFullscreen(poolFullscreenActive);nativeDone=true;}}catch(e){}if(!nativeDone){try{if(poolFullscreenActive){var el=document.documentElement;if(el.requestFullscreen)el.requestFullscreen().catch(function(){});if(screen.orientation&&screen.orientation.lock)screen.orientation.lock('landscape').catch(function(){});}else{if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen().catch(function(){});if(screen.orientation&&screen.orientation.unlock)screen.orientation.unlock();}}catch(e){}}setTimeout(function(){try{var g=poolGames[activePoolId];if(g)poolDrawBalls(g.state.balls,true);}catch(e){}window.dispatchEvent(new Event('resize'));},260);}
'''
marker = 'function poolAnimateFrames(frames){return new Promise(function(resolve){if(!frames||!frames.length){resolve();return;}var stride=Math.max(1,Math.ceil(frames.length/150)),i=0;function tick(){poolDrawBalls(frames[Math.min(i,frames.length-1)],false);i+=stride;if(i<frames.length)requestAnimationFrame(tick);else{poolDrawBalls(frames[frames.length-1],false);setTimeout(resolve,70);}}tick();});}'
replacement = audio_code + '\n' + "function poolAnimateFrames(frames){return new Promise(function(resolve){if(!frames||!frames.length){resolve();return;}poolPlayCueSound(poolPower);var stride=Math.max(1,Math.ceil(frames.length/220)),i=0,lastClack=0;function tick(){var fr=frames[Math.min(i,frames.length-1)];poolDrawBalls(fr,false);if(fr&&fr._sound){var now=Date.now();if(now-lastClack>34){lastClack=now;poolPlayBallSound(fr._sound);}}i+=stride;if(i<frames.length)requestAnimationFrame(tick);else{poolDrawBalls(frames[frames.length-1],false);setTimeout(resolve,90);}}tick();});}"
if marker not in h:
    raise SystemExit('poolAnimateFrames marker missing')
h = h.replace(marker, replacement, 1)

old = "$('poolBackBtn').addEventListener('click',function(){activePoolId='';openGamesHub();});$('poolHitBtn').addEventListener('click',poolShoot);"
new = "$('poolBackBtn').addEventListener('click',function(){if(poolFullscreenActive)poolSetFullscreen(false);activePoolId='';openGamesHub();});$('poolFullscreenBtn').addEventListener('click',function(){poolSetFullscreen(!poolFullscreenActive);});$('poolHitBtn').addEventListener('click',poolShoot);"
if old not in h:
    raise SystemExit('pool event marker missing')
h = h.replace(old, new, 1)
idx.write_text(h, encoding='utf-8')
