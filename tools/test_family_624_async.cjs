const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const html = fs.readFileSync('duoapp/src/main/assets/index.html', 'utf8');
const start = html.indexOf('window.__ourFamilyRelayResult=function');
const end = html.indexOf('function ackRaw(ids)', start);
assert(start > 0 && end > start, 'relay implementation missing');
const calls = [];
const context = {
  window: {}, Map, Promise, JSON, String, Error, setTimeout, clearTimeout,
  relayRequests: new Map(), randomId: (() => { let n = 0; return () => (++n).toString(16).padStart(32, '0'); })(),
  role: 'sergey', ownTag: 'me', roleTag: async () => 'peer', inboxTopic: async () => '123456789012',
  encryptFor: async (_peer, payload) => JSON.stringify(payload),
  AndroidBridge: {
    sendRelayAsync(...args) { calls.push({ type: 'single', args }); return true; },
    sendRelayBatchAsync(...args) { calls.push({ type: 'batch', args }); return true; },
    queueRelay(...args) { calls.push({ type: 'queued', args }); return 'OK'; },
  },
};
vm.createContext(context);
vm.runInContext(html.slice(start, end), context);

const flush = () => new Promise(resolve => setImmediate(resolve));
(async () => {
  let completed = false;
  const sending = context.publishEnvelope('sveta', 'direct_chat', { text: 'hello' }, 3, 'direct_chat', 'm1')
    .then(() => { completed = true; });
  await flush();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].type, 'single');
  assert.equal(completed, false, 'send must wait for server acknowledgment');
  context.window.__ourFamilyRelayResult(calls[0].args[0], 'OK');
  await sending;
  assert.equal(completed, true);
  assert.equal(context.relayRequests.size, 0);

  const failed = context.publishEnvelope('sveta', 'direct_chat', { text: 'retry' }, 3, 'direct_chat', 'm2');
  await flush();
  context.window.__ourFamilyRelayResult(calls.at(-1).args[0], 'ERR:http:503');
  await assert.rejects(failed, /ERR:http:503/);

  const candidatePromise = context.publishEnvelope('sveta', 'direct_candidate', { candidate: 'abc' }, 4, 'direct_candidate', 'call1', true);
  await flush();
  assert.equal(calls.at(-1).type, 'single', 'ICE must wait for an actual server response');
  context.window.__ourFamilyRelayResult(calls.at(-1).args[0], 'OK');
  const candidate = await candidatePromise;
  assert(candidate.id);

  const rejectedCandidate = context.publishEnvelope('sveta', 'direct_candidate', { candidate: 'abc' }, 4, 'direct_candidate', 'call1', true);
  await flush();
  context.window.__ourFamilyRelayResult(calls.at(-1).args[0], 'ERR:ConnectException');
  await assert.rejects(rejectedCandidate, /ERR:ConnectException/);

  const offer = context.publishEnvelope('sveta', 'direct_offer', { sdp: 'x'.repeat(3900) }, 5, 'direct_offer', 'call1', true);
  await flush();
  assert.equal(calls.at(-1).type, 'batch');
  assert.equal(JSON.parse(calls.at(-1).args[2]).length > 1, true);
  context.window.__ourFamilyRelayResult(calls.at(-1).args[0], 'OK');
  await offer;
  console.log('PASS: async send acknowledgment, candidate failure propagation and SDP batch');
})().catch(error => { console.error(error); process.exitCode = 1; });
