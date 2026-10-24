// Installazione di Peak (PWA). beforeinstallprompt può partire prima che la Home sia montata: si cattura qui, all'avvio del modulo.
import { installTarget } from '@common/install.ts'
import { standalone } from './push.ts'

type Prompt = Event & { prompt(): Promise<void> }

const KEY = 'peak.install-dismissed'
const MONTH = 30 * 864e5

export const I = $state<{ ev: Prompt | null; installed: boolean; dismissed: boolean }>({ ev: null, installed: false, dismissed: false })

const read = (): number => { try { return Number(localStorage.getItem(KEY)) || 0 } catch { return 0 } }
I.dismissed = Date.now() - read() < MONTH

addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); I.ev = e as Prompt })
addEventListener('appinstalled', () => { I.installed = true; I.ev = null })

/** Cosa mostrare nel banner, o null se non c'è niente da proporre. */
export const target = () => (I.installed || I.dismissed || standalone() ? null : installTarget(navigator.userAgent, navigator.maxTouchPoints, !!I.ev))

export function dismissInstall() {
	I.dismissed = true
	try { localStorage.setItem(KEY, String(Date.now())) } catch { /* senza archivio si ripresenta al prossimo avvio */ }
}

/** Da un tocco. Il prompt si usa una volta sola: poi l'evento è consumato. */
export async function installNow() {
	const ev = I.ev
	if (!ev) return
	I.ev = null
	await ev.prompt()
}
