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
