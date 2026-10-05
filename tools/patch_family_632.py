from pathlib import Path

p = Path('duoapp/src/main/assets/index.html')
h = p.read_text(encoding='utf-8')


def once(old: str, new: str, label: str) -> None:
    global h
    count = h.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    h = h.replace(old, new, 1)


h = h.replace('6.0.31', '6.0.32')

css = '''
.voice-recording{display:flex;align-items:center;gap:9px;background:var(--card);border-top:1px solid var(--line);padding:8px 10px;color:var(--ink)}
.voice-recording .voice-dot{width:10px;height:10px;border-radius:50%;background:var(--red);box-shadow:0 0 0 5px color-mix(in srgb,var(--red) 18%,transparent);animation:voicePulse 1.15s infinite}
.voice-recording .voice-time{flex:1;font-weight:900;font-variant-numeric:tabular-nums}.voice-recording button{border:0;border-radius:12px;padding:9px 11px;font-weight:850}.voice-cancel{background:color-mix(in srgb,var(--card) 80%,var(--red) 20%);color:var(--red)}.voice-send{background:var(--blue);color:#fff}
#micBtn.recording{background:color-mix(in srgb,var(--card) 72%,var(--red) 28%);color:var(--red)}
.attachment-audio{display:block;width:min(290px,72vw);height:42px}.composer button:disabled,.composer textarea:disabled{opacity:.48}
@keyframes voicePulse{0%,100%{transform:scale(.82);opacity:.72}50%{transform:scale(1.08);opacity:1}}
'''
if css.strip() not in h:
    h = h.replace('</style>', css + '</style>', 1)

old = '''  <div id="replyDraft" class="reply-draft hidden"><div id="replyDraftText" class="reply-reference"></div><button id="cancelReplyBtn" aria-label="Отменить ответ">×</button></div>
  <div class="composer">
    <button id="attachBtn" class="mini" aria-label="Вложение">＋</button>
    <button id="emojiBtn" class="mini" aria-label="Смайлы">😊</button>
    <textarea id="messageInput" rows="1" placeholder="Сообщение…"></textarea>
    <button id="sendBtn" class="send">➤</button>
  </div>
  <input id="photoInput" type="file" accept="image/*" class="hidden">
  <input id="videoInput" type="file" accept="video/*" class="hidden">
  <input id="fileInput" type="file" class="hidden">
  <div id="attachMenu" class="attach-menu hidden">
    <button id="pickPhotoBtn">🖼️ Фото из галереи</button>
    <button id="pickVideoBtn">🎬 Видео с телефона</button>
    <button id="pickFileBtn">📎 Файл с телефона</button>
  </div>'''
new = '''  <div id="replyDraft" class="reply-draft hidden"><div id="replyDraftText" class="reply-reference"></div><button id="cancelReplyBtn" aria-label="Отменить ответ">×</button></div>
  <div id="voiceRecordingBar" class="voice-recording hidden">
    <span class="voice-dot"></span><span id="voiceTimer" class="voice-time">00:00</span>
    <button id="cancelVoiceBtn" class="voice-cancel">Отмена</button>
    <button id="sendVoiceBtn" class="voice-send">Отправить</button>
  </div>
  <div class="composer">
    <button id="attachBtn" class="mini" aria-label="Вложение">＋</button>
    <button id="emojiBtn" class="mini" aria-label="Смайлы">😊</button>
    <textarea id="messageInput" rows="1" placeholder="Сообщение…"></textarea>
    <button id="micBtn" class="mini" aria-label="Голосовое сообщение">🎤</button>
    <button id="sendBtn" class="send">➤</button>
  </div>
  <input id="photoInput" type="file" accept="image/*" class="hidden">
  <input id="videoInput" type="file" accept="video/*" class="hidden">
  <input id="chatCaptureVideoInput" type="file" accept="video/*" capture="environment" class="hidden">
  <input id="fileInput" type="file" class="hidden">
  <div id="attachMenu" class="attach-menu hidden">
    <button id="pickPhotoBtn">🖼️ Фото из галереи</button>
    <button id="captureChatVideoBtn">🎥 Снять видео сейчас</button>
    <button id="pickVideoBtn">🎬 Видео из галереи</button>
    <button id="pickFileBtn">📎 Файл с телефона</button>
  </div>'''
once(old, new, 'chat composer')

