<script lang="ts">
	import { tick } from 'svelte'
	import { setSetting } from '../lib/actions.ts'
	import { t } from '../lib/i18n.ts'

	/**
	 * Un suggerimento della prima volta sul controllo `target` (selettore `[data-tour=…]`), dentro il foglio stesso: il
	 * tutorial principale si nasconde quando c'è un foglio aperto, quindi i due non si sovrappongono. Come il tutorial
	 * evidenzia il controllo vero, ma non blocca i tocchi. «Salta» li spegne per sempre.
	 */
	let { target, title, text, last = false, onnext }: { target: string; title: string; text: string; last?: boolean; onnext: () => void } = $props()

	let rect = $state<{ x: number; y: number; w: number; h: number } | null>(null)
	$effect(() => {
		const sel = target
		let alive = true
		const measure = () => {
			if (!alive) return
			const r = (document.querySelector(sel) as HTMLElement | null)?.getBoundingClientRect()
			rect = r && r.width > 0 ? { x: r.left, y: r.top, w: r.width, h: r.height } : null
		}
		void tick().then(measure)
		const id = setInterval(measure, 250)
		addEventListener('resize', measure); addEventListener('scroll', measure, true)
		return () => { alive = false; clearInterval(id); removeEventListener('resize', measure); removeEventListener('scroll', measure, true) }
	})

	const PAD = 8
	const hole = $derived(rect ? { x: Math.max(0, rect.x - PAD), y: Math.max(0, rect.y - PAD), w: rect.w + PAD * 2, h: rect.h + PAD * 2 } : null)
	// sotto il controllo se sta nella metà alta dello schermo, sopra altrimenti
	const cardStyle = $derived(!hole ? 'bottom:16px' : hole.y + hole.h / 2 < innerHeight / 2 ? `top:${hole.y + hole.h + 12}px` : `bottom:${innerHeight - hole.y + 12}px`)
	function done() { if (last) setSetting('machineTipsSeen', true); onnext() }
</script>

{#if hole}<div class="ring" style="left:{hole.x}px;top:{hole.y}px;width:{hole.w}px;height:{hole.h}px"></div>{/if}
<div class="card tip" style={cardStyle} role="dialog" aria-label={title}>
	<div class="mid">{title}</div>
	<p class="muted small" style="margin:6px 0 12px">{text}</p>
	<div class="row between">
		<button class="link small" onclick={() => setSetting('machineTipsSeen', true)}>{t('Salta')}</button>
		<button class="btn primary small" onclick={done}>{last ? t('Fine') : t('Ok')}</button>
	</div>
</div>

<style>
	.ring { position: fixed; z-index: 50; border-radius: 16px; pointer-events: none; border: 2px solid var(--accent); box-shadow: 0 0 0 4px rgba(var(--accent-rgb), .25), 0 0 24px rgba(var(--accent-rgb), .5); animation: glow 1.6s ease-in-out infinite; }
	@keyframes glow { 50% { box-shadow: 0 0 0 8px rgba(var(--accent-rgb), .12), 0 0 34px rgba(var(--accent-rgb), .7); } }
	.tip { position: fixed; z-index: 51; left: 16px; right: 16px; max-width: 420px; margin: 0 auto; background: #151a28; border-color: rgba(var(--accent-rgb), .4); box-shadow: 0 20px 50px rgba(0,0,0,.6); animation: tip-in var(--dur-2) var(--ease-out) backwards; }
	@keyframes tip-in { from { opacity: 0; transform: scale(0.98); } }
	@media (prefers-reduced-motion: reduce) { .ring { animation: none; } }
</style>
