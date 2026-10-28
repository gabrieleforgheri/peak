<script lang="ts">
	import { onDestroy, tick } from 'svelte'
	import { setSetting } from '../lib/actions.ts'
	import { reduced } from '../lib/fx.ts'
	import { t } from '../lib/i18n.ts'
	import { go, route } from '../lib/router.svelte.ts'
	import { batch, getModel } from '../lib/store.svelte.ts'
	import Ideas from './Ideas.svelte'
	import Icon from './Icon.svelte'

	/**
	 * Il tutorial: prima quattro schermi con le idee di Peak (Ideas), poi un giro sulle schermate vere con un riquadro luminoso che
	 * vola da un controllo all'altro: le quattro schede, «Avvia allenamento», «+ Serie». Gli ultimi due si fanno davvero:
	 * parte un allenamento vero e la prima serie salvata chiude il giro. Si salta da ogni schermo e da ogni passo.
	 */
	let { onend }: { onend: () => void } = $props()
	const m = $derived(getModel())

	type StepId = 'tabs' | 'start' | 'set'
	let phase = $state<'ideas' | 'marks'>('ideas')
	let stepId = $state<StepId>('tabs')
	let cheer = $state(false)
	let cheerTimer: ReturnType<typeof setTimeout> | undefined
	onDestroy(() => clearTimeout(cheerTimer))

	const STEPS = $derived<Record<StepId, { target: string; title: string; text: string; next?: boolean }>>({
		tabs: { target: '[data-tour="tabs"]', title: t('Quattro sezioni'), text: t('Allenamento, Macchinari, Me, Amici.'), next: true },
		start: { target: '[data-tour="start"]', title: t('Inizia da qui'), text: t('Tocca il pulsante per iniziare.') },
		set: { target: '[data-tour="add-set"]', title: t('Aggiungi una serie'), text: t('Tocca + Serie e salvala.') },
	})
	const step = $derived(phase === 'marks' ? STEPS[stepId] : null)

	function finish() { batch(() => { setSetting('tutorialDone', true); setSetting('tourSeen', 3) }) }
	function skip() { finish(); onend() }

	// ---- dove siamo: l'allenamento aperto e se se ne sta guardando la pagina ----
	const active = $derived(m.workouts.find((w) => w.isActive))
	const inSession = $derived(!!active && route.parts[0] === 'allenamento' && route.parts[1] === active.uuid && route.parts[2] !== 'riepilogo')
	const onMain = $derived(route.parts.length <= 1 && route.parts[0] !== 'impostazioni')
	const onWorkouts = $derived(route.parts.length === 0 || route.parts[0] === 'allenamento')

	// porta alla schermata del passo (dopo «Rivedi il tutorial» si parte da Impostazioni); chi ha già un allenamento aperto riprende da lì
	$effect(() => {
		if (phase !== 'marks' || cheer) return
		if (stepId === 'tabs') { if (!onMain) go('allenamento', true) }
		else if (stepId === 'start') {
			if (inSession) stepId = 'set'
			else if (active) go(`allenamento/${active.uuid}`, true)
			else if (!onWorkouts || route.parts.length > 1) go('allenamento', true)
		} else if (!active) stepId = 'start'
		else if (!inSession) go(`allenamento/${active.uuid}`, true)
	})

	// l'ultimo passo si chiude da solo quando nell'allenamento compare una serie in più
	let base = -1
	$effect(() => {
		if (phase !== 'marks' || stepId !== 'set' || !active || cheer) { base = -1; return }
		const n = active.sets.length
		if (base < 0 || n < base) base = n
		else if (n > base) {
			finish()
			cheer = true
			cheerTimer = setTimeout(onend, 3600)
		}
	})

	// ---- il riquadro: segue il controllo e vola da un passo all'altro ----
	let rect = $state<{ x: number; y: number; w: number; h: number } | null>(null)
	let hidden = $state(false)
	let fly = $state(false)
	let flyTimer: ReturnType<typeof setTimeout> | undefined
	onDestroy(() => clearTimeout(flyTimer))
	$effect(() => {
		const s = step
		if (!s) return
		fly = true
		clearTimeout(flyTimer)
		flyTimer = setTimeout(() => (fly = false), 520) // la scia vale solo per il cambio di passo, non mentre la pagina scorre
		let alive = true
		let scrolledAt = 0
		const measure = () => {
			if (!alive) return
			hidden = !!document.querySelector('.backdrop') // un foglio aperto (il catalogo, la serie) passa davanti
			const el = document.querySelector(s.target) as HTMLElement | null
			const r = el?.getBoundingClientRect()
			if (el && r && r.width > 0) {
				// fuori dallo schermo: si porta al centro (il velo non lascia scorrere a mano)
				if ((r.top < 70 || r.bottom > innerHeight - 90) && Date.now() - scrolledAt > 900) { scrolledAt = Date.now(); el.scrollIntoView({ block: 'center', behavior: reduced() ? 'auto' : 'smooth' }) }
				rect = { x: r.left, y: r.top, w: r.width, h: r.height }
			} else rect = null
		}
		void tick().then(measure)
		const id = setInterval(measure, 250)
		addEventListener('resize', measure); addEventListener('scroll', measure, true)
		return () => { alive = false; clearInterval(id); removeEventListener('resize', measure); removeEventListener('scroll', measure, true) }
	})

	const PAD = 6
	const hole = $derived.by(() => {
		if (!rect) return null
		const x = Math.max(3, rect.x - PAD), y = Math.max(3, rect.y - PAD)
		return { x, y, w: Math.min(innerWidth - 3, rect.x + rect.w + PAD) - x, h: Math.min(innerHeight - 3, rect.y + rect.h + PAD) - y }
	})
	// la carta sta fuori dal buco: sotto se c'è posto, sopra altrimenti; senza controllo (non c'è ancora) in basso, senza velo
	const cardStyle = $derived.by(() => {
		if (!hole) return 'bottom:calc(env(safe-area-inset-bottom, 0px) + 24px)'
		// schermo largo, controllo nel binario a sinistra: la carta gli sta accanto
		if (innerWidth >= 900 && hole.x + hole.w < 260) return `left:${hole.x + hole.w + 18}px;right:auto;margin:0;top:${Math.max(16, Math.min(hole.y, innerHeight - 200))}px`
		const below = hole.y + hole.h + 14
		return innerHeight - below > 170 ? `top:${below}px` : `bottom:${innerHeight - hole.y + 14}px`
	})
	// dentro il buco i tocchi arrivano al controllo vero; fuori, il resto è bloccato
	const clip = $derived(hole ? `polygon(evenodd, 0 0, 100% 0, 100% 100%, 0 100%, 0 0, ${hole.x}px ${hole.y}px, ${hole.x}px ${hole.y + hole.h}px, ${hole.x + hole.w}px ${hole.y + hole.h}px, ${hole.x + hole.w}px ${hole.y}px, ${hole.x}px ${hole.y}px)` : 'none')
