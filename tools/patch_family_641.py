from pathlib import Path
import re


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new)

# Version
p=Path('duoapp/build.gradle'); s=p.read_text(encoding='utf-8')
s=must_replace(s,'versionCode 6040','versionCode 6041','versionCode')
s=must_replace(s,"versionName '6.0.40'","versionName '6.0.41'",'versionName')
p.write_text(s,encoding='utf-8')

p=Path('duoapp/src/main/AndroidManifest.xml'); s=p.read_text(encoding='utf-8')
s=s.replace('Наша семья 6.0.40','Наша семья 6.0.41')
p.write_text(s,encoding='utf-8')

# Native orientation + reliable game audio
p=Path('duoapp/src/main/java/com/sergey/duochat/MainActivity.java'); s=p.read_text(encoding='utf-8')
s=must_replace(s,'import android.media.AudioManager;','import android.media.AudioManager;\nimport android.media.AudioFormat;\nimport android.media.AudioTrack;','audio imports')
s=must_replace(s,'    private static volatile WeakReference<MainActivity> visibleActivity = new WeakReference<>(null);', '''    private static final ThreadPoolExecutor GAME_AUDIO = new ThreadPoolExecutor(
            1, 2, 1L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), r -> {
                Thread t = new Thread(r, "OurFamilyGameAudio");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private static volatile WeakReference<MainActivity> visibleActivity = new WeakReference<>(null);''','audio executor')
s=must_replace(s,'    private boolean telecomPromptShownThisRun = false;','    private boolean telecomPromptShownThisRun = false;\n    private volatile boolean poolGameActive = false;','pool active field')

native_helper='''    private void playPoolSoundNative(String kind, int strengthPercent) {
        final boolean cue = "cue".equals(kind);
        final float strength = Math.max(0.15f, Math.min(1f, strengthPercent / 100f));
        try {
            GAME_AUDIO.execute(() -> {
                AudioTrack track = null;
                try {
                    final int sampleRate = 22050;
                    final int durationMs = cue ? 105 : 58;
                    final int count = sampleRate * durationMs / 1000;
                    short[] pcm = new short[count];
                    long seed = System.nanoTime() ^ (cue ? 0x4f11L : 0x91a7L);
                    for (int i = 0; i < count; i++) {
                        double t = i / (double) sampleRate;
                        double x = i / (double) Math.max(1, count - 1);
                        double env = Math.pow(1.0 - x, cue ? 2.0 : 3.4);
                        seed = seed * 6364136223846793005L + 1442695040888963407L;
                        double noise = (((seed >>> 33) & 0x7fffffffL) / 1073741824.0) - 1.0;
                        double freq = cue ? (235.0 - 95.0 * x) : (1850.0 - 520.0 * x);
                        double tone = Math.sin(2.0 * Math.PI * freq * t);
                        double second = Math.sin(2.0 * Math.PI * (cue ? 92.0 : 980.0) * t);
                        double sample = env * (cue ? (0.56 * tone + 0.24 * second + 0.20 * noise)
                                                  : (0.68 * tone + 0.18 * second + 0.14 * noise));
                        pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                                sample * 32767.0 * (0.48 + 0.48 * strength)));
                    }
                    int min = AudioTrack.getMinBufferSize(sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    int bytes = Math.max(min, pcm.length * 2);
                    track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            bytes, AudioTrack.MODE_STATIC);
                    track.write(pcm, 0, pcm.length);
                    track.setVolume(Math.min(1f, 0.55f + 0.40f * strength));
                    track.play();
                    Thread.sleep(durationMs + 35L);
                } catch (Exception ignored) {
                } finally {
                    if (track != null) {
                        try { track.stop(); } catch (Exception ignored) {}
                        try { track.release(); } catch (Exception ignored) {}
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {}
    }

'''
s=must_replace(s,'    public class AndroidBridge {',native_helper+'    public class AndroidBridge {','native sound helper')
pat=re.compile(r'''        @JavascriptInterface\n        public void setPoolGameFullscreen\(boolean enabled\) \{.*?\n        \}\n\n        @JavascriptInterface\n        public String loadProfile\(\) \{''',re.S)
replacement='''        @JavascriptInterface
        public void setPoolGameActive(boolean active) {
            runOnUiThread(() -> {
                poolGameActive = active;
                try {
                    if (active) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void requestPoolLandscape() {
            runOnUiThread(() -> {
                poolGameActive = true;
                try {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    if (webView != null) webView.postDelayed(() -> {
                        if (poolGameActive) {
                            try { setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR); }
                            catch (Exception ignored) {}
                        }
                    }, 700);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void setPoolGameFullscreen(boolean enabled) {
            runOnUiThread(() -> {
                try {
                    if (enabled) {
                        getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    }
                    setRequestedOrientation(poolGameActive
                            ? ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void playPoolSound(String kind, int strengthPercent) {
            playPoolSoundNative(kind, strengthPercent);
        }

        @JavascriptInterface
        public String loadProfile() {'''
