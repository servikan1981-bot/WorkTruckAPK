// Small ntfy-compatible relay for the native Android transport. Messages and
// attachments are opaque ciphertext; the family secret stays on the phones.
const topicPattern = /^[A-Za-z0-9_-]{12,100}$/;
const filePattern = /^of5file-[a-f0-9]{32}$/;
const familyNewsPattern = /^of5n-[a-f0-9]{48}$/;
const json = (value, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });

export class TopicMailbox {
  constructor(ctx) {
    this.ctx = ctx;
    this.waiters = new Set();
    this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, time INTEGER NOT NULL, message TEXT NOT NULL)');
    this.ctx.storage.sql.exec('CREATE INDEX IF NOT EXISTS events_time ON events(time)');
    this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS news_dedupe (client_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL)');
  }

  makeWaiter(timeoutMs) {
    let resolvePromise;
    let timer;
    let finished = false;
    const waiter = {};
    const promise = new Promise(resolve => { resolvePromise = resolve; });
    const finish = () => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      this.waiters.delete(waiter);
      resolvePromise();
    };
    waiter.finish = finish;
    this.waiters.add(waiter);
    timer = setTimeout(finish, timeoutMs);
    return { promise, cancel: finish };
  }

  wakeWaiters() {
    for (const waiter of [...this.waiters]) waiter.finish();
  }

  rowsFor(url, topic) {
    const afterRaw = url.searchParams.get('after') || '';
    const after = /^\d+$/.test(afterRaw) ? Number.parseInt(afterRaw, 10) : 0;
    if (after > 0) {
      return this.ctx.storage.sql.exec(
        'SELECT seq, id, time, message FROM events WHERE seq > ? ORDER BY seq ASC LIMIT 800', after
      ).toArray();
    }

    const longNews = familyNewsPattern.test(topic) && url.searchParams.get('since') === '90d';
    const duration = /^\d{1,2}m$/.test(url.searchParams.get('since') || '')
      ? Math.min(10, Number.parseInt(url.searchParams.get('since'), 10)) : 10;
    const since = Math.floor(Date.now() / 1000) - (longNews ? 90 * 86400 : duration * 60);
    return longNews
      ? this.ctx.storage.sql.exec('SELECT seq, id, time, message FROM events WHERE time >= ? ORDER BY seq DESC LIMIT 300', since).toArray().reverse()
      : this.ctx.storage.sql.exec('SELECT seq, id, time, message FROM events WHERE time >= ? ORDER BY seq ASC LIMIT 800', since).toArray();
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (request.method === 'POST') {
      const { topic, message } = await request.json();
      const time = Math.floor(Date.now() / 1000);
      const newsId = familyNewsPattern.test(topic) && /^of6news\|([a-f0-9]{32})\|[A-Za-z0-9+/=]+$/.exec(message)?.[1];
      if (familyNewsPattern.test(topic) && !newsId) return json({ error: 'invalid encrypted news' }, 400);
      if (newsId) {
        const inserted = this.ctx.storage.sql.exec('INSERT OR IGNORE INTO news_dedupe (client_id, created_at) VALUES (?, ?)', newsId, time);
        if (!inserted.rowsWritten) return json({ ok: true, duplicate: true });
      }
      const event = { id: crypto.randomUUID(), time, event: 'message', topic, message };
      this.ctx.storage.sql.exec('INSERT INTO events (id, time, message) VALUES (?, ?, ?)', event.id, time, message);
      const retention = familyNewsPattern.test(topic) ? 90 * 86400 : topic.startsWith('of5p-') ? 300 : 86400;
      this.ctx.storage.sql.exec('DELETE FROM events WHERE time < ?', time - retention);
      if (newsId) this.ctx.storage.sql.exec('DELETE FROM news_dedupe WHERE created_at < ?', time - retention);
      this.wakeWaiters();
      return json(event);
    }

    const topic = request.headers.get('X-Topic');
    const waitSeconds = Math.max(0, Math.min(25, Number.parseInt(url.searchParams.get('wait') || '0', 10) || 0));
    let waiter = null;
    if (waitSeconds > 0) waiter = this.makeWaiter(waitSeconds * 1000);

    let rows = this.rowsFor(url, topic);
    if (!rows.length && waiter) {
      await waiter.promise;
      rows = this.rowsFor(url, topic);
    } else if (waiter) {
      waiter.cancel();
    }

    const lines = rows.map(row => JSON.stringify({
      seq: row.seq, id: row.id, time: row.time, event: 'message', topic, message: row.message
    }));
    return new Response(lines.length ? lines.join('\n') + '\n' : '', {
      headers: { 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-store' }
    });
  }
}

