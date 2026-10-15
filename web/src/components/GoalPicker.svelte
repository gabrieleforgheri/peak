<script lang="ts">
	import { setSetting } from '../lib/actions.ts'
	import { t } from '../lib/i18n.ts'

	/** La scelta dell'obiettivo, a tutto schermo: quanti allenamenti a settimana. Si apre la prima volta e dalla Home. */
	let { current = 0, suggested = 3, onclose }: { current?: number; suggested?: number; onclose: () => void } = $props()
	let pick = $state(current || suggested)
	const OPTIONS = $derived([
		{ n: 2, name: t('Leggero'), text: t('Per cominciare, senza fretta.') },
		{ n: 3, name: t('Regolare'), text: t('Il passo giusto per crescere.') },
		{ n: 4, name: t('Impegnato'), text: t('Una routine seria.') },
		{ n: 5, name: t('Intenso'), text: t('Quasi tutti i giorni.') },
		{ n: 6, name: t('Da campione'), text: t('Per chi vive in palestra.') },
	])
	function confirm() { setSetting('weeklyGoal', pick); onclose() }
</script>

<div class="goal" role="dialog" aria-modal="true" aria-label={t('Il tuo obiettivo')}>
	<div class="in">
		{#if current}<button class="x link" onclick={onclose}>{t('Annulla')}</button>{/if}
		<div class="mount" aria-hidden="true">⛰️</div>
		<h2>{t('Quanti allenamenti a settimana?')}</h2>
		<p class="muted">{t('Scegli un obiettivo che puoi mantenere. Lo cambi quando vuoi.')}</p>
		<div class="opts" role="radiogroup">
			{#each OPTIONS as o}
				<button class="opt" class:on={pick === o.n} role="radio" aria-checked={pick === o.n} onclick={() => (pick = o.n)}>
					<span class="segs">{#each Array(6) as _, k}<i class:f={k < o.n}></i>{/each}</span>
					<span class="tx"><b>{t('{0} a settimana', o.n)}</b><small>{o.name} · {o.text}</small></span>
					<span class="radio"></span>
				</button>
			{/each}
		</div>
		<button class="btn primary block go" onclick={confirm}>{t('Continua')}</button>
	</div>
</div>

<style>
	.goal { position: fixed; inset: 0; z-index: 50; overflow-y: auto; background: radial-gradient(circle 360px at 50% -5%, rgba(var(--accent-rgb), .3), transparent), #0b0d14; animation: fade .25s; }
	.in { max-width: 480px; margin: 0 auto; padding: calc(env(safe-area-inset-top) + 32px) 20px calc(env(safe-area-inset-bottom) + 28px); text-align: center; }
	.x { position: absolute; left: 20px; top: calc(env(safe-area-inset-top) + 18px); }
	.mount { font-size: 64px; line-height: 1; margin-bottom: 14px; }
	h2 { font-size: 28px; font-weight: 900; letter-spacing: -.03em; line-height: 1.1; margin: 0 0 8px; }
	.opts { display: flex; flex-direction: column; gap: 10px; margin: 24px 0 22px; text-align: left; }
	.opt { display: flex; align-items: center; gap: 14px; width: 100%; padding: 14px 16px; border-radius: 18px; color: inherit; background: var(--card); border: 2px solid var(--stroke); transition: transform .12s, border-color .15s, background .15s; }
	.opt:active { transform: scale(.985); }
	.opt.on { border-color: var(--accent); background: rgba(var(--accent-rgb), .12); box-shadow: 0 0 24px rgba(var(--accent-rgb), .18); }
	.segs { display: flex; gap: 3px; flex: none; }
	.segs i { width: 7px; height: 22px; border-radius: 3px; background: rgba(255,255,255,.14); }
	.segs i.f { background: var(--accent); }
	.tx { flex: 1; text-align: left; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
	.tx b { font-size: 17px; }
	.tx small { color: var(--text-2); font-size: 12px; }
	.radio { width: 22px; height: 22px; border-radius: 50%; border: 2px solid var(--stroke); flex: none; }
	.opt.on .radio { border-color: var(--accent); background: radial-gradient(circle, var(--accent) 45%, transparent 50%); }
	.go { min-height: 54px; font-size: 17px; }
</style>
