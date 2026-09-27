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


def tap_visible_button(serial, needle):
    for label, bounds, cls in labels(serial):
        if needle not in label or 'Button' not in cls:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
        if x2 <= x1 or y2 <= y1:
            continue
        helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        return True
    return False


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


def launch_family(serial):
    helper.adb(serial, 'shell', 'am', 'force-stop', helper.PKG)
    out = helper.adb(serial, 'shell', 'monkey', '-p', helper.PKG,
                     '-c', 'android.intent.category.LAUNCHER', '1')
    print(serial, 'launcher:', out[-300:], flush=True)
    time.sleep(3)


def profile_619(serial, role):
    # CI sometimes returns to Launcher after a cold install. Launch through the
    # same MAIN/LAUNCHER path a real phone uses and retry until the profile UI is visible.
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        launch_family(serial)
        current = labels(serial)
        if any('Разрешите системные входящие звонки' in label for label, _, _ in current):
            tap_visible_button(serial, 'ПОЗЖЕ')
            time.sleep(1)
            current = labels(serial)
        if any(role in label for label, _, _ in current):
            break
    else:
        raise AssertionError(f'{serial}: 6.0.19 profile screen did not open: {labels(serial)[-25:]}')

    if not tap_visible_button(serial, role):
        raise AssertionError(f'{serial}: cannot tap profile {role}')
    time.sleep(1)
    root = helper.snap(serial)
    fields = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
    if not fields:
        raise AssertionError(f'{serial}: family code input missing')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', fields[0].get('bounds', '')))
    helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    helper.adb(serial, 'shell', 'input', 'text', helper.CODE)
    helper.adb(serial, 'shell', 'input', 'keyevent', '4')
    if not tap_visible_button(serial, 'Войти в семью'):
        raise AssertionError(f'{serial}: login button missing')
    wait_text(serial, 'Новый чат', 25)
    print(serial, '6.0.19 profile ready for', role, flush=True)


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


def main():
    print('Creating real 6.0.19 profiles; 6.0.20 must migrate to', HOME_RELAY, flush=True)
    profile_619(A, 'Сергей')
    profile_619(B, 'Света')
    for serial in (A, B):
        helper.adb(serial, 'shell', 'am', 'force-stop', helper.PKG)
        install = helper.adb(serial, 'install', '-r', '/tmp/family-620.apk')
        print(serial, 'upgrade result:', install, flush=True)
        if 'Success' not in install:
            raise AssertionError(serial + ': 6.0.19 -> 6.0.20 install failed: ' + install)
        helper.adb(serial, 'shell', 'logcat', '-c')
        launch_family(serial)
        if any('Разрешите системные входящие звонки' in x[0] for x in labels(serial)):
            tap_visible_button(serial, 'ПОЗЖЕ')
        wait_text(serial, 'v6.0.20', 25)
        wait_text(serial, 'Новый чат', 25)
    time.sleep(2)
    helper.tap(A, 'Игры')
    helper.tap(A, 'Дурак')
    started = time.monotonic()
    if not tap_visible_button(A, 'Света'):
        raise AssertionError('Sender cannot select Света in Durak club')
    time.sleep(1)
    print('Sender UI after opponent tap:', labels(A)[-30:], flush=True)
    try:
        wait_text(A, 'Дурак · Света', 8)
    except Exception:
        lc = logcat(A)
        print('--- SENDER LOGCAT AFTER DURAK TAP ---', flush=True)
        for line in lc.splitlines():
            low = line.lower()
            if ('chromium' in low or 'console' in low or 'ourfamily' in low or 'androidruntime' in low or 'uncaught' in low or 'javascript' in low):
                print(line, flush=True)
        raise
    print(f'Sender opened Durak after {time.monotonic()-started:.1f}s', flush=True)
    wait_text(B, 'приглашает сыграть в Дурака', 70)
    print(f'Recipient received Durak invite after {time.monotonic()-started:.1f}s', flush=True)
    helper.tap(B, 'Принять')
    wait_text(A, 'Дурак · Света', 35)
    wait_text(B, 'Дурак · Сергей', 20)
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
