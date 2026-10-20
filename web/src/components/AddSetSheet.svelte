<script lang="ts">
	import { beats, bestOf, oneRepMax, plateMode, round2, toDisplay } from '@common/domain.ts'
	import { addSet, deleteSet, patchSet } from '../lib/actions.ts'
	import { stepOf, suggestionFor, warmupFor } from '../lib/derive.ts'
	import { W, fromUnit, relDay, setLine, toUnit, unit } from '../lib/fmt.ts'
	import { locale, t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { play } from '../lib/sound.ts'
	import type { Ex, SetItem, Wk } from '../lib/types.ts'
	import { suggestedGlyph } from '@common/glyph.ts'
	import Icon from './Icon.svelte'
	import PlateBar from './PlateBar.svelte'
	import Sheet from './Sheet.svelte'
	import Stepper from './Stepper.svelte'

	let { workout, ex, set = null, onclose, onadded }: {
		workout: Wk; ex: Ex; set?: SetItem | null; onclose: () => void; onadded?: (r: { isPR: boolean; isWarmup: boolean; uuid?: string }) => void
	} = $props()
	const m = getModel()
	const step = stepOf(ex, m)
	const nf = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)

	// partenza: la serie stessa; altrimenti l'ultima di questo esercizio nell'allenamento, l'ultima di sempre,
	// il massimale (almeno 5 rip), quattro passi
	function start(): { w: number; r: number } {
		if (set) return { w: toUnit(set.weight), r: set.reps }
		const inWorkout = [...workout.sets].reverse().find((s) => s.exercise === ex.uuid && !s.isWarmup)
		const prev = inWorkout ?? ex.sets.find((s) => !s.isWarmup) ?? [...workout.sets].reverse().find((s) => s.exercise === ex.uuid) ?? ex.sets[0]
		if (prev) return { w: toUnit(prev.weight), r: prev.reps }
		if (ex.records[0]) return { w: toUnit(ex.records[0].weight), r: Math.max(5, ex.records[0].reps) }
		return { w: round2(step * 4), r: 8 }
	}
	const init = start()
	let weight = $state(init.w)
	let reps = $state(init.r)
	let warm = $state(set?.isWarmup ?? false)
	let showRecent = $state(false)
	const prevSets = $derived((set ? ex.sets.filter((s) => s.uuid !== set.uuid) : ex.sets).filter((s) => !s.isWarmup))

	const kg = $derived(fromUnit(weight))
	const reference = $derived(bestOf(set ? ex.records.filter((r) => r.uuid !== set.uuid) : ex.records))
	const isRecord = $derived(!warm && beats(kg, reps, reference))
	const next = $derived(set ? null : suggestionFor(ex, m))
	const warmSteps = $derived(warmupFor(ex, m))
	// l'attrezzo: quello del catalogo (glyphName lo usa già), oppure l'icona scelta; senza icona lo si deduce dal nome
	const mode = $derived(m.settings.plateCalculatorEnabled ? plateMode(ex.glyphName ?? suggestedGlyph(ex.name, ex.muscleGroup), ex.name) : null)
	const plates = $derived(m.settings.availablePlates.map((p) => round2(toDisplay(p, m.settings.unit))))

	function save() {
		if (!(weight > 0) || reps < 1) return
		if (set) { const isPR = patchSet(set.uuid, { weight: kg, reps, isWarmup: warm }); onadded?.({ isPR, isWarmup: warm, uuid: set.uuid }) }
		else { const r = addSet(workout.uuid, ex.uuid, kg, reps, warm); onadded?.({ isPR: r.isPR, isWarmup: warm, uuid: r.uuid }) }
		onclose()
	}
	function remove() {
		if (!set) return
		deleteSet(set.uuid)
		play('remove')
		onclose()
	}
</script>

