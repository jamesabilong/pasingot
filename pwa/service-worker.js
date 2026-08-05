// =============================================================================
// service-worker.js
// Cache-first for the app shell (static, versioned assets).
// Network-first, falling back to cache, for anything else that could change.
// =============================================================================

const CACHE_VERSION = 'v4';
const CACHE_NAME = `workout-app-shell-${CACHE_VERSION}`;

const APP_SHELL = [
  './',
  './index.html',
  './app.js',
  './manifest.json',
  './vendor/tailwind.css',
  './vendor/papaparse.min.js',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

function isAppShellRequest(url) {
  const path = new URL(url).pathname;
  return APP_SHELL.some((asset) => path.endsWith(asset.replace('./', '/')) || path.endsWith(asset.replace('./', '')));
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  if (request.mode === 'navigate' || isAppShellRequest(request.url)) {
    // Cache-first: app shell rarely changes and must work fully offline.
    event.respondWith(
      caches.match(request).then((cached) => {
        if (cached) return cached;
        return fetch(request).then((response) => {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
          return response;
        });
      }).catch(() => caches.match('./index.html'))
    );
    return;
  }

  // Network-first, falling back to cache: anything else that could change.
  event.respondWith(
    fetch(request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
        return response;
      })
      .catch(() => caches.match(request))
  );
});
