<script lang="ts">
	import { beats, oneRepMax, toDisplay, round2 } from '@common/domain.ts'
	import { addRecord } from '../lib/actions.ts'
	import { stepOf } from '../lib/derive.ts'
	import { W, dateLong, fromDateInput, fromUnit, setLine, toDateInput, unit } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Ex } from '../lib/types.ts'
	import Sheet from './Sheet.svelte'
	import Stepper from './Stepper.svelte'

	let { ex, onclose }: { ex: Ex; onclose: () => void } = $props()
	const m = getModel()
	const step = stepOf(ex, m)

	// valori di partenza: l'ultimo massimale, altrimenti l'ultima serie, altrimenti quattro passi
	const last = ex.records[0] ?? null
	const lastSet = ex.sets.find((s) => !s.isWarmup) ?? ex.sets[0] ?? null
	let weight = $state(round2(toDisplay(last?.weight ?? lastSet?.weight ?? step * 4, m.settings.unit)))
	let reps = $state(last?.reps ?? lastSet?.reps ?? 1)
	let day = $state(toDateInput(Date.now()))
	let note = $state('')
	let arm = $state(false)

	const kg = $derived(fromUnit(weight))
	const best = $derived(arm ? ex.armBest : ex.best)
	const isNew = $derived(beats(kg, reps, best))
	const est = $derived(oneRepMax(kg, reps))

	function save() {
		addRecord(ex.uuid, kg, reps, fromDateInput(day), note.trim(), arm)
		onclose()
	}
</script>

<Sheet title={t('Aggiorna massimale')} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!(weight > 0)}>{t('Salva')}</button>{/snippet}
	<div class="stack">
		<div class="card">
			<div class="sec-title" style="margin-top:0">{t('Carico')}</div>
			<Stepper bind:value={weight} {step} min={0} max={5000} unit={unit()} />
			<div class="sec-title">{t('Ripetizioni')}</div>
			<Stepper bind:value={reps} step={1} min={1} max={50} />
		</div>
		<div class="card">
			<div class="row between">
				<span class="muted">{isNew ? '' : t('Prestazione')}</span>
				{#if isNew}<span class="badge accent">{t('NUOVO RECORD')}</span>{/if}
			</div>
			{#if reps > 1}<div class="row between"><span class="muted">{t('1RM stimato')}</span><b class="num">≈ {W(est)}</b></div>{/if}
			{#if best}<div class="row between"><span class="muted">{t('Precedente')}</span><span class="num">{setLine(best.weight, best.reps, arm)} · {dateLong(best.date)}</span></div>{/if}
		</div>
		<label class="card row between"><span>{t('Un braccio')}</span><input class="toggle" type="checkbox" bind:checked={arm} /></label>
		<label class="field"><span>{t('Data')}</span><input class="input" type="date" bind:value={day} max={toDateInput(Date.now())} /></label>
		<label class="field"><span>{t('Nota')}</span><input class="input" bind:value={note} maxlength="500" placeholder={t('Facoltativa')} /></label>
	</div>
</Sheet>
