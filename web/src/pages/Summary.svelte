<script lang="ts">
	import { onMount, untrack } from 'svelte'
	import { itemsFromWorkout } from '../lib/actions.ts'
	import { weekStart, weeklyGoal, weeklyStreak } from '@common/stats.ts'
	import { V, duration, setLine } from '../lib/fmt.ts'
	import { MEDAL, TIERS, fmtMedal, medalsAt } from '../lib/medalinfo.ts'
	import { locale, t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import { celebrated, confetti, countUp, firstTime, grow, pop, pulse, rise, sequence, wiggle } from '../lib/fx.ts'
	import { play, playDay } from '../lib/sound.ts'
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

	// ---- la festa ----
	// Solo se l'allenamento è finito da meno di 10 minuti e la prima volta che si apre il riepilogo: altrimenti tutto fermo, già finale.
	// I tempi (ms) si calcolano una volta: giorni in ordine di settimana, poi i segmenti, poi obiettivo, serie, medaglie, record (in tutto ~4 s).
	const days = $derived([...trainedDays].sort((a, b) => a - b))
	const streakUp = $derived(streakNow > streakBefore)
	const P = untrack(() => {
		const playing = !!wk && wk.endedMs !== null && Date.now() - wk.endedMs < 600_000 && firstTime(`sum.${id}`)
		const segN = Math.min(goal, thisWeek.length)
		const goalNow = thisWeek.length >= goal && thisWeek.length - 1 < goal // questo allenamento ha raggiunto l'obiettivo
		const tDay = (k: number) => 800 + k * 140
		const tPulse = tDay(Math.max(0, days.length - 1)) + 260
		const tSeg = tPulse + 120
		let c = tSeg + segN * 100 + 80
		const tGoal = c
		if (goalNow) c += 450
		const tStreak = c
		if (streakUp) c += 450
		const tMedal = c
		const mStep = Math.min(150, 600 / Math.max(1, moved.length)) // con molte medaglie lo scarto si stringe: il totale resta sotto i 4 s
		if (moved.length) c += moved.length * mStep + 250
		return { playing, segN, goalNow, tDay, tPulse, tSeg, tGoal, tStreak, tMedal, mStep, tRec: c }
	})
	let dayStep = $state<number | null>(P.playing ? 0 : null) // quanti giorni sono già pieni (null: tutti)
	let seg = $state<number | null>(P.playing ? 0 : null) // quanti segmenti dell'obiettivo (null: i veri)
	let reveal = $state(P.playing && P.goalNow) // «Obiettivo raggiunto!» compare al suo momento
	const segShown = $derived(seg ?? thisWeek.length)

	let heroEl = $state<HTMLElement>()
	let titleEl = $state<HTMLElement>()
	let tilesEl = $state<HTMLElement>()
	let weekEl = $state<HTMLElement>()
	let daysEl = $state<HTMLElement>()
	let goalEl = $state<HTMLElement>()
	let flameEl = $state<HTMLElement>()
	let plusEl = $state<HTMLElement>()
	let medSec = $state<HTMLElement>()
	let medList = $state<HTMLElement>()
	let recSec = $state<HTMLElement>()
	let recList = $state<HTMLElement>()

	onMount(() => {
		if (!wk) return
		// quello che si vede qui la Home non lo ripete (con il massimo, perché da un riepilogo vecchio i numeri possono essere più bassi)
		if (ws === weekStart(Date.now())) {
			const w = celebrated.week()
			celebrated.setWeek(ws, w?.ws === ws ? Math.max(w.n, thisWeek.length) : thisWeek.length)
			celebrated.setStreak(Math.max(celebrated.streak() ?? 0, streakNow))
		}
		if (!P.playing) return
		const tl = sequence()
		const circle = (d: number) => daysEl?.children[d]?.querySelector('.c')

		// 0: icona e titolo, coriandoli, suono
		pop(heroEl, 0, 14)
		pop(titleEl, 80)
		confetti(heroEl)
		play('finish')
		// 0.25: le quattro schede entrano una dopo l'altra
		;[...(tilesEl?.children ?? [])].forEach((el, i) => rise(el, 250 + i * 60))
		// ~0.8: i giorni fatti si riempiono in ordine, una nota in più ciascuno; poi l'anello di oggi batte; poi i segmenti
		days.forEach((d, k) => tl.at(P.tDay(k), () => { dayStep = k + 1; pop(circle(d)); playDay(k) }))
		pulse(circle(todayIdx), P.tPulse)
		for (let k = 0; k < P.segN; k++) tl.at(P.tSeg + k * 100, () => (seg = k + 1))
		tl.at(P.tGoal - 40, () => { dayStep = null; seg = null })
		// obiettivo raggiunto da questo allenamento
		if (P.goalNow) tl.at(P.tGoal, () => { reveal = false; pop(goalEl); confetti(weekEl); play('goal') })
		// una settimana di fila in più: la fiamma dondola, il numero sale (countUp sul nodo), esce il +1
		if (streakUp) {
			wiggle(flameEl, P.tStreak)
			pop(plusEl, P.tStreak + 200)
			tl.at(P.tStreak, () => play('streak'))
		}
		// medaglie: le barre crescono dal valore di prima a quello di adesso; chi ha cambiato livello fa «pop» e suona
		if (moved.length) {
			rise(medSec, P.tMedal)
			;[...(medList?.children ?? [])].forEach((row, j) => {
				grow(row.querySelector('.gain'), P.tMedal + 150 + j * P.mStep)
				pop(row.querySelector('.tier'), P.tMedal + 350 + j * P.mStep)
			})
			if (moved.some((x) => x.a.tier > x.b.tier)) tl.at(P.tMedal + 350, () => play('medal'))
		}
		// record: le righe entrano a catena
		if (prs.length) {
			rise(recSec, P.tRec)
			;[...(recList?.children ?? [])].forEach((row, i) => rise(row, P.tRec + 120 + i * 70, 8))
			tl.at(P.tRec, () => play('record'))
		}
		return tl.stop // uscendo dalla pagina non resta nessun suono in coda
	})
</script>

<main class="screen no-tabs">
	{#if !wk}
		<div class="empty">{t('Allenamento non trovato.')}</div>
		<button class="btn block" onclick={() => go('allenamenti', true)}>{t('Fine')}</button>
	{:else}
		<div class="hero">
			<span class="hi" bind:this={heroEl}><Icon name={prs.length ? 'trophy' : 'check'} size={34} /></span>
			<h1 bind:this={titleEl}>{prs.length ? t('Nuovi massimali!') : t('Allenamento concluso')}</h1>
		</div>
		<div class="tiles" bind:this={tilesEl}>
			<div class="card tile"><div class="tiny muted">{t('Durata')}</div><div class="big num">{wk.endedMs ? duration((wk.endedMs - wk.dateMs) / 1000) : '—'}</div></div>
			<div class="card tile"><div class="tiny muted">{t('Volume')}</div><div class="big num" use:countUp={{ value: wk.volume, format: V, from: P.playing ? 0 : undefined, delay: 400 }}></div></div>
			<div class="card tile"><div class="tiny muted">{t('Serie')}</div><div class="big num" use:countUp={{ value: wk.workingSets, from: P.playing ? 0 : undefined, delay: 460 }}></div></div>
			<div class="card tile"><div class="tiny muted">{t('Esercizi')}</div><div class="big num" use:countUp={{ value: exCount, from: P.playing ? 0 : undefined, delay: 520 }}></div></div>
		</div>
		<div class="sec-title">{t('La tua settimana')}</div>
		<div class="card stack" bind:this={weekEl}>
			<div class="row between">
				<span class="row" style="gap:10px"><span class="fl" bind:this={flameEl}><Icon name="flame" size={22} /></span><span><b class="num" style="font-size:22px" use:countUp={{ value: streakNow, from: P.playing && streakUp ? streakBefore : undefined, delay: P.tStreak }}></b> <span class="muted">{streakNow === 1 ? t('settimana di fila') : t('settimane di fila')}</span></span></span>
				{#if streakUp}<span class="badge accent" bind:this={plusEl}>+1</span>{/if}
			</div>
			<div class="days" bind:this={daysEl}>
				{#each dayNames as d, i}
					{@const k = days.indexOf(i)}
					{@const on = k >= 0 && k < (dayStep ?? days.length)}
					<div class="d" class:on class:now={i === todayIdx}><span class="c">{#if on}<Icon name="check" size={16} />{/if}</span><span class="tiny muted">{d}</span></div>
				{/each}
			</div>
			<div>
				<div class="row between small" style="margin-bottom:6px"><span class="muted" bind:this={goalEl}>{thisWeek.length >= goal && !reveal && segShown >= goal ? t('Obiettivo raggiunto!') : t('Obiettivo della settimana')}</span><b class="num">{segShown} / {goal}</b></div>
				<div class="segs">{#each Array(goal) as _, k}<i class:on={k < segShown}></i>{/each}</div>
				{#if thisWeek.length > goal}<div class="small muted" style="margin-top:6px">{tn(thisWeek.length - goal, '+1 oltre l\'obiettivo', '+{0} oltre l\'obiettivo')}</div>{/if}
			</div>
		</div>
		{#if moved.length}
			<div bind:this={medSec}>
			<div class="sec-title">{t('Medaglie')}</div>
			<div class="card list" bind:this={medList}>
				{#each moved as { a, b } (a.id)}
					{@const from = a.tier > b.tier ? 0 : Math.round(b.progress * 100)}
					<div class="item" style="flex-direction:column;align-items:stretch;gap:6px">
						<div class="row between"><span class="row" style="gap:10px"><Icon name={MEDAL[a.id].icon} size={20} /><b>{MEDAL[a.id].title}</b></span>
							{#if a.tier > b.tier}<span class="badge accent tier">{t('Nuovo livello')}: {TIERS[a.tier]}</span>{:else}<span class="small muted num">{fmtMedal(a.id, a.value)}{a.next === null ? '' : ` / ${fmtMedal(a.id, a.next)}`}</span>{/if}</div>
						<div class="bar2"><i style="width:{from}%"></i><i class="gain" style="left:{from}%;width:{Math.max(0, Math.round(a.progress * 100) - from)}%"></i></div>
					</div>
				{/each}
			</div>
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
			<div bind:this={recSec}>
			<div class="sec-title">{t('Record di oggi')}</div>
			<div class="card list" bind:this={recList}>
				{#each prs as s (s.uuid)}
					<div class="item"><div class="grow"><b>{m.exById.get(s.exercise ?? '')?.name ?? t('Esercizio rimosso')}</b></div><span class="num">{setLine(s.weight, s.reps)}</span><span class="badge pos">PR</span></div>
				{/each}
			</div>
			</div>
		{/if}
		{#if exCount}<button class="btn block" style="margin-top:24px" onclick={() => (saving = true)}><Icon name="list" size={20} />{t('Salva come giorno')}</button>{/if}
		<button class="btn primary block" style="margin-top:12px" onclick={() => go('allenamenti', true)}>{t('Fine')}</button>
		{#if saving}<TemplateEditor initial={{ name: wk.name, items: itemsFromWorkout(wk) }} onclose={() => (saving = false)} onsaved={() => toast(t('Giorno salvato.'))} />{/if}
	{/if}
</main>

<style>
	/* in testa: l'icona in un cerchio d'accento, sopra il titolo */
	.hero { display: flex; flex-direction: column; align-items: center; gap: 14px; margin: 12px 0 22px; text-align: center; }
	.hero h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; }
	.hi { width: 72px; height: 72px; border-radius: 50%; display: grid; place-items: center; background: var(--accent); color: var(--on-accent); box-shadow: 0 0 36px rgba(var(--accent-rgb), .4); }
	.fl { width: 38px; height: 38px; border-radius: 12px; display: grid; place-items: center; background: rgba(var(--accent-rgb), .16); color: var(--accent); }
	.days { display: flex; justify-content: space-between; }
	.d { display: flex; flex-direction: column; align-items: center; gap: 4px; }
	.d .c { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--card-strong); color: var(--on-accent); }
	.d.on .c { background: var(--accent); }
	.d.now .c { box-shadow: 0 0 0 2px var(--bg, #0b0e16), 0 0 0 4px rgba(var(--accent-rgb), .7); }
	.segs { display: flex; gap: 6px; }
	.segs i { position: relative; flex: 1; height: 12px; border-radius: 6px; background: var(--card-strong); }
	/* il riempimento è un velo che si allunga da sinistra (transform): niente width */
	.segs i::after { content: ''; position: absolute; inset: 0; border-radius: 6px; background: linear-gradient(90deg, rgba(var(--accent-rgb), .7), rgb(var(--accent-rgb))); transform: scaleX(0); transform-origin: left; transition: transform 220ms var(--ease-out); }
	.segs i.on::after { transform: none; }
	.bar2 { position: relative; height: 8px; border-radius: 4px; background: var(--stroke); overflow: hidden; }
	.bar2 i { position: absolute; top: 0; bottom: 0; left: 0; background: rgba(var(--accent-rgb), .55); }
	.bar2 i.gain { background: var(--accent); box-shadow: 0 0 10px rgba(var(--accent-rgb), .8); transform-origin: left; }
</style>
