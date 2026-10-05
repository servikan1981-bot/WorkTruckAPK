#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import base64,hashlib,json,pathlib
m=json.loads(pathlib.Path('updates/candidates/613.json').read_text())
data=base64.b64decode(''.join((pathlib.Path('updates/parts')/u.rsplit('/',1)[-1]).read_text() for u in m['apkBase64Parts']))
assert hashlib.sha256(data).hexdigest()==m['sha256']
pathlib.Path('/tmp/family-video.apk').write_bytes(data)
PY
echo no | avdmanager create avd -n family-second -k "system-images;android-${FAMILY_API_LEVEL:-35};google_apis;x86_64" --force
"$ANDROID_HOME/emulator/emulator" -avd family-second -port 5556 -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot -camera-front emulated -camera-back emulated >/tmp/second-emulator.log 2>&1 &
adb -s emulator-5556 wait-for-device
for attempt in $(seq 1 80); do
  if [ "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
  sleep 2
done
test "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1
for s in emulator-5554 emulator-5556; do
  adb -s "$s" install -r /tmp/family-video.apk
  for p in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do adb -s "$s" shell pm grant com.sergey.ourfamily "$p" || true; done
done
python3 tools/diagnose_locked_call_613.py
