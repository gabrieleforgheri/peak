<script lang="ts">
	import { tick } from 'svelte'
	import { setSetting } from '../lib/actions.ts'
	import { t } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'

	/**
	 * Il tutorial sulle schermate vere: un riquadro luminoso su un controllo alla volta. I passi «da fare» (aggiungi un
	 * macchinario, crea una scheda) aspettano che lo fai davvero e si saltano da soli se l'hai già fatto.
	 */
	const m = $derived(getModel())

	interface Step { id: string; route?: string; target?: string; title: string; text: string; done?: () => boolean; optional?: boolean }
	const hasMachine = () => m.exercises.some((e) => !e.isArchived)
	const hasTemplate = () => m.templates.length > 0
	const steps = $derived<Step[]>([
		{ id: 'welcome', route: 'home', title: t('Benvenuto in Peak'), text: t('Un minuto per fare il giro: ti mostro dove sta ogni cosa e ti faccio creare il primo macchinario e la prima scheda.') },
		{ id: 'home', route: 'home', target: '[data-tour="hero"]', title: t('La tua Home'), text: t('Qui trovi la tua settimana, i grafici, le medaglie e le novità dei tuoi amici. Il pulsante sotto la montagna ti fa partire con la prossima scheda.') },
		{ id: 'machines', route: 'macchinari', target: '[data-tour="tab-macchinari"]', title: t('Macchinari'), text: t('Qui tieni i tuoi macchinari e i loro massimali. Si aggiungono cercando in un catalogo: così il tuo «Chest Press» è lo stesso dei tuoi amici e potete fare la classifica.') },
		{ id: 'add-machine', route: 'macchinari', target: '[data-tour="add-machine"]', title: t('Aggiungi il primo macchinario'), text: t('Tocca + e cerca nel catalogo. Poi gli puoi dare un nome tuo.'), done: hasMachine },
		{ id: 'workout', route: 'allenamento', target: '[data-tour="tab-allenamento"]', title: t('Allenamento'), text: t('Qui alleni: scegli una scheda o parti a mano e registra le serie una per una.') },
		{ id: 'template', route: 'allenamento', target: '[data-tour="new-template"]', title: t('Crea la tua prima scheda'), text: t('Una scheda è una lista di esercizi con serie e ripetizioni: la scegli quando inizi e li trovi già pronti.'), done: hasTemplate },
		{ id: 'progress', route: 'progressi', target: '[data-tour="tab-progressi"]', title: t('Progressi'), text: t('I tuoi numeri nel tempo: volume, record, peso corporeo.') },
		{ id: 'friends', route: 'amici', target: '[data-tour="tab-amici"]', title: t('Amici'), text: t('Invita gli amici con un link o un QR: vedete i massimali e le schede l\'uno dell\'altro e fate la classifica.') },
		{ id: 'invite', route: 'amici', target: '[data-tour="invite"]', title: t('Invita un amico'), text: t('Quando vuoi. Il link vale una volta sola.'), optional: true },
		{ id: 'settings', route: 'impostazioni', target: '[data-tour="tab-impostazioni"]', title: t('Impostazioni'), text: t('Unità, colore, timer di recupero, backup e il tuo account yG. Il tutorial lo rivedi da Generale.') },
	])

	// si parte dal primo passo che non è già soddisfatto
	let at = $state(0)
	let rect = $state<{ x: number; y: number; w: number; h: number } | null>(null)
	let hidden = $state(false)
	const step = $derived(steps[at])
	const last = $derived(at === steps.length - 1)

	function finish() { setSetting('tutorialDone', true) }
	function advance() {
		let n = at + 1
		while (n < steps.length && steps[n]!.done?.()) n++ // chi l'ha già fatto salta il passo
		if (n >= steps.length) return finish()
		at = n
	}
	// un passo «da fare» si chiude quando l'hai fatto
	$effect(() => { if (step?.done?.()) { const id = step.id; setTimeout(() => { if (steps[at]?.id === id) advance() }, 700) } })

	// porta alla schermata del passo e segue il controllo (si sposta, si apre un foglio…)
	$effect(() => {
		const s = step
		if (!s) return
		if (s.route) go(s.route, true)
		let alive = true
		const measure = () => {
			if (!alive) return
			hidden = !!document.querySelector('.backdrop') // un foglio aperto (il catalogo, l'editor) passa davanti
			const el = s.target ? (document.querySelector(s.target) as HTMLElement | null) : null
			const r = el?.getBoundingClientRect()
			rect = r && r.width > 0 ? { x: r.left, y: r.top, w: r.width, h: r.height } : null
		}
		void tick().then(measure)
		const id = setInterval(measure, 250)
		addEventListener('resize', measure); addEventListener('scroll', measure, true)
		return () => { alive = false; clearInterval(id); removeEventListener('resize', measure); removeEventListener('scroll', measure, true) }
	})

	const PAD = 8
	const hole = $derived(rect ? { x: Math.max(0, rect.x - PAD), y: Math.max(0, rect.y - PAD), w: rect.w + PAD * 2, h: rect.h + PAD * 2 } : null)
	// il riquadro sta fuori dal buco: sotto se c'è posto, sopra altrimenti
	const cardStyle = $derived.by(() => {
		if (!hole) return 'top:50%;transform:translateY(-50%)'
		const below = hole.y + hole.h + 14
		return innerHeight - below > 190 ? `top:${below}px` : `bottom:${innerHeight - hole.y + 14}px`
	})
	// dentro il buco i tocchi arrivano al controllo vero; fuori, il resto è bloccato
	const clip = $derived(hole ? `polygon(evenodd, 0 0, 100% 0, 100% 100%, 0 100%, 0 0, ${hole.x}px ${hole.y}px, ${hole.x}px ${hole.y + hole.h}px, ${hole.x + hole.w}px ${hole.y + hole.h}px, ${hole.x + hole.w}px ${hole.y}px, ${hole.x}px ${hole.y}px)` : 'none')
