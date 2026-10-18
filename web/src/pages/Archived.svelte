<script lang="ts">
	import { MUSCLE_GROUPS } from '@common/docs.ts'
	import { patchExercise } from '../lib/actions.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { back } from '../lib/router.svelte.ts'
	import Icon from '../components/Icon.svelte'
	import Thumb from '../components/Thumb.svelte'

	const m = $derived(getModel())
	let query = $state('')

	const matches = (n: string) => n.toLowerCase().includes(query.trim().toLowerCase())
	const list = $derived(m.exercises.filter((e) => matches(e.name) && e.isArchived))
	const groups = $derived(MUSCLE_GROUPS.map((g) => ({ g, items: list.filter((e) => e.muscleGroup === g) })).filter((x) => x.items.length))

</script>

<main class="screen no-tabs wide">
	<div class="bar">
		<button class="icon-btn" onclick={() => back('impostazioni')} aria-label={t('Indietro')}><Icon name="left" /></button>
		<h1 class="small">{t('Archiviati')}</h1>
		<span style="width:40px"></span>
	</div>
	<input class="input" type="search" placeholder={t('Cerca macchinario')} bind:value={query} style="margin-bottom:8px" />

	{#each groups as { g, items }}
		<div class="sec-title" style="color:var(--{g})">{GROUP_NAMES[g]}</div>
		<div class="card list cells">
			{#each items as ex (ex.uuid)}
				<div class="item">
					<Thumb {ex} />
					<div class="grow ellipsis" style="font-weight:600">{ex.name}</div>
					<button class="btn small" onclick={() => patchExercise(ex.uuid, { isArchived: false })}>{t('Ripristina')}</button>
				</div>
			{/each}
		</div>
	{/each}
	{#if !list.length}<div class="empty">{t('Nessun macchinario archiviato.')}</div>{/if}
</main>

<style>
	/* schermi larghi: la ricerca non si stira su tutta la riga */
	@media (min-width: 900px) { .input { max-width: 360px; } }
</style>
