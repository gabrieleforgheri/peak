<script lang="ts">
	import type { MuscleGroup } from '@common/docs.ts'
	import { deleteWorkout } from '../lib/actions.ts'
	import { GROUP_NAMES, V, duration, relDay } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Wk } from '../lib/types.ts'
	import FocusPicker from '../components/FocusPicker.svelte'
	import Icon from '../components/Icon.svelte'
	import Sheet from '../components/Sheet.svelte'

	const m = $derived(getModel())
	const active = $derived(m.workouts.find((w) => w.isActive))
	const history = $derived(m.workouts.filter((w) => !w.isActive))
	let starting = $state(false)
	let menu = $state<Wk | null>(null)

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

<main class="screen">
	<div class="bar"><h1>{t('Allenamenti')}</h1></div>

	{#if active}
		<div class="card" style="border-color:rgba(var(--accent-rgb),.4)">
			<div class="tiny" style="color:var(--accent);font-weight:800;letter-spacing:.08em">{t('SESSIONE IN CORSO')}</div>
			<div class="mid" style="margin:4px 0">{title(active)}</div>
			<div class="small muted">{tn(active.workingSets, '1 serie', '{0} serie')} · {V(active.volume)} · {tn(exCount(active), '1 esercizio', '{0} esercizi')}</div>
			<button class="btn primary block" style="margin-top:12px" onclick={() => go(`allenamenti/${active.uuid}`)}>{t('Riprendi allenamento')}</button>
		</div>
	{:else}
		<div class="card">
			<div class="mid">{t('Inizia allenamento')}</div>
			<p class="muted small" style="margin:4px 0 12px">{t('Scegli cosa alleni e registra le serie una per una.')}</p>
			<button class="btn primary block" onclick={() => (starting = true)}><Icon name="plus" size={20} />{t('Inizia allenamento')}</button>
		</div>
	{/if}

	<div class="sec-title">{t('Storico')}</div>
	{#if history.length === 0}
		<div class="empty">{t('Qui compariranno gli allenamenti conclusi.')}</div>
	{:else}
		<div class="stack">
			{#each history as w (w.uuid)}
				<button class="card" style="text-align:left;color:inherit;display:block;width:100%"
					onclick={() => { if (!longPressed) go(`allenamenti/${w.uuid}`) }} onpointerdown={() => press(w)} onpointerup={release} onpointerleave={release} onpointercancel={release}
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
</main>

{#if starting}<FocusPicker onclose={() => (starting = false)} onstart={(id) => go(`allenamenti/${id}`)} />{/if}
{#if menu}
	<Sheet title={title(menu)} onclose={() => (menu = null)}>
		<div class="stack">
			<button class="btn block" onclick={() => { go(`allenamenti/${menu!.uuid}`); menu = null }}>{t('Apri')}</button>
			<button class="btn danger block" onclick={() => { if (confirm(t('Eliminare questo allenamento e le sue serie?'))) deleteWorkout(menu!.uuid); menu = null }}><Icon name="trash" size={18} />{t('Elimina')}</button>
		</div>
	</Sheet>
{/if}
