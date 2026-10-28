<script lang="ts">
	import { t, tn } from '../lib/i18n.ts'
	import { countUp } from '../lib/fx.ts'

	/** La prima cosa che vedi: una montagna e una frase che cambia. Sotto, i due numeri che contano di più.
	 *  `roll`: la serie di settimane sale da `from` (festa dopo un allenamento arrivato dalla sincronizzazione), dopo `delay` ms. */
	let { greeting, name, streak, weekSessions, goal, roll, onavatar, onchangegoal }: {
		greeting: string; name: string; streak: number; weekSessions: number; goal: number; roll?: { from: number; delay: number }; onavatar: () => void; onchangegoal: () => void
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
		t('Costruisci oggi, quello che sarai domani.'),
		t('La disciplina è la via, per ogni vetta.'),
		t('Non contare i giorni, fai contare i giorni.'),
		t('La forza cresce, quando pensi di non farcela.'),
		t('Focalizzati sull\'obiettivo, dimentica il rumore.'),
		t('Ogni goccia di sudore, è un passo avanti.'),
		t('Vinci la mente, e il corpo seguirà.'),
		t('Il ferro non mente mai, ti dice sempre chi sei.'),
		t('Supera i tuoi limiti, un chilo alla volta.'),
		t('La determinazione, trasforma la fatica in forza.'),
		t('Non fermarti quando sei stanco, fermati quando hai finito.'),
		t('Non cercare scorciatoie, la salita fa parte del viaggio.'),
		t('La motivazione ti fa partire, l\'abitudine ti fa andare avanti.'),
		t('Una serie in più oggi, un record in più domani.'),
		t('Fidati del processo, i risultati arriveranno.'),
		t('Non serve essere perfetti, basta essere costanti.'),
	]
	// una frase diversa ogni giorno, e ogni sette secondi la successiva; chi non vuole movimento ne vede una sola
	const day = Math.floor((Date.now() - new Date().getTimezoneOffset() * 60_000) / 86_400_000)
	let i = $state(day % PHRASES.length)
	$effect(() => {
		if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
		const id = setInterval(() => { i = (i + 1) % PHRASES.length }, 7_000)
		return () => clearInterval(id)
	})
</script>

