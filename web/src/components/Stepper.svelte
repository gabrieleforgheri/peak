<script lang="ts">
	import { round2 } from '@common/domain.ts'
	import { locale } from '../lib/i18n.ts'

	let { value = $bindable(), step = 1, min = 0, max = 100000, unit = '', onchange }: {
		value: number; step?: number; min?: number; max?: number; unit?: string; onchange?: (v: number) => void
	} = $props()

	const clamp = (v: number) => Math.min(max, Math.max(min, round2(v)))
	const fmt = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)
	const set = (v: number) => { value = clamp(v); onchange?.(value) }
	let text = $state('')
	let editing = $state(false)
	const shown = $derived(editing ? text : fmt(value))

	function commit(e: Event) {
		editing = false
		const n = Number(String((e.target as HTMLInputElement).value).replace(',', '.'))
		if (Number.isFinite(n)) set(n)
	}
</script>

<div class="stepper">
	<button type="button" onclick={() => set(value - step)} aria-label="−">−</button>
	<div style="flex:1;min-width:0;text-align:center">
		<input class="val num" inputmode="decimal" value={shown} onfocus={(e) => { editing = true; text = String(value).replace('.', ','); (e.target as HTMLInputElement).select() }}
			oninput={(e) => (text = (e.target as HTMLInputElement).value)} onchange={commit} onblur={commit} />
		{#if unit}<div class="faint tiny" style="margin-top:-4px">{unit}</div>{/if}
	</div>
	<button type="button" onclick={() => set(value + step)} aria-label="+">+</button>
</div>
