// Avviso a fine recupero a telefono bloccato (Web Push). Il server tiene l'orario di fine e manda l'avviso;
// qui si gestisce l'iscrizione di questo browser. Su iPhone funziona solo con Peak aggiunta alla schermata Home (iOS 16.4+).

import { call } from './sync.ts'

const FLAG = 'peak.push'

export type PushState = 'unsupported' | 'needs-install' | 'denied' | 'off' | 'on'

export const pushSupported = (): boolean => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
const standalone = (): boolean => matchMedia('(display-mode: standalone)').matches || (navigator as unknown as { standalone?: boolean }).standalone === true
const isIos = (): boolean => /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
const enabled = (): boolean => { try { return localStorage.getItem(FLAG) === '1' } catch { return false } }

export async function pushState(): Promise<PushState> {
	if (isIos() && !standalone()) return 'needs-install'
	if (!pushSupported()) return 'unsupported'
	if (Notification.permission === 'denied') return 'denied'
	if (!enabled() || Notification.permission !== 'granted') return 'off'
	const sub = await (await navigator.serviceWorker.ready).pushManager.getSubscription()
	return sub ? 'on' : 'off'
}

const key = (b64: string) => Uint8Array.from(atob(b64.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0))

/** Da chiamare da un tocco: il permesso si può chiedere solo così. */
export async function enablePush(vapid: string): Promise<PushState> {
	if (await Notification.requestPermission() !== 'granted') return pushState()
	const reg = await navigator.serviceWorker.ready
	const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key(vapid) }))
	const res = await call('/api/push', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ endpoint: sub.endpoint }) })
	if (!res.ok) { await sub.unsubscribe(); throw new Error(String(res.status)) }
	localStorage.setItem(FLAG, '1')
	return 'on'
}

export async function disablePush(): Promise<PushState> {
	localStorage.removeItem(FLAG)
	const sub = await (await navigator.serviceWorker.ready).pushManager.getSubscription()
	if (sub) {
		await call('/api/push', { method: 'DELETE', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ endpoint: sub.endpoint }) }).catch(() => {})
		await sub.unsubscribe()
	}
	return pushState()
}

export const pushEnabled = enabled

async function endpoint(): Promise<string | null> {
	if (!enabled() || !pushSupported() || Notification.permission !== 'granted') return null
	return (await (await navigator.serviceWorker.ready).pushManager.getSubscription())?.endpoint ?? null
}

/** Dice al server quando finisce il recupero (anche dopo +15/−15). Senza rete resta solo il timer nella pagina. */
export async function scheduleRestPush(endsAt: number): Promise<void> {
	try {
		const ep = await endpoint()
		if (ep) await call('/api/recupero', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ endpoint: ep, fine: endsAt }) })
	} catch { /* offline: vale il timer nella pagina */ }
}

export async function cancelRestPush(): Promise<void> {
	try {
		const ep = await endpoint()
		if (ep) await call('/api/recupero', { method: 'DELETE', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ endpoint: ep }) })
	} catch { /* come sopra */ }
}

/** Tornando nell'app l'avviso di fine recupero non serve più. */
export function clearRestNotification() {
	navigator.serviceWorker?.ready.then((r) => r.getNotifications({ tag: 'recupero' }).then((ns) => ns.forEach((n) => n.close()))).catch(() => {})
}
