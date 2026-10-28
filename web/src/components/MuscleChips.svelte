<script lang="ts">
	import { MUSCLE_INFO, type Muscle } from '@common/muscle-info.ts'
	import { lang } from '../lib/i18n.ts'

	/** I muscoli come targhette col colore del loro gruppo. */
	let { muscles }: { muscles: readonly Muscle[] } = $props()
</script>

<div class="row wrap" style="gap:8px">
	{#each muscles as m}
		<div class="muscle-chip" style="border-color:color-mix(in srgb, var(--{MUSCLE_INFO[m].group}) 45%, transparent);background:color-mix(in srgb, var(--{MUSCLE_INFO[m].group}) 20%, transparent)">
			<span class="dot" style="background:var(--{MUSCLE_INFO[m].group})"></span>
			<span style="font-weight:700;font-size:13px;color:var(--text)">{lang === 'it' ? MUSCLE_INFO[m].it : MUSCLE_INFO[m].en}</span>
		</div>
	{/each}
</div>

<style>
	.muscle-chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 5px 10px;
		border-radius: 8px;
		border-width: 1px;
		border-style: solid;
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		flex-shrink: 0;
	}
</style>
