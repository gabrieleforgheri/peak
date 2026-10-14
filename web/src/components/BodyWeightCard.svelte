<script lang="ts">
	import { round2, toDisplay } from '@common/domain.ts'
	import { W, dateLong, dateShort, unit } from '../lib/fmt.ts'
	import { locale, t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Bw } from '../lib/types.ts'
	import BodyWeightSheet from './BodyWeightSheet.svelte'
	import Icon from './Icon.svelte'
	import LineChart from './LineChart.svelte'

	const m = $derived(getModel())
	let sheet = $state<Bw | 'new' | null>(null)

	const latest = $derived(m.bodyweights.at(-1))
	// la variazione è rispetto alla prima pesata di almeno 30 giorni prima dell'ultima
	const change = $derived.by(() => {
		if (!latest) return null
		const ref = m.bodyweights.find((b) => latest.dateMs - b.dateMs >= 30 * 86_400_000)
		if (!ref) return null
		const d = round2(toDisplay(latest.weight, m.settings.unit)) - round2(toDisplay(ref.weight, m.settings.unit))
		return Math.abs(d) >= 0.05 ? d : null
	})
	const recent = $derived(m.bodyweights.filter((b) => b.dateMs >= Date.now() - 180 * 86_400_000))
	const points = $derived(recent.map((b) => ({ y: round2(toDisplay(b.weight, m.settings.unit)), label: dateShort(b.dateMs), faded: b.fromHealth })))
	const domain = $derived.by((): [number, number] => {
		const ys = points.map((p) => p.y)
		if (!ys.length) return [0, 1]
		const lo = Math.min(...ys), hi = Math.max(...ys), pad = Math.max(0.5, (hi - lo) * 0.2)
		return [lo - pad, hi + pad]
	})
	const nf = new Intl.NumberFormat(locale, { maximumFractionDigits: 1, signDisplay: 'exceptZero' })
</script>

<div class="card">
	<div class="row between">
		<div class="sec-title" style="margin:0">{t('Peso corporeo')}</div>
		<button class="btn small" onclick={() => (sheet = 'new')}><Icon name="plus" size={16} />{t('Aggiungi pesata')}</button>
	</div>
	{#if latest}
		<div class="row" style="margin:8px 0;align-items:baseline">
			<span class="big num">{W(latest.weight, false)}</span><span class="mid muted">{unit()}</span>
			{#if change !== null}<span class="badge" class:pos={change < 0}>{nf.format(change)} {unit()}</span>{/if}
		</div>
		<div class="small muted">{dateLong(latest.dateMs)}</div>
		{#if points.length >= 2}<div style="margin-top:8px"><LineChart {points} {domain} /></div>{/if}
		<div class="list" style="margin-top:10px">
			{#each [...m.bodyweights].reverse().slice(0, 5) as b (b.uuid)}
				<button class="item" onclick={() => (sheet = b)}><div class="grow small muted">{dateLong(b.dateMs)}</div><b class="num">{W(b.weight)}</b></button>
			{/each}
		</div>
	{:else}
		<p class="muted small" style="margin-top:6px">{t('Registra le pesate per vedere come cambia il tuo peso.')}</p>
	{/if}
</div>
{#if sheet}<BodyWeightSheet entry={sheet === 'new' ? null : sheet} onclose={() => (sheet = null)} />{/if}
