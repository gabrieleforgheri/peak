<script lang="ts">
	import { round2 } from '@common/domain.ts'
	import { locale } from '../lib/i18n.ts'

	let { value = $bindable(), step = 1, min = 0, max = 100000, unit = '', compact = false, integer = false, onchange }: {
		value: number; step?: number; min?: number; max?: number; unit?: string; compact?: boolean; integer?: boolean; onchange?: (v: number) => void
	} = $props()

	const clamp = (v: number) => Math.min(max, Math.max(min, integer ? Math.round(v) : round2(v)))
	const fmt = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(v)
	// da modificare: stesso separatore decimale di quello mostrato, senza separatore delle migliaia (1,000 diventerebbe 1)
	const plain = (v: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 2, useGrouping: false }).format(v)
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

<div class="stepper" class:compact>
	<button type="button" onclick={() => set(value - step)} aria-label="−">−</button>
	<div style="flex:1;min-width:0;text-align:center">
		<input class="val num" inputmode={integer ? 'numeric' : 'decimal'} value={shown} onfocus={(e) => { const el = e.target as HTMLInputElement; editing = true; text = plain(value); el.value = text; el.select() }}
			oninput={(e) => (text = (e.target as HTMLInputElement).value)} onchange={commit} onblur={commit} />
		{#if unit}<div class="faint tiny" style="margin-top:-4px">{unit}</div>{/if}
	</div>
	<button type="button" onclick={() => set(value + step)} aria-label="+">+</button>
</div>
