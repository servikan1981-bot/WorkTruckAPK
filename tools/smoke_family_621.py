import base64, hashlib, json, os, re, subprocess, time, urllib.request, uuid, xml.etree.ElementTree as ET

PKG='com.sergey.ourfamily'
ACT=PKG+'/com.sergey.duochat.MainActivity'
CODE='FamilySmoke621-20260927'
RELAY='https://family.familysergey.netcraze.pro'


def adb(*args, timeout=40):
    return subprocess.check_output(['adb', *args], text=True, timeout=timeout, stderr=subprocess.STDOUT).strip()


def snap():
    for _ in range(5):
        try:
            adb('shell','uiautomator','dump','/sdcard/ui.xml')
            xml=adb('exec-out','cat','/sdcard/ui.xml')
            if '<hierarchy' in xml:
                return ET.fromstring(xml)
        except Exception:
            pass
        time.sleep(1)
    raise AssertionError('uiautomator dump unavailable')


def ui_text():
    return ET.tostring(snap(), encoding='unicode')


def tap(needle, optional=False):
    last=''
    for _ in range(8):
        root=snap(); last=ET.tostring(root,encoding='unicode')
        for node in root.iter('node'):
            label=(node.get('text','')+' '+node.get('content-desc','')).strip()
            if needle not in label: continue
            nums=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(nums)!=4: continue
            x1,y1,x2,y2=nums
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(0.8)
            return True
        time.sleep(1)
    if optional:return False
    raise AssertionError('missing '+needle+'; '+last[-1800:])


def setup_sveta():
    adb('shell','am','start','-W','-n',ACT); time.sleep(4)
    tap('ПОЗЖЕ',optional=True)
    tap('Света')
    root=snap()
    fields=[n for n in root.iter('node') if n.get('class')=='android.widget.EditText']
    assert fields, 'family code input missing'
    x1,y1,x2,y2=map(int,re.findall(r'\d+',fields[0].get('bounds','')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    adb('shell','input','text',CODE)
    adb('shell','input','keyevent','4')
    tap('Войти в семью')
    deadline=time.time()+20
    while time.time()<deadline:
        if 'Новый чат' in ui_text(): return
        time.sleep(1)
    raise AssertionError('home screen did not open')


def sha(s): return hashlib.sha256(s.encode()).hexdigest()
def tag(role): return sha('OurFamily-v5-tag|'+CODE+'|'+role)[:16]
def inbox(role): return 'of5-'+sha('OurFamily-v5-inbox|'+CODE+'|'+role)[:48]

def urgent_wire(msg_id,text):
    ts=int(time.time()*1000)
    enc=base64.urlsafe_b64encode(text.encode()).decode().rstrip('=')
    token=sha('OurFamily-v621-urgent|'+CODE+'|'+msg_id+'|'+str(ts)+'|'+enc+'|sergey|sveta')[:32]
    return 'of5urgent|'+tag('sergey')+'|'+tag('sveta')+'|'+msg_id+'|'+str(ts)+'|'+enc+'|'+token


def post_wire(wire):
    data=json.dumps({'topic':inbox('sveta'),'message':wire}).encode()
    req=urllib.request.Request(RELAY, data=data, headers={'Content-Type':'application/json','User-Agent':'OurFamily621Smoke'}, method='POST')
    with urllib.request.urlopen(req,timeout=25) as r:
        assert r.status==200, r.status
        return r.read()


def wait_contains(text, seconds=18):
    deadline=time.time()+seconds
    while time.time()<deadline:
        if text in ui_text(): return True
        time.sleep(0.7)
    return False


def main():
    old=os.environ['APK620']; new=os.environ['APK621']
    adb('install','-r',old)
    adb('install','-r',new)
    pkg=adb('shell','dumpsys','package',PKG)
    assert re.search(r'versionCode=6021\b',pkg), '6.0.21 not installed over 6.0.20'
    for p in ['android.permission.POST_NOTIFICATIONS','android.permission.CAMERA','android.permission.RECORD_AUDIO']:
        try: adb('shell','pm','grant',PKG,p)
        except Exception: pass
    setup_sveta()

    text1='Срочная проверка 6.0.21 — сообщение доставлено'
    first=uuid.uuid4().hex
    wire=urgent_wire(first,text1)
    post_wire(wire)
    assert wait_contains(text1), 'urgent message did not open while app visible'
    assert 'СРОЧНОЕ СООБЩЕНИЕ' in ui_text(), 'urgent full-screen title missing'
    tap('Скрыть срочное сообщение')
    time.sleep(1)
    assert text1 not in ui_text(), 'urgent screen did not close'

    # A dismissed urgent ID must not reopen if the relay redelivers it.
    post_wire(wire); time.sleep(3)
    assert text1 not in ui_text(), 'dismissed urgent message reopened'

    # Background delivery must at least produce a high priority notification;
    # opening the app must then show the full-screen message.
    adb('shell','input','keyevent','3'); time.sleep(1)
    text2='Фоновая срочная проверка 6.0.21'
    post_wire(urgent_wire(uuid.uuid4().hex,text2))
    time.sleep(5)
    notifications=adb('shell','dumpsys','notification','--noredact')
    assert text2 in notifications or 'СРОЧНО ОТ СЕРГЕЯ' in notifications, 'urgent notification missing in background'
    adb('shell','am','start','-W','-n',ACT); time.sleep(2)
    assert wait_contains(text2,12), 'pending urgent message did not open on app launch'
    print('PASS: 6.0.20 -> 6.0.21 upgrade and urgent foreground/background delivery')

if __name__=='__main__':
    main()
