// Small ntfy-compatible relay for the native Android transport. Messages and
// attachments are opaque ciphertext; the family secret stays on the phones.
const topicPattern = /^[A-Za-z0-9_-]{12,100}$/;
const filePattern = /^of5file-[a-f0-9]{32}$/;
const json = (value, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });

export class TopicMailbox {
  constructor(ctx) {
    this.ctx = ctx;
    this.ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL, time INTEGER NOT NULL, message TEXT NOT NULL)');
    this.ctx.storage.sql.exec('CREATE INDEX IF NOT EXISTS events_time ON events(time)');
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (request.method === 'POST') {
      const { topic, message } = await request.json();
      const time = Math.floor(Date.now() / 1000);
      const event = { id: crypto.randomUUID(), time, event: 'message', topic, message };
      this.ctx.storage.sql.exec('INSERT INTO events (id, time, message) VALUES (?, ?, ?)', event.id, time, message);
      this.ctx.storage.sql.exec('DELETE FROM events WHERE time < ?', time - (topic.startsWith('of5p-') ? 300 : 86400));
      return json(event);
    }
    const duration = /^\d{1,2}m$/.test(url.searchParams.get('since') || '')
      ? Math.min(10, Number.parseInt(url.searchParams.get('since'), 10)) : 10;
    const since = Math.floor(Date.now() / 1000) - duration * 60;
    const rows = this.ctx.storage.sql.exec('SELECT id, time, message FROM events WHERE time >= ? ORDER BY seq ASC LIMIT 800', since).toArray();
    const topic = request.headers.get('X-Topic');
    const lines = rows.map(row => JSON.stringify({ id: row.id, time: row.time, event: 'message', topic, message: row.message }));
    return new Response(lines.length ? lines.join('\n') + '\n' : '', {
      headers: { 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-store' }
    });
  }
}

// One isolated SQLite-backed object per random attachment URL. 512 KiB chunks
// fit below the 2 MiB individual value limit; an alarm removes files after 7 days.
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
      await this.ctx.storage.setAlarm(Date.now() + 7 * 86400_000);
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
      return mailbox.fetch(new Request(`https://internal/list?since=${encodeURIComponent(url.searchParams.get('since') || '10m')}`, {
        headers: { 'X-Topic': topic }
      }));
    } catch (error) {
      return json({ error: 'relay unavailable' }, 503);
    }
  }
};
