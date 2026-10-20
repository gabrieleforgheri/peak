<script lang="ts">
	import { onDestroy, onMount, untrack } from 'svelte'
	import { oneRepMax } from '@common/domain.ts'
	import { medals, medalTotals } from '@common/medals.ts'
	import { weekStart, weeklyGoal, weeklyStreak, workoutVolume, type StatWorkout } from '@common/stats.ts'
	import { startWorkout } from '../lib/actions.ts'
	import { nextDay } from '../lib/derive.ts'
	import { entryName, loadCatalog } from '../lib/catalog.ts'
	import { V, W, relDay } from '../lib/fmt.ts'
	import { MEDAL, medalSub } from '../lib/medalinfo.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { F, loadFriends, loadNews } from '../lib/social.svelte.ts'
	import { S, getModel } from '../lib/store.svelte.ts'
	import { celebrated, confetti, countUp, sequence, wiggle } from '../lib/fx.ts'
	import { play, playDay } from '../lib/sound.ts'
	import GoalPicker from '../components/GoalPicker.svelte'
	import SummitHero from '../components/SummitHero.svelte'
	import Icon from '../components/Icon.svelte'
	import TemplateIconView from '../components/TemplateIcon.svelte'
	import Medal from '../components/Medal.svelte'

	const m = $derived(getModel())
	let names = $state(new Map<string, string>())
	onMount(() => { void loadNews(); void loadFriends(); void loadCatalog().then((c) => (names = new Map(c.map((e) => [e.id, entryName(e)])))) })

	// ---- chi sei e cosa tocca ----
	const hour = new Date().getHours()
	const greeting = hour < 12 ? t('Buongiorno') : hour < 18 ? t('Buon pomeriggio') : t('Buonasera')
	const first = $derived((S.user?.nome ?? '').split(' ')[0] || S.user?.username || '')
	let choosing = $state(false)
	const active = $derived(m.workouts.find((w) => w.isActive))
	const finished = $derived(m.workouts.filter((w) => !w.isActive))
	// il giorno che tocca, nella scheda in uso
	const next = $derived(nextDay(m))
	function begin() {
		if (active) return go(`allenamento/${active.uuid}`)
		if (next) return go(`allenamento/${startWorkout('', [], next)}`)
		go('allenamento')
	}

	// ---- numeri ----
	const stat = $derived<StatWorkout[]>(finished.map((w) => ({ date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup })) })))
	const streak = $derived(weeklyStreak(stat))
	const weekCount = $derived(finished.filter((w) => w.dateMs >= weekStart(Date.now())).length)
	const goalN = $derived(m.settings.weeklyGoal || weeklyGoal(stat))

	// ---- festa: quello che è salito da quando l'hai visto l'ultima volta (un allenamento fatto sull'iPhone e arrivato con la sincronizzazione) ----
	// Il confronto è con ciò che Riepilogo o questa stessa pagina hanno già festeggiato (celebrated); la prima volta si salva in silenzio.
	let shown = $state<number | null>(null) // segmenti mostrati mentre si festeggia (null: quelli veri)
	let roll = $state<{ from: number; delay: number } | undefined>() // la serie sale da `from`
	let heroBox = $state<HTMLElement>()
	let flame = $state<HTMLElement>()
	const tl = sequence()
	onDestroy(tl.stop) // uscendo dalla Home non resta nessun suono in coda
	// $effect.pre: il confronto avviene prima che il DOM mostri i numeri nuovi, così non c'è il lampo del valore finale
	$effect.pre(() => {
		if (!S.synced) return
		const n = weekCount, s = streak, g = goalN, ws = weekStart(Date.now())
		untrack(() => {
			const cw = celebrated.week(), cs = celebrated.streak()
			celebrated.setWeek(ws, n)
			celebrated.setStreak(s)
			const base = cw ? (cw.ws === ws ? cw.n : 0) : n // un'altra settimana: si riparte da zero
			const streakUp = cs !== null && s > cs
			if (n <= base && !streakUp) return
			tl.stop()
			let t = 500
			if (n > base) {
				shown = base
				for (let k = base; k < n; k++, t += 160) tl.at(t, () => { shown = k + 1; playDay(k) }) // un segmento e una nota alla volta
				if (n >= g && base < g) { tl.at(t, () => { play('goal'); confetti(heroBox) }); t += 450 }
			}
			if (streakUp) {
				roll = { from: cs, delay: t }
				tl.at(t, () => { wiggle(flame); play('streak') })
				t += 900
			}
			tl.at(t, () => { shown = null; roll = undefined })
		})
	})
	const totalVolume = $derived(stat.reduce((v, w) => v + workoutVolume(w), 0))
	const recordCount = $derived(m.exercises.reduce((n, e) => n + e.records.length, 0))
	const used = $derived(m.exercises.filter((e) => e.records.length || e.sets.length))
	const oneRms = $derived(used.flatMap((e) => [...e.records.map((r) => oneRepMax(r.weight, r.reps)), ...e.sets.filter((s) => !s.isWarmup).map((s) => oneRepMax(s.weight, s.reps))]))
	const bestOneRm = $derived(oneRms.reduce((a, b) => Math.max(a, b), 0))

	// ---- medaglie ----
	const friendCount = $derived(F.friends?.amici.length ?? 0)
	const medalList = $derived(medals({ sessions: finished.length, records: recordCount, streak, volumeKg: totalVolume, bestOneRm, machines: used.length, friends: friendCount }))
	const totals = $derived(medalTotals(medalList))

	// ---- in breve ----
	const latest = $derived(m.exercises.filter((e) => e.records.length).map((e) => ({ ...e.records[0]!, name: e.name })).sort((a, b) => b.date - a.date).slice(0, 4))
	const news = $derived((F.news ?? []).slice(0, 4))
