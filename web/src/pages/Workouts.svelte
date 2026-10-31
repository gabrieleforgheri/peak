<script lang="ts">
	import { onDestroy, untrack } from 'svelte'
	import { flip } from 'svelte/animate'
	import type { MuscleGroup } from '@common/docs.ts'
	import { weekStart, weeklyGoal, weeklyStreak, type StatWorkout } from '@common/stats.ts'
	import { deleteWorkout, setActiveProgram, startWorkout } from '../lib/actions.ts'
	import { activeProgram, daysOf, todayDay } from '../lib/derive.ts'
	import { GROUP_NAMES, V, duration, relDay, weekdayName } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { stopRest } from '../lib/timer.svelte.ts'
	import { S, getModel } from '../lib/store.svelte.ts'
	import { celebrated, confetti, move, rowIn, sequence, stagger } from '../lib/fx.ts'
	import { play, playDay } from '../lib/sound.ts'
	import type { Prg, Tpl, Wk } from '../lib/types.ts'
	import GoalPicker from '../components/GoalPicker.svelte'
	import InstallBanner from '../components/InstallBanner.svelte'
	import SummitHero from '../components/SummitHero.svelte'
	import FocusPicker from '../components/FocusPicker.svelte'
	import Icon from '../components/Icon.svelte'
	import MonthGroups from '../components/MonthGroups.svelte'
	import TemplateIconView from '../components/TemplateIcon.svelte'
	import Sheet from '../components/Sheet.svelte'
	import ProgramEditor from '../components/ProgramEditor.svelte'
	import TemplateEditor from '../components/TemplateEditor.svelte'

	const m = $derived(getModel())
	const active = $derived(m.workouts.find((w) => w.isActive))
	const prog = $derived(activeProgram(m))
	// con giorni fissi si elencano in ordine di settimana
	const days = $derived.by(() => { const d = daysOf(m, prog); return prog?.useDays ? [...d].sort((a, b) => (a.weekday < 0 ? 9 : a.weekday) - (b.weekday < 0 ? 9 : b.weekday)) : d })
	let editingProg = $state<Prg | 'new' | null>(null)
	const history = $derived(m.workouts.filter((w) => !w.isActive))
	let starting = $state(false)
	let menu = $state<Wk | null>(null)
	// null = chiuso, 'new' = scheda nuova
	let editing = $state<Tpl | 'new' | null>(null)

	const title = (w: Wk) => w.name.trim() || relDay(w.dateMs)
	const exCount = (w: Wk) => new Set(w.sets.map((s) => s.exercise).filter(Boolean)).size
	const groupsOf = (w: Wk): MuscleGroup[] => {
		const out: MuscleGroup[] = []
		for (const s of w.sets) { const g = s.exercise ? m.exById.get(s.exercise)?.muscleGroup : undefined; if (g && !out.includes(g)) out.push(g) }
		return out.slice(0, 4)
	}

	let pressTimer: ReturnType<typeof setTimeout> | undefined
	let longPressed = false
	const press = (w: Wk) => { longPressed = false; clearTimeout(pressTimer); pressTimer = setTimeout(() => { longPressed = true; menu = w }, 520) }
	const release = () => clearTimeout(pressTimer)

	// ---- la montagna in cima: chi sei e come va la settimana ----
	const hour = new Date().getHours()
	const greeting = hour < 12 ? t('Buongiorno') : hour < 18 ? t('Buon pomeriggio') : t('Buonasera')
	const first = $derived((S.user?.nome ?? '').split(' ')[0] || S.user?.username || '')
	let choosing = $state(false)
	const today = $derived(todayDay(m))
	const stat = $derived<StatWorkout[]>(history.map((w) => ({ date: w.dateMs, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup, singleArm: s.singleArm })) })))
	const streak = $derived(weeklyStreak(stat))
	const weekCount = $derived(history.filter((w) => w.dateMs >= weekStart(Date.now())).length)
	const goalN = $derived(m.settings.weeklyGoal || weeklyGoal(stat))

	// ---- festa: quello che è salito da quando l'hai visto l'ultima volta (un allenamento fatto sull'iPhone e arrivato con la sincronizzazione) ----
	// Il confronto è con ciò che Riepilogo o questa stessa pagina hanno già festeggiato (celebrated); la prima volta si salva in silenzio.
	let shown = $state<number | null>(null) // segmenti mostrati mentre si festeggia (null: quelli veri)
	let roll = $state<{ from: number; delay: number } | undefined>() // la serie sale da `from`
	let heroBox = $state<HTMLElement>()
	const tl = sequence()
	onDestroy(tl.stop) // uscendo da qui non resta nessun suono in coda
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
				tl.at(t, () => { play('streak') })
				t += 900
			}
			tl.at(t, () => { shown = null; roll = undefined })
		})
	})
