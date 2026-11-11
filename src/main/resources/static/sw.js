const SHELL_CACHE = 'kaidoku-shell-v1';
const DATA_CACHE_PREFIX = 'kaidoku-data-';
const DATA_VERSION_KEY = '/__data-version';
const MANIFEST_PATH = '/learning/manifest.json';
const PRECACHE = [
  '/',
  '/index.html',
  '/grammar.html',
  '/learn.html',
  '/about.html',
  '/manifest.webmanifest',
  '/css/about.css',
  '/css/base.css',
  '/css/grammar.css',
  '/css/home.css',
  '/css/intro.css',
  '/css/learn.css',
  '/css/quiz.css',
  '/css/tokens.css',
  '/js/grammar.js',
  '/js/home.js',
  '/js/intro-config.js',
  '/js/intro-guard.js',
  '/js/intro.js',
  '/js/shell.js',
  '/js/theme.js',
  '/js/learn/data.js',
  '/js/learn/dom.js',
  '/js/learn/format.js',
  '/js/learn/main.js',
  '/js/learn/progress.js',
  '/js/learn/quiz-view.js',
  '/js/learn/quiz.js',
  '/js/learn/romaji.js',
  '/js/learn/srs.js',
  '/js/learn/store.js',
  '/js/learn/views.js',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/icon-maskable-512.png',
  '/icons/apple-touch-icon.png'
];
const shellPaths = new Set(PRECACHE);
let versionPromise = null;
let manifestRequest = null;
const preparations = new Map();
let preparationQueue = Promise.resolve();

function noStore(response) {
  return /(?:^|,)\s*no-store\s*(?:,|$)/i.test(response.headers.get('Cache-Control') ?? '');
}

function cacheable(response) {
  return response.status === 200 && !response.redirected && response.type !== 'opaqueredirect' && !noStore(response);
}

function currentVersion() {
  versionPromise ??= caches.open(SHELL_CACHE)
      .then(cache => cache.match(DATA_VERSION_KEY))
      .then(response => response ? response.text() : null);
  return versionPromise;
}

async function deleteOldDataCaches(current) {
  const names = await caches.keys();
  await Promise.all(names.filter(name => name.startsWith(DATA_CACHE_PREFIX) && name !== current)
      .map(name => caches.delete(name)));
}

async function saveResponse(cacheName, key, response) {
  if (cacheName && cacheable(response)) {
    const cache = await caches.open(cacheName);
    await cache.put(key, response.clone());
  }
}

async function networkFirst(request, cacheName, key = request, updateCache = true) {
  let response;
  try {
    response = await fetch(request);
  } catch (error) {
    const cached = cacheName ? await caches.match(key, { cacheName }) : null;
    if (cached) {
      return cached;
    }
    throw error;
  }
  if (updateCache) {
    await saveResponse(cacheName, key, response).catch(() => {});
  }
  return response;
}

async function prepare(manifestResponse) {
  if (!cacheable(manifestResponse)) {
    throw new Error('Manifest cannot be cached');
  }
  const { dataset_version: version, artifacts } = await manifestResponse.clone().json();
  if (typeof version !== 'string' || !version.trim() || !artifacts || typeof artifacts !== 'object' || Array.isArray(artifacts)) {
    throw new Error('Invalid learning manifest');
  }
  if (!preparations.has(version)) {
    const preparation = preparationQueue.then(async () => {
      if (version === await currentVersion()) {
        return;
      }
      const cacheName = DATA_CACHE_PREFIX + version;
      try {
        const cache = await caches.open(cacheName);
        const paths = [
          ...Object.keys(artifacts).map(name => `/learning/${name}`),
          '/api/learning/summary',
          '/api/learning/kanji/lessons',
          '/api/learning/vocab/lessons'
        ];
        const downloads = await Promise.allSettled(paths.map(async path => {
          const response = await fetch(new Request(new URL(path, self.location.origin), { cache: 'reload' }));
          if (!cacheable(response)) {
            throw new Error(`Cannot cache ${path}`);
          }
          await cache.put(path, response);
        }));
        const failed = downloads.find(result => result.status === 'rejected');
        if (failed) {
          throw failed.reason;
        }
        await cache.put(MANIFEST_PATH, manifestResponse.clone());
        const shell = await caches.open(SHELL_CACHE);
        await shell.put(DATA_VERSION_KEY, new Response(version));
        versionPromise = Promise.resolve(version);
      } catch (error) {
        await caches.delete(cacheName);
        throw error;
      }
      await deleteOldDataCaches(cacheName).catch(() => {});
    }).finally(() => preparations.delete(version));
    preparations.set(version, preparation);
    preparationQueue = preparation.catch(() => {});
  }
  return preparations.get(version);
}

