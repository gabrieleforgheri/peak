<script lang="ts">
	import { onMount } from 'svelte'
	import { copyTemplate } from '../lib/actions.ts'
	import { entryName, loadCatalog } from '../lib/catalog.ts'
	import { GROUP_NAMES, W, relDay } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { back, go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { getProfile, removeFriend, type Max, type Profile } from '../lib/social.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import { oneRepMax } from '@common/domain.ts'
	import Icon from '../components/Icon.svelte'

	let { id }: { id: string } = $props()
	let profile = $state<Profile | null>(null)
	let failed = $state(false)
	let names = $state(new Map<string, { name: string; group: string }>())
	const m = $derived(getModel())

	onMount(async () => {
		const [p, cat] = await Promise.all([getProfile(Number(id)), loadCatalog().catch(() => [])])
		if (!p) failed = true
		profile = p
		names = new Map(cat.map((c) => [c.id, { name: entryName(c), group: c.group }]))
	})
	const label = (x: { catalog: string; name: string }) => names.get(x.catalog)?.name ?? x.name
	// i tuoi, per confrontare: il migliore 1RM fra i tuoi esercizi con lo stesso id
	const mine = $derived.by(() => {
		const out = new Map<string, number>()
		for (const e of m.exercises) if (e.catalog && !e.isArchived && e.best) out.set(e.catalog, Math.max(out.get(e.catalog) ?? 0, ...e.records.map((r) => oneRepMax(r.weight, r.reps)), ...e.sets.filter((s) => !s.isWarmup).map((s) => oneRepMax(s.weight, s.reps))))
		return out
	})
	// quanto è più forte lui di te (in % sul tuo 1RM stimato)
	const diff = (x: Max) => { const me = x.catalog ? mine.get(x.catalog) : undefined; return me ? Math.round(((x.oneRm - me) / me) * 100) : null }

	async function copy(tpl: Profile['templates'][number]) {
		const idNew = await copyTemplate(tpl)
		toast(t('Scheda copiata fra le tue.'))
		void idNew
		go('allenamento')
	}
	async function unfriend() {
		if (!profile || !confirm(t('Togliere {0} dagli amici?', profile.username))) return
		await removeFriend(profile.id)
		back('amici')
	}
</script>

<main class="screen no-tabs wide page two">
	<div class="bar span">
		<button class="icon-btn" onclick={() => back('amici')} aria-label={t('Indietro')}><Icon name="left" /></button>
		<h1 class="small">{profile ? '@' + profile.username : ''}</h1>
		<span style="width:40px"></span>
	</div>
	{#if failed}
		<div class="empty span">{t('Non riesco a caricare questo profilo.')}</div>
	{:else if !profile}
		<div class="empty span">…</div>
	{:else}
		<div class="blk">
		<div class="sec-title">{t('Massimali')}</div>
		{#if !profile.maxes.length}<div class="empty">{t('Ancora nessun massimale.')}</div>{:else}
			<div class="card list">
				{#each profile.maxes as x (x.catalog + x.name)}
					{@const d = diff(x)}
					<div class="item">
						<span style="width:10px;height:10px;border-radius:50%;flex:none;background:var(--{names.get(x.catalog)?.group ?? 'core'})"></span>
						<div class="grow" style="min-width:0"><b class="ellipsis" style="display:block">{label(x)}</b><div class="small muted ellipsis">{x.catalog && label(x) !== x.name ? x.name + ' · ' : ''}{relDay(x.date)}</div></div>
						<div style="text-align:right;flex:none"><b class="num">{W(x.weight)} × {x.reps}</b>{#if d !== null && d !== 0}<div class="small num muted">{d > 0 ? t('{0}% sopra di te', d) : t('{0}% sotto di te', -d)}</div>{/if}</div>
					</div>
				{/each}
			</div>
		{/if}

		</div>

		<div class="blk">
		<div class="sec-title">{t('Schede')}</div>
		{#if !profile.templates.length}<div class="empty">{t('Nessuna scheda condivisa.')}</div>{:else}
			<div class="stack">
				{#each profile.templates as tpl (tpl.uuid)}
					<div class="card">
						<div class="row between"><b>{tpl.name}</b><span class="small muted">{tn(tpl.rows.length, '1 esercizio', '{0} esercizi')}</span></div>
						<div class="stack" style="gap:4px;margin:8px 0 12px">
							{#each tpl.rows as r}
								<div class="row between small"><span class="ellipsis">{label(r)}</span><span class="muted num">{r.sets ? r.sets + '×' : ''}{r.repsMax ? (r.repsMin === r.repsMax ? r.repsMax : r.repsMin + '–' + r.repsMax) : ''}</span></div>
							{/each}
						</div>
						<button class="btn block" onclick={() => copy(tpl)}>{t('Copia nelle mie schede')}</button>
					</div>
				{/each}
			</div>
		{/if}

		</div>

		<button class="btn danger block span out" onclick={unfriend}>{t('Togli dagli amici')}</button>
	{/if}
</main>

<style>
	.btn.out { margin-top: 20px; }
	/* a schermo largo il bottone non si stira su tutta la riga */
	@media (min-width: 900px) { .btn.out { width: auto; min-width: 260px; justify-self: start; margin-top: 0; } }
</style>
