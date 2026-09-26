import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { AutoNews } from '../src/index.js';

test('daily internet quote is shared by all clients and cached for Moscow date', async () => {
  const entries = new Map();
  const storage = {
    sql: { exec: () => ({}) },
    get: async key => entries.get(key),
    put: async (key, value) => { entries.set(key, value); }
  };
  const quoteObject = new AutoNews({ storage });
  const env = { AUTO_NEWS: { idFromName: name => name, get: () => ({ fetch: url => quoteObject.fetch(new Request(url)) }) } };
  const originalFetch = globalThis.fetch;
  let calls = 0;
  try {
    globalThis.fetch = async url => {
      calls++;
      assert.match(url, /^https:\/\/api\.forismatic\.com\//);
      return Response.json({ quoteText: 'Дорогу осилит идущий.', quoteAuthor: 'Автор' });
    };
    const first = await worker.fetch(new Request('https://family.example/quote/today'), env);
    assert.equal(first.status, 200);
    const picked = await first.json();
    assert.match(picked.date, /^\d{4}-\d{2}-\d{2}$/);
    assert.equal(picked.text, 'Дорогу осилит идущий.');
    assert.equal((await (await worker.fetch(new Request('https://family.example/quote/today'), env)).json()).text, picked.text);
    assert.equal(calls, 1);
    entries.set('dailyQuoteV2', { ...picked, date: '2000-01-01' });
    entries.set('dailyQuoteLastTryV2', 0);
    assert.equal((await (await worker.fetch(new Request('https://family.example/quote/today'), env)).json()).date, picked.date);
    assert.equal(calls, 2);
  } finally { globalThis.fetch = originalFetch; }
});

test('Russian archive selection survives provider outage', async () => {
  const storage = {
    sql: { exec: () => ({}) }, get: async () => undefined, put: async () => {}
  };
  const quoteObject = new AutoNews({ storage });
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response('', { status: 503 });
    const result = await quoteObject.fetch(new Request('https://internal/quote'));
    assert.equal(result.status, 200);
    const quote = await result.json();
    assert.equal(quote.source, 'Викицитатник');
    assert.match(quote.text, /[А-Яа-яЁё]/);
  } finally { globalThis.fetch = originalFetch; }
});
