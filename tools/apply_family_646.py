from pathlib import Path
import re

H=Path('duoapp/src/main/assets/index.html')
A=Path('duoapp/src/main/assets/arkanoid.js')
G=Path('duoapp/build.gradle')
M=Path('duoapp/src/main/AndroidManifest.xml')
D=Path('tools/design646.css')
h=H.read_text(encoding='utf-8')
a=A.read_text(encoding='utf-8')
g=G.read_text(encoding='utf-8')
m=M.read_text(encoding='utf-8')

if "APP_VERSION='6.0.46'" not in h:
    if 'versionCode 6045' not in g or "versionName '6.0.45'" not in g:
        raise SystemExit('Expected 6.0.45 Gradle baseline not found')
    g=g.replace('versionCode 6045','versionCode 6046',1).replace("versionName '6.0.45'","versionName '6.0.46'",1)
    m=m.replace('6.0.45','6.0.46')
    h=h.replace('6.0.45','6.0.46')

# Four permanent game cards. Arkanoid used to be injected after the hub opened.
if 'id="hubArkanoidBtn"' not in h:
    pat=re.compile(r'(<button id="hubPoolBtn" class="game-card pool-card">.*?</button>)(</div>\s*<div class="section-title">Текущие партии)',re.S)
    ark='<button id="hubArkanoidBtn" class="game-card arkanoid-card"><span class="emoji">🧱</span><strong>Арканоид</strong><span>Одиночная игра · уровни · бонусы</span></button>'
    h,n=pat.subn(r'\1'+ark+r'\2',h,count=1)
    if n!=1: raise SystemExit('Games hub anchor not found')

old_ark=""" var grid=doc.querySelector('#gamesHub .game-grid');
 if(grid&&!doc.getElementById('hubArkanoidBtn')){
  var b=doc.createElement('button');b.id='hubArkanoidBtn';b.className='game-card arkanoid-card';b.innerHTML='<span class=\"emoji\">🧱</span><strong>Арканоид</strong><span>Одиночная игра · уровни · бонусы</span>';grid.appendChild(b);b.addEventListener('click',openGame);
 }
"""
new_ark=""" var grid=doc.querySelector('#gamesHub .game-grid'),hub=doc.getElementById('hubArkanoidBtn');
 if(grid&&!hub){
  hub=doc.createElement('button');hub.id='hubArkanoidBtn';hub.className='game-card arkanoid-card';hub.innerHTML='<span class=\"emoji\">🧱</span><strong>Арканоид</strong><span>Одиночная игра · уровни · бонусы</span>';grid.appendChild(hub);
 }
 if(hub&&hub.dataset.arkanoidBound!=='1'){hub.dataset.arkanoidBound='1';hub.addEventListener('click',openGame);}
"""
if old_ark in a:
    a=a.replace(old_ark,new_ark,1)
elif "hub.dataset.arkanoidBound==='1'" not in a:
    raise SystemExit('Arkanoid hub binding anchor not found')

# Coalesce repeated chat auto-scroll operations.
old_scroll="""function scrollChatToBottom(){
 var box=$('messages');if(!box||$('chat').classList.contains('hidden'))return;
 function go(){try{box.scrollTop=box.scrollHeight;box.scrollTo({top:box.scrollHeight,behavior:'auto'});}catch(e){box.scrollTop=box.scrollHeight;}}
 go();requestAnimationFrame(function(){go();requestAnimationFrame(go);});setTimeout(go,60);setTimeout(go,180);setTimeout(go,420);
}
"""
new_scroll="""var chatScrollGeneration=0;
function scrollChatToBottom(){
 var box=$('messages');if(!box||$('chat').classList.contains('hidden'))return;
 var generation=++chatScrollGeneration;
 function go(){if(generation!==chatScrollGeneration||!box.isConnected)return;try{box.scrollTop=box.scrollHeight;box.scrollTo({top:box.scrollHeight,behavior:'auto'});}catch(e){box.scrollTop=box.scrollHeight;}}
 go();requestAnimationFrame(go);setTimeout(go,120);
}
"""
if old_scroll in h:
    h=h.replace(old_scroll,new_scroll,1)
