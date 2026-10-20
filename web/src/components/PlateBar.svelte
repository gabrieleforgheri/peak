<script lang="ts">
	import { fly } from 'svelte/transition'
	import { loadPlates, round2, KG_PER_LB } from '@common/domain.ts'
	import { locale, t } from '../lib/i18n.ts'

	/** Il calcolatore dischi: mezzo bilanciere con i dischi che entrano a molla a ogni cambio di carico (PlateBarView.swift). */
	let { total, bar, plates, unit, delta = null }: { total: number; bar: number; plates: number[]; unit: 'kg' | 'lb'; delta?: number | null } = $props()

	const load = $derived(loadPlates(total, bar, plates))
	const relWeight = $derived(Math.max(0, total - bar))
	const perSideWeight = $derived(round2(relWeight / 2))
	const nf = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)
	const maxP = $derived(Math.max(...plates, 1))

	// colori olimpici sul peso in kg equivalente
	function color(p: number): string {
		const k = unit === 'lb' ? p * KG_PER_LB : p
		if (k >= 25) return 'rgb(219,51,51)'
		if (k >= 20) return 'rgb(51,107,230)'
		if (k >= 15) return 'rgb(242,199,51)'
		if (k >= 10) return 'rgb(64,179,89)'
		if (k >= 5) return 'rgb(235,235,235)'
		if (k >= 2.5) return 'rgb(219,77,77)'
		return 'rgb(166,166,166)'
	}
	const h = (p: number) => 34 + (p / maxP) * 46
</script>

{#snippet disc(p: number)}
	<div in:fly={{ x: 24, duration: 320 }} class="disc" style="width:{p >= 10 ? 15 : 10}px;height:{h(p)}px;background:{color(p)}"></div>
{/snippet}

<div class="card">
	<div class="row between" style="margin-bottom:8px">
		<div class="sec-title" style="margin:0">{t('Calcolatore dischi')}</div>
		{#if bar > 0}
			<span class="badge" style="font-weight:600">{t('Sbarra')} {nf(bar)} {unit}</span>
		{/if}
	</div>
	<div class="bar">
		<div class="end"></div>
		<div class="side">{#each [...load.perSide].reverse() as p, i (i + ':' + p)}{@render disc(p)}{/each}</div>
		<div class="collar"></div>
		<div class="shaft"></div>
		<div class="collar"></div>
		<div class="side">{#each load.perSide as p, i (i + ':' + p)}{@render disc(p)}{/each}</div>
		<div class="end"></div>
	</div>
	<div class="row between" style="margin-top:8px;align-items:baseline">
		<div class="num" style="font-weight:700">
			{#if load.perSide.length}
				{load.perSide.map(nf).join(' + ')} {unit} <span class="muted small" style="font-weight:500">({t('per lato')})</span>
			{:else}
				<span class="muted">{bar > 0 ? t('Solo la sbarra') : t('Nessun disco')}</span>
			{/if}
		</div>
		{#if delta !== null && delta !== 0}
			<span class="delta-tag" class:pos={delta > 0} class:neg={delta < 0}>
				{delta > 0 ? '+' : ''}{nf(delta)} {unit} ({delta > 0 ? '+' : ''}{nf(delta / 2)} {t('per lato')})
			</span>
		{/if}
	</div>

	<div class="row between wrap small muted" style="margin-top:8px;padding-top:8px;border-top:1px solid var(--stroke);gap:4px">
		{#if bar > 0}
			<span>{t('Relativo')}: <b class="num" style="color:var(--text)">{nf(relWeight)} {unit}</b> <span class="faint tiny">({nf(perSideWeight)} {t('per lato')})</span></span>
			<span>{t('Sbarra')}: <b class="num" style="color:var(--text)">{nf(bar)} {unit}</b></span>
			<span>{t('Assoluto')}: <b class="num" style="color:var(--accent)">{nf(total)} {unit}</b></span>
		{:else}
			<span>{t('Carico dischi')}: <b class="num" style="color:var(--accent)">{nf(total)} {unit}</b> <span class="faint tiny">({nf(total / 2)} {unit} {t('per lato')})</span></span>
		{/if}
	</div>

	{#if load.leftover > 0}<div class="small neg" style="margin-top:6px">{t('Mancano {0} {1} per lato con i dischi disponibili.', nf(load.leftover), unit)}</div>{/if}
</div>

<style>
	.bar { display: flex; align-items: center; justify-content: center; height: 92px; gap: 2px; }
	.end { width: 14px; height: 14px; background: linear-gradient(#cfd2d8, #8d9099); flex: none; border-radius: 3px; }
	.side { display: flex; align-items: center; gap: 2px; }
	.collar { width: 8px; height: 44px; background: #8d9099; border-radius: 2px; flex: none; }
	.shaft { height: 14px; flex: 1; min-width: 24px; background: linear-gradient(#cfd2d8, #8d9099); }
	.disc { border-radius: 4px; flex: none; box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .25); }
	.delta-tag { font-size: 12px; font-weight: 700; font-family: var(--font-num); padding: 2px 8px; border-radius: 6px; }
	.delta-tag.pos { background: rgba(50, 205, 50, 0.15); color: #32cd32; }
	.delta-tag.neg { background: rgba(255, 69, 0, 0.15); color: #ff6b6b; }
</style>
