<script lang="ts">
	import { round2, toDisplay } from '@common/domain.ts'
	import { sessionCount, topMovers, volumeByGroup, weeklyStreak, weeklyVolume, workoutVolume, type StatWorkout } from '@common/stats.ts'
	import { GROUP_NAMES, V, dateShort, delta, relDay, setLine } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import BodyWeightCard from '../components/BodyWeightCard.svelte'

	const m = $derived(getModel())
	// contano solo gli allenamenti conclusi
	const fin = $derived<StatWorkout[]>(m.workouts.filter((w) => !w.isActive).map((w) => ({
		date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup })),
	})))
	const DAY = 86_400_000
	const sessions = $derived(sessionCount(fin, 30))
	const streak = $derived(weeklyStreak(fin))
	const volume30 = $derived(fin.filter((w) => w.date >= Date.now() - 30 * DAY).reduce((v, w) => v + workoutVolume(w), 0))
	const allRecords = $derived(m.exercises.flatMap((e) => e.records.map((r) => ({ ...r, name: e.name }))))
	const records30 = $derived(allRecords.filter((r) => r.date >= Date.now() - 30 * DAY).length)
	const weekly = $derived(weeklyVolume(fin, 12))
	const maxWeek = $derived(Math.max(1, ...weekly.map((w) => w.volume)))
	const groups = $derived(volumeByGroup(fin, (id) => m.exById.get(id)?.muscleGroup, 30))
	const maxGroup = $derived(Math.max(1, ...groups.map((g) => g.volume)))
	const movers = $derived(topMovers(m.exercises.map((e) => ({ id: e.uuid, records: e.records })), 90, 5))
	const latest = $derived([...allRecords].sort((a, b) => b.date - a.date).slice(0, 8))
	const empty = $derived(fin.length === 0 && allRecords.length === 0 && m.bodyweights.length === 0)
</script>

<main class="screen">
	<div class="bar"><h1>{t('Progressi')}</h1></div>
	{#if empty}<p class="muted" style="margin-bottom:12px">{t('Finisci un allenamento o registra un massimale: qui vedrai come cresci.')}</p>{/if}

	<div class="tiles">
		<div class="card tile"><div class="tiny muted">{t('Sessioni 30gg')}</div><div class="big num">{sessions}</div></div>
		<div class="card tile"><div class="tiny muted">{t('Settimane di fila')}</div><div class="big num">{streak}</div></div>
		<div class="card tile"><div class="tiny muted">{t('Volume 30gg')}</div><div class="big num">{V(volume30)}</div></div>
		<div class="card tile"><div class="tiny muted">{t('Record 30gg')}</div><div class="big num">{records30}</div></div>
	</div>

	<div style="margin-top:12px"><BodyWeightCard /></div>

	{#if weekly.length}
		<div class="sec-title">{t('Volume settimanale')}</div>
		<div class="card">
			<div class="bars">{#each weekly as w}<div style="height:{Math.max(3, (w.volume / maxWeek) * 100)}%" title="{dateShort(w.weekStart)}: {V(w.volume)}"></div>{/each}</div>
			<div class="row between tiny faint" style="margin-top:6px"><span>{dateShort(weekly[0].weekStart)}</span><span>{dateShort(weekly.at(-1)!.weekStart)}</span></div>
		</div>
	{/if}

	{#if groups.length}
		<div class="sec-title">{t('Dove va il lavoro')}</div>
		<div class="card stack">
			{#each groups as g}
				<div><div class="row between small"><b style="color:var(--{g.group})">{GROUP_NAMES[g.group]}</b><span class="muted num">{V(g.volume)}</span></div>
					<div class="hbar" style="margin-top:4px"><div style="width:{(g.volume / maxGroup) * 100}%;background:var(--{g.group})"></div></div></div>
			{/each}
		</div>
	{/if}

	{#if movers.length}
		<div class="sec-title">{t('Cresciuti di più')}</div>
		<div class="card list">
			{#each movers as mv (mv.exercise)}
				<div class="item"><div class="grow ellipsis" style="font-weight:600">{m.exById.get(mv.exercise)?.name}</div>
					{#if mv.previous > 0}<span class="badge pos">{delta(mv.deltaPercent)}</span>{:else}<span class="badge accent">{t('NUOVO')}</span>{/if}
				</div>
			{/each}
		</div>
	{/if}

	{#if latest.length}
		<div class="sec-title">{t('Ultimi record')}</div>
		<div class="card list">
			{#each latest as r (r.uuid)}
				<div class="item"><div class="grow"><b class="ellipsis" style="display:block">{r.name}</b><span class="small muted">{relDay(r.date)}</span></div><span class="num">{setLine(r.weight, r.reps)}</span></div>
			{/each}
		</div>
	{/if}
</main>
