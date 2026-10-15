<script lang="ts">
	import { onMount } from 'svelte'
	import type { MuscleGroup } from '@common/docs.ts'
	import { oneRepMax } from '@common/domain.ts'
	import { addSet, addToPlan, deleteWorkout, endWorkout, itemsFromWorkout, moveInPlan, patchWorkout, removeFromPlan, replaceInPlan, templateChanged, todayItems, updateTemplateFromWorkout } from '../lib/actions.ts'
	import { GROUP_NAMES, V, W, clock, dateTime, duration, setLine, targetLine } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { back, go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { R, shiftRest, startRest, stopRest } from '../lib/timer.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import type { Ex, SetItem } from '../lib/types.ts'
	import AddSetSheet from '../components/AddSetSheet.svelte'
	import ExercisePicker from '../components/ExercisePicker.svelte'
	import FocusPicker from '../components/FocusPicker.svelte'
	import Icon from '../components/Icon.svelte'
	import Sheet from '../components/Sheet.svelte'
	import TemplateEditor from '../components/TemplateEditor.svelte'
	import Thumb from '../components/Thumb.svelte'

	let { id }: { id: string } = $props()
	const m = $derived(getModel())
	const wk = $derived(m.wkById.get(id))

	const store = (k: string) => `peak.${k}.${id}`
	let current = $state<string | null>(sessionStorage.getItem(store('current')))
	$effect(() => { current ? sessionStorage.setItem(store('current'), current) : sessionStorage.removeItem(store('current')) })

	let now = $state(Date.now())
	onMount(() => { const i = setInterval(() => (now = Date.now()), 1000); return () => clearInterval(i) })

	let pickingEx = $state(false)
	let editFocus = $state(false)
	let menuFor = $state<Ex | null>(null)
	let replacing = $state<Ex | null>(null)
	let savingTemplate = $state(false)
	let adding = $state<{ ex: Ex; set: SetItem | null } | null>(null)
	let note = $state('')
	let noteFor = ''
	$effect(() => { if (wk && noteFor !== wk.uuid) { note = wk.note; noteFor = wk.uuid } })

	const focus = $derived(new Set((wk?.focus ?? '').split(',').filter(Boolean) as MuscleGroup[]))
	// la lista di oggi: il piano (scheda o esercizi aggiunti) e, in coda, gli esercizi con serie che nel piano non ci sono
	const items = $derived(wk ? todayItems(wk) : [])
	const orphanSets = $derived(wk?.sets.filter((s) => !s.exercise) ?? [])
	const seconds = $derived(wk ? ((wk.endedMs ?? now) - wk.dateMs) / 1000 : 0)

	function added(ex: Ex, r: { isPR: boolean; isWarmup: boolean }) {
		current = ex.uuid
		if (r.isPR) toast(t('Nuovo massimale!'))
		if (wk?.isActive && m.settings.restTimerEnabled) startRest(ex.restSeconds || m.settings.restSeconds)
	}

	function repeat(ex: Ex) {
		const last = [...(wk?.sets ?? [])].reverse().find((s) => s.exercise === ex.uuid)
		if (!last || !wk) return
		added(ex, addSet(wk.uuid, ex.uuid, last.weight, last.reps, last.isWarmup).isPR ? { isPR: true, isWarmup: last.isWarmup } : { isPR: false, isWarmup: last.isWarmup })
	}

	function finish() {
		if (!wk || !confirm(t('Terminare l\'allenamento?'))) return
		const tpl = templateChanged(wk)
		endWorkout(wk.uuid)
		stopRest()
		if (tpl && confirm(t('Aggiornare la scheda «{0}» con le modifiche di oggi?', tpl.name))) updateTemplateFromWorkout(tpl, wk)
		go(`allenamenti/${wk.uuid}/riepilogo`, true)
	}
	function remove() {
		if (!wk || !confirm(t('Eliminare questo allenamento e le sue serie?'))) return
		stopRest()
		deleteWorkout(wk.uuid)
		go('allenamenti', true)
	}
</script>

<main class="screen no-tabs">
	{#if !wk}
		<div class="bar"><button class="icon-btn" onclick={() => back('allenamenti')} aria-label={t('Indietro')}><Icon name="left" /></button></div>
		<div class="empty">{t('Allenamento non trovato.')}</div>
	{:else}
		<div class="bar">
			<button class="icon-btn" onclick={() => back('allenamenti')} aria-label={t('Indietro')}><Icon name="left" /></button>
			<h1 class="small ellipsis">{wk.name.trim() || dateTime(wk.dateMs)}</h1>
			<button class="icon-btn" onclick={remove} aria-label={t('Elimina')}><Icon name="trash" /></button>
		</div>

		{#if R.endsAt && wk.isActive}
			<div class="card rest" style="border-color:rgba(var(--accent-rgb),.5);background:rgba(18,21,31,.92)">
				<div class="row">
					<Icon name="timer" size={26} />
					<div class="grow"><div class="tiny muted" style="font-weight:800;letter-spacing:.08em">{t('RECUPERO')}</div><div class="mid num">{clock(R.left)}</div></div>
					<button class="btn small" onclick={() => shiftRest(-15)}>−15</button>
					<button class="btn small" onclick={() => shiftRest(15)}>+15</button>
					<button class="btn small" onclick={stopRest}>{t('Salta')}</button>
				</div>
			</div>
		{/if}

		<div class="card">
			<div class="row between">
				<span class="tiny" style="font-weight:800;letter-spacing:.08em;color:{wk.isActive ? 'var(--accent)' : 'var(--text-2)'}">{wk.isActive ? t('IN CORSO') : t('CONCLUSO')}</span>
				<span class="small muted num">{dateTime(wk.dateMs)} · {duration(seconds)}</span>
			</div>
			<div class="row between" style="margin-top:10px;text-align:center">
				<div><div class="mid num">{wk.workingSets}</div><div class="tiny muted">{t('serie')}</div></div>
				<div><div class="mid num">{V(wk.volume)}</div><div class="tiny muted">{t('volume')}</div></div>
				<div><div class="mid num">{wk.prCount}</div><div class="tiny muted">{t('record')}</div></div>
			</div>
			{#if focus.size || wk.isActive}
				<div class="row wrap" style="margin-top:10px;gap:6px">
					{#each [...focus] as g}<span class="badge" style="color:var(--{g});background:color-mix(in srgb, var(--{g}) 16%, transparent)">{GROUP_NAMES[g]}</span>{/each}
					{#if wk.isActive}<button class="link small" onclick={() => (editFocus = true)}>{t('Modifica')}</button>{/if}
				</div>
			{/if}
		</div>

		{#each items as it (it.exercise)}
			{@const ex = m.exById.get(it.exercise)!}
			{@const sets = wk.sets.filter((s) => s.exercise === ex.uuid)}
			{@const goal = targetLine(it, sets.filter((s) => !s.isWarmup).length, ex.sets[0]?.weight ?? null)}
			<div class="card" style={current === ex.uuid && wk.isActive ? 'border-color:rgba(var(--accent-rgb),.45)' : ''}>
				<div class="row"><button class="row grow" style="background:none;border:0;text-align:left;padding:0;min-width:0" onclick={() => (current = ex.uuid)}>
					<Thumb {ex} />
					<div class="grow"><b class="ellipsis" style="display:block">{ex.name}</b>
						{#if current === ex.uuid && wk.isActive}<span class="tiny" style="color:var(--accent);font-weight:800;letter-spacing:.08em">{t('IN CORSO')}</span>
						{:else if ex.best}<span class="small muted num">{t('max')} {setLine(ex.best.weight, ex.best.reps)}</span>{/if}
						{#if goal}<span class="small muted num" style="display:block">{goal}</span>{/if}</div>
				</button>
				{#if wk.isActive}<button class="icon-btn" onclick={() => (menuFor = ex)} aria-label={t('Altro')}><Icon name="more" /></button>{/if}</div>
				{#if sets.length}
					<div class="list" style="margin-top:10px">
						{#each sets as s, i (s.uuid)}
							<button class="item" onclick={() => (adding = { ex, set: s })}>
								<span class="muted num" style="width:20px">{i + 1}</span>
								<div class="grow num" style="font-weight:700">{setLine(s.weight, s.reps)}</div>
								{#if s.isWarmup}<span class="badge">{t('risc.')}</span>{/if}
								{#if s.isPR}<span class="badge pos">PR</span>{/if}
								{#if s.reps > 1 && !s.isWarmup}<span class="small muted num">≈ {W(oneRepMax(m.settings.formula, s.weight, s.reps))}</span>{/if}
							</button>
						{/each}
					</div>
				{/if}
				<div class="row" style="margin-top:12px">
					<button class="btn small grow" onclick={() => (adding = { ex, set: null })}><Icon name="plus" size={18} />{t('Serie')}</button>
					{#if sets.length}<button class="btn small" onclick={() => repeat(ex)} aria-label={t('Ripeti l\'ultima serie')}><Icon name="repeat" size={18} /></button>{/if}
				</div>
			</div>
		{/each}

		{#if orphanSets.length}
			<div class="card">
				<b class="muted">{t('Esercizio rimosso')}</b>
				<div class="list" style="margin-top:8px">{#each orphanSets as s (s.uuid)}<div class="item num"><div class="grow">{setLine(s.weight, s.reps)}</div>{#if s.isWarmup}<span class="badge">{t('risc.')}</span>{/if}</div>{/each}</div>
			</div>
		{/if}

		<button class="btn block" style="margin-top:12px" onclick={() => (pickingEx = true)}><Icon name="plus" size={20} />{t('Aggiungi esercizio')}</button>

		{#if items.length}<button class="btn block" style="margin-top:12px" onclick={() => (savingTemplate = true)}><Icon name="list" size={20} />{t('Salva come scheda')}</button>{/if}

		<label class="field" style="margin-top:16px"><span>{t('Note')}</span><textarea class="textarea" bind:value={note} onchange={() => patchWorkout(wk.uuid, { note })} maxlength="2000" placeholder={t('Com\'è andata?')}></textarea></label>

		{#if wk.isActive}<button class="btn primary block" style="margin-top:16px" onclick={finish}>{t('Termina allenamento')}</button>
		{:else}<button class="btn block" style="margin-top:16px" onclick={() => go(`allenamenti/${wk.uuid}/riepilogo`)}>{t('Riepilogo')}</button>{/if}
	{/if}
</main>

{#if pickingEx && wk}<ExercisePicker {focus} onclose={() => (pickingEx = false)} onpick={(ex) => { addToPlan(wk, ex.uuid); current = ex.uuid; adding = { ex, set: null } }} />{/if}
{#if replacing && wk}<ExercisePicker {focus} onclose={() => (replacing = null)} onpick={(ex) => { if (items.some((i) => i.exercise === ex.uuid)) { toast(t('È già in lista.')); return } replaceInPlan(wk, replacing!.uuid, ex.uuid); current = ex.uuid }} />{/if}
{#if menuFor && wk}
	{@const ex = menuFor}
	{@const idx = items.findIndex((i) => i.exercise === ex.uuid)}
	{@const empty = !wk.sets.some((s) => s.exercise === ex.uuid)}
	<Sheet title={ex.name} onclose={() => (menuFor = null)}>
		<div class="stack">
			<div class="row">
				<button class="btn grow" disabled={idx <= 0} onclick={() => moveInPlan(wk, ex.uuid, -1)}><Icon name="up" size={18} />{t('Su')}</button>
				<button class="btn grow" disabled={idx < 0 || idx >= items.length - 1} onclick={() => moveInPlan(wk, ex.uuid, 1)}><Icon name="down" size={18} />{t('Giù')}</button>
			</div>
			{#if empty}
				<button class="btn block" onclick={() => { replacing = ex; menuFor = null }}><Icon name="repeat" size={18} />{t('Sostituisci')}</button>
				<button class="btn danger block" onclick={() => { removeFromPlan(wk, ex.uuid); menuFor = null }}><Icon name="trash" size={18} />{t('Togli dalla lista')}</button>
			{:else}<p class="small muted">{t('Ha già delle serie: per cambiarlo eliminale prima.')}</p>{/if}
		</div>
	</Sheet>
{/if}
{#if savingTemplate && wk}<TemplateEditor initial={{ name: wk.name, items: itemsFromWorkout(wk) }} onclose={() => (savingTemplate = false)} onsaved={() => toast(t('Scheda salvata.'))} />{/if}
{#if editFocus && wk}<FocusPicker workout={wk} onclose={() => (editFocus = false)} />{/if}
{#if adding && wk}<AddSetSheet workout={wk} ex={adding.ex} set={adding.set} onclose={() => (adding = null)} onadded={(r) => added(adding!.ex, r)} />{/if}
