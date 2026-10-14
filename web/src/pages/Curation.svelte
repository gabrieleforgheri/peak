<script lang="ts">
	import { MUSCLE_GROUPS } from '@common/docs.ts'
	import { patchExercise } from '../lib/actions.ts'
	import { batch, getModel } from '../lib/store.svelte.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { back } from '../lib/router.svelte.ts'
	import Icon from '../components/Icon.svelte'
	import Thumb from '../components/Thumb.svelte'

	let { archived = false }: { archived?: boolean } = $props()
	const m = $derived(getModel())
	let query = $state('')

	const matches = (n: string) => n.toLowerCase().includes(query.trim().toLowerCase())
	const list = $derived(m.exercises.filter((e) => matches(e.name) && (!archived || e.isArchived)))
	const groups = $derived(MUSCLE_GROUPS.map((g) => ({ g, items: list.filter((e) => e.muscleGroup === g) })).filter((x) => x.items.length))
	const activeCount = $derived(m.exercises.filter((e) => !e.isArchived).length)

	const setAll = (g: string, on: boolean) => batch(() => { for (const e of m.exercises) if (e.muscleGroup === g && matches(e.name)) patchExercise(e.uuid, { isArchived: !on }) })
</script>

<main class="screen no-tabs">
	<div class="bar">
		<button class="icon-btn" onclick={() => back('impostazioni')} aria-label={t('Indietro')}><Icon name="left" /></button>
		<h1 class="small">{archived ? t('Archiviati') : t('La tua palestra')}</h1>
		<span style="width:40px"></span>
	</div>
	{#if !archived}<p class="muted small" style="margin-bottom:10px">{t('Togli la spunta a quello che la tua palestra non ha: sparisce dalle liste senza perdere nulla. {0} su {1} macchinari attivi.', activeCount, m.exercises.length)}</p>{/if}
	<input class="input" type="search" placeholder={t('Cerca macchinario')} bind:value={query} style="margin-bottom:8px" />

	{#each groups as { g, items }}
		<div class="sec-title row between" style="color:var(--{g})">
			<span>{GROUP_NAMES[g]}</span>
			{#if !archived}<span class="row" style="gap:12px"><button class="link small" onclick={() => setAll(g, true)}>{t('Tutti')}</button><button class="link small" onclick={() => setAll(g, false)}>{t('Nessuno')}</button></span>{/if}
		</div>
		<div class="card list">
			{#each items as ex (ex.uuid)}
				<div class="item">
					<Thumb {ex} />
					<div class="grow ellipsis" style="font-weight:600;{ex.isArchived && !archived ? 'opacity:.5' : ''}">{ex.name}</div>
					{#if archived}
						<button class="btn small" onclick={() => patchExercise(ex.uuid, { isArchived: false })}>{t('Ripristina')}</button>
					{:else}
						<input class="toggle" type="checkbox" checked={!ex.isArchived} onchange={(e) => patchExercise(ex.uuid, { isArchived: !e.currentTarget.checked })} />
					{/if}
				</div>
			{/each}
		</div>
	{/each}
	{#if archived && !list.length}<div class="empty">{t('Nessun macchinario archiviato.')}</div>{/if}
</main>
