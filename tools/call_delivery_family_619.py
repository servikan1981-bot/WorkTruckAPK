"""Check call invitation and video between two Android emulators."""
import os
import re
import subprocess
import time
from pathlib import Path

import video_family_613 as helper

helper.CODE = 'Family619-' + os.environ.get('GITHUB_RUN_ID', 'local')
A, B = 'emulator-5554', 'emulator-5556'


def event(serial, phase):
    log = helper.adb(serial, 'logcat', '-d', '-s', 'OurFamilyCall:I', '*:S')
    return [int(v) for v in re.findall(r'OurFamilyCall: ' + phase + r' (\d+)', log)]


def wait_event(serial, phase, count, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        events = event(serial, phase)
        if len(events) >= count:
            return events[-1]
        time.sleep(0.5)
    raise AssertionError(f'{serial}: {phase} did not arrive in {timeout}s; logs: '
                         + helper.adb(serial, 'logcat', '-d', '-s', 'OurFamilyCall:I', '*:S')[-1500:])


def main():
    helper.profile(A, 'Сергей')
    helper.profile(B, 'Света')
    helper.tap(A, 'Света')
    for trial in range(2):
        helper.adb(A, 'logcat', '-c')
        helper.adb(B, 'logcat', '-c')
        if trial:
            # The second invitation must wake the background recipient.
            helper.adb(B, 'shell', 'input', 'keyevent', '3')
        helper.tap(A, '🎥')
        sent = wait_event(A, 'invite_start', 1, 10)
        received = wait_event(B, 'invite_received', 1, 15)
        delay = received - sent
        print(f'CALL {trial+1} invite delivered in {delay}ms', flush=True)
        assert 0 <= delay < 8000, f'Invitation took {delay}ms'
        if trial == 0:
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if helper.tap(B, 'Принять', optional=True):
                    break
                time.sleep(0.5)
            else:
                raise AssertionError('Incoming call UI missing')
            wait_event(A, 'remote_frame', 1, 30)
            wait_event(B, 'remote_frame', 1, 30)
            print('VIDEO frames received on both devices', flush=True)
        helper.tap(A, '✕')
        time.sleep(3)


if __name__ == '__main__':
    try:
        main()
    finally:
        for serial in (A, B):
            try:
                Path('/tmp/' + serial + '-call.log').write_text(
                    helper.adb(serial, 'logcat', '-d', '-s', 'OurFamilyCall:I', '*:S'))
                Path('/tmp/' + serial + '-screen.png').write_bytes(
                    subprocess.check_output(['adb', '-s', serial, 'exec-out', 'screencap', '-p']))
                print(serial, helper.adb(serial, 'logcat', '-d', '-s', 'OurFamilyCall:I', '*:S')[-1000:], flush=True)
            except Exception:
                pass
