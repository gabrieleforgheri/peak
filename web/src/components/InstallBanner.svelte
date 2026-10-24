<script lang="ts">
	import { dismissInstall, installNow, target } from '../lib/install.svelte.ts'
	import { t } from '../lib/i18n.ts'
	import Icon from './Icon.svelte'

	const tg = $derived(target())
	const prompt = $derived(tg === 'android-prompt' || tg === 'desktop-prompt')
	// testo con un'icona al posto del segnaposto: la frase resta una sola chiave da tradurre
	const SEP = '\u0000'
	const steps = $derived.by((): { text: string; icon?: string } | null => {
		switch (tg) {
			case 'ios-safari': return { text: t('Tocca Condividi {0} e poi «Aggiungi alla schermata Home».', SEP), icon: 'share' }
			case 'ios-other': return { text: t('Tocca Condividi {0} nella barra dell\'indirizzo e poi «Aggiungi alla schermata Home». Se non c\'è, apri Peak in Safari.', SEP), icon: 'share' }
			case 'android-samsung': return { text: t('Apri il menu del browser, poi «Aggiungi pagina a» e «Schermata Home».') }
			case 'android-firefox': return { text: t('Tocca il menu {0} e poi «Installa».', SEP), icon: 'more-v' }
			case 'android-other': return { text: t('Tocca il menu {0} e poi «Aggiungi a schermata Home».', SEP), icon: 'more-v' }
			default: return null
		}
	})
</script>

{#if tg}
	<div class="card install" role="region" aria-label={t('Installa Peak')}>
		<span class="ai"><Icon name="phone" size={22} /></span>
		<div class="grow">
			<b>{t('Installa Peak')}</b>
			<div class="small muted">
				{#if prompt}{t('Aprila dalla schermata Home, a schermo intero.')}
				{:else if steps}{@const [a, b] = steps.text.split(SEP)}{a}{#if steps.icon}<span class="ic"><Icon name={steps.icon} size={16} /></span>{b}{/if}{/if}
			</div>
		</div>
		{#if prompt}<button class="btn primary small" onclick={installNow}>{t('Installa')}</button>{/if}
		<button class="icon-btn" onclick={dismissInstall} aria-label={t('Chiudi')}><Icon name="x" /></button>
	</div>
{/if}

<style>
	.install { display: flex; align-items: center; gap: 12px; margin-top: 12px; padding: 12px 14px; }
	.ai { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; background: var(--accent); color: var(--on-accent); flex: none; }
	.grow { flex: 1; min-width: 0; }
	.ic { display: inline-flex; vertical-align: -3px; margin: 0 2px; color: var(--accent); }
</style>
