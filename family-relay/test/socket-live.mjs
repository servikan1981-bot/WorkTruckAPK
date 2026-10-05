import test from 'node:test';
import assert from 'node:assert/strict';

const relay = process.env.RELAY_TEST_URL || 'http://127.0.0.1:8787';
const topic = 'of5-' + crypto.randomUUID().replaceAll('-', '');

test('hibernating subscription delivers a new message to existing Android long polls', async () => {
  const read = fetch(`${relay}/${topic}/json?since=10m&after=0&wait=20`);
  await new Promise(resolve => setTimeout(resolve, 300));
  const post = await fetch(relay + '/', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic, message: 'test-first' }) });
  assert.equal(post.status, 200, await post.text());
  const received = await read;
  assert.equal(received.status, 200);
  const [event] = (await received.text()).trim().split('\n').map(JSON.parse);
  assert.equal(event.message, 'test-first');
  assert.ok(event.seq > 0);

  const pending = fetch(`${relay}/${topic}/json?since=10m&after=${event.seq}&wait=20`);
  await new Promise(resolve => setTimeout(resolve, 300));
  const next = await fetch(relay + '/', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic, message: 'test-second' }) });
  assert.equal(next.status, 200, await next.text());
  const [second] = (await (await pending).text()).trim().split('\n').map(JSON.parse);
  assert.equal(second.message, 'test-second');
  assert.ok(second.seq > event.seq);
});
