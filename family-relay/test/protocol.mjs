import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { TopicMailbox, EncryptedFile } from '../src/index.js';

class MemoryStorage {
  constructor() {
    this.entries = new Map();
    this.events = [];
    this.sql = { exec: (query, ...args) => {
      if (query.startsWith('INSERT')) this.events.push({ seq: this.events.length + 1, id: args[0], time: args[1], message: args[2] });
      if (query.startsWith('DELETE')) this.events = this.events.filter(row => row.time >= args[0]);
      return { toArray: () => this.events.filter(row => row.time >= args[0]).slice(0, 800) };
    } };
  }
  get(key) { return Promise.resolve(this.entries.get(key)); }
  put(key, value) {
    if (typeof key === 'string') this.entries.set(key, value);
    else for (const [k, v] of Object.entries(key)) this.entries.set(k, v);
    return Promise.resolve();
  }
  setAlarm(time) { this.alarmTime = time; return Promise.resolve(); }
  deleteAll() { this.entries.clear(); return Promise.resolve(); }
}

function environment() {
  function namespace(ctor) {
    const instances = new Map();
    return {
      idFromName: name => name,
      get: id => {
        if (!instances.has(id)) instances.set(id, new ctor({ storage: new MemoryStorage() }));
        return instances.get(id);
      }
    };
  }
  return { MAILBOX: namespace(TopicMailbox), FILES: namespace(EncryptedFile) };
}

test('Android publish and recent JSON read preserve encrypted wire message', async () => {
  const env = environment();
  const base = 'https://family.example';
  const topic = 'of5-' + 'a'.repeat(48);
  const wire = 'of5|sender|recipient|direct_chat|id|group|0|1|ciphertext';
  const posted = await worker.fetch(new Request(base + '/', { method: 'POST', body: JSON.stringify({ topic, message: wire, priority: 3 }) }), env);
  assert.equal(posted.status, 200);
  const recent = await worker.fetch(new Request(base + '/' + topic + '/json?since=10m'), env);
  assert.equal(recent.status, 200);
  const [event] = (await recent.text()).trim().split('\n').map(JSON.parse);
  assert.equal(event.message, wire);
  assert.equal(event.topic, topic);
  assert.equal(event.event, 'message');
  assert.match(event.id, /^[0-9a-f-]{36}$/);
  assert.ok(Math.abs(Date.now() / 1000 - event.time) < 5);
});

test('online and offline transitions are delivered in publication order', async () => {
  const env = environment();
  const base = 'https://family.example/';
  const topic = 'of5p-' + 'b'.repeat(48);
  for (const state of ['on', 'off']) {
    const response = await worker.fetch(new Request(base, { method: 'POST', body: JSON.stringify({ topic, message: `of5presence|tag|${Date.now()}|${state}` }) }), env);
    assert.equal(response.status, 200);
  }
  const response = await worker.fetch(new Request(base + topic + '/json?since=2m'), env);
  const events = (await response.text()).trim().split('\n').map(JSON.parse);
  assert.equal(events.length, 2);
  assert.match(events[0].message, /\|on$/);
  assert.match(events[1].message, /\|off$/);
});

test('encrypted attachment survives chunking and expires after alarm', async () => {
  const env = environment();
  const key = 'of5file-' + 'c'.repeat(32);
  const bytes = crypto.getRandomValues(new Uint8Array(65536));
  const attachment = new Uint8Array(2_500_000);
  for (let offset = 0; offset < attachment.length; offset += bytes.length) attachment.set(bytes.subarray(0, Math.min(bytes.length, attachment.length - offset)), offset);
  const response = await worker.fetch(new Request('https://family.example/' + key, { method: 'PUT', body: attachment }), env);
  assert.equal(response.status, 200);
  const { attachment: { url } } = await response.json();
  const received = await worker.fetch(new Request(url), env);
  assert.deepEqual(new Uint8Array(await received.arrayBuffer()), attachment);
  await env.FILES.get(key).alarm();
  assert.equal((await worker.fetch(new Request(url), env)).status, 404);
});
