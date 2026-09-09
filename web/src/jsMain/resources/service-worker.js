// Network-first resources keep releases fresh; cached resources support offline play.
const CACHE_NAME = 'nice-sudoku-v1.3.1';
const STATIC_CACHE_URLS = [
  '/', '/index.html', '/web.js?v=1.3.1', '/manifest.json', '/CHANGELOG.md?v=v1.3.1',
  '/favicon.svg', '/favicon.ico', '/icon-192.png', '/icon-512.png',
  '/icon-maskable-512.png', '/apple-touch-icon.png',
  ...['en', 'es', 'de', 'zh', 'hi', 'fr', 'ar', 'bn', 'ru', 'pt', 'ur'].map(code => `/languages/${code}.json`),
  ...['beginner', 'easy', 'medium', 'tough', 'hard', 'expert', 'diabolical'].map(level => `/puzzles/${level}.json`)
];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(STATIC_CACHE_URLS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(names => Promise.all(names
    .filter(name => name.startsWith('nice-sudoku-') && name !== CACHE_NAME)
    .map(name => caches.delete(name)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') || url.pathname === '/health') return;
  const navigation = request.mode === 'navigate';
  const resource = STATIC_CACHE_URLS.some(path => new URL(path, url.origin).pathname === url.pathname);
  if (!navigation && !resource) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    try {
      const response = await fetch(request, { cache: 'no-cache' });
      if (response.ok) await cache.put(request, response.clone());
      return response;
    } catch (error) {
      const cached = await cache.match(request) || (navigation ? await cache.match('/index.html') : null);
      if (cached) return cached;
      throw error;
    }
  })());
});
