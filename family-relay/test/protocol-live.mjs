import test from 'node:test';
import assert from 'node:assert/strict';

const relay = process.env.RELAY_TEST_URL || 'http://127.0.0.1:8787';
const topic = 'of5-' + crypto.randomUUID().replaceAll('-', '');

test('local Cloudflare runtime accepts Android-compatible messages, presence, and files', async () => {
  const health = await fetch(relay + '/health');
  assert.equal((await health.json()).ok, true);

  const wire = 'of5|sender|receiver|direct_chat|message|group|0|1|encrypted-data';
  const post = await fetch(relay + '/', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'User-Agent': 'OurFamily/6.0.5 Android' },
    body: JSON.stringify({ topic, message: wire, priority: 3 })
  });
  assert.equal(post.status, 200, await post.text());
  const received = await fetch(relay + '/' + topic + '/json?since=10m', {
    headers: { 'User-Agent': 'Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7)' }
  });
  const events = (await received.text()).trim().split('\n').map(JSON.parse);
  assert.equal(events.length, 1);
  assert.equal(events[0].message, wire);

  const fileTopic = 'of5file-' + crypto.randomUUID().replaceAll('-', '');
  const payload = new Uint8Array(2_500_000);
  payload.fill(178);
  const upload = await fetch(relay + '/' + fileTopic, { method: 'PUT', body: payload });
  if (!upload.ok) assert.fail('upload failed: ' + await upload.text());
  const file = (await upload.json()).attachment.url;
  assert.deepEqual(new Uint8Array(await (await fetch(file)).arrayBuffer()), payload);

  const presenceTopic = 'of5p-' + crypto.randomUUID().replaceAll('-', '');
  for (const state of ['on', 'off']) {
    const answer = await fetch(relay + '/', { method: 'POST',
      body: JSON.stringify({ topic: presenceTopic, message: `of5presence|tag|${Date.now()}|${state}` }) });
    assert.equal(answer.status, 200);
  }
  const statuses = await fetch(relay + '/' + presenceTopic + '/json?since=2m');
  assert.match(await statuses.text(), /\|off/);
});

test('live long polling delivers chat and call signaling without multi-second gaps', async () => {
  const topicA = 'of5-' + crypto.randomUUID().replaceAll('-', '');
  const topicB = 'of5-' + crypto.randomUUID().replaceAll('-', '');

  async function waitNext(topic, after) {
    const started = Date.now();
    const response = await fetch(relay + '/' + topic + '/json?since=10m&after=' + after + '&wait=25');
    assert.equal(response.status, 200);
    const text = (await response.text()).trim();
    const events = text ? text.split('\n').map(JSON.parse) : [];
    return { events, elapsed: Date.now() - started };
  }

  async function publish(topic, message) {
    const response = await fetch(relay + '/', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'User-Agent': 'OurFamily/6.0.11 Android' },
      body: JSON.stringify({ topic, message, priority: 5 })
    });
    assert.equal(response.status, 200, await response.text());
  }

  let a = 0, b = 0;
  const stages = [
    [topicB, 'direct_invite'],
    [topicA, 'direct_accept'],
    [topicB, 'direct_offer'],
    [topicA, 'direct_answer'],
    [topicB, 'direct_candidate']
  ];

  for (const [topic, kind] of stages) {
    const after = topic === topicA ? a : b;
    const waiting = waitNext(topic, after);
    await new Promise(resolve => setTimeout(resolve, 120));
    await publish(topic, 'of5|sender|receiver|' + kind + '|call|group|0|1|ciphertext');
    const { events, elapsed } = await waiting;
    assert.equal(events.length, 1, kind + ' must deliver exactly once');
    assert.equal(events[0].message.includes('|' + kind + '|'), true);
    assert.ok(events[0].seq > after);
    assert.ok(elapsed < 2500, kind + ' signaling latency too high: ' + elapsed + 'ms');
    if (topic === topicA) a = events[0].seq; else b = events[0].seq;
  }
});

test('all family devices read identical encrypted family and automatic news', async () => {
  const topic = 'of5n-' + crypto.randomUUID().replaceAll('-', '') + 'a'.repeat(16);
  const secret = 'Family-test-' + crypto.randomUUID();
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey({ name: 'PBKDF2', salt: new TextEncoder().encode('OurFamily-v6-news-key'), iterations: 220000, hash: 'SHA-256' }, base, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
  const id = crypto.randomUUID().replaceAll('-', '');
  const post = { id, from: 'sergey', text: 'Одна новость для всей семьи', attachment: null, ts: Date.now() };
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(post))));
  const bytes = new Uint8Array(iv.length + ciphertext.length); bytes.set(iv); bytes.set(ciphertext, iv.length);
  const wire = 'of6news|' + id + '|' + Buffer.from(bytes).toString('base64');
  for (let attempt = 0; attempt < 2; attempt++) {
    const published = await fetch(relay + '/', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ topic, message: wire }) });
    assert.equal(published.status, 200, await published.text());
  }
  const read = async () => {
    const response = await fetch(relay + '/' + topic + '/json?since=90d');
    assert.equal(response.status, 200);
    return (await response.text()).trim().split('\n').map(JSON.parse);
  };
  const onSergey = await read(), onSveta = await read();
  assert.deepEqual(onSergey, onSveta);
  assert.equal(onSergey.length, 1, 'retry must not create duplicate news');
  const encoded = onSveta[0].message.split('|')[2];
  const received = Buffer.from(encoded, 'base64');
  const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: received.subarray(0, 12) }, key, received.subarray(12));
  assert.deepEqual(JSON.parse(new TextDecoder().decode(plain)), post);
  const malformed = await fetch(relay + '/', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ topic, message: 'plain-text-news' }) });
  assert.equal(malformed.status, 400);

  const first = await fetch(relay + '/news/auto'), second = await fetch(relay + '/news/auto');
  assert.equal(first.status, 200); assert.equal(second.status, 200);
  const a = await first.json(), b = await second.json();
  assert.deepEqual(a, b);
  if (!Array.isArray(a) || a.length < 2 || a.length > 20) {
    const diagnostic = await (await fetch(relay + '/news/auto-status')).text();
    assert.fail('shared source must supply the daily two stories; status=' + diagnostic);
  }
});

test('all family devices receive the same daily internet quote', async () => {
  let first;
  for (let attempt = 0; attempt < 6; attempt++) {
    const response = await fetch(relay + '/quote/today');
    if (response.ok) { first = await response.json(); break; }
    if (attempt === 5) assert.fail('daily quote unavailable: ' + await response.text());
    await new Promise(resolve => setTimeout(resolve, 12000));
  }
  const second = await (await fetch(relay + '/quote/today')).json();
  assert.deepEqual(first, second);
  assert.match(first.date, /^\d{4}-\d{2}-\d{2}$/);
  assert.ok(first.text.length >= 20 && first.author.length >= 2);
  assert.ok(['Forismatic', 'FavQs'].includes(first.source));
});
