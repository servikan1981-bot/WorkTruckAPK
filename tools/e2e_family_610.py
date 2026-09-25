"""Exercise a disposable Sergey -> Sveta message over the real relay on an emulator."""
import hashlib
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.request import Request, urlopen


PKG = "com.sergey.ourfamily"
CODE = "Family-smoke-" + os.environ.get("GITHUB_RUN_ID", "local")
TEXT = "relay-smoke-" + os.environ.get("GITHUB_RUN_ID", "local")
NEWS_TEXT = "family-news-" + os.environ.get("GITHUB_RUN_ID", "local")


def adb(*args, timeout=50):
    return subprocess.check_output(["adb", *args], timeout=timeout, text=True).strip()


def snapshot():
    adb("shell", "uiautomator", "dump", "/sdcard/family-e2e.xml")
    xml = adb("exec-out", "cat", "/sdcard/family-e2e.xml")
    Path("/tmp/family-e2e.xml").write_text(xml)
    return ET.fromstring(xml)


def tap(needle, *, optional=False):
    for attempt in range(3):
        root = snapshot()
        for node in root.iter("node"):
            text = node.get("text", "") + " " + node.get("content-desc", "")
            bounds = node.get("bounds", "")
            if needle in text and bounds:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
                if x2 > x1 and y2 > y1:
                    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    time.sleep(1)
                    return True
        adb("shell", "input", "swipe", "160", "520", "160", "210", "250")
    if optional:
        return False
    raise AssertionError("UI text not found: " + needle + "; XML: " + Path("/tmp/family-e2e.xml").read_text()[:4500])


def tap_input():
    for node in snapshot().iter("node"):
        if node.get("class") == "android.widget.EditText":
            bounds = node.get("bounds", "")
            if bounds:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
                if x2 > x1 and y2 > y1:
                    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    time.sleep(1)
                    return
    raise AssertionError("Visible input not found")


def enter_profile(role):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", PKG + "/com.sergey.duochat.MainActivity")
    time.sleep(5)
    tap("ПОЗЖЕ", optional=True)
    tap(role)
    tap_input()
    adb("shell", "input", "text", CODE)
    adb("shell", "input", "keyevent", "4")
    tap("Войти в семью")
    time.sleep(5)
    assert "Новый чат" in ET.tostring(snapshot(), encoding="unicode"), "Profile did not open"


def main():
    try:
        enter_profile("Сергей")
        tap("Света")
        tap_input()
        adb("shell", "input", "text", TEXT)
        adb("shell", "input", "keyevent", "4")
        tap("➤")
        time.sleep(8)
        root = ET.tostring(snapshot(), encoding="unicode")
        assert TEXT in root, "Outgoing message missing from chat"
        assert 'text="!"' not in root, "Android app reported relay publish failure"

        topic = "of5-" + hashlib.sha256(("OurFamily-v5-inbox|" + CODE + "|sveta").encode()).hexdigest()[:48]
        url = "https://our-family-relay.family-860c7981b2d4.workers.dev/" + topic + "/json?since=10m"
        request = Request(url, headers={"User-Agent": "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7)"})
        with urlopen(request, timeout=20) as response:
            received = response.read().decode()
        assert '"of5|' in received, "Relay did not store Android message: " + received[:500]

        tap("‹")
        tap("Семейные новости")
        tap_input()
        adb("shell", "input", "text", NEWS_TEXT)
        adb("shell", "input", "keyevent", "4")
        tap("➤")
        time.sleep(4)
        assert NEWS_TEXT in ET.tostring(snapshot(), encoding="unicode"), "Published family news missing from Sergey's feed"

        adb("shell", "pm", "clear", PKG)
        for permission in ["android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.POST_NOTIFICATIONS"]:
            subprocess.run(["adb", "shell", "pm", "grant", PKG, permission], capture_output=True)
        enter_profile("Света")
        tap("Сергей")
        for _ in range(8):
            time.sleep(3)
            if TEXT in ET.tostring(snapshot(), encoding="unicode"):
                print("PASS: encrypted message published by Sergey and displayed for Sveta")
                break
        else:
            raise AssertionError("Sveta did not receive the message from the family relay")
        tap("‹")
        tap("Семейные новости")
        for _ in range(12):
            time.sleep(3)
            if NEWS_TEXT in ET.tostring(snapshot(), encoding="unicode"):
                print("PASS: encrypted family news published by Sergey and displayed for Sveta")
                break
        else:
            raise AssertionError("Sveta did not receive the same family news from the shared feed")
    finally:
        try:
            Path("/tmp/family-e2e.png").write_bytes(subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=20))
        except Exception:
            pass


if __name__ == "__main__":
    main()
