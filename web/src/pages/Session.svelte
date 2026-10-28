<script lang="ts">
	import { onMount } from 'svelte'
	import type { MuscleGroup } from '@common/docs.ts'
	import { oneRepMax } from '@common/domain.ts'
	import { addSet, addToPlan, deleteWorkout, endWorkout, itemsFromWorkout, moveInPlan, patchWorkout, removeFromPlan, replaceInPlan, templateChanged, todayItems, updateTemplateFromWorkout } from '../lib/actions.ts'
	import { GROUP_NAMES, V, W, clock, dateShort, dateTime, duration, notebookLine, setLine, targetLine } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { back, go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { R, shiftRest, startRest, stopRest } from '../lib/timer.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import { confetti, rowIn, rowOut } from '../lib/fx.ts'
	import { play } from '../lib/sound.ts'
	import type { Ex, SetItem, Wk } from '../lib/types.ts'
	import AddSetSheet from '../components/AddSetSheet.svelte'
	import ExercisePicker from '../components/ExercisePicker.svelte'
	import FocusPicker from '../components/FocusPicker.svelte'
	import Icon from '../components/Icon.svelte'
	import Sheet from '../components/Sheet.svelte'
	import TemplateEditor from '../components/TemplateEditor.svelte'
	import Thumb from '../components/Thumb.svelte'
	import FigThumb from '../components/FigThumb.svelte'
	import CardBackground from '../components/CardBackground.svelte'
	import MuscleDetailSheet from '../components/MuscleDetailSheet.svelte'

	let { id }: { id: string } = $props()
	const m = $derived(getModel())
	const wk = $derived(m.wkById.get(id))

	const store = (k: string) => `peak.${k}.${id}`
	let current = $state<string | null>(sessionStorage.getItem(store('current')))
	$effect(() => { current ? sessionStorage.setItem(store('current'), current) : sessionStorage.removeItem(store('current')) })

	let now = $state(Date.now())
	onMount(() => { const i = setInterval(() => (now = Date.now()), 1000); return () => { clearInterval(i); clearTimeout(freshTimer) } })

	let pickingEx = $state(false)
	let editFocus = $state(false)
	let menuFor = $state<Ex | null>(null)
	let muscleDetailFor = $state<Ex | null>(null)
	let replacing = $state<Ex | null>(null)
	let savingTemplate = $state(false)
	let adding = $state<{ ex: Ex; set: SetItem | null } | null>(null)
	let note = $state('')
	let noteFor = ''
	$effect(() => { if (wk && noteFor !== wk.uuid) { note = wk.note; noteFor = wk.uuid } })

	const focus = $derived(new Set((wk?.focus ?? '').split(',').filter(Boolean) as MuscleGroup[]))
	// la lista di oggi: il piano (scheda o esercizi aggiunti) e, in coda, gli esercizi con serie che nel piano non ci sono
	const items = $derived(wk ? todayItems(wk) : [])
	const orphanSets = $derived(wk?.sets.filter((s) => !s.exercise) ?? [])
	const seconds = $derived(wk ? ((wk.endedMs ?? now) - wk.dateMs) / 1000 : 0)

	// la serie appena salvata: solo lei ha la festa del record (lampo verde e badge PR che fa «pop»), per un secondo e mezzo
	let fresh = $state('')
	let freshTimer: ReturnType<typeof setTimeout> | undefined
	function added(ex: Ex, r: { isPR: boolean; isWarmup: boolean; uuid?: string }) {
		current = ex.uuid
		fresh = r.uuid ?? ''
		clearTimeout(freshTimer)
		freshTimer = setTimeout(() => (fresh = ''), 1500)
		if (r.isPR) {
			toast(t('Nuovo massimale!'), 2600, 'trophy')
			confetti({ x: innerWidth / 2, y: 120 })
			play('record')
		} else play('set')
		if (wk?.isActive && m.settings.restTimerEnabled) startRest(ex.restSeconds || m.settings.restSeconds)
	}

	function previousWorkingSets(exercise: Ex, currentWk: Wk): SetItem[] {
		const otherWorking = exercise.sets.filter((s) => s.workout !== currentWk.uuid && !s.isWarmup)
		if (!otherWorking.length) return []
		const prevWkId = otherWorking[0].workout
		return exercise.sets
			.filter((s) => s.workout === prevWkId && !s.isWarmup)
			.sort((a, b) => a.order - b.order || a.createdAt - b.createdAt)
	}

	// gli ultimi 3 allenamenti (giorni) con questo esercizio, escluso quello in corso, dal più recente; riscaldamento fuori
	function previousDays(exercise: Ex, currentWk: Wk): { dateMs: number; line: string }[] {
		const rel = exercise.weightDisplay === 'relative' && !exercise.perSide && (exercise.barWeight ?? 0) > 0
		const ids = [...new Set(exercise.sets.filter((s) => s.workout !== currentWk.uuid && !s.isWarmup).map((s) => s.workout))]
		return ids
			.map((id) => m.wkById.get(id))
			.filter((w) => !!w)
			.sort((a, b) => b.dateMs - a.dateMs)
			.slice(0, 3)
			.map((w) => ({
				dateMs: w.dateMs,
				line: notebookLine(
					exercise.sets
						.filter((s) => s.workout === w.uuid && !s.isWarmup)
						.sort((a, b) => a.order - b.order || a.createdAt - b.createdAt)
						.map((s) => ({ weight: rel ? Math.max(0, s.weight - exercise.barWeight) : s.weight, reps: s.reps, singleArm: s.singleArm })),
					exercise.muscleGroup,
				),
			}))
	}
	// pagina visibile del blocco «Scorso» di ogni esercizio, per i puntini
	let prevPage = $state<Record<string, number>>({})

	function formatSetLine(weightKg: number, reps: number, ex: Ex, singleArm: boolean): { main: string; sub?: string } {
		if (ex.weightDisplay === 'relative' && !ex.perSide && (ex.barWeight ?? 0) > 0) {
			const relKg = Math.max(0, weightKg - ex.barWeight)
			return {
				main: setLine(relKg, reps, singleArm, ex.muscleGroup),
				sub: `${t('tot.')} ${W(weightKg)}`,
			}
		}
		return { main: setLine(weightKg, reps, singleArm, ex.muscleGroup) }
	}

	function repeat(ex: Ex) {
		const last = [...(wk?.sets ?? [])].reverse().find((s) => s.exercise === ex.uuid)
		if (!last || !wk) return
		const r = addSet(wk.uuid, ex.uuid, last.weight, last.reps, last.isWarmup, last.singleArm)
		added(ex, { isPR: r.isPR, isWarmup: last.isWarmup, uuid: r.uuid })
	}

	function finish() {
		if (!wk || !confirm(t('Terminare l\'allenamento?'))) return
		const tpl = templateChanged(wk)
		endWorkout(wk.uuid)
		stopRest()
		if (tpl && confirm(t('Aggiornare il giorno «{0}» con le modifiche di oggi?', tpl.name))) updateTemplateFromWorkout(tpl, wk)
		go(`allenamento/${wk.uuid}/riepilogo`, true)
	}
	function remove() {
		if (!wk || !confirm(t('Eliminare questo allenamento e le sue serie?'))) return
		stopRest()
		deleteWorkout(wk.uuid)
		go('allenamento', true)
	}
</script>

<main class="screen no-tabs">
	{#if !wk}
		<div class="bar"><button class="icon-btn" onclick={() => back('allenamenti')} aria-label={t('Indietro')}><Icon name="left" /></button></div>
		<div class="empty">{t('Allenamento non trovato.')}</div>
	{:else}
		<div class="bar">
			<button class="icon-btn" onclick={() => back('allenamenti')} aria-label={t('Indietro')}><Icon name="left" /></button>
			<h1 class="small ellipsis">{wk.name.trim() || dateTime(wk.dateMs)}</h1>
			<button class="icon-btn" onclick={remove} aria-label={t('Elimina')}><Icon name="trash" /></button>
		</div>

		{#if R.endsAt && wk.isActive}
			<div class="card rest" style="border-color:rgba(var(--accent-rgb),.5);background:rgba(18,21,31,.92)">
				<div class="row">
					<Icon name="timer" size={26} />
					<div class="grow"><div class="tiny muted" style="font-weight:800;letter-spacing:.08em">{t('RECUPERO')}</div><div class="mid num">{clock(R.left)}</div></div>
					<button class="btn small" onclick={() => shiftRest(-15)}>−15</button>
					<button class="btn small" onclick={() => shiftRest(15)}>+15</button>
					<button class="btn small" onclick={stopRest}>{t('Salta')}</button>
				</div>
			</div>
		{/if}

		<div class="card">
			<div class="row between">
				<span class="tiny" style="font-weight:800;letter-spacing:.08em;color:{wk.isActive ? 'var(--accent)' : 'var(--text-2)'}">{wk.isActive ? t('IN CORSO') : t('CONCLUSO')}</span>
				<span class="small muted num">{dateTime(wk.dateMs)} · {duration(seconds)}</span>
			</div>
			<div class="row between" style="margin-top:10px;text-align:center">
				<div><div class="mid num">{wk.workingSets}</div><div class="tiny muted">{t('serie')}</div></div>
				<div><div class="mid num">{V(wk.volume)}</div><div class="tiny muted">{t('volume')}</div></div>
				<div><div class="mid num">{wk.prCount}</div><div class="tiny muted">{t('record')}</div></div>
			</div>
			{#if focus.size || wk.isActive}
				<div class="row wrap" style="margin-top:10px;gap:6px">
					{#each [...focus] as g}<span class="badge" style="color:var(--{g});background:color-mix(in srgb, var(--{g}) 16%, transparent)">{GROUP_NAMES[g]}</span>{/each}
					{#if wk.isActive}<button class="link small" onclick={() => (editFocus = true)}>{t('Modifica')}</button>{/if}
				</div>
			{/if}
		</div>

		{#each items as it, idx (it.exercise)}
			{@const ex = m.exById.get(it.exercise)!}
			{@const sets = wk.sets.filter((s) => s.exercise === ex.uuid)}
			{@const goal = targetLine(it, sets.filter((s) => !s.isWarmup).length, null)}
			{@const prevSets = previousWorkingSets(ex, wk).slice(0, 3)}
			{@const prevDays = previousDays(ex, wk)}
			<div class="card session-ex-card" style={current === ex.uuid && wk.isActive ? 'border-color:rgba(var(--accent-rgb),.45)' : ''}>
				<CardBackground {ex} />

				<div class="card-content-wrap">
					<!-- Sezione superiore: Sinistra (Titolo grande + goal + ultime 3 serie) / Destra (menu + figura muscoli 68px e bottone Muscoli ↗ sotto) -->
					<div class="row between" style="align-items:flex-start;gap:12px">
						<!-- Colonna sinistra -->
						<div class="stack grow" style="gap:6px;min-width:0">
							<button class="stack" style="background:none;border:0;text-align:left;padding:0;gap:2px" onclick={() => (current = ex.uuid)}>
								<div class="row" style="align-items:center;gap:6px">
									<b class="ellipsis" style="display:block;font-size:22px;font-weight:800;letter-spacing:-.02em;line-height:1.2">{ex.name}</b>
								</div>
								{#if current === ex.uuid && wk.isActive}
									<span class="tiny" style="color:var(--{ex.muscleGroup || 'accent'});font-weight:800;letter-spacing:.08em">{t('IN CORSO')}</span>
								{/if}
							</button>

							{#if goal}
								<div class="small muted num" style="font-weight:600">{goal}</div>
							{/if}

							<!-- Scorso: due pagine da scorrere, le ultime serie (fino a 3) e gli ultimi 3 allenamenti in riga da taccuino -->
							{#if prevSets.length}
								<div class="stack" style="gap:3px;margin-top:2px">
									<div class="tiny muted" style="font-weight:700">{t('Scorso:')}</div>
									<div class="prev-pager" onscroll={(e) => (prevPage[ex.uuid] = Math.round(e.currentTarget.scrollLeft / e.currentTarget.clientWidth))}>
										<div class="stack" style="gap:2px">
											{#each prevSets as s, i}
												{@const fmt = formatSetLine(s.weight, s.reps, ex, s.singleArm)}
												<div class="row small muted num" style="gap:6px">
													<span style="width:16px;opacity:0.6">{i + 1}.</span>
													<span>{fmt.main}{#if fmt.sub} <span class="faint tiny">({fmt.sub})</span>{/if}</span>
												</div>
											{/each}
										</div>
										<div class="stack small muted num" style="gap:2px">
											{#each prevDays as d}
												<div><span style="opacity:0.6">{dateShort(d.dateMs)}</span>&nbsp; {d.line}</div>
											{/each}
										</div>
									</div>
									<div class="prev-dots" aria-hidden="true">
										<i class:on={!prevPage[ex.uuid]}></i><i class:on={prevPage[ex.uuid] === 1}></i>
									</div>
								</div>
							{:else}
								<div class="small muted" style="margin-top:2px">{t('Nessun allenamento precedente')}</div>
							{/if}
						</div>

						<!-- Colonna destra: Menu in alto, poi figura muscolo 68px con "Muscoli ↗" sotto -->
						<div class="stack center" style="flex-shrink:0;gap:8px;align-items:flex-end">
							{#if wk.isActive}
								<button class="icon-btn" onclick={() => (menuFor = ex)} aria-label={t('Altro')}><Icon name="more" /></button>
							{/if}

							{#if ex.figure.muscles.length}
								<button
									class="muscle-btn"
									onclick={() => (muscleDetailFor = ex)}
									aria-label={t('Mappa muscolare')}
								>
									<FigThumb muscles={ex.figure.muscles} view={ex.figure.view} group={ex.muscleGroup} size={68} />
									<span class="muscle-sub-label" style="color:var(--{ex.muscleGroup})">
										{t('Muscoli')} ↗
									</span>
								</button>
							{/if}
						</div>
					</div>

					<!-- sempre presente (vuota non occupa nulla): così anche la prima serie di un esercizio entra con la sua transizione -->
					<div class="list" style:margin-top={sets.length ? '12px' : '0'}>
						{#each sets as s, i (s.uuid)}
							{@const fmt = formatSetLine(s.weight, s.reps, ex, s.singleArm)}
							<button class="item" class:flash={s.isPR && s.uuid === fresh} onclick={() => (adding = { ex, set: s })} in:rowIn out:rowOut>
								<span class="muted num" style="width:20px">{i + 1}</span>
								<div class="grow num" style="font-weight:700">
									{fmt.main}{#if fmt.sub} <span class="tiny muted" style="font-weight:500">({fmt.sub})</span>{/if}
								</div>
								{#if s.isWarmup}<span class="badge">{t('risc.')}</span>{/if}
								{#if s.isPR}<span class="badge pos">PR</span>{/if}
								{#if s.reps > 1 && !s.isWarmup}<span class="small muted num">≈ {W(oneRepMax(s.weight, s.reps))}</span>{/if}
							</button>
						{/each}
					</div>

					<div class="row" style="margin-top:12px">
						<button class="btn small grow" onclick={() => (adding = { ex, set: null })} data-tour={idx === 0 ? 'add-set' : undefined}><Icon name="plus" size={18} />{t('Serie')}</button>
						{#if sets.length}<button class="btn small" onclick={() => repeat(ex)} aria-label={t('Ripeti l\'ultima serie')}><Icon name="repeat" size={18} /></button>{/if}
					</div>
				</div>
			</div>
		{/each}

		{#if orphanSets.length}
			<div class="card">
				<b class="muted">{t('Esercizio rimosso')}</b>
				<div class="list" style="margin-top:8px">{#each orphanSets as s (s.uuid)}<div class="item num"><div class="grow">{setLine(s.weight, s.reps, s.singleArm)}</div>{#if s.isWarmup}<span class="badge">{t('risc.')}</span>{/if}</div>{/each}</div>
			</div>
		{/if}

		<button class="btn block" style="margin-top:12px" onclick={() => (pickingEx = true)}><Icon name="plus" size={20} />{t('Aggiungi esercizio')}</button>

		{#if items.length}<button class="btn block" style="margin-top:12px" onclick={() => (savingTemplate = true)}><Icon name="list" size={20} />{t('Salva come giorno')}</button>{/if}

		<label class="field" style="margin-top:16px"><span>{t('Note')}</span><textarea class="textarea" bind:value={note} onchange={() => patchWorkout(wk.uuid, { note })} maxlength="2000" placeholder={t('Com\'è andata?')}></textarea></label>

		{#if wk.isActive}<button class="btn primary block" style="margin-top:16px" onclick={finish}>{t('Termina allenamento')}</button>
		{:else}<button class="btn block" style="margin-top:16px" onclick={() => go(`allenamento/${wk.uuid}/riepilogo`)}>{t('Riepilogo')}</button>{/if}
	{/if}
</main>

{#if pickingEx && wk}<ExercisePicker {focus} onclose={() => (pickingEx = false)} onpick={(ex) => { addToPlan(wk, ex.uuid); current = ex.uuid; adding = { ex, set: null } }} />{/if}
{#if replacing && wk}<ExercisePicker {focus} onclose={() => (replacing = null)} onpick={(ex) => { if (items.some((i) => i.exercise === ex.uuid)) { toast(t('È già in lista.')); return } replaceInPlan(wk, replacing!.uuid, ex.uuid); current = ex.uuid }} />{/if}
{#if menuFor && wk}
	{@const ex = menuFor}
	{@const idx = items.findIndex((i) => i.exercise === ex.uuid)}
	{@const empty = !wk.sets.some((s) => s.exercise === ex.uuid)}
	<Sheet title={ex.name} onclose={() => (menuFor = null)}>
		<div class="stack">
			<button class="btn block" onclick={() => { const id = menuFor!.uuid; menuFor = null; go('macchinari/' + id) }}><Icon name="machine" size={18} />{t('Vedi macchinario')}</button>
			<div class="row">
				<button class="btn grow" disabled={idx <= 0} onclick={() => moveInPlan(wk, ex.uuid, -1)}><Icon name="up" size={18} />{t('Su')}</button>
				<button class="btn grow" disabled={idx < 0 || idx >= items.length - 1} onclick={() => moveInPlan(wk, ex.uuid, 1)}><Icon name="down" size={18} />{t('Giù')}</button>
			</div>
			{#if empty}
				<button class="btn block" onclick={() => { replacing = ex; menuFor = null }}><Icon name="repeat" size={18} />{t('Sostituisci')}</button>
				<button class="btn danger block" onclick={() => { removeFromPlan(wk, ex.uuid); menuFor = null }}><Icon name="trash" size={18} />{t('Togli dalla lista')}</button>
			{:else}<p class="small muted">{t('Ha già delle serie: per cambiarlo eliminale prima.')}</p>{/if}
		</div>
	</Sheet>
{/if}
{#if savingTemplate && wk}<TemplateEditor initial={{ name: wk.name, items: itemsFromWorkout(wk) }} onclose={() => (savingTemplate = false)} onsaved={() => toast(t('Giorno salvato.'))} />{/if}
{#if editFocus && wk}<FocusPicker workout={wk} onclose={() => (editFocus = false)} />{/if}
{#if adding && wk}<AddSetSheet workout={wk} ex={adding.ex} set={adding.set} onclose={() => (adding = null)} onadded={(r) => added(adding!.ex, r)} />{/if}
{#if muscleDetailFor}<MuscleDetailSheet ex={muscleDetailFor} onclose={() => (muscleDetailFor = null)} />{/if}

<style>
	.session-ex-card {
		position: relative;
		overflow: hidden;
	}
	.card-content-wrap {
		position: relative;
		z-index: 1;
	}
	/* due pagine affiancate che si agganciano allo scorrimento orizzontale; gli altri gesti restano alla pagina */
	.prev-pager {
		display: flex;
		overflow-x: auto;
		scroll-snap-type: x mandatory;
		scrollbar-width: none;
		overscroll-behavior-x: contain;
	}
	.prev-pager::-webkit-scrollbar { display: none; }
	.prev-pager > * { flex: 0 0 100%; min-width: 0; scroll-snap-align: start; }
	.prev-dots { display: flex; gap: 4px; }
	.prev-dots i { width: 5px; height: 5px; border-radius: 50%; background: currentColor; opacity: .25; }
	.prev-dots i.on { opacity: .7; }
	.muscle-btn {
		background: none;
		border: 0;
		padding: 0;
		cursor: pointer;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 5px;
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

	/* serie nuova che è un record: lampo verde sotto la riga (solo opacità, 600 ms) e il badge PR fa «pop» */
	.item.flash { position: relative; isolation: isolate; }
	.item.flash::before { content: ''; position: absolute; inset: 2px -8px; z-index: -1; border-radius: 10px; background: rgba(95, 217, 138, .22); pointer-events: none; animation: flash 600ms var(--ease-out); }
	.item.flash .badge.pos { animation: pop 450ms var(--ease-out) 120ms backwards; }
	@keyframes flash { 0% { opacity: 0; } 25% { opacity: 1; } 100% { opacity: 0; } }
</style>
