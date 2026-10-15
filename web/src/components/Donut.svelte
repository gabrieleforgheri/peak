<script lang="ts">
	/** Torta ad anello: una fetta per gruppo, nel colore del gruppo. Al centro, quello che passi. */
	let { parts, center = '', label = '' }: { parts: { color: string; value: number }[]; center?: string; label?: string } = $props()
	const R = 42, C = 2 * Math.PI * R
	const total = $derived(parts.reduce((s, p) => s + p.value, 0) || 1)
	const slices = $derived.by(() => {
		let at = 0
		return parts.map((p) => { const len = (p.value / total) * C; const s = { color: p.color, len, at }; at += len; return s })
	})
</script>

<svg viewBox="0 0 100 100" role="img" aria-label={label}>
	<circle cx="50" cy="50" r={R} fill="none" stroke="rgba(255,255,255,.06)" stroke-width="12" />
	{#each slices as s}
		<circle cx="50" cy="50" r={R} fill="none" stroke={s.color} stroke-width="12" stroke-linecap="butt"
			stroke-dasharray="{Math.max(0, s.len - 1.5)} {C}" stroke-dashoffset={-s.at} transform="rotate(-90 50 50)" />
	{/each}
	<text x="50" y="54" text-anchor="middle" font-size="15" font-weight="800" fill="currentColor">{center}</text>
</svg>
