import { quintOut } from 'svelte/easing'

/* Animazioni di festa: WAAPI e un solo canvas, nessuna dipendenza. Il CSS di app.css spegne i suoi movimenti con «riduci movimento»,
 * ma WAAPI e canvas no: qui si controlla a mano. Con movimento ridotto restano solo le dissolvenze, niente coriandoli, i numeri sono
 * subito quelli finali. I suoni non dipendono da questo (vedi sound.ts). */

export const EASE_OUT = 'cubic-bezier(0.23, 1, 0.32, 1)'
export const reduced = (): boolean => matchMedia('(prefers-reduced-motion: reduce)').matches
/** Durate (ms) del movimento di tutti i giorni: specchio dei --dur-1/2/3/4 di app.css (JS e CSS non si leggono: se cambi qui, cambia là). */
export const DUR = { press: 120, fade: 200, enter: 300, chart: 600 } as const

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

/** Riga di lista che entra (sale di 6 px) o esce (si riduce appena e chiude il suo spazio): con `in:` e `out:`, e `animate:flip` (move) per le altre righe.
 *  Con movimento ridotto restano solo le dissolvenze. */
export function rowIn(_: Element) {
	const r = reduced()
	return { duration: r ? 150 : 220, easing: quintOut, css: (t: number) => `opacity:${t};transform:translateY(${r ? 0 : (1 - t) * 6}px)` }
}
export function rowOut(node: Element) {
	if (reduced()) return { duration: 120, easing: quintOut, css: (t: number) => `opacity:${t}` }
	// l'altezza si chiude insieme alla dissolvenza: le righe sotto scivolano su invece di saltare a fine uscita
	const cs = getComputedStyle(node)
	const h = (node as HTMLElement).offsetHeight, pt = parseFloat(cs.paddingTop), pb = parseFloat(cs.paddingBottom), bt = parseFloat(cs.borderTopWidth)
	return {
		duration: 200, easing: quintOut,
		css: (t: number) => `opacity:${t};overflow:hidden;height:${h * t}px;padding-top:${pt * t}px;padding-bottom:${pb * t}px;border-top-width:${bt * t}px;transform:scale(${0.98 + 0.02 * t})`,
	}
}
/** Parametri di `animate:flip` per le righe che cambiano posto. */
export const move = () => ({ duration: reduced() ? 0 : 260, easing: quintOut })
/** Parametri di `transition:slide` (di svelte/transition) per una sezione che si apre e si chiude in altezza. */
export const fold = () => ({ duration: reduced() ? 0 : 240, easing: quintOut })

type Count = { value: number; from?: number; delay?: number; ms?: number; follow?: boolean; format?: (n: number) => string }

/** Azione Svelte: scrive nel nodo `value` formattato; con `from` ci arriva contando (ease-out, `ms` = 800 di norma, dopo `delay`), senza `from` (o con
 *  movimento ridotto) lo scrive subito. Il nodo deve essere vuoto: il testo lo gestisce lei.
 *  `follow`: `from` vale solo la prima volta; quando `value` cambia dopo, il numero scorre (400 ms) da quello che si vede al nuovo, come per una serie aggiunta. */
export function countUp(node: HTMLElement, p: Count) {
	let raf = 0
	let timer: ReturnType<typeof setTimeout> | undefined
	let last = ''
	let cur = 0 // il numero che si vede adesso (a metà conteggio, quello intermedio)
	let first = true
	function start(q: Count) {
		const key = `${q.value}|${q.from}|${q.delay}`
		if (key === last) return // i parametri sono un oggetto nuovo a ogni cambio del modello: se i numeri sono gli stessi non si riparte
		last = key
		cancelAnimationFrame(raf)
		clearTimeout(timer)
		const f = q.format ?? ((n: number) => String(Math.round(n)))
		const again = q.follow && !first
		const from = again ? cur : q.from
		const delay = again ? 0 : (q.delay ?? 0)
		const ms = again ? 400 : (q.ms ?? 800)
		first = false
		if (from === undefined || from === q.value || reduced()) { cur = q.value; node.textContent = f(q.value); return }
		cur = from
		node.textContent = f(from)
		timer = setTimeout(() => {
			const t0 = performance.now()
			const tick = (now: number) => {
				const k = Math.min(1, Math.max(0, (now - t0) / ms)) // il tempo del fotogramma può precedere t0 di qualche ms: mai sotto lo 0
				cur = from + (q.value - from) * (1 - (1 - k) ** 5)
				node.textContent = f(cur)
				if (k < 1) raf = requestAnimationFrame(tick)
			}
			raf = requestAnimationFrame(tick)
		}, delay)
	}
	start(p)
	return { update: start, destroy() { cancelAnimationFrame(raf); clearTimeout(timer) } }
}

