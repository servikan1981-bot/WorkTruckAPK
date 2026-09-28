const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const html = fs.readFileSync('duoapp/src/main/assets/index.html', 'utf8');
function section(from, to) {
  const start = html.indexOf(from), end = html.indexOf(to, start);
  assert(start >= 0 && end > start);
  return html.slice(start, end);
}
const source = section('function iceServers()', 'function stopRingback()') +
  section('async function makeOffer(', 'async function restartPeer(');

const handlers = new Map();
const pc = {
  localDescription: null,
  iceGatheringState: 'gathering',
  createOffer: async () => ({ type: 'offer', sdp: 'v=0\r\n' }),
  async setLocalDescription(desc) { this.localDescription = desc; },
  addEventListener(name, fn) { handlers.set(name, fn); },
  removeEventListener(name) { handlers.delete(name); },
};
const published = [];
const ps = { pc, sdpPublishing: false };
const context = {
  Promise, setTimeout, clearTimeout,
  turnUrl: 'turn:192.168.1.139:3478?transport=udp',
  turnUser: 'familyvideo', turnPass: 'secret',
  callPeers: { sveta: ps }, activeCall: { id: 'call1' },
  createPeer: () => ps, callSignalKind: () => 'direct_offer',
  publishEnvelope: async (_peer, _kind, data) => { published.push(data.sdp.sdp); },
  drainCandidateQueue() {}, markCall() {},
};
vm.createContext(context);
vm.runInContext(source, context);

(async () => {
  const servers = context.iceServers();
  assert.equal(servers.length, 1, 'a configured TURN call must not wait on public STUN servers');
  assert.equal(servers[0].username, 'familyvideo');

  const offer = context.makeOffer('sveta', false);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(published.length, 0, 'offer must wait for a relay candidate');
  pc.localDescription.sdp += 'a=candidate:1 1 udp 123 192.168.1.139 49160 typ relay\r\n';
  handlers.get('icecandidate')({ candidate: { candidate: 'candidate:1 1 udp 123 192.168.1.139 49160 typ relay' } });
  await offer;
  assert.equal(published.length, 1);
  assert.match(published[0], /typ relay/);
  assert.equal(handlers.size, 0, 'ICE gathering listeners must be removed');

  context.turnUrl = '';
  assert.equal(context.iceServers().length, 2, 'calls without TURN keep STUN fallback');
  console.log('PASS: TURN offer carries a relay candidate in the first signal');
})().catch(err => { console.error(err); process.exitCode = 1; });
