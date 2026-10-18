import { quintOut } from 'svelte/easing'

/* Animazioni di festa: WAAPI e un solo canvas, nessuna dipendenza. Il CSS di app.css spegne i suoi movimenti con «riduci movimento»,
 * ma WAAPI e canvas no: qui si controlla a mano. Con movimento ridotto restano solo le dissolvenze, niente coriandoli, i numeri sono
 * subito quelli finali. I suoni non dipendono da questo (vedi sound.ts). */

export const EASE_OUT = 'cubic-bezier(0.23, 1, 0.32, 1)'
export const reduced = (): boolean => matchMedia('(prefers-reduced-motion: reduce)').matches

/** Parte con `delay` e, finché non parte, l'elemento resta nel primo fotogramma (fill backwards): niente lampo dello stato finale. */
function run(el: Element | null | undefined, frames: Keyframe[], ms: number, delay: number): Animation | undefined {
	if (!el) return
	return el.animate(reduced() ? [{ opacity: 0 }, { opacity: 1 }] : frames, { duration: ms, delay, easing: EASE_OUT, fill: 'backwards' })
}

/** Comparsa «pop»: scala .7 → 1.08 → 1 con dissolvenza, 450 ms; `rotate` (gradi) la fa assestare con una piccola rotazione. */
export const pop = (el: Element | null | undefined, delay = 0, rotate = 0) => run(el, [
	{ transform: `scale(.7) rotate(${-rotate}deg)`, opacity: 0 },
	{ transform: `scale(1.08) rotate(${rotate / 3}deg)`, opacity: 1, offset: 0.6 },
	{ transform: 'scale(1) rotate(0deg)', opacity: 1 },
], 450, delay)

/** Entrata leggera: sale di `dy` px e appare, 300 ms. */
export const rise = (el: Element | null | undefined, delay = 0, dy = 12) => run(el, [{ transform: `translateY(${dy}px)`, opacity: 0 }, { transform: 'none', opacity: 1 }], 300, delay)

/** La barra che si allunga (transform, non width): per il tratto guadagnato delle medaglie; l'elemento ha transform-origin: left. */
export const grow = (el: Element | null | undefined, delay = 0) => run(el, [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }], 600, delay)

/** La fiamma: ingrandisce e dondola assestandosi. Solo movimento, quindi con movimento ridotto non fa nulla. */
export function wiggle(el: Element | null | undefined, delay = 0) {
	if (!el || reduced()) return
	el.animate([
		{ transform: 'scale(1) rotate(0deg)' },
		{ transform: 'scale(1.3) rotate(-10deg)', offset: 0.25 },
		{ transform: 'scale(1.15) rotate(10deg)', offset: 0.5 },
		{ transform: 'scale(1.1) rotate(-5deg)', offset: 0.75 },
		{ transform: 'scale(1) rotate(0deg)' },
	], { duration: 650, delay, easing: EASE_OUT })
}

/** Un battito unico, per l'anello di oggi. */
export function pulse(el: Element | null | undefined, delay = 0) {
	if (!el || reduced()) return
	el.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.18)', offset: 0.4 }, { transform: 'scale(1)' }], { duration: 380, delay, easing: EASE_OUT })
}

/** Una serie di azioni a tempo (ms da ora) che si annullano insieme: uscendo dalla pagina non resta nessun suono in coda. */
export function sequence() {
	const ids: ReturnType<typeof setTimeout>[] = []
	return {
		at(ms: number, fn: () => void) { ids.push(setTimeout(fn, ms)) },
		stop() { ids.forEach(clearTimeout); ids.length = 0 },
	}
}

/** Riga di lista che entra (sale di 6 px) o esce (si riduce appena): per le serie, con `in:` e `out:`. */
export function rowIn(_: Element) {
	const r = reduced()
	return { duration: r ? 150 : 220, easing: quintOut, css: (t: number) => `opacity:${t};transform:translateY(${r ? 0 : (1 - t) * 6}px)` }
}
export function rowOut(_: Element) {
	const r = reduced()
	return { duration: r ? 120 : 180, easing: quintOut, css: (t: number) => `opacity:${t};transform:scale(${r ? 1 : 0.98 + 0.02 * t})` }
}

type Count = { value: number; from?: number; delay?: number; format?: (n: number) => string }

/** Azione Svelte: scrive nel nodo `value` formattato; con `from` ci arriva contando (ease-out, 800 ms, dopo `delay`), senza `from` (o con movimento
 *  ridotto) lo scrive subito. Il nodo deve essere vuoto: il testo lo gestisce lei. */
export function countUp(node: HTMLElement, p: Count) {
	let raf = 0
	let timer: ReturnType<typeof setTimeout> | undefined
	let last = ''
	function start(q: Count) {
		const key = `${q.value}|${q.from}|${q.delay}`
		if (key === last) return // i parametri sono un oggetto nuovo a ogni cambio del modello: se i numeri sono gli stessi non si riparte
		last = key
		cancelAnimationFrame(raf)
		clearTimeout(timer)
		const f = q.format ?? ((n: number) => String(Math.round(n)))
		const from = q.from
		if (from === undefined || from === q.value || reduced()) { node.textContent = f(q.value); return }
		node.textContent = f(from)
		timer = setTimeout(() => {
			const t0 = performance.now()
			const tick = (now: number) => {
				const k = Math.min(1, (now - t0) / 800)
				node.textContent = f(from + (q.value - from) * (1 - (1 - k) ** 5))
				if (k < 1) raf = requestAnimationFrame(tick)
			}
			raf = requestAnimationFrame(tick)
		}, q.delay ?? 0)
	}
	start(p)
	return { update: start, destroy() { cancelAnimationFrame(raf); clearTimeout(timer) } }
}

