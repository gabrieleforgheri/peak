<script lang="ts" generics="T">
	import type { Snippet } from 'svelte'
	import { byMonth } from '@common/format.ts'
	import { monthYear } from '../lib/fmt.ts'
	import { disclosure } from '../lib/fx.ts'
	import Icon from './Icon.svelte'

	/* Storico raggruppato per mese (dal più recente): il primo aperto, gli altri richiusi. `items` va passato dal più recente. */
	let { items, dateOf, count, body = 'card list', row, open = 1 }: {
		items: T[]; dateOf: (item: T) => number; count: (n: number) => string
		/** classe del contenitore delle righe */
		body?: string
		row: Snippet<[T]>
		/** quanti mesi partono aperti */
		open?: number
	} = $props()
	const groups = $derived(byMonth(items, dateOf))
</script>

{#each groups as g, i (g.key)}
	<details class="month" open={i < open} use:disclosure>
		<summary><Icon name="right" size={16} /><span class="grow">{monthYear(g.ms)}</span><span class="muted num">{count(g.items.length)}</span></summary>
		<div class={body}>{#each g.items as it}{@render row(it)}{/each}</div>
	</details>
{/each}

<style>
	.month + .month { margin-top: 4px; }
	summary { display: flex; align-items: center; gap: 8px; padding: 10px 4px; cursor: pointer; list-style: none; font-size: 13px; font-weight: 700; color: var(--text-2); user-select: none; }
	summary::-webkit-details-marker { display: none; }
	summary > .grow { flex: 1; text-transform: capitalize; }
	summary > .muted { font-weight: 500; font-size: 12px; }
	summary > :global(svg) { transition: transform var(--dur-2) var(--ease-out); }
	/* .closing lo mette fx.disclosure mentre l'altezza si chiude: la freccia torna subito, non a fine corsa */
	details[open]:not(:global(.closing)) > summary > :global(svg) { transform: rotate(90deg); }
	summary:active { opacity: 0.7; }
	@media (prefers-reduced-motion: reduce) { summary > :global(svg) { transition: none; } }
</style>
