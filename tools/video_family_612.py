import os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path

PKG='com.sergey.ourfamily'
ACT=PKG+'/com.sergey.duochat.MainActivity'
CODE='FamilyVideoCheck20260925'

def adb(serial,*args,timeout=40):
    return subprocess.check_output(['adb','-s',serial,*args],text=True,timeout=timeout,stderr=subprocess.STDOUT).strip()

def snap(serial):
    adb(serial,'shell','uiautomator','dump','/sdcard/video-ui.xml')
    xml=adb(serial,'exec-out','cat','/sdcard/video-ui.xml')
    Path('/tmp/'+serial+'.xml').write_text(xml)
    return ET.fromstring(xml)

def tap(serial,needle,optional=False):
    for attempt in range(6):
        root=snap(serial)
        for node in root.iter('node'):
            label=node.get('text','')+' '+node.get('content-desc','')
            if needle not in label: continue
            nums=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(nums)!=4: continue
            x1,y1,x2,y2=nums
            adb(serial,'shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            time.sleep(1)
            return True
        time.sleep(2)
    if optional:return False
    raise AssertionError(serial+' missing '+needle+'; '+ET.tostring(root,encoding='unicode')[-1700:])

def profile(serial,role):
    adb(serial,'shell','am','start','-W','-n',ACT)
    time.sleep(5)
    tap(serial,'ПОЗЖЕ',optional=True)
    tap(serial,role)
    root=snap(serial)
    for node in root.iter('node'):
        if node.get('class')=='android.widget.EditText':
            x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds','')))
            adb(serial,'shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            break
    else:raise AssertionError('family code input missing')
    adb(serial,'shell','input','text',CODE)
    adb(serial,'shell','input','keyevent','4')
    tap(serial,'Войти в семью')
    time.sleep(5)
    assert 'Новый чат' in ET.tostring(snap(serial),encoding='unicode')

def main():
    a,b='emulator-5554','emulator-5556'
    for s,role in [(a,'Сергей'),(b,'Света')]:profile(s,role)
    tap(a,'Света')
    tap(a,'🎥')
    deadline=time.monotonic()+45
    while time.monotonic()<deadline:
        if tap(b,'Принять',optional=True):break
        time.sleep(2)
    else:raise AssertionError('Incoming video call did not arrive')
    deadline=time.monotonic()+50
    while time.monotonic()<deadline:
        xa=ET.tostring(snap(a),encoding='unicode')
        xb=ET.tostring(snap(b),encoding='unicode')
        if 'Разговор' in xa and 'Разговор' in xb:
            print('PASS: both peers established WebRTC video connection')
            return
        time.sleep(3)
    raise AssertionError('No media connection; caller='+xa[-1400:]+' callee='+xb[-1400:])

if __name__=='__main__':
    try:main()
    finally:
        for s in ['emulator-5554','emulator-5556']:
            try:Path('/tmp/'+s+'.png').write_bytes(subprocess.check_output(['adb','-s',s,'exec-out','screencap','-p'],timeout=20))
            except Exception:pass
            try:Path('/tmp/'+s+'-crash.log').write_text(adb(s,'logcat','-d','-b','crash'))
            except Exception:pass