s2,n=pat.subn(replacement,s,count=1)
if n!=1: raise SystemExit('fullscreen bridge pattern not found')
p.write_text(s2,encoding='utf-8')

# Physics: real rolling lasts substantially longer. Patch both standalone and embedded PoolRules.
for name in ['duoapp/src/main/assets/billiards.js','duoapp/src/main/assets/durak.js']:
    p=Path(name); s=p.read_text(encoding='utf-8')
    for old,new,label in [
        ('step<1900','step<3200','max physics steps'),
        ('>.015)moving=true','>.008)moving=true','moving threshold'),
        ('step-lastFrame>=7','step-lastFrame>=8','frame cadence'),
        ('bb.vx*=.993;bb.vy*=.993','bb.vx*=.9965;bb.vy*=.9965','cloth friction'),
        ('Math.abs(bb.vx)<.010','Math.abs(bb.vx)<.006','vx stop'),
        ('Math.abs(bb.vy)<.010','Math.abs(bb.vy)<.006','vy stop')]:
        if old not in s: raise SystemExit(f'{name}: missing {label}')
        s=s.replace(old,new)
    p.write_text(s,encoding='utf-8')

# UI + audio bridge + auto landscape fullscreen
p=Path('duoapp/src/main/assets/index.html'); s=p.read_text(encoding='utf-8')
s=s.replace('6.0.40','6.0.41')
s=must_replace(s,"#poolFullscreenBtn{font-size:24px;font-weight:900;color:#f5fff9;background:rgba(255,255,255,.11)}",'''#poolFullscreenBtn{width:50px;height:42px;font-size:25px;font-weight:950;color:#251a04;background:linear-gradient(135deg,#ffe28a,#dca72e);box-shadow:0 4px 12px rgba(220,167,46,.35)}
.pool-fullscreen-big{display:block;width:min(760px,100%);margin:7px auto 5px;border:2px solid #f1ca5d;border-radius:14px;padding:12px 14px;background:linear-gradient(135deg,#ffe28a,#dca72e);color:#2b1e05;font-weight:950;font-size:15px;letter-spacing:.02em;box-shadow:0 7px 18px rgba(220,167,46,.28)}''','fullscreen css')
s=must_replace(s,'body.pool-fullscreen-mode #poolBackBtn{display:none!important}', 'body.pool-fullscreen-mode #poolBackBtn{display:none!important}\nbody.pool-fullscreen-mode #poolFullscreenBigBtn{display:none!important}', 'fullscreen big hidden')
s=must_replace(s,'<div class="pool-table-shell"><canvas id="poolCanvas" width="1000" height="500" aria-label="Стол для американского бильярда"></canvas></div>', '<div class="pool-table-shell"><canvas id="poolCanvas" width="1000" height="500" aria-label="Стол для американского бильярда"></canvas></div>\n    <button id="poolFullscreenBigBtn" class="pool-fullscreen-big" type="button">⛶ НА ВЕСЬ ЭКРАН · ПОВЕРНУТЬ ТЕЛЕФОН</button>', 'big fullscreen button')

