<script lang="ts">
	import { MUSCLE_GROUPS, type TemplateIcon, type TemplateItem } from '@common/docs.ts'
	import { suggestTemplateIcon } from '@common/template-icon.ts'
	import { deleteTemplate, saveTemplate } from '../lib/actions.ts'
	import { activeProgram } from '../lib/derive.ts'
	import { fromUnit, toUnit, unit, weekdayName } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Tpl } from '../lib/types.ts'
	import ExercisePicker from './ExercisePicker.svelte'
	import Icon from './Icon.svelte'
	import IconPicker from './IconPicker.svelte'
	import TemplateIconView from './TemplateIcon.svelte'
	import Sheet from './Sheet.svelte'
	import Thumb from './Thumb.svelte'
	import FigThumb from './FigThumb.svelte'

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
	let hidden = $state(tpl?.hidden ?? false)
	let icon = $state<'' | TemplateIcon>(tpl?.icon ?? '')
	let allIcons = $state(false)
	let program = $state(tpl?.program ?? activeProgram(m)?.uuid ?? '')
	let weekday = $state(tpl?.weekday ?? -1)
	const prog = $derived(m.programs.find((p) => p.uuid === program))
	// la prima casella è l'icona che dice il nome (finché non ne scegli un'altra); poi i muscoli e qualche icona da palestra
	const auto = $derived(suggestTemplateIcon(name) || 'dumbbell')
	const QUICK: TemplateIcon[] = ['flame', 'bolt', 'trophy', 'timer', 'heart']

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

	// serie, ripetizioni e peso tornano vuoti: restano gli esercizi (utile quando la scheda nasce da un allenamento)
	const reset = () => { rows = rows.map((r) => ({ ...r, sets: null, repsMin: null, repsMax: null, weight: null })) }

	function save() {
		if (!name.trim()) return
		const id = saveTemplate(tpl?.uuid ?? null, name, items(), hidden, icon, { program, weekday: prog?.useDays ? weekday : -1 })
		onsaved?.(id)
		onclose()
	}
	function remove() {
		if (!tpl || !confirm(t('Eliminare questo giorno? Gli allenamenti già fatti restano.'))) return
		deleteTemplate(tpl.uuid)
		onclose()
	}
</script>

<Sheet title={tpl ? t('Giorno') : t('Nuovo giorno')} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!name.trim()}>{t('Salva')}</button>{/snippet}
	<div class="stack">
		<label class="field"><span>{t('Nome')}</span><input class="input" bind:value={name} placeholder={t('es. Push 1')} maxlength="80" /></label>

		{#if m.programs.length > 1}
			<div class="field"><span class="sec-title" style="margin:0 4px 6px">{t('Scheda')}</span>
				<div class="chips">{#each m.programs as p (p.uuid)}<button class="chip" class:on={program === p.uuid} onclick={() => (program = p.uuid)}>{p.name}</button>{/each}</div></div>
		{/if}
		{#if prog?.useDays}
			<div class="field"><span class="sec-title" style="margin:0 4px 6px">{t('Giorno della settimana')}</span>
				<div class="chips">
					<button class="chip" class:on={weekday === -1} onclick={() => (weekday = -1)}>{t('Nessuno')}</button>
					{#each [0, 1, 2, 3, 4, 5, 6] as d}<button class="chip" class:on={weekday === d} onclick={() => (weekday = d)}>{weekdayName(d)}</button>{/each}
				</div></div>
		{/if}
		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Icona')}</span>
			<div class="chips">
				<button class="chip" class:on={icon === ''} onclick={() => (icon = '')} style="padding:0 12px;gap:6px;display:inline-flex;align-items:center"><TemplateIconView icon="" {name} size={22} />{t('Automatica')}</button>
				{#each MUSCLE_GROUPS as g}
					<button class="chip" class:on={icon === `muscle:${g}`} onclick={() => (icon = `muscle:${g}`)} aria-label={g} style="padding:0 8px"><TemplateIconView icon={`muscle:${g}`} size={24} /></button>
				{/each}
				{#each QUICK as ic}
					<button class="chip" class:on={icon === ic} onclick={() => (icon = ic)} aria-label={ic} style="padding:0 10px"><Icon name={ic} size={20} /></button>
				{/each}
				<button class="chip" onclick={() => (allIcons = true)}>{t('Tutte')}</button>
			</div>
		</div>

		{#if rows.length}<button class="btn small" style="align-self:flex-start" onclick={reset}>{t('Azzera i campi')}</button>{/if}

		{#each rows as r, i (r.exercise)}
			{@const ex = m.exById.get(r.exercise)}
			{#if ex}
				<div class="card">
					<!-- RIGA 1: Titolo esercizio sopra a tutta larghezza e pulsanti a destra -->
					<div class="row between" style="align-items:center;gap:8px">
						<b class="ellipsis grow" style="font-size:16px">{ex.name}</b>
						<div class="row" style="gap:4px;flex-shrink:0">
							<button class="icon-btn" onclick={() => move(i, -1)} disabled={i === 0} aria-label={t('Su')}><Icon name="up" /></button>
							<button class="icon-btn" onclick={() => move(i, 1)} disabled={i === rows.length - 1} aria-label={t('Giù')}><Icon name="down" /></button>
							<button class="icon-btn" onclick={() => (rows = rows.filter((x) => x !== r))} aria-label={t('Togli')}><Icon name="x" /></button>
						</div>
					</div>

					<!-- RIGA 2: Miniature macchinario e corpo -->
					<div class="row" style="margin-top:8px;gap:6px">
						<Thumb {ex} />{#if ex.figure.muscles.length}<FigThumb muscles={ex.figure.muscles} view={ex.figure.view} group={ex.muscleGroup} />{/if}
					</div>
					<div class="row" style="margin-top:10px;gap:8px">
						<label class="field grow"><span>{t('Serie')}</span><input class="input num" type="number" inputmode="numeric" min="0" max="20" bind:value={r.sets} /></label>
						<label class="field grow"><span>{t('Rip min')}</span><input class="input num" type="number" inputmode="numeric" min="0" max="100" bind:value={r.repsMin} /></label>
						<label class="field grow"><span>{t('Rip max')}</span><input class="input num" type="number" inputmode="numeric" min="0" max="100" bind:value={r.repsMax} /></label>
						<label class="field" style="flex:1.6"><span>{unit()}</span><input class="input num" type="number" inputmode="decimal" min="0" step="0.5" bind:value={r.weight} placeholder={t('ultimo')} /></label>
					</div>
				</div>
			{/if}
		{/each}

		<button class="btn block" onclick={() => (picking = true)}><Icon name="plus" size={20} />{t('Aggiungi esercizio')}</button>
		<label class="row between card"><span>{t('Nascondi agli amici')}</span><input class="toggle" type="checkbox" bind:checked={hidden} /></label>
		{#if tpl}<button class="btn danger block" onclick={remove}><Icon name="trash" size={18} />{t('Elimina giorno')}</button>{/if}
	</div>
</Sheet>

{#if allIcons}<IconPicker current={icon} onpick={(i) => (icon = i)} onclose={() => (allIcons = false)} />{/if}
{#if picking}<ExercisePicker focus={new Set()} onclose={() => (picking = false)} onpick={(ex) => { if (!rows.some((x) => x.exercise === ex.uuid)) rows = [...rows, { exercise: ex.uuid, sets: null, repsMin: null, repsMax: null, weight: null }] }} />{/if}
