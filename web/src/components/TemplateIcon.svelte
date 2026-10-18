<script lang="ts">
	import type { GlyphName, MuscleGroup, TemplateIcon } from '@common/docs.ts'
	import { suggestTemplateIcon } from '@common/template-icon.ts'
	import Glyph from './Glyph.svelte'
	import Icon from './Icon.svelte'

	/** L'icona di una scheda: quella scelta, altrimenti quella che dice il nome, altrimenti il manubrio. */
	let { icon = '', name = '', size = 20 }: { icon?: '' | TemplateIcon; name?: string; size?: number } = $props()
	const shown = $derived(icon || suggestTemplateIcon(name) || 'dumbbell')
	const group = $derived(shown.startsWith('muscle:') ? (shown.slice(7) as MuscleGroup) : null)
	// il disegno di ogni gruppo: il bicipite (Lucide) per le braccia, gli altri da game-icons
	const OF_GROUP: Record<MuscleGroup, string> = { petto: 'torso', schiena: 'flex', gambe: 'leg', spalle: 'shoulders', braccia: 'g-braccia', core: 'abs' }
</script>

{#if group}
	<span style="color:var(--{group});display:inline-grid"><Icon name={OF_GROUP[group]} {size} /></span>
{:else if shown.startsWith('body:')}
	<Icon name={shown.slice(5)} {size} />
{:else if shown.startsWith('glyph:')}
	<span class="fig" style="width:{size}px;height:{size}px"><Glyph name={shown.slice(6) as GlyphName} /></span>
{:else}
	<Icon name={shown} {size} />
{/if}

<style>
	.fig { display: inline-grid; place-items: center; }
	.fig :global(svg) { width: 100%; height: 100%; }
</style>
