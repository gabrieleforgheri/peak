<script lang="ts">
	import { searchCatalog, type CatalogEntry } from '@common/catalog.ts'
	import { MUSCLE_GROUPS, type MuscleGroup } from '@common/docs.ts'
	import { entryName, entryOther, loadCatalog } from '../lib/catalog.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'

	/** Prima il gruppo muscolare, poi la ricerca in quel gruppo. `onpick(null, gruppo)` = lo aggiungi a mano, fuori dal catalogo. */
	let { title = t('Scegli il macchinario'), custom = true, group = null, onpick, onclose }: {
		title?: string; custom?: boolean; group?: MuscleGroup | null; onpick: (entry: CatalogEntry | null, group: MuscleGroup) => void; onclose: () => void
	} = $props()
	let grp = $state<MuscleGroup | null>(group)

	let all = $state<CatalogEntry[] | null>(null)
	let failed = $state(false)
	let query = $state('')
	loadCatalog().then((l) => (all = l), () => (failed = true))

	const owned = $derived(new Set(getModel().exercises.filter((e) => !e.isArchived).map((e) => e.catalog)))
	const results = $derived(all && grp ? searchCatalog(all, query, 40, grp) : [])
</script>

<Sheet {title} {onclose}>
	{#if !grp}
		<div class="stack">
			<p class="muted small" style="margin:0 4px">{t('Che muscoli lavora?')}</p>
			<div class="tiles">
				{#each MUSCLE_GROUPS as g}
					<button class="chip" onclick={() => (grp = g)} style="min-height:56px;border-radius:14px;color:var(--{g})">{GROUP_NAMES[g]}</button>
				{/each}
			</div>
		</div>
	{:else}
	<div class="stack">
		<div class="row between"><button class="chip" onclick={() => { grp = null; query = '' }}><span style="color:var(--{grp})">{GROUP_NAMES[grp]}</span> · {t('Cambia')}</button></div>
		<div style="position:relative">
			<!-- svelte-ignore a11y_autofocus -->
			<input class="input" style="padding-left:40px" type="search" placeholder={t('Cerca nel catalogo')} bind:value={query} autofocus />
			<span style="position:absolute;left:12px;top:12px;color:var(--text-3)"><Icon name="search" size={22} /></span>
		</div>
		{#if failed}
			<div class="empty">{t('Il catalogo non si carica: serve la connessione.')}</div>
		{:else if !all}
			<div class="empty">…</div>
		{:else}
			<div class="card list">
				{#each results as e (e.id)}
					<button class="item" onclick={() => onpick(e, e.group)}>
						<span style="width:10px;height:10px;border-radius:50%;flex:none;background:var(--{e.group})"></span>
						<div class="grow ellipsis"><div style="font-weight:600">{entryName(e)}</div><div class="small muted ellipsis">{entryOther(e)}</div></div>
						{#if owned.has(e.id)}<span class="small faint">{t('Ce l\'hai già')}</span>{/if}
					</button>
				{/each}
			</div>
			{#if !results.length}<div class="empty">{t('Nessun risultato.')}</div>{/if}
			{#if custom}
				<button class="btn block" onclick={() => onpick(null, grp!)}>{t('Non c\'è: crea un macchinario personalizzato')}</button>
				<p class="small faint">{t('Scegli il più vicino se puoi: solo i macchinari del catalogo entrano nelle classifiche con gli amici.')}</p>
			{/if}
		{/if}
	</div>
	{/if}
</Sheet>