// ---- movimento di tutti i giorni: pagine, contenuti, grafici, fogli. Brevi, solo opacità e transform, mai rimbalzi; con movimento ridotto niente ----

/** Da dove arriva una pagina: cambio scheda (`tab`), un dettaglio che si apre (`fwd`) o si torna indietro (`back`). Lo calcola il router. */
export type Dir = 'tab' | 'fwd' | 'back'

/** Azione sul contenitore della pagina: solo entrata, nessuna uscita (la vecchia sparisce subito, così le pagine non si sovrappongono e il tocco non aspetta).
 *  Cambio scheda: sale di 6 px; dettaglio: arriva da destra di 16 px, tornando indietro da sinistra. */
export function pageIn(node: Element, dir: Dir) {
	if (reduced()) return
	const from = dir === 'tab' ? 'translateY(6px)' : `translateX(${dir === 'fwd' ? 16 : -16}px)`
	node.animate([{ opacity: 0, transform: from }, { opacity: 1, transform: 'none' }], { duration: dir === 'tab' ? DUR.fade : 260, easing: EASE_OUT, fill: 'backwards' })
}

const staged = new WeakSet<Element>() // chi ha già avuto la sua entrata: non si ripete
/** I figli che si vedono: un contenitore senza riquadro (`display: contents`, come .blk sul telefono) lascia il posto ai suoi figli. */
function visibleKids(root: Element, out: Element[] = []): Element[] {
	for (const el of root.children) {
		if (getComputedStyle(el).display === 'contents') visibleKids(el, out)
		else out.push(el)
	}
	return out
}

/** Azione sul `<main>` di una pagina: i suoi blocchi salgono di 8 px uno dopo l'altro (30 ms l'uno, dall'ottavo in poi tutti insieme). Solo i blocchi già sullo
 *  schermo e solo la prima volta. Se il contenuto arriva dopo (rete), si passa un valore che cambia quando c'è: entrano i blocchi nuovi, quelli di prima no. */
export function stagger(node: HTMLElement, _when?: unknown) {
	function enter() {
		if (reduced()) return
		let i = 0
		for (const el of visibleKids(node)) {
			if (staged.has(el)) continue
			staged.add(el)
			if (el.getBoundingClientRect().top > innerHeight) continue // sotto lo schermo: nessuno lo vede entrare
			rise(el, Math.min(i++, 8) * 30, 8)
		}
	}
	enter()
	return { update: enter }
}

/** Azione: marca `data-in` = 0 finché il nodo non si è mai visto e 1 dalla prima volta che entra nello schermo. Il CSS parte da lì per disegnare
 *  barre e grafici solo quando li guardi (e non ogni volta che i dati cambiano). Senza IntersectionObserver o con movimento ridotto, subito 1. */
export function inview(node: HTMLElement | SVGElement) {
	if (reduced() || typeof IntersectionObserver === 'undefined') { node.dataset.in = '1'; return }
	node.dataset.in = '0'
	const io = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) { node.dataset.in = '1'; io.disconnect() } }, { rootMargin: '0px 0px -8% 0px' })
	io.observe(node)
	return { destroy: () => io.disconnect() }
}

const EASE_DRAWER = 'cubic-bezier(0.32, 0.72, 0, 1)' // come --ease-drawer
/** Azione sul velo (`.backdrop`) di un foglio. Apertura (260 ms): il velo si dissolve e il foglio sale dal bordo (telefono) o si dissolve crescendo da .98 (schermo
 *  largo, dove è una finestra al centro). Chiusura (200 ms): il foglio vero sparisce subito, perché Svelte ne toglie il DOM e una transizione `out:` non parte se a
 *  chiudere è un genitore (un cambio di pagina) né vale la pena tenere viva la pagina vecchia; al suo posto resta un doppione inerte che fa l'uscita e si toglie da
 *  solo. Il doppione non prende i tocchi (la pagina sotto è subito cliccabile) e perde il nome `.backdrop` e i `data-tour`, così il tutorial non lo vede. */
