<script lang="ts">
	import { onDestroy } from 'svelte'
	import { quintOut } from 'svelte/easing'
	import { MUSCLE_GROUPS, type MuscleGroup } from '@common/docs.ts'
	import type { CatalogEntry } from '@common/catalog.ts'
	import { SUGGESTED_MACHINES, SUGGESTED_PRESELECTED } from '@common/onboarding.ts'
	import { createStarterPlan, deleteExercise, setSetting, undoStarterPlan } from '../lib/actions.ts'
	import { entryName, loadCatalog } from '../lib/catalog.ts'
	import { reduced } from '../lib/fx.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { play, playDay } from '../lib/sound.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import ExerciseEditor from '../components/ExerciseEditor.svelte'
	import Icon from '../components/Icon.svelte'
	import SummitDraw from '../components/SummitDraw.svelte'
	import TemplateIconView from '../components/TemplateIcon.svelte'
	import Thumb from '../components/Thumb.svelte'

	/**
	 * Il primo avvio di un account vuoto, a tutto schermo e senza scorciatoie: benvenuto, quanti giorni, quali gruppi, quali
	 * macchinari, scheda pronta. I dati si creano entrando nell'ultima schermata (e si tolgono se torni indietro); finisce con `onfinish`.
	 */
	let { onfinish }: { onfinish: () => void } = $props()

	const LAST = 4
	let step = $state(0)
	let dir = 1 // 1 = avanti, -1 = indietro: da che parte scorre lo schermo
	let heroSeen = $state(false) // la montagna si disegna una volta sola; tornando al benvenuto è già finita

	let goal = $state(getModel().settings.weeklyGoal)
	let groups = $state(new Set<MuscleGroup>())
	const picked = $derived(MUSCLE_GROUPS.filter((g) => groups.has(g)))
	// i macchinari: voci del catalogo scelte fra i suggeriti, e quelli aggiunti dalla ricerca (già creati dall'editor)
	let on = $state(new Set<string>())
	let extras = $state<{ uuid: string; group: MuscleGroup }[]>([])
	const seeded = new Set<MuscleGroup>()
	let catalog = $state(new Map<string, CatalogEntry>())
	let failed = $state(false)
	loadCatalog().then((l) => (catalog = new Map(l.map((e) => [e.id, e]))), () => (failed = true))
	let searching = $state(false)

	const chosenOf = (g: MuscleGroup) => SUGGESTED_MACHINES[g].filter((id) => on.has(id) && catalog.has(id))
	const extrasOf = (g: MuscleGroup) => extras.filter((x) => x.group === g)
	const total = $derived(picked.reduce((n, g) => n + chosenOf(g).length + extrasOf(g).length, 0))

	let plan = $state<{ program: string; exercises: string[] } | null>(null)
	let made = $state<{ group: MuscleGroup; name: string; count: number }[]>([])
	let timer: ReturnType<typeof setTimeout> | undefined
	onDestroy(() => clearTimeout(timer))

	function go(to: number) { dir = to > step ? 1 : -1; step = to }
	function back() {
		clearTimeout(timer)
		if (step === LAST && plan) { undoStarterPlan(plan); plan = null }
		go(step - 1)
	}

	// 2. i giorni: si tocca un numero e dopo un attimo si va avanti
	function pickDays(n: number) {
		goal = n
		setSetting('weeklyGoal', n)
		playDay(n - 1)
		clearTimeout(timer)
		timer = setTimeout(() => go(2), 250)
	}

	// 3. i gruppi
	function toggleGroup(g: MuscleGroup) {
		const n = new Set(groups)
		n.has(g) ? n.delete(g) : n.add(g)
		groups = n
	}
	function toMachines() {
		// ogni gruppo, la prima volta, parte con i suoi primi tre già scelti
		const n = new Set(on)
		for (const g of picked) if (!seeded.has(g)) { seeded.add(g); SUGGESTED_MACHINES[g].slice(0, SUGGESTED_PRESELECTED).forEach((id) => n.add(id)) }
		on = n
		go(3)
	}

	// 4. i macchinari
	function toggleMachine(id: string) {
		const n = new Set(on)
		n.has(id) ? n.delete(id) : n.add(id)
		on = n
	}
	function dropExtra(uuid: string) {
		extras = extras.filter((x) => x.uuid !== uuid)
		deleteExercise(uuid) // l'aveva creato la ricerca solo per questa scelta
	}
	// un macchinario aggiunto dalla ricerca finisce nel suo gruppo; se il gruppo non era scelto, si aggiunge (e parte vuoto)
	function added(uuid: string) {
		const ex = getModel().exById.get(uuid)
		if (!ex) return
		const g = ex.muscleGroup
		if (!groups.has(g)) { groups = new Set(groups).add(g); seeded.add(g) }
		if (ex.catalog && SUGGESTED_MACHINES[g].includes(ex.catalog)) on = new Set(on).add(ex.catalog)
		else extras = [...extras, { uuid, group: g }]
	}

	// 5. i dati: una scheda con un giorno per gruppo, nell'ordine di sempre
	function toReady() {
		const days = picked.map((g) => ({ group: g, name: GROUP_NAMES[g], machines: [...chosenOf(g).map((id) => catalog.get(id)!), ...extrasOf(g).map((x) => x.uuid)] })).filter((d) => d.machines.length)
		plan = createStarterPlan(t('La mia scheda'), days)
		made = days.map((d) => ({ group: d.group, name: d.name, count: d.machines.length }))
		play('goal')
		go(LAST)
	}

	// il cambio di schermo: scorre di poco e sfuma (350 ms); con «riduci movimento» solo una dissolvenza breve
	function pane(sign: 1 | -1) {
		return (_: Element) => {
			const r = reduced(), d = dir
			return { duration: r ? 160 : 350, easing: quintOut, css: (v: number, u: number) => (r ? `opacity:${v}` : `opacity:${v};transform:translateX(${sign * d * u * 36}px)`) }
		}
	}
	const paneIn = pane(1)
	const paneOut = pane(-1)
	// la tastiera riparte dal titolo del nuovo schermo
	const focusTitle = (node: HTMLElement) => { node.focus({ preventScroll: true }) }