</script>

<main class="screen page home">
	<!-- a schermo largo: a sinistra la montagna, il giorno che tocca e le medaglie; a destra record e novità -->
	<div class="blk main">
		<div bind:this={heroBox}><SummitHero {greeting} name={first || 'Peak'} {streak} weekSessions={shown ?? weekCount} goal={goalN} {roll} onchangegoal={() => (choosing = true)} onavatar={() => go('me')} /></div>

		{#if choosing}<GoalPicker current={m.settings.weeklyGoal} suggested={weeklyGoal(stat)} onclose={() => (choosing = false)} />{/if}

		<!-- subito sotto: cosa fare adesso, in piccolo -->
		<button class="next" onclick={begin} data-tour="start">
			<span class="ni">{#if active || !next}<Icon name={active ? 'repeat' : 'dumbbell'} size={22} />{:else}<TemplateIconView icon={next.icon} name={next.name} size={26} />{/if}</span>
			<span class="nt">
				<b>{active ? t('Riprendi allenamento') : next ? t('Avvia {0}', next.name) : t('Inizia un allenamento')}</b>
				<span class="small muted">{active ? `${tn(active.workingSets, '1 serie', '{0} serie')} · ${V(active.volume)}` : next ? tn(next.items.length, '1 esercizio', '{0} esercizi') : t('Crea un giorno con i tuoi esercizi, oppure allenati a mano.')}</span>
			</span>
			<Icon name="right" size={20} />
		</button>

		<!-- i numeri della tua storia -->
		<div class="stats">
			<div class="stat"><div class="n">{finished.length}</div><div class="l">{t('Allenamenti')}</div></div>
			<div class="stat"><div class="n">{V(totalVolume)}</div><div class="l">{t('Sollevati')}</div></div>
			<div class="stat"><div class="n">{recordCount}</div><div class="l">{t('Record')}</div></div>
			<div class="stat flame"><div class="n"><span class="fl" bind:this={flame}><Icon name="flame" size={22} /></span><span use:countUp={{ value: streak, from: roll?.from, delay: roll?.delay }}></span></div><div class="l">{t('Settimane di fila')}</div></div>
		</div>

		<div class="row between sec-row"><div class="sec-title">{t('Medaglie')}</div><button class="link small" onclick={() => go('me')}>{totals.earned} / {totals.total} · {t('Vedi tutto')}</button></div>
		<div class="medals" data-tour="medals">
			{#each medalList as md (md.id)}<Medal medal={md} icon={MEDAL[md.id].icon} title={MEDAL[md.id].title} sub={medalSub(md)} />{/each}
		</div>
	</div>

	<div class="blk side fan">
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

		<div class="blk">
			<div class="row between sec-row"><div class="sec-title">{t('Novità degli amici')}</div><button class="link small" onclick={() => go('amici')}>{friendCount ? t('Amici') : t('Invita')}</button></div>
			{#if news.length}
				<div class="card list">
					{#each news as e, i (e.id + e.catalog + e.date + i)}
						<button class="item" onclick={() => go(`amici/${e.id}`)}>
							<div class="grow ellipsis"><b>@{e.username}</b> · {names.get(e.catalog) ?? e.name}<div class="small muted">{relDay(e.date)}</div></div>
							<div style="text-align:right"><b class="num">{W(e.weight)} × {e.reps}</b>{#if e.sorpasso}<div class="small" style="color:var(--neg)">{t('Ti ha superato')}</div>{/if}</div>
						</button>
					{/each}
				</div>
			{:else}
				<div class="empty">{friendCount ? t('Nessun record dei tuoi amici negli ultimi giorni.') : t('Invita un amico: vedrai qui i suoi record e le classifiche.')}</div>
			{/if}
		</div>
	</div>
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
	.fl { display: inline-flex; }
	/* schermi larghi: colonna principale più larga; a tre colonne record e novità si separano (la .fan si scioglie) */
	@media (min-width: 900px) { .home { grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); } }
	@media (min-width: 1400px) { .home { grid-template-columns: minmax(0, 3fr) minmax(0, 2fr) minmax(0, 2fr); } }
	.trophy { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; background: rgba(255,212,94,.14); color: #ffd45e; flex: none; }
</style>
