<script lang="ts">
	import { onMount } from 'svelte'
	import { reduced } from '../lib/fx.ts'

	/**
	 * Il grafico di un titolo che sale e si fa montagna, con la bandiera sulla vetta (circa 3 s, una volta). `still`: il
	 * fotogramma finale, senza movimento (anche con «riduci movimento»).
	 * La linea si disegna da JS (la testa luminosa la segue); fondo, neve e bandiera sono animazioni CSS con ritardo,
	 * e il CSS di base è già il fotogramma finale, quindi senza animazioni resta la montagna finita.
	 */
	let { still = false }: { still?: boolean } = $props()
	const play = !still && !reduced()

	type P = [number, number]
	type Seg = [P, P, P, P]
	/** Una curva morbida per i punti (Catmull-Rom scritta come Bézier cubiche); nei punti `corners` resta uno spigolo netto. */
	function smooth(pts: P[], corners: number[] = []): { d: string; segs: Seg[] } {
		const f = (n: number) => +n.toFixed(1)
		const segs: Seg[] = []
		let d = `M${pts[0]![0]} ${pts[0]![1]}`
		for (let i = 0; i < pts.length - 1; i++) {
			const a = pts[i]!, b = pts[i + 1]!
			const p0 = i === 0 || corners.includes(i) ? a : pts[i - 1]!
			const p3 = i + 2 >= pts.length || corners.includes(i + 1) ? b : pts[i + 2]!
			const c1: P = [f(a[0] + (b[0] - p0[0]) / 6), f(a[1] + (b[1] - p0[1]) / 6)]
			const c2: P = [f(b[0] - (p3[0] - a[0]) / 6), f(b[1] - (p3[1] - a[1]) / 6)]
			segs.push([a, c1, c2, b])
			d += ` C${c1[0]} ${c1[1]} ${c2[0]} ${c2[1]} ${b[0]} ${b[1]}`
		}
		return { d, segs }
	}
	const at = (s: Seg, t: number): P => {
		const u = 1 - t, w = [u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t]
		return [s.reduce((n, p, i) => n + p[0] * w[i]!, 0), s.reduce((n, p, i) => n + p[1] * w[i]!, 0)]
	}
	/** Il punto della curva alla quota `y` (la quota sale o scende sempre lungo il tratto). */
	function atY(s: Seg, y: number): P {
		let lo = 0, hi = 1
		const up = s[3][1] < s[0][1]
		for (let k = 0; k < 24; k++) { const m = (lo + hi) / 2; (at(s, m)[1] < y) === up ? (hi = m) : (lo = m) }
		return at(s, (lo + hi) / 2)
	}

	// un solo monte, visto da vicino: la vetta vicina al centro (un po' a destra) a un quarto dall'alto, due fianchi lunghi fino ai bordi,
	// una spalla piatta per lato e nessun zigzag. Dietro, una sagoma più grande e più pallida
	const SUMMIT: P = [192, 70]
	const ridge = smooth([[0, 238], [62, 194], [112, 170], SUMMIT, [240, 128], [286, 148], [360, 228]], [3])
	const range = smooth([[0, 216], [72, 158], [136, 104], [214, 134], [290, 160], [360, 196]])
	// la neve: solo la punta, con il bordo di sotto a zig zag
	const cap = (() => {
		const yb = SUMMIT[1] + 26
		const l = atY(ridge.segs[2]!, yb), r = atY(ridge.segs[3]!, yb), w = r[0] - l[0], f = (n: number) => +n.toFixed(1)
		return `M${SUMMIT[0]} ${SUMMIT[1]} L${f(l[0])} ${f(yb)} L${f(l[0] + w * 0.3)} ${f(yb - 3)} L${f(l[0] + w * 0.5)} ${f(yb + 2)} L${f(l[0] + w * 0.72)} ${f(yb - 3)} L${f(r[0])} ${f(yb)} Z`
	})()

	let path = $state<SVGPathElement>()
	let halo = $state<SVGPathElement>()
	let head = $state<SVGCircleElement>()

	onMount(() => {
		if (!play || !path || !halo || !head) return
		const len = path.getTotalLength()
		const T0 = 150, DUR = 1600
		const t0 = performance.now()
		let raf = 0
		const frame = (now: number) => {
			const k = Math.min(1, Math.max(0, (now - t0 - T0) / DUR))
			const p = k < 0.5 ? 4 * k * k * k : 1 - (-2 * k + 2) ** 3 / 2 // ease-in-out cubica
			path!.style.strokeDashoffset = halo!.style.strokeDashoffset = String(1 - p)
			const pt = path!.getPointAtLength(p * len)
			head!.setAttribute('cx', String(pt.x)); head!.setAttribute('cy', String(pt.y))
			head!.style.opacity = k > 0 && k < 1 ? '1' : '0'
			if (k < 1) raf = requestAnimationFrame(frame)
		}
		raf = requestAnimationFrame(frame)
		return () => cancelAnimationFrame(raf)
	})
