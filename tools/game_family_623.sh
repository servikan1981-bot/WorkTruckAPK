#!/usr/bin/env bash
set -euo pipefail
for host in https://family.familysergey.netcraze.pro https://our-family-relay.family-860c7981b2d4.workers.dev; do
 echo "Relay check: $host"
 curl -L --max-time 8 -sS -w ' HTTP %{http_code}\n' "$host/health" || true
done
python3 - <<'PY'
import base64,hashlib,json,pathlib
for version in (622,623):
 meta=json.loads(pathlib.Path(f'updates/{"candidates/623" if version==623 else "candidates/622"}.json').read_text())
 assert meta['versionCode']=={622:6022,623:6023}[version]
 data=base64.b64decode(''.join(pathlib.Path('updates/parts/'+u.rsplit('/',1)[-1]).read_text() for u in meta['apkBase64Parts']))
 assert hashlib.sha256(data).hexdigest()==meta['sha256']
 pathlib.Path(f'/tmp/family-{version}.apk').write_bytes(data)
PY
for v in 622 623; do
 "$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -v 4 "/tmp/family-$v.apk" >/dev/null
 "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "/tmp/family-$v.apk" >/dev/null
done
echo no | avdmanager create avd -n family-second -k 'system-images;android-33;google_apis;x86_64' --force
"$ANDROID_HOME/emulator/emulator" -avd family-second -port 5556 -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot >/tmp/second-emulator.log 2>&1 &
adb -s emulator-5556 wait-for-device
for attempt in $(seq 1 80); do
 if [ "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
 sleep 2
done
test "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1
for serial in emulator-5554 emulator-5556; do
 adb -s "$serial" install -r /tmp/family-622.apk
 # Start from a clean test-only profile. This does not affect any real phone.
 adb -s "$serial" shell pm clear com.sergey.ourfamily >/dev/null || true
 for permission in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do
  adb -s "$serial" shell pm grant com.sergey.ourfamily "$permission" || true
 done
 adb -s "$serial" shell am force-stop com.sergey.ourfamily || true
 adb -s "$serial" shell am start -W -n com.sergey.ourfamily/com.sergey.duochat.MainActivity >/tmp/${serial}-622-start.txt
 # The old 6.0.19 sometimes returns to Launcher during cold WebView startup on CI.
 # Retry the launch before the profile helper begins tapping the role selector.
 for attempt in $(seq 1 8); do
  if adb -s "$serial" shell dumpsys window windows | grep -q 'com.sergey.ourfamily'; then break; fi
  sleep 2
  adb -s "$serial" shell am start -W -n com.sergey.ourfamily/com.sergey.duochat.MainActivity >/dev/null || true
 done
 sleep 2
done
python3 tools/game_family_623.py
