<script lang="ts">
	// «2 br | 1 br» (gambe «2 gb | 1 gb»): una pillola piccola accanto al titolo del peso; `value` è il flag singleArm
	import { sideText } from '../lib/fmt.ts'

	let { value = $bindable(false), group }: { value: boolean; group?: string | null } = $props()
</script>

<div class="side-seg" role="group" aria-label={sideText(group, 'long')}>
	<button type="button" class:on={!value} aria-pressed={!value} onclick={() => (value = false)}>{sideText(group, 'two')}</button>
	<button type="button" class:on={value} aria-pressed={value} onclick={() => (value = true)}>{sideText(group, 'one')}</button>
</div>

<style>
	.side-seg { display: inline-flex; padding: 2px; border-radius: 9px; background: var(--card-strong); }
	button { border: 0; border-radius: 7px; padding: 4px 9px; background: none; color: var(--text-3); font: 700 12px var(--font); }
	button.on { background: rgba(255, 255, 255, 0.16); color: var(--text); }
</style>
