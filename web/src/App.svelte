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
	import Settings from './pages/Settings.svelte'
	import Curation from './pages/Curation.svelte'

	const m = $derived(getModel())
	const tab = $derived(route.parts[0] === 'allenamenti' ? 'allenamenti' : route.parts[0] === 'progressi' ? 'progressi' : route.parts[0] === 'impostazioni' ? 'impostazioni' : 'massimali')
	const deep = $derived(route.parts.length > 1)
	const tabs = [
		{ id: 'massimali', icon: 'trophy', label: 'Massimali' },
		{ id: 'allenamenti', icon: 'dumbbell', label: 'Allenamenti' },
		{ id: 'progressi', icon: 'chart', label: 'Progressi' },
		{ id: 'impostazioni', icon: 'sliders', label: 'Impostazioni' },
	]

	$effect(() => { document.documentElement.dataset.accent = m.settings.accent })
	const showFirstRun = $derived(S.ready && S.synced && !m.hasData)
	const loading = $derived(!S.ready || (!S.synced && !m.hasData && S.auth !== 'expired'))
</script>

<div class="app">
	{#if S.error === 'offline' && !S.user}
		<main class="screen no-tabs"><div class="empty"><div class="mid">{t('Senza rete')}</div>{t('Apri Peak una volta con la connessione, poi funzionerà anche offline.')}</div></main>
	{:else if loading}
		<main class="screen no-tabs"><div class="empty">{t('Carico i tuoi dati…')}</div></main>
	{:else if showFirstRun}
		<FirstRun />
	{:else}
		{#if S.auth === 'expired'}
			<div class="screen" style="padding-bottom:0;padding-top:calc(env(safe-area-inset-top) + 8px);margin-bottom:-4px">
				<div class="banner warn"><div class="grow"><b>{t('Sessione scaduta')}</b>{t('I tuoi dati sono al sicuro sul telefono; accedi di nuovo per sincronizzarli.')}</div><a class="btn small" href="/auth/login">{t('Accedi')}</a></div>
			</div>
		{/if}
		{#key route.parts.join('/')}
			{#if tab === 'massimali'}
				{#if route.parts[1]}<ExerciseDetail id={route.parts[1]} />{:else}<Maxes />{/if}
			{:else if tab === 'allenamenti'}
				{#if route.parts[2] === 'riepilogo'}<Summary id={route.parts[1]} />
				{:else if route.parts[1]}<Session id={route.parts[1]} />
				{:else}<Workouts />{/if}
			{:else if tab === 'progressi'}
				<Progress />
			{:else if route.parts[1] === 'catalogo' || route.parts[1] === 'archivio'}
				<Curation archived={route.parts[1] === 'archivio'} />
			{:else}
				<Settings />
			{/if}
		{/key}
		{#if !deep}
			<nav class="tabbar"><div class="tabbar-in">
				{#each tabs as x}
					<button class="tab" class:on={tab === x.id} onclick={() => go(x.id)}><Icon name={x.icon} />{t(x.label)}</button>
				{/each}
			</div></nav>
		{/if}
	{/if}
	{#if T.msg}<div class="toast" role="status">{T.msg}</div>{/if}
</div>
