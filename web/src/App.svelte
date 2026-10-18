<script lang="ts">
	import { S, getModel } from './lib/store.svelte.ts'
	import { route } from './lib/router.svelte.ts'
	import { go } from './lib/router.svelte.ts'
	import { t } from './lib/i18n.ts'
	import { T } from './lib/toast.svelte.ts'
	import Icon from './components/Icon.svelte'
	import FirstRun from './pages/FirstRun.svelte'
	import Maxes from './pages/Maxes.svelte'
	import ExerciseDetail from './pages/ExerciseDetail.svelte'
	import Workouts from './pages/Workouts.svelte'
	import Session from './pages/Session.svelte'
	import Summary from './pages/Summary.svelte'
	import Progress from './pages/Progress.svelte'
	import Friends from './pages/Friends.svelte'
	import Home from './pages/Home.svelte'
	import FriendProfile from './pages/FriendProfile.svelte'
	import Invite from './pages/Invite.svelte'
	import Tour from './components/Tour.svelte'
	import GoalPicker from './components/GoalPicker.svelte'
	import Settings from './pages/Settings.svelte'
	import Archived from './pages/Archived.svelte'

	const m = $derived(getModel())
	const TABS = ['home', 'allenamento', 'progressi', 'macchinari', 'amici', 'impostazioni']
	const tab = $derived(TABS.includes(route.parts[0] ?? '') ? route.parts[0]! : route.parts[0] === 'invito' ? 'invito' : 'home')
	const deep = $derived(route.parts.length > 1)
	const tabs = [
		{ id: 'home', icon: 'home', label: 'Home' },
		{ id: 'allenamento', icon: 'dumbbell', label: 'Allenamento' },
		{ id: 'progressi', icon: 'chart', label: 'Progressi' },
		{ id: 'macchinari', icon: 'machine', label: 'Macchinari' },
		{ id: 'amici', icon: 'users', label: 'Amici' },
	]

	$effect(() => { document.documentElement.dataset.accent = m.settings.accent })
	let started = $state(false)
	const showFirstRun = $derived(S.ready && S.synced && !m.hasData && !started)
	const loading = $derived(!S.ready || (!S.synced && !m.hasData && S.auth !== 'expired'))
</script>

<div class="app">
	{#if S.error === 'offline' && !S.user}
		<main class="screen no-tabs"><div class="empty"><div class="mid">{t('Senza rete')}</div>{t('Apri Peak una volta con la connessione, poi funzionerà anche offline.')}</div></main>
	{:else if loading}
		<main class="screen no-tabs"><div class="empty">{t('Carico i tuoi dati…')}</div></main>
	{:else if showFirstRun}
		<FirstRun onstart={() => (started = true)} />
	{:else}
		{#if S.auth === 'expired'}
			<div class="screen" style="padding-bottom:0;padding-top:calc(env(safe-area-inset-top) + 8px);margin-bottom:-4px">
				<div class="banner warn"><div class="grow"><b>{t('Sessione scaduta')}</b>{t('I tuoi dati sono al sicuro sul telefono; accedi di nuovo per sincronizzarli.')}</div><a class="btn small" href="/auth/login">{t('Accedi')}</a></div>
			</div>
		{/if}
		{#key route.parts.join('/')}
			{#if tab === 'home'}
				<Home />
			{:else if tab === 'invito'}
				<Invite code={route.parts[1] ?? ''} />
			{:else if tab === 'macchinari'}
				{#if route.parts[1]}<ExerciseDetail id={route.parts[1]} />{:else}<Maxes />{/if}
			{:else if tab === 'allenamento'}
				{#if route.parts[2] === 'riepilogo'}<Summary id={route.parts[1]} />
				{:else if route.parts[1]}<Session id={route.parts[1]} />
				{:else}<Workouts />{/if}
			{:else if tab === 'progressi'}
				<Progress />
			{:else if tab === 'amici'}
				{#if route.parts[1]}<FriendProfile id={route.parts[1]} />{:else}<Friends />{/if}
			{:else if route.parts[1] === 'archivio'}
				<Archived />
			{:else}
				<Settings />
			{/if}
		{/key}
		{#if !deep && tab !== 'impostazioni'}
			<nav class="tabbar"><div class="tabbar-in">
				{#each tabs as x}
					<button class="tab" class:on={tab === x.id} onclick={() => go(x.id)} data-tour="tab-{x.id}"><Icon name={x.icon} />{t(x.label)}</button>
				{/each}
			</div></nav>
		{/if}
	{/if}
	<!-- la prima volta: prima l'obiettivo, poi il tutorial -->
	{#if !loading && !showFirstRun && S.synced && !m.settings.weeklyGoal}<GoalPicker onclose={() => {}} />
	{:else if !loading && !showFirstRun && S.synced && (!m.settings.tutorialDone || m.settings.tourSeen < 2)}<Tour />{/if}
	{#if T.msg}<div class="toast" role="status">{T.msg}</div>{/if}
</div>
