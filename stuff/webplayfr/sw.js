// webplayfr service worker: keeps the app shell available offline. API, audio and images always go to the network.
const V = "wpf-v1";
const SHELL = ["./", "index.html", "css/style.css", "manifest.webmanifest", "icons/icon.svg",
  "js/main.js", "js/util.js", "js/store.js", "js/backend.js", "js/player.js", "js/lyrics.js", "js/fx.js", "js/keys.js",
  "js/touch.js", "js/views.js", "js/np.js", "js/settings.js"];
self.addEventListener("install", e => { e.waitUntil(caches.open(V).then(c => c.addAll(SHELL).catch(() => {})).then(() => self.skipWaiting())); });
self.addEventListener("activate", e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== V).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener("fetch", e => {
  const u = new URL(e.request.url);
  if (e.request.method !== "GET" || u.origin !== location.origin || /\/(api|stream|media)\//.test(u.pathname)) return;
  e.respondWith(fetch(e.request).then(r => { const c = r.clone(); caches.open(V).then(x => x.put(e.request, c)); return r; }).catch(() => caches.match(e.request).then(r => r || caches.match("index.html"))));
});
