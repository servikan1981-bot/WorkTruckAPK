"""Upgrade two phones, accept a Durak invitation, and synchronize a round."""
import os
import re
import subprocess
import time
from pathlib import Path
import video_family_613 as helper

helper.CODE = 'Family620-' + os.environ.get('GITHUB_RUN_ID', 'local')
A, B = 'emulator-5554', 'emulator-5556'


def labels(serial):
    return [(n.get('text', '') + ' ' + n.get('content-desc', ''), n.get('bounds', ''), n.get('class', ''))
            for n in helper.snap(serial).iter('node')]


def wait_text(serial, needle, timeout=35):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if any(needle in label for label, _, _ in labels(serial)):
            return
        time.sleep(1)
    raise AssertionError(f'{serial}: missing {needle}: {labels(serial)[-25:]}')


def first_hand_card(serial):
    candidates = []
    for label, bounds, cls in labels(serial):
        if 'Button' not in cls or not re.search(r'[6-9JQKA]|10', label) or not bounds:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
        if y1 > 800 and y2 < 1900 and x2 - x1 < 150:
            candidates.append((y1, x1, (x1+x2)//2, (y1+y2)//2, label))
    assert candidates, f'no hand card in {labels(serial)[-35:]}'
    _, _, x, y, label = sorted(candidates, reverse=True)[-1]
    helper.adb(serial, 'shell', 'input', 'tap', str(x), str(y))
    print('Tapped card', serial, label, flush=True)


def tap_visible_button(serial, needle):
    for label, bounds, cls in labels(serial):
        if needle not in label or 'Button' not in cls:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
        if x2 <= x1 or y2 <= y1:
            continue
        helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        return
    raise AssertionError(f'{serial}: no visible button {needle}')


def main():
    helper.profile(A, 'Сергей')
    helper.profile(B, 'Света')
    for serial in (A, B):
        helper.adb(serial, 'shell', 'am', 'force-stop', helper.PKG)
        helper.adb(serial, 'install', '-r', '/tmp/family-620.apk')
        helper.adb(serial, 'shell', 'am', 'start', '-n', helper.ACT)
        if any('Разрешите системные входящие звонки' in x[0] for x in labels(serial)):
            helper.tap(serial, 'ПОЗЖЕ')
        wait_text(serial, 'v6.0.20', 20)
        wait_text(serial, 'Новый чат', 20)
    helper.tap(A, 'Игры')
    helper.tap(A, 'Дурак')
    tap_visible_button(A, 'Света')
    wait_text(B, 'приглашает сыграть в Дурака', 40)
    helper.tap(B, 'Принять')
    wait_text(A, 'Дурак · Света', 25)
    wait_text(B, 'Дурак · Сергей', 25)
    # A complete exchange must appear on both replicas.
    attacker = A if any('Ваш ход — атакуйте' in x[0] for x in labels(A)) else B
    defender = B if attacker == A else A
    first_hand_card(attacker)
    wait_text(defender, 'Ваш ход — отбивайтесь', 35)
    helper.tap(defender, 'Взять')
    wait_text(attacker, 'Ваш ход — атакуйте', 35)
    print('PASS upgrade 6.0.19 -> 6.0.20, invite, accept, attack and pickup on two phones', flush=True)


if __name__ == '__main__':
    try:
        main()
    finally:
        for serial in (A, B):
            try:
                Path('/tmp/' + serial + '-game.png').write_bytes(
                    subprocess.check_output(['adb', '-s', serial, 'exec-out', 'screencap', '-p']))
                Path('/tmp/' + serial + '-game.xml').write_text(
                    helper.adb(serial, 'exec-out', 'cat', '/sdcard/video-ui.xml'))
            except Exception:
                pass
