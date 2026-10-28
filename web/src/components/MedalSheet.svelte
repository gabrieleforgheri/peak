<script lang="ts">
	import type { Medal as MedalT } from '@common/medals.ts'
	import { dateLong } from '../lib/fmt.ts'
	import { countUp } from '../lib/fx.ts'
	import { t } from '../lib/i18n.ts'
	import { MEDAL, MEDAL_INFO, TIERS, fmtMedal, medalDates, medalFrac } from '../lib/medalinfo.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import Medal from './Medal.svelte'
	import Sheet from './Sheet.svelte'

	let { md, friends, onclose }: { md: MedalT; friends: number; onclose: () => void } = $props()

	// quando hai preso ogni livello: dalla storia, una volta per modello
	const dates = $derived(medalDates(getModel(), friends)[md.id])
	const TIER = ['bronze', 'silver', 'gold'] as const
	/** Quanto di ogni tratto del percorso è fatto: il tratto verso il bronzo parte da 0, gli altri dalla soglia sotto. */
	const fill = (i: number): number => {
		const from = i === 0 ? 0 : md.tiers[i - 1]!
		return Math.max(0, Math.min(1, (md.value - from) / (md.tiers[i]! - from)))
	}
	const nextLine = $derived(md.next === null ? t('Oro conquistato.') : t('Mancano {0} per {1}.', fmtMedal(md.id, md.next - md.value), TIERS[md.tier + 1]))
</script>

<Sheet title={MEDAL[md.id].title} {onclose}>
	<div class="hero">
		<p class="small muted info">{MEDAL_INFO[md.id]}</p>
		<Medal medal={md} icon={MEDAL[md.id].icon} size={112} progress={medalFrac(md)} sweep />
		<div class="big num" use:countUp={{ value: md.value, from: 0, delay: 120, format: (n) => fmtMedal(md.id, n) }}></div>
		<div class="tier" data-tier={TIER[md.tier - 1]}>{TIERS[md.tier]}</div>
		<div class="small muted">{nextLine}</div>
	</div>

	<!-- il percorso: dal basso (zero) in su, bronzo, argento, oro; il tratto si riempie fino al valore di adesso -->
	<ol class="path">
		{#each [2, 1, 0] as i (i)}
			<li data-tier={TIER[i]} class:on={md.tier > i} style="--k:{i}">
				<span class="trk"><i style="transform:scaleY({fill(i)})"></i></span>
				<span class="node"></span>
				<div class="pname"><b>{TIERS[i + 1]}</b><span class="small muted num">{fmtMedal(md.id, md.tiers[i]!)}</span></div>
				{#if md.tier > i && dates[i]}<span class="small muted when">{dateLong(dates[i]!)}</span>{/if}
			</li>
		{/each}
	</ol>
</Sheet>

<style>
	.hero { display: flex; flex-direction: column; align-items: center; gap: 4px; text-align: center; padding: 0 0 18px; }
	.info { margin: 0 0 12px; max-width: 30ch; }
	.big { font-size: 34px; font-weight: 800; letter-spacing: -0.02em; line-height: 1.1; margin-top: 12px; }
	.tier { font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; color: var(--tint, var(--text-3)); }
	.tier[data-tier='bronze'] { --tint: var(--bronze); }
	.tier[data-tier='silver'] { --tint: var(--silver); }
	.tier[data-tier='gold'] { --tint: var(--gold); }

	.path { list-style: none; margin: 0; padding: 0 4px; }
	li { position: relative; display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; column-gap: 10px; align-items: start; min-height: 72px; opacity: .55; }
	li.on { opacity: 1; }
	li[data-tier='bronze'] { --tint: var(--bronze); }
	li[data-tier='silver'] { --tint: var(--silver); }
	li[data-tier='gold'] { --tint: var(--gold); }
	.node { grid-column: 1; grid-row: 1; justify-self: center; margin-top: 4px; width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--stroke-strong); box-sizing: border-box; }
	li.on .node { background: var(--tint); border-color: var(--tint); box-shadow: 0 0 12px color-mix(in srgb, var(--tint) 50%, transparent); animation: lit 250ms var(--ease-out) backwards; animation-delay: calc(var(--k) * 200ms + 450ms); }
	/* il tratto sotto il nodo, fino al nodo di sotto */
	.trk { position: absolute; left: 12.5px; top: 20px; bottom: -4px; width: 3px; border-radius: 2px; background: var(--stroke); overflow: hidden; }
	li:last-child .trk { bottom: 0; }
	.trk i { display: block; height: 100%; background: var(--tint); transform-origin: bottom; animation: fillup 400ms var(--ease-out) backwards; animation-delay: calc(var(--k) * 200ms + 150ms); }
	.pname { grid-column: 2; display: flex; flex-direction: column; line-height: 1.3; padding-top: 1px; }
	.pname b { font-size: 15px; }
	li.on .pname b { color: var(--tint); }
	.when { grid-column: 3; padding-top: 3px; text-align: right; }
	@keyframes fillup { from { transform: scaleY(0); } }
	@keyframes lit { from { background: transparent; border-color: var(--stroke-strong); box-shadow: none; } }
</style>
