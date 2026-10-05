#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
import base64, hashlib, json, pathlib
for name, path in [('old', 'updates/candidates/607.json'), ('new', 'updates/candidates/608.json')]:
    meta = json.loads(pathlib.Path(path).read_text())
    parts = [pathlib.Path('updates/parts') / url.rsplit('/', 1)[-1] for url in meta['apkBase64Parts']]
    apk = base64.b64decode(''.join(p.read_text() for p in parts))
    assert hashlib.sha256(apk).hexdigest() == meta['sha256']
    pathlib.Path('/tmp/ourfamily-' + name + '.apk').write_bytes(apk)
PY

pkg=com.sergey.ourfamily
activity=com.sergey.duochat.MainActivity

# The release must install over the exact stable 6.0.7 package without deleting data.
adb install -r /tmp/ourfamily-old.apk
adb install -r /tmp/ourfamily-new.apk
test "$(adb shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')" = 6008

check_launch() {
  for permission in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do
    adb shell pm grant "$pkg" "$permission" || true
  done
  adb shell am force-stop "$pkg" || true
  adb logcat -c
  adb shell am start -W -n "$pkg/$activity"
  sleep 8
  if ! adb shell pidof "$pkg" >/dev/null; then
    adb logcat -d -b crash | tail -100
    echo 'App process died after launch' >&2
    exit 1
  fi
  if ! adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | grep -q "$pkg"; then
    adb shell dumpsys activity activities | grep -E 'ResumedActivity|mCurrentFocus' | tail -10
    adb logcat -d -b crash | tail -100
    echo 'Main activity is not resumed' >&2
    exit 1
  fi
  if adb logcat -d -b crash | grep -A 3 'FATAL EXCEPTION' | grep -q "Process: $pkg"; then
    adb logcat -d -b crash | tail -100
    exit 1
  fi
}

check_launch
adb shell pm clear "$pkg"
check_launch
adb exec-out screencap -p > /tmp/family-screen.png
adb shell uiautomator dump /sdcard/family-window.xml >/dev/null || true
adb pull /sdcard/family-window.xml /tmp/family-window.xml >/dev/null || true
echo 'Upgrade and clean-start smoke tests passed'
