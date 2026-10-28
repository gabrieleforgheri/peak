<script lang="ts">
	import { untrack } from 'svelte'
	import type { Muscle, View } from '@common/muscle-info.ts'
	import { muscleViews, zoomFor, type Box } from '@common/muscles.ts'
	import { t } from '../lib/i18n.ts'
	import Icon from './Icon.svelte'
	import MuscleFigure from './MuscleFigure.svelte'
	import MuscleChips from './MuscleChips.svelte'

	/** La figura a tutto schermo: pizzico e trascinamento cambiano il viewBox (resta vettoriale), doppio tocco alterna corpo intero e zona che lavora. */
	let { muscles, side: first, onclose }: { muscles: readonly Muscle[]; side: View; onclose: () => void } = $props()

	const W = 724, H = 1448, MAXZ = 6, TAP_MS = 300
	let side = $state<View>(untrack(() => first))
	let stage = $state<HTMLDivElement>()
	let sw = $state(0), sh = $state(0)
	// la vista: centro nel disegno e pixel per unità del disegno; il viewBox ha sempre la forma dello schermo, quindi la figura lo riempie senza bande
	let cx = $state(W / 2), cy = $state(H / 2), k = $state(1)

	const kmin = $derived(sw && sh ? Math.min(sw / W, sh / H) : 1)
	const mine = $derived(muscles.filter((m) => muscleViews(m).includes(side)))
	const box = $derived<Box>([cx - sw / (2 * k), cy - sh / (2 * k), cx + sw / (2 * k), cy + sh / (2 * k)])

	/** Zoom entro i limiti e centro che tiene la figura sullo schermo: se sta tutta, resta centrata. */
	function clamp(nk: number, ncx: number, ncy: number) {
		nk = Math.min(Math.max(nk, kmin), kmin * MAXZ)
		const lim = (c: number, view: number, full: number) => (view >= full ? full / 2 : Math.min(Math.max(c, view / 2), full - view / 2))
		return { k: nk, cx: lim(ncx, sw / nk, W), cy: lim(ncy, sh / nk, H) }
	}
	function put(v: { k: number; cx: number; cy: number }) { ({ k, cx, cy } = v) }
	/** Il punto (wx, wy) del disegno sta sotto lo schermo in (sx, sy), con zoom nk. */
	function pin(sx: number, sy: number, wx: number, wy: number, nk: number) {
		nk = Math.min(Math.max(nk, kmin), kmin * MAXZ)
		put(clamp(nk, wx - (sx - sw / 2) / nk, wy - (sy - sh / 2) / nk))
	}
	function target(whole: boolean) {
		const b: Box = whole ? [0, 0, W, H] : zoomFor(muscles, side)
		return clamp(Math.min(sw / (b[2] - b[0]), sh / (b[3] - b[1])), (b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
	}

	let raf = 0
	const stopAnim = () => { cancelAnimationFrame(raf); raf = 0 }
	function goTo(to: { k: number; cx: number; cy: number }, animate: boolean) {
		stopAnim()
		if (!animate || matchMedia('(prefers-reduced-motion: reduce)').matches) return put(to)
		const from = { k, cx, cy }, t0 = performance.now()
		const step = (now: number) => {
			const p = Math.min((now - t0) / 240, 1), e = 1 - (1 - p) ** 3
			put({ k: from.k * (to.k / from.k) ** e, cx: from.cx + (to.cx - from.cx) * e, cy: from.cy + (to.cy - from.cy) * e })
			raf = p < 1 ? requestAnimationFrame(step) : 0
		}
		raf = requestAnimationFrame(step)
	}

	// all'apertura, cambiando lato o dimensione dello schermo: inquadra i muscoli di quel lato (se non ce ne sono, il corpo intero)
	$effect(() => {
		if (!sw || !sh) return
		void side
		untrack(() => goTo(target(false), false))
	})

	function toggle() {
		const f = target(false)
		const atFrame = Math.abs(k / f.k - 1) < 0.03 && Math.abs(cx - f.cx) < 8 && Math.abs(cy - f.cy) < 8
		goTo(atFrame ? target(true) : f, true)
	}

	const ptr = new Map<number, { x: number; y: number }>()
	let travel = 0, downAt = 0, lastTap = 0
	const rel = (e: { clientX: number; clientY: number }) => {
		const r = stage!.getBoundingClientRect()
		return { x: e.clientX - r.left, y: e.clientY - r.top }
	}
	const geom = () => {
		const [a, b] = [...ptr.values()]
		return b ? { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, d: Math.hypot(a.x - b.x, a.y - b.y) } : { ...a!, d: 0 }
	}

	function down(e: PointerEvent) {
		stage!.setPointerCapture(e.pointerId)
		stopAnim()
		ptr.set(e.pointerId, rel(e))
		if (ptr.size === 1) { travel = 0; downAt = e.timeStamp } else travel = 99
	}
	function move(e: PointerEvent) {
		if (!ptr.has(e.pointerId)) return
		const a = geom()
		ptr.set(e.pointerId, rel(e))
		const b = geom()
		travel += Math.hypot(b.x - a.x, b.y - a.y)
		// il punto del disegno che era sotto le dita prima deve stare sotto le dita adesso: vale per il trascinamento e per il pizzico
		const ratio = a.d && b.d ? b.d / a.d : 1
		pin(b.x, b.y, cx + (a.x - sw / 2) / k, cy + (a.y - sh / 2) / k, k * ratio)
	}
	function up(e: PointerEvent) {
		if (!ptr.delete(e.pointerId) || ptr.size) return
		if (e.type === 'pointerup' && travel < 8 && e.timeStamp - downAt < TAP_MS) {
			if (e.timeStamp - lastTap < TAP_MS) { lastTap = 0; toggle() } else lastTap = e.timeStamp
		}
	}

	$effect(() => {
		const el = stage
		if (!el) return
		// rotella e pizzico del trackpad (ctrl+rotella): zoom attorno al cursore; serve non passivo per fermare lo zoom della pagina
		const wheel = (e: WheelEvent) => {
			e.preventDefault()
			stopAnim()
			const p = rel(e)
			pin(p.x, p.y, cx + (p.x - sw / 2) / k, cy + (p.y - sh / 2) / k, k * Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0015)))
		}
		el.addEventListener('wheel', wheel, { passive: false })
		return () => { el.removeEventListener('wheel', wheel); stopAnim() }
	})

	// Esc chiude solo il visore, non la scheda sotto: ascolta in cattura e ferma l'evento
	$effect(() => {
		const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); onclose() } }
		window.addEventListener('keydown', esc, true)
		return () => window.removeEventListener('keydown', esc, true)
	})