</script>

{#if phase === 'ideas'}
	<Ideas onnext={() => (phase = 'marks')} onskip={skip} />
{:else if cheer}
	<div class="card coach cheer" role="status">
		<svg viewBox="0 0 48 48" width="48" height="48" aria-hidden="true"><circle cx="24" cy="24" r="21" /><path d="M14 25l7 7 13-15" /></svg>
		<div class="mid">{t('Prima serie fatta')}</div>
		<p class="muted small">{t('Ora sai come si fa.')}</p>
		<button class="btn primary block" onclick={onend}>{t('Fine')}</button>
	</div>
{:else if step && !hidden}
	{#if hole}
		<div class="shield" style="clip-path:{clip}"></div>
		<div class="spot" class:fly style="left:{hole.x}px;top:{hole.y}px;width:{hole.w}px;height:{hole.h}px"><i class="dim"></i><i class="ring"></i></div>
	{/if}
	{#key stepId}
		<div class="card coach" style={cardStyle} role="dialog" aria-label={step.title}>
			<div class="mid">{step.title}</div>
			<p class="muted small">{step.text}</p>
			<div class="row between">
				<button class="skip" onclick={skip}>{t('Salta')}</button>
				{#if step.next}<button class="btn primary" onclick={() => (stepId = 'start')}>{t('Avanti')}<Icon name="right" size={18} /></button>
				{:else}<span class="small faint">{t('Fallo per continuare')}</span>{/if}
			</div>
		</div>
	{/key}
{/if}

<style>
	.shield { position: fixed; inset: 0; z-index: 30; }
	/* il buco: l'alone scuro è l'ombra enorme del riquadro; si sposta (left/top/width/height) da un controllo all'altro */
	.spot { position: fixed; z-index: 31; pointer-events: none; }
	.spot.fly { transition: left 450ms var(--ease-out), top 450ms var(--ease-out), width 450ms var(--ease-out), height 450ms var(--ease-out); }
	.spot i { position: absolute; inset: 0; border-radius: 16px; }
	.dim { box-shadow: 0 0 0 150vmax rgba(4, 7, 14, .78); }
	.ring { border: 2px solid var(--accent); animation: ping 1.8s ease-out infinite; }
	@keyframes ping {
		0% { box-shadow: 0 0 0 0 rgba(var(--accent-rgb), .55), 0 0 24px rgba(var(--accent-rgb), .45); }
		100% { box-shadow: 0 0 0 16px rgba(var(--accent-rgb), 0), 0 0 24px rgba(var(--accent-rgb), .45); }
	}
	.coach { position: fixed; z-index: 32; left: max(16px, env(safe-area-inset-left, 0px)); right: max(16px, env(safe-area-inset-right, 0px)); max-width: 380px; max-height: calc(100dvh - 32px); overflow-y: auto; margin: 0 auto; background: #151a28; border-color: rgba(var(--accent-rgb), .4); box-shadow: 0 20px 50px rgba(0, 0, 0, .6);
		animation: cardin 350ms var(--ease-out); }
	.coach .mid { font-size: 18px; letter-spacing: -0.01em; }
	.coach p { margin: 4px 0 12px; text-wrap: balance; }
	.coach .btn { min-height: 44px; }
	.coach :focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
	.skip { min-height: 44px; margin-left: -8px; padding: 0 8px; border: 0; background: none; color: var(--text-2); font-weight: 600; font-size: 15px; border-radius: 10px; }
	@keyframes cardin { from { opacity: 0; transform: translateY(8px); } }
	/* la prima serie fatta */
	.cheer { top: calc(env(safe-area-inset-top, 0px) + 16px); text-align: center; display: flex; flex-direction: column; align-items: center; gap: 2px; }
	.cheer p { margin-bottom: 10px; }
	.cheer svg { margin-bottom: 4px; }
	.cheer circle { fill: rgba(95, 217, 138, .16); stroke: var(--pos); stroke-width: 2.5; }
	.cheer path { fill: none; stroke: var(--pos); stroke-width: 3.5; stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 30; animation: draw 500ms var(--ease-out) 200ms backwards; }
	@keyframes draw { from { stroke-dashoffset: 30; } }
</style>
