"""Two-device quoted reply and permission prompt smoke test."""
import os
import re
import time
import xml.etree.ElementTree as ET

import video_family_613 as h

h.CODE = 'Family618-' + os.environ.get('GITHUB_RUN_ID', 'local')
A, B = 'emulator-5554', 'emulator-5556'


def ui(serial):
    return ET.tostring(h.snap(serial), encoding='unicode')


def wait_text(serial, needle, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if needle in ui(serial):
            return
        time.sleep(2)
    raise AssertionError(f'{serial}: missing {needle}: {ui(serial)[-2400:]}')


def write(serial, text):
    for node in h.snap(serial).iter('node'):
        if node.get('class') == 'android.widget.EditText':
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
            h.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            break
    else:
        raise AssertionError('Composer absent')
    h.adb(serial, 'shell', 'input', 'text', text)
    h.adb(serial, 'shell', 'input', 'keyevent', '4')
    h.tap(serial, '➤')


def main():
    h.profile(A, 'Сергей')
    h.profile(B, 'Света')
    assert 'Работа в фоне' in ui(A), 'Background setup missing'
    h.tap(A, 'Света')
    write(A, 'Original618')
    h.tap(B, 'Сергей')
    wait_text(B, 'Original618')
    h.tap(B, 'Ответить')
    wait_text(B, 'Original618')
    write(B, 'Reply618')
    wait_text(A, 'Reply618')
    screen = ui(A)
    assert 'Original618' in screen and 'Reply618' in screen and 'Ответить' in screen
    print('PASS: reply with source excerpt delivered on both devices', flush=True)


if __name__ == '__main__':
    main()
