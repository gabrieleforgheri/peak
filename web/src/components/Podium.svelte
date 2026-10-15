<script lang="ts">
	import Icon from './Icon.svelte'

	/** I primi tre sul podio (di meno, se siete in pochi): il primo al centro, il più alto. `you` = la tua riga. */
	let { rows, onpick }: { rows: { id: number; name: string; value: string; you: boolean }[]; onpick?: (id: number) => void } = $props()
	// ordine di disegno: secondo, primo, terzo
	const order = $derived([rows[1], rows[0], rows[2]].map((r, i) => (r ? { r, rank: [2, 1, 3][i] as 1 | 2 | 3 } : null)).filter((x) => x !== null))
	const H = { 1: 112, 2: 84, 3: 64 }
</script>

<div class="podium">
	{#each order as { r, rank } (r.id)}
		<button class="slot" data-rank={rank} class:you={r.you} onclick={() => !r.you && onpick?.(r.id)}>
			<div class="who"><div class="av">{r.name.replace(/^@/, '').slice(0, 1).toUpperCase()}</div><div class="nm">{#if r.you}<Icon name="star" size={12} />{/if} {r.name}</div><div class="val">{r.value}</div></div>
			<div class="ped" style="height:{H[rank]}px"><span>{rank}</span></div>
		</button>
	{/each}
</div>

<style>
	.podium { display: flex; align-items: flex-end; justify-content: center; gap: 10px; padding-top: 6px; }
	.slot { flex: 1; max-width: 120px; min-width: 0; border: 0; background: none; color: inherit; padding: 0; text-align: center; }
	.who { padding-bottom: 8px; }
	.av { width: 48px; height: 48px; margin: 0 auto 6px; border-radius: 50%; display: grid; place-items: center; font-weight: 800; font-size: 20px; color: #1a1a1a;
		background: radial-gradient(circle at 30% 25%, var(--hi), var(--lo)); box-shadow: 0 6px 18px var(--glow); }
	.nm { font-weight: 700; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.val { font-size: 13px; color: var(--text-2); font-variant-numeric: tabular-nums; }
	.ped { border-radius: 14px 14px 4px 4px; display: grid; place-items: start center; padding-top: 8px; font-weight: 900; font-size: 24px; color: rgba(0,0,0,.55);
		background: linear-gradient(to bottom, var(--hi), var(--lo)); }
	[data-rank='1'] { --hi: #ffe69a; --lo: #c9921a; --glow: rgba(255,212,94,.5); }
	[data-rank='2'] { --hi: #f4f6fa; --lo: #8f98a8; --glow: rgba(223,228,236,.35); }
	[data-rank='3'] { --hi: #f1b27a; --lo: #94562a; --glow: rgba(224,149,90,.35); }
	.you .nm { color: var(--accent); }
</style>
