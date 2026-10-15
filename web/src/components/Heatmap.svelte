<script lang="ts">
	import { weekStart } from '@common/stats.ts'
	import { dateShort } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'

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
	const level = (n: number) => (n <= 0 ? 0 : n < 8 ? 1 : n < 16 ? 2 : n < 24 ? 3 : 4)
</script>

<div class="grid" style="--cols:{weeks}" role="img" aria-label={t('Giorni in cui ti sei allenato')}>
	{#each cells as col}
		<div class="col">
			{#each col as c}
				<div class="cell" class:future={c.n < 0} data-l={level(c.n)} title="{dateShort(c.d.getTime())}{c.n > 0 ? ' · ' + c.n : ''}"></div>
			{/each}
		</div>
	{/each}
</div>

<style>
	.grid { display: grid; grid-template-columns: repeat(var(--cols), 1fr); gap: 4px; }
	.col { display: grid; grid-template-rows: repeat(7, 1fr); gap: 4px; }
	.cell { aspect-ratio: 1; border-radius: 4px; background: rgba(255,255,255,.06); }
	.cell.future { background: transparent; }
	.cell[data-l='1'] { background: rgba(var(--accent-rgb), .3); }
	.cell[data-l='2'] { background: rgba(var(--accent-rgb), .55); }
	.cell[data-l='3'] { background: rgba(var(--accent-rgb), .8); }
	.cell[data-l='4'] { background: rgb(var(--accent-rgb)); box-shadow: 0 0 8px rgba(var(--accent-rgb), .6); }
</style>
