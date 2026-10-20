import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import { boot } from './lib/sync.ts'
import { clearRestNotification } from './lib/push.ts'
import { resumeRest } from './lib/timer.svelte.ts'

// ogni errore va anche al server (finisce nel suo log), un massimo di 5 per pagina
let reported = 0
const report = (e: unknown) => {
	if (reported++ >= 5) return
	const err = e instanceof Error ? e : new Error(String(e))
	const body = JSON.stringify({ msg: err.message, stack: err.stack ?? '', url: location.hash, ua: navigator.userAgent })
	void fetch('/api/errore', { method: 'POST', headers: { 'content-type': 'application/json' }, body, keepalive: true }).catch(() => {})
}
addEventListener('error', (e) => report(e.error ?? e.message))
addEventListener('unhandledrejection', (e) => report(e.reason))

mount(App,{ target: document.getElementById('app')! })
void boot()
resumeRest()
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') clearRestNotification() })

// l'app senza rete passa dal service worker (sw.js)
if ('serviceWorker' in navigator && import.meta.env.PROD) navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => {})
