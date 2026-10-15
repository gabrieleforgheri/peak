<script lang="ts">
	import { climb } from '@common/altitude.ts'
	import { t, tn } from '../lib/i18n.ts'

	/** La prima cosa che vedi: la tua quota. Ogni 100 kg sollevati sono un metro di salita verso la prossima vetta. */
	let { volumeKg, greeting, name, streak, weekSessions, onavatar }: {
		volumeKg: number; greeting: string; name: string; streak: number; weekSessions: number; onavatar: () => void
	} = $props()

	const c = $derived(climb(volumeKg))
	const nf = new Intl.NumberFormat()

	// il numero sale da zero: una volta, in meno di un secondo (e fermo se hai chiesto meno movimento)
	let shown = $state(0)
	$effect(() => {
		const to = c.meters
		if (matchMedia('(prefers-reduced-motion: reduce)').matches) { shown = to; return }
		const from = shown, t0 = performance.now()
		let raf = requestAnimationFrame(function step(now) {
			const k = Math.min(1, (now - t0) / 900)
			shown = Math.round(from + (to - from) * (1 - (1 - k) ** 3))
			if (k < 1) raf = requestAnimationFrame(step)
		})
		return () => cancelAnimationFrame(raf)
	})

	// il sentiero: una curva dal campo base alla vetta; il puntino sta dove sei
	const P0 = [30, 178], P1 = [150, 158], P2 = [262, 48]
	const at = (k: number) => [0, 1].map((i) => (1 - k) ** 2 * P0[i]! + 2 * (1 - k) * k * P1[i]! + k ** 2 * P2[i]!) as [number, number]
	const dot = $derived(at(Math.min(1, Math.max(0.02, c.progress))))
</script>

<section class="peak">
	<svg class="mount" viewBox="0 0 360 220" preserveAspectRatio="xMaxYMax slice" aria-hidden="true">
		<defs>
			<linearGradient id="ridge" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".38" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity=".04" /></linearGradient>
		</defs>
		<path d="M0 150 L40 122 L84 140 L132 92 L182 128 L232 72 L292 122 L332 96 L360 114 V220 H0Z" fill="rgba(255,255,255,.05)" />
		<path d="M0 182 L58 146 L112 164 L168 110 L214 142 L262 48 L304 104 L338 84 L360 96 V220 H0Z" fill="url(#ridge)" />
		<path d="M262 48 L246 78 L256 72 L262 82 L270 72 L282 80 Z" fill="rgba(255,255,255,.85)" />
		<path d="M0 220 V184 L70 170 L132 188 L202 160 L262 180 L330 164 L360 176 V220Z" fill="rgba(0,0,0,.38)" />
		<path d="M{P0[0]} {P0[1]} Q {P1[0]} {P1[1]} {P2[0]} {P2[1]}" fill="none" stroke="rgba(255,255,255,.3)" stroke-width="1.5" stroke-dasharray="2 5" stroke-linecap="round" />
		<path d="M{P0[0]} {P0[1]} Q {P1[0]} {P1[1]} {P2[0]} {P2[1]}" pathLength="100" fill="none" stroke="rgb(var(--accent-rgb))" stroke-width="3" stroke-linecap="round" stroke-dasharray="{c.progress * 100} 100" />
		<path d="M262 48 V26 L282 33 L262 40" fill="rgb(var(--accent-rgb))" stroke="rgb(var(--accent-rgb))" stroke-width="1.5" stroke-linejoin="round" />
		<circle class="pulse" cx={dot[0]} cy={dot[1]} r="6" fill="rgb(var(--accent-rgb))" />
		<circle cx={dot[0]} cy={dot[1]} r="4.5" fill="#fff" stroke="rgb(var(--accent-rgb))" stroke-width="2" />
	</svg>

	<div class="top">
		<div><div class="small muted">{greeting}</div><div class="who">{name}</div></div>
		<button class="avatar" onclick={onavatar} aria-label={t('Account yG')}>{name.slice(0, 1).toUpperCase()}</button>
	</div>

	<div class="alt">
		<div class="kicker">{t('QUOTA RAGGIUNTA')}</div>
		<div class="num"><span>{nf.format(shown)}</span><small>m</small></div>
		<div class="goal">
			{#if c.next}{t('Prossima vetta: {0}', c.next.name)}<br /><b>{t('mancano {0} m', nf.format(c.missing))}</b> <span class="faint">· {nf.format(c.next.meters)} m</span>
			{:else}{t('Hai scalato ogni vetta.')}{/if}
		</div>
	</div>

	<div class="foot">
		<span class="pill"><b>{streak}</b> {t('settimane di fila')}</span>
		<span class="pill">{tn(weekSessions, '1 allenamento questa settimana', '{0} allenamenti questa settimana')}</span>
	</div>
	<div class="rule">{t('1 m ogni 100 kg sollevati')}{c.last ? ' · ' + t('ultima vetta: {0}', c.last.name) : ''}</div>
</section>

<style>
	.peak { position: relative; overflow: hidden; border-radius: 28px; padding: 18px 18px 14px; min-height: 330px; display: flex; flex-direction: column;
		border: 1px solid rgba(var(--accent-rgb), .3);
		background: radial-gradient(circle 260px at 76% 12%, rgba(var(--accent-rgb), .35), transparent 70%), linear-gradient(180deg, rgba(var(--accent-rgb), .1), rgba(10, 14, 24, .9) 70%); }
	.mount { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
	.pulse { opacity: .35; transform-box: fill-box; transform-origin: center; animation: ping 2s ease-out infinite; }
	@keyframes ping { 0% { transform: scale(.8); opacity: .55 } 100% { transform: scale(3); opacity: 0 } }
	@media (prefers-reduced-motion: reduce) { .pulse { animation: none } }
	.top { position: relative; display: flex; align-items: center; justify-content: space-between; }
	.who { font-size: 22px; font-weight: 800; letter-spacing: -0.02em; }
	.avatar { width: 40px; height: 40px; border-radius: 50%; border: 0; font-weight: 800; font-size: 17px; color: var(--on-accent); background: linear-gradient(135deg, var(--accent), rgba(var(--accent-rgb), .55)); box-shadow: 0 6px 18px rgba(var(--accent-rgb), .35); }
	.alt { position: relative; margin-top: 26px; max-width: 62%; }
	.kicker { font-size: 11px; font-weight: 800; letter-spacing: .14em; color: var(--accent); }
	.num { font-weight: 900; letter-spacing: -0.04em; line-height: 1; font-size: 62px; font-variant-numeric: tabular-nums; text-shadow: 0 4px 30px rgba(var(--accent-rgb), .4); }
	.num small { font-size: 26px; font-weight: 800; margin-left: 4px; color: var(--accent); letter-spacing: 0; }
	.goal { margin-top: 8px; font-size: 14px; color: var(--text-2); line-height: 1.35; text-shadow: 0 1px 12px rgba(0,0,0,.7); }
	.goal b { color: var(--text); }
	.foot { position: relative; margin-top: auto; padding-top: 18px; display: flex; flex-wrap: wrap; gap: 8px; }
	.pill { padding: 6px 12px; border-radius: 999px; font-size: 12px; color: var(--text-2); background: rgba(0,0,0,.35); border: 1px solid rgba(255,255,255,.1); backdrop-filter: blur(6px); }
	.pill b { color: var(--accent); font-size: 14px; margin-right: 2px; }
	.rule { position: relative; margin-top: 10px; font-size: 11px; color: var(--text-3); }
</style>