</script>

<main class="screen page split" use:stagger>

	<!-- a schermo largo: a sinistra cosa fare adesso e la scheda, a destra lo storico -->
	<div class="blk">
		<div bind:this={heroBox}><SummitHero {greeting} name={first || 'Peak'} {streak} weekSessions={shown ?? weekCount} goal={goalN} {roll} onchangegoal={() => (choosing = true)} onavatar={() => go('me')} /></div>
		<InstallBanner />
		{#if choosing}<GoalPicker current={m.settings.weeklyGoal} suggested={weeklyGoal(stat)} onclose={() => (choosing = false)} />{/if}

		{#if active}
			<div class="card" style="margin-top:12px;border-color:rgba(var(--accent-rgb),.4)">
				<div class="tiny" style="color:var(--accent);font-weight:800;letter-spacing:.08em">{t('SESSIONE IN CORSO')}</div>
				<div class="mid" style="margin:4px 0">{title(active)}</div>
				<div class="small muted">{tn(active.workingSets, '1 serie', '{0} serie')} · {V(active.volume)} · {tn(exCount(active), '1 esercizio', '{0} esercizi')}</div>
				<button class="btn primary block" style="margin-top:12px" onclick={() => go(`allenamento/${active.uuid}`)}>{t('Riprendi allenamento')}</button>
				<!-- per buttare una sessione lasciata aperta senza doverci entrare -->
				<button class="btn danger block" style="margin-top:8px" onclick={() => { if (confirm(t('Eliminare questo allenamento e le sue serie?'))) { stopRest(); deleteWorkout(active.uuid) } }}><Icon name="trash" size={18} />{t('Elimina')}</button>
			</div>
		{:else if today}
			<button class="next" onclick={() => go(`allenamento/${startWorkout('', [], today)}`)} data-tour="start">
				<span class="ni"><TemplateIconView icon={today.icon} name={today.name} size={26} /></span>
				<span class="nt"><b>{t('Avvia {0}', today.name)}</b><span class="small muted">{tn(today.items.filter((i) => m.exById.has(i.exercise)).length, '1 esercizio', '{0} esercizi')}</span></span>
				<Icon name="right" size={20} />
			</button>
			<button class="link small pick" onclick={() => (starting = true)}>{t('oppure scegli tu')}</button>
		{:else}
			<button class="btn primary block" style="margin-top:12px" onclick={() => (starting = true)} data-tour="start"><Icon name="plus" size={20} />{t('Avvia allenamento')}</button>
		{/if}

		<div class="row between sec-row"><div class="sec-title">{t('Scheda')}</div><button class="link small" onclick={() => (editingProg = 'new')}>{t('Nuova scheda')}</button></div>
		{#if m.programs.length > 1}
			<div class="chips" style="margin-bottom:10px">
				{#each m.programs as p (p.uuid)}<button class="chip" class:on={p.uuid === prog?.uuid} onclick={() => setActiveProgram(p.uuid)}>{p.name}</button>{/each}
			</div>
		{/if}
		{#if prog}<div class="row between" style="margin:0 4px 8px"><b class="ellipsis">{prog.name}</b><button class="link small" onclick={() => (editingProg = prog)}>{t('Modifica scheda')}</button></div>{/if}
		{#if days.length === 0}
			<div class="empty">{t('Un giorno è una lista di esercizi con serie e ripetizioni: lo scegli quando inizi e li trovi già pronti.')}</div>
		{:else}
			<div class="card list">
				{#each days as tpl (tpl.uuid)}
					<button class="item" onclick={() => (editing = tpl)} animate:flip={move()} in:rowIn>
						<span class="ti"><TemplateIconView icon={tpl.icon} name={tpl.name} size={22} /></span>
						<div class="grow"><b class="ellipsis" style="display:block">{tpl.name}</b><span class="small muted">{tn(tpl.items.filter((i) => m.exById.has(i.exercise)).length, '1 esercizio', '{0} esercizi')}</span></div>
						{#if prog?.useDays}{#if tpl.weekday >= 0}<span class="badge accent">{weekdayName(tpl.weekday)}</span>{:else}<span class="small faint">{t('Nessuno')}</span>{/if}{/if}
						<Icon name="right" size={20} />
					</button>
				{/each}
			</div>
		{/if}
		<button class="btn block" style="margin-top:10px" onclick={() => (editing = 'new')}><Icon name="plus" size={20} />{t('Nuovo giorno')}</button>
	</div>

	<div class="blk">
		<div class="sec-title">{t('Storico')}</div>
		{#if history.length === 0}
			<div class="empty">{t('Qui compariranno gli allenamenti conclusi.')}</div>
		{:else}
			<MonthGroups items={history} dateOf={(w) => w.dateMs} body="stack cells" count={(n) => tn(n, '1 allenamento', '{0} allenamenti')}>
				{#snippet row(w)}
					<button class="card" style="text-align:left;color:inherit;display:block;width:100%"
						onclick={() => { if (!longPressed) go(`allenamento/${w.uuid}`) }} onpointerdown={() => press(w)} onpointerup={release} onpointerleave={release} onpointercancel={release}
						oncontextmenu={(e) => { e.preventDefault(); menu = w }}>
						<div class="row between">
							<b class="ellipsis">{title(w)}</b>
							<span class="row" style="gap:6px">{#if w.prCount}<span class="badge pos">{w.prCount} PR</span>{/if}<span class="small muted num">{w.endedMs ? duration((w.endedMs - w.dateMs) / 1000) : ''}</span></span>
						</div>
						{#if w.note}<div class="small muted" style="display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;margin-top:2px">{w.note}</div>{/if}
						<div class="row between small muted" style="margin-top:8px">
							<span>{tn(w.workingSets, '1 serie', '{0} serie')} · {V(w.volume)}</span>
							<span class="row" style="gap:5px">{#each groupsOf(w) as g}<span title={GROUP_NAMES[g]} style="width:10px;height:10px;border-radius:50%;background:var(--{g})"></span>{/each}</span>
						</div>
					</button>
				{/snippet}
			</MonthGroups>
		{/if}
	</div>
</main>

{#if editingProg}<ProgramEditor prog={editingProg === 'new' ? null : editingProg} onclose={() => (editingProg = null)} onsaved={(id) => setActiveProgram(id)} />{/if}
{#if editing}<TemplateEditor tpl={editing === 'new' ? null : editing} onclose={() => (editing = null)} />{/if}
{#if starting}<FocusPicker onclose={() => (starting = false)} onstart={(id) => go(`allenamento/${id}`)} />{/if}
{#if menu}
	<Sheet title={title(menu)} onclose={() => (menu = null)}>
		<div class="stack">
			<button class="btn block" onclick={() => { go(`allenamento/${menu!.uuid}`); menu = null }}>{t('Apri')}</button>
			<button class="btn danger block" onclick={() => { if (confirm(t('Eliminare questo allenamento e le sue serie?'))) deleteWorkout(menu!.uuid); menu = null }}><Icon name="trash" size={18} />{t('Elimina')}</button>
		</div>
	</Sheet>
{/if}

<style>
	.next { display: flex; align-items: center; gap: 12px; width: 100%; margin-top: 12px; padding: 12px 14px; border-radius: 18px; text-align: left; color: inherit;
		background: var(--card); border: 1px solid var(--stroke); transition: transform var(--dur-1) var(--ease-out); }
	.next:active { transform: scale(0.98); }
	.next .ni { width: 44px; height: 44px; border-radius: 14px; display: grid; place-items: center; background: var(--accent); color: var(--on-accent); flex: none; }
	.next .nt { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
	.next .nt b { font-size: 16px; }
	.pick { display: block; margin: 8px auto 0; }
	.ti { width: 36px; height: 36px; border-radius: 11px; display: grid; place-items: center; background: rgba(var(--accent-rgb), .16); color: var(--accent); flex: none; }
</style>
