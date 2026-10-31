<script lang="ts">
	import { onMount } from 'svelte'
	import { t } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { loadFriends, useInvite, type Person } from '../lib/social.svelte.ts'

	let { code }: { code: string } = $props()
	let phase = $state<'wait' | 'ok' | 'bad'>('wait')
	let friend = $state<Person | null>(null)

	// aprire il link vale come un sì: il codice si consuma qui
	onMount(async () => {
		const r = await useInvite(code)
		if (r) {
			friend = r.amico; phase = 'ok'; void loadFriends()
			// il codice è consumato: ricaricando si apre il profilo, non «invito non valido»
			history.replaceState(null, '', `/#/amici/${r.amico.id}`)
		} else phase = 'bad'
	})
</script>

<main class="screen no-tabs">
	<div class="empty" style="margin-top:20vh">
		{#if phase === 'wait'}…
		{:else if phase === 'ok'}
			<div class="mid">{t('Ora siete amici.')}</div>
			<p class="muted" style="margin:8px 0 20px">@{friend?.username}</p>
			<button class="btn primary" onclick={() => go(`amici/${friend!.id}`, true)}>{t('Vedi il suo profilo')}</button>
		{:else}
			<div class="mid">{t('Questo invito non vale più.')}</div>
			<p class="muted" style="margin:8px 0 20px">{t('È già stato usato, è scaduto o è il tuo. Chiedi un link nuovo.')}</p>
			<button class="btn" onclick={() => go('amici', true)}>{t('Vai agli amici')}</button>
		{/if}
	</div>
</main>
