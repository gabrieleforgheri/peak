import set from '../sounds/set.m4a?url'
import record from '../sounds/record.m4a?url'
import finish from '../sounds/finish.m4a?url'
import streak from '../sounds/streak.m4a?url'
import goal from '../sounds/goal.m4a?url'
import medal from '../sounds/medal.m4a?url'
import remove from '../sounds/remove.m4a?url'
import day1 from '../sounds/day1.m4a?url'
import day2 from '../sounds/day2.m4a?url'
import day3 from '../sounds/day3.m4a?url'
import day4 from '../sounds/day4.m4a?url'
import day5 from '../sounds/day5.m4a?url'
import day6 from '../sounds/day6.m4a?url'
import day7 from '../sounds/day7.m4a?url'

/* Effetti sonori (sintetizzati da scripts/make-sounds.mjs). Un solo AudioContext, creato al primo tocco o tasto (iOS Safari non suona prima);
 * i file si decodificano una volta e restano in memoria. Mai un errore: se il suono non si può fare, tace. L'interruttore è di questo
 * dispositivo (Impostazioni → Generale → Suoni) e di base è acceso. */

export type Sound = 'set' | 'record' | 'finish' | 'streak' | 'goal' | 'medal' | 'remove'

const URLS: Record<string, string> = { set, record, finish, streak, goal, medal, remove, day1, day2, day3, day4, day5, day6, day7 }
const KEY = 'peak.sounds'

let ctx: AudioContext | null = null
let master: GainNode | null = null
const buffers = new Map<string, AudioBuffer>()
const loading = new Set<string>()

export function soundsOn(): boolean {
	try { return localStorage.getItem(KEY) !== '0' } catch { return true }
}

export function setSounds(on: boolean): void {
	try { localStorage.setItem(KEY, on ? '1' : '0') } catch { /* senza archivio vale solo per questa pagina */ }
	if (on) unlock()
}

/** Crea il contesto la prima volta, lo risveglia se dorme (iOS lo sospende dopo una chiamata o lo sfondo) e scarica i suoni mancanti. */
function unlock(): void {
	if (!soundsOn()) return
	try {
		if (!ctx) {
			ctx = new AudioContext()
			master = ctx.createGain()
			master.gain.value = 0.8
			master.connect(ctx.destination)
		}
		if (ctx.state !== 'running') void ctx.resume().catch(() => {})
		for (const [name, url] of Object.entries(URLS)) {
			if (buffers.has(name) || loading.has(name)) continue
			loading.add(name)
			fetch(url).then((r) => r.arrayBuffer()).then((b) => ctx!.decodeAudioData(b)).then((a) => { buffers.set(name, a) })
				.catch(() => { /* quel suono resta muto; si riprova al prossimo tocco */ }).finally(() => loading.delete(name))
		}
	} catch { /* niente audio su questo browser */ }
}
// iOS Safari non sblocca l'audio con pointerdown (solo al rilascio del dito, al click o a un tasto): si ascolta tutto, è a costo zero
for (const ev of ['pointerdown', 'pointerup', 'click', 'keydown']) addEventListener(ev, unlock, { passive: true })

function start(name: string): void {
	try {
		if (!soundsOn() || document.visibilityState === 'hidden' || !ctx || !master || ctx.state !== 'running') return
		const buffer = buffers.get(name)
		if (!buffer) return
		const src = ctx.createBufferSource()
		src.buffer = buffer
		src.connect(master)
		src.start()
	} catch { /* mai un errore per un suono */ }
}

/** Suona un effetto; più chiamate di fila si sovrappongono. Muto se spento, non ancora sbloccato o pagina in secondo piano. */
export function play(name: Sound): void {
	start(name)
}

/** Il giorno `index` (0-6) che si riempie sulla settimana: sette note di una pentatonica, in fila fanno una melodia che sale. */
export function playDay(index: number): void {
	start(`day${Math.min(6, Math.max(0, Math.round(index) || 0)) + 1}`)
}
