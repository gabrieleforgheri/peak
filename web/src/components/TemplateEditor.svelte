<script lang="ts">
	import type { TemplateItem } from '@common/docs.ts'
	import { deleteTemplate, saveTemplate } from '../lib/actions.ts'
	import { fromUnit, toUnit, unit } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Tpl } from '../lib/types.ts'
	import ExercisePicker from './ExercisePicker.svelte'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'
	import Thumb from './Thumb.svelte'

	/** `tpl`: modifica una scheda; `initial`: righe già pronte (salva come scheda), la scheda è nuova. */
	let { tpl = null, initial = null, onclose, onsaved }: {
		tpl?: Tpl | null; initial?: { name: string; items: TemplateItem[] } | null; onclose: () => void; onsaved?: (id: string) => void
	} = $props()
	const m = getModel()

	interface Row { exercise: string; sets: number | null; repsMin: number | null; repsMax: number | null; weight: number | null }
	const toRow = (i: TemplateItem): Row => ({ exercise: i.exercise, sets: i.sets || null, repsMin: i.repsMin || null, repsMax: i.repsMax || null, weight: i.weight === null ? null : toUnit(i.weight) })
	const source = tpl ?? initial
	let name = $state(source?.name ?? '')
	let rows = $state<Row[]>((source?.items ?? []).filter((i) => m.exById.has(i.exercise)).map(toRow))
	let picking = $state(false)

	const s = m.settings
	const int = (v: number | null, max: number) => Math.min(max, Math.max(0, Math.round(v ?? 0)))

	function move(i: number, dir: -1 | 1) {
		const j = i + dir
		if (j < 0 || j >= rows.length) return
		;[rows[i], rows[j]] = [rows[j]!, rows[i]!]
	}

	function items(): TemplateItem[] {
		return rows.map((r) => {
			let min = int(r.repsMin, 100), max = int(r.repsMax, 100)
			if (!min) min = max // basta una cifra sola
			if (max < min) max = min
			const w = r.weight === null || !(r.weight >= 0) ? null : Math.min(2000, fromUnit(r.weight))
			return { exercise: r.exercise, sets: int(r.sets, 20), repsMin: min, repsMax: max, weight: w }
		})
	}

	function save() {
		if (!name.trim()) return
		const id = saveTemplate(tpl?.uuid ?? null, name, items())
		onsaved?.(id)
		onclose()
	}
	function remove() {
		if (!tpl || !confirm(t('Eliminare questa scheda? Gli allenamenti già fatti restano.'))) return
		deleteTemplate(tpl.uuid)
		onclose()
	}
</script>

<Sheet title={tpl ? t('Scheda') : t('Nuova scheda')} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!name.trim()}>{t('Salva')}</button>{/snippet}
	<div class="stack">
		<label class="field"><span>{t('Nome')}</span><input class="input" bind:value={name} placeholder={t('es. Push 1')} maxlength="80" /></label>

		{#each rows as r, i (r.exercise)}
			{@const ex = m.exById.get(r.exercise)}
			{#if ex}
				<div class="card">
					<div class="row">
						<Thumb {ex} />
						<b class="grow ellipsis">{ex.name}</b>
						<button class="icon-btn" onclick={() => move(i, -1)} disabled={i === 0} aria-label={t('Su')}><Icon name="up" /></button>
						<button class="icon-btn" onclick={() => move(i, 1)} disabled={i === rows.length - 1} aria-label={t('Giù')}><Icon name="down" /></button>
						<button class="icon-btn" onclick={() => (rows = rows.filter((x) => x !== r))} aria-label={t('Togli')}><Icon name="x" /></button>
					</div>
					{#if s.templateSets || s.templateReps || s.templateWeight}
						<div class="row" style="margin-top:10px;gap:8px">
							{#if s.templateSets}<label class="field grow"><span>{t('Serie')}</span><input class="input num" type="number" inputmode="numeric" min="0" max="20" bind:value={r.sets} /></label>{/if}
							{#if s.templateReps}
								<label class="field grow"><span>{t('Rip min')}</span><input class="input num" type="number" inputmode="numeric" min="0" max="100" bind:value={r.repsMin} /></label>
								<label class="field grow"><span>{t('Rip max')}</span><input class="input num" type="number" inputmode="numeric" min="0" max="100" bind:value={r.repsMax} /></label>
							{/if}
							{#if s.templateWeight}<label class="field" style="flex:1.6"><span>{unit()}</span><input class="input num" type="number" inputmode="decimal" min="0" step="0.5" bind:value={r.weight} placeholder={t('ultimo')} /></label>{/if}
						</div>
					{/if}
				</div>
			{/if}
		{/each}

		<button class="btn block" onclick={() => (picking = true)}><Icon name="plus" size={20} />{t('Aggiungi esercizio')}</button>
		{#if tpl}<button class="btn danger block" onclick={remove}><Icon name="trash" size={18} />{t('Elimina scheda')}</button>{/if}
	</div>
</Sheet>

{#if picking}<ExercisePicker focus={new Set()} onclose={() => (picking = false)} onpick={(ex) => { if (!rows.some((x) => x.exercise === ex.uuid)) rows = [...rows, { exercise: ex.uuid, sets: null, repsMin: null, repsMax: null, weight: null }] }} />{/if}
