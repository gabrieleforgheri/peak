<script lang="ts">
	import { oneRepMax, toDisplay, round2 } from '@common/domain.ts'
	import { GROUP_NAMES, W, dateLong, dateShort, delta, relDay, setLine, toUnit, unit } from '../lib/fmt.ts'
	import { deleteRecord } from '../lib/actions.ts'
	import { deltaOf, stepOf, suggestionFor, warmupFor } from '../lib/derive.ts'
	import { locale, t, tn } from '../lib/i18n.ts'
	import { back, go } from '../lib/router.svelte.ts'
	import { F, getBoard, loadFriends, type BoardRow } from '../lib/social.svelte.ts'
	import { onMount } from 'svelte'
	import { getModel } from '../lib/store.svelte.ts'
	import EditMaxSheet from '../components/EditMaxSheet.svelte'
	import ExerciseEditor from '../components/ExerciseEditor.svelte'
	import Icon from '../components/Icon.svelte'
	import LineChart from '../components/LineChart.svelte'
	import MonthGroups from '../components/MonthGroups.svelte'
	import FigThumb from '../components/FigThumb.svelte'
	import MuscleDetailSheet from '../components/MuscleDetailSheet.svelte'
	import CardBackground from '../components/CardBackground.svelte'

	let { id }: { id: string } = $props()
	const m = $derived(getModel())
	const ex = $derived(m.exById.get(id))
	type Range = '1m' | '3m' | '1y' | 'all'
	let range = $state<Range>('all')
	let editing = $state(false)
	let updating = $state(false)
	let showingMuscleDetail = $state(false)

	// la classifica con gli amici: solo per i macchinari del catalogo, e solo se hai degli amici
	let board = $state<BoardRow[] | null>(null)
	const catalog = $derived(ex?.catalog ?? '')
	onMount(() => { void loadFriends() })
	$effect(() => { const c = catalog; board = null; if (c) void getBoard(c).then((r) => { if (c === catalog) board = r }) })

	const spanDays = $derived.by(() => {
		if (!ex || !ex.records.length) return 0
		const earliest = Math.min(...ex.records.map((r) => r.date))
		return Math.max(0, (Date.now() - earliest) / 86_400_000)
	})
	const availableRanges = $derived.by((): Range[] => {
		const out: Range[] = []
		if (spanDays >= 30 && spanDays < 365) out.push('1m')
		if (spanDays >= 90) out.push('3m')
		if (spanDays >= 365) out.push('1y')
		out.push('all')
		return out
	})
	$effect(() => {
		if (!availableRanges.includes(range)) range = availableRanges[0] ?? 'all'
	})

	const warm = $derived(ex ? warmupFor(ex, m) : [])
	const next = $derived(ex ? suggestionFor(ex, m) : null)
	const nf = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)

	const chartRecords = $derived.by(() => {
		if (!ex) return []
		const days = range === '1m' ? 30 : range === '3m' ? 90 : range === '1y' ? 365 : null
		const since = days ? Date.now() - days * 86_400_000 : 0
		return [...ex.records].filter((r) => r.date >= since).sort((a, b) => a.date - b.date)
	})
	const points = $derived(chartRecords.map((r) => ({ y: round2(toDisplay(r.weight, m.settings.unit)), label: dateShort(r.date), faded: r.isAuto })))
	const domain = $derived.by((): [number, number] => {
		const ys = points.map((p) => p.y)
		if (!ys.length) return [0, 1]
		const lo = Math.min(...ys), hi = Math.max(...ys)
		const pad = Math.max(1, (hi - lo) * 0.18)
		return [lo - pad, hi + pad]
	})
</script>

