// Offline cache regression checks, with an isolated in-memory browser API.
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const handlers = {}, deleted = [];
const entries = new Map();
let offline = false;
const cache = {
  addAll: async urls => urls.forEach(url => entries.set(url, { tag: 'precache' })),
  match: async request => entries.get(typeof request === 'string' ? request : request.url),
  put: async (request, response) => entries.set(request.url, response)
};
const context = {
  URL, console,
  self: { location: { origin: 'https://sudoku.test' }, addEventListener: (event, fn) => handlers[event] = fn,
    skipWaiting: async () => {}, clients: { claim: async () => {} } },
  caches: { open: async () => cache, keys: async () => ['nice-sudoku-v2', 'unrelated-app'], delete: async key => deleted.push(key) },
  fetch: async () => { if (offline) throw new Error('offline'); return { ok: true, tag: 'fresh', clone() { return this; } }; }
};
vm.runInNewContext(readFileSync('web/src/jsMain/resources/service-worker.js', 'utf8'), context);
async function lifecycle(name) { let promise; handlers[name]({ waitUntil: p => promise = p }); await promise; }
function request(path, options = {}) {
  let result;
  handlers.fetch({ request: { url: `https://sudoku.test${path}`, method: 'GET', mode: 'cors', ...options }, respondWith: p => result = p });
  return result;
}
(async () => {
  await lifecycle('install'); await lifecycle('activate');
  assert.deepEqual(deleted, ['nice-sudoku-v2']);
  assert.equal(request('/api/techniques/find'), undefined);
  assert.equal(request('/health'), undefined);
  assert.equal(request('/index.html', { method: 'POST' }), undefined);
  assert.equal((await request('/index.html')).tag, 'fresh');
  offline = true;
  assert.equal((await request('/index.html')).tag, 'fresh');
  assert.equal((await request('/de/', { mode: 'navigate' })).tag, 'precache');
  assert(entries.has('/languages/de.json'));
  assert(entries.has('/puzzles/easy.json'));
  console.log('Service worker: network-first, offline navigation, cache ownership, and API bypass passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });
