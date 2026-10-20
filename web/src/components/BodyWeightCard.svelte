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
	type Range = '1m' | '3m' | '1y' | 'all'
	let range = $state<Range>('all')

	const latest = $derived(m.bodyweights.at(-1))
	// la variazione è rispetto alla prima pesata di almeno 30 giorni prima dell'ultima
	const change = $derived.by(() => {
		if (!latest) return null
		const ref = m.bodyweights.find((b) => latest.dateMs - b.dateMs >= 30 * 86_400_000)
		if (!ref) return null
		const d = round2(toDisplay(latest.weight, m.settings.unit)) - round2(toDisplay(ref.weight, m.settings.unit))
		return Math.abs(d) >= 0.05 ? d : null
	})

	const spanDays = $derived.by(() => {
		if (!m.bodyweights.length) return 0
		const earliest = Math.min(...m.bodyweights.map((b) => b.dateMs))
		return Math.max(0, (Date.now() - earliest) / 86_400_000)
	})

	const availableRanges = $derived.by((): Range[] => {
		const out: Range[] = []
		if (spanDays >= 30 && spanDays < 365) out.push('1m')
		if (spanDays >= 90) out.push('3m')
		if (spanDays >= 365) out.push('1y')
		out.push('all')
		return out
	})

	$effect(() => {
		if (!availableRanges.includes(range)) range = availableRanges[0] ?? 'all'
	})

	const filteredWeights = $derived.by(() => {
		const days = range === '1m' ? 30 : range === '3m' ? 90 : range === '1y' ? 365 : null
		if (!days) return m.bodyweights
		const since = Date.now() - days * 86_400_000
		return m.bodyweights.filter((b) => b.dateMs >= since)
	})

	const points = $derived(filteredWeights.map((b) => ({ y: round2(toDisplay(b.weight, m.settings.unit)), label: dateShort(b.dateMs), faded: b.fromHealth })))
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
		{#if m.bodyweights.length >= 2}
			<div style="margin-top:10px">
				{#if availableRanges.length > 1}
					<div class="row between" style="margin-bottom:8px">
						<span class="small muted">{t('Andamento')}</span>
						<div class="seg" style="width:auto">
							{#if availableRanges.includes('1m')}<button class:on={range === '1m'} onclick={() => (range = '1m')}>{t('1 mese')}</button>{/if}
							{#if availableRanges.includes('3m')}<button class:on={range === '3m'} onclick={() => (range = '3m')}>{t('3 mesi')}</button>{/if}
							{#if availableRanges.includes('1y')}<button class:on={range === '1y'} onclick={() => (range = '1y')}>{t('1 anno')}</button>{/if}
							<button class:on={range === 'all'} onclick={() => (range = 'all')}>{t('Tutto')}</button>
						</div>
					</div>
				{/if}
				{#if points.length >= 2}
					<LineChart {points} {domain} />
				{:else}
					<div class="small muted center" style="padding:20px 0">{t('Dati non sufficienti per questo intervallo')}</div>
				{/if}
			</div>
		{/if}
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
