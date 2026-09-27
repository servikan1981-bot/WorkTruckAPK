#!/usr/bin/env bash
set -euo pipefail

unsigned=/tmp/family624/duoapp-release-unsigned.apk
test -s "$unsigned"
# A temporary test certificate is deliberately separate from the family's release key.
keytool -genkeypair -keystore /tmp/family624-test.p12 -storetype PKCS12 \
  -storepass testpassword -keypass testpassword -alias family-test \
  -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=OurFamily Test' >/dev/null 2>&1
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f -p 4 "$unsigned" /tmp/family624-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign \
  --ks /tmp/family624-test.p12 --ks-type PKCS12 \
  --ks-pass pass:testpassword --key-pass pass:testpassword \
  --out /tmp/family-video.apk /tmp/family624-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify /tmp/family-video.apk

echo no | avdmanager create avd -n family-second -k 'system-images;android-35;google_apis;x86_64' --force
"$ANDROID_HOME/emulator/emulator" -avd family-second -port 5556 -no-window \
  -gpu swiftshader_indirect -noaudio -no-boot-anim -no-snapshot \
  -camera-front emulated -camera-back emulated >/tmp/second-emulator.log 2>&1 &
adb -s emulator-5556 wait-for-device
for attempt in $(seq 1 80); do
  if [ "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
  sleep 2
done
test "$(adb -s emulator-5556 shell getprop sys.boot_completed | tr -d '\r')" = 1
for serial in emulator-5554 emulator-5556; do
  adb -s "$serial" install -r /tmp/family-video.apk
  for permission in android.permission.CAMERA android.permission.RECORD_AUDIO android.permission.POST_NOTIFICATIONS; do
    adb -s "$serial" shell pm grant com.sergey.ourfamily "$permission" || true
  done
done
if [ "${FAMILY_E2E_SUITE:-video}" = game ]; then
  python3 tools/game_family_624.py
else
  python3 tools/video_family_624.py
fi