</script>

<div class="zoom" role="dialog" aria-modal="true" aria-label={t('Mappa muscolare')}>
	<div class="top">
		<span style="width:40px"></span>
		<div class="seg">
			<button class:on={side === 'front'} onclick={() => (side = 'front')}>{t('Fronte')}</button>
			<button class:on={side === 'back'} onclick={() => (side = 'back')}>{t('Retro')}</button>
		</div>
		<button class="icon-btn" onclick={onclose} aria-label={t('Chiudi')}><Icon name="x" /></button>
	</div>
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="stage" bind:this={stage} bind:clientWidth={sw} bind:clientHeight={sh}
		onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up}>
		<div class="fill"><MuscleFigure {muscles} view={side} {box} /></div>
	</div>
	{#if mine.length}<div class="chips"><MuscleChips muscles={mine} /></div>{/if}
</div>

<style>
	.zoom { position: fixed; inset: 0; z-index: 50; display: flex; flex-direction: column; background: var(--bg); padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left); animation: fade 0.2s var(--ease-out); }
	.top { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 16px; }
	.seg { width: 200px; }
	.stage { position: relative; flex: 1; min-height: 0; touch-action: none; user-select: none; -webkit-user-select: none; overflow: hidden; cursor: grab; }
	.stage:active { cursor: grabbing; }
	.fill { position: absolute; inset: 0; --fig-h: 100%; }
	.chips { padding: 12px 16px 16px; display: flex; justify-content: center; }
</style>