export function sheetMotion(backdrop: HTMLElement) {
	if (reduced()) return
	const sheet = backdrop.querySelector<HTMLElement>('.sheet')
	if (!sheet) return
	const wide = matchMedia('(min-width: 900px)').matches
	const dialog = (open: boolean): Keyframe[] => { const a = { opacity: 0, transform: 'scale(0.98)' }, b = { opacity: 1, transform: 'none' }; return open ? [a, b] : [b, a] }
	const drawer = (open: boolean): Keyframe[] => (open ? [{ transform: 'translateY(100%)' }, { transform: 'none' }] : [{ transform: 'none' }, { transform: 'translateY(100%)' }])
	const play = (b: HTMLElement, s: HTMLElement, open: boolean) => {
		const o: KeyframeAnimationOptions = { duration: open ? 260 : DUR.fade, easing: wide ? EASE_OUT : EASE_DRAWER, fill: open ? 'backwards' : 'forwards' }
		b.animate(open ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 1 }, { opacity: 0 }], { ...o, easing: 'ease-out' })
		return s.animate(wide ? dialog(open) : drawer(open), o)
	}
	play(backdrop, sheet, true)
	let top = 0
	const onscroll = () => (top = sheet.scrollTop)
	sheet.addEventListener('scroll', onscroll, { passive: true })
	return {
		destroy() {
			sheet.removeEventListener('scroll', onscroll)
			const ghost = backdrop.cloneNode(true) as HTMLElement
			ghost.classList.replace('backdrop', 'backdrop-ghost')
			ghost.inert = true
			ghost.setAttribute('aria-hidden', 'true')
			ghost.querySelectorAll('[data-tour]').forEach((e) => e.removeAttribute('data-tour'))
			;(document.querySelector('.app') ?? document.body).append(ghost)
			const gs = ghost.querySelector<HTMLElement>('.sheet')
			if (!gs) return ghost.remove()
			gs.scrollTop = top // il doppione parte da dove era scorso il contenuto
			play(ghost, gs, false).onfinish = () => ghost.remove()
		},
	}
}

type Tr = { direction: 'in' | 'out' | 'both' }
/** L'avviso in basso: sale di 12 px mentre appare, scende mentre sparisce (ha già un `translate` per centrarsi: il transform si somma). */
export function toastFx(_: Element, __: unknown, { direction }: Tr) {
	return { duration: reduced() ? 0 : direction === 'out' ? DUR.fade : 240, easing: quintOut, css: (t: number) => `opacity:${t};transform:translateY(${(1 - t) * 12}px)` }
}

/** Azione su un `<details>` con `<summary>` e il contenuto subito dopo: apre e chiude facendo scorrere l'altezza (240 ms), anche se lo tocchi a metà. */
export function disclosure(details: HTMLDetailsElement) {
	const summary = details.querySelector(':scope > summary')
	let anim: Animation | undefined
	let want = details.open
	function click(e: Event) {
		const body = summary?.nextElementSibling as HTMLElement | null
		if (!body || reduced()) return // il browser fa da sé
		e.preventDefault()
		want = !want
		const from = anim ? body.getBoundingClientRect().height : want ? 0 : body.scrollHeight
		anim?.cancel()
		details.open = true
		details.classList.toggle('closing', !want)
		body.style.overflow = 'hidden'
		const to = want ? body.scrollHeight : 0
		const a = (anim = body.animate({ height: [`${from}px`, `${to}px`], opacity: [from ? 1 : 0, want ? 1 : 0] }, { duration: 240, easing: EASE_OUT }))
		a.onfinish = () => { anim = undefined; body.style.overflow = ''; details.open = want; details.classList.remove('closing') }
	}
	summary?.addEventListener('click', click)
	return { destroy() { summary?.removeEventListener('click', click); anim?.cancel() } }
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
