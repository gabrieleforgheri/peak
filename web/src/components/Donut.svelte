<script lang="ts">
	import { inview } from '../lib/fx.ts'

	/** Torta ad anello: una fetta per gruppo, nel colore del gruppo. Al centro, quello che passi. */
	let { parts, center = '', label = '' }: { parts: { color: string; value: number }[]; center?: string; label?: string } = $props()
	const R = 42, C = 2 * Math.PI * R
	const total = $derived(parts.reduce((s, p) => s + p.value, 0) || 1)
	const slices = $derived.by(() => {
		let at = 0
		return parts.map((p) => { const len = (p.value / total) * C; const s = { color: p.color, len, at }; at += len; return s })
	})
	const uid = $props.id()
</script>

<svg viewBox="0 0 100 100" role="img" aria-label={label} use:inview>
	<!-- la maschera è un anello bianco che si disegna in senso orario dalle 12: le fette compaiono man mano che passa -->
	<mask id="sweep-{uid}" maskUnits="userSpaceOnUse" x="0" y="0" width="100" height="100">
		<circle class="sweep" cx="50" cy="50" r={R} fill="none" stroke="#fff" stroke-width="14" pathLength="1" transform="rotate(-90 50 50)" />
	</mask>
	<circle cx="50" cy="50" r={R} fill="none" stroke="rgba(255,255,255,.06)" stroke-width="12" />
	<g mask="url(#sweep-{uid})">
		{#each slices as s}
			<circle cx="50" cy="50" r={R} fill="none" stroke={s.color} stroke-width="12" stroke-linecap="butt"
				stroke-dasharray="{Math.max(0, s.len - 1.5)} {C}" stroke-dashoffset={-s.at} transform="rotate(-90 50 50)" />
		{/each}
	</g>
	<text x="50" y="54" text-anchor="middle" font-size="15" font-weight="800" fill="currentColor">{center}</text>
</svg>

<style>
	.sweep { stroke-dasharray: 1 2; transition: stroke-dashoffset var(--dur-4) var(--ease-out); }
	:global([data-in='0']) .sweep { stroke-dashoffset: 1.02; }
</style>