old = "var uiSettings={theme:'ocean',videoLayout:'pip'},attachmentKeys={},attachmentDbPromise=null;"
new = "var uiSettings={theme:'ocean',videoLayout:'pip'},attachmentKeys={},attachmentDbPromise=null;\nvar voiceRecorder=null,voiceStream=null,voiceChunks=[],voiceStartedAt=0,voiceTimerHandle=null,voiceSendOnStop=false,voiceContext='';\nvar VOICE_MAX_MS=300000;"
once(old, new, 'voice globals')

old = """   }else if((att.mime||'').startsWith('video/')){
     var video=document.createElement('video');video.className='attachment-video';video.src=url;video.controls=true;video.playsInline=true;video.preload='metadata';
     video.addEventListener('loadedmetadata',function(){scrollChatToBottom();});card.appendChild(video);
   }else{"""
new = """   }else if((att.mime||'').startsWith('video/')){
     var video=document.createElement('video');video.className='attachment-video';video.src=url;video.controls=true;video.playsInline=true;video.preload='metadata';
     video.addEventListener('loadedmetadata',function(){scrollChatToBottom();});card.appendChild(video);
   }else if((att.mime||'').startsWith('audio/')){
     var audio=document.createElement('audio');audio.className='attachment-audio';audio.src=url;audio.controls=true;audio.preload='metadata';
     audio.addEventListener('loadedmetadata',function(){scrollChatToBottom();});card.appendChild(audio);
   }else{"""
once(old, new, 'audio attachment renderer')

old = "function messagePreview(m){if(!m)return '';if(m.text)return m.text;if(m.attachment)return ((m.attachment.mime||'').startsWith('video/')?'🎬 ':'📎 ')+(m.attachment.name||'Вложение');return 'Сообщение';}"
new = "function messagePreview(m){if(!m)return '';if(m.text)return m.text;if(m.attachment){var mt=m.attachment.mime||'';if(mt.startsWith('audio/'))return '🎤 Голосовое сообщение';if(mt.startsWith('video/'))return '🎬 '+(m.attachment.name||'Видео');return '📎 '+(m.attachment.name||'Вложение');}return 'Сообщение';}"
once(old, new, 'message preview')

marker = 'async function sendAttachment(file,isPhoto){'
if h.count(marker) != 1:
    raise SystemExit('sendAttachment marker mismatch')
voice_js = r'''
function voiceMimeType(){
 if(!window.MediaRecorder)return '';
 var candidates=['audio/webm;codecs=opus','audio/webm','audio/mp4'];
 for(var i=0;i<candidates.length;i++)try{if(MediaRecorder.isTypeSupported(candidates[i]))return candidates[i];}catch(e){}
 return '';
}
function setVoiceUi(active){
 $('voiceRecordingBar').classList.toggle('hidden',!active);$('micBtn').classList.toggle('recording',active);
 ['attachBtn','emojiBtn','messageInput','sendBtn'].forEach(function(id){$(id).disabled=!!active;});
}
function stopVoiceTracks(){
 if(voiceStream)try{voiceStream.getTracks().forEach(function(t){t.stop();});}catch(e){}
 voiceStream=null;
}
function cleanupVoiceUi(){
 clearInterval(voiceTimerHandle);voiceTimerHandle=null;stopVoiceTracks();setVoiceUi(false);voiceRecorder=null;voiceChunks=[];voiceStartedAt=0;voiceSendOnStop=false;voiceContext='';
}
function updateVoiceTimer(){
 if(!voiceStartedAt)return;
 var elapsed=Math.max(0,Date.now()-voiceStartedAt),sec=Math.floor(elapsed/1000),mm=String(Math.floor(sec/60)).padStart(2,'0'),ss=String(sec%60).padStart(2,'0');
 $('voiceTimer').textContent=mm+':'+ss+' / 05:00';
 if(elapsed>=VOICE_MAX_MS)stopVoiceRecording(true);
}
async function startVoiceRecording(){
 if(!currentThread)return;
 if(voiceRecorder)return;
 if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia||!window.MediaRecorder){toast('На этом телефоне запись голосовых не поддерживается');return;}
 try{
   $('attachMenu').classList.add('hidden');$('emojiPanel').classList.add('hidden');
   var stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true},video:false});
   var mime=voiceMimeType(),opts={audioBitsPerSecond:32000};if(mime)opts.mimeType=mime;
   var rec;try{rec=new MediaRecorder(stream,opts);}catch(e){rec=new MediaRecorder(stream);}
   voiceStream=stream;voiceRecorder=rec;voiceChunks=[];voiceContext=attachmentContextForThread(currentThread);voiceSendOnStop=false;
   rec.ondataavailable=function(e){if(e.data&&e.data.size)voiceChunks.push(e.data);};
   rec.onerror=function(){toast('Ошибка записи голосового сообщения');stopVoiceRecording(false);};
   rec.onstop=function(){
     var chunks=voiceChunks.slice(),send=voiceSendOnStop,ctx=voiceContext,type=rec.mimeType||mime||'audio/webm';
     clearInterval(voiceTimerHandle);voiceTimerHandle=null;stopVoiceTracks();setVoiceUi(false);voiceRecorder=null;voiceChunks=[];voiceStartedAt=0;voiceSendOnStop=false;voiceContext='';
     if(!send||!chunks.length)return;
     if(!currentThread||attachmentContextForThread(currentThread)!==ctx){toast('Голосовое отменено: открыт другой чат');return;}
     try{
       var ext=type.indexOf('mp4')>=0?'m4a':'webm',blob=new Blob(chunks,{type:type}),file=new File([blob],'voice-'+Date.now()+'.'+ext,{type:type,lastModified:Date.now()});
       sendAttachment(file,false);
     }catch(e){toast('Не удалось подготовить голосовое сообщение');}
   };
   rec.start(250);voiceStartedAt=Date.now();setVoiceUi(true);updateVoiceTimer();voiceTimerHandle=setInterval(updateVoiceTimer,500);
 }catch(e){cleanupVoiceUi();toast('Нет доступа к микрофону. Разрешите микрофон для «Наша семья»');}
}
function stopVoiceRecording(send){
 if(!voiceRecorder)return;
 voiceSendOnStop=!!send;
 try{if(voiceRecorder.state==='inactive'){var f=voiceRecorder.onstop;if(f)f();}else voiceRecorder.stop();}catch(e){cleanupVoiceUi();}
}

'''
h = h.replace(marker, voice_js + marker, 1)

