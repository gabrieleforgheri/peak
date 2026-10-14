<script lang="ts">
	import type { GlyphName, MuscleGroup } from '@common/docs.ts'
	import { photoUrl } from '../lib/photo.ts'
	import Glyph from './Glyph.svelte'

	let { ex, large = false }: { ex: { photo: string; photoOffset: number; glyphName: GlyphName; muscleGroup: MuscleGroup }; large?: boolean } = $props()
	let src = $state('')
	$effect(() => {
		const sha = ex.photo
		src = ''
		if (sha) photoUrl(sha).then((u) => { if (ex.photo === sha) src = u })
	})
	// lo spostamento sull'asse che sborda: la percentuale agisce solo dove c'è eccedenza
	const pos = $derived(`${50 - ex.photoOffset * 50}% ${50 - ex.photoOffset * 50}%`)
</script>

<div class="thumb" class:lg={large} style:background="color-mix(in srgb, var(--{ex.muscleGroup}) 20%, transparent)" style:color="var(--{ex.muscleGroup})">
	{#if ex.photo && src}
		<img {src} alt="" style:object-position={pos} />
	{:else}
		<Glyph name={ex.glyphName} />
	{/if}
</div>
