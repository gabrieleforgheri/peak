<script lang="ts">
	import { V } from '../lib/fmt.ts'
	import { locale, t } from '../lib/i18n.ts'

	/** Una barra per settimana, dalla più vecchia a questa; sotto, il giorno in cui la settimana comincia (il lunedì). */
	let { weeks }: { weeks: { ws: number; volume: number }[] } = $props()
	const max = $derived(Math.max(1, ...weeks.map((w) => w.volume)))
	const day = new Intl.DateTimeFormat(locale, { day: 'numeric' })
	const month = new Intl.DateTimeFormat(locale, { month: 'short' })
	const full = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' })
	// il mese si scrive alla prima barra e ogni volta che cambia
	const monthAt = (i: number) => i === 0 || new Date(weeks[i]!.ws).getMonth() !== new Date(weeks[i - 1]!.ws).getMonth()
</script>

<div class="wk" role="img" aria-label={t('Volume sollevato per settimana')}>
	<div class="bars">
		{#each weeks as w, i}
			<div class="col" class:now={i === weeks.length - 1}>
				<div class="bar" class:zero={w.volume === 0} style="height:{Math.max(4, (w.volume / max) * 100)}%" title="{t('Settimana dal {0}', full.format(w.ws))}: {V(w.volume)}"></div>
			</div>
		{/each}
	</div>
	<div class="axis">
		{#each weeks as w, i}
			<div class="col" class:now={i === weeks.length - 1}><span>{day.format(w.ws)}</span><span class="mo">{monthAt(i) ? month.format(w.ws) : ''}</span></div>
		{/each}
	</div>
	<div class="row between tiny muted" style="margin-top:8px"><span>{t('1 barra = 1 settimana')}</span><span>{t('questa settimana')}: <b style="color:var(--text)">{V(weeks.at(-1)?.volume ?? 0)}</b></span></div>
</div>

<style>
	.bars, .axis { display: flex; gap: 6px; }
	.bars { align-items: flex-end; height: 110px; }
	.col { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; }
	.axis .col { height: auto; font-size: 10px; color: var(--text-3); margin-top: 6px; font-variant-numeric: tabular-nums; line-height: 1.3; }
	.axis .mo { min-height: 13px; white-space: nowrap; text-transform: capitalize; }
	.axis .now span:first-child { color: var(--accent); font-weight: 800; }
	.bar { width: 100%; border-radius: 6px 6px 3px 3px; background: linear-gradient(to top, rgba(var(--accent-rgb), .25), rgba(var(--accent-rgb), .7)); }
	.bar.zero { background: rgba(255,255,255,.07); }
	.now .bar { background: linear-gradient(to top, rgba(var(--accent-rgb), .5), rgb(var(--accent-rgb))); box-shadow: 0 0 14px rgba(var(--accent-rgb), .45); }
</style>
