from pathlib import Path

p = Path('duoapp/src/main/assets/index.html')
build_p = Path('duoapp/build.gradle')
manifest_p = Path('duoapp/src/main/AndroidManifest.xml')
h = p.read_text(encoding='utf-8')
b = build_p.read_text(encoding='utf-8')
m = manifest_p.read_text(encoding='utf-8')

def one(s, old, new, label):
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, got {count}')
    return s.replace(old, new)

b = one(b, 'versionCode 6021', 'versionCode 6022', 'version code')
b = one(b, "versionName '6.0.21'", "versionName '6.0.22'", 'version name')
m = one(m, 'android:label="Наша семья 6.0.21"', 'android:label="Наша семья 6.0.22"', 'app label')
h = one(h, '<title>Наша семья 6.0.21</title>', '<title>Наша семья 6.0.22</title>', 'page title')
h = one(h, "var APP_VERSION='6.0.21'", "var APP_VERSION='6.0.22'", 'app version')

# The bottom navigation has a dedicated More page. All existing menu buttons
# keep their ids and handlers, including the Sergey-only urgent message.
h = one(h, '    <button id="menuBtn" class="iconbtn">⋮</button>\n', '', 'three-dot button')
h = one(h, '    <button id="newsBtn" class="news-action">📰 Семейные новости · ссылки · фото · видео · поздравления</button>\n', '', 'home news shortcut')
archive = '    <button id="adminArchiveBtn" class="primary wide hidden" style="margin-bottom:11px">Семейный архив</button>\n'
h = one(h, archive, '', 'home archive shortcut')
h = one(h, '<button id="navNews"><span>📰</span>Новости</button>',
        '<button id="navNews"><span>📰</span>Новости<small id="newsNavBadge" class="nav-badge hidden"></small></button>',
        'news navigation badge')
h = one(h, '<div id="settingsPop" class="settings-pop hidden">\n',
        '<section id="more" class="screen hidden"><div class="topbar"><div class="avatar">☰</div><div class="who"><div class="name">Ещё</div><div class="status">Настройки и возможности семьи</div></div></div>\n'
        '<div class="body"><div id="settingsPop" class="more-options">\n'
        '  <button id="adminArchiveBtn" class="hidden">🗂️ Семейный архив</button>\n',
        'more page')
h = one(h, '  <button id="resetBtn">Сменить профиль / код</button>\n</div>\n\n<div id="relayModal"',
        '  <button id="resetBtn">Сменить профиль / код</button>\n</div></div></section>\n\n<div id="relayModal"',
        'more page close')

css = '.settings-pop{position:fixed;right:10px;top:66px;background:#fff;border-radius:17px;padding:7px;box-shadow:var(--shadow);z-index:35}.settings-pop button{display:block;width:100%;border:0;background:#fff;padding:11px 13px;border-radius:11px;text-align:left;font-weight:750}\n'
h = one(h, css,
        '.more-options{display:grid;gap:9px;padding-bottom:24px}.more-options button{display:block;width:100%;min-height:50px;border:1px solid var(--line);background:var(--card);color:var(--ink);padding:13px 15px;border-radius:14px;text-align:left;font-size:15px;font-weight:750;box-shadow:var(--shadow)}.more-options button:active{transform:scale(.99)}.more-options button.hidden{display:none}.nav-badge{display:inline-block;min-width:18px;margin-left:3px;padding:1px 4px;border-radius:9px;background:var(--red);color:#fff;font-size:10px}.nav-badge.hidden{display:none}\n',
        'more page style')

h = one(h, "['settingsPop','attachMenu','emojiPanel','newsCaptureMenu','relayModal'",
        "['attachMenu','emojiPanel','newsCaptureMenu','relayModal'", 'transient list')
h = one(h, "['setup','home','picker','chat','news','adminArchive','adminChat','checkers','gamesHub','durak']",
        "['setup','home','picker','chat','news','more','adminArchive','adminChat','checkers','gamesHub','durak']",
        'screens')
h = one(h, "['home','gamesHub','news'].includes(id)", "['home','gamesHub','news','more'].includes(id)", 'bottom navigation visibility')
h = one(h, "['navFamily','navGames','navNews'].forEach", "['navFamily','navGames','navNews','navMore'].forEach", 'navigation reset')
h = one(h, "if(id==='news')$('navNews').classList.add('active');}",
        "if(id==='news')$('navNews').classList.add('active');if(id==='more')$('navMore').classList.add('active');}",
        'more navigation selected')
h = one(h, " $('adminArchiveBtn').classList.toggle('hidden',role!=='sergey');\n var unreadNews=messages.filter(function(m){return m.kind==='news'&&m.from!==role&&!m.read;}).length;\n $('newsBtn').textContent='📰 Семейные новости · ссылки · фото · видео · поздравления'+(unreadNews?' · '+unreadNews:'');",
        " $('adminArchiveBtn').classList.toggle('hidden',role!=='sergey');\n var unreadNews=messages.filter(function(m){return m.kind==='news'&&m.from!==role&&!m.read;}).length;\n $('newsNavBadge').textContent=unreadNews>99?'99+':String(unreadNews);$('newsNavBadge').classList.toggle('hidden',!unreadNews);",
        'news badge and archive access')

h = one(h, "$('navMore').addEventListener('click',function(){showScreen('home');renderHome();$('settingsPop').classList.remove('hidden');});",
        "$('navMore').addEventListener('click',function(){renderHome();showScreen('more');});",
        'more tab handler')
h = one(h, "$('menuBtn').addEventListener('click',function(){$('settingsPop').classList.toggle('hidden');});\n", '', 'three-dot handler')
h = one(h, "$('newsBtn').addEventListener('click',openNews);\n", '', 'home news handler')
h = one(h, "$('adminBackBtn').addEventListener('click',function(){showScreen('home');});",
        "$('adminBackBtn').addEventListener('click',function(){showScreen('more');});",
        'archive back')
h = one(h, "if(!$('adminArchive').classList.contains('hidden')){showScreen('home');return true;}",
        "if(!$('adminArchive').classList.contains('hidden')){showScreen('more');return true;}\n if(!$('more').classList.contains('hidden')){showScreen('home');renderHome();return true;}",
        'Android back navigation')
h = one(h, " if(!t.closest('#settingsPop')&&!t.closest('#menuBtn'))$('settingsPop').classList.add('hidden');\n",
        '', 'popup dismissal')
# Former popup handlers simply open their corresponding setting screens/modals.
# Removing the hide call prevents a blank More page after closing a modal.
h = h.replace("$('settingsPop').classList.add('hidden');", '')
assert "$('settingsPop').classList.add('hidden')" not in h
assert 'id="menuBtn"' not in h and 'id="newsBtn"' not in h
assert h.count('id="adminArchiveBtn"') == 1
assert 'id="urgentMenuBtn"' in h and "role!=='sergey'" in h

p.write_text(h, encoding='utf-8')
build_p.write_text(b, encoding='utf-8')
manifest_p.write_text(m, encoding='utf-8')
print('Applied OurFamily 6.0.22 More-page navigation')
