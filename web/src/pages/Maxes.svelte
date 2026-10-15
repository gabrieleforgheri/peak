<script lang="ts">
	import { MUSCLE_GROUPS, type MuscleGroup } from '@common/docs.ts'
		import { patchExercise } from '../lib/actions.ts'
	import { deltaOf, warmupFor } from '../lib/derive.ts'
	import { GROUP_NAMES, W, delta, relDay, unit } from '../lib/fmt.ts'
	import { locale, t } from '../lib/i18n.ts'
	import { go } from '../lib/router.svelte.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import type { Ex } from '../lib/types.ts'
	import ExerciseEditor from '../components/ExerciseEditor.svelte'
	import Icon from '../components/Icon.svelte'
	import Sheet from '../components/Sheet.svelte'
	import Thumb from '../components/Thumb.svelte'

	const m = $derived(getModel())
	let query = $state('')
	let filter = $state<MuscleGroup | null>(null)
	let creating = $state(false)
	let menu = $state<Ex | null>(null)

	const visible = $derived(m.exercises.filter((e) => !e.isArchived && (!filter || e.muscleGroup === filter) && e.name.toLowerCase().includes(query.trim().toLowerCase())))
	const groups = $derived(MUSCLE_GROUPS.map((g) => ({ g, items: visible.filter((e) => e.muscleGroup === g) })).filter((x) => x.items.length))
	const active = $derived(m.exercises.filter((e) => !e.isArchived))
	const withMax = $derived(active.filter((e) => e.best).length)

	// tenere premuto (o tasto destro) apre le azioni, al posto dello swipe di iOS
	let pressTimer: ReturnType<typeof setTimeout> | undefined
	let longPressed = false
	function press(e: PointerEvent, ex: Ex) {
		longPressed = false
		clearTimeout(pressTimer)
		pressTimer = setTimeout(() => { longPressed = true; menu = ex }, 520)
	}
	const release = () => clearTimeout(pressTimer)
	function open(ex: Ex) { if (!longPressed) go(`macchinari/${ex.uuid}`) }

	function sub(ex: Ex): string {
		if (!ex.best) return t('Nessun massimale')
		const w = warmupFor(ex, m)[0]
		const first = w ? `${t('Risc.')} ${new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(w.weight)} ${unit()} · ` : ''
		return first + relDay(ex.best.date)
	}
</script>

<main class="screen">
	<div class="bar">
		<h1>{t('Macchinari')}</h1>
		<button class="icon-btn" onclick={() => (creating = true)} aria-label={t('Nuovo macchinario')}><Icon name="plus" /></button>
	</div>

	<div style="position:relative;margin-bottom:10px">
		<input class="input" style="padding-left:40px" type="search" placeholder={t('Cerca macchinario')} bind:value={query} />
		<span style="position:absolute;left:12px;top:12px;color:var(--text-3)"><Icon name="search" size={22} /></span>
	</div>
	<div class="chips" style="margin-bottom:6px">
		<button class="chip" class:on={filter === null} onclick={() => (filter = null)}>{t('Tutti')}</button>
		{#each MUSCLE_GROUPS as g}
			<button class="chip" class:on={filter === g} onclick={() => (filter = filter === g ? null : g)}>{GROUP_NAMES[g]}</button>
		{/each}
	</div>

	{#if active.length === 0}
		<div class="empty"><div class="mid">{t('Aggiungi macchinario')}</div>{t('Tocca + per creare il primo.')}</div>
	{:else}
		{#each groups as { g, items }}
			<div class="sec-title" style="display:flex;justify-content:space-between"><span style="color:var(--{g})">{GROUP_NAMES[g]}</span><span>{items.length}</span></div>
			<div class="card list">
				{#each items as ex (ex.uuid)}
					<button class="item" onclick={() => open(ex)} onpointerdown={(e) => press(e, ex)} onpointerup={release} onpointerleave={release} onpointercancel={release}
						oncontextmenu={(e) => { e.preventDefault(); menu = ex }}>
						<Thumb {ex} />
						<div class="grow">
							<div class="ellipsis" style="font-weight:600">{ex.name}</div>
							<div class="small muted ellipsis">{sub(ex)}</div>
						</div>
						{#if ex.best}
							<div style="text-align:right">
								<div class="num" style="font-weight:800;font-size:19px">{W(ex.best.weight, false)} <span class="small muted" style="font-weight:600">{unit()} × {ex.best.reps}</span></div>
								{#if deltaOf(ex) !== null}<span class="badge pos">{delta(deltaOf(ex)!)}</span>{/if}
							</div>
						{/if}
					</button>
				{/each}
			</div>
		{/each}
		{#if visible.length === 0}<div class="empty">{t('Nessun risultato.')}</div>{/if}
		<p class="faint small" style="text-align:center;margin-top:16px">{t('{0} macchinari con massimale su {1}', withMax, active.length)}</p>
	{/if}
</main>

{#if creating}<ExerciseEditor onclose={() => (creating = false)} onsaved={(id) => go(`macchinari/${id}`)} />{/if}
{#if menu}
	<Sheet title={menu.name} onclose={() => (menu = null)}>
		<div class="stack">
			<button class="btn block" onclick={() => { go(`macchinari/${menu!.uuid}`); menu = null }}>{t('Apri')}</button>
			<button class="btn block" onclick={() => { patchExercise(menu!.uuid, { isArchived: true }); menu = null }}><Icon name="archive" size={18} />{t('Archivia')}</button>
		</div>
	</Sheet>
{/if}
