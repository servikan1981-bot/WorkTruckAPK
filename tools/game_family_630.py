"""Exercise 6.0.30 games on two Android emulators, including background notifications."""
import re
import subprocess
import time
from pathlib import Path
import video_family_613 as helper

helper.CODE = 'Family630-' + str(int(time.time()))
A, B = 'emulator-5554', 'emulator-5556'
MAIN_COMPONENT = helper.PKG + '/com.sergey.duochat.MainActivity'


def snap_retry(serial, attempts=8):
    last = None
    for _ in range(attempts):
        try:
            return helper.snap(serial)
        except Exception as e:
            last = e
            time.sleep(.7)
    raise last


def labels(serial):
    out = []
    for n in snap_retry(serial).iter('node'):
        bounds = n.get('bounds', '')
        coords = list(map(int, re.findall(r'\d+', bounds)))
        if len(coords) != 4 or coords[2] <= coords[0] or coords[3] <= coords[1]:
            continue
        out.append((n.get('text', '') + ' ' + n.get('content-desc', ''), bounds, n.get('class', '')))
    return out


def tap_button(serial, needle):
    for label, bounds, cls in labels(serial):
        if needle not in label or 'Button' not in cls:
            continue
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
        helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        return True
    return False


def wait_text(serial, needle, timeout=35):
    end = time.monotonic() + timeout
    last = []
    while time.monotonic() < end:
        try:
            current = labels(serial)
            last = current
        except Exception:
            time.sleep(.8)
            continue
        if any('Разрешите системные входящие звонки' in x[0] for x in current):
            tap_button(serial, 'ПОЗЖЕ')
            time.sleep(.5)
            continue
        if any(needle in x[0] for x in current):
            return
        time.sleep(.7)
    raise AssertionError(f'{serial}: missing {needle}: {last[-25:]}')


def launch(serial):
    helper.adb(serial, 'shell', 'am', 'start', '-W', '-n', MAIN_COMPONENT)
    wait_text(serial, 'Наша семья', 30)


