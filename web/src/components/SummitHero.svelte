<script lang="ts">
	import { t, tn } from '../lib/i18n.ts'

	/** La prima cosa che vedi: una montagna e una frase che cambia. Sotto, i due numeri che contano di più. */
	let { greeting, name, streak, weekSessions, onavatar }: {
		greeting: string; name: string; streak: number; weekSessions: number; onavatar: () => void
	} = $props()

	const PHRASES = [
		t('Oggi è un buon giorno per battere il tuo record.'),
		t('Ogni vetta comincia con un primo passo.'),
		t('La costanza batte il talento.'),
		t('Il peso non si alza da solo.'),
		t('Niente scuse, solo serie.'),
		t('Sei più forte di ieri. Domani lo sarai di più.'),
		t('Fatica oggi, orgoglio domani.'),
		t('Un record nasce da una serie normale.'),
		t('Respira, spingi, ripeti.'),
		t('La vetta non si vede dal divano.'),
		t('Il tuo unico avversario è la scheda di ieri.'),
		t('Piccoli passi, grandi carichi.'),
		t('Ogni ripetizione conta.'),
		t('Chi si allena oggi ringrazia domani.'),
		t('Riposa se serve, ma non mollare.'),
		t('Un allenamento alla volta.'),
	]
	// una frase diversa ogni giorno, e ogni dieci secondi la successiva; chi non vuole movimento ne vede una sola
	const day = Math.floor((Date.now() - new Date().getTimezoneOffset() * 60_000) / 86_400_000)
	let i = $state(day % PHRASES.length)
	$effect(() => {
		if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
		const id = setInterval(() => { i = (i + 1) % PHRASES.length }, 10_000)
		return () => clearInterval(id)
	})
</script>

<section class="peak" data-tour="hero">
	<svg class="mount" viewBox="0 0 360 220" preserveAspectRatio="xMaxYMax slice" aria-hidden="true">
		<defs>
			<linearGradient id="ridge" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".38" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity=".04" /></linearGradient>
		</defs>
		<path d="M0 150 L40 122 L84 140 L132 92 L182 128 L232 72 L292 122 L332 96 L360 114 V220 H0Z" fill="rgba(255,255,255,.05)" />
		<path d="M0 182 L58 146 L112 164 L168 110 L214 142 L262 48 L304 104 L338 84 L360 96 V220 H0Z" fill="url(#ridge)" />
		<path d="M262 48 L246 78 L256 72 L262 82 L270 72 L282 80 Z" fill="rgba(255,255,255,.85)" />
		<path d="M0 220 V184 L70 170 L132 188 L202 160 L262 180 L330 164 L360 176 V220Z" fill="rgba(0,0,0,.38)" />
		<path d="M262 48 V26 L282 33 L262 40" fill="rgb(var(--accent-rgb))" stroke="rgb(var(--accent-rgb))" stroke-width="1.5" stroke-linejoin="round" />
	</svg>

	<div class="top">
		<div><div class="small muted">{greeting}</div><div class="who">{name}</div></div>
		<button class="avatar" onclick={onavatar} aria-label={t('Account yG')}>{name.slice(0, 1).toUpperCase()}</button>
	</div>

	<div class="quote">{#key i}<p>{PHRASES[i]}</p>{/key}</div>

	<div class="foot">
		<span class="pill"><b>{streak}</b> {t('settimane di fila')}</span>
		<span class="pill">{tn(weekSessions, '1 allenamento questa settimana', '{0} allenamenti questa settimana')}</span>
	</div>
</section>

<style>
	.peak { position: relative; overflow: hidden; border-radius: 28px; padding: 18px 18px 16px; min-height: 320px; display: flex; flex-direction: column;
		border: 1px solid rgba(var(--accent-rgb), .3);
		background: radial-gradient(circle 260px at 76% 12%, rgba(var(--accent-rgb), .35), transparent 70%), linear-gradient(180deg, rgba(var(--accent-rgb), .1), rgba(10, 14, 24, .9) 70%); }
	.mount { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
	.top { position: relative; display: flex; align-items: center; justify-content: space-between; }
	.who { font-size: 22px; font-weight: 800; letter-spacing: -0.02em; }
	.avatar { width: 40px; height: 40px; border-radius: 50%; border: 0; font-weight: 800; font-size: 17px; color: var(--on-accent); background: linear-gradient(135deg, var(--accent), rgba(var(--accent-rgb), .55)); box-shadow: 0 6px 18px rgba(var(--accent-rgb), .35); }
	.quote { position: relative; margin-top: 30px; max-width: 74%; min-height: 108px; }
	.quote p { margin: 0; font-size: 28px; font-weight: 900; letter-spacing: -0.03em; line-height: 1.08; text-shadow: 0 4px 30px rgba(var(--accent-rgb), .45); animation: in .7s cubic-bezier(.22, 1, .36, 1); }
	@keyframes in { from { opacity: 0; transform: translateY(10px); } }
	@media (prefers-reduced-motion: reduce) { .quote p { animation: none; } }
	.foot { position: relative; margin-top: auto; padding-top: 18px; display: flex; flex-wrap: wrap; gap: 8px; }
	.pill { padding: 6px 12px; border-radius: 999px; font-size: 12px; color: var(--text-2); background: rgba(0,0,0,.35); border: 1px solid rgba(255,255,255,.1); backdrop-filter: blur(6px); }
	.pill b { color: var(--accent); font-size: 14px; margin-right: 2px; }
</style>
