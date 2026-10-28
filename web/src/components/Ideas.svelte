<script lang="ts">
	import { reduced } from '../lib/fx.ts'
	import { setLine } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import Icon from './Icon.svelte'

	/**
	 * Le quattro idee di Peak, a tutto schermo e nello stesso linguaggio del primo avvio: una piccola animazione che si ripete,
	 * un titolo e una riga. Si scorre con «Avanti» o col dito (scroll-snap, come un carosello nativo). Il CSS di base di ogni
	 * disegno è il fotogramma finale: con «riduci movimento» (che spegne le animazioni) resta quello.
	 */
	let { onnext, onskip }: { onnext: () => void; onskip: () => void } = $props()

	const PAGES = 4
	let at = $state(0)
	let pager = $state<HTMLElement>()

	function go(i: number) { pager?.scrollTo({ left: i * pager.clientWidth, behavior: reduced() ? 'auto' : 'smooth' }) }
	function next() { at === PAGES - 1 ? onnext() : go(at + 1) }
	const onscroll = () => { if (pager) at = Math.round(pager.scrollLeft / pager.clientWidth) }
	function key(e: KeyboardEvent) {
		if (e.key === 'ArrowRight') go(Math.min(PAGES - 1, at + 1))
		else if (e.key === 'ArrowLeft') go(Math.max(0, at - 1))
	}

	const TIERS = [
		{ hi: '#f1b27a', lo: '#a35f2a', ink: '#3a1d08', tint: '#e0955a' },
		{ hi: '#f4f6fa', lo: '#98a1b1', ink: '#232a36', tint: '#dfe4ec' },
		{ hi: '#ffe69a', lo: '#d39a14', ink: '#3d2a00', tint: '#ffd45e' },
	]
	const NAMES = $derived([t('Bronzo'), t('Argento'), t('Oro')])
	// i coriandoli del record: dodici, a raggiera
	const BURST = Array.from({ length: 12 }, (_, k) => { const a = (k / 12) * Math.PI * 2 + 0.2, r = 34 + (k % 3) * 9; return { dx: Math.cos(a) * r, dy: Math.sin(a) * r, c: ['var(--pos)', 'var(--accent)', 'var(--spalle)'][k % 3]!, s: k % 2 ? 2.6 : 3.6 } })
	const BOARD = [{ w: 188, c: 'var(--petto)', l: 'A' }, { w: 146, c: 'var(--schiena)', l: 'M' }, { w: 104, c: 'var(--gambe)', l: 'G' }]
</script>

