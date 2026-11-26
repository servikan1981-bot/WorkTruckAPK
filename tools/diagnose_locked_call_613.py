import re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
from video_family_613 import adb, profile, tap, snap

A='emulator-5554'
B='emulator-5556'

def evidence(stage):
    for serial in (A,B):
        for suffix,args in [
            ('png',['exec-out','screencap','-p']),
            ('xml',['exec-out','uiautomator','dump','/dev/tty']),
            ('window',['shell','dumpsys','window']),
            ('telecom',['shell','dumpsys','telecom']),
            ('calls',['shell','dumpsys','notification','--noredact']),
        ]:
            try:
                raw=subprocess.check_output(['adb','-s',serial,*args],timeout=25)
                Path(f'/tmp/{stage}-{serial}.{suffix}').write_bytes(raw)
            except Exception as e: print('evidence',stage,serial,suffix,str(e))
    for serial in (A,B):
        try:Path(f'/tmp/{stage}-{serial}.xml').write_text(ET.tostring(snap(serial),encoding='unicode'))
        except Exception as e:print('snapshot',str(e))

def main():
    profile(A,'Сергей')
    profile(B,'Света')
    tap(A,'Света')
    print('PIN:',adb(B,'shell','locksettings','set-pin','1234'))
    adb(B,'shell','input','keyevent','26')
    time.sleep(2)
    print('keyguard-before:',adb(B,'shell','dumpsys','window','policy')[-1700:])
    tap(A,'🎥')
    time.sleep(10)
    evidence('ringing')
    text=ET.tostring(snap(B),encoding='unicode')
    print('ringing labels:',re.findall(r'text="([^"]+)"',text)[-25:])
    print('accept visible:', 'Принять' in text)
    if 'Принять' in text:
        tap(B,'Принять')
        time.sleep(5)
        evidence('accepted')
        print('accepted labels:',re.findall(r'text="([^"]+)"',ET.tostring(snap(B),encoding='unicode'))[-25:])

if __name__=='__main__':
    try:main()
    finally:
        for serial in (A,B):
            try:Path(f'/tmp/{serial}-crash.log').write_text(adb(serial,'logcat','-d','-b','crash'))
            except Exception:pass
