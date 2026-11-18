<script lang="ts">
	import { tick } from 'svelte'
	import { searchCatalog, type CatalogEntry } from '@common/catalog.ts'
	import { MUSCLE_GROUPS, type MuscleGroup } from '@common/docs.ts'
	import { addCatalogExercise } from '../lib/actions.ts'
	import { entryName, entryOther, exMatches, loadCatalog } from '../lib/catalog.ts'
	import { GROUP_NAMES, W } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Ex } from '../lib/types.ts'
	import ExerciseEditor from './ExerciseEditor.svelte'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'
	import Thumb from './Thumb.svelte'

	let { focus, onpick, onclose }: { focus: Set<MuscleGroup>; onpick: (ex: Ex) => void; onclose: () => void } = $props()
	const m = getModel()
	let query = $state('')
	let all = $state(false)
	let creating = $state(false)

	const filtering = $derived(focus.size > 0 && !all)
	const pool = $derived(m.exercises.filter((e) => !e.isArchived && (!filtering || focus.has(e.muscleGroup))))
	const searching = $derived(query.trim().length > 0)
	const list = $derived(pool.filter((e) => exMatches(e, query)))

	// il catalogo: solo i macchinari che non hai già, con lo stesso filtro per gruppo dei tuoi
	let catalog = $state<CatalogEntry[]>([])
	loadCatalog().then((l) => (catalog = l), () => {})
	const owned = $derived(new Set(m.exercises.filter((e) => !e.isArchived).map((e) => e.catalog)))
	const fromCatalog = $derived(searching ? searchCatalog(catalog, query, 200).filter((e) => !owned.has(e.id) && (!filtering || focus.has(e.group))).slice(0, 20) : [])
	// nel gestore del tocco il modello si ricalcola solo al tick dopo la scrittura
	const addFromCatalog = async (e: CatalogEntry) => { const id = addCatalogExercise(e); await tick(); const ex = getModel().exById.get(id); if (ex) { onpick(ex); onclose() } }

	// i più usati di recente: le ultime 80 serie, fino a sei macchinari diversi
	const recent = $derived.by(() => {
		const all = m.workouts.flatMap((w) => w.sets).sort((a, b) => b.createdAt - a.createdAt).slice(0, 80)
		const seen = new Set<string>(), out: Ex[] = []
		for (const s of all) {
			const e = s.exercise ? m.exById.get(s.exercise) : undefined
			if (!e || seen.has(e.uuid) || e.isArchived || (filtering && !focus.has(e.muscleGroup))) continue
			seen.add(e.uuid); out.push(e)
			if (out.length === 6) break
		}
		return out
	})
	const groups = $derived(MUSCLE_GROUPS.map((g) => ({ g, items: list.filter((e) => e.muscleGroup === g) })).filter((x) => x.items.length))
</script>

<Sheet title={t('Scegli esercizio')} {onclose}>
	{#snippet action()}<button class="icon-btn" onclick={() => (creating = true)} aria-label={t('Nuovo macchinario')}><Icon name="plus" /></button>{/snippet}
	<div class="stack">
		<div style="position:relative">
			<input class="input" style="padding-left:40px" type="search" placeholder={t('Cerca macchinario')} bind:value={query} />
			<span style="position:absolute;left:12px;top:12px;color:var(--text-3)"><Icon name="search" size={22} /></span>
		</div>
		{#if !searching && recent.length}
			<div class="sec-title" style="margin:4px 0 0">{t('Usati di recente')}</div>
			<div class="card list">
				{#each recent as ex (ex.uuid)}
					<button class="item" onclick={() => { onpick(ex); onclose() }}><Thumb {ex} /><div class="grow ellipsis" style="font-weight:600">{ex.name}</div>{#if ex.best}<span class="num muted">{W(ex.best.weight)}</span>{/if}</button>
				{/each}
			</div>
		{/if}
		{#each groups as { g, items }}
			<div class="sec-title" style="margin:4px 0 0;color:var(--{g})">{GROUP_NAMES[g]}</div>
			<div class="card list">
				{#each items as ex (ex.uuid)}
					<button class="item" onclick={() => { onpick(ex); onclose() }}><Thumb {ex} /><div class="grow ellipsis" style="font-weight:600">{ex.name}</div>{#if ex.best}<span class="num muted">{W(ex.best.weight)}</span>{/if}</button>
				{/each}
			</div>
		{/each}
		{#if fromCatalog.length}
			<div class="sec-title" style="margin:4px 0 0">{t('Dal catalogo')}</div>
			<div class="card list">
				{#each fromCatalog as e (e.id)}
					<button class="item" onclick={() => addFromCatalog(e)}>
						<span style="width:10px;height:10px;border-radius:50%;flex:none;background:var(--{e.group})"></span>
						<div class="grow ellipsis"><div style="font-weight:600">{entryName(e)}</div><div class="small muted ellipsis">{entryOther(e)} · {GROUP_NAMES[e.group]}</div></div>
						<span class="muted"><Icon name="plus" size={20} /></span>
					</button>
				{/each}
			</div>
		{/if}
		{#if !list.length && !fromCatalog.length}<div class="empty">{t('Nessun risultato.')}</div>{/if}
		{#if focus.size > 0 && !all}<button class="btn block" onclick={() => (all = true)}>{t('Mostra tutti i macchinari')}</button>{/if}
	</div>
</Sheet>
{#if creating}<ExerciseEditor name={query.trim()} onclose={() => (creating = false)} onsaved={(id) => { const e = getModel().exById.get(id); if (e) { onpick(e); onclose() } }} />{/if}
