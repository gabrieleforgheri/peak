<script lang="ts">
	import { weekStart } from '@common/stats.ts'
	import { dateShort } from '../lib/fmt.ts'
	import { locale, t } from '../lib/i18n.ts'

	/** Una cella per giorno, le ultime `weeks` settimane (colonne) da lunedì a domenica (righe): più serie, più colore. */
	let { sets, weeks = 14 }: { sets: Map<string, number>; weeks?: number } = $props()
	const key = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
	const cells = $derived.by(() => {
		const today = new Date()
		const first = new Date(weekStart(today.getTime()))
		first.setDate(first.getDate() - 7 * (weeks - 1))
		return Array.from({ length: weeks }, (_, c) => Array.from({ length: 7 }, (_, r) => {
			const d = new Date(first); d.setDate(first.getDate() + c * 7 + r)
			return { d, n: d > today ? -1 : (sets.get(key(d)) ?? 0) }
		}))
	})
	// le righe sono i giorni (lunedì in alto), le colonne le settimane: lettera del giorno a sinistra, mese sopra
	const dayNames = Array.from({ length: 7 }, (_, i) => new Intl.DateTimeFormat(locale, { weekday: 'narrow' }).format(new Date(2024, 0, 1 + i)))
	const monthOf = new Intl.DateTimeFormat(locale, { month: 'short' })
	// il mese sta sopra la colonna che contiene il giorno 1 (guardo la domenica), non sopra il primo lunedì
	const months = $derived(cells.map((col, c) => (c === 0 || col[6]!.d.getMonth() !== cells[c - 1]![6]!.d.getMonth() ? monthOf.format(col[6]!.d) : '')))
	const level = (n: number) => (n <= 0 ? 0 : n < 8 ? 1 : n < 16 ? 2 : n < 24 ? 3 : 4)
</script>

<div class="map" role="img" aria-label={t('Giorni in cui ti sei allenato')}>
	<div class="days">{#each dayNames as d}<span>{d}</span>{/each}</div>
	<div class="main">
		<div class="mo" style="--cols:{weeks}">{#each months as m}<span>{m}</span>{/each}</div>
		<div class="grid" style="--cols:{weeks}">
			{#each cells as col}
				<div class="col">
					{#each col as c}
						<div class="cell" class:future={c.n < 0} class:first={c.d.getDate() === 1} data-l={level(c.n)} title="{dateShort(c.d.getTime())}{c.n > 0 ? ' · ' + c.n : ''}"></div>
					{/each}
				</div>
			{/each}
		</div>
	</div>
</div>

<style>
	.map { display: flex; gap: 6px; }
	.main { flex: 1; min-width: 0; }
	.days { display: grid; grid-template-rows: repeat(7, 1fr); gap: 4px; margin-top: 16px; font-size: 10px; color: var(--text-3); width: 10px; }
	.days span { display: flex; align-items: center; justify-content: center; }
	.mo { display: grid; grid-template-columns: repeat(var(--cols), 1fr); gap: 4px; height: 12px; margin-bottom: 4px; font-size: 10px; color: var(--text-3); text-transform: capitalize; }
	.mo span { white-space: nowrap; overflow: visible; }
	.grid { display: grid; grid-template-columns: repeat(var(--cols), 1fr); gap: 4px; }
	.col { display: grid; grid-template-rows: repeat(7, 1fr); gap: 4px; }
	.cell { aspect-ratio: 1; border-radius: 4px; background: rgba(255,255,255,.06); }
	.cell.future { background: transparent; }
	/* il giorno 1 del mese è bordato: si vede dove inizia il mese dentro la settimana */
	.cell.first { outline: 1.5px solid var(--text-2); outline-offset: -1.5px; }
	.cell[data-l='1'] { background: rgba(var(--accent-rgb), .3); }
	.cell[data-l='2'] { background: rgba(var(--accent-rgb), .55); }
	.cell[data-l='3'] { background: rgba(var(--accent-rgb), .8); }
	.cell[data-l='4'] { background: rgb(var(--accent-rgb)); box-shadow: 0 0 8px rgba(var(--accent-rgb), .6); }
</style>
