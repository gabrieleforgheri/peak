<script lang="ts">
	import { itemsFromWorkout } from '../lib/actions.ts'
	import { weekStart, weeklyGoal, weeklyStreak } from '@common/stats.ts'
	import { V, duration, setLine } from '../lib/fmt.ts'
	import { MEDAL, TIERS, fmtMedal, medalsAt } from '../lib/medalinfo.ts'
	import { locale, t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import Icon from '../components/Icon.svelte'
	import TemplateEditor from '../components/TemplateEditor.svelte'
	import MuscleFigure from '../components/MuscleFigure.svelte'

	let { id }: { id: string } = $props()
	const m = $derived(getModel())
	const wk = $derived(m.wkById.get(id))
	const prs = $derived(wk?.sets.filter((s) => s.isPR) ?? [])
	let saving = $state(false)
	const exCount = $derived(new Set(wk?.sets.map((s) => s.exercise).filter(Boolean)).size)
	// i muscoli di tutta la seduta, senza doppioni: una vista per volta, come nelle schede
	const muscles = $derived([...new Set(wk?.sets.flatMap((s) => (s.exercise ? (m.exById.get(s.exercise)?.figure.muscles ?? []) : [])) ?? [])])
	let side = $state<'front' | 'back'>('front')

	// ---- la settimana di questo allenamento: giorni fatti, obiettivo, settimane di fila ----
	const done = $derived(m.workouts.filter((w) => !w.isActive && wk && w.dateMs <= wk.dateMs))
	const ws = $derived(wk ? weekStart(wk.dateMs) : 0)
	const thisWeek = $derived(done.filter((w) => weekStart(w.dateMs) === ws))
	const trainedDays = $derived(new Set(thisWeek.map((w) => (new Date(w.dateMs).getDay() + 6) % 7)))
	const todayIdx = $derived(wk ? (new Date(wk.dateMs).getDay() + 6) % 7 : 0)
	const dayNames = Array.from({ length: 7 }, (_, i) => new Intl.DateTimeFormat(locale, { weekday: 'narrow' }).format(new Date(2024, 0, 1 + i)))
	const goal = $derived(m.settings.weeklyGoal || weeklyGoal(done.map((w) => ({ date: w.dateMs }))))
	const streakNow = $derived(weeklyStreak(done.map((w) => ({ date: w.dateMs })), wk?.dateMs))
	const streakBefore = $derived(weeklyStreak(done.filter((w) => w.uuid !== wk?.uuid).map((w) => ({ date: w.dateMs })), wk?.dateMs))

	// ---- medaglie che questo allenamento ha fatto salire ----
	const moved = $derived.by(() => {
		if (!wk) return []
		const before = medalsAt(m, 0, { ms: wk.dateMs, inclusive: false })
		return medalsAt(m, 0, { ms: wk.dateMs, inclusive: true }).map((a, i) => ({ a, b: before[i]! })).filter((x) => x.a.value > x.b.value)
	})
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
		<div class="sec-title">{t('La tua settimana')}</div>
		<div class="card stack">
			<div class="row between">
				<span class="row" style="gap:10px"><span class="fl"><Icon name="flame" size={22} /></span><span><b class="num" style="font-size:22px">{streakNow}</b> <span class="muted">{streakNow === 1 ? t('settimana di fila') : t('settimane di fila')}</span></span></span>
				{#if streakNow > streakBefore}<span class="badge accent">+1</span>{/if}
			</div>
			<div class="days">
				{#each dayNames as d, i}
					<div class="d" class:on={trainedDays.has(i)} class:now={i === todayIdx}><span class="c">{#if trainedDays.has(i)}<Icon name="check" size={16} />{/if}</span><span class="tiny muted">{d}</span></div>
				{/each}
			</div>
			<div>
				<div class="row between small" style="margin-bottom:6px"><span class="muted">{thisWeek.length >= goal ? t('Obiettivo raggiunto!') : t('Obiettivo della settimana')}</span><b class="num">{thisWeek.length} / {goal}</b></div>
				<div class="segs">{#each Array(goal) as _, k}<i class:on={k < thisWeek.length}></i>{/each}</div>
				{#if thisWeek.length > goal}<div class="small muted" style="margin-top:6px">{tn(thisWeek.length - goal, '+1 oltre l\'obiettivo', '+{0} oltre l\'obiettivo')}</div>{/if}
			</div>
		</div>
		{#if moved.length}
			<div class="sec-title">{t('Medaglie')}</div>
			<div class="card list">
				{#each moved as { a, b } (a.id)}
					{@const from = a.tier > b.tier ? 0 : Math.round(b.progress * 100)}
					<div class="item" style="flex-direction:column;align-items:stretch;gap:6px">
						<div class="row between"><span class="row" style="gap:10px"><Icon name={MEDAL[a.id].icon} size={20} /><b>{MEDAL[a.id].title}</b></span>
							{#if a.tier > b.tier}<span class="badge accent">{t('Nuovo livello')}: {TIERS[a.tier]}</span>{:else}<span class="small muted num">{fmtMedal(a.id, a.value)}{a.next === null ? '' : ` / ${fmtMedal(a.id, a.next)}`}</span>{/if}</div>
						<div class="bar2"><i style="width:{from}%"></i><i class="gain" style="left:{from}%;width:{Math.max(0, Math.round(a.progress * 100) - from)}%"></i></div>
					</div>
				{/each}
			</div>
		{/if}
		{#if muscles.length}
			<div class="sec-title">{t('Muscoli lavorati')}</div>
			<div class="card" style="display:flex;flex-direction:column;align-items:center;gap:10px;padding:12px">
				<div style="width:200px"><MuscleFigure {muscles} view={side} zoom={false} /></div>
				<button class="chip" onclick={() => (side = side === 'front' ? 'back' : 'front')}>{side === 'front' ? t('Vedi di schiena') : t('Vedi di fronte')}</button>
			</div>
		{/if}
		{#if prs.length}
			<div class="sec-title">{t('Record di oggi')}</div>
			<div class="card list">
				{#each prs as s (s.uuid)}
					<div class="item"><div class="grow"><b>{m.exById.get(s.exercise ?? '')?.name ?? t('Esercizio rimosso')}</b></div><span class="num">{setLine(s.weight, s.reps)}</span><span class="badge pos">PR</span></div>
				{/each}
			</div>
		{/if}
		{#if exCount}<button class="btn block" style="margin-top:24px" onclick={() => (saving = true)}><Icon name="list" size={20} />{t('Salva come giorno')}</button>{/if}
		<button class="btn primary block" style="margin-top:12px" onclick={() => go('allenamenti', true)}>{t('Fine')}</button>
		{#if saving}<TemplateEditor initial={{ name: wk.name, items: itemsFromWorkout(wk) }} onclose={() => (saving = false)} onsaved={() => toast(t('Giorno salvato.'))} />{/if}
	{/if}
</main>

<style>
	.fl { width: 38px; height: 38px; border-radius: 12px; display: grid; place-items: center; background: rgba(var(--accent-rgb), .16); color: var(--accent); }
	.days { display: flex; justify-content: space-between; }
	.d { display: flex; flex-direction: column; align-items: center; gap: 4px; }
	.d .c { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--card-strong); color: var(--on-accent); }
	.d.on .c { background: var(--accent); }
	.d.now .c { box-shadow: 0 0 0 2px var(--bg, #0b0e16), 0 0 0 4px rgba(var(--accent-rgb), .7); }
	.segs { display: flex; gap: 6px; }
	.segs i { flex: 1; height: 12px; border-radius: 6px; background: var(--card-strong); }
	.segs i.on { background: linear-gradient(90deg, rgba(var(--accent-rgb), .7), rgb(var(--accent-rgb))); }
	.bar2 { position: relative; height: 8px; border-radius: 4px; background: var(--stroke); overflow: hidden; }
	.bar2 i { position: absolute; top: 0; bottom: 0; left: 0; background: rgba(var(--accent-rgb), .55); }
	.bar2 i.gain { background: var(--accent); box-shadow: 0 0 10px rgba(var(--accent-rgb), .8); }
</style>
