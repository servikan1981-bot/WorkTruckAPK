#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import base64,hashlib,json,pathlib
for label,candidate in [('old','613.json'),('new','614.json')]:
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
test "$(adb shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')" = 6014
adb shell am force-stop "$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/$act"
sleep 8
adb logcat -d -b crash > /tmp/family-614-crash.log
if adb logcat -d -b crash | grep -A 4 'FATAL EXCEPTION' | grep -q "Process: $pkg"; then cat /tmp/family-614-crash.log; exit 1; fi
adb shell pidof "$pkg"
python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import tap
tap('ПОЗЖЕ', optional=True)
PY
adb shell uiautomator dump /sdcard/family-613.xml >/dev/null
adb exec-out cat /sdcard/family-613.xml > /tmp/family-613.xml
adb exec-out screencap -p > /tmp/family-613.png
adb logcat -d -t 1500 > /tmp/family-614-logcat.txt
if ! grep -q 'Света' /tmp/family-613.xml; then
  sleep 12
  adb shell uiautomator dump /sdcard/family-613.xml >/dev/null
  adb exec-out cat /sdcard/family-613.xml > /tmp/family-613.xml
fi
grep -q 'Света' /tmp/family-613.xml
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/$act"
sleep 5
test -n "$(adb shell pidof "$pkg")"
if adb logcat -d -b crash | grep -A 4 'FATAL EXCEPTION' | grep -q "Process: $pkg"; then adb logcat -d -b crash > /tmp/family-614-crash.log; cat /tmp/family-614-crash.log; exit 1; fi
echo 'PASS: Android 6.0.12 upgraded over 6.0.11 and restored existing profile'
