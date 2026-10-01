const CACHE='aidc-pwa-v2';
const CORE=['./','./index.html','./styles.css','./app.js?v=2','./manifest.webmanifest','./icons/icon.svg'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(c=>c.addAll(CORE)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET') return;
  event.respondWith(fetch(event.request).then(res=>{const clone=res.clone();caches.open(CACHE).then(c=>c.put(event.request,clone));return res;}).catch(()=>caches.match(event.request).then(r=>r||caches.match('./index.html'))));
});
