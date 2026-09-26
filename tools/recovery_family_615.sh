#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import base64,hashlib,json,pathlib
for label,candidate in [('old','614.json'),('new','615.json')]:
    m=json.loads((pathlib.Path('updates/candidates')/candidate).read_text())
    data=base64.b64decode(''.join((pathlib.Path('updates/parts')/u.rsplit('/',1)[-1]).read_text() for u in m['apkBase64Parts']))
    assert hashlib.sha256(data).hexdigest()==m['sha256']
    pathlib.Path('/tmp/family-'+label+'.apk').write_bytes(data)
PY
pkg=com.sergey.ourfamily
act=com.sergey.duochat.MainActivity
adb install -r /tmp/family-old.apk
for p in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do adb shell pm grant "$pkg" "$p" || true; done
python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import enter_profile
enter_profile('Сергей')
PY
adb install -r /tmp/family-new.apk
test "$(adb shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')" = 6015
if [ "${FAMILY_API_LEVEL:-0}" -ge 34 ]; then
  adb shell appops set "$pkg" USE_FULL_SCREEN_INTENT deny
fi
adb shell am force-stop "$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/$act"
sleep 8
adb logcat -d -b crash > /tmp/family-615-crash.log
if adb logcat -d -b crash | grep -A 4 'FATAL EXCEPTION' | grep -q "Process: $pkg"; then cat /tmp/family-615-crash.log; exit 1; fi
for attempt in $(seq 1 10); do
  if [ -n "$(adb shell pidof "$pkg")" ]; then break; fi
  sleep 2
done
test -n "$(adb shell pidof "$pkg")"
python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import tap
tap('ПОЗЖЕ', optional=True)
PY
if [ "${FAMILY_API_LEVEL:-0}" -ge 34 ]; then
  for attempt in $(seq 1 12); do
    adb shell uiautomator dump /sdcard/family-615.xml >/dev/null
    adb exec-out cat /sdcard/family-615.xml > /tmp/family-615.xml
    if grep -q 'Android требует отдельное разрешение' /tmp/family-615.xml; then break; fi
    sleep 2
  done
  adb exec-out screencap -p > /tmp/family-615-denied.png
  adb logcat -d -t 1500 > /tmp/family-615-denied-logcat.txt
  grep -q 'Android требует отдельное разрешение' /tmp/family-615.xml
  adb shell appops set "$pkg" USE_FULL_SCREEN_INTENT allow
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$pkg/$act"
  python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import tap
tap('ПОЗЖЕ', optional=True)
PY
fi
for attempt in $(seq 1 18); do
  adb shell uiautomator dump /sdcard/family-615.xml >/dev/null
  adb exec-out cat /sdcard/family-615.xml > /tmp/family-615.xml
  if grep -q 'ЦИТАТА ДНЯ' /tmp/family-615.xml && grep -Eq 'Forismatic|Викицитатник' /tmp/family-615.xml; then break; fi
  sleep 3
done
grep -q 'ЦИТАТА ДНЯ' /tmp/family-615.xml
grep -Eq 'Forismatic|Викицитатник' /tmp/family-615.xml
if grep -q 'Android требует отдельное разрешение' /tmp/family-615.xml; then
  echo 'Permission banner remained after enabling full-screen calls' >&2; exit 1
fi
adb shell uiautomator dump /sdcard/family-615.xml >/dev/null
adb exec-out cat /sdcard/family-615.xml > /tmp/family-615.xml
adb exec-out screencap -p > /tmp/family-615.png
adb logcat -d -t 1500 > /tmp/family-615-logcat.txt
if ! grep -q 'Света' /tmp/family-615.xml; then
  sleep 12
  adb shell uiautomator dump /sdcard/family-615.xml >/dev/null
  adb exec-out cat /sdcard/family-615.xml > /tmp/family-615.xml
fi
grep -q 'Света' /tmp/family-615.xml
python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import tap
tap('⋮')
PY
adb shell uiautomator dump /sdcard/family-615.xml >/dev/null
adb exec-out cat /sdcard/family-615.xml > /tmp/family-615-menu.xml
if grep -q 'Разрешение экрана звонка' /tmp/family-615-menu.xml; then
  echo 'Granted permission menu item remained visible' >&2; exit 1
fi
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/$act"
sleep 5
test -n "$(adb shell pidof "$pkg")"
if adb logcat -d -b crash | grep -A 4 'FATAL EXCEPTION' | grep -q "Process: $pkg"; then adb logcat -d -b crash > /tmp/family-615-crash.log; cat /tmp/family-615-crash.log; exit 1; fi
echo 'PASS: 6.0.14 upgraded to 6.0.15, permission prompt disappeared, and shared daily quote appeared'
