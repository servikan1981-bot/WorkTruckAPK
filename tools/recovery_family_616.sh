#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import base64,hashlib,json,pathlib
for label,num in [('old','615'),('new','616')]:
    m=json.loads(pathlib.Path('updates/candidates/'+num+'.json').read_text())
    data=base64.b64decode(''.join(pathlib.Path('updates/parts/'+u.rsplit('/',1)[-1]).read_text() for u in m['apkBase64Parts']))
    assert hashlib.sha256(data).hexdigest()==m['sha256']
    pathlib.Path('/tmp/family-'+label+'.apk').write_bytes(data)
PY
pkg=com.sergey.ourfamily
act=com.sergey.duochat.MainActivity
adb install -r /tmp/family-old.apk
for permission in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do adb shell pm grant "$pkg" "$permission" || true; done
python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import enter_profile
enter_profile('Сергей')
PY
adb install -r /tmp/family-new.apk
test "$(adb shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')" = 6016
adb shell am force-stop "$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/$act"
sleep 6
adb shell input tap 154 414
if [ "${FAMILY_API_LEVEL:-0}" -ge 34 ]; then
  adb exec-out screencap -p > /tmp/family-616.png
  test -n "$(adb shell pidof "$pkg")"
else
  python3 - <<'PY'
import sys,time,xml.etree.ElementTree as ET
sys.path.insert(0,'tools')
from e2e_family_610 import tap,snapshot,adb,enter_profile
tap('Новый чат')
tap('Света')
if 'Написать' in ET.tostring(snapshot(),encoding='unicode'):tap('Написать')
tap('Пригласить в шашки')
for i in range(10):
    root=ET.tostring(snapshot(),encoding='unicode')
    if 'Ждём ответа' in root:break
    time.sleep(2)
assert 'Ждём ответа' in root,root[:3000]
adb('shell','pm','clear','com.sergey.ourfamily')
for permission in ['android.permission.CAMERA','android.permission.RECORD_AUDIO','android.permission.POST_NOTIFICATIONS']:
    adb('shell','pm','grant','com.sergey.ourfamily',permission)
enter_profile('Света')
for i in range(20):
    root=ET.tostring(snapshot(),encoding='unicode')
    if 'приглашает вас в шашки' in root:break
    time.sleep(3)
assert 'приглашает вас в шашки' in root,root[:3000]
tap('Принять')
root=ET.tostring(snapshot(),encoding='unicode')
assert 'Шашки · Сергей' in root,root[:3000]
print('PASS: upgrade 6.0.15 -> 6.0.16, Sergey invites Sveta, Sveta accepts')
PY
  adb exec-out screencap -p > /tmp/family-616.png
fi
adb logcat -d -b crash > /tmp/family-616-crash.log
if grep -A 4 'FATAL EXCEPTION' /tmp/family-616-crash.log | grep -q "Process: $pkg"; then cat /tmp/family-616-crash.log; exit 1; fi
test -n "$(adb shell pidof "$pkg")"