<main class="screen no-tabs wide page two">
	{#if !ex}
		<div class="bar span"><button class="icon-btn" onclick={() => back('macchinari')} aria-label={t('Indietro')}><Icon name="left" /></button></div>
		<div class="empty span">{t('Macchinario non trovato.')}</div>
	{:else}
		<div class="bar span">
			<button class="icon-btn" onclick={() => back('macchinari')} aria-label={t('Indietro')}><Icon name="left" /></button>
			<h1 class="small ellipsis">{ex.name}</h1>
			<button class="icon-btn" onclick={() => (editing = true)} aria-label={t('Modifica')}><Icon name="edit" /></button>
		</div>

		<!-- a schermo largo: a sinistra il massimale e cosa fare, a destra andamento e storico -->
		<div class="blk">
		<div class="card" style="--tint:var(--{ex.muscleGroup});position:relative;overflow:hidden">
			<CardBackground {ex} />
			<div class="card-content-wrap">
				<div class="row between" style="align-items:flex-start;gap:12px">
					<div class="grow" style="min-width:0">
						<div class="row wrap" style="gap:6px">
							<span class="badge" style="color:var(--{ex.muscleGroup});background:color-mix(in srgb, var(--{ex.muscleGroup}) 16%, transparent)">{GROUP_NAMES[ex.muscleGroup]}</span>
							{#if ex.isUnilateral}<span class="badge">{ex.muscleGroup === 'gambe' ? t('Una gamba') : t('Un braccio')}</span>{/if}
							{#if (ex.barWeight ?? 0) > 0}<span class="badge">{t('Sbarra')} {nf(toUnit(ex.barWeight))} {unit()}</span>{/if}
							{#if (ex.barWeight ?? 0) > 0 && ex.weightDisplay === 'relative'}<span class="badge">{t('Dischi')}</span>{/if}
							{#if ex.best?.isAuto}<span class="badge">{t('da allenamento')}</span>{/if}
						</div>
						{#if ex.best}
							<div class="big num" style="margin-top:6px">{W(ex.best.weight, false)}<span class="mid muted"> {unit()} × {ex.best.reps}</span></div>
							<div class="small muted">{dateLong(ex.best.date)}
								{#if deltaOf(ex) !== null}<span class="badge pos" style="margin-left:6px">{delta(deltaOf(ex)!)}</span>{/if}
							</div>
							{#if ex.best.reps > 1}<div class="small muted">≈ {W(oneRepMax(ex.best.weight, ex.best.reps))} {t('di 1RM stimato')}</div>{/if}
						{:else}
							<div class="mid" style="margin-top:6px">{t('Nessun massimale')}</div>
						{/if}
					</div>

					{#if ex.figure.muscles.length}
						<button
							class="muscle-btn"
							onclick={() => (showingMuscleDetail = true)}
							aria-label={t('Mappa muscolare')}
						>
							<FigThumb muscles={ex.figure.muscles} view={ex.figure.view} group={ex.muscleGroup} size={68} />
							<span class="muscle-sub-label" style="color:var(--{ex.muscleGroup})">
								{t('Muscoli')} ↗
							</span>
						</button>
					{/if}
				</div>
				<button class="btn primary block" style="margin-top:14px" onclick={() => (updating = true)}>{t('Aggiorna massimale')}</button>
			</div>
		</div>

		{#if catalog && F.friends?.amici.length && board && board.length > 1}
			<div class="card">
				<div class="sec-title" style="margin:0 0 6px">{t('Classifica amici')}</div>
				<div class="stack" style="gap:6px">
					{#each board as r, i (r.id)}
						<button class="row between" style="text-align:left;color:inherit;padding:2px 0" onclick={() => !r.tu && go(`amici/${r.id}`)}>
							<span class="row" style="gap:10px"><b class="num" style="width:20px;color:{i === 0 ? 'var(--accent)' : 'var(--text-3)'}">{i + 1}</b><span style="font-weight:{r.tu ? 800 : 600}">{r.tu ? t('Tu') : '@' + r.username}</span></span>
							<span class="num"><b>{W(r.oneRm)}</b> <span class="small muted">{t('1RM')}</span></span>
						</button>
					{/each}
				</div>
				<p class="small faint" style="margin-top:8px">{t('1RM stimato dalle serie migliori.')}</p>
			</div>
		{:else if !catalog}
			<div class="card"><p class="small muted">{t('Per la classifica con gli amici collega questo macchinario al catalogo (Modifica → Catalogo).')}</p></div>
		{/if}

		{#if next}
			<div class="card">
				<div class="sec-title" style="margin:0 0 6px">{t('Prossima volta')}</div>
				<div class="row between"><b class="num mid">{nf(next.weight)} {unit()} × {next.reps}</b><Icon name="repeat" /></div>
				<p class="small muted">{t('Doppia progressione: sale di un passo quando tutte le serie arrivano in cima al range.')}</p>
			</div>
		{/if}

		{#if warm.length}
			<div class="card">
				<div class="sec-title" style="margin:0 0 6px">{t('Riscaldamento')}</div>
				<div class="row between">
					{#each warm as s}
						<div style="text-align:center"><div class="num" style="font-weight:800;font-size:20px">{nf(s.weight)}</div><div class="small muted">{unit()} × {s.reps}</div><div class="tiny faint">{Math.round(s.percent * 100)}%</div></div>
					{/each}
				</div>
				<p class="small faint" style="margin-top:8px">{t('Pesi sulla griglia del macchinario, a passi di {0} {1}.', nf(stepOf(ex, m)), unit())}</p>
			</div>
		{/if}

		</div>

		<div class="blk">
		{#if ex.records.length >= 2}
			<div class="card lead">
				<div class="row between" style="margin-bottom:10px">
					<div class="sec-title" style="margin:0">{t('Andamento')}</div>
					{#if availableRanges.length > 1}
						<div class="seg" style="width:auto">
							{#if availableRanges.includes('1m')}<button class:on={range === '1m'} onclick={() => (range = '1m')}>{t('1 mese')}</button>{/if}
							{#if availableRanges.includes('3m')}<button class:on={range === '3m'} onclick={() => (range = '3m')}>{t('3 mesi')}</button>{/if}
							{#if availableRanges.includes('1y')}<button class:on={range === '1y'} onclick={() => (range = '1y')}>{t('1 anno')}</button>{/if}
							<button class:on={range === 'all'} onclick={() => (range = 'all')}>{t('Tutto')}</button>
						</div>
					{/if}
				</div>
				{#if points.length >= 2}
					<LineChart {points} {domain} />
					<div class="row small muted" style="gap:14px"><span><span style="color:var(--accent)">●</span> {t('Registrato')}</span><span><span style="color:var(--accent);opacity:.5">●</span> {t('Da allenamento')}</span></div>
				{:else}
					<div class="small muted center" style="padding:20px 0">{t('Dati non sufficienti per questo intervallo')}</div>
				{/if}
			</div>
		{/if}

		<div class="sec-title">{t('Storico dei massimali')}</div>
		{#if ex.records.length}
			<MonthGroups items={ex.records} dateOf={(r) => r.date} count={(n) => tn(n, '1 massimale', '{0} massimali')}>
				{#snippet row(r)}
				<div class="item">
					<div class="grow"><div class="num" style="font-weight:700">{setLine(r.weight, r.reps)}</div><div class="small muted">{dateLong(r.date)} · {r.isAuto ? t('Da allenamento') : t('Registrato')}</div></div>
					{#if r.reps > 1}<span class="small muted num">≈ {W(oneRepMax(r.weight, r.reps))}</span>{/if}
					<button class="icon-btn" style="width:34px;height:34px" onclick={() => deleteRecord(r)} aria-label={t('Elimina')}><Icon name="trash" size={16} /></button>
				</div>
				{/snippet}
			</MonthGroups>
		{:else}<div class="card muted">{t('Ancora nessun massimale.')}</div>{/if}

		{#if ex.sets.length}
			<div class="sec-title">{t('Serie')}</div>
			<MonthGroups items={ex.sets} dateOf={(s) => s.createdAt} count={(n) => tn(n, '1 serie', '{0} serie')}>
				{#snippet row(s)}
				{@const isRel = ex.weightDisplay === 'relative' && (ex.barWeight ?? 0) > 0}
				<div class="item">
					<div class="grow">
						<div class="num" style="font-weight:700">
							{#if isRel}
								{setLine(Math.max(0, s.weight - ex.barWeight), s.reps)}
								<span class="tiny muted" style="font-weight:500">({t('tot.')} {W(s.weight)})</span>
							{:else}
								{setLine(s.weight, s.reps)}
							{/if}
						</div>
						<div class="small muted">{relDay(s.createdAt)}</div>
					</div>
					{#if s.isPR}<span class="badge pos">PR</span>{/if}
					{#if s.isWarmup}<span class="badge">{t('risc.')}</span>{/if}
				</div>
				{/snippet}
			</MonthGroups>
		{/if}

		{#if ex.notes}<div class="sec-title">{t('Note')}</div><div class="card muted" style="white-space:pre-wrap">{ex.notes}</div>{/if}
		</div>
	{/if}
</main>

{#if ex && editing}<ExerciseEditor id={ex.uuid} onclose={() => (editing = false)} ondeleted={() => back('macchinari')} />{/if}
{#if ex && updating}<EditMaxSheet {ex} onclose={() => (updating = false)} />{/if}
{#if ex && showingMuscleDetail}<MuscleDetailSheet {ex} onclose={() => (showingMuscleDetail = false)} />{/if}

<style>
	.card-content-wrap { position: relative; z-index: 1; }
	.muscle-btn {
		background: none;
		border: 0;
		padding: 0;
		cursor: pointer;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 5px;
		flex-shrink: 0;
	}
	.muscle-sub-label {
		font-size: 11px;
		font-weight: 700;
		padding: 2px 7px;
		border-radius: 999px;
		background: rgba(255, 255, 255, 0.08);
		letter-spacing: .02em;
		display: inline-flex;
		align-items: center;
	}
	/* il primo riquadro della colonna a destra segue l'ultimo della sinistra: sul telefono la distanza è quella fra due card */
	.lead { margin-top: 12px; }
	@media (min-width: 900px) { .lead { margin-top: 0; } }
</style>
