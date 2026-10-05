#!/usr/bin/env bash
set -euo pipefail
adb install -r /tmp/family621.apk
for permission in CAMERA RECORD_AUDIO POST_NOTIFICATIONS; do adb shell pm grant com.sergey.ourfamily "android.permission.$permission"; done
adb shell am start -n com.sergey.ourfamily/com.sergey.duochat.MainActivity
sleep 3
adb install -r /tmp/family622.apk
adb shell am force-stop com.sergey.ourfamily
adb shell input keyevent 3
adb shell am start -n com.sergey.ourfamily/com.sergey.duochat.MainActivity
sleep 5
adb shell dumpsys package com.sergey.ourfamily > "/tmp/package622-api${FAMILY_API_LEVEL}.txt"
adb logcat -d -v brief > "/tmp/upgrade622-api${FAMILY_API_LEVEL}.log"
grep 'versionCode=' "/tmp/package622-api${FAMILY_API_LEVEL}.txt" | head -1
adb shell pidof com.sergey.ourfamily || true

if grep -E 'FATAL EXCEPTION|Fatal signal' "/tmp/upgrade622-api${FAMILY_API_LEVEL}.log" | grep -q 'com.sergey.ourfamily'; then exit 1; fi
adb shell screencap -p /sdcard/upgrade622.png
adb pull /sdcard/upgrade622.png "/tmp/upgrade622-api${FAMILY_API_LEVEL}.png"
grep -q 'versionCode=6022' "/tmp/package622-api${FAMILY_API_LEVEL}.txt"
adb shell pidof com.sergey.ourfamily
