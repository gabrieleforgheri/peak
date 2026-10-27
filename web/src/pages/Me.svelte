<script lang="ts">
	import { onMount } from 'svelte'
	import { oneRepMax } from '@common/domain.ts'
	import { medalTotals } from '@common/medals.ts'
	import { topMovers, volumeByGroup, weekStart, weeklyStreak, workoutVolume, type StatWorkout } from '@common/stats.ts'
	import { GROUP_NAMES, V, W, dateShort, delta, relDay } from '../lib/fmt.ts'
	import { MEDAL, MEDAL_INFO, TIERS, fmtMedal, medalSub, medalsAt } from '../lib/medalinfo.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { F, loadFriends } from '../lib/social.svelte.ts'
	import { S, getModel } from '../lib/store.svelte.ts'
	import BodyWeightCard from '../components/BodyWeightCard.svelte'
	import Donut from '../components/Donut.svelte'
	import Heatmap from '../components/Heatmap.svelte'
	import Icon from '../components/Icon.svelte'
	import Medal from '../components/Medal.svelte'
	import LineChart from '../components/LineChart.svelte'
	import WeekBars from '../components/WeekBars.svelte'

	const m = $derived(getModel())
	onMount(() => { void loadFriends() })

	const name = $derived(S.user?.nome || S.user?.username || 'Peak')
	const finished = $derived(m.workouts.filter((w) => !w.isActive))
	const stat = $derived<StatWorkout[]>(finished.map((w) => ({ date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup, singleArm: s.singleArm })) })))
	const streak = $derived(weeklyStreak(stat))
	const totalVolume = $derived(stat.reduce((v, w) => v + workoutVolume(w), 0))
	const allRecords = $derived(m.exercises.flatMap((e) => e.records.map((r) => ({ ...r, name: e.name }))))
	const friendCount = $derived(F.friends?.amici.length ?? 0)

	// ---- attività: le serie di ogni giorno ----
	const perDay = $derived.by(() => {
		const out = new Map<string, number>()
		for (const w of finished) {
			const d = new Date(w.dateMs)
			const k = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
			out.set(k, (out.get(k) ?? 0) + w.workingSets)
		}
		return out
	})
	const DAY = 86_400_000
	const last90 = $derived(finished.filter((w) => w.dateMs >= Date.now() - 98 * DAY).length)

	// ---- volume per settimana: tutte le ultime 12, anche vuote ----
	const weeks = $derived.by(() => {
		const start = new Date(weekStart(Date.now()))
		return Array.from({ length: 12 }, (_, i) => {
			const d = new Date(start); d.setDate(start.getDate() - 7 * (11 - i))
			const ws = d.getTime()
			return { ws, volume: stat.filter((w) => weekStart(w.date) === ws).reduce((v, w) => v + workoutVolume(w), 0) }
		})
	})

	const groups = $derived(volumeByGroup(stat, (id) => m.exById.get(id)?.muscleGroup, 30))
	const groupTotal = $derived(groups.reduce((s, g) => s + g.volume, 0))

	// ---- il sollevamento che cresce di più: il tuo miglior macchinario con almeno tre massimali ----
	type Range = '1m' | '3m' | '1y' | 'all'
	let liftRange = $state<Range>('all')

	const liftPick = $derived.by(() => {
		const cands = m.exercises.filter((e) => e.records.length >= 3).map((e) => ({ e, top: Math.max(...e.records.map((r) => oneRepMax(r.weight, r.reps))) }))
		return cands.sort((a, b) => b.top - a.top)[0]?.e ?? null
	})

	const liftSpanDays = $derived.by(() => {
		if (!liftPick || !liftPick.records.length) return 0
		const earliest = Math.min(...liftPick.records.map((r) => r.date))
		return Math.max(0, (Date.now() - earliest) / 86_400_000)
	})

	const liftAvailableRanges = $derived.by((): Range[] => {
		const out: Range[] = []
		if (liftSpanDays >= 30 && liftSpanDays < 365) out.push('1m')
		if (liftSpanDays >= 90) out.push('3m')
		if (liftSpanDays >= 365) out.push('1y')
		out.push('all')
		return out
	})

	$effect(() => {
		if (!liftAvailableRanges.includes(liftRange)) liftRange = liftAvailableRanges[0] ?? 'all'
	})

	const lift = $derived.by(() => {
		if (!liftPick) return null
		const days = liftRange === '1m' ? 30 : liftRange === '3m' ? 90 : liftRange === '1y' ? 365 : null
		const since = days ? Date.now() - days * 86_400_000 : 0
		const recs = [...liftPick.records].filter((r) => r.date >= since).sort((a, b) => a.date - b.date)
		const pts = recs.map((r) => ({ y: oneRepMax(r.weight, r.reps), label: dateShort(r.date) }))
		const ys = pts.map((p) => p.y)
		return {
			name: liftPick.name,
			points: pts,
			domain: (ys.length ? [Math.min(...ys) * 0.97, Math.max(...ys) * 1.03] : [0, 1]) as [number, number],
			from: ys[0] ?? 0,
			to: ys.at(-1) ?? 0
		}
	})

	const medalList = $derived(medalsAt(m, friendCount))
	const totals = $derived(medalTotals(medalList))
	const nextLine = (md: (typeof medalList)[number]) => md.next === null ? t('Oro conquistato.') : t('Mancano {0} per {1}.', fmtMedal(md.id, md.next - md.value), TIERS[md.tier + 1])

	const movers = $derived(topMovers(m.exercises.map((e) => ({ id: e.uuid, records: e.records })), 90, 5))
	const latest = $derived([...allRecords].sort((a, b) => b.date - a.date).slice(0, 4))
