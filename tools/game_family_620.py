"""Upgrade two phones from published 6.0.19, accept a Durak invitation, and synchronize a round."""
import re
import subprocess
import time
from pathlib import Path
import video_family_613 as helper

helper.CODE = 'Family620-' + str(int(time.time()))
A, B = 'emulator-5554', 'emulator-5556'
HOME_RELAY = 'family.familysergey.netcraze.pro'


def snap_retry(serial, attempts=6):
    last = None
    for _ in range(attempts):
        try:
            return helper.snap(serial)
        except Exception as e:
            last = e
            time.sleep(1)
    raise last


def labels(serial):
    result = []
    for n in snap_retry(serial).iter('node'):
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
    last = []
    while time.monotonic() < deadline:
        try:
            current = labels(serial)
            last = current
        except Exception:
            time.sleep(1)
            continue
        if any('Разрешите системные входящие звонки' in label for label, _, _ in current):
            tap_visible_button(serial, 'ПОЗЖЕ')
            time.sleep(1)
            continue
        if any(needle in label for label, _, _ in current):
            return
        time.sleep(1)
    raise AssertionError(f'{serial}: missing {needle}: {last[-25:]}')


def launch_family(serial):
    helper.adb(serial, 'shell', 'am', 'force-stop', helper.PKG)
    out = helper.adb(serial, 'shell', 'monkey', '-p', helper.PKG,
                     '-c', 'android.intent.category.LAUNCHER', '1')
    print(serial, 'launcher:', out[-300:], flush=True)


def profile_619(serial, role):
    launch_family(serial)
    wait_text(serial, 'Кто использует этот телефон?', 45)
    if not tap_visible_button(serial, role):
        raise AssertionError(f'{serial}: cannot tap profile {role}: {labels(serial)[-25:]}')
    time.sleep(1)
    root = snap_retry(serial)
    fields = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
    if not fields:
        raise AssertionError(f'{serial}: family code input missing')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', fields[0].get('bounds', '')))
    helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    helper.adb(serial, 'shell', 'input', 'text', helper.CODE)
    helper.adb(serial, 'shell', 'input', 'keyevent', '4')
    if not tap_visible_button(serial, 'Войти в семью'):
        raise AssertionError(f'{serial}: login button missing')
    wait_text(serial, 'Новый чат', 30)
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


def pickup_durak(serial):
    # WebView accessibility occasionally omits the action row even though it
    # is visibly rendered (confirmed by screencap). Prefer semantic lookup;
    # fall back to the fixed emulator-relative position of the yellow pickup button.
    for _ in range(4):
        try:
            if tap_visible_button(serial, 'Взять'):
                print('Tapped Durak pickup semantically on', serial, flush=True)
                return
        except Exception:
            pass
        time.sleep(.6)
    size = helper.adb(serial, 'shell', 'wm', 'size')
    match = re.search(r'(\d+)x(\d+)', size)
    if not match:
        raise AssertionError(serial + ': cannot determine screen size for Durak pickup')
    width, height = map(int, match.groups())
    x, y = round(width * 0.34), round(height * 0.91)
    helper.adb(serial, 'shell', 'input', 'tap', str(x), str(y))
    print('Tapped visible Durak pickup by screen-relative fallback on', serial, x, y, flush=True)


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
        wait_text(serial, 'v6.0.20', 35)
        wait_text(serial, 'Новый чат', 35)
    time.sleep(2)
    helper.tap(A, 'Игры')
    helper.tap(A, 'Дурак')
    started = time.monotonic()
    if not tap_visible_button(A, 'Света'):
        raise AssertionError('Sender cannot select Света in Durak club')
    wait_text(A, 'Дурак · Света', 10)
    print(f'Sender opened Durak after {time.monotonic()-started:.1f}s', flush=True)
    wait_text(B, 'приглашает сыграть в Дурака', 70)
    print(f'Recipient received Durak invite after {time.monotonic()-started:.1f}s', flush=True)
    helper.tap(B, 'Принять')
    wait_text(A, 'Дурак · Света', 35)
    wait_text(B, 'Дурак · Сергей', 25)
    attacker = A if any('Ваш ход — атакуйте' in x[0] for x in labels(A)) else B
    defender = B if attacker == A else A
    first_hand_card(attacker)
    wait_text(defender, 'Ваш ход — отбивайтесь', 70)
    pickup_durak(defender)
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