const textOf = (xml, name) => {
  const match = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${name}>`, 'i').exec(xml);
  return (match?.[1] || '').replace(/^<!\[CDATA\[|\]\]>$/g, '').replace(/<[^>]*>/g, ' ')
    .replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/\s+/g, ' ').trim();
};

const moscowDay = now => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit'
}).format(now);

async function internetQuote(day) {
  // Forismatic supports Russian and a numeric key, so the daily pick is stable.
  const seed = Number(day.replaceAll('-', '')) % 1_000_000;
  try {
    const url = `https://api.forismatic.com/api/1.0/?method=getQuote&format=json&lang=ru&key=${seed}`;
    const response = await globalThis.fetch(url, { signal: AbortSignal.timeout(5500) });
    if (response.ok) {
      const data = await response.json();
      const text = String(data.quoteText || '').replace(/\s+/g, ' ').trim();
      const author = String(data.quoteAuthor || '').replace(/\s+/g, ' ').trim();
      if (text.length >= 20 && text.length <= 350 && author.length >= 2 && author.length <= 90)
        return { text, author, source: 'Forismatic', sourceUrl: 'https://www.forismatic.com/' };
    }
  } catch (_) { /* Fall back to the daily source. */ }
  // The public quote-of-the-day endpoint needs no account or token.
  const response = await globalThis.fetch('https://favqs.com/api/qotd', {
    signal: AbortSignal.timeout(6500), headers: { Accept: 'application/vnd.favqs.v2+json' }
  });
  if (!response.ok) throw new Error('quote providers unavailable');
  const data = await response.json();
  const text = String(data.quote?.body || '').replace(/\s+/g, ' ').trim();
  const author = String(data.quote?.author || '').replace(/\s+/g, ' ').trim();
  if (data.error_code || text.length < 20 || text.length > 350 || author.length < 2 || author.length > 90)
    throw new Error('quote provider returned invalid data');
  return { text, author, source: 'FavQs', sourceUrl: 'https://favqs.com/' };
}

export class AutoNews {
  constructor(ctx) {
    this.ctx = ctx;
    this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS articles (id TEXT PRIMARY KEY, day TEXT NOT NULL, data TEXT NOT NULL, added_at INTEGER NOT NULL)');
    this.ctx.storage.sql.exec('CREATE INDEX IF NOT EXISTS articles_newest ON articles(added_at DESC)');
  }

  async fetch(request) {
    const pathname = new URL(request.url).pathname;
    if (pathname === '/quote') {
      const day = moscowDay(Date.now());
      const previous = await this.ctx.storage.get('dailyQuote');
      if (previous?.date === day) return json(previous);
      const now = Date.now();
      if (now - ((await this.ctx.storage.get('dailyQuoteLastTry')) || 0) < 60_000)
        return json({ error: 'quote pending' }, 503);
      await this.ctx.storage.put('dailyQuoteLastTry', now);
      try {
        const quote = { date: day, ...await internetQuote(day) };
        await this.ctx.storage.put('dailyQuote', quote);
        return json(quote);
      } catch (_) {
        return json({ error: 'quote unavailable' }, 503);
      }
    }
    if (pathname === '/status') {
      return json({ count: this.ctx.storage.sql.exec('SELECT COUNT(*) AS total FROM articles').one().total,
        lastError: (await this.ctx.storage.get('lastError')) || '', lastTry: (await this.ctx.storage.get('lastTryAutoV4')) || 0 });
    }
    const now = Date.now();
    const day = moscowDay(now);
    const count = this.ctx.storage.sql.exec('SELECT COUNT(*) AS total FROM articles WHERE day = ?', day).one().total;
    const lastTry = (await this.ctx.storage.get('lastTryAutoV4')) || 0;
    if (count < 2 && now - lastTry >= 15 * 60_000) {
      await this.ctx.storage.put('lastTryAutoV4', now);
      try {
        const response = await globalThis.fetch('https://wildcar.org/news/rss.xml', { signal: AbortSignal.timeout(10000), headers: { 'Accept': 'application/rss+xml, application/xml' } });
        if (!response.ok) throw new Error('RSS unavailable');
        const xml = await response.text();
        const items = [...xml.matchAll(/<item(?:\s[^>]*)?>([\s\S]*?)<\/item>/gi)].slice(0, 25);
        if (!items.length) throw new Error('RSS contained no items');
        let added = 0;
        for (const [, item] of items) {
          if (count + added >= 2) break;
          const title = textOf(item, 'title').slice(0, 250);
          const summary = (textOf(item, 'description') || textOf(item, 'content:encoded') || textOf(item, 'summary') || title).slice(0, 520);
          const link = textOf(item, 'link');
          if (!title || !summary || !/^https:\/\//.test(link)) continue;
          const id = [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(link)))].map(v => v.toString(16).padStart(2, '0')).join('').slice(0, 24);
          if (this.ctx.storage.sql.exec('SELECT id FROM articles WHERE id = ?', id).toArray().length) continue;
          const image = /<enclosure[^>]*\burl=["'](https:[^"']+)["'][^>]*\btype=["']image\//i.exec(item)?.[1] || '';
          const article = { id, title, summary, image, source: 'Позитивные новости', link, addedAt: now + added };
          this.ctx.storage.sql.exec('INSERT INTO articles (id, day, data, added_at) VALUES (?, ?, ?, ?)', id, day, JSON.stringify(article), now + added);
          added++;
        }
        this.ctx.storage.sql.exec('DELETE FROM articles WHERE id NOT IN (SELECT id FROM articles ORDER BY added_at DESC LIMIT 20)');
        if (!added && count === 0) throw new Error('RSS contained no new usable articles');
        await this.ctx.storage.delete('lastError');
      } catch (error) { await this.ctx.storage.put('lastError', String(error).slice(0,180)); }
    }
    return json(this.ctx.storage.sql.exec('SELECT data FROM articles ORDER BY added_at DESC LIMIT 20').toArray().map(row => JSON.parse(row.data)));
  }
}