<section class="peak">
	<svg class="mount" viewBox="0 0 360 220" overflow="visible" preserveAspectRatio="xMaxYMax meet" aria-hidden="true">
		<defs>
			<linearGradient id="ridge" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".38" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity=".04" /></linearGradient>
		</defs>
		<path d="M-900 150 H0 L40 122 L84 140 L132 92 L182 128 L232 72 L292 122 L332 96 L360 114 V220 H-900Z" fill="rgba(255,255,255,.05)" />
		<path d="M-900 182 H0 L58 146 L112 164 L168 110 L214 142 L262 48 L304 104 L338 84 L360 96 V220 H-900Z" fill="url(#ridge)" />
		<path d="M262 48 L246 78 L256 72 L262 82 L270 72 L282 80 Z" fill="rgba(255,255,255,.85)" />
		<path d="M-900 220 V184 H0 L70 170 L132 188 L202 160 L262 180 L330 164 L360 176 V220 H-900Z" fill="rgba(0,0,0,.38)" />
		<path d="M262 48 V26 L282 33 L262 40" fill="rgb(var(--accent-rgb))" stroke="rgb(var(--accent-rgb))" stroke-width="1.5" stroke-linejoin="round" />
	</svg>

	<div class="top">
		<div><div class="small muted">{greeting}</div><div class="who">{name}</div></div>
		<button class="avatar" onclick={onavatar} aria-label={t('Impostazioni')}>{name.slice(0, 1).toUpperCase()}</button>
	</div>

	<div class="quote">{#key i}<p>{PHRASES[i].replace(', ', ',\n')}</p>{/key}</div>

	<!-- l'obiettivo della settimana: un segmento per allenamento -->
	<button class="goal" onclick={onchangegoal} aria-label="{t('Obiettivo della settimana')}: {Math.min(weekSessions, goal)} / {goal}. {t('Cambia l\'obiettivo della settimana')}">
		<div class="gl"><span>{weekSessions >= goal ? t('Obiettivo raggiunto!') : t('Obiettivo della settimana')}</span><b>{weekSessions} / {goal}</b></div>
		<div class="segs">{#each Array(goal) as _, k}<i class:on={k < weekSessions} class:last={k === weekSessions - 1}></i>{/each}</div>
	</button>

	<div class="foot">
		<span class="pill"><b use:countUp={{ value: streak, from: roll?.from, delay: roll?.delay }}></b> {t('settimane di fila')}</span>
		{#if weekSessions > goal}<span class="pill">{tn(weekSessions - goal, '+1 oltre l\'obiettivo', '+{0} oltre l\'obiettivo')}</span>{/if}
	</div>
</section>

<style>
	.peak { position: relative; overflow: hidden; border-radius: 28px; padding: 13px 14px 12px; min-height: 224px; display: flex; flex-direction: column;
		border: 1px solid rgba(var(--accent-rgb), .3);
		background: radial-gradient(circle 260px at 76% 12%, rgba(var(--accent-rgb), .35), transparent 70%), linear-gradient(180deg, rgba(var(--accent-rgb), .1), rgba(10, 14, 24, .9) 70%); }
	.mount { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
	.top { position: relative; display: flex; align-items: center; justify-content: space-between; }
	.who { font-size: 18px; font-weight: 800; letter-spacing: -0.02em; }
	.avatar { width: 34px; height: 34px; border-radius: 50%; border: 0; font-weight: 800; font-size: 15px; color: var(--on-accent); background: linear-gradient(135deg, var(--accent), rgba(var(--accent-rgb), .55)); box-shadow: 0 6px 18px rgba(var(--accent-rgb), .35); }
	.quote { position: relative; margin-top: 14px; max-width: 62%; min-height: 68px; }
	.quote p { margin: 0; font-size: 20px; font-weight: 900; letter-spacing: -0.03em; line-height: 1.08; white-space: pre-line; text-shadow: 0 4px 30px rgba(var(--accent-rgb), .45); animation: in .7s cubic-bezier(.22, 1, .36, 1); }
	@keyframes in { from { opacity: 0; transform: translateY(10px); } }
	@media (prefers-reduced-motion: reduce) { .quote p { animation: none; } }
	.goal { position: relative; margin-top: auto; padding: 8px 0 0; border: 0; background: none; color: inherit; text-align: left; width: 100%; }
	.gl { display: flex; justify-content: space-between; align-items: baseline; font-size: 11px; color: var(--text-2); margin-bottom: 6px; text-shadow: 0 1px 10px rgba(0,0,0,.7); }
	.gl b { font-size: 13px; color: var(--text); font-variant-numeric: tabular-nums; }
	.segs { display: flex; gap: 6px; }
	.segs i { position: relative; flex: 1; height: 9px; border-radius: 5px; background: rgba(255,255,255,.14); box-shadow: inset 0 0 0 1px rgba(255,255,255,.06); }
	/* il riempimento è un velo che si allunga da sinistra (transform), così un segmento nuovo si riempie invece di comparire */
	.segs i::after { content: ''; position: absolute; inset: 0; border-radius: 6px; background: linear-gradient(90deg, rgba(var(--accent-rgb), .7), rgb(var(--accent-rgb))); transform: scaleX(0); transform-origin: left; transition: transform 220ms var(--ease-out); }
	.segs i.on::after { transform: none; }
	.segs i.last::after { box-shadow: 0 0 14px rgba(var(--accent-rgb), .7); }
	.foot { position: relative; padding-top: 8px; display: flex; flex-wrap: wrap; gap: 8px; }
	.pill { padding: 4px 10px; border-radius: 999px; font-size: 11px; color: var(--text-2); background: rgba(0,0,0,.35); border: 1px solid rgba(255,255,255,.1); backdrop-filter: blur(6px); }
	.pill b { color: var(--accent); font-size: 13px; margin-right: 2px; }
</style>
