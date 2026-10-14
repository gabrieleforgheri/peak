import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import { boot } from './lib/sync.ts'
import { clearRestNotification } from './lib/push.ts'
import { resumeRest } from './lib/timer.svelte.ts'

mount(App, { target: document.getElementById('app')! })
void boot()
resumeRest()
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') clearRestNotification() })

// l'app senza rete passa dal service worker (sw.js)
if ('serviceWorker' in navigator && import.meta.env.PROD) navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => {})
