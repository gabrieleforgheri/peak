<script lang="ts">
	import { tickIndices } from '@common/stats.ts'
	import { inview } from '../lib/fx.ts'

	/** Un punto per misurazione, a passo fisso: saltare due settimane non disegna una riga piatta (come l'app iPhone). */
	let { points, domain, accent = 'var(--accent)', height = 160 }: {
		points: { y: number; label: string; faded?: boolean }[]; domain: [number, number]; accent?: string; height?: number
	} = $props()

	const padL = 8, padR = 8, padT = 10, padB = 22
	// larghezza vera del grafico: fino a 320 resta il disegno di sempre (scalato), sopra si allarga invece di ingrandire testi e punti
	let box = $state(0)
	const W = $derived(Math.max(320, box))
	const x = (i: number) => padL + (points.length < 2 ? (W - padL - padR) / 2 : (i * (W - padL - padR)) / (points.length - 1))
	const y = (v: number) => padT + (1 - (v - domain[0]) / Math.max(0.0001, domain[1] - domain[0])) * (height - padT - padB)
	const path = $derived(points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(p.y).toFixed(1)}`).join(' '))
	const ticks = $derived(tickIndices(points.length, 4))
</script>

<div bind:clientWidth={box} use:inview>
<svg class="chart" viewBox="0 0 {W} {height}" role="img">
	{#each [0, 0.5, 1] as g}
		<line x1={padL} x2={W - padR} y1={padT + g * (height - padT - padB)} y2={padT + g * (height - padT - padB)} stroke="var(--stroke)" stroke-width="1" />
	{/each}
	<path class="line" d={path} pathLength="1" fill="none" stroke={accent} stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" />
	<g class="dots">
	{#each points as p, i}
		<circle cx={x(i)} cy={y(p.y)} r={p.faded ? 3 : 4.5} fill={accent} opacity={p.faded ? 0.5 : 1} />
	{/each}
	</g>
	{#each ticks as i}
		<text x={Math.min(W - padR - 14, Math.max(padL + 14, x(i)))} y={height - 4} text-anchor="middle" font-size="10" fill="var(--text-3)">{points[i].label}</text>
	{/each}
</svg>
</div>

<style>
	/* la linea si disegna da sinistra (tratteggio lungo quanto tutto il percorso, pathLength=1) la prima volta che il grafico si vede, poi compaiono i punti;
	   se il dato cambia a grafico già visto, la linea resta intera e si aggiorna */
	.line { stroke-dasharray: 1 2; transition: stroke-dashoffset var(--dur-4) var(--ease-out); }
	.dots { transition: opacity var(--dur-2) var(--ease-out) 380ms; }
	:global([data-in='0']) .line { stroke-dashoffset: 1.02; }
	:global([data-in='0']) .dots { opacity: 0; }
</style>
