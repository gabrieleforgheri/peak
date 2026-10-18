<script lang="ts">
	import { ANATOMY } from '@common/anatomy.ts'
	import { MUSCLE_INFO, type Muscle, type View } from '@common/muscle-info.ts'
	import { figureFor, zoomFor } from '@common/muscles.ts'
	import { t } from '../lib/i18n.ts'

	/** La figura dei muscoli: solo la zona che lavora, come nelle foto dei macchinari. Con `ontap` si toccano i muscoli. */
	let { muscles, view = '', zoom = true, ontap, label = '' }: {
		muscles: readonly Muscle[]; view?: '' | View; zoom?: boolean; ontap?: (m: Muscle) => void; label?: string
	} = $props()

	const GROUP_COLOR = { petto: '#ff7a6b', schiena: '#6cc4ff', gambe: '#a4e66f', spalle: '#ffd062', braccia: '#d6a0ff', core: '#7fefdc' } as const
	const fig = $derived(figureFor(muscles, view))
	const box = $derived(zoom ? zoomFor(muscles, fig.view) : [0, 0, 724, 1448])
	const hot = $derived(new Set(muscles))
	const names = $derived(muscles.map((m) => t(MUSCLE_INFO[m].it)).join(', '))
</script>

<svg
	class="fig"
	class:tap={!!ontap}
	viewBox="{box[0]} {box[1]} {box[2] - box[0]} {box[3] - box[1]}"
	role={ontap ? 'group' : 'img'}
	aria-label={label || names}
>
	<defs>
		<linearGradient id="fig-skin" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="724" y2="0">
			<stop offset="0" stop-color="#2f333a" /><stop offset=".3" stop-color="#7d838d" /><stop offset=".5" stop-color="#b4bac3" /><stop offset=".7" stop-color="#7d838d" /><stop offset="1" stop-color="#2f333a" />
		</linearGradient>
		<linearGradient id="fig-sheen" x1="0" y1="0" x2="0" y2="1">
			<stop offset="0" stop-color="#fff" stop-opacity=".3" /><stop offset=".55" stop-color="#fff" stop-opacity="0" /><stop offset="1" stop-color="#000" stop-opacity=".25" />
		</linearGradient>
		<filter id="fig-glow" x="-20%" y="-20%" width="140%" height="140%">
			<feGaussianBlur stdDeviation="9" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
		</filter>
	</defs>
	{#each ANATOMY[fig.view] as piece}
		{#if piece.muscle}
			{@const on = hot.has(piece.muscle)}
			{#each piece.paths as d}
				{#if on}<path {d} fill={GROUP_COLOR[MUSCLE_INFO[piece.muscle].group]} opacity=".7" filter="url(#fig-glow)" pointer-events="none" />{/if}
				<path
					{d}
					fill={on ? GROUP_COLOR[MUSCLE_INFO[piece.muscle].group] : '#4b5159'}
					opacity={on ? 1 : 0.55}
					stroke="#1c1f25"
					stroke-width="1.6"
					vector-effect="non-scaling-stroke"
					onclick={ontap ? () => ontap(piece.muscle as Muscle) : undefined}
					onkeydown={ontap ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); ontap(piece.muscle as Muscle) } } : undefined}
					role={ontap ? 'button' : undefined}
					aria-label={ontap ? t(MUSCLE_INFO[piece.muscle].it) : undefined}
				/>
				{#if on}<path {d} fill="url(#fig-sheen)" pointer-events="none" />{/if}
			{/each}
		{:else}
			{#each piece.paths as d}
				<path {d} fill="url(#fig-skin)" pointer-events="none" /><path {d} fill="url(#fig-sheen)" pointer-events="none" />
			{/each}
		{/if}
	{/each}
</svg>

<style>
	.fig { display: block; width: 100%; height: var(--fig-h, auto); max-height: var(--fig-max, none); }
	.tap path { cursor: pointer; }
</style>
