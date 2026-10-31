<script lang="ts">
	import { type ArmMemo, beats, bestOf, defaultBarWeightFor, oneRepMax, plateMode, round2, switchArm, toDisplay } from '@common/domain.ts'
	import { addSet, deleteSet, patchSet } from '../lib/actions.ts'
	import { stepOf, warmupFor } from '../lib/derive.ts'
	import { W, fromUnit, hasSides, relDay, setLine, toUnit, unit } from '../lib/fmt.ts'
	import { locale, t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { play } from '../lib/sound.ts'
	import type { Ex, SetItem, Wk } from '../lib/types.ts'
	import { suggestedGlyph } from '@common/glyph.ts'
	import Icon from './Icon.svelte'
	import SideSegment from './SideSegment.svelte'
	import PlateBar from './PlateBar.svelte'
	import Sheet from './Sheet.svelte'
	import Stepper from './Stepper.svelte'

	let { workout, ex, set = null, onclose, onadded }: {
		workout: Wk; ex: Ex; set?: SetItem | null; onclose: () => void; onadded?: (r: { isPR: boolean; isWarmup: boolean; uuid?: string }) => void
	} = $props()
	const m = getModel()
	const step = stepOf(ex, m)
	const nf = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)

	const barKg = $derived(ex.barWeight ?? defaultBarWeightFor(ex.glyphName ?? suggestedGlyph(ex.name, ex.muscleGroup), ex.name))
	const bar = $derived(toUnit(barKg))
	// tre modi di leggere il campo peso: totale, solo dischi (tolta la sbarra), un lato (totale = 2 x + sbarra); si salva sempre il totale
	const side = $derived(ex.perSide === true)
	const isRel = $derived(!side && ex.weightDisplay === 'relative' && bar > 0)
	/** totale (unità scelta) -> valore del campo */
	const fromTotal = (u: number): number => side ? Math.max(0, round2((u - bar) / 2)) : isRel ? Math.max(0, round2(u - bar)) : u
	/** valore del campo -> totale (unità scelta) */
	const toTotal = (v: number): number => side ? round2(v * 2 + bar) : isRel ? round2(v + bar) : v
	/** totale in kg -> kg mostrati nelle serie recenti */
	const listKg = (kg: number): number => side ? Math.max(0, (kg - barKg) / 2) : isRel ? Math.max(0, kg - barKg) : kg
	const toDisplayWeight = (absKg: number): number => fromTotal(toUnit(absKg))

	// partenza: la serie stessa; altrimenti l'ultima di questo esercizio nell'allenamento, l'ultima di sempre,
	// il massimale (almeno 5 rip), quattro passi
	function start(): { w: number; r: number } {
		if (set) return { w: toDisplayWeight(set.weight), r: set.reps }
		const inWorkout = [...workout.sets].reverse().find((s) => s.exercise === ex.uuid && !s.isWarmup)
		const prev = inWorkout ?? ex.sets.find((s) => !s.isWarmup) ?? [...workout.sets].reverse().find((s) => s.exercise === ex.uuid) ?? ex.sets[0]
		if (prev) return { w: toDisplayWeight(prev.weight), r: prev.reps }
		if (ex.records[0]) return { w: toDisplayWeight(ex.records[0].weight), r: Math.max(5, ex.records[0].reps) }
		const defWeight = round2(step * 4)
		return { w: fromTotal(defWeight), r: 8 }
	}
	const init = start()
	let weight = $state(init.w)
	let reps = $state(init.r)
	let warm = $state(set?.isWarmup ?? false)
	// a un braccio (o una gamba): la serie stessa; una serie nuova parte sempre a due
	let arm = $state(set ? set.singleArm : false)
	// il peso di prima del dimezzamento (totale, unità scelta): se non lo si tocca, tornando a due bracci si rimette identico
	let armMemo: ArmMemo | null = null
	let showRecent = $state(false)
	const prevSets = $derived((set ? ex.sets.filter((s) => s.uuid !== set.uuid) : ex.sets).filter((s) => !s.isWarmup))
	// le serie recenti per giorno di calendario (locale), gli ultimi 5 giorni; prevSets è già dal più recente
	const days = $derived.by(() => {
		const out: { key: string; at: number; sets: SetItem[] }[] = []
		for (const s of prevSets) {
			const d = new Date(s.createdAt), key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
			const g = out.find((x) => x.key === key)
			if (g) g.sets.push(s); else out.push({ key, at: s.createdAt, sets: [s] })
		}
		return out.slice(0, 5)
	})
	const bestSet = (sets: SetItem[]) => bestOf(sets.map((s) => ({ weight: s.weight, reps: s.reps, date: s.createdAt, singleArm: s.singleArm })))!
	// l'attrezzo: quello del catalogo (glyphName lo usa già), oppure l'icona scelta; senza icona lo si deduce dal nome
	const mode = $derived(m.settings.plateCalculatorEnabled ? plateMode(ex.glyphName ?? suggestedGlyph(ex.name, ex.muscleGroup), ex.name, barKg, ex.plateLoaded) : null)
	const plates = $derived(m.settings.availablePlates.map((p) => round2(toDisplay(p, m.settings.unit))))

	const absWeight = $derived(toTotal(weight))
	const relWeight = $derived(isRel ? weight : Math.max(0, round2(absWeight - (mode?.bar ? bar : 0))))
	const perSideW = $derived(round2(relWeight / 2))

	const inWorkoutPrev = $derived([...workout.sets].reverse().find((s) => s.exercise === ex.uuid && !s.isWarmup && (!set || s.uuid !== set.uuid)))
	const prevSet = $derived(inWorkoutPrev ?? ex.sets.find((s) => !s.isWarmup && (!set || s.uuid !== set.uuid)))
	const prevAbsWeight = $derived(prevSet ? toUnit(prevSet.weight) : null)
	const delta = $derived(prevAbsWeight !== null ? round2(absWeight - prevAbsWeight) : null)

	const kg = $derived(fromUnit(absWeight))
	const reference = $derived(bestOf((arm ? ex.armRecords : ex.records).filter((r) => !set || r.uuid !== set.uuid)))
	const isRecord = $derived(!warm && beats(kg, reps, reference))
	const warmSteps = $derived(warmupFor(ex, m))

	// solo il tocco sul selettore (non il caricamento di una serie): metà o doppio, sulla griglia sbarra + k × passo
	// (per lato il totale sale di due passi alla volta)
	function onArm(toSingle: boolean) {
		const grid = side || isRel || mode?.bar ? bar : 0
		const r = switchArm(toSingle, absWeight, armMemo, grid, side ? step * 2 : step)
		armMemo = r.memo
		weight = fromTotal(r.weight)
	}

	function save(isWarm: boolean = warm) {
		if (!(absWeight > 0) || reps < 1) return
		if (set) { const isPR = patchSet(set.uuid, { weight: kg, reps, isWarmup: isWarm, singleArm: arm }); onadded?.({ isPR, isWarmup: isWarm, uuid: set.uuid }) }
		else { const r = addSet(workout.uuid, ex.uuid, kg, reps, isWarm, arm); onadded?.({ isPR: r.isPR, isWarmup: isWarm, uuid: r.uuid }) }
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
	<div class="stack">
		<!-- CARICO E RIPETIZIONI (insieme, per il riquadro del tutorial) -->
		<div class="stack" data-tour="load">
		<!-- CARD CARICO -->
		<div class="card">
			<div class="row between" style="align-items:baseline">
				<div class="sec-title" style="margin-top:0">{side ? t('Per lato') : isRel ? t('Carico dischi') : t('Carico')}</div>
				<div class="row" style="gap:8px">
					{#if mode?.bar && bar > 0}
						<span class="small badge muted">{t('Sbarra')} {nf(bar)} {unit()}</span>
					{/if}
					{#if hasSides(ex.muscleGroup)}<SideSegment bind:value={arm} group={ex.muscleGroup} onchange={onArm} />{/if}
				</div>
			</div>
			<Stepper bind:value={weight} {step} min={0} max={Math.floor(toDisplayWeight(2000) * 100) / 100} unit={unit()} />
			{#if side}<div class="small muted" style="margin-top:6px">{t('Totale')} <b class="num">{nf(absWeight)} {unit()}</b></div>{/if}

			{#if mode?.bar && bar > 0 && !side}
				<div class="bar-breakdown">
					{#if isRel}
						<div class="row between">
							<span class="small muted">{t('Carico relativo (dischi)')}</span>
							<b class="num" style="font-size:15px;color:var(--accent)">{nf(relWeight)} {unit()} <span class="muted small" style="font-weight:500">({nf(perSideW)} {unit()} {t('per lato')})</span></b>
						</div>
						<div class="row between" style="margin-top:4px">
							<span class="small muted">{t('Carico assoluto (totale con sbarra)')}</span>
							<span class="num" style="font-weight:700">{nf(absWeight)} {unit()}</span>
						</div>
					{:else}
						<div class="row between">
							<span class="small muted">{t('Carico assoluto (totale)')}</span>
							<b class="num" style="font-size:15px">{nf(absWeight)} {unit()}</b>
						</div>
						<div class="row between" style="margin-top:4px">
							<span class="small muted">{t('Carico relativo (dischi)')}</span>
							<span class="num" style="font-weight:700;color:var(--accent)">
								{nf(relWeight)} {unit()}
								<span class="muted small" style="font-weight:500">({nf(perSideW)} {unit()} {t('per lato')})</span>
							</span>
						</div>
					{/if}
				</div>
			{/if}

			{#if delta !== null && delta !== 0}
				<div class="delta-box" class:pos={delta > 0} class:neg={delta < 0}>
					<div class="row" style="gap:6px">
						{#if delta > 0}
							<Icon name="up" size={15} />
							<span><b>+{nf(delta)} {unit()}</b>{#if mode?.bar && bar > 0 || mode || side} (+{nf(delta / 2)} {unit()} {t('per lato')}){/if} <span class="muted">{inWorkoutPrev ? t('rispetto alla serie precedente') : t('rispetto all\'ultimo allenamento')}</span></span>
						{:else}
							<Icon name="down" size={15} />
							<span><b>-{nf(Math.abs(delta))} {unit()}</b>{#if mode?.bar && bar > 0 || mode || side} (-{nf(Math.abs(delta) / 2)} {unit()} {t('per lato')}){/if} <span class="muted">{inWorkoutPrev ? t('rispetto alla serie precedente') : t('rispetto all\'ultimo allenamento')}</span></span>
						{/if}
					</div>
				</div>
			{/if}
		</div>

		<!-- CARD RIPETIZIONI -->
		<div class="card">
			<div class="sec-title" style="margin-top:0">{t('Ripetizioni')}</div>
			<Stepper bind:value={reps} step={1} min={1} max={50} integer />
			<div class="chips" style="margin-top:8px">
				{#each [1, 3, 5, 8, 10, 12] as q}<button class="chip" class:on={reps === q} onclick={() => (reps = q)}>{q}</button>{/each}
			</div>
		</div>
		</div>

		{#if mode && absWeight > 0}<PlateBar total={absWeight} bar={mode.bar ? bar : 0} {plates} unit={unit()} {delta} />{/if}

		<div class="card" style={isRecord ? 'border-color:rgba(var(--accent-rgb),.5)' : ''}>
			<div class="row between">
				<span class="tiny muted" style="font-weight:800;letter-spacing:.08em">{isRecord ? t('QUESTA SERIE') : t('MASSIMALE DA BATTERE')}</span>
				{#if isRecord}<span class="badge accent">{t('NUOVO RECORD')}</span>{/if}
			</div>
			{#if isRecord}
				<div class="mid num">{setLine(kg, reps, arm, ex.muscleGroup)}</div>
				{#if reps > 1}<div class="small muted">≈ {W(oneRepMax(kg, reps))} {t('di 1RM stimato')}</div>{/if}
				{#if reference}<div class="small muted">{t('Precedente')}: {setLine(reference.weight, reference.reps, arm, ex.muscleGroup)}</div>{/if}
			{:else if reference}
				<div class="mid num">{setLine(reference.weight, reference.reps, arm, ex.muscleGroup)}</div>
			{:else}
				<div class="muted">{t('Nessun massimale: la prima serie lo stabilisce.')}</div>
			{/if}
		</div>

		<!-- ULTIME SERIE SU QUESTO MACCHINARIO SUBITO SOTTO MASSIMALE DA BATTERE -->
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
						{#each days as d, i (d.key)}
							{@const best = bestSet(d.sets)}
							<details class="day" open={i === 0}>
								<summary class="row between">
									<span class="row" style="gap:6px"><Icon name="right" size={14} /><b>{relDay(d.at)}</b></span>
									<span class="small muted num">{t('{0} serie', d.sets.length)} · {setLine(listKg(best.weight), best.reps, best.singleArm, ex.muscleGroup)}</span>
								</summary>
								<div class="list">
									{#each d.sets as s (s.uuid)}
										<button class="item" style="background:none;border:0;width:100%;text-align:left;cursor:pointer" onclick={() => { weight = toDisplayWeight(s.weight); reps = s.reps; warm = false; arm = s.singleArm; armMemo = null }}>
											<div class="grow num" style="font-weight:700">
												{setLine(listKg(s.weight), s.reps, s.singleArm, ex.muscleGroup)}
												{#if isRel || side}<span class="tiny muted" style="font-weight:500">({t('tot.')} {W(s.weight)})</span>{/if}
											</div>
											{#if s.isPR}<span class="badge pos">PR</span>{/if}
											{#if s.reps > 1}<span class="small muted num">≈ {W(oneRepMax(s.weight, s.reps))}</span>{/if}
										</button>
									{/each}
								</div>
							</details>
						{/each}
					{/if}
				</div>
			{/if}
		</div>

		<!-- CHIP RISCALDAMENTO CONSIGLIATO -->
		{#if warmSteps.length}
			<div class="stack" style="gap:6px">
				<div class="tiny faint" style="font-weight:700">{t('Riscaldamento consigliato')}:</div>
				<div class="chips">
					{#each warmSteps as s}
						{@const sWeight = fromTotal(s.weight)}
						<button class="chip" onclick={() => { weight = sWeight; reps = s.reps }}>{Math.round(s.percent * 100)}% · {nf(sWeight)} × {s.reps}</button>
					{/each}
				</div>
			</div>
		{/if}

		<!-- PULSANTI AZIONE: RISCALDAMENTO DIRETTO E AGGIUNGI SERIE -->
		<div class="actions">
			<button class="btn block" style="background:rgba(255,255,255,0.06);border-color:var(--stroke)" onclick={() => save(true)} disabled={!(absWeight > 0)}>
				<Icon name="flame" size={17} />{t('Riscaldamento')}
			</button>
			<button class="btn primary block" onclick={() => save(set ? warm : false)} disabled={!(absWeight > 0)} data-tour="save">
				<Icon name="plus" size={17} />{set ? t('Salva') : t('Aggiungi serie')}
			</button>
		</div>

		{#if set}<button class="btn danger block" onclick={remove}>{t('Elimina serie')}</button>{/if}
	</div>
</Sheet>

<style>
	/* i due tasti non vanno mai a capo (320 px, inglese compreso): «Riscaldamento» prende il suo, il principale tutto il resto; sotto i 340 px via le icone */
	.actions { display: grid; grid-template-columns: auto 1fr; gap: 8px; margin-top: 6px; }
	.actions .btn { width: auto; white-space: nowrap; padding: 0 12px; gap: 6px; min-width: 0; }
	@media (max-width: 340px) { .actions .btn :global(svg) { display: none; } }
	.day > summary { cursor: pointer; padding: 6px 0; list-style: none; }
	.day > summary::-webkit-details-marker { display: none; }
	.day > summary :global(svg) { transition: transform .15s ease; }
	.day[open] > summary :global(svg) { transform: rotate(90deg); }
	.bar-breakdown {
		margin-top: 10px;
		padding: 8px 12px;
		border-radius: 12px;
		background: rgba(255, 255, 255, 0.04);
		border: 1px solid var(--stroke);
	}
	.delta-box {
		margin-top: 8px;
		padding: 6px 10px;
		border-radius: 10px;
		font-size: 13px;
		font-family: var(--font-num);
		background: rgba(255, 255, 255, 0.03);
		border: 1px solid var(--stroke);
	}
	.delta-box.pos { border-color: rgba(50, 205, 50, 0.3); color: #32cd32; background: rgba(50, 205, 50, 0.08); }
	.delta-box.neg { border-color: rgba(255, 69, 0, 0.3); color: #ff6b6b; background: rgba(255, 69, 0, 0.08); }
</style>
