<script lang="ts">
	import { onMount } from 'svelte'
	import { reduced } from '../lib/fx.ts'

	/**
	 * Il grafico di un titolo in borsa che si disegna da solo e diventa la montagna di SummitHero, con la bandiera sulla vetta
	 * (circa 2,4 s, una volta). `still`: il fotogramma finale, senza movimento (anche con «riduci movimento»).
	 * La linea si disegna da JS (la testa luminosa la segue); fondo, neve e bandiera sono animazioni CSS con ritardo,
	 * e il CSS di base è già il fotogramma finale, quindi senza animazioni resta la montagna finita.
	 */
	let { still = false }: { still?: boolean } = $props()
	const play = !still && !reduced()

	// i massimali di una salita vera: su e giù, ma sempre più in alto, fino alla vetta (262, 22) e poi giù dall'altra parte
	const PTS: [number, number][] = [[0, 176], [18, 160], [34, 168], [52, 142], [68, 152], [88, 122], [104, 134], [126, 100], [140, 114], [160, 88], [176, 98], [196, 70], [210, 82], [232, 50], [244, 60], [262, 22], [278, 56], [292, 46], [312, 78], [326, 66], [344, 92], [360, 84]]
	const LINE = 'M' + PTS.map((p) => p.join(' ')).join(' L')

	let path = $state<SVGPathElement>()
	let head = $state<SVGCircleElement>()

	onMount(() => {
		if (!play || !path || !head) return
		const len = path.getTotalLength()
		const T0 = 150, DUR = 1200
		const t0 = performance.now()
		let raf = 0
		const frame = (now: number) => {
			const k = Math.min(1, Math.max(0, (now - t0 - T0) / DUR))
			const p = k < 0.5 ? 4 * k * k * k : 1 - (-2 * k + 2) ** 3 / 2 // ease-in-out cubica
			path!.style.strokeDashoffset = String(1 - p)
			const pt = path!.getPointAtLength(p * len)
			head!.setAttribute('cx', String(pt.x)); head!.setAttribute('cy', String(pt.y))
			head!.style.opacity = k > 0 && k < 1 ? '1' : '0'
			if (k < 1) raf = requestAnimationFrame(frame)
		}
		raf = requestAnimationFrame(frame)
		return () => cancelAnimationFrame(raf)
	})
</script>

<svg class="sd" class:play viewBox="0 -60 360 260" aria-hidden="true">
	<defs>
		<linearGradient id="sd-ridge" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".42" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity=".04" /></linearGradient>
		<linearGradient id="sd-gh" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0" /><stop offset=".07" stop-color="#fff" /><stop offset=".93" stop-color="#fff" /><stop offset="1" stop-color="#fff" stop-opacity="0" /></linearGradient>
		<linearGradient id="sd-gv" x1="0" y1="0" x2="0" y2="1"><stop offset=".76" stop-color="#fff" /><stop offset="1" stop-color="#fff" stop-opacity="0" /></linearGradient>
		<mask id="sd-mh" maskUnits="userSpaceOnUse" x="0" y="-60" width="360" height="260"><rect x="0" y="-60" width="360" height="260" fill="url(#sd-gh)" /></mask>
		<mask id="sd-mv" maskUnits="userSpaceOnUse" x="0" y="-60" width="360" height="260"><rect x="0" y="-60" width="360" height="260" fill="url(#sd-gv)" /></mask>
		<radialGradient id="sd-glow"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".4" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity="0" /></radialGradient>
	</defs>

	<!-- i bordi sfumano nello sfondo: a destra e a sinistra e in basso, sotto i monti -->
	<g mask="url(#sd-mh)"><g mask="url(#sd-mv)">
	<g class="grid"><path d="M0 60H360M0 110H360M0 160H360" /></g>
	<path class="back" d="M0 160 L40 138 L84 152 L130 112 L180 140 L232 96 L292 132 L332 108 L360 120 V200 H0Z" />
	<path class="area" d="{LINE} V200 H0Z" fill="url(#sd-ridge)" />
	<path class="snow" d="M262 22 L252.5 42 L258 37 L262 46 L267 38 L271.4 42 Z" />
	<path class="foot" d="M0 200 V188 L60 176 L130 190 L200 172 L262 186 L330 174 L360 180 V200Z" />
	<path bind:this={path} class="line" d={LINE} pathLength="1" style:stroke-dashoffset={play ? 1 : 0} />
	<circle bind:this={head} class="head" r="4.5" cx="0" cy="176" />
	</g></g>

	<g transform="translate(262 22)">
		<circle class="glow" cy="-8" r="52" fill="url(#sd-glow)" />
		<g class="flag">
			<path class="pole" d="M0 0 V-26" />
			<path class="cloth" d="M0 -26 L24 -18.5 L0 -11 Z" />
		</g>
	</g>
</svg>

<style>
	.sd { display: block; width: 100%; height: auto; overflow: visible; }
	.grid path { fill: none; stroke: rgba(255, 255, 255, .07); stroke-width: 1; stroke-dasharray: 3 5; }
	.back { fill: rgba(255, 255, 255, .05); }
	.snow { fill: rgba(255, 255, 255, .88); }
	.foot { fill: rgba(0, 0, 0, .38); }
	.line { fill: none; stroke: rgb(var(--accent-rgb)); stroke-width: 1.8; stroke-linejoin: round; stroke-linecap: round; stroke-dasharray: 1 1;
		filter: drop-shadow(0 0 3px rgba(var(--accent-rgb), .55)); }
	.head { fill: #fff; opacity: 0; filter: drop-shadow(0 0 6px rgb(var(--accent-rgb))); }
	.pole { fill: none; stroke: rgb(var(--accent-rgb)); stroke-width: 1.6; stroke-linecap: round; }
	.cloth { fill: rgb(var(--accent-rgb)); stroke: rgb(var(--accent-rgb)); stroke-width: 1.4; stroke-linejoin: round; transform-box: fill-box; transform-origin: 0 50%; animation: wave 1.6s ease-in-out infinite alternate; }
	.flag { transform-box: fill-box; transform-origin: 0 100%; }

	/* la scaletta: linea 0,15-1,35 s; riempimento, neve, vetta e bandiera dopo; la bandiera ondeggia a regime */
	.play .area { transform-box: fill-box; transform-origin: 50% 100%; animation: grow 800ms var(--ease-out) 1300ms backwards; }
	.play .back { animation: fade 700ms ease-out 1450ms backwards; }
	.play .foot { animation: fade 600ms ease-out 1600ms backwards; }
	.play .line { animation: ridge 700ms ease-out 1300ms backwards; }
	.play .snow { animation: fade 450ms ease-out 1850ms backwards; }
	.play .glow { animation: fade 700ms ease-out 1900ms backwards; }
	.play .flag { animation: spring 560ms cubic-bezier(.34, 1.56, .64, 1) 1950ms backwards; }
	.play .cloth { animation-delay: 2500ms; animation-fill-mode: backwards; }
	@keyframes grow { from { transform: scaleY(0); opacity: 0; } }
	@keyframes fade { from { opacity: 0; } }
	@keyframes ridge { from { stroke-width: 2.8; filter: drop-shadow(0 0 8px rgba(var(--accent-rgb), .95)); } }
	@keyframes spring { from { transform: scale(0); } }
	@keyframes wave { from { transform: skewY(-5deg) scaleX(1); } to { transform: skewY(5deg) scaleX(.88); } }
</style>