old = "   toast('Подготавливаю '+(isPhoto?'фото':((file.type||'').startsWith('video/')?'видео':'файл'))+'…');"
new = "   var ft=file.type||'',kindLabel=isPhoto?'фото':(ft.startsWith('audio/')?'голосовое сообщение':(ft.startsWith('video/')?'видео':'файл'));\n   toast('Подготавливаю '+kindLabel+'…');"
once(old, new, 'attachment status label')

old = """$('pickPhotoBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('photoInput').click();});
$('pickVideoBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('videoInput').click();});
$('pickFileBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('fileInput').click();});
$('photoInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,true);});
$('videoInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,false);});
$('fileInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,false);});"""
new = """$('pickPhotoBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('photoInput').click();});
$('captureChatVideoBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('chatCaptureVideoInput').click();});
$('pickVideoBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('videoInput').click();});
$('pickFileBtn').addEventListener('click',function(){$('attachMenu').classList.add('hidden');$('fileInput').click();});
$('photoInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,true);});
$('chatCaptureVideoInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,false);});
$('videoInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,false);});
$('fileInput').addEventListener('change',function(){var f=this.files&&this.files[0];this.value='';if(f)sendAttachment(f,false);});
$('micBtn').addEventListener('click',function(){if(voiceRecorder)stopVoiceRecording(true);else startVoiceRecording();});
$('cancelVoiceBtn').addEventListener('click',function(){stopVoiceRecording(false);});
$('sendVoiceBtn').addEventListener('click',function(){stopVoiceRecording(true);});"""
once(old, new, 'voice and capture listeners')

p.write_text(h, encoding='utf-8')

g = Path('duoapp/build.gradle')
s = g.read_text(encoding='utf-8').replace('versionCode 6031', 'versionCode 6032').replace("versionName '6.0.31'", "versionName '6.0.32'")
if 'versionCode 6032' not in s or "versionName '6.0.32'" not in s:
    raise SystemExit('Gradle version update failed')
g.write_text(s, encoding='utf-8')

m = Path('duoapp/src/main/AndroidManifest.xml')
s = m.read_text(encoding='utf-8').replace('android:label="Наша семья 6.0.31"', 'android:label="Наша семья 6.0.32"')
if 'android:label="Наша семья 6.0.32"' not in s:
    raise SystemExit('Manifest version update failed')
m.write_text(s, encoding='utf-8')

r = Path('duoapp/src/main/java/com/sergey/duochat/NativeRelayTransport.java')
s = r.read_text(encoding='utf-8').replace('OurFamily/6.0.31 Android', 'OurFamily/6.0.32 Android')
r.write_text(s, encoding='utf-8')

print('Patched OurFamily 6.0.32 voice messages and instant video capture')
