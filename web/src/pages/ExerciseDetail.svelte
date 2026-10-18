<script lang="ts">
	import { oneRepMax, toDisplay, round2 } from '@common/domain.ts'
	import { GROUP_NAMES, W, dateLong, dateShort, delta, relDay, setLine, unit } from '../lib/fmt.ts'
	import { deleteRecord } from '../lib/actions.ts'
	import { deltaOf, stepOf, suggestionFor, warmupFor } from '../lib/derive.ts'
	import { locale, t } from '../lib/i18n.ts'
	import { back, go } from '../lib/router.svelte.ts'
	import { F, getBoard, loadFriends, type BoardRow } from '../lib/social.svelte.ts'
	import { onMount } from 'svelte'
	import { getModel } from '../lib/store.svelte.ts'
	import EditMaxSheet from '../components/EditMaxSheet.svelte'
	import ExerciseEditor from '../components/ExerciseEditor.svelte'
	import Icon from '../components/Icon.svelte'
	import LineChart from '../components/LineChart.svelte'
	import Thumb from '../components/Thumb.svelte'
	import MuscleFigure from '../components/MuscleFigure.svelte'

	let { id }: { id: string } = $props()
	const m = $derived(getModel())
	const ex = $derived(m.exById.get(id))
	let range = $state<'3m' | '1y' | 'all'>('3m')
	let editing = $state(false)
	let updating = $state(false)

	// la classifica con gli amici: solo per i macchinari del catalogo, e solo se hai degli amici
	let board = $state<BoardRow[] | null>(null)
	const catalog = $derived(ex?.catalog ?? '')
	onMount(() => { void loadFriends() })
	$effect(() => { const c = catalog; board = null; if (c) void getBoard(c).then((r) => { if (c === catalog) board = r }) })

	const warm = $derived(ex ? warmupFor(ex, m) : [])
	const next = $derived(ex ? suggestionFor(ex, m) : null)
	const nf = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)

	const chartRecords = $derived.by(() => {
		if (!ex) return []
		const since = range === 'all' ? 0 : Date.now() - (range === '3m' ? 90 : 365) * 86_400_000
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

<main class="screen no-tabs">
	{#if !ex}
		<div class="bar"><button class="icon-btn" onclick={() => back('macchinari')} aria-label={t('Indietro')}><Icon name="left" /></button></div>
		<div class="empty">{t('Macchinario non trovato.')}</div>
	{:else}
		<div class="bar">
			<button class="icon-btn" onclick={() => back('macchinari')} aria-label={t('Indietro')}><Icon name="left" /></button>
			<h1 class="small ellipsis">{ex.name}</h1>
			<button class="icon-btn" onclick={() => (editing = true)} aria-label={t('Modifica')}><Icon name="edit" /></button>
		</div>

		<div class="card" style="--tint:var(--{ex.muscleGroup})">
			{#if ex.figure.muscles.length}<div style="width:220px;margin:0 auto"><MuscleFigure muscles={ex.figure.muscles} view={ex.figure.view} /></div>{/if}
			<div class="row" style="align-items:flex-start">
				<Thumb {ex} large />
				<div class="grow">
					<div class="row wrap" style="gap:6px">
						<span class="badge" style="color:var(--{ex.muscleGroup});background:color-mix(in srgb, var(--{ex.muscleGroup}) 16%, transparent)">{GROUP_NAMES[ex.muscleGroup]}</span>
						{#if ex.isUnilateral}<span class="badge">{ex.muscleGroup === 'gambe' ? t('Una gamba') : t('Un braccio')}</span>{/if}
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
			</div>
			<button class="btn primary block" style="margin-top:14px" onclick={() => (updating = true)}>{t('Aggiorna massimale')}</button>
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

		{#if points.length >= 2}
			<div class="card">
				<div class="row between" style="margin-bottom:10px">
					<div class="sec-title" style="margin:0">{t('Andamento')}</div>
					<div class="seg" style="width:auto"><button class:on={range === '3m'} onclick={() => (range = '3m')}>{t('3 mesi')}</button><button class:on={range === '1y'} onclick={() => (range = '1y')}>{t('1 anno')}</button><button class:on={range === 'all'} onclick={() => (range = 'all')}>{t('Tutto')}</button></div>
				</div>
				<LineChart {points} {domain} />
				<div class="row small muted" style="gap:14px"><span><span style="color:var(--accent)">●</span> {t('Registrato')}</span><span><span style="color:var(--accent);opacity:.5">●</span> {t('Da allenamento')}</span></div>
			</div>
		{/if}

		<div class="sec-title">{t('Storico dei massimali')}</div>
		{#if ex.records.length}
			<div class="card list">
				{#each ex.records.slice(0, 12) as r (r.uuid)}
					<div class="item">
						<div class="grow"><div class="num" style="font-weight:700">{setLine(r.weight, r.reps)}</div><div class="small muted">{dateLong(r.date)} · {r.isAuto ? t('Da allenamento') : t('Registrato')}</div></div>
						{#if r.reps > 1}<span class="small muted num">≈ {W(oneRepMax(r.weight, r.reps))}</span>{/if}
						<button class="icon-btn" style="width:34px;height:34px" onclick={() => deleteRecord(r)} aria-label={t('Elimina')}><Icon name="trash" size={16} /></button>
					</div>
				{/each}
			</div>
		{:else}<div class="card muted">{t('Ancora nessun massimale.')}</div>{/if}

		{#if ex.sets.length}
			<div class="sec-title">{t('Ultime serie')}</div>
			<div class="card list">
				{#each ex.sets.slice(0, 8) as s (s.uuid)}
					<div class="item">
						<div class="grow"><div class="num" style="font-weight:700">{setLine(s.weight, s.reps)}</div><div class="small muted">{relDay(s.createdAt)}</div></div>
						{#if s.isPR}<span class="badge pos">PR</span>{/if}
						{#if s.isWarmup}<span class="badge">{t('risc.')}</span>{/if}
					</div>
				{/each}
			</div>
		{/if}

		{#if ex.notes}<div class="sec-title">{t('Note')}</div><div class="card muted" style="white-space:pre-wrap">{ex.notes}</div>{/if}
	{/if}
</main>

{#if ex && editing}<ExerciseEditor id={ex.uuid} onclose={() => (editing = false)} ondeleted={() => back('macchinari')} />{/if}
{#if ex && updating}<EditMaxSheet {ex} onclose={() => (updating = false)} />{/if}