start=s.index('function poolGetAudio()')
end=s.index('function poolSetFullscreen(on)',start)
audio='''function poolGetAudio(){try{var C=window.AudioContext||window.webkitAudioContext;if(!C)return null;if(!poolAudioCtx)poolAudioCtx=new C();if(poolAudioCtx.state==='suspended')poolAudioCtx.resume().catch(function(){});return poolAudioCtx;}catch(e){return null;}}
function poolNativeSound(kind,strength){try{if(window.AndroidBridge&&AndroidBridge.playPoolSound){AndroidBridge.playPoolSound(kind,Math.round(Math.max(.1,Math.min(1,Number(strength)||.5))*100));return true;}}catch(e){}return false;}
function poolNoiseClick(freq,duration,gain){var c=poolGetAudio();if(!c)return;try{var len=Math.max(64,Math.floor(c.sampleRate*duration)),buf=c.createBuffer(1,len,c.sampleRate),d=buf.getChannelData(0);for(var i=0;i<len;i++){var env=1-i/len;d[i]=(Math.random()*2-1)*env*env;}var src=c.createBufferSource(),filter=c.createBiquadFilter(),g=c.createGain();filter.type='bandpass';filter.frequency.value=freq;filter.Q.value=1.25;g.gain.setValueAtTime(Math.max(.035,gain),c.currentTime);g.gain.exponentialRampToValueAtTime(.001,c.currentTime+duration);src.buffer=buf;src.connect(filter);filter.connect(g);g.connect(c.destination);src.start();src.stop(c.currentTime+duration+.01);}catch(e){}}
function poolPlayCueSound(strength){var p=Math.max(.2,Math.min(1,Number(strength)||.6));if(poolNativeSound('cue',p))return;poolNoiseClick(390,.085,.34*p+.08);var c=poolGetAudio();if(!c)return;try{var o=c.createOscillator(),g=c.createGain();o.type='triangle';o.frequency.setValueAtTime(190,c.currentTime);o.frequency.exponentialRampToValueAtTime(82,c.currentTime+.08);g.gain.setValueAtTime(.22*p+.04,c.currentTime);g.gain.exponentialRampToValueAtTime(.001,c.currentTime+.095);o.connect(g);g.connect(c.destination);o.start();o.stop(c.currentTime+.10);}catch(e){}}
function poolPlayBallSound(strength){var now=Date.now();if(now-poolLastBallSound<42)return;poolLastBallSound=now;var p=Math.max(.12,Math.min(1,Number(strength)||.45));if(poolNativeSound('ball',p))return;poolNoiseClick(1900,.052,.26*p+.05);var c=poolGetAudio();if(!c)return;try{var o=c.createOscillator(),g=c.createGain();o.type='sine';o.frequency.setValueAtTime(1550+620*p,c.currentTime);o.frequency.exponentialRampToValueAtTime(760,c.currentTime+.045);g.gain.setValueAtTime(.15*p+.025,c.currentTime);g.gain.exponentialRampToValueAtTime(.001,c.currentTime+.055);o.connect(g);g.connect(c.destination);o.start();o.stop(c.currentTime+.06);}catch(e){}}
function poolSetGameActive(on){try{if(window.AndroidBridge&&AndroidBridge.setPoolGameActive)AndroidBridge.setPoolGameActive(!!on);}catch(e){}}
function poolRequestLandscape(){try{if(window.AndroidBridge&&AndroidBridge.requestPoolLandscape){AndroidBridge.requestPoolLandscape();return true;}}catch(e){}return false;}
function poolSyncOrientation(){if(!activePoolId||$('pool').classList.contains('hidden'))return;var landscape=window.innerWidth>window.innerHeight*1.08;if(landscape&&!poolFullscreenActive)poolSetFullscreen(true);else if(!landscape&&poolFullscreenActive)poolSetFullscreen(false);}
'''
s=s[:start]+audio+s[end:]

