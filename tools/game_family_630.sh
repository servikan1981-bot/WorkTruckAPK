#!/usr/bin/env bash
set -euo pipefail
# Full two-emulator verification: background recipient must still get game notifications.
APK=duoapp/build/outputs/apk/debug/duoapp-debug.apk
test -s "$APK"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK" | grep -q "versionCode='6030' versionName='6.0.30'"

echo no | avdmanager create avd -n family-second -k 'system-images;android-33;google_apis;x86_64' --force
"$ANDROID_HOME/emulator/emulator" -avd family-second -port 5556 -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot >/tmp/second-emulator.log 2>&1 &
adb -s emulator-5556 wait-for-device
for attempt in $(seq 1 90); do
  [ "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break
  sleep 2
done
test "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1

for serial in emulator-5554 emulator-5556; do
  adb -s "$serial" install -r "$APK"
  for permission in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do
    adb -s "$serial" shell pm grant com.sergey.ourfamily "$permission" || true
  done
done
python3 tools/game_family_630.py
