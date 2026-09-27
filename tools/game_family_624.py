"""Smoke test a chat message and a Durak invitation on two clean 6.0.24 phones."""
import re
import time
import video_family_613 as helper
import game_family_623 as game

A, B = 'emulator-5554', 'emulator-5556'
helper.CODE = 'Family624-' + str(int(time.time()))


def fill_message(serial, message):
    fields = [n for n in helper.snap(serial).iter('node')
              if n.get('class') == 'android.widget.EditText'
              and 'Сообщение' in n.get('text', '')]
    if not fields:
        # Android WebView may expose the empty textarea as a hint instead of text.
        fields = [n for n in helper.snap(serial).iter('node')
                  if n.get('class') == 'android.widget.EditText']
    assert fields, serial + ': chat composer missing'
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', fields[-1].get('bounds', '')))
    helper.adb(serial, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    helper.adb(serial, 'shell', 'input', 'text', message)
    helper.adb(serial, 'shell', 'input', 'keyevent', '4')
    helper.tap(serial, '➤')


def main():
    helper.profile(A, 'Сергей')
    helper.profile(B, 'Света')
    helper.tap(A, 'Света')
    helper.tap(B, 'Сергей')
    text = 'HELLO624' + str(int(time.time()))
    started = time.monotonic()
    fill_message(A, text)
    game.wait_text(B, text, 45)
    print(f'PASS: chat message reached Света in {time.monotonic()-started:.1f}s', flush=True)

    helper.tap(A, 'Пригласить в игру')
    helper.tap(A, 'Дурак')
    started = time.monotonic()
    game.wait_text(B, 'приглашает сыграть в Дурака', 50)
    print(f'PASS: Durak invitation reached Света in {time.monotonic()-started:.1f}s', flush=True)
    helper.tap(B, 'Принять')
    game.wait_text(B, 'Дурак · Сергей', 20)
    game.wait_text(A, 'Сдаться', 35)
    print('PASS: Durak accepted on two devices', flush=True)


if __name__ == '__main__':
    try:
        main()
    finally:
        for serial in (A, B):
            try:
                from pathlib import Path
                import subprocess
                Path('/tmp/' + serial + '-game.png').write_bytes(
                    subprocess.check_output(['adb', '-s', serial, 'exec-out', 'screencap', '-p']))
                Path('/tmp/' + serial + '-game.log').write_text(game.logcat(serial), errors='replace')
            except Exception:
                pass