old_fs=re.search(r'function poolSetFullscreen\(on\)\{.*?\n\nfunction poolAnimateFrames',s,re.S)
if not old_fs: raise SystemExit('poolSetFullscreen block missing')
new_fs='''function poolSetFullscreen(on){poolFullscreenActive=!!on;document.body.classList.toggle('pool-fullscreen-mode',poolFullscreenActive);var b=$('poolFullscreenBtn'),big=$('poolFullscreenBigBtn');if(b){b.textContent=poolFullscreenActive?'✕':'⛶';b.title=poolFullscreenActive?'Выйти из полноэкранного режима':'На весь экран';}if(big)big.textContent=poolFullscreenActive?'✕ ВЫЙТИ ИЗ ПОЛНОГО ЭКРАНА':'⛶ НА ВЕСЬ ЭКРАН · ПОВЕРНУТЬ ТЕЛЕФОН';var nativeDone=false;try{if(window.AndroidBridge&&AndroidBridge.setPoolGameFullscreen){AndroidBridge.setPoolGameFullscreen(poolFullscreenActive);nativeDone=true;}}catch(e){}if(!nativeDone){try{if(poolFullscreenActive){var el=document.documentElement;if(el.requestFullscreen)el.requestFullscreen().catch(function(){});}else if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen().catch(function(){});}catch(e){}}setTimeout(function(){try{var g=poolGames[activePoolId];if(g)poolDrawBalls(g.state.balls,true);}catch(e){}window.dispatchEvent(new Event('resize'));},260);}

function poolAnimateFrames'''
s=s[:old_fs.start()]+new_fs+s[old_fs.end():]
s=must_replace(s,'Math.ceil(frames.length/220)','Math.ceil(frames.length/420)','animation duration cap')
s=must_replace(s,"function openPool(id){var g=poolGames[id];if(!g)return;activePoolId=id;$('checkersRequest').classList.add('hidden');showScreen('pool');setTimeout(renderPool,0);}","function openPool(id){var g=poolGames[id];if(!g)return;activePoolId=id;poolSetGameActive(true);$('checkersRequest').classList.add('hidden');showScreen('pool');setTimeout(function(){renderPool();poolSyncOrientation();},0);}",'openPool')
s=must_replace(s,"$('poolBackBtn').addEventListener('click',function(){if(poolFullscreenActive)poolSetFullscreen(false);activePoolId='';openGamesHub();});$('poolFullscreenBtn').addEventListener('click',function(){poolSetFullscreen(!poolFullscreenActive);});$('poolHitBtn').addEventListener('click',poolShoot);", "$('poolBackBtn').addEventListener('click',function(){if(poolFullscreenActive)poolSetFullscreen(false);poolSetGameActive(false);activePoolId='';openGamesHub();});function poolFullscreenTap(){if(poolFullscreenActive){poolSetFullscreen(false);}else{poolRequestLandscape();poolSetFullscreen(true);}}$('poolFullscreenBtn').addEventListener('click',poolFullscreenTap);$('poolFullscreenBigBtn').addEventListener('click',poolFullscreenTap);$('poolHitBtn').addEventListener('pointerdown',function(){poolGetAudio();});$('poolHitBtn').addEventListener('click',poolShoot);",'pool fullscreen listeners')
s=must_replace(s,"$('poolCanvas').addEventListener('pointerdown',function(e){this.setPointerCapture&&this.setPointerCapture(e.pointerId);poolSetAimFromEvent(e);});$('poolCanvas').addEventListener('pointermove',function(e){if(e.buttons)poolSetAimFromEvent(e);});", "$('poolCanvas').addEventListener('pointerdown',function(e){poolGetAudio();this.setPointerCapture&&this.setPointerCapture(e.pointerId);poolSetAimFromEvent(e);});$('poolCanvas').addEventListener('pointermove',function(e){if(e.buttons)poolSetAimFromEvent(e);});var poolOrientationTimer=0;function poolOrientationChanged(){clearTimeout(poolOrientationTimer);poolOrientationTimer=setTimeout(poolSyncOrientation,140);}window.addEventListener('resize',poolOrientationChanged);window.addEventListener('orientationchange',poolOrientationChanged);",'orientation listener')
p.write_text(s,encoding='utf-8')

# Stronger test: the shot must produce a visibly long animation now.
p=Path('tools/test_billiards.cjs'); s=p.read_text(encoding='utf-8')
s=must_replace(s,"assert(r1.frames.length>2,'shot should animate');","assert(r1.frames.length>240,'shot should have realistically long rolling animation');",'physics test')
p.write_text(s,encoding='utf-8')

print('PATCH_641_OK')
