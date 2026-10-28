<script lang="ts">
	import { onMount } from 'svelte'
	import type { Medal } from '@common/medals.ts'
	import { EASE_OUT, reduced } from '../lib/fx.ts'
	import Icon from './Icon.svelte'

	/** `progress`: quanto dell'anello è pieno (0-1); `sweep`: all'apertura l'anello parte da vuoto e si riempie. */
	let { medal, icon, size = 52, progress, sweep = false }: { medal: Medal; icon: string; size?: number; progress: number; sweep?: boolean } = $props()
	const TIER = ['', 'bronze', 'silver', 'gold'] as const
	let arc: SVGCircleElement
	onMount(() => {
		if (sweep && !reduced() && progress > 0) arc.animate([{ strokeDashoffset: 1 }, { strokeDashoffset: 1 - progress }], { duration: 700, delay: 120, easing: EASE_OUT, fill: 'backwards' })
	})
</script>

<span class="medal" class:locked={medal.tier === 0} data-tier={TIER[medal.tier]} style="--s:{size}px" aria-hidden="true">
	<!-- l'anello sottile si riempie verso il livello dopo; a oro preso è pieno (pathLength=1: il tratto va da 0 a 1) -->
	<svg viewBox="0 0 100 100">
		<circle class="track" cx="50" cy="50" r="46" />
		<circle class="arc" bind:this={arc} cx="50" cy="50" r="46" pathLength="1" stroke-dasharray="1" stroke-dashoffset={1 - progress} />
	</svg>
	<span class="disc"><Icon name={icon} size={Math.round(size * 0.42)} /></span>
</span>

<style>
	.medal { position: relative; flex: none; width: var(--s); height: var(--s); display: block; }
	svg { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
	circle { fill: none; stroke-width: 4.5; }
	.track { stroke: rgba(255,255,255,.1); }
	.arc { stroke: var(--tint); }
	.disc { position: absolute; inset: 11%; border-radius: 50%; display: grid; place-items: center; color: var(--ink);
		background: radial-gradient(circle at 32% 26%, var(--hi), var(--lo)); box-shadow: inset 0 -2px 5px rgba(0,0,0,.25), 0 4px 12px var(--glow); }
	.medal[data-tier='bronze'] { --tint: var(--bronze); --hi: #f1b27a; --lo: #a35f2a; --ink: #3a1d08; --glow: rgba(224,149,90,.3); }
	.medal[data-tier='silver'] { --tint: var(--silver); --hi: #f4f6fa; --lo: #98a1b1; --ink: #232a36; --glow: rgba(223,228,236,.25); }
	.medal[data-tier='gold'] { --tint: var(--gold); --hi: #ffe69a; --lo: #d39a14; --ink: #3d2a00; --glow: rgba(255,212,94,.4); }
	.medal.locked { --tint: var(--accent); --hi: rgba(255,255,255,.08); --lo: rgba(255,255,255,.03); --ink: var(--text-3); --glow: transparent; }
</style>