</script>

<main class="screen page">
	<header class="head span">
		<div class="av">{name.slice(0, 1).toUpperCase()}</div>
		<div class="grow">
			<h1 class="ellipsis">{name}</h1>
			{#if S.user?.username}<div class="small muted">@{S.user.username}</div>{/if}
		</div>
		<button class="icon-btn" onclick={() => go('impostazioni')} aria-label={t('Impostazioni')} data-tour="settings"><Icon name="settings" /></button>
	</header>

	<div class="row friends span">
		<button class="btn block grow" onclick={() => go('amici')}><Icon name="users" size={18} />{friendCount ? tn(friendCount, '1 amico', '{0} amici') : t('Aggiungi amici')}</button>
	</div>

	<div class="blk">
		<div class="sec-title">{t('Panoramica')}</div>
		<div class="card ov">
			<div><Icon name="flame" size={22} /><div><b class="num">{streak}</b><span class="small muted">{t('Settimane di fila')}</span></div></div>
			<div><Icon name="dumbbell" size={22} /><div><b class="num">{finished.length}</b><span class="small muted">{t('Allenamenti')}</span></div></div>
			<div><Icon name="scale" size={22} /><div><b class="num">{V(totalVolume)}</b><span class="small muted">{t('Sollevati')}</span></div></div>
			<div><Icon name="trophy" size={22} /><div><b class="num">{allRecords.length}</b><span class="small muted">{t('Record')}</span></div></div>
		</div>
	</div>

	{#if latest.length}
		<div class="blk">
			<div class="sec-title">{t('Record recenti')}</div>
			<div class="card list">
				{#each latest as r (r.uuid)}
					<div class="item"><span class="trophy"><Icon name="trophy" size={18} /></span><div class="grow ellipsis"><b>{r.name}</b><div class="small muted">{relDay(r.date)}</div></div><b class="num">{W(r.weight)} × {r.reps}</b></div>
				{/each}
			</div>
		</div>
	{/if}

	<div class="blk span">
		<div class="row between sec-row"><div class="sec-title">{t('Medaglie')}</div><span class="small muted">{totals.earned} / {totals.total}</span></div>
		<div class="medals">
			{#each medalList as md (md.id)}<Medal medal={md} icon={MEDAL[md.id].icon} title={MEDAL[md.id].title} sub={medalSub(md)} />{/each}
		</div>
		<div class="card list cells" data-tour="medals" style="margin-top:8px">
			{#each medalList as md (md.id)}
				<div class="item" style="flex-direction:column;align-items:stretch;gap:6px">
					<div class="row between"><span class="row" style="gap:10px"><Icon name={MEDAL[md.id].icon} size={20} /><b>{MEDAL[md.id].title}</b></span><span class="small" style="color:var(--accent);font-weight:700">{TIERS[md.tier]}</span></div>
					<div class="small muted">{MEDAL_INFO[md.id]}</div>
					<div class="tiny muted num">{TIERS[1]} {fmtMedal(md.id, md.tiers[0])} · {TIERS[2]} {fmtMedal(md.id, md.tiers[1])} · {TIERS[3]} {fmtMedal(md.id, md.tiers[2])}</div>
					<div style="height:6px;border-radius:3px;background:var(--stroke);overflow:hidden"><div style="height:100%;width:{Math.round(md.progress * 100)}%;background:var(--accent)"></div></div>
					<div class="small">{nextLine(md)}</div>
				</div>
			{/each}
		</div>
	</div>

	<div class="blk">
		<div class="sec-title">{t('Attività')}</div>
		<div class="card">
			<Heatmap sets={perDay} />
			<div class="row between small muted" style="margin-top:10px"><span>{tn(last90, '1 allenamento in 14 settimane', '{0} allenamenti in 14 settimane')}</span></div>
		</div>
	</div>

	<div class="blk">
		<div class="sec-title">{t('Volume settimanale')}</div>
		<div class="card"><WeekBars {weeks} /></div>
	</div>

	{#if groups.length}
		<div class="blk">
			<div class="sec-title">{t('Dove va il lavoro')}</div>
			<div class="card split">
				<div class="donut"><Donut parts={groups.map((g) => ({ color: `var(--${g.group})`, value: g.volume }))} center={V(groupTotal)} label={t('Dove va il lavoro')} /></div>
				<div class="stack" style="gap:8px;flex:1">
					{#each groups as g}<div class="row between small"><span class="row" style="gap:8px"><i class="dot" style="background:var(--{g.group})"></i>{GROUP_NAMES[g.group]}</span><b class="num">{Math.round((g.volume / groupTotal) * 100)}%</b></div>{/each}
					<div class="tiny faint">{t('Ultimi 30 giorni')}</div>
				</div>
			</div>
		</div>
	{/if}

	<div class="blk">
		<div class="sec-title">{t('Peso corporeo')}</div>
		<BodyWeightCard />
	</div>

	{#if lift}
		<div class="blk">
			<div class="sec-title">{t('Il tuo sollevamento in crescita')}</div>
			<div class="card">
				<div class="row between">
					<div>
						<b>{lift.name}</b>
						<div class="small muted">{t('1RM stimato')}</div>
					</div>
					{#if lift.points.length >= 2}
						<span class="badge pos">{W(lift.from)} → {W(lift.to)}</span>
					{/if}
				</div>
				{#if liftAvailableRanges.length > 1}
					<div class="row end" style="margin:8px 0">
						<div class="seg" style="width:auto">
							{#if liftAvailableRanges.includes('1m')}<button class:on={liftRange === '1m'} onclick={() => (liftRange = '1m')}>{t('1 mese')}</button>{/if}
							{#if liftAvailableRanges.includes('3m')}<button class:on={liftRange === '3m'} onclick={() => (liftRange = '3m')}>{t('3 mesi')}</button>{/if}
							{#if liftAvailableRanges.includes('1y')}<button class:on={liftRange === '1y'} onclick={() => (liftRange = '1y')}>{t('1 anno')}</button>{/if}
							<button class:on={liftRange === 'all'} onclick={() => (liftRange = 'all')}>{t('Tutto')}</button>
						</div>
					</div>
				{/if}
				{#if lift.points.length >= 2}
					<LineChart points={lift.points} domain={lift.domain} />
				{:else}
					<div class="small muted center" style="padding:20px 0">{t('Dati non sufficienti per questo intervallo')}</div>
				{/if}
			</div>
		</div>
	{/if}

	{#if movers.length}
		<div class="blk">
			<div class="sec-title">{t('Cresciuti di più')}</div>
			<div class="card list">
				{#each movers as mv (mv.exercise)}
					<div class="item"><div class="grow ellipsis" style="font-weight:600">{m.exById.get(mv.exercise)?.name}</div>
						{#if mv.previous > 0}<span class="badge pos">{delta(mv.deltaPercent)}</span>{:else}<span class="badge accent">{t('NUOVO')}</span>{/if}
					</div>
				{/each}
			</div>
		</div>
	{/if}
</main>

<style>
	.head { display: flex; align-items: center; gap: 14px; }
	.head h1 { margin: 0; font-size: 26px; }
	.av { width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center; font-size: 28px; font-weight: 800; color: var(--on-accent); background: linear-gradient(135deg, var(--accent), rgba(var(--accent-rgb), .55)); box-shadow: 0 6px 18px rgba(var(--accent-rgb), .35); flex: none; }
	.ov { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 12px; }
	.ov > div { display: flex; align-items: center; gap: 12px; color: var(--accent); }
	.ov b { display: block; font-size: 22px; color: var(--text); letter-spacing: -0.02em; }
	.ov span { display: block; }
	.split { display: flex; gap: 18px; align-items: center; }
	.donut { width: 132px; flex: none; color: var(--text); }
	.friends { margin: 14px 0 4px; gap: 10px; }
	@media (min-width: 900px) { .friends { margin: 0; } .friends > .btn { flex: none; width: auto; min-width: 240px; } }
	.trophy { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: rgba(255,212,94,.14); color: #ffd45e; flex: none; }
	.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
</style>