</script>

{#if step && !hidden}
	<div class="veil" style="clip-path:{clip}"></div>
	{#if hole}<div class="ring" style="left:{hole.x}px;top:{hole.y}px;width:{hole.w}px;height:{hole.h}px"></div>{/if}
	<div class="card tour" style={cardStyle} role="dialog" aria-label={step.title}>
		<div class="tiny faint" style="margin-bottom:4px">{at + 1} / {steps.length}</div>
		<div class="mid">{step.title}</div>
		<p class="muted small" style="margin:6px 0 14px">{step.text}</p>
		<div class="row between">
			<button class="link small" onclick={finish}>{t('Salta il tutorial')}</button>
			{#if !step.done || step.optional}<button class="btn primary small" onclick={advance}>{last ? t('Fine') : t('Avanti')}</button>
			{:else}<span class="small faint">{t('Fallo per continuare')}</span>{/if}
		</div>
	</div>
{/if}

<style>
	.veil { position: fixed; inset: 0; z-index: 30; background: rgba(4, 7, 14, .78); }
	.ring { position: fixed; z-index: 31; border-radius: 16px; pointer-events: none; border: 2px solid var(--accent); box-shadow: 0 0 0 4px rgba(var(--accent-rgb), .25), 0 0 24px rgba(var(--accent-rgb), .5); animation: glow 1.6s ease-in-out infinite; }
	@keyframes glow { 50% { box-shadow: 0 0 0 8px rgba(var(--accent-rgb), .12), 0 0 34px rgba(var(--accent-rgb), .7); } }
	.tour { position: fixed; z-index: 32; left: 16px; right: 16px; max-width: 420px; margin: 0 auto; background: #151a28; border-color: rgba(var(--accent-rgb), .4); box-shadow: 0 20px 50px rgba(0,0,0,.6); }
	@media (prefers-reduced-motion: reduce) { .ring { animation: none; } }
</style>
