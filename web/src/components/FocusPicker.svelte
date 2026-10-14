<script lang="ts">
	import { MUSCLE_GROUPS, type MuscleGroup } from '@common/docs.ts'
	import { focusRaw, patchWorkout, startWorkout } from '../lib/actions.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Wk } from '../lib/types.ts'
	import Sheet from './Sheet.svelte'

	let { workout = null, onclose, onstart }: { workout?: Wk | null; onclose: () => void; onstart?: (id: string) => void } = $props()
	const m = getModel()

	const parse = (raw: string) => new Set(raw.split(',').filter(Boolean) as MuscleGroup[])
	let name = $state(workout?.name ?? '')
	let focus = $state(parse(workout?.focus ?? ''))

	// fino a sei allenamenti con nome recenti, uno per nome
	const recent = (() => {
		const seen = new Set<string>(), out: Wk[] = []
		for (const w of m.workouts) {
			const k = w.name.trim().toLowerCase()
			if (!k || seen.has(k)) continue
			seen.add(k); out.push(w)
			if (out.length === 6) break
		}
		return out
	})()

	const toggle = (g: MuscleGroup) => { const n = new Set(focus); n.has(g) ? n.delete(g) : n.add(g); focus = n }

	function go() {
		if (workout) patchWorkout(workout.uuid, { name: name.trim(), focus: focusRaw(focus) })
		else onstart?.(startWorkout(name, focus))
		onclose()
	}
</script>

<Sheet title={t('Cosa alleni oggi?')} {onclose}>
	<div class="stack">
		<label class="field"><span>{t('Nome (facoltativo)')}</span><input class="input" bind:value={name} placeholder={t('es. Push 1')} maxlength="80" /></label>
		{#if recent.length && !workout}
			<div class="chips">
				{#each recent as w}<button class="chip" onclick={() => { name = w.name; focus = parse(w.focus) }}>{w.name}</button>{/each}
			</div>
		{/if}
		<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">
			{#each MUSCLE_GROUPS as g}
				<button class="chip" class:on={focus.has(g)} onclick={() => toggle(g)} style="min-height:52px;border-radius:14px">{GROUP_NAMES[g]}</button>
			{/each}
		</div>
		<p class="small faint">{t('Se non scegli nulla vedi tutti i macchinari.')}</p>
		<button class="btn primary block" onclick={go}>{workout ? t('Salva') : t('Inizia')}</button>
	</div>
</Sheet>