elif 'var chatScrollGeneration=0;' not in h:
    raise SystemExit('Chat scroll anchor not found')

# Compress photos earlier; video calls and video attachments are not altered.
old_photo="""async function compressPhoto(file){
 var limit=maxAttachmentSize();if(file.size<=limit)return file;
 try{
   var url=URL.createObjectURL(file),img=new Image();
   await new Promise(function(resolve,reject){img.onload=resolve;img.onerror=reject;img.src=url;});
   var scale=Math.min(1,1600/Math.max(img.width,img.height)),c=document.createElement('canvas');
   c.width=Math.max(1,Math.round(img.width*scale));c.height=Math.max(1,Math.round(img.height*scale));
   c.getContext('2d').drawImage(img,0,0,c.width,c.height);URL.revokeObjectURL(url);
   var blob=await new Promise(function(resolve){c.toBlob(resolve,'image/jpeg',.78);});
   if(blob&&blob.size<=limit)return new File([blob],(file.name||'photo').replace(/\\.[^.]+$/,'')+'.jpg',{type:'image/jpeg'});
 }catch(e){}
 return file;
}
"""
new_photo="""function photoUploadTargetSize(){
 var host='';try{host=(new URL(relayBase)).hostname;}catch(e){}
 var conn=navigator.connection||navigator.mozConnection||navigator.webkitConnection||{};
 if(conn.saveData||/2g/i.test(String(conn.effectiveType||'')))return 1150000;
 if(/3g/i.test(String(conn.effectiveType||'')))return 1650000;
 return /(^|\\.)ntfy\\.sh$/i.test(host)?1350000:2400000;
}
async function compressPhoto(file){
 var hard=maxAttachmentSize(),target=Math.min(hard,photoUploadTargetSize());if(file.size<=target)return file;
 var url='';
 try{
   url=URL.createObjectURL(file);var img=new Image();
   await new Promise(function(resolve,reject){img.onload=resolve;img.onerror=reject;img.src=url;});
   var maxSide=1920,scale=Math.min(1,maxSide/Math.max(img.width,img.height)),c=document.createElement('canvas');
   c.width=Math.max(1,Math.round(img.width*scale));c.height=Math.max(1,Math.round(img.height*scale));
   c.getContext('2d',{alpha:false}).drawImage(img,0,0,c.width,c.height);
   var blob=await new Promise(function(resolve){c.toBlob(resolve,'image/jpeg',.82);});
   if(blob&&blob.size>target&&Math.max(c.width,c.height)>1600){
     var scale2=1600/Math.max(c.width,c.height),c2=document.createElement('canvas');c2.width=Math.max(1,Math.round(c.width*scale2));c2.height=Math.max(1,Math.round(c.height*scale2));c2.getContext('2d',{alpha:false}).drawImage(c,0,0,c2.width,c2.height);blob=await new Promise(function(resolve){c2.toBlob(resolve,'image/jpeg',.74);});
   }
   if(blob&&blob.size<file.size&&blob.size<=hard)return new File([blob],(file.name||'photo').replace(/\\.[^.]+$/,'')+'.jpg',{type:'image/jpeg'});
 }catch(e){}finally{if(url)try{URL.revokeObjectURL(url);}catch(_e){}}
 return file;
}
"""
if old_photo in h:
    h=h.replace(old_photo,new_photo,1)
elif 'function photoUploadTargetSize()' not in h:
    raise SystemExit('Photo compression anchor not found')

if 'id="design646"' not in h:
    css=D.read_text(encoding='utf-8').strip()
    if '</head>' not in h: raise SystemExit('head close not found')
    h=h.replace('</head>','<style id="design646">\n'+css+'\n</style>\n</head>',1)

H.write_text(h,encoding='utf-8')
A.write_text(a,encoding='utf-8')
G.write_text(g,encoding='utf-8')
M.write_text(m,encoding='utf-8')
print('6.0.46 polish applied')
