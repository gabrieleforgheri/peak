<script lang="ts">
	import { deleteProgram, saveProgram, saveTemplate } from '../lib/actions.ts'
	import { weekdayName } from '../lib/fmt.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { t } from '../lib/i18n.ts'
	import type { Prg } from '../lib/types.ts'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'

	/** La scheda generale: un nome, e se i suoi giorni hanno un giorno fisso della settimana o è solo un raccoglitore. */
	let { prog = null, onclose, onsaved }: { prog?: Prg | null; onclose: () => void; onsaved?: (id: string) => void } = $props()
	let name = $state(prog?.name ?? '')
	let useDays = $state(prog?.useDays ?? false)
	// i giorni della scheda e il giorno della settimana di ciascuno, da assegnare qui
	const days = getModel().templates.filter((d) => prog && d.program === prog.uuid)
	let weekdays = $state<Record<string, number>>(Object.fromEntries(days.map((d) => [d.uuid, d.weekday])))

	function save() {
		if (!name.trim()) return
		const id = saveProgram(prog?.uuid ?? null, name, useDays)
		// senza giorni fissi, ognuno torna senza giorno
		for (const d of days) {
			const w = useDays ? (weekdays[d.uuid] ?? -1) : -1
			if (w !== d.weekday) saveTemplate(d.uuid, d.name, d.items, undefined, undefined, { program: d.program, weekday: w })
		}
		onsaved?.(id)
		onclose()
	}
	function remove() {
		if (!prog || !confirm(t('Eliminare questa scheda e i suoi giorni? Gli allenamenti già fatti restano.'))) return
		deleteProgram(prog.uuid)
		onclose()
	}
</script>

<Sheet title={prog ? t('Scheda') : t('Nuova scheda')} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!name.trim()}>{t('Salva')}</button>{/snippet}
	<div class="stack">
		<label class="field"><span>{t('Nome')}</span><input class="input" bind:value={name} placeholder={t('es. Push Pull Legs')} maxlength="80" /></label>
		<label class="row between card"><span>{t('Giorni fissi della settimana')}</span><input class="toggle" type="checkbox" bind:checked={useDays} /></label>
		{#if useDays && days.length}
			{#each days as d (d.uuid)}
				<div class="card">
					<b class="ellipsis" style="display:block;margin-bottom:8px">{d.name}</b>
					<div class="chips">
						<button class="chip" class:on={weekdays[d.uuid] === -1} onclick={() => (weekdays[d.uuid] = -1)}>{t('Nessuno')}</button>
						{#each [0, 1, 2, 3, 4, 5, 6] as w}<button class="chip" class:on={weekdays[d.uuid] === w} onclick={() => (weekdays[d.uuid] = w)}>{weekdayName(w)}</button>{/each}
					</div>
				</div>
			{/each}
		{/if}
		{#if prog}<button class="btn danger block" onclick={remove}><Icon name="trash" size={18} />{t('Elimina scheda')}</button>{/if}
	</div>
</Sheet>
