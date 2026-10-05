"""Measure first video frames and make a real dragged checkers move on two emulators."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

import video_family_613 as helper

helper.CODE = 'Family617-' + os.environ.get('GITHUB_RUN_ID', 'local')
A, B = 'emulator-5554', 'emulator-5556'


def text(serial):
    return ET.tostring(helper.snap(serial), encoding='unicode')


def center(serial, label):
    for node in helper.snap(serial).iter('node'):
        if label in (node.get('content-desc', '') + ' ' + node.get('text', '')):
            coords = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
            if len(coords) == 4:
                x1, y1, x2, y2 = coords
                return (x1 + x2) // 2, (y1 + y2) // 2
    raise AssertionError(f'{serial}: no cell {label}')


def phases(serial):
    log = helper.adb(serial, 'logcat', '-d', '-s', 'OurFamilyCall:I', '*:S')
    Path('/tmp/' + serial + '-call.log').write_text(log)
    return {phase: [int(v) for v in re.findall(r'OurFamilyCall: ' + phase + r' (\d+)', log)]
            for phase in ('accepted', 'media_ready', 'offer_sent', 'offer_received',
                          'offer_apply_start', 'remote_description', 'answer_local_description',
                          'answer_sent', 'answer_received', 'connected', 'remote_frame')}


def main():
    helper.profile(A, 'Сергей')
    helper.profile(B, 'Света')
    helper.tap(A, 'Света')
    helper.tap(A, '🎥')
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if helper.tap(B, 'Принять', optional=True):
            break
        time.sleep(1)
    else:
        raise AssertionError('Video invitation did not arrive')
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        caller, callee = phases(A), phases(B)
        if caller['remote_frame'] and callee['remote_frame'] and callee['accepted']:
            ms = (caller['remote_frame'][-1] - callee['accepted'][-1],
                  callee['remote_frame'][-1] - callee['accepted'][-1])
            print(f'VIDEO accept-to-first-frame caller={ms[0]}ms callee={ms[1]}ms', flush=True)
            print('CALLER PHASES', caller, 'CALLEE PHASES', callee, flush=True)
            video_slow = not all(0 <= value < 5000 for value in ms)
            break
        time.sleep(1)
    else:
        raise AssertionError('Remote video frame missing: ' + str((caller, callee)))

    helper.tap(A, '✕')
    time.sleep(2)
    helper.tap(A, 'Пригласить в шашки')
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        if helper.tap(B, 'Принять', optional=True):
            break
        time.sleep(1)
    else:
        raise AssertionError('Checkers invitation did not arrive')
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        if 'Ваш ход' in text(A):
            break
        time.sleep(1)
    else:
        raise AssertionError('Sergey did not receive checkers acceptance')
    x1, y1 = center(A, 'Клетка 2, 6 шашка чёрная')
    x2, y2 = center(A, 'Клетка 1, 5')
    helper.adb(A, 'shell', 'input', 'swipe', str(x1), str(y1), str(x2), str(y2), '500')
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        if 'Клетка 1, 5 шашка чёрная' in text(B):
            print('CHECKERS dragged black piece and received move on Sveta device', flush=True)
            assert not video_slow, 'Video first frame exceeded five seconds'
            return
        time.sleep(1)
    raise AssertionError('Dragged move did not reach Sveta: ' + text(B)[-2000:])


if __name__ == '__main__':
    try:
        main()
    finally:
        for serial in (A, B):
            for label, args in [('png', ['exec-out', 'screencap', '-p']),
                                ('crash.log', ['logcat', '-d', '-b', 'crash'])]:
                try:
                    Path('/tmp/' + serial + '-' + label).write_bytes(
                        subprocess.check_output(['adb', '-s', serial, *args], timeout=25))
                except Exception:
                    pass
