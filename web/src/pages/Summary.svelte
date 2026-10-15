<script lang="ts">
	import { itemsFromWorkout } from '../lib/actions.ts'
	import { V, duration, setLine } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import Icon from '../components/Icon.svelte'
	import TemplateEditor from '../components/TemplateEditor.svelte'

	let { id }: { id: string } = $props()
	const m = $derived(getModel())
	const wk = $derived(m.wkById.get(id))
	const prs = $derived(wk?.sets.filter((s) => s.isPR) ?? [])
	let saving = $state(false)
	const exCount = $derived(new Set(wk?.sets.map((s) => s.exercise).filter(Boolean)).size)
</script>

<main class="screen no-tabs">
	{#if !wk}
		<div class="empty">{t('Allenamento non trovato.')}</div>
		<button class="btn block" onclick={() => go('allenamenti', true)}>{t('Fine')}</button>
	{:else}
		<div class="bar"><h1>{prs.length ? t('Nuovi massimali!') : t('Allenamento concluso')}</h1></div>
		<div class="tiles">
			<div class="card tile"><div class="tiny muted">{t('Durata')}</div><div class="big num">{wk.endedMs ? duration((wk.endedMs - wk.dateMs) / 1000) : '—'}</div></div>
			<div class="card tile"><div class="tiny muted">{t('Volume')}</div><div class="big num">{V(wk.volume)}</div></div>
			<div class="card tile"><div class="tiny muted">{t('Serie')}</div><div class="big num">{wk.workingSets}</div></div>
			<div class="card tile"><div class="tiny muted">{t('Esercizi')}</div><div class="big num">{exCount}</div></div>
		</div>
		{#if prs.length}
			<div class="sec-title">{t('Record di oggi')}</div>
			<div class="card list">
				{#each prs as s (s.uuid)}
					<div class="item"><div class="grow"><b>{m.exById.get(s.exercise ?? '')?.name ?? t('Esercizio rimosso')}</b></div><span class="num">{setLine(s.weight, s.reps)}</span><span class="badge pos">PR</span></div>
				{/each}
			</div>
		{/if}
		{#if exCount}<button class="btn block" style="margin-top:24px" onclick={() => (saving = true)}><Icon name="list" size={20} />{t('Salva come scheda')}</button>{/if}
		<button class="btn primary block" style="margin-top:12px" onclick={() => go('allenamenti', true)}>{t('Fine')}</button>
		{#if saving}<TemplateEditor initial={{ name: wk.name, items: itemsFromWorkout(wk) }} onclose={() => (saving = false)} onsaved={() => toast(t('Scheda salvata.'))} />{/if}
	{/if}
</main>