</script>

<main class="ob" aria-label="Peak">
	<header class="ob-head ob-col" class:hide={step === 0}>
		<button class="ob-back" onclick={back} aria-label={t('Indietro')}><Icon name="left" size={26} /></button>
		<div class="ob-prog" role="progressbar" aria-label={t('Passo {0} di {1}', step, LAST)} aria-valuemin="0" aria-valuemax={LAST} aria-valuenow={step} style="--p:{step / LAST}"><i></i></div>
		<span class="end"></span>
	</header>

	<div class="ob-stage">
		{#key step}
			<div class="ob-pane" in:paneIn out:paneOut onoutrostart={(e) => ((e.currentTarget as HTMLElement).inert = true)}>
				{#if step === 0}
					<div class="ob-body">
						<div class="ob-col hero ob-mid ob-split">
							<div class="art"><SummitDraw still={heroSeen} /></div>
							<div>
								<h1 class="ob-title peak" class:late={!heroSeen} tabindex="-1" use:focusTitle>Peak</h1>
								<p class="ob-sub tag" class:late={!heroSeen}>{t('Ogni allenamento, un po\' più in alto.')}</p>
							</div>
						</div>
					</div>
					<div class="ob-foot"><div class="ob-col"><button class="btn primary block ob-cta cta" class:late={!heroSeen} onclick={() => { heroSeen = true; go(1) }}>{t('Iniziamo')}</button></div></div>

				{:else if step === 1}
					<div class="ob-body">
						<div class="ob-col ob-mid">
							<h1 class="ob-title" tabindex="-1" use:focusTitle>{t('Quante volte ti alleni?')}</h1>
							<p class="ob-sub">{t('a settimana')}</p>
							<div class="nums" role="radiogroup" aria-label={t('Quante volte ti alleni?')}>
								{#each [1, 2, 3, 4, 5, 6, 7] as n}
									<button class="num" class:on={goal === n} role="radio" aria-checked={goal === n} onclick={() => pickDays(n)}>{n}</button>
								{/each}
							</div>
						</div>
					</div>

				{:else if step === 2}
					<div class="ob-body">
						<div class="ob-col ob-mid">
							<h1 class="ob-title" tabindex="-1" use:focusTitle>{t('Cosa alleni?')}</h1>
							<p class="ob-sub">{t('Un giorno per ogni gruppo.')}</p>
							<div class="gtiles" role="group" aria-label={t('Cosa alleni?')}>
								{#each MUSCLE_GROUPS as g}
									<button class="gtile" class:on={groups.has(g)} aria-pressed={groups.has(g)} style="--c:var(--{g})" onclick={() => toggleGroup(g)}>
										<span class="chk" aria-hidden="true">{#if groups.has(g)}<Icon name="check" size={14} />{/if}</span>
										<TemplateIconView icon={`muscle:${g}` as const} size={36} />
										<span class="gn">{GROUP_NAMES[g]}</span>
									</button>
								{/each}
							</div>
						</div>
					</div>
					<div class="ob-foot"><div class="ob-col"><button class="btn primary block ob-cta" disabled={groups.size === 0} onclick={toMachines}>{t('Continua')}</button></div></div>

				{:else if step === 3}
					<div class="ob-body">
						<div class="ob-col">
							<h1 class="ob-title" tabindex="-1" use:focusTitle>{t('I tuoi macchinari')}</h1>
							<p class="ob-sub">{t('Tocca quelli che hai in palestra.')}</p>
							{#if failed}<div class="banner warn" style="margin-top:16px">{t('Il catalogo non si carica: serve la connessione.')}</div>{/if}
							{#each picked as g (g)}
								<section class="msec" style="--c:var(--{g})">
									<h2><i></i>{GROUP_NAMES[g]}<span>{chosenOf(g).length + extrasOf(g).length}</span></h2>
									<div class="mgrid">
										{#each SUGGESTED_MACHINES[g] as id (id)}
											{@const e = catalog.get(id)}
											{#if e}
												<button class="mach" class:on={on.has(id)} aria-pressed={on.has(id)} onclick={() => toggleMachine(id)}>
													<span class="chk" aria-hidden="true">{#if on.has(id)}<Icon name="check" size={14} />{/if}</span>
													<Thumb ex={{ photo: '', photoOffset: 0, glyphName: e.glyph, muscleGroup: g }} />
													<span class="mn">{entryName(e)}</span>
												</button>
											{:else}<div class="mach skel" aria-hidden="true"></div>{/if}
										{/each}
										{#each extrasOf(g) as x (x.uuid)}
											{@const ex = getModel().exById.get(x.uuid)}
											{#if ex}
												<button class="mach on" aria-pressed="true" onclick={() => dropExtra(x.uuid)}>
													<span class="chk" aria-hidden="true"><Icon name="check" size={14} /></span>
													<Thumb {ex} />
													<span class="mn">{ex.name}</span>
												</button>
											{/if}
										{/each}
									</div>
								</section>
							{/each}
							<button class="btn block find" onclick={() => (searching = true)}><Icon name="search" size={20} />{t('Cerca nel catalogo')}</button>
						</div>
					</div>
					<div class="ob-foot"><div class="ob-col"><button class="btn primary block ob-cta" disabled={total === 0} onclick={toReady}>{t('Continua')}</button></div></div>

				{:else}
					<div class="ob-body">
						<div class="ob-col ob-mid">
							<h1 class="ob-title" tabindex="-1" use:focusTitle>{t('Tutto pronto')}</h1>
							<p class="ob-sub">{t('Ecco la tua scheda.')}</p>
							<div class="days">
								{#each made as d, i}
									<div class="day" style="--i:{i};--c:var(--{d.group})">
										<span class="di"><TemplateIconView icon={`muscle:${d.group}` as const} size={26} /></span>
										<div class="grow"><b class="ellipsis">{d.name}</b><span class="small muted">{tn(d.count, '1 macchinario', '{0} macchinari')}</span></div>
									</div>
								{/each}
							</div>
						</div>
					</div>
					<div class="ob-foot"><div class="ob-col"><button class="btn primary block ob-cta" onclick={onfinish}>{t('Fai il giro')}</button></div></div>
				{/if}
			</div>
		{/key}
	</div>
</main>

{#if searching}<ExerciseEditor onclose={() => (searching = false)} onsaved={added} />{/if}

<style>
	.ob-head.hide > * { visibility: hidden; }
	.end { flex: none; width: 34px; }

	/* benvenuto */
	.art { width: 100%; max-width: 640px; }
	.art :global(svg) { max-height: min(50dvh, 460px); }
	@media (orientation: landscape) and (max-height: 520px) { .art :global(svg) { max-height: calc(100dvh - 140px); } }
	.peak { font-size: clamp(44px, 13vw, 60px); letter-spacing: -0.04em; }
	.tag { font-size: 18px; margin-top: 10px; }
	.late { animation: rise 600ms var(--ease-out) 2100ms backwards; }
	.ob-sub.late { animation-delay: 2250ms; }
	.cta.late { animation-delay: 2450ms; }
	@keyframes rise { from { opacity: 0; transform: translateY(10px); } }

	/* giorni a settimana: quattro più tre, sempre quadrati e grandi */
	.nums { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 28px; }
	.num { flex: 0 0 calc((100% - 30px) / 4); aspect-ratio: 1; border-radius: 22px; border: 2px solid var(--stroke); background: var(--card); color: var(--text); font-size: 34px; font-weight: 900; font-variant-numeric: tabular-nums;
		transition: transform 120ms var(--ease-out), background 150ms, border-color 150ms; }
	.num:active { transform: scale(0.93); }
	.num.on { background: var(--accent); border-color: var(--accent); color: var(--on-accent); box-shadow: 0 0 28px rgba(var(--accent-rgb), 0.35); animation: choose 300ms var(--ease-out); }
	@keyframes choose { 45% { transform: scale(1.12); } }
	@media (orientation: landscape) and (max-height: 520px) {
		.nums { margin-top: 14px; gap: 8px; }
		.num { flex-basis: calc((100% - 48px) / 7); border-radius: 16px; font-size: 24px; }
	}

	/* gruppi */
	.gtiles { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 24px; }
	.gtile { position: relative; min-height: 104px; padding: 14px 6px 10px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; border-radius: 18px; border: 2px solid var(--stroke); background: var(--card); color: var(--text);
		transition: transform 120ms var(--ease-out), background 150ms, border-color 150ms; }
	.gtile:active { transform: scale(0.96); }
	.gtile.on { border-color: var(--c); background: color-mix(in srgb, var(--c) 16%, transparent); }
	.gn { font-size: 14px; font-weight: 700; max-width: 100%; overflow-wrap: anywhere; }
	.chk { position: absolute; top: 8px; right: 8px; width: 20px; height: 20px; border-radius: 50%; display: grid; place-items: center; border: 1.5px solid var(--stroke-strong); color: var(--on-accent); }
	.on > .chk { border-color: transparent; background: var(--c, var(--accent)); animation: choose 300ms var(--ease-out); }
	.mach.on > .chk { background: var(--accent); }
	@media (orientation: landscape) and (max-height: 520px) { .gtiles { margin-top: 12px; gap: 8px; } .gtile { min-height: 78px; padding-block: 8px; gap: 4px; } }

	/* macchinari */
	.msec { margin-top: 22px; }
	.msec h2 { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; font-size: 13px; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; color: var(--c); }
	.msec h2 i { width: 10px; height: 10px; border-radius: 50%; background: var(--c); }
	.msec h2 span { margin-left: auto; color: var(--text-3); letter-spacing: 0; font-variant-numeric: tabular-nums; }
	.mgrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; }
	.mach { position: relative; min-height: 118px; padding: 12px 8px 10px; display: flex; flex-direction: column; align-items: center; justify-content: flex-start; gap: 8px; border-radius: 16px; border: 2px solid var(--stroke); background: var(--card); color: var(--text); text-align: center;
		transition: transform 120ms var(--ease-out), background 150ms, border-color 150ms; }
	.mach:active { transform: scale(0.97); }
	.mach.on { border-color: var(--accent); background: rgba(var(--accent-rgb), 0.1); }
	.mach :global(.thumb) { width: 56px; height: 56px; border-radius: 16px; }
	.mn { font-size: 13px; font-weight: 700; line-height: 1.2; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
	.skel { background: linear-gradient(100deg, var(--card) 30%, var(--card-strong) 50%, var(--card) 70%) 0 0 / 200% 100%; border-color: transparent; animation: shimmer 1.2s linear infinite; }
	@keyframes shimmer { to { background-position: -200% 0; } }
	.find { margin-top: 22px; }

	/* scheda pronta: i giorni si compongono uno dopo l'altro */
	.days { display: flex; flex-direction: column; gap: 10px; margin-top: 24px; }
	.day { display: flex; align-items: center; gap: 14px; padding: 14px 16px; min-height: 68px; border-radius: 18px; border: 1px solid color-mix(in srgb, var(--c) 40%, transparent); background: color-mix(in srgb, var(--c) 10%, var(--card));
		animation: compose 520ms var(--ease-out) calc(280ms + var(--i) * 80ms) backwards; }
	.day b { display: block; font-size: 17px; }
	.di { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; flex: none; background: color-mix(in srgb, var(--c) 20%, transparent); }
	@keyframes compose { from { opacity: 0; transform: translateY(16px) scale(0.96); } }
</style>
