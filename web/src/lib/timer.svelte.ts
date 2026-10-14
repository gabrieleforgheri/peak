// Timer di recupero: un solo orario di fine, condiviso. Sopravvive a un ricaricamento della pagina.
// Il suono suona solo con la pagina aperta; la notifica a telefono bloccato è il Web Push (push.ts).

import { t } from './i18n.ts'
import { cancelRestPush, pushEnabled, scheduleRestPush } from './push.ts'

const KEY = 'peak.rest'

export const R = $state({ endsAt: 0, left: 0 })

let tick: ReturnType<typeof setInterval> | undefined
let onEnd: (() => void) | undefined

function update() {
	R.left = Math.max(0, Math.ceil((R.endsAt - Date.now()) / 1000))
	if (R.endsAt && R.left === 0) { finish(true) }
}

function finish(ring: boolean) {
	if (!ring && R.endsAt) void cancelRestPush()
	clearInterval(tick); tick = undefined
	R.endsAt = 0; R.left = 0
	try { localStorage.removeItem(KEY) } catch { /* modalità privata */ }
	if (ring) { beep(); onEnd?.() }
}

export function startRest(seconds: number, ended?: () => void) {
	onEnd = ended
	R.endsAt = Date.now() + seconds * 1000
	try { localStorage.setItem(KEY, String(R.endsAt)) } catch { /* come sopra */ }
	void scheduleRestPush(R.endsAt)
	clearInterval(tick)
	tick = setInterval(update, 250)
	update()
}

export function shiftRest(seconds: number) {
	if (!R.endsAt) return
	R.endsAt = Math.max(Date.now() + 1000, R.endsAt + seconds * 1000)
	try { localStorage.setItem(KEY, String(R.endsAt)) } catch { /* come sopra */ }
	void scheduleRestPush(R.endsAt)
	update()
}

export const stopRest = () => finish(false)

export function resumeRest() {
	const saved = Number(localStorage.getItem(KEY) ?? 0)
	if (saved > Date.now()) { R.endsAt = saved; clearInterval(tick); tick = setInterval(update, 250); update() }
}

function beep() {
	try {
		const ctx = new AudioContext()
		for (const [i, f] of [880, 1175].entries()) {
			const o = ctx.createOscillator(), g = ctx.createGain()
			o.frequency.value = f; o.connect(g); g.connect(ctx.destination)
			g.gain.setValueAtTime(0.0001, ctx.currentTime + i * 0.22)
			g.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + i * 0.22 + 0.02)
			g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + i * 0.22 + 0.2)
			o.start(ctx.currentTime + i * 0.22); o.stop(ctx.currentTime + i * 0.22 + 0.22)
		}
		setTimeout(() => ctx.close(), 800)
	} catch { /* senza audio */ }
	if (!pushEnabled() && document.visibilityState === 'hidden' && 'Notification' in window && Notification.permission === 'granted')
		navigator.serviceWorker?.ready.then((r) => r.showNotification(t('Recupero finito'), { body: t('Tocca alla prossima serie.'), tag: 'recupero' })).catch(() => {})
}
