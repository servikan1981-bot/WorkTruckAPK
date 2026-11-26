#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import base64,hashlib,json,pathlib
for version in ('616','617'):
    m=json.loads(pathlib.Path('updates/candidates/'+version+'.json').read_text())
    data=base64.b64decode(''.join(pathlib.Path('updates/parts/'+u.rsplit('/',1)[-1]).read_text() for u in m['apkBase64Parts']))
    assert hashlib.sha256(data).hexdigest()==m['sha256']
    pathlib.Path('/tmp/family-'+version+'.apk').write_bytes(data)
PY
pkg=com.sergey.ourfamily
adb install -r /tmp/family-616.apk
for permission in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do adb shell pm grant "$pkg" "$permission" || true; done
python3 - <<'PY'
import sys
sys.path.insert(0,'tools')
from e2e_family_610 import enter_profile
enter_profile('Сергей')
PY
adb install -r /tmp/family-617.apk
test "$(adb shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')" = 6017
adb shell am force-stop "$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/com.sergey.duochat.MainActivity"
sleep 7
adb logcat -d -b crash > /tmp/family-617-crash.log
if grep -A 4 'FATAL EXCEPTION' /tmp/family-617-crash.log | grep -q "Process: $pkg"; then cat /tmp/family-617-crash.log; exit 1; fi
test -n "$(adb shell pidof "$pkg")"
adb exec-out screencap -p > /tmp/family-617-upgrade.png
echo "PASS: Android ${FAMILY_API_LEVEL} upgrade 6.0.16 -> 6.0.17 launches"
