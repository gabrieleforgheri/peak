<script lang="ts">
	import { onMount } from 'svelte'
	import { oneRepMax } from '@common/domain.ts'
	import { medals, medalTotals, type MedalId } from '@common/medals.ts'
	import { volumeByGroup, weekStart, weeklyStreak, workoutVolume, type StatWorkout } from '@common/stats.ts'
	import { startWorkout } from '../lib/actions.ts'
	import { entryName, loadCatalog } from '../lib/catalog.ts'
	import { GROUP_NAMES, V, W, dateShort, relDay } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { F, loadFriends, loadNews } from '../lib/social.svelte.ts'
	import { S, getModel } from '../lib/store.svelte.ts'
	import Donut from '../components/Donut.svelte'
	import Heatmap from '../components/Heatmap.svelte'
	import SummitHero from '../components/SummitHero.svelte'
	import Icon from '../components/Icon.svelte'
	import LineChart from '../components/LineChart.svelte'
	import Medal from '../components/Medal.svelte'

	const m = $derived(getModel())
	let names = $state(new Map<string, string>())
	onMount(() => { void loadNews(); void loadFriends(); void loadCatalog().then((c) => (names = new Map(c.map((e) => [e.id, entryName(e)])))) })

	// ---- chi sei e cosa tocca ----
	const hour = new Date().getHours()
	const greeting = hour < 12 ? t('Buongiorno') : hour < 18 ? t('Buon pomeriggio') : t('Buonasera')
	const first = $derived((S.user?.nome ?? '').split(' ')[0] || S.user?.username || '')
	const active = $derived(m.workouts.find((w) => w.isActive))
	const finished = $derived(m.workouts.filter((w) => !w.isActive))
	// la scheda che tocca: quella dopo l'ultima usata, a rotazione
	const next = $derived.by(() => {
		if (!m.templates.length) return null
		const last = m.workouts.find((w) => w.template && m.tplById.has(w.template))
		const i = last ? m.templates.findIndex((x) => x.uuid === last.template) : -1
		return m.templates[(i + 1) % m.templates.length]
	})
	function begin() {
		if (active) return go(`allenamento/${active.uuid}`)
		if (next) return go(`allenamento/${startWorkout('', [], next)}`)
		go('allenamento')
	}

	// ---- numeri ----
	const stat = $derived<StatWorkout[]>(finished.map((w) => ({ date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup })) })))
	const streak = $derived(weeklyStreak(stat))
	const totalVolume = $derived(stat.reduce((v, w) => v + workoutVolume(w), 0))
	const recordCount = $derived(m.exercises.reduce((n, e) => n + e.records.length, 0))
	const used = $derived(m.exercises.filter((e) => e.records.length || e.sets.length))
	const oneRms = $derived(used.flatMap((e) => [...e.records.map((r) => oneRepMax(r.weight, r.reps)), ...e.sets.filter((s) => !s.isWarmup).map((s) => oneRepMax(s.weight, s.reps))]))
	const bestOneRm = $derived(oneRms.reduce((a, b) => Math.max(a, b), 0))

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
			const volume = stat.filter((w) => weekStart(w.date) === ws).reduce((v, w) => v + workoutVolume(w), 0)
			return { ws, volume }
		})
	})
	const maxWeek = $derived(Math.max(1, ...weeks.map((w) => w.volume)))

	// ---- dove va il lavoro ----
	const groups = $derived(volumeByGroup(stat, (id) => m.exById.get(id)?.muscleGroup, 30))

	// ---- il sollevamento che cresce di più: il tuo miglior macchinario con almeno tre massimali ----
	const lift = $derived.by(() => {
		const cands = m.exercises.filter((e) => e.records.length >= 3).map((e) => ({ e, top: Math.max(...e.records.map((r) => oneRepMax(r.weight, r.reps))) }))
		const pick = cands.sort((a, b) => b.top - a.top)[0]
		if (!pick) return null
		const pts = [...pick.e.records].sort((a, b) => a.date - b.date).slice(-12).map((r) => ({ y: oneRepMax(r.weight, r.reps), label: dateShort(r.date) }))
		const ys = pts.map((p) => p.y)
		return { name: pick.e.name, points: pts, domain: [Math.min(...ys) * 0.97, Math.max(...ys) * 1.03] as [number, number], from: ys[0], to: ys.at(-1)! }
	})

	// ---- medaglie ----
	const friendCount = $derived(F.friends?.amici.length ?? 0)
	const medalList = $derived(medals({ sessions: finished.length, records: recordCount, streak, volumeKg: totalVolume, bestOneRm, machines: used.length, friends: friendCount }))
	const totals = $derived(medalTotals(medalList))
	const MEDAL: Record<MedalId, { icon: string; title: string }> = {
		sessions: { icon: 'dumbbell', title: t('Costanza') }, records: { icon: 'trophy', title: t('Cacciatore di record') },
		streak: { icon: 'flame', title: t('In serie') }, volume: { icon: 'scale', title: t('Tonnellate') },
		strength: { icon: 'bolt', title: t('Forza') }, variety: { icon: 'compass', title: t('Esploratore') }, friends: { icon: 'users', title: t('Squadra') },
	}
	const TIERS = [t('da conquistare'), t('Bronzo'), t('Argento'), t('Oro')]
	const medalSub = (md: (typeof medalList)[number]) => md.next === null ? TIERS[3] : md.tier === 0 ? `${fmtMedal(md.id, md.value)} / ${fmtMedal(md.id, md.next)}` : `${TIERS[md.tier]} · ${fmtMedal(md.id, md.value)}/${fmtMedal(md.id, md.next)}`
	const fmtMedal = (id: MedalId, v: number) => id === 'volume' ? V(v) : id === 'strength' ? W(v) : String(Math.round(v))

	// ---- in breve ----
	const latest = $derived(m.exercises.filter((e) => e.records.length).map((e) => ({ ...e.records[0]!, name: e.name })).sort((a, b) => b.date - a.date).slice(0, 4))
	const news = $derived((F.news ?? []).slice(0, 4))