<svelte:window onkeydown={key} />
<div class="ob top flush" role="dialog" aria-modal="true" aria-label={t('Tutorial')}>
	<header class="ob-head ob-col gut">
		<div class="ob-prog" role="progressbar" aria-label={t('Passo {0} di {1}', at + 1, PAGES)} aria-valuemin="1" aria-valuemax={PAGES} aria-valuenow={at + 1} style="--p:{(at + 1) / PAGES}"><i></i></div>
		<button class="ob-skip" onclick={onskip}>{t('Salta')}</button>
	</header>

	<div class="ob-stage">
		<div class="pager" bind:this={pager} {onscroll}>
			<!-- 1. la montagna -->
			<section class="slide" class:on={at === 0} aria-label="1 / {PAGES}">
				<div class="ob-body"><div class="ob-col hero gut ob-mid ob-split">
					<div class="art">
						<svg class="svg" viewBox="0 0 320 240" aria-hidden="true">
							<defs>
								<linearGradient id="i-ridge" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="rgb(var(--accent-rgb))" stop-opacity=".5" /><stop offset="1" stop-color="rgb(var(--accent-rgb))" stop-opacity=".05" /></linearGradient>
								<clipPath id="i-card"><rect width="320" height="176" rx="28" /></clipPath>
							</defs>
							<g class="loop">
								<rect width="320" height="176" rx="28" class="panel" />
								<g clip-path="url(#i-card)">
									<path d="M0 112 L36 86 L74 104 L118 62 L160 92 L206 52 L252 86 L290 68 L320 84 V176 H0Z" fill="rgba(255,255,255,.06)" />
									<path d="M0 148 L48 112 L98 130 L150 80 L190 108 L240 34 L282 90 L320 72 V176 H0Z" fill="url(#i-ridge)" />
									<path d="M240 34 L223.8 58 L231 52 L240 64 L248 53 L258 58 Z" fill="rgba(255,255,255,.88)" />
									<path d="M0 176 V160 L60 150 L130 164 L200 148 L260 160 L320 150 V176Z" fill="rgba(0,0,0,.38)" />
								</g>
								<g transform="translate(240 34)"><g class="flag m-flag"><path class="pole" d="M0 0 V-24" /><path class="cloth" d="M0 -24 L22 -17 L0 -10 Z" /></g></g>
								{#each [0, 1, 2] as i}
									<rect x={40 + i * 84} y="192" width="76" height="10" rx="5" fill="rgba(255,255,255,.14)" />
									<rect class="sg sg{i}" x={40 + i * 84} y="192" width="76" height="10" rx="5" />
								{/each}
								<g class="streak">
									<rect x="70" y="212" width="180" height="26" rx="13" fill="rgba(0,0,0,.35)" stroke="rgba(255,255,255,.12)" />
									<g transform="translate(84 215)" style="color:var(--accent)"><Icon name="flame" size={20} /></g>
									<text class="n n0" x="114" y="230">3</text><text class="n n1" x="114" y="230">4</text>
									<text class="lab" x="130" y="229.5">{t('settimane di fila')}</text>
								</g>
							</g>
						</svg>
					</div>
					<div><h2 class="ob-title">{t('Sali ogni settimana')}</h2><p class="ob-sub">{t('Raggiungi l\'obiettivo e allunga la serie di settimane.')}</p></div>
				</div></div>
			</section>

			<!-- 2. i record -->
			<section class="slide" class:on={at === 1} aria-label="2 / {PAGES}">
				<div class="ob-body"><div class="ob-col hero gut ob-mid ob-split">
					<div class="art">
						<svg class="svg" viewBox="0 0 320 240" aria-hidden="true">
							<g class="loop">
								<rect class="panel" x="10" y="36" width="300" height="168" rx="26" />
								<text class="cap" x="30" y="68">Chest Press</text>
								<line x1="30" y1="112" x2="290" y2="112" stroke="rgba(255,255,255,.1)" />
								<text class="idx" x="30" y="100">1</text><text class="set" x="58" y="100">{setLine(60, 8)}</text>
								<rect class="flash" x="18" y="122" width="284" height="50" rx="16" />
								<text class="idx" x="30" y="154">2</text>
								<text class="set s-old" x="58" y="154">{setLine(60, 8)}</text>
								<text class="set s-new" x="58" y="154">{setLine(65, 8)}</text>
								<g transform="translate(262 147)">
									{#each BURST as p}<circle class="part" r={p.s} fill={p.c} style="--dx:{p.dx}px;--dy:{p.dy}px" />{/each}
									<g class="pr"><rect x="-24" y="-13" width="48" height="26" rx="13" fill="rgba(95,217,138,.18)" /><text class="prt" y="5">PR</text></g>
								</g>
							</g>
						</svg>
					</div>
					<div><h2 class="ob-title">{t('Batti i tuoi record')}</h2><p class="ob-sub">{t('Ogni serie più forte diventa un record.')}</p></div>
				</div></div>
			</section>

			<!-- 3. le medaglie -->
			<section class="slide" class:on={at === 2} aria-label="3 / {PAGES}">
				<div class="ob-body"><div class="ob-col hero gut ob-mid ob-split">
					<div class="art">
						<svg class="svg" viewBox="0 0 320 240" aria-hidden="true">
							<defs>
								{#each TIERS as tr, i}<linearGradient id="i-med{i}" x1="0.2" y1="0.1" x2="0.8" y2="0.95"><stop offset="0" stop-color={tr.hi} /><stop offset="1" stop-color={tr.lo} /></linearGradient>{/each}
							</defs>
							<g class="loop md" style="--n:3">
								{#each TIERS as tr, i}
									<g class="tier" style="--i:{i}">
										<circle class="wave" cx="160" cy="116" r="46" stroke={tr.tint} />
										<g class="medal">
											<path d="M124 8 H150 L176 76 H150 Z" fill={tr.lo} /><path d="M196 8 H170 L144 76 H170 Z" fill={tr.tint} opacity=".85" />
											<circle cx="160" cy="116" r="48" fill="url(#i-med{i})" />
											<circle cx="160" cy="116" r="38" fill="none" stroke={tr.ink} stroke-opacity=".28" stroke-width="2" />
											<path transform="translate(139 97) scale(1.75)" d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z" fill={tr.ink} fill-opacity=".8" />
										</g>
										<text class="tname" x="160" y="214" fill={tr.tint}>{NAMES[i]}</text>
									</g>
								{/each}
							</g>
						</svg>
					</div>
					<div><h2 class="ob-title">{t('Medaglie a tre livelli')}</h2><p class="ob-sub">{t('Bronzo, argento, oro: sempre un traguardo più su.')}</p></div>
				</div></div>
			</section>

			<!-- 4. gli amici -->
			<section class="slide" class:on={at === 3} aria-label="4 / {PAGES}">
				<div class="ob-body"><div class="ob-col hero gut ob-mid ob-split">
					<div class="art">
						<svg class="svg" viewBox="0 0 320 240" aria-hidden="true">
							<g class="loop">
								<g class="invite">
									<rect x="86" y="8" width="148" height="38" rx="19" fill="rgba(var(--accent-rgb),.16)" stroke="rgba(var(--accent-rgb),.45)" />
									<g transform="translate(100 15)" style="color:var(--accent)" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" transform="scale(.95)" /></g>
									<rect x="136" y="24" width="84" height="7" rx="3.5" fill="rgba(255,255,255,.32)" />
								</g>
								<rect class="panel" x="10" y="62" width="300" height="168" rx="26" />
								<text class="cap" x="30" y="92">Chest Press</text>
								{#each BOARD as b, i}
									<g class="rowb r{i}">
										<circle cx="42" cy={126 + i * 38} r="14" fill={b.c} /><text class="av" x="42" y={131 + i * 38}>{b.l}</text>
										<rect x="68" y={120 + i * 38} width="222" height="12" rx="6" fill="rgba(255,255,255,.08)" />
										<rect class="meter" x="68" y={120 + i * 38} width={b.w} height="12" rx="6" fill={b.c} />
									</g>
								{/each}
								<g transform="translate(30 98)"><g class="crown"><path d="M11.562 3.266a.5.5 0 0 1 .876 0L15.39 8.87a1 1 0 0 0 1.516.294L21.183 5.5a.5.5 0 0 1 .798.519l-2.834 10.246a1 1 0 0 1-.956.734H5.81a1 1 0 0 1-.957-.734L2.02 6.02a.5.5 0 0 1 .798-.519l4.276 3.664a1 1 0 0 0 1.516-.294z" transform="scale(.62)" fill="var(--spalle)" /></g></g>
							</g>
						</svg>
					</div>
					<div><h2 class="ob-title">{t('Sfida gli amici')}</h2><p class="ob-sub">{t('Invitali con un link e confrontatevi sugli stessi macchinari.')}</p></div>
				</div></div>
			</section>
		</div>
	</div>

	<div class="ob-foot"><div class="ob-col gut"><button class="btn primary block ob-cta" onclick={next}>{at === PAGES - 1 ? t('Proviamo') : t('Avanti')}</button></div></div>
</div>

<style>
	.pager { flex: 1; min-height: 0; display: flex; overflow-x: auto; overflow-y: hidden; scroll-snap-type: x mandatory; overscroll-behavior-x: contain; scrollbar-width: none; }
	.pager::-webkit-scrollbar { display: none; }
	.slide { flex: 0 0 100%; min-width: 0; display: flex; flex-direction: column; scroll-snap-align: start; scroll-snap-stop: always; }
	.art { width: 100%; max-width: 480px; }
	/* la riga sotto il titolo tiene sempre due righe: il titolo non salta da una pagina all'altra */
	.slide .ob-sub { min-height: 2.7em; }
	.svg { display: block; width: 100%; height: auto; max-height: min(46dvh, 400px); }
	@media (orientation: landscape) and (max-height: 520px) { .svg { max-height: calc(100dvh - 150px); } }

	/* basi comuni: il fotogramma finale. In pagina attiva (.on) ogni disegno si ripete ogni --T e sfuma agli estremi, così il giro riparte pulito */
	.slide { --T: 7s; }
	text { font-family: var(--font); fill: var(--text); }
	.panel { fill: rgba(255, 255, 255, .06); stroke: rgba(255, 255, 255, .12); }
	.cap { font-size: 13px; font-weight: 700; fill: var(--text-2); }
	.on .loop { animation: loopfade var(--T) linear infinite; }
	@keyframes loopfade { 0% { opacity: 0; } 4% { opacity: 1; } 93% { opacity: 1; } 100% { opacity: 0; } }

	/* 1. montagna: tre segmenti si riempiono, la bandiera sale, la serie passa da 3 a 4 */
	.slide:nth-child(1) .panel { fill: rgba(var(--accent-rgb), .08); stroke: rgba(var(--accent-rgb), .3); }
	.sg { fill: rgb(var(--accent-rgb)); transform-box: fill-box; transform-origin: left; }
	.sg2 { filter: drop-shadow(0 0 5px rgba(var(--accent-rgb), .7)); }
	.pole { fill: none; stroke: rgb(var(--accent-rgb)); stroke-width: 1.8; stroke-linecap: round; }
	.cloth { fill: rgb(var(--accent-rgb)); stroke: rgb(var(--accent-rgb)); stroke-width: 1.4; stroke-linejoin: round; transform-box: fill-box; transform-origin: 0 50%; }
	.m-flag { transform-box: fill-box; transform-origin: 0 100%; }
	.n { font-size: 17px; font-weight: 900; fill: var(--accent); text-anchor: end; }
	.n0 { opacity: 0; }
	.lab { font-size: 12px; fill: var(--text-2); }
	.streak { transform-box: fill-box; transform-origin: center; }
	.on .sg0 { animation: s0 var(--T) var(--ease-out) infinite; }
	.on .sg1 { animation: s1 var(--T) var(--ease-out) infinite; }
	.on .sg2 { animation: s2 var(--T) var(--ease-out) infinite; }
	.on .m-flag { animation: spring var(--T) cubic-bezier(.34, 1.56, .64, 1) infinite; }
	.on .cloth { animation: wave 1.5s ease-in-out infinite alternate; }
	.on .n0 { animation: n0 var(--T) ease infinite; }
	.on .n1 { animation: n1 var(--T) ease infinite; }
	.on .streak { animation: bump var(--T) var(--ease-out) infinite; }
	@keyframes s0 { 0%, 8% { transform: scaleX(0); } 16%, 100% { transform: scaleX(1); } }
	@keyframes s1 { 0%, 20% { transform: scaleX(0); } 28%, 100% { transform: scaleX(1); } }
	@keyframes s2 { 0%, 32% { transform: scaleX(0); } 40%, 100% { transform: scaleX(1); } }
	@keyframes spring { 0%, 42% { transform: scale(0); } 52%, 100% { transform: scale(1); } }
	@keyframes wave { from { transform: skewY(-5deg) scaleX(1); } to { transform: skewY(5deg) scaleX(.88); } }
	@keyframes n0 { 0%, 54% { opacity: 1; } 58%, 100% { opacity: 0; } }
	@keyframes n1 { 0%, 54% { opacity: 0; } 58%, 100% { opacity: 1; } }
	@keyframes bump { 0%, 54% { transform: scale(1); } 60% { transform: scale(1.08); } 68%, 100% { transform: scale(1); } }

	/* 2. record: la riga 2 passa da 60 a 65 kg, lampo verde, «PR» che fa pop e i coriandoli */
	.slide:nth-child(2) { --T: 6s; }
	.idx { font-size: 15px; font-weight: 700; fill: var(--text-3); }
	.set { font-size: 22px; font-weight: 800; font-variant-numeric: tabular-nums; }
	.s-old { opacity: 0; }
	.flash { fill: rgba(95, 217, 138, .22); opacity: .6; }
	.prt { font-size: 14px; font-weight: 900; fill: var(--pos); text-anchor: middle; }
	.pr { transform-box: fill-box; transform-origin: center; }
	.part { transform-box: fill-box; transform-origin: center; opacity: 0; }
	.on .s-old { animation: sold var(--T) ease infinite; }
	.on .s-new { animation: snew var(--T) ease infinite; }
	.on .flash { animation: flash var(--T) ease infinite; }
	.on .pr { animation: pop var(--T) cubic-bezier(.34, 1.56, .64, 1) infinite; }
	.on .part { animation: burst var(--T) var(--ease-out) infinite; }
	@keyframes sold { 0%, 36% { opacity: 1; } 42%, 100% { opacity: 0; } }
	@keyframes snew { 0%, 36% { opacity: 0; transform: translateY(8px); } 44%, 100% { opacity: 1; transform: none; } }
	@keyframes flash { 0%, 38% { opacity: 0; } 46% { opacity: 1; } 70%, 100% { opacity: 0; } }
	@keyframes pop { 0%, 40% { transform: scale(0); } 50%, 100% { transform: scale(1); } }
	@keyframes burst { 0%, 41.9% { transform: translate(0, 0) scale(.3); opacity: 0; } 42%, 70% { transform: translate(0, 0) scale(.3); opacity: 1; } 78%, 100% { transform: translate(var(--dx), var(--dy)) scale(1); opacity: 0; } }

	/* 3. medaglie: bronzo, argento, oro, uno dopo l'altro; ogni livello arriva con un'onda e il suo nome */
	.slide:nth-child(3) { --T: 9s; }
	.tier .medal, .tier .tname, .tier .wave { opacity: 0; }
	.tier:last-child .medal, .tier:last-child .tname { opacity: 1; }
	.wave { fill: none; stroke-width: 3; transform-box: fill-box; transform-origin: center; }
	.tname { font-size: 20px; font-weight: 800; text-anchor: middle; }
	.medal { transform-box: fill-box; transform-origin: center; filter: drop-shadow(0 8px 18px rgba(0, 0, 0, .35)); }
	.on .tier .medal { animation: tier var(--T) cubic-bezier(.34, 1.56, .64, 1) infinite backwards; animation-delay: calc(var(--i) * var(--T) / 3); }
	.on .tier .tname { animation: tierl var(--T) ease infinite backwards; animation-delay: calc(var(--i) * var(--T) / 3); }
	.on .tier .wave { animation: wave2 var(--T) ease-out infinite backwards; animation-delay: calc(var(--i) * var(--T) / 3); }
	@keyframes tier { 0% { opacity: 0; transform: scale(.7); } 4% { opacity: 1; transform: scale(1); } 30% { opacity: 1; transform: scale(1); } 34%, 100% { opacity: 0; transform: scale(.92); } }
	@keyframes tierl { 0%, 3% { opacity: 0; } 6%, 30% { opacity: 1; } 34%, 100% { opacity: 0; } }
	@keyframes wave2 { 0%, 2% { opacity: 0; transform: scale(1); } 4% { opacity: .8; } 14%, 100% { opacity: 0; transform: scale(1.8); } }

	/* 4. amici: il link, poi la classifica sullo stesso macchinario che si riempie, con la corona al primo */
	.slide:nth-child(4) { --T: 7s; }
	.invite, .rowb, .crown { transform-box: fill-box; transform-origin: center; }
	.meter { transform-box: fill-box; transform-origin: left; }
	.av { font-size: 14px; font-weight: 900; fill: #0b0d14; text-anchor: middle; }
	.on .invite { animation: chip var(--T) cubic-bezier(.34, 1.56, .64, 1) infinite; }
	.on .r0 { animation: rowb var(--T) var(--ease-out) infinite; }
	.on .r1 { animation: rowb1 var(--T) var(--ease-out) infinite; }
	.on .r2 { animation: rowb2 var(--T) var(--ease-out) infinite; }
	.on .meter { animation: bar var(--T) var(--ease-out) infinite; }
	.on .crown { animation: crown var(--T) cubic-bezier(.34, 1.56, .64, 1) infinite; }
	@keyframes chip { 0%, 2% { transform: scale(0); opacity: 0; } 10%, 100% { transform: scale(1); opacity: 1; } }
	@keyframes rowb { 0%, 18% { opacity: 0; transform: translateX(-24px); } 28%, 100% { opacity: 1; transform: none; } }
	@keyframes rowb1 { 0%, 26% { opacity: 0; transform: translateX(-24px); } 36%, 100% { opacity: 1; transform: none; } }
	@keyframes rowb2 { 0%, 34% { opacity: 0; transform: translateX(-24px); } 44%, 100% { opacity: 1; transform: none; } }
	@keyframes bar { 0%, 30% { transform: scaleX(0); } 62%, 100% { transform: scaleX(1); } }
	@keyframes crown { 0%, 64% { transform: scale(0) translateY(-6px); opacity: 0; } 74%, 100% { transform: none; opacity: 1; } }
</style>
