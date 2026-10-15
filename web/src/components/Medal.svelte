<script lang="ts">
	import type { Medal } from '@common/medals.ts'
	import Icon from './Icon.svelte'

	let { medal, icon, title, sub }: { medal: Medal; icon: string; title: string; sub: string } = $props()
	const TIER = ['', 'bronze', 'silver', 'gold'] as const
	const deg = $derived(Math.round(medal.progress * 360))
</script>

<div class="medal" class:locked={medal.tier === 0} data-tier={TIER[medal.tier]}>
	<!-- l'anello si riempie verso il livello dopo; a oro preso è pieno -->
	<div class="ring" style="--deg:{deg}deg"><div class="disc"><Icon name={icon} size={26} /></div></div>
	<div class="name">{title}</div>
	<div class="sub">{sub}</div>
</div>

<style>
	.medal { flex: none; width: 104px; text-align: center; scroll-snap-align: start; }
	.ring { width: 76px; height: 76px; margin: 0 auto 8px; border-radius: 50%; padding: 4px; display: grid; place-items: center;
		background: conic-gradient(var(--tint) var(--deg), rgba(255,255,255,.1) 0); }
	.disc { width: 100%; height: 100%; border-radius: 50%; display: grid; place-items: center; color: var(--ink);
		background: radial-gradient(circle at 32% 26%, var(--hi), var(--lo)); box-shadow: inset 0 -3px 6px rgba(0,0,0,.25), 0 6px 16px var(--glow); }
	.medal[data-tier='bronze'] { --tint: #e0955a; --hi: #f1b27a; --lo: #a35f2a; --ink: #3a1d08; --glow: rgba(224,149,90,.35); }
	.medal[data-tier='silver'] { --tint: #dfe4ec; --hi: #f4f6fa; --lo: #98a1b1; --ink: #232a36; --glow: rgba(223,228,236,.3); }
	.medal[data-tier='gold'] { --tint: #ffd45e; --hi: #ffe69a; --lo: #d39a14; --ink: #3d2a00; --glow: rgba(255,212,94,.45); }
	.medal.locked { --tint: var(--accent); --hi: rgba(255,255,255,.08); --lo: rgba(255,255,255,.03); --ink: var(--text-3); --glow: transparent; }
	.name { font-weight: 700; font-size: 13px; line-height: 1.2; }
	.sub { font-size: 11px; color: var(--text-3); margin-top: 2px; }
</style>
