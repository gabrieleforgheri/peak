<script lang="ts">
	import { onMount } from 'svelte'
	import { qrSvg } from '@common/qr.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { F, acceptFriend, createInvite, inviteLink, loadFriends, removeFriend, requestFriend, searchUsers, type FriendState, type Person } from '../lib/social.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'

	let query = $state('')
	let found = $state<(Person & { stato: FriendState })[]>([])
	let searched = $state('')
	let invite = $state<string | null>(null)
	onMount(() => { void loadFriends() })

	let timer: ReturnType<typeof setTimeout> | undefined
	function typing() {
		clearTimeout(timer)
		const q = query.trim()
		if (q.replace(/^@/, '').length < 2) { found = []; searched = ''; return }
		timer = setTimeout(async () => { found = await searchUsers(q); searched = q }, 300)
	}
	async function ask(p: Person) {
		const state = await requestFriend(p.id)
		if (!state) return void toast(t('Non è andata: riprova.'))
		found = found.map((x) => (x.id === p.id ? { ...x, stato: state } : x))
		toast(state === 'amici' ? t('Ora siete amici.') : t('Richiesta inviata.'))
	}
	async function makeInvite() {
		const r = await createInvite()
		if (!r) return void toast(t('Serve la connessione.'))
		invite = inviteLink(r.codice)
	}
	async function share(link: string) {
		try { await navigator.share({ title: 'Peak', text: t('Aggiungimi su Peak:'), url: link }) } catch (e) { if ((e as Error).name !== 'AbortError') void copy(link) }
	}
	async function copy(text: string) { try { await navigator.clipboard.writeText(text); toast(t('Link copiato.')) } catch { toast(text) } }
	async function remove(p: Person) { if (confirm(t('Togliere {0} dagli amici?', p.username))) await removeFriend(p.id) }
</script>

<div class="stack">
	<button class="btn primary block" onclick={makeInvite} data-tour="invite"><Icon name="upload" size={20} />{t('Invita un amico')}</button>
	<p class="small faint" style="margin:-4px 4px 0">{t('Il link vale una volta sola: chi lo apre diventa tuo amico subito.')}</p>

	<div style="position:relative">
		<input class="input" style="padding-left:40px" type="search" placeholder={t('Cerca un username')} bind:value={query} oninput={typing} autocapitalize="none" autocomplete="off" />
		<span style="position:absolute;left:12px;top:12px;color:var(--text-3)"><Icon name="search" size={22} /></span>
	</div>
	{#if searched && query.trim()}
		{#if found.length}
			<div class="card list">
				{#each found as p (p.id)}
					<div class="item">
						<div class="grow"><b>@{p.username}</b></div>
						{#if p.stato === 'nessuno'}<button class="btn small" onclick={() => ask(p)}>{t('Aggiungi')}</button>
						{:else if p.stato === 'amici'}<span class="small muted">{t('Amici')}</span>
						{:else if p.stato === 'inviata'}<span class="small muted">{t('Richiesta inviata')}</span>
						{:else}<button class="btn small primary" onclick={() => ask(p)}>{t('Accetta')}</button>{/if}
					</div>
				{/each}
			</div>
		{:else}<div class="empty">{t('Nessun utente con questo username.')}</div>{/if}
	{/if}

	{#if F.friends?.ricevute.length}
		<div class="sec-title" style="margin:8px 0 0">{t('Richieste')}</div>
		<div class="card list">
			{#each F.friends.ricevute as p (p.id)}
				<div class="item">
					<div class="grow"><b>@{p.username}</b></div>
					<button class="btn small primary" onclick={() => acceptFriend(p.id)}>{t('Accetta')}</button>
					<button class="icon-btn" onclick={() => removeFriend(p.id)} aria-label={t('Rifiuta')}><Icon name="x" size={20} /></button>
				</div>
			{/each}
		</div>
	{/if}

	{#if F.friends?.inviate.length}
		<p class="small faint" style="margin:0 4px">{tn(F.friends.inviate.length, '1 richiesta in attesa', '{0} richieste in attesa')}: {F.friends.inviate.map((p) => '@' + p.username).join(', ')}</p>
	{/if}
</div>

<style>
	/* il QR resta nero su bianco anche col tema scuro: gli scanner lo vogliono così */
	.qr { width: min(100%, 280px); margin: 0 auto; border-radius: 16px; overflow: hidden; background: #fff; line-height: 0; }
	.qr :global(svg) { width: 100%; height: auto; display: block; }
</style>

{#if invite}
	<Sheet title={t('Invita un amico')} onclose={() => (invite = null)}>
		<div class="stack">
			<p class="muted small">{t('Fagli inquadrare il QR, oppure mandagli il link. Vale una volta sola e per sette giorni.')}</p>
			<div class="qr" aria-label={t('Codice QR dell\'invito')} role="img">{@html qrSvg(invite)}</div>
			{#if typeof navigator.share === 'function'}<button class="btn primary block" onclick={() => share(invite!)}><Icon name="upload" size={20} />{t('Condividi')}</button>{/if}
			<button class="btn block" onclick={() => copy(invite!)}>{t('Copia il link')}</button>
		</div>
	</Sheet>
{/if}