</script>

<svg class="sd" class:play viewBox="0 0 360 260" aria-hidden="true">
	<defs>
		<linearGradient id="sd-line" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="360" y2="0"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity="0" /><stop offset=".35" stop-color="rgb(var(--accent-rgb))" stop-opacity=".7" /><stop offset=".65" stop-color="rgb(var(--accent-rgb))" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" /></linearGradient>
		<linearGradient id="sd-fill" gradientUnits="userSpaceOnUse" x1="0" y1="56" x2="0" y2="260"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".35" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity="0" /></linearGradient>
		<linearGradient id="sd-back" gradientUnits="userSpaceOnUse" x1="0" y1="100" x2="0" y2="230"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".12" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity="0" /></linearGradient>
		<radialGradient id="sd-halo"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".26" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity="0" /></radialGradient>
		<linearGradient id="sd-gh" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0" /><stop offset=".08" stop-color="#fff" /><stop offset=".92" stop-color="#fff" /><stop offset="1" stop-color="#fff" stop-opacity="0" /></linearGradient>
		<mask id="sd-mh" maskUnits="userSpaceOnUse" x="0" y="0" width="360" height="260"><rect x="0" y="0" width="360" height="260" fill="url(#sd-gh)" /></mask>
		<filter id="sd-glow" x="-10%" y="-40%" width="120%" height="180%"><feGaussianBlur stdDeviation="4" /></filter>
		<filter id="sd-soft" x="-10%" y="-40%" width="120%" height="180%"><feGaussianBlur stdDeviation="2.5" /></filter>
	</defs>

	<!-- i bordi a destra e a sinistra sfumano nello sfondo -->
	<g mask="url(#sd-mh)">
		<path class="back" d="{range.d} V260 H0Z" fill="url(#sd-back)" filter="url(#sd-soft)" />
		<path class="area" d="{ridge.d} V260 H0Z" fill="url(#sd-fill)" />
		<path class="snow" d={cap} />
		<path bind:this={halo} class="halo" d={ridge.d} pathLength="1" filter="url(#sd-glow)" style:stroke-dashoffset={play ? 1 : 0} />
		<path bind:this={path} class="line" d={ridge.d} pathLength="1" style:stroke-dashoffset={play ? 1 : 0} />
		<circle bind:this={head} class="head" r="3.5" cx="0" cy="238" />
	</g>

	<g transform="translate({SUMMIT[0]} {SUMMIT[1]})">
		<circle class="glow" cy="-14" r="40" fill="url(#sd-halo)" />
		<g class="flag">
			<path class="pole" d="M0 0 V-24" />
			<path class="cloth" d="M0 -24 L16 -18.5 L0 -13 Z" />
		</g>
	</g>
</svg>

<style>
	.sd { display: block; width: 100%; height: auto; overflow: visible; }
	.snow { fill: rgba(255, 255, 255, .72); }
	.halo { fill: none; stroke: rgb(var(--accent-rgb)); stroke-width: 5; stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 1 1; opacity: .35; }
	.line { fill: none; stroke: url(#sd-line); stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 1 1; }
	.head { fill: #fff; opacity: 0; filter: drop-shadow(0 0 5px rgb(var(--accent-rgb))); }
	.pole { fill: none; stroke: rgb(var(--accent-rgb)); stroke-width: 1.2; stroke-linecap: round; }
	.cloth { fill: rgb(var(--accent-rgb)); stroke: rgb(var(--accent-rgb)); stroke-width: 1; stroke-linejoin: round; transform-box: fill-box; transform-origin: 0 50%; animation: wave 1.8s ease-in-out infinite alternate; }
	.flag { transform-box: fill-box; transform-origin: 0 100%; }

	/* la scaletta: linea 0,15-1,75 s; riempimento, monti dietro, neve, alone e bandiera dopo; la bandiera ondeggia a regime */
	.play .area { transform-box: fill-box; transform-origin: 50% 100%; animation: grow 900ms var(--ease-out) 1700ms backwards; }
	.play .back { animation: fade 900ms ease-out 1850ms backwards; }
	.play .halo { animation: settle 800ms ease-out 1700ms backwards; }
	.play .snow { animation: fade 450ms ease-out 2250ms backwards; }
	.play .glow { animation: fade 800ms ease-out 2300ms backwards; }
	.play .flag { animation: spring 560ms cubic-bezier(.34, 1.56, .64, 1) 2350ms backwards; }
	.play .cloth { animation-delay: 2900ms; animation-fill-mode: backwards; }
	@keyframes grow { from { transform: scaleY(0); opacity: 0; } }
	@keyframes fade { from { opacity: 0; } }
	@keyframes settle { from { opacity: .6; } }
	@keyframes spring { from { transform: scale(0); } }
	@keyframes wave { from { transform: skewY(-4deg) scaleX(1); } to { transform: skewY(4deg) scaleX(.9); } }
</style>
