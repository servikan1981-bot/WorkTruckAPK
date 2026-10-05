import re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
from video_family_613 import adb, profile, snap, tap

A, B = 'emulator-5554', 'emulator-5556'

def evidence(stage):
    for serial in (A, B):
        for suffix, args in [
            ('png', ['exec-out', 'screencap', '-p']),
            ('window', ['shell', 'dumpsys', 'window']),
            ('call.log', ['logcat', '-d', '-s', 'OurFamilyCall:I', '*:S']),
        ]:
            try:
                Path(f'/tmp/{stage}-{serial}.{suffix}').write_bytes(
                    subprocess.check_output(['adb', '-s', serial, *args], timeout=25))
            except Exception as exc:
                print('evidence:', stage, serial, suffix, exc)
        try:
            Path(f'/tmp/{stage}-{serial}.xml').write_text(
                ET.tostring(snap(serial), encoding='unicode'))
        except Exception as exc:
            print('snapshot:', stage, serial, exc)

def main():
    profile(A, 'Сергей')
    time.sleep(4)
    profile(B, 'Света')
    tap(A, 'Света')
    print(adb(B, 'shell', 'locksettings', 'set-pin', '1234'))
    adb(B, 'shell', 'input', 'keyevent', '26')
    time.sleep(2)
    tap(A, '🎥')

    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        root = snap(B)
        buttons = [n for n in root.iter('node')
                   if n.get('text', '').lower() == 'принять' and n.get('clickable') == 'true']
        if buttons:
            break
        time.sleep(1)
    else:
        evidence('missing-answer')
        raise AssertionError('No accessible Answer button over keyguard')

    # Keep the answer tap ahead of slow diagnostic captures: the caller times out.
    Path('/tmp/ringing-emulator-5556.png').write_bytes(
        subprocess.check_output(['adb', '-s', B, 'exec-out', 'screencap', '-p'], timeout=15))
    bounds = list(map(int, re.findall(r'\d+', buttons[0].get('bounds', ''))))
    height = int(re.search(r'(\d+)x(\d+)', adb(B, 'shell', 'wm', 'size')).group(2))
    assert len(bounds) == 4 and height * .12 < bounds[1] < bounds[3] < height * .9, \
        f'Answer button outside usable screen: {bounds}, height={height}'
    window = adb(B, 'shell', 'dumpsys', 'window')
    assert 'isKeyguardShowing=true' in window, 'PIN keyguard was not active'
    assert 'IncomingCallActivity' in window, 'Incoming call screen not foreground'
    print('PASS: Answer button visible above secure keyguard at', bounds)

    print('Tapping Answer at', time.time(), flush=True)
    tap(B, 'Принять')
    print('Answer tap completed at', time.time(), flush=True)
    deadline = time.monotonic() + 50
    while time.monotonic() < deadline:
        logs = {serial: adb(serial, 'logcat', '-d', '-s', 'OurFamilyCall:I', '*:S')
                for serial in (A, B)}
        if all('remote_frame' in logs[serial] for serial in (A, B)):
            text = ET.tostring(snap(B), encoding='unicode')
            assert 'Разговор' in text and 'Видеозвонок' in text, \
                'Answer did not transition to the video call'
            assert 'Введите PIN' not in text, 'PIN unlock required to answer'
            evidence('accepted')
            print('PASS: answered over PIN keyguard and remote video visible on both devices')
            return
        time.sleep(1)
    evidence('no-video')
    raise AssertionError('Answer did not establish video on both devices')

if __name__ == '__main__':
    try:
        main()
    finally:
        for serial in (A, B):
            try:
                Path(f'/tmp/{serial}-crash.log').write_text(
                    adb(serial, 'logcat', '-d', '-b', 'crash'))
            except Exception:
                pass