</script>

<main class="screen">
	<SummitHero volumeKg={totalVolume} {greeting} name={first || 'Peak'} {streak} weekSessions={finished.filter((w) => w.dateMs >= weekStart(Date.now())).length} onavatar={() => go('impostazioni/account')} />

	<!-- subito sotto: cosa fare adesso, in piccolo -->
	<button class="next" onclick={begin} data-tour="start">
		<span class="ni"><Icon name={active ? 'repeat' : 'dumbbell'} size={22} /></span>
		<span class="nt">
			<b>{active ? t('Riprendi allenamento') : next ? t('Avvia {0}', next.name) : t('Inizia un allenamento')}</b>
			<span class="small muted">{active ? `${tn(active.workingSets, '1 serie', '{0} serie')} · ${V(active.volume)}` : next ? tn(next.items.length, '1 esercizio', '{0} esercizi') : t('Crea una scheda con i tuoi esercizi, oppure allenati a mano.')}</span>
		</span>
		<Icon name="right" size={20} />
	</button>

	<!-- i numeri della tua storia -->
	<div class="stats">
		<div class="stat"><div class="n">{finished.length}</div><div class="l">{t('Allenamenti')}</div></div>
		<div class="stat"><div class="n">{V(totalVolume)}</div><div class="l">{t('Sollevati')}</div></div>
		<div class="stat"><div class="n">{recordCount}</div><div class="l">{t('Record')}</div></div>
		<div class="stat flame"><div class="n"><Icon name="flame" size={22} />{streak}</div><div class="l">{t('Settimane di fila')}</div></div>
	</div>

	<div class="sec-title">{t('Attività')}</div>
	<div class="card">
		<Heatmap sets={perDay} />
		<div class="row between small muted" style="margin-top:10px"><span>{tn(last90, '1 allenamento in 14 settimane', '{0} allenamenti in 14 settimane')}</span><span class="legend"><i data-l="0"></i><i data-l="1"></i><i data-l="2"></i><i data-l="3"></i><i data-l="4"></i></span></div>
	</div>

	<div class="sec-title">{t('Volume settimanale')}</div>
	<div class="card">
		<div class="bars">
			{#each weeks as w, i}<div class:now={i === 11} class:zero={w.volume === 0} style="height:{Math.max(4, (w.volume / maxWeek) * 100)}%" title="{dateShort(w.ws)}: {V(w.volume)}"></div>{/each}
		</div>
		<div class="row between tiny faint" style="margin-top:6px"><span>{dateShort(weeks[0].ws)}</span><span>{t('questa settimana')}: <b style="color:var(--text)">{V(weeks[11].volume)}</b></span></div>
	</div>

	{#if groups.length}
		<div class="sec-title">{t('Dove va il lavoro')}</div>
		<div class="card split">
			<div class="donut"><Donut parts={groups.map((g) => ({ color: `var(--${g.group})`, value: g.volume }))} center={V(groups.reduce((s, g) => s + g.volume, 0))} label={t('Dove va il lavoro')} /></div>
			<div class="stack" style="gap:8px;flex:1">
				{#each groups as g}<div class="row between small"><span class="row" style="gap:8px"><i class="dot" style="background:var(--{g.group})"></i>{GROUP_NAMES[g.group]}</span><b class="num">{Math.round((g.volume / groups.reduce((s, x) => s + x.volume, 0)) * 100)}%</b></div>{/each}
				<div class="tiny faint">{t('Ultimi 30 giorni')}</div>
			</div>
		</div>
	{/if}

	{#if lift}
		<div class="sec-title">{t('Il tuo sollevamento in crescita')}</div>
		<div class="card">
			<div class="row between"><b>{lift.name}</b><span class="badge pos">{W(lift.from)} → {W(lift.to)}</span></div>
			<div class="small muted" style="margin-bottom:6px">{t('1RM stimato')}</div>
			<LineChart points={lift.points} domain={lift.domain} />
		</div>
	{/if}

	<div class="row between"><div class="sec-title">{t('Medaglie')}</div><span class="small muted" style="margin:20px 4px 8px">{totals.earned} / {totals.total}</span></div>
	<div class="medals">
		{#each medalList as md (md.id)}<Medal medal={md} icon={MEDAL[md.id].icon} title={MEDAL[md.id].title} sub={medalSub(md)} />{/each}
	</div>

	{#if latest.length}
		<div class="sec-title">{t('Record recenti')}</div>
		<div class="card list">
			{#each latest as r (r.uuid)}
				<div class="item"><span class="trophy"><Icon name="trophy" size={18} /></span><div class="grow ellipsis"><b>{r.name}</b><div class="small muted">{relDay(r.date)}</div></div><b class="num">{W(r.weight)} × {r.reps}</b></div>
			{/each}
		</div>
	{/if}

	<div class="row between"><div class="sec-title">{t('Novità degli amici')}</div><button class="link small" style="margin:20px 4px 8px" onclick={() => go('amici')}>{friendCount ? t('Amici') : t('Invita')}</button></div>
	{#if news.length}
		<div class="card list">
			{#each news as e (e.id + e.catalog)}
				<button class="item" onclick={() => go(`amici/${e.id}`)}>
					<div class="grow ellipsis"><b>@{e.username}</b> · {names.get(e.catalog) ?? e.name}<div class="small muted">{relDay(e.date)}</div></div>
					<div style="text-align:right"><b class="num">{W(e.weight)} × {e.reps}</b>{#if e.sorpasso}<div class="small" style="color:var(--neg)">{t('Ti ha superato')}</div>{/if}</div>
				</button>
			{/each}
		</div>
	{:else}
		<div class="empty">{friendCount ? t('Nessun record dei tuoi amici negli ultimi giorni.') : t('Invita un amico: vedrai qui i suoi record e le classifiche.')}</div>
	{/if}
</main>

<style>
	.next { display: flex; align-items: center; gap: 12px; width: 100%; margin-top: 12px; padding: 12px 14px; border-radius: 18px; text-align: left; color: inherit;
		background: var(--card); border: 1px solid var(--stroke); }
	.next .ni { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; background: var(--accent); color: var(--on-accent); flex: none; }
	.next .nt { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
	.next .nt b { font-size: 16px; }
	.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 12px; }
	.stat { padding: 12px 6px; border-radius: 16px; background: var(--card); border: 1px solid var(--stroke); text-align: center; min-width: 0; }
	.stat .n { font-size: 20px; font-weight: 800; letter-spacing: -0.02em; display: flex; align-items: center; justify-content: center; gap: 2px; }
	.stat .l { font-size: 10px; color: var(--text-3); margin-top: 2px; line-height: 1.2; }
	.stat.flame .n { color: var(--accent); }
	.legend { display: inline-flex; gap: 3px; align-items: center; }
	.legend i { width: 11px; height: 11px; border-radius: 3px; background: rgba(255,255,255,.06); }
	.legend i[data-l='1'] { background: rgba(var(--accent-rgb), .3); } .legend i[data-l='2'] { background: rgba(var(--accent-rgb), .55); }
	.legend i[data-l='3'] { background: rgba(var(--accent-rgb), .8); } .legend i[data-l='4'] { background: rgb(var(--accent-rgb)); }
	.bars { display: flex; align-items: flex-end; gap: 6px; height: 110px; }
	.bars div { flex: 1; border-radius: 6px 6px 3px 3px; background: linear-gradient(to top, rgba(var(--accent-rgb), .25), rgba(var(--accent-rgb), .7)); }
	.bars div.zero { background: rgba(255,255,255,.07); }
	.bars div.now { background: linear-gradient(to top, rgba(var(--accent-rgb), .5), rgb(var(--accent-rgb))); box-shadow: 0 0 14px rgba(var(--accent-rgb), .45); }
	.split { display: flex; gap: 18px; align-items: center; }
	.donut { width: 132px; flex: none; color: var(--text); }
	.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
	.medals { display: flex; gap: 6px; overflow-x: auto; scroll-snap-type: x proximity; padding: 4px 2px 10px; margin: 0 -4px; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
	.medals::-webkit-scrollbar { display: none; }
	.trophy { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: rgba(255,212,94,.14); color: #ffd45e; flex: none; }
</style>
