from pathlib import Path
import re

h=Path('duoapp/src/main/assets/index.html').read_text(encoding='utf-8')
g=Path('duoapp/build.gradle').read_text(encoding='utf-8')
m=Path('duoapp/src/main/AndroidManifest.xml').read_text(encoding='utf-8')

assert 'versionCode 6035' in g
assert "versionName '6.0.35'" in g
assert '6.0.35' in m
assert "APP_VERSION='6.0.35'" in h
assert "'gamesHub','durak','ludo'" in h
assert 'function ensureLudoRules()' in h
assert 'window.LudoRules={initial:initial' in h
assert 'function validReusableLudoGame' in h
assert 'Array.isArray(g.state.pieces)' in h
assert 'var rules=ensureLudoRules(),state=rules.initial(players.length);' in h
assert "$('ludoPicker').classList.add('hidden');openLudo(id);" in h
assert "sendLudoInvites(g).catch(function(err)" in h
assert 'function ludoSettled(tasks)' in h
assert 'return ludoSettled(tasks);' in h
assert "function openLudo(id){var g=ludoGames[id];if(!g)return;ensureLudoRules();" in h
assert "toast('Не удалось запустить: '+msg.slice(0,80));" in h

# The invite function must not await network delivery before opening the game.
invite=re.search(r'async function inviteLudo\(peers\)\{(.*?)\n\}\nasync function sendLudoInvites',h,re.S)
assert invite, 'inviteLudo not found'
body=invite.group(1)
assert 'openLudo(id);' in body
assert 'await sendLudoInvites' not in body
assert 'validReusableLudoGame' in body

print('6.0.35 Ludo startup regression guards OK')
