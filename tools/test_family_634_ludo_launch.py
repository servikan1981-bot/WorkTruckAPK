from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
h = (ROOT / 'duoapp/src/main/assets/index.html').read_text(encoding='utf-8')
g = (ROOT / 'duoapp/build.gradle').read_text(encoding='utf-8')
m = (ROOT / 'duoapp/src/main/AndroidManifest.xml').read_text(encoding='utf-8')

assert 'versionCode 6034' in g
assert "versionName '6.0.34'" in g
assert 'Наша семья 6.0.34' in m
assert "APP_VERSION='6.0.34'" in h

# Exact regression for the 6.0.33 phone bug: Ludo must be a registered screen.
show = re.search(r"function showScreen\(id\)\{(.*?)\n\}", h, re.S)
assert show, 'showScreen not found'
assert "'ludo'" in show.group(1), 'Ludo is not registered in showScreen()'
assert "showScreen('ludo');renderLudo();" in h, 'openLudo no longer opens Ludo screen'

# Picker behavior seen in the screenshot: maximum 3 invited participants.
assert 'id="ludoPickerHint"' in h
assert 'selectedCount()>3' in h
assert 'максимум 3 участников' in h
assert "btn.textContent='Запускаем…'" in h
assert "catch(err){console.error('Ludo start failed'" in h

# Existing features must still be present.
for marker in [
    'id="hubCheckersBtn"', 'id="hubDurakBtn"', 'id="hubLudoBtn"',
    'id="micBtn"', 'captureChatVideoBtn', 'VOICE_MAX_MS=300000',
    'PermissionGateActivity',
]:
    hay = h if marker != 'PermissionGateActivity' else m
    assert marker in hay, marker

print('PASS: 6.0.34 Ludo launch regression guards')
