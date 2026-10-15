<script lang="ts">
	import { MUSCLE_GROUPS, type MuscleGroup } from '@common/docs.ts'
	import { focusRaw, patchWorkout, startWorkout } from '../lib/actions.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Tpl, Wk } from '../lib/types.ts'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'

	let { workout = null, onclose, onstart }: { workout?: Wk | null; onclose: () => void; onstart?: (id: string) => void } = $props()
	const m = getModel()

	const parse = (raw: string) => new Set(raw.split(',').filter(Boolean) as MuscleGroup[])
	let name = $state(workout?.name ?? '')
	let focus = $state(parse(workout?.focus ?? ''))

	const toggle = (g: MuscleGroup) => { const n = new Set(focus); n.has(g) ? n.delete(g) : n.add(g); focus = n }

	function fromTemplate(tpl: Tpl) {
		onstart?.(startWorkout('', [], tpl))
		onclose()
	}

	function go() {
		if (workout) patchWorkout(workout.uuid, { name: name.trim(), focus: focusRaw(focus) })
		else onstart?.(startWorkout(name, focus))
		onclose()
	}
</script>

<Sheet title={t('Cosa alleni oggi?')} {onclose}>
	<div class="stack">
		{#if !workout && m.templates.length}
			<div class="sec-title" style="margin:0 4px">{t('Le tue schede')}</div>
			<div class="card list">
				{#each m.templates as tpl (tpl.uuid)}
					<button class="item" onclick={() => fromTemplate(tpl)}>
						<div class="grow"><b class="ellipsis" style="display:block">{tpl.name}</b><span class="small muted">{tn(tpl.items.length, '1 esercizio', '{0} esercizi')}</span></div>
						<Icon name="right" size={20} />
					</button>
				{/each}
			</div>
			<div class="sec-title" style="margin:4px 4px 0">{t('Oppure scegli i gruppi')}</div>
		{/if}
		<label class="field"><span>{t('Nome (facoltativo)')}</span><input class="input" bind:value={name} placeholder={t('es. Push 1')} maxlength="80" /></label>
XX		<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">
			{#each MUSCLE_GROUPS as g}
				<button class="chip" class:on={focus.has(g)} onclick={() => toggle(g)} style="min-height:52px;border-radius:14px">{GROUP_NAMES[g]}</button>
			{/each}
		</div>
		<p class="small faint">{t('Se non scegli nulla vedi tutti i macchinari.')}</p>
		<button class="btn primary block" onclick={go}>{workout ? t('Salva') : t('Inizia')}</button>
	</div>
</Sheet>
