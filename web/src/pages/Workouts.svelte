<script lang="ts">
	import type { MuscleGroup } from '@common/docs.ts'
	import { deleteWorkout, setActiveProgram } from '../lib/actions.ts'
	import { activeProgram, daysOf } from '../lib/derive.ts'
	import { GROUP_NAMES, V, duration, relDay, weekdayName } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Prg, Tpl, Wk } from '../lib/types.ts'
	import FocusPicker from '../components/FocusPicker.svelte'
	import Icon from '../components/Icon.svelte'
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
</script>

<main class="screen page split">
	<div class="bar span"><h1>{t('Allenamento')}</h1></div>

	<!-- a schermo largo: a sinistra cosa fare adesso e la scheda, a destra lo storico -->
	<div class="blk">
		{#if active}
			<div class="card" style="border-color:rgba(var(--accent-rgb),.4)">
				<div class="tiny" style="color:var(--accent);font-weight:800;letter-spacing:.08em">{t('SESSIONE IN CORSO')}</div>
				<div class="mid" style="margin:4px 0">{title(active)}</div>
				<div class="small muted">{tn(active.workingSets, '1 serie', '{0} serie')} · {V(active.volume)} · {tn(exCount(active), '1 esercizio', '{0} esercizi')}</div>
				<button class="btn primary block" style="margin-top:12px" onclick={() => go(`allenamento/${active.uuid}`)}>{t('Riprendi allenamento')}</button>
			</div>
		{:else}
			<div class="card">
				<div class="mid">{t('Inizia allenamento')}</div>
				<p class="muted small" style="margin:4px 0 12px">{t('Scegli cosa alleni e registra le serie una per una.')}</p>
				<button class="btn primary block" onclick={() => (starting = true)}><Icon name="plus" size={20} />{t('Inizia allenamento')}</button>
			</div>
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
					<button class="item" onclick={() => (editing = tpl)}>
						<span class="ti"><TemplateIconView icon={tpl.icon} name={tpl.name} size={22} /></span>
						<div class="grow"><b class="ellipsis" style="display:block">{tpl.name}</b><span class="small muted">{tn(tpl.items.length, '1 esercizio', '{0} esercizi')}</span></div>
						{#if prog?.useDays}{#if tpl.weekday >= 0}<span class="badge accent">{weekdayName(tpl.weekday)}</span>{:else}<span class="small faint">{t('Nessuno')}</span>{/if}{/if}
						<Icon name="right" size={20} />
					</button>
				{/each}
			</div>
		{/if}
		<button class="btn block" style="margin-top:10px" onclick={() => (editing = 'new')} data-tour="new-template"><Icon name="plus" size={20} />{t('Nuovo giorno')}</button>
	</div>

	<div class="blk">
		<div class="sec-title">{t('Storico')}</div>
		{#if history.length === 0}
			<div class="empty">{t('Qui compariranno gli allenamenti conclusi.')}</div>
		{:else}
			<div class="stack cells">
				{#each history as w (w.uuid)}
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
				{/each}
			</div>
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
	.ti { width: 36px; height: 36px; border-radius: 11px; display: grid; place-items: center; background: rgba(var(--accent-rgb), .16); color: var(--accent); flex: none; }
</style>
