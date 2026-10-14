import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import { boot } from './lib/sync.ts'
import { resumeRest } from './lib/timer.svelte.ts'

mount(App, { target: document.getElementById('app')! })
void boot()
resumeRest()

// l'app senza rete passa dal service worker (sw.js)
if ('serviceWorker' in navigator && import.meta.env.PROD) navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => {})