/** Una raffica di coriandoli (circa 90): accento e i sei colori dei muscoli, su un canvas a tutto schermo che sparisce da solo.
 *  `origin`: un punto del viewport o un elemento (il suo centro); senza, la parte alta dello schermo. */
export function confetti(origin?: { x: number; y: number } | Element | null): void {
	if (reduced() || document.visibilityState === 'hidden') return
	let x = innerWidth / 2
	let y = innerHeight * 0.35
	if (origin instanceof Element) { const r = origin.getBoundingClientRect(); x = r.left + r.width / 2; y = r.top + r.height / 2 }
	else if (origin) ({ x, y } = origin)
	const css = getComputedStyle(document.documentElement)
	const colors = ['--accent', '--accent', '--petto', '--schiena', '--gambe', '--spalle', '--braccia', '--core'].map((v) => css.getPropertyValue(v).trim() || '#b8f04d')
	const cv = document.createElement('canvas')
	const ctx = cv.getContext('2d')
	if (!ctx) return
	const dpr = Math.min(devicePixelRatio || 1, 2)
	cv.width = Math.round(innerWidth * dpr)
	cv.height = Math.round(innerHeight * dpr)
	// dentro .app, così i fogli (40) e l'avviso (60) restano sopra; z-index 38 sopra tutto il resto
	cv.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:38'
	cv.setAttribute('aria-hidden', 'true')
	ctx.scale(dpr, dpr)
	;(document.querySelector('.app') ?? document.body).append(cv)

	const ps = Array.from({ length: 90 }, () => {
		const a = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 1.1 // verso l'alto, aperti a ventaglio
		const v = 600 + Math.random() * 900
		return {
			x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v,
			rot: Math.random() * 6.28, spin: (Math.random() - 0.5) * 18, // rotazione
			flip: Math.random() * 6.28, flipV: 6 + Math.random() * 8, // il rettangolo si gira di taglio
			w: 7 + Math.random() * 6, h: 4 + Math.random() * 4, circle: Math.random() < 0.3,
			color: colors[Math.floor(Math.random() * colors.length)]!,
		}
	})
	const DUR = 1400
	let t0 = 0
	let prev = 0
	const frame = (now: number) => {
		if (!t0) t0 = prev = now
		const life = (now - t0) / DUR
		const dt = Math.min(0.05, (now - prev) / 1000)
		prev = now
		ctx.clearRect(0, 0, innerWidth, innerHeight)
		if (life >= 1) { cv.remove(); return } // finito: via il canvas, e il ciclo non riparte
		ctx.globalAlpha = life < 0.7 ? 1 : (1 - life) / 0.3 // dissolvenza nell'ultimo 30%
		const drag = Math.exp(-1.3 * dt)
		for (const p of ps) {
			p.vx *= drag
			p.vy = p.vy * drag + 1300 * dt // attrito e gravità
			p.x += p.vx * dt
			p.y += p.vy * dt
			p.rot += p.spin * dt
			p.flip += p.flipV * dt
			ctx.save()
			ctx.translate(p.x, p.y)
			ctx.rotate(p.rot)
			ctx.fillStyle = p.color
			if (p.circle) { ctx.beginPath(); ctx.arc(0, 0, p.h * 0.7, 0, Math.PI * 2); ctx.fill() }
			else { ctx.scale(1, Math.cos(p.flip)); ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h) }
			ctx.restore()
		}
		requestAnimationFrame(frame)
	}
	requestAnimationFrame(frame)
}

// ---- cosa è già stato festeggiato (per non ripeterlo alla Home) ----
const rd = (k: string) => { try { return localStorage.getItem(`peak.celebrated.${k}`) } catch { return null } }
const wr = (k: string, v: string | number) => { try { localStorage.setItem(`peak.celebrated.${k}`, String(v)) } catch { /* senza archivio si festeggia di più, non si rompe nulla */ } }

export const celebrated = {
	/** Le settimane di fila già festeggiate; null se non c'è ancora un valore (prima volta: si salva in silenzio). */
	streak(): number | null { const v = rd('streak'); return v && /^\d+$/.test(v) ? +v : null },
	setStreak: (n: number) => wr('streak', n),
	/** La settimana (inizio in ms) e gli allenamenti già festeggiati: "<weekStartMs>:<count>". */
	week(): { ws: number; n: number } | null { const m = /^(\d+):(\d+)$/.exec(rd('week') ?? ''); return m ? { ws: +m[1]!, n: +m[2]! } : null },
	setWeek: (ws: number, n: number) => wr('week', `${ws}:${n}`),
}

/** True la prima volta che si chiede `key` in questa scheda del browser: il riepilogo non rifà la festa se lo riapri. */
export function firstTime(key: string): boolean {
	try {
		const k = `peak.played.${key}`
		if (sessionStorage.getItem(k)) return false
		sessionStorage.setItem(k, '1')
	} catch { /* senza archivio si rifà, pazienza */ }
	return true
}
