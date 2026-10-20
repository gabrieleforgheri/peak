<script lang="ts">
	import type { GlyphName, MuscleGroup } from '@common/docs.ts'
	import { photoUrl } from '../lib/photo.ts'
	import Glyph from './Glyph.svelte'

	let { ex }: { ex: { photo?: string; glyphName?: GlyphName | null; muscleGroup?: MuscleGroup } } = $props()
	let src = $state('')
	$effect(() => {
		const sha = ex.photo
		src = ''
		if (sha) photoUrl(sha).then((u) => { if (ex.photo === sha) src = u })
	})
</script>

<div class="card-bg-wrap" aria-hidden="true">
	{#if ex.photo && src}
		<div class="card-bg-photo" style:background-image="url({src})"></div>
	{:else if ex.glyphName}
		<div class="card-bg-glyph" style:color="var(--{ex.muscleGroup || 'accent'})">
			<Glyph name={ex.glyphName} />
		</div>
	{/if}
	<div class="card-bg-scrim"></div>
</div>

<style>
	.card-bg-wrap {
		position: absolute;
		inset: 0;
		overflow: hidden;
		pointer-events: none;
		border-radius: inherit;
		z-index: 0;
	}
	.card-bg-photo {
		position: absolute;
		inset: -15px;
		background-size: cover;
		background-position: center 35%;
		filter: blur(10px) brightness(0.65) saturate(1.2);
		opacity: 0.65;
		transform: scale(1.08);
	}
	.card-bg-glyph {
		position: absolute;
		right: 0;
		bottom: -15px;
		width: 210px;
		height: 210px;
		opacity: 0.28;
		filter: blur(1.5px);
	}
	.card-bg-glyph :global(svg) {
		width: 100%;
		height: 100%;
	}
	.card-bg-scrim {
		position: absolute;
		inset: 0;
		background: linear-gradient(90deg, rgba(16, 18, 24, 0.90) 0%, rgba(16, 18, 24, 0.65) 55%, rgba(14, 16, 20, 0.40) 100%);
	}
</style>
