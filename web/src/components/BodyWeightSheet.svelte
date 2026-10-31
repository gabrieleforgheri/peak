<script lang="ts">
	import { BODYWEIGHT_STEP } from '@common/domain.ts'
	import { addBodyWeight, deleteBodyWeight, patchBodyWeight } from '../lib/actions.ts'
	import { fromDateInput, fromUnit, toDateInput, toUnit, unit } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Bw } from '../lib/types.ts'
	import Sheet from './Sheet.svelte'
	import Stepper from './Stepper.svelte'

	let { entry = null, onclose }: { entry?: Bw | null; onclose: () => void } = $props()
	const m = getModel()
	const last = m.bodyweights.at(-1)
	let weight = $state(toUnit(entry?.weight ?? last?.weight ?? 75))
	let day = $state(toDateInput(entry?.dateMs ?? Date.now()))

	// i limiti del server sono in kg (20-400): si controllano lì, non nell'unità mostrata
	const kg = $derived(fromUnit(weight))
	const ok = $derived(kg >= 20 && kg <= 400)

	function save() {
		if (entry) patchBodyWeight(entry.uuid, kg, fromDateInput(day))
		else addBodyWeight(kg, fromDateInput(day))
		onclose()
	}
</script>

<Sheet title={entry ? t('Modifica pesata') : t('Aggiungi pesata')} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!ok}>{t('Salva')}</button>{/snippet}
	<div class="stack">
		<div class="card"><Stepper bind:value={weight} step={BODYWEIGHT_STEP[m.settings.unit]} min={0} max={Math.floor(toUnit(400) * 10) / 10} unit={unit()} /></div>
		<label class="field"><span>{t('Data')}</span><input class="input" type="date" bind:value={day} min="2000-01-01" max={toDateInput(Date.now())} /></label>
		{#if entry}<button class="btn danger block" onclick={() => { deleteBodyWeight(entry.uuid); onclose() }}>{t('Elimina pesata')}</button>{/if}
	</div>
</Sheet>