async function cachedManifest() {
  const version = await currentVersion();
  const cache = version ? await caches.open(DATA_CACHE_PREFIX + version) : null;
  return cache?.match(MANIFEST_PATH);
}

async function refreshManifest(request) {
  let response;
  try {
    response = await fetch(request);
  } catch (error) {
    const cached = await cachedManifest();
    if (cached) {
      return { response: cached };
    }
    throw error;
  }
  if (!cacheable(response)) {
    return { response };
  }
  let next = null;
  try {
    next = (await response.clone().json()).dataset_version;
  } catch {
  }
  const version = await currentVersion();
  if (!version) {
    await prepare(response.clone()).catch(() => {});
    return { response };
  }
  if (next === version) {
    return { response };
  }
  const preparation = prepare(response.clone()).catch(() => {});
  return { response: await cachedManifest() ?? response, preparation };
}

function manifestNetworkFirst(request, event) {
  manifestRequest ??= refreshManifest(request).finally(() => {
    manifestRequest = null;
  });
  event?.waitUntil(manifestRequest.then(result => result.preparation).catch(() => {}));
  return manifestRequest.then(result => result.response.clone());
}

async function currentDataCache() {
  if (manifestRequest) {
    await manifestRequest.catch(() => {});
  }
  let version = await currentVersion();
  if (!version) {
    await manifestNetworkFirst(new Request(new URL(MANIFEST_PATH, self.location.origin))).catch(() => {});
    version = await currentVersion();
  }
  return version ? DATA_CACHE_PREFIX + version : null;
}

async function dataCacheFirst(request) {
  const cacheName = await currentDataCache();
  const cache = cacheName ? await caches.open(cacheName) : null;
  const cached = await cache?.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (cache && cacheName === DATA_CACHE_PREFIX + await currentVersion() && cacheable(response)) {
    await cache.put(request, response.clone()).catch(() => {});
  }
  return response;
}

function navigationPage(path) {
  if (path === '/grammar' || path === '/grammar.html') {
    return '/grammar.html';
  }
  if (path === '/about' || path === '/about.html') {
    return '/about.html';
  }
  if (path === '/kanji' || path.startsWith('/kanji/') || path === '/vocab' || path.startsWith('/vocab/')) {
    return '/learn.html';
  }
  return '/index.html';
}

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE);
    await Promise.all(PRECACHE.map(async path => {
      const response = await fetch(new Request(new URL(path, self.location.origin), { cache: 'reload' }));
      if (!response.ok) {
        throw new Error(`Cannot fetch ${path}`);
      }
      if (cacheable(response)) {
        await cache.put(path, response);
      }
    }));
    if (!await currentVersion()) {
      await fetch(new Request(new URL(MANIFEST_PATH, self.location.origin), { cache: 'reload' }))
          .then(prepare).catch(() => {});
    }
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const version = await currentVersion();
    const current = version ? DATA_CACHE_PREFIX + version : null;
    const names = await caches.keys();
    await Promise.all(names.filter(name => name !== SHELL_CACHE && name !== current)
        .map(name => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin) {
    return;
  }
  if (url.pathname.startsWith('/api/learning/')) {
    event.respondWith(dataCacheFirst(request));
  } else if (url.pathname.startsWith('/api/')) {
    return;
  } else if (request.mode === 'navigate') {
    const page = navigationPage(url.pathname);
    const known = page !== '/index.html' || url.pathname === '/' || url.pathname === '/index.html';
    event.respondWith(networkFirst(request, SHELL_CACHE, page, known));
  } else if (shellPaths.has(url.pathname)) {
    event.respondWith(networkFirst(request, SHELL_CACHE, url.pathname));
  } else if (url.pathname === MANIFEST_PATH) {
    event.respondWith(manifestNetworkFirst(request, event));
  } else if (url.pathname.startsWith('/learning/')) {
    event.respondWith(dataCacheFirst(request));
  }
});
