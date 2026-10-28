<script lang="ts">
	import { S, getModel } from './lib/store.svelte.ts'
	import { route } from './lib/router.svelte.ts'
	import { go } from './lib/router.svelte.ts'
	import { applyLang, lang, t } from './lib/i18n.ts'
	import { T } from './lib/toast.svelte.ts'
	import { adoptLooseTemplates } from './lib/actions.ts'
	import Icon from './components/Icon.svelte'
	import Onboarding from './pages/Onboarding.svelte'
	import Maxes from './pages/Maxes.svelte'
	import ExerciseDetail from './pages/ExerciseDetail.svelte'
	import Workouts from './pages/Workouts.svelte'
	import Session from './pages/Session.svelte'
	import Summary from './pages/Summary.svelte'
	import Me from './pages/Me.svelte'
	import Friends from './pages/Friends.svelte'
	import FriendProfile from './pages/FriendProfile.svelte'
	import Invite from './pages/Invite.svelte'
	import Tour from './components/Tour.svelte'
	import GoalPicker from './components/GoalPicker.svelte'
	import Settings from './pages/Settings.svelte'
	import Archived from './pages/Archived.svelte'

	const m = $derived(getModel())
	const TABS = ['allenamento', 'macchinari', 'me', 'amici', 'impostazioni']
	const tab = $derived(TABS.includes(route.parts[0] ?? '') ? route.parts[0]! : route.parts[0] === 'invito' ? 'invito' : 'allenamento')
	const deep = $derived(route.parts.length > 1)
	const tabs = [
		{ id: 'allenamento', icon: 'dumbbell', label: 'Allenamento' },
		{ id: 'macchinari', icon: 'machine', label: 'Macchinari' },
		{ id: 'me', icon: 'user', label: 'Me' },
		{ id: 'amici', icon: 'users', label: 'Amici' },
	]

	$effect(() => { document.documentElement.dataset.accent = m.settings.accent })
	// la lingua scelta su un altro dispositivo arriva con la sincronizzazione
	$effect(() => { const l = m.settings.language; if (l && l !== lang) applyLang(l) })
	// i giorni di prima delle schede generali vanno in «La mia scheda»
	$effect(() => { if (S.ready && S.synced) adoptLooseTemplates(t('La mia scheda')) })
	// il primo avvio di un account vuoto: resta finché non finisce, anche se nel frattempo i dati ci sono (li crea lui)
	let onboardHeld = $state(false)
	let onboarded = $state(false)
	const onboarding = $derived(!onboarded && (onboardHeld || (S.ready && S.synced && !m.hasData)))
	$effect(() => { if (onboarding) onboardHeld = true })
	// il tutorial parte da solo (dopo il primo avvio, o da «Rivedi il tutorial») e resta finché non lo chiudi, anche se intanto le impostazioni cambiano
	let tutorialHeld = $state(false)
	const wantTutorial = $derived(S.ready && S.synced && !onboarding && m.settings.weeklyGoal > 0 && (!m.settings.tutorialDone || m.settings.tourSeen < 2))
	$effect(() => { if (wantTutorial) tutorialHeld = true })
	const loading = $derived(!S.ready || (!S.synced && !m.hasData && S.auth !== 'expired'))
	// le schede (barra in basso o binario a sinistra) si vedono solo nelle pagine principali
	const showNav = $derived(!deep && tab !== 'impostazioni')
	const offline = $derived(S.error === 'offline' && !S.user)
</script>

<div class="app" class:rail={showNav && !offline && !loading && !onboarding}>
	{#if offline}
		<main class="screen no-tabs"><div class="empty"><div class="mid">{t('Senza rete')}</div>{t('Apri Peak una volta con la connessione, poi funzionerà anche offline.')}</div></main>
	{:else if loading}
		<main class="screen no-tabs"><div class="empty">{t('Carico i tuoi dati…')}</div></main>
	{:else if onboarding}
		<Onboarding onfinish={() => (onboarded = true)} />
	{:else}
		{#if S.auth === 'expired'}
			<div class="screen" style="padding-bottom:0;padding-top:calc(env(safe-area-inset-top) + 8px);margin-bottom:-4px">
				<div class="banner warn"><div class="grow"><b>{t('Sessione scaduta')}</b>{t('I tuoi dati sono al sicuro sul telefono; accedi di nuovo per sincronizzarli.')}</div><a class="btn small" href="/auth/login">{t('Accedi')}</a></div>
			</div>
		{/if}
		{#key route.parts.join('/')}
			{#if tab === 'invito'}
				<Invite code={route.parts[1] ?? ''} />
			{:else if tab === 'macchinari'}
				{#if route.parts[1]}<ExerciseDetail id={route.parts[1]} />{:else}<Maxes />{/if}
			{:else if tab === 'allenamento'}
				{#if route.parts[2] === 'riepilogo'}<Summary id={route.parts[1]} />
				{:else if route.parts[1]}<Session id={route.parts[1]} />
				{:else}<Workouts />{/if}
			{:else if tab === 'me'}
				<Me />
			{:else if tab === 'amici'}
				{#if route.parts[1]}<FriendProfile id={route.parts[1]} />{:else}<Friends />{/if}
			{:else if route.parts[1] === 'archivio'}
				<Archived />
			{:else}
				<Settings />
			{/if}
		{/key}
		{#if showNav}
			<nav class="tabbar"><div class="tabbar-in" data-tour="tabs">
				{#each tabs as x}
					<button class="tab" class:on={tab === x.id} onclick={() => go(x.id)}><Icon name={x.icon} />{t(x.label)}</button>
				{/each}
			</div></nav>
		{/if}
	{/if}
	<!-- la prima volta: prima l'obiettivo (nel primo avvio l'ha già scelto), poi il tutorial -->
	{#if !loading && !onboarding && S.synced && !m.settings.weeklyGoal}<GoalPicker onclose={() => {}} />
	{:else if !loading && !onboarding && (tutorialHeld || wantTutorial)}<Tour onend={() => (tutorialHeld = false)} />{/if}
	{#if T.msg}<div class="toast" role="status">{#if T.icon}<Icon name={T.icon} size={18} />{/if}{T.msg}</div>{/if}
</div>
