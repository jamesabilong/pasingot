// Cache-first for versioned app shell assets; network-first for data that can change.
const CACHE_VERSION = 'v12-native-cache-fix';
const CACHE_NAME = `workout-app-shell-${CACHE_VERSION}`;
const APP_SHELL = [
  './',
  './index.html',
  './assets/app.js',
  './assets/index.css',
  './data/exercises.csv',
  './data/quest-templates.csv',
  './data/quest-workouts.csv',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});

function isAppShellRequest(url) {
  const path = new URL(url).pathname;
  return APP_SHELL.some((asset) => path.endsWith(asset.replace('./', '/')));
}

function cacheFirst(request) {
  return caches.match(request, { ignoreVary: true }).then((cached) => cached || fetch(request).then((response) => {
    caches.open(CACHE_NAME).then((cache) => cache.put(request, response.clone()));
    return response;
  }));
}

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  if (event.request.mode === 'navigate') {
    event.respondWith(cacheFirst(event.request).catch(() => caches.match('./index.html')));
    return;
  }
  if (isAppShellRequest(event.request.url)) {
    event.respondWith(cacheFirst(event.request));
    return;
  }
  event.respondWith(fetch(event.request).then((response) => {
    caches.open(CACHE_NAME).then((cache) => cache.put(event.request, response.clone()));
    return response;
  }).catch(() => caches.match(event.request)));
});