def profile(serial, role):
    helper.adb(serial, 'shell', 'pm', 'clear', helper.PKG)
    for perm in ('android.permission.CAMERA', 'android.permission.RECORD_AUDIO', 'android.permission.POST_NOTIFICATIONS'):
        try:
            helper.adb(serial, 'shell', 'pm', 'grant', helper.PKG, perm)
        except Exception:
            pass
    launch(serial)
    wait_text(serial, 'Кто использует этот телефон?', 35)
    if not tap_button(serial, role):
        raise AssertionError(f'{serial}: cannot choose {role}')
    root = snap_retry(serial)
    fields = [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']
    if not fields:
        raise AssertionError(serial + ': family code field missing')
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', fields[0].get('bounds','')))
    helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    helper.adb(serial, 'shell', 'input', 'text', helper.CODE)
    helper.adb(serial, 'shell', 'input', 'keyevent', '4')
    if not tap_button(serial, 'Войти в семью'):
        raise AssertionError(serial + ': login button missing')
    wait_text(serial, 'Семья', 35)
    print('PROFILE', serial, role, flush=True)


def notification_dump(serial):
    try:
        return subprocess.check_output(
            ['adb','-s',serial,'shell','dumpsys','notification','--noredact'],
            text=True, stderr=subprocess.STDOUT, errors='replace')
    except Exception:
        return ''


def wait_notification(serial, needle, timeout=30):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        dump = notification_dump(serial)
        if needle.lower() in dump.lower():
            return time.monotonic()
        time.sleep(.5)
    Path('/tmp/' + serial + '-notifications.txt').write_text(notification_dump(serial), errors='replace')
    raise AssertionError(f'{serial}: notification missing: {needle}')


def home(serial):
    helper.adb(serial, 'shell', 'input', 'keyevent', '3')
    time.sleep(1)


def foreground(serial):
    helper.adb(serial, 'shell', 'am', 'start', '-W', '-n', MAIN_COMPONENT)
    time.sleep(1)


def first_hand_card(serial):
    candidates=[]
    for label,bounds,cls in labels(serial):
        if 'Button' not in cls or not re.search(r'[6-9JQKA]|10',label):
            continue
        x1,y1,x2,y2=map(int,re.findall(r'\d+',bounds))
        if y1 > 300 and x2-x1 < 120:
            candidates.append((y1,x1,(x1+x2)//2,(y1+y2)//2,label))
    if not candidates:
        raise AssertionError(serial + ': no Durak hand card')
    _,_,x,y,label=sorted(candidates, reverse=True)[0]
    helper.adb(serial,'shell','input','tap',str(x),str(y))
    print('CARD', serial, label, flush=True)


def main():
    profile(A, 'Сергей')
    profile(B, 'Света')
    time.sleep(2)

    # Checkers invitation must notify while recipient is merely backgrounded.
    home(B)
    helper.tap(A, 'Игры')
    helper.tap(A, 'Шашки')
    t0=time.monotonic()
    if not tap_button(A, 'Света'):
        raise AssertionError('cannot choose Света for checkers')
    tn=wait_notification(B, 'Приглашает играть в шашки', 30)
    print(f'PASS checkers background invite notification {tn-t0:.2f}s', flush=True)
    foreground(B)
    wait_text(B, 'приглашает вас в шашки', 30)
    if not tap_button(B, 'Принять'):
        raise AssertionError('checkers accept missing')
    wait_text(A, 'Шашки', 25)
    wait_text(B, 'Шашки', 25)
    # Return both to game hub.
    tap_button(A, '‹')
    tap_button(B, '‹')
    time.sleep(1)

    # Durak invite + turn notifications and round synchronization.
    home(B)
    helper.tap(A, 'Дурак')
    t0=time.monotonic()
    if not tap_button(A, 'Света'):
        raise AssertionError('cannot choose Света for Durak')
    tn=wait_notification(B, 'Приглашает играть в дурака', 30)
    print(f'PASS Durak background invite notification {tn-t0:.2f}s', flush=True)
    foreground(B)
    wait_text(B, 'приглашает сыграть в Дурака', 30)
    if not tap_button(B, 'Принять'):
        raise AssertionError('Durak accept missing')
    wait_text(A, 'Дурак · Света', 30)
    wait_text(B, 'Дурак · Сергей', 30)

    attacker = A if any('Ваш ход — атакуйте' in x[0] for x in labels(A)) else B
    defender = B if attacker == A else A
    home(defender)
    t1=time.monotonic()
    first_hand_card(attacker)
    tn=wait_notification(defender, 'Ваш ход в дураке', 30)
    print(f'PASS Durak move notification {tn-t1:.2f}s {attacker}->{defender}', flush=True)
    foreground(defender)
    wait_text(defender, 'Ваш ход — отбивайтесь', 30)

    home(attacker)
    t2=time.monotonic()
    if not tap_button(defender, 'Взять'):
        raise AssertionError('Durak pickup missing')
    tn=wait_notification(attacker, 'Ваш ход в дураке', 30)
    print(f'PASS Durak reverse move notification {tn-t2:.2f}s {defender}->{attacker}', flush=True)
    foreground(attacker)
    wait_text(attacker, 'Ваш ход — атакуйте', 30)
    print('PASS two-phone games: checkers invite, Durak invite, ordered move, reverse move, background notifications', flush=True)


if __name__ == '__main__':
    try:
        main()
    finally:
        for serial in (A,B):
            try:
                Path('/tmp/'+serial+'-notifications.txt').write_text(notification_dump(serial), errors='replace')
                Path('/tmp/'+serial+'-game.png').write_bytes(subprocess.check_output(['adb','-s',serial,'exec-out','screencap','-p']))
            except Exception:
                pass
