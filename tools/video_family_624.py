"""Run the two-device video test in an isolated relay namespace."""
import subprocess
import time
from pathlib import Path
import video_family_613 as video

video.CODE = 'FamilyVideo624-' + str(time.time_ns())

if __name__ == '__main__':
    try:
        video.main()
    finally:
        for serial in ('emulator-5554', 'emulator-5556'):
            try:
                Path('/tmp/' + serial + '.png').write_bytes(subprocess.check_output(
                    ['adb', '-s', serial, 'exec-out', 'screencap', '-p'], timeout=20))
            except Exception:
                pass
            for kind, args in (('call.log', ('logcat', '-d', '-s', 'OurFamilyCall:I', '*:S')),
                               ('crash.log', ('logcat', '-d', '-b', 'crash'))):
                try:
                    Path('/tmp/' + serial + '-' + kind).write_text(video.adb(serial, *args))
                except Exception:
                    pass
