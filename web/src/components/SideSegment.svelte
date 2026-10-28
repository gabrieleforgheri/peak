<script lang="ts">
	// «2 br | 1 br» (gambe «2 gb | 1 gb»): una pillola piccola accanto al titolo del peso; `value` è il flag singleArm
	import { sideText } from '../lib/fmt.ts'

	let { value = $bindable(false), group, onchange }: { value: boolean; group?: string | null; onchange?: (v: boolean) => void } = $props()
	// solo il tocco dell'utente passa di qui: chi lo ascolta può reagire (il peso si dimezza), chi imposta `value` da fuori no
	const pick = (v: boolean) => { if (v === value) return; value = v; onchange?.(v) }
</script>

<div class="side-seg" role="group" aria-label={sideText(group, 'long')}>
	<button type="button" class:on={!value} aria-pressed={!value} onclick={() => pick(false)}>{sideText(group, 'two')}</button>
	<button type="button" class:on={value} aria-pressed={value} onclick={() => pick(true)}>{sideText(group, 'one')}</button>
</div>

<style>
	.side-seg { display: inline-flex; padding: 2px; border-radius: 9px; background: var(--card-strong); }
	button { border: 0; border-radius: 7px; padding: 4px 9px; background: none; color: var(--text-3); font: 700 12px var(--font); transition: background-color var(--dur-2) var(--ease-out), color var(--dur-2) var(--ease-out), transform var(--dur-1) var(--ease-out); }
	button:active { transform: scale(0.96); }
	button.on { background: rgba(255, 255, 255, 0.16); color: var(--text); }
</style>