<Sheet title={ex.name} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!(weight > 0)}>{set ? t('Salva') : t('Aggiungi')}</button>{/snippet}
	<div class="stack">
		<div class="card">
			{#if next && !warm}
				<button class="chip" style="margin-bottom:10px" onclick={() => { weight = next.weight; reps = next.reps }}>{t('Consigliato')}: {nf(next.weight)} {unit()} × {next.reps}</button>
			{/if}
			<div class="sec-title" style="margin-top:0">{t('Carico')}</div>
			<Stepper bind:value={weight} {step} min={0} max={5000} unit={unit()} />
			<div class="sec-title">{t('Ripetizioni')}</div>
			<Stepper bind:value={reps} step={1} min={1} max={50} />
			<div class="chips" style="margin-top:8px">
				{#each [1, 3, 5, 8, 10, 12] as q}<button class="chip" class:on={reps === q} onclick={() => (reps = q)}>{q}</button>{/each}
			</div>
		</div>

		{#if mode && weight > 0}<PlateBar total={weight} bar={mode.bar ? toUnit(m.settings.barWeight) : 0} {plates} unit={unit()} />{/if}
		{#if !warm}
			<div class="card" style={isRecord ? 'border-color:rgba(var(--accent-rgb),.5)' : ''}>
				<div class="row between">
					<span class="tiny muted" style="font-weight:800;letter-spacing:.08em">{isRecord ? t('QUESTA SERIE') : t('MASSIMALE DA BATTERE')}</span>
					{#if isRecord}<span class="badge accent">{t('NUOVO RECORD')}</span>{/if}
				</div>
				{#if isRecord}
					<div class="mid num">{setLine(kg, reps)}</div>
					{#if reps > 1}<div class="small muted">≈ {W(oneRepMax(kg, reps))} {t('di 1RM stimato')}</div>{/if}
					{#if reference}<div class="small muted">{t('Precedente')}: {setLine(reference.weight, reference.reps)}</div>{/if}
				{:else if reference}
					<div class="mid num">{setLine(reference.weight, reference.reps)}</div>
				{:else}
					<div class="muted">{t('Nessun massimale: la prima serie lo stabilisce.')}</div>
				{/if}
			</div>
		{/if}

		<label class="row between card"><span><b>{t('Serie di riscaldamento')}</b><br /><span class="small muted">{t('Esclusa dal volume e dai record')}</span></span><input class="toggle" type="checkbox" bind:checked={warm} /></label>
		{#if warm && warmSteps.length}
			<div class="chips">
				{#each warmSteps as s}<button class="chip" onclick={() => { weight = s.weight; reps = s.reps }}>{Math.round(s.percent * 100)}% · {nf(s.weight)} × {s.reps}</button>{/each}
			</div>
		{/if}


		<div class="card">
			<button class="row between" style="background:none;border:0;width:100%;text-align:left;padding:0;cursor:pointer" onclick={() => (showRecent = !showRecent)}>
				<span class="row" style="gap:8px">
					<Icon name="timer" size={18} />
					<b style="font-size:14px">{t('Ultime serie su questo macchinario')}</b>
				</span>
				<div class="row" style="gap:6px">
					{#if prevSets.length}<span class="badge">{prevSets.length}</span>{/if}
					<Icon name={showRecent ? 'up' : 'down'} size={16} />
				</div>
			</button>
			{#if showRecent}
				<div class="stack" style="margin-top:12px;gap:8px">
					{#if !prevSets.length}
						<div class="small muted">{t('Nessuna serie precedente')}</div>
					{:else}
						<div class="tiny muted">{t('Tocca una serie per usarla nel modulo.')}</div>
						<div class="list">
							{#each prevSets.slice(0, 15) as s (s.uuid)}
								<button class="item" style="background:none;border:0;width:100%;text-align:left;cursor:pointer" onclick={() => { weight = toUnit(s.weight); reps = s.reps; warm = false }}>
									<div class="grow num" style="font-weight:700">{setLine(s.weight, s.reps)}</div>
									<div class="small muted">{relDay(s.createdAt)}</div>
									{#if s.isPR}<span class="badge pos">PR</span>{/if}
									{#if s.reps > 1}<span class="small muted num">≈ {W(oneRepMax(s.weight, s.reps))}</span>{/if}
								</button>
							{/each}
						</div>
					{/if}
				</div>
			{/if}
		</div>

		{#if set}<button class="btn danger block" onclick={remove}>{t('Elimina serie')}</button>{/if}
	</div>
</Sheet>
