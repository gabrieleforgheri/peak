<script lang="ts">
	import type { MuscleGroup } from '@common/docs.ts'
	import type { Muscle, View } from '@common/muscle-info.ts'
	import MuscleFigure from './MuscleFigure.svelte'

	/** La figura di un esercizio nel riquadro, col colore del suo gruppo: solo la zona che lavora. */
	let { muscles, view = '', group, size = 56, zoom = true }: {
		muscles: Muscle[]; view?: '' | View; group: MuscleGroup; size?: number; zoom?: boolean
	} = $props()
</script>

<div class="fig-thumb" style:width="{size}px" style:height="{size}px" style:background="color-mix(in srgb, var(--{group}) 20%, transparent)">
	<MuscleFigure {muscles} {view} {zoom} />
</div>

<style>
	.fig-thumb { border-radius: 14px; overflow: hidden; flex: none; padding: 4px; display: grid; place-items: center; }
</style>
