"""OurFamily relay on an owner-controlled computer. Standard library only."""
import datetime
import hashlib
import json
import os
import re
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen
from xml.etree import ElementTree

ROOT = Path(os.environ.get('FAMILY_DATA', './data')).resolve()
ROOT.mkdir(parents=True, exist_ok=True)
(ROOT / 'files').mkdir(exist_ok=True)
DB = sqlite3.connect(ROOT / 'relay.sqlite3', check_same_thread=False, isolation_level=None)
DB.execute('PRAGMA journal_mode=WAL')
DB.execute('PRAGMA busy_timeout=5000')
DB.execute('CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT NOT NULL, id TEXT NOT NULL, time INTEGER NOT NULL, message TEXT NOT NULL)')
DB.execute('CREATE INDEX IF NOT EXISTS events_topic_seq ON events(topic,seq)')
DB.execute('CREATE INDEX IF NOT EXISTS events_topic_time ON events(topic,time)')
DB.execute('CREATE TABLE IF NOT EXISTS news_dedupe (topic TEXT NOT NULL, client_id TEXT NOT NULL, PRIMARY KEY(topic,client_id))')
DB.execute('CREATE TABLE IF NOT EXISTS articles (id TEXT PRIMARY KEY, day TEXT NOT NULL, data TEXT NOT NULL, added_at INTEGER NOT NULL)')
DB.execute('CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
LOCK = threading.RLock()
WAKE = threading.Condition(LOCK)
TOPIC = re.compile(r'^[A-Za-z0-9_-]{12,100}$')
FILE = re.compile(r'^of5file-[a-f0-9]{32}$')
NEWS = re.compile(r'^of5n-[a-f0-9]{48}$')
QUOTES = [
    ('Дисциплина — мать победы.', 'Александр Суворов'),
    ('Наше счастье зависит в большей степени от того, как мы встречаем события нашей жизни, чем от природы самих событий.', 'Александр Гумбольдт'),
    ('Каждый подарок, даже самый маленький, становится великим даром, если ты вручаешь его с любовью.', 'Джон Уолкот'),
    ('Вовсе не обязательно соглашаться с собеседником, чтобы найти с ним общий язык.', 'Маргарет Тэтчер'),
]


def metadata(key, value=None):
    with LOCK:
        if value is not None:
            DB.execute('INSERT OR REPLACE INTO metadata VALUES (?,?)', (key, value))
        row = DB.execute('SELECT value FROM metadata WHERE key=?', (key,)).fetchone()
        return row[0] if row else None


def read_events(topic, params):
    try:
        after = max(0, int(params.get('after', ['0'])[0]))
    except ValueError:
        after = 0
    if after:
        rows = DB.execute('SELECT seq,id,time,message FROM events WHERE topic=? AND seq>? ORDER BY seq LIMIT 800', (topic, after)).fetchall()
    else:
        long_news = bool(NEWS.fullmatch(topic) and params.get('since') == ['90d'])
        raw = params.get('since', ['10m'])[0]
        minutes = min(10, int(raw[:-1])) if re.fullmatch(r'\d{1,2}m', raw) else 10
        since = int(time.time()) - (90 * 86400 if long_news else minutes * 60)
        if long_news:
            rows = list(reversed(DB.execute('SELECT seq,id,time,message FROM events WHERE topic=? AND time>=? ORDER BY seq DESC LIMIT 300', (topic, since)).fetchall()))
        else:
            rows = DB.execute('SELECT seq,id,time,message FROM events WHERE topic=? AND time>=? ORDER BY seq LIMIT 800', (topic, since)).fetchall()
    return ''.join(json.dumps(dict(seq=seq, id=ident, time=timestamp, event='message', topic=topic, message=message), ensure_ascii=False) + '\n' for seq, ident, timestamp, message in rows)


def utc_moscow_day():
    return (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=3)).date().isoformat()


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def send_bytes(self, payload, status=200, content_type='application/json; charset=utf-8'):
        if not isinstance(payload, bytes):
            payload = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(payload)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        if self.path != '/':
            return self.send_bytes({'error': 'route not found'}, 404)
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if size < 1 or size > 140000:
                return self.send_bytes({'error': 'invalid size'}, 413)
            body = json.loads(self.rfile.read(size))
            topic = body.get('topic')
            messages = body.get('messages') if isinstance(body.get('messages'), list) else [body.get('message')]
            if not isinstance(topic, str) or not TOPIC.fullmatch(topic) or not 1 <= len(messages) <= 16 or (NEWS.fullmatch(topic) and len(messages) != 1) or any(not isinstance(m, str) or not 1 <= len(m) <= 8000 for m in messages):
                return self.send_bytes({'error': 'invalid message'}, 400)
            now = int(time.time())
            with WAKE:
                DB.execute('BEGIN IMMEDIATE')
                try:
                    if NEWS.fullmatch(topic):
                        match = re.fullmatch(r'of6news\|([a-f0-9]{32})\|[A-Za-z0-9+/=]+', messages[0])
                        if not match:
                            DB.execute('ROLLBACK')
                            return self.send_bytes({'error': 'invalid encrypted news'}, 400)
                        if DB.execute('SELECT 1 FROM news_dedupe WHERE topic=? AND client_id=?', (topic, match[1])).fetchone():
                            DB.execute('COMMIT')
                            return self.send_bytes({'ok': True, 'duplicate': True})
                        DB.execute('INSERT INTO news_dedupe VALUES (?,?)', (topic, match[1]))
                    result = None
                    for message in messages:
                        result = dict(id=str(uuid.uuid4()), time=now, event='message', topic=topic, message=message)
                        DB.execute('INSERT INTO events(topic,id,time,message) VALUES (?,?,?,?)', (topic, result['id'], now, message))
                    retention = 90 * 86400 if NEWS.fullmatch(topic) else 300 if topic.startswith('of5p-') else 86400
                    DB.execute('DELETE FROM events WHERE topic=? AND time<?', (topic, now - retention))
                    DB.execute('COMMIT')
                    WAKE.notify_all()
                except Exception:
                    DB.execute('ROLLBACK')
                    raise
            self.send_bytes(result if len(messages) == 1 else {'ok': True, 'count': len(messages)})
        except (ValueError, json.JSONDecodeError, TypeError):
            self.send_bytes({'error': 'invalid request'}, 400)

    def do_PUT(self):
        topic = self.path.strip('/')
        if not FILE.fullmatch(topic):
            return self.send_bytes({'error': 'invalid file'}, 400)
        try:
            size = int(self.headers.get('Content-Length', '0'))
        except ValueError:
            size = 0
        if size < 1 or size > 13_000_000:
            return self.send_bytes({'error': 'file too large'}, 413)
        target = ROOT / 'files' / topic
        if target.exists():
            return self.send_bytes({'error': 'file exists'}, 409)
        temporary = ROOT / 'files' / ('.' + topic)
        with temporary.open('xb') as output:
            remaining = size
            while remaining:
                chunk = self.rfile.read(min(65536, remaining))
                if not chunk:
                    temporary.unlink(missing_ok=True)
                    return self.send_bytes({'error': 'incomplete upload'}, 400)
                output.write(chunk)
                remaining -= len(chunk)
        temporary.rename(target)
        host = self.headers.get('Host', '')
        scheme = 'http' if host.startswith(('127.0.0.1:', 'localhost:')) else 'https'
        origin = scheme + '://' + host
        self.send_bytes({'attachment': {'url': origin + '/attachment/' + topic}})

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path == '/health':
            return self.send_bytes({'ok': True, 'protocol': 'ourfamily-relay-v1'})
        if url.path.startswith('/attachment/'):
            name = url.path.split('/')[-1]
            target = ROOT / 'files' / name
            if not FILE.fullmatch(name) or not target.is_file():
                return self.send_bytes({'error': 'file not found'}, 404)
            return self.send_bytes(target.read_bytes(), content_type='application/octet-stream')
        if url.path == '/quote/today':
            day = utc_moscow_day()
            cached = metadata('quote-' + day)
            if cached:
                return self.send_bytes(json.loads(cached))
            seed = int(day.replace('-', '')) % 1000000
            quote = None
            try:
                with urlopen('https://api.forismatic.com/api/1.0/?method=getQuote&format=json&lang=ru&key=' + str(seed), timeout=5.5) as res:
                    item = json.load(res)
                text, author = str(item.get('quoteText', '')).strip(), str(item.get('quoteAuthor', '')).strip()
                if 20 <= len(text) <= 350 and 2 <= len(author) <= 90:
                    quote = dict(date=day, text=text, author=author, source='Forismatic', sourceUrl='https://www.forismatic.com/')
            except Exception:
                pass
            if quote is None:
                text, author = QUOTES[datetime.date.fromisoformat(day).toordinal() % len(QUOTES)]
                quote = dict(date=day, text=text, author=author, source='Викицитатник', sourceUrl='https://ru.wikiquote.org/wiki/Шаблон:Избранная_цитата/Архив/2007')
            metadata('quote-' + day, json.dumps(quote, ensure_ascii=False))
            return self.send_bytes(quote)
        if url.path in ('/news/auto', '/news/auto-status'):
            return self.news(url.path)
        match = re.fullmatch(r'/([A-Za-z0-9_-]{12,100})/json', url.path)
        if not match:
            return self.send_bytes({'error': 'route not found'}, 404)
        topic, params = match[1], parse_qs(url.query)
        try:
            wait = min(25, max(0, int(params.get('wait', ['0'])[0])))
        except ValueError:
            wait = 0
        with WAKE:
            result = read_events(topic, params)
            deadline = time.monotonic() + wait
            while not result and wait and time.monotonic() < deadline:
                WAKE.wait(deadline - time.monotonic())
                result = read_events(topic, params)
        self.send_bytes(result.encode(), content_type='application/x-ndjson; charset=utf-8')

    def news(self, path):
        now = int(time.time() * 1000)
        if path.endswith('status'):
            count = DB.execute('SELECT COUNT(*) FROM articles').fetchone()[0]
            return self.send_bytes({'count': count, 'lastError': metadata('news_error') or '', 'lastTry': int(metadata('news_last_try') or 0)})
        day = utc_moscow_day()
        with LOCK:
            count = DB.execute('SELECT COUNT(*) FROM articles WHERE day=?', (day,)).fetchone()[0]
            last = int(metadata('news_last_try') or 0)
            if count < 2 and now - last >= 900000:
                metadata('news_last_try', str(now))
                try:
                    with urlopen(Request('https://wildcar.org/news/rss.xml', headers={'Accept': 'application/rss+xml, application/xml'}), timeout=10) as res:
                        xml = ElementTree.fromstring(res.read())
                    added = 0
                    for item in xml.findall('.//item')[:25]:
                        if count + added >= 2:
                            break
                        title = ' '.join((item.findtext('title') or '').split())[:250]
                        summary = ' '.join((item.findtext('description') or title).split())[:520]
                        link = (item.findtext('link') or '').strip()
                        if not title or not summary or not link.startswith('https://'):
                            continue
                        ident = hashlib.sha256(link.encode()).hexdigest()[:24]
                        if DB.execute('SELECT 1 FROM articles WHERE id=?', (ident,)).fetchone():
                            continue
                        article = dict(id=ident, title=title, summary=summary, image='', source='Позитивные новости', link=link, addedAt=now + added)
                        DB.execute('INSERT INTO articles VALUES (?,?,?,?)', (ident, day, json.dumps(article, ensure_ascii=False), now + added))
                        added += 1
                    DB.execute('DELETE FROM articles WHERE id NOT IN (SELECT id FROM articles ORDER BY added_at DESC LIMIT 20)')
                    metadata('news_error', '')
                except Exception as error:
                    metadata('news_error', str(error)[:180])
            articles = [json.loads(row[0]) for row in DB.execute('SELECT data FROM articles ORDER BY added_at DESC LIMIT 20')]
        self.send_bytes(articles)


if __name__ == '__main__':
    port = os.environ.get('PORT') or os.environ.get('FAMILY_PORT', '8787')
    default_bind = '0.0.0.0' if os.environ.get('PORT') else '127.0.0.1'
    server = ThreadingHTTPServer((os.environ.get('FAMILY_BIND', default_bind), int(port)), Handler)
    server.daemon_threads = True
    print('OurFamily home relay listening on', server.server_address, flush=True)
    server.serve_forever()
