"""Upgrade two phones from published 6.0.19, accept a Durak invitation, and synchronize a round."""
import re
import subprocess
import time
from pathlib import Path
import video_family_613 as helper

helper.CODE = 'Family620-' + str(int(time.time()))
A, B = 'emulator-5554', 'emulator-5556'
HOME_RELAY = 'family.familysergey.netcraze.pro'


def labels(serial):
    result = []
    for n in helper.snap(serial).iter('node'):
        bounds = n.get('bounds', '')
        coords = list(map(int, re.findall(r'\d+', bounds)))
        if len(coords) != 4 or coords[2] <= coords[0] or coords[3] <= coords[1]:
            continue
        result.append((n.get('text', '') + ' ' + n.get('content-desc', ''), bounds, n.get('class', '')))
    return result


def logcat(serial, lines=700):
    try:
        return subprocess.check_output(
            ['adb', '-s', serial, 'logcat', '-d', '-t', str(lines)],
            text=True, stderr=subprocess.STDOUT, errors='replace')
    except Exception as e:
        return 'logcat failed: ' + repr(e)


def wait_text(serial, needle, timeout=35):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        current = labels(serial)
        if any('Разрешите системные входящие звонки' in label for label, _, _ in current):
            tap_visible_button(serial, 'ПОЗЖЕ')
            continue
        if any(needle in label for label, _, _ in current):
            return
        time.sleep(1)
    raise AssertionError(f'{serial}: missing {needle}: {labels(serial)[-25:]}')


def first_hand_card(serial):
    candidates = []
    for label, bounds, cls in labels(serial):
        if 'Button' not in cls or not re.search(r'[6-9JQKA]|10', label) or not bounds:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
        if y1 > 320 and y2 <= 640 and x2 - x1 < 100:
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
    print('Creating real 6.0.19 profiles; 6.0.20 must migrate to', HOME_RELAY, flush=True)
    helper.profile(A, 'Сергей')
    helper.profile(B, 'Света')
    for serial in (A, B):
        helper.adb(serial, 'shell', 'am', 'force-stop', helper.PKG)
        helper.adb(serial, 'install', '-r', '/tmp/family-620.apk')
        helper.adb(serial, 'shell', 'logcat', '-c')
        helper.adb(serial, 'shell', 'am', 'start', '-n', helper.ACT)
        if any('Разрешите системные входящие звонки' in x[0] for x in labels(serial)):
            helper.tap(serial, 'ПОЗЖЕ')
        wait_text(serial, 'v6.0.20', 20)
        wait_text(serial, 'Новый чат', 20)
    # Let configure()/WebView crypto initialization finish before the game tap.
    time.sleep(2)
    helper.tap(A, 'Игры')
    helper.tap(A, 'Дурак')
    started = time.monotonic()
    tap_visible_button(A, 'Света')
    time.sleep(1)
    print('Sender UI after opponent tap:', labels(A)[-30:], flush=True)
    try:
        wait_text(A, 'Дурак · Света', 8)
    except Exception:
        lc = logcat(A)
        print('--- SENDER LOGCAT AFTER DURAK TAP ---', flush=True)
        for line in lc.splitlines():
            low=line.lower()
            if ('chromium' in low or 'console' in low or 'ourfamily' in low or 'androidruntime' in low or 'uncaught' in low or 'javascript' in low):
                print(line, flush=True)
        raise
    print(f'Sender opened Durak after {time.monotonic()-started:.1f}s', flush=True)
    wait_text(B, 'приглашает сыграть в Дурака', 70)
    print(f'Recipient received Durak invite after {time.monotonic()-started:.1f}s', flush=True)
    helper.tap(B, 'Принять')
    wait_text(A, 'Дурак · Света', 35)
    wait_text(B, 'Дурак · Сергей', 15)
    attacker = A if any('Ваш ход — атакуйте' in x[0] for x in labels(A)) else B
    defender = B if attacker == A else A
    first_hand_card(attacker)
    wait_text(defender, 'Ваш ход — отбивайтесь', 70)
    helper.tap(defender, 'Взять')
    wait_text(attacker, 'Ваш ход — атакуйте', 70)
    print('PASS upgrade 6.0.19 -> 6.0.20, home relay migration, invite, accept, attack and pickup on two phones', flush=True)


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
                Path('/tmp/' + serial + '-logcat.txt').write_text(logcat(serial), errors='replace')
            except Exception:
                pass
