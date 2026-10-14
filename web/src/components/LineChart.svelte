<script lang="ts">
	import { tickIndices } from '@common/stats.ts'

	/** Un punto per misurazione, a passo fisso: saltare due settimane non disegna una riga piatta (come l'app iPhone). */
	let { points, domain, accent = 'var(--accent)', height = 160 }: {
		points: { y: number; label: string; faded?: boolean }[]; domain: [number, number]; accent?: string; height?: number
	} = $props()

	const W = 320, padL = 8, padR = 8, padT = 10, padB = 22
	const x = (i: number) => padL + (points.length < 2 ? (W - padL - padR) / 2 : (i * (W - padL - padR)) / (points.length - 1))
	const y = (v: number) => padT + (1 - (v - domain[0]) / Math.max(0.0001, domain[1] - domain[0])) * (height - padT - padB)
	const path = $derived(points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(p.y).toFixed(1)}`).join(' '))
	const ticks = $derived(tickIndices(points.length, 4))
</script>

<svg class="chart" viewBox="0 0 {W} {height}" role="img">
	{#each [0, 0.5, 1] as g}
		<line x1={padL} x2={W - padR} y1={padT + g * (height - padT - padB)} y2={padT + g * (height - padT - padB)} stroke="var(--stroke)" stroke-width="1" />
	{/each}
	<path d={path} fill="none" stroke={accent} stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" />
	{#each points as p, i}
		<circle cx={x(i)} cy={y(p.y)} r={p.faded ? 3 : 4.5} fill={accent} opacity={p.faded ? 0.5 : 1} />
	{/each}
	{#each ticks as i}
		<text x={Math.min(W - padR - 14, Math.max(padL + 14, x(i)))} y={height - 4} text-anchor="middle" font-size="10" fill="var(--text-3)">{points[i].label}</text>
	{/each}
</svg>
