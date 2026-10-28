/* Il service worker di Peak (registrato da src/main.ts, scope /).
 *
 * - Il guscio dell'app (pagina, script, stile, icone) si tiene sul dispositivo: Peak si apre anche senza rete.
 *   La lista dei file la scrive Vite alla build (vite.config.ts, serviceWorker()).
 * - La pagina "/" prima va in rete (il server controlla la sessione); senza rete si apre il guscio. Una risposta
 *   che è un reindirizzamento (sessione scaduta → login) non si mette mai in cache.
 * - Le foto dei macchinari (/api/foto/<sha256>) non cambiano mai: prima la cache.
 * - L'API non si tocca: i dati stanno in IndexedDB e li sincronizza l'app. */

const SHELL = ["/","/assets/index-CO61ob_3.js","/assets/catalog-LQoF-E0c.js","/assets/day1-CmerdDsK.m4a","/assets/day2-ClDcIYqc.m4a","/assets/day3-ClyWpz9_.m4a","/assets/day4-D2SDwU0m.m4a","/assets/day5-BJx2N6jx.m4a","/assets/day6-DfznrENH.m4a","/assets/day7-D7IMfGAa.m4a","/assets/finish-Cbs1SR7_.m4a","/assets/goal-Dh2wX4ZL.m4a","/assets/index-B5A0aTXE.css","/assets/medal-DLhEwGvt.m4a","/assets/record-ripQ09NX.m4a","/assets/remove-1isPNzvf.m4a","/assets/set-DQIF1Gp8.m4a","/assets/streak-BsVjrHqX.m4a","/manifest.webmanifest","/icon-192.png"]
const VERSION = "0253148bb7"
const SHELL_CACHE = `peak-guscio-${VERSION}`
const PHOTOS = 'peak-foto'

self.addEventListener('install', (e) => {
	e.waitUntil(
		caches.open(SHELL_CACHE).then(async (c) => {
			await c.addAll(SHELL.filter((f) => f !== '/'))
			// la pagina "/" risponde solo con una sessione: se non c'è (reindirizza al login) non si mette in cache,
			// e l'installazione non fallisce per questo
			try {
				const res = await fetch('/', { credentials: 'same-origin', redirect: 'manual' })
				if (res.ok) await c.put('/', res)
			} catch { /* senza rete: al prossimo avvio */ }
		}).then(() => self.skipWaiting())
	)
})

self.addEventListener('activate', (e) => {
	e.waitUntil(
		caches.keys()
			.then((keys) => Promise.all(keys.filter((k) => k.startsWith('peak-guscio-') && k !== SHELL_CACHE).map((k) => caches.delete(k))))
			.then(() => self.clients.claim())
	)
})

self.addEventListener('fetch', (e) => {
	const req = e.request
	if (req.method !== 'GET') return
	const url = new URL(req.url)
	if (url.origin !== location.origin) return

	if (req.mode === 'navigate' && (url.pathname === '/' || url.pathname === '/index.html')) {
		e.respondWith(
			fetch(req).then(
				async (res) => {
					if (res.ok && !res.redirected) await (await caches.open(SHELL_CACHE)).put('/', res.clone())
					return res
				},
				async () => (await caches.match('/')) ?? Response.error()
			)
		)
		return
	}

	if (url.pathname.startsWith('/api/foto/')) {
		e.respondWith(caches.open(PHOTOS).then(async (c) => {
			const hit = await c.match(req)
			if (hit) return hit
			const res = await fetch(req)
			if (res.ok) c.put(req, res.clone())
			return res
		}))
		return
	}

	if (url.pathname.startsWith('/assets/') || SHELL.includes(url.pathname)) {
		e.respondWith(caches.match(req).then((r) => r || fetch(req)))
	}
})

/* Web Push: il server manda un avviso senza testo quando finisce il recupero; il testo è qui, tradotto. */
self.addEventListener('push', (e) => {
	const it = (self.navigator.language || 'it').toLowerCase().startsWith('it')
	e.waitUntil(self.registration.showNotification(it ? 'Recupero finito' : 'Rest over', {
		body: it ? 'Tocca alla prossima serie.' : 'Time for your next set.', tag: 'recupero', icon: '/icon-192.png',
	}))
})

self.addEventListener('notificationclick', (e) => {
	e.notification.close()
	e.waitUntil(self.clients.matchAll({ type: 'window' }).then((cs) => (cs.length ? cs[0].focus() : self.clients.openWindow('/'))))
})