// One isolated SQLite-backed object per random attachment URL. 512 KiB chunks
// fit below the 2 MiB individual value limit; an alarm removes files after 90 days.
export class EncryptedFile {
  constructor(ctx) { this.ctx = ctx; }

  async fetch(request) {
    if (request.method === 'PUT') {
      const bytes = new Uint8Array(await request.arrayBuffer());
      if (!bytes.length || bytes.length > 13_000_000) return json({ error: 'file too large' }, 413);
      if (await this.ctx.storage.get('length')) return json({ error: 'file exists' }, 409);
      const entries = {};
      for (let offset = 0, index = 0; offset < bytes.length; offset += 524288, index++)
        entries[`part-${String(index).padStart(3, '0')}`] = bytes.slice(offset, offset + 524288);
      await this.ctx.storage.put(entries);
      await this.ctx.storage.put('length', bytes.length);
      await this.ctx.storage.setAlarm(Date.now() + 90 * 86400_000);
      return json({ ok: true });
    }
    const length = await this.ctx.storage.get('length');
    if (!length) return json({ error: 'file not found' }, 404);
    const result = new Uint8Array(length);
    for (let offset = 0, index = 0; offset < length; offset += 524288, index++) {
      const part = await this.ctx.storage.get(`part-${String(index).padStart(3, '0')}`);
      if (!part) return json({ error: 'file unavailable' }, 503);
      result.set(part, offset);
    }
    return new Response(result, { headers: { 'Content-Type': 'application/octet-stream', 'Cache-Control': 'private, max-age=3600' } });
  }

  async alarm() { await this.ctx.storage.deleteAll(); }
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (request.method === 'GET' && url.pathname === '/health') return json({ ok: true, protocol: 'ourfamily-relay-v1' });
      if (request.method === 'GET' && url.pathname === '/news/auto')
        return env.AUTO_NEWS.get(env.AUTO_NEWS.idFromName('shared-positive-news')).fetch('https://internal/news');
      if (request.method === 'GET' && url.pathname === '/news/auto-status')
        return env.AUTO_NEWS.get(env.AUTO_NEWS.idFromName('shared-positive-news')).fetch('https://internal/status');
      if (request.method === 'GET' && url.pathname === '/quote/today')
        return env.AUTO_NEWS.get(env.AUTO_NEWS.idFromName('shared-positive-news')).fetch('https://internal/quote');

      const attachment = /^\/attachment\/(of5file-[a-f0-9]{32})$/.exec(url.pathname);
      if (attachment && request.method === 'GET') {
        return env.FILES.get(env.FILES.idFromName(attachment[1])).fetch('https://internal/file');
      }

      const path = url.pathname.split('/').filter(Boolean);
      if (path.length === 1 && filePattern.test(path[0]) && request.method === 'PUT') {
        if (Number(request.headers.get('Content-Length') || 0) > 13_000_000) return json({ error: 'file too large' }, 413);
        const bytes = await request.arrayBuffer();
        if (!bytes.byteLength || bytes.byteLength > 13_000_000) return json({ error: 'file too large' }, 413);
        const response = await env.FILES.get(env.FILES.idFromName(path[0])).fetch(
          new Request('https://internal/file', { method: 'PUT', body: bytes }));
        if (!response.ok) return response;
        return json({ attachment: { url: `${url.origin}/attachment/${path[0]}` } });
      }

      let topic, message;
      if (path.length === 0 && request.method === 'POST') {
        const body = await request.json();
        topic = body.topic;
        message = body.message;
      } else if (path.length === 2 && path[1] === 'json' && request.method === 'GET') {
        topic = path[0];
      } else {
        return json({ error: 'route not found' }, 404);
      }
      if (typeof topic !== 'string' || !topicPattern.test(topic)) return json({ error: 'invalid topic' }, 400);
      if (request.method === 'POST' && (typeof message !== 'string' || !message.length || message.length > 8000))
        return json({ error: 'invalid message' }, 400);

      const id = env.MAILBOX.idFromName(topic);
      const mailbox = env.MAILBOX.get(id);
      if (request.method === 'POST') {
        return mailbox.fetch(new Request('https://internal/post', {
          method: 'POST', body: JSON.stringify({ topic, message })
        }));
      }
      const query = new URLSearchParams({
        since: url.searchParams.get('since') || '10m',
        after: url.searchParams.get('after') || '0',
        wait: url.searchParams.get('wait') || '0'
      });
      return mailbox.fetch(new Request(`https://internal/list?${query.toString()}`, {
        headers: { 'X-Topic': topic }
      }));
    } catch (error) {
      return json({ error: 'relay unavailable' }, 503);
    }
  }
};
