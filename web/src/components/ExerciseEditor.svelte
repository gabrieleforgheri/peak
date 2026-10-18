<script lang="ts">
	import { GLYPHS, MUSCLE_GROUPS, type GlyphName, type MuscleGroup } from '@common/docs.ts'
	import type { Muscle, View } from '@common/muscle-info.ts'
	import { GLYPH_TITLES, glyphOf } from '@common/glyph.ts'
	import { PhotoFraming } from '@common/domain.ts'
	import { deleteExercise, saveExercise } from '../lib/actions.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { getModel } from '../lib/store.svelte.ts'
	import { photoUrl, preparePhoto } from '../lib/photo.ts'
	import type { CatalogEntry } from '@common/catalog.ts'
	import { entryName, loadCatalog } from '../lib/catalog.ts'
	import CatalogPicker from './CatalogPicker.svelte'
	import Glyph from './Glyph.svelte'
	import MuscleFigure from './MuscleFigure.svelte'
	import Icon from './Icon.svelte'
	import Sheet from './Sheet.svelte'
	import Stepper from './Stepper.svelte'

	let { id = null, name: startName = '', onclose, onsaved, ondeleted }: {
		id?: string | null; name?: string; onclose: () => void; onsaved?: (id: string) => void; ondeleted?: () => void
	} = $props()

	const m = getModel()
	const existing = id ? m.exById.get(id) : undefined
	let name = $state(existing?.name ?? startName)
	let group = $state<MuscleGroup>(existing?.muscleGroup ?? 'petto')
	let notes = $state(existing?.notes ?? '')
	let step = $state(existing?.incrementStep ?? 2.5)
	let glyph = $state<'' | GlyphName>(existing?.glyph ?? '')
	let photo = $state(existing?.photo ?? '')
	let offset = $state(existing?.photoOffset ?? 0)
	let unilateral = $state(existing?.isUnilateral ?? false)
	let rest = $state(existing?.restSeconds ?? 0)
	let archived = $state(existing?.isArchived ?? false)
	// i muscoli di un macchinario personalizzato: si tocca la figura. Collegato, valgono quelli della voce del catalogo
	let muscles = $state<Muscle[]>(existing?.muscles ?? [])
	let view = $state<'' | View>(existing?.view ?? '')
	let hidden = $state(existing?.hidden ?? false)
	// creato a mano di proposito: non gli si ricorda di collegarlo al catalogo
	let own = $state(existing?.custom ?? false)

	// il catalogo: un macchinario nuovo parte dalla ricerca; uno esistente può essere (ri)collegato
	let catalog = $state(existing?.catalog ?? '')
	let picking = $state<'new' | 'link' | null>(id ? null : 'new')
	let linked = $state<CatalogEntry | null>(null)
	$effect(() => { const c = catalog; if (!c) { linked = null; return } void loadCatalog().then((l) => { if (catalog === c) linked = l.find((e) => e.id === c) ?? null }) })

	function chosen(e: CatalogEntry | null, g: MuscleGroup) {
		const wasNew = picking === 'new'
		picking = null
		catalog = e?.id ?? ''
		if (!e) { if (wasNew) { group = g; own = true } return }
		if (wasNew) {
			// precompila: il nome si può cambiare subito con uno familiare
			name = startName.trim() || entryName(e); group = e.group; step = e.step; notes = e.notes
		}
	}

	// collegato: l'icona del suo attrezzo; personalizzato: quella che scegli, o nessuna
	const shown = $derived(glyphOf({ glyph, catalog }, linked?.glyph))
	let src = $state('')
	let aspect = $state(1)
	$effect(() => { const s = photo; src = ''; if (s) photoUrl(s).then((u) => { if (photo === s) src = u }) })

	async function pick(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0]
		;(e.target as HTMLInputElement).value = ''
		if (!file) return
		photo = await preparePhoto(file)
		offset = 0
	}

	// l'inquadratura si regola trascinando l'anteprima, sull'asse che sborda
	let drag: { x: number; y: number; off: number } | null = null
	function down(e: PointerEvent) { drag = { x: e.clientX, y: e.clientY, off: offset }; (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId) }
	function move(e: PointerEvent) {
		if (!drag) return
		const pts = PhotoFraming.isVertical(aspect) ? e.clientY - drag.y : e.clientX - drag.x
		offset = Math.max(-1, Math.min(1, drag.off + PhotoFraming.normalizedDelta(pts, aspect, 200)))
	}

	function toggleMuscle(m: Muscle) {
		muscles = muscles.includes(m) ? muscles.filter((x) => x !== m) : [...muscles, m]
	}
	function flipView() {
		view = (view || 'front') === 'front' ? 'back' : 'front'
	}

	const valid = $derived(name.trim().length > 0)
	function save() {
		if (!valid) return
		const uuid = saveExercise(id, {
			name: name.trim(), muscleGroup: group, notes, incrementStep: step, isArchived: archived, glyph, photo,
			photoOffset: Math.round(offset * 1000) / 1000, isUnilateral: unilateral, restSeconds: rest,
			catalog, hidden, custom: !catalog && own, muscles, view,
		})
		onsaved?.(uuid)
		onclose()
	}
	function del() {
		if (!id || !confirm(t('Eliminare questo macchinario? I suoi massimali vanno persi; le serie restano negli allenamenti.'))) return
		deleteExercise(id)
		ondeleted?.()
		onclose()
	}
</script>

{#if picking}
	<CatalogPicker title={picking === 'new' ? t('Nuovo macchinario') : t('Collega al catalogo')} custom={picking === 'new'} group={picking === 'link' ? group : null} onpick={chosen} onclose={() => { if (picking === 'new') onclose(); picking = null }} />
{:else}
<Sheet title={id ? t('Modifica macchinario') : t('Nuovo macchinario')} {onclose}>
	{#snippet action()}<button class="btn primary small" onclick={save} disabled={!valid}>{t('Salva')}</button>{/snippet}
	<div class="stack">
		<label class="field"><span>{t('Nome')}</span><input class="input" bind:value={name} placeholder={t('es. Chest Press')} maxlength="80" /></label>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Catalogo')}</span>
			<div class="row card">
				<div class="grow">{#if catalog}<b>{linked ? entryName(linked) : catalog}</b>{:else}<span class="muted">{t('Non collegato: resta fuori dalle classifiche.')}</span>{/if}</div>
				<button class="btn small" onclick={() => (picking = 'link')}>{catalog ? t('Cambia') : t('Collega')}</button>
			</div>
		</div>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Immagine')}</span>
			<div class="row" style="align-items:flex-start">
				<div style="width:200px;height:200px;border-radius:18px;overflow:hidden;flex:none;position:relative;touch-action:none;background:color-mix(in srgb, var(--{group}) 20%, transparent);color:var(--{group});display:grid;place-items:center"
					onpointerdown={down} onpointermove={move} onpointerup={() => (drag = null)} onpointercancel={() => (drag = null)} role="presentation">
					{#if photo && src}
						<img {src} alt="" draggable="false" onload={(e) => { const i = e.currentTarget as HTMLImageElement; aspect = i.naturalWidth / i.naturalHeight }}
							style="width:100%;height:100%;object-fit:cover;object-position:{50 - offset * 50}% {50 - offset * 50}%" />
					{:else if shown}
						<div style="width:60%;height:60%"><Glyph name={shown} /></div>
					{/if}
				</div>
				<div class="stack" style="flex:1;gap:8px">
					<label class="btn small block" style="cursor:pointer"><Icon name="camera" size={18} />{t('Scatta')}<input type="file" accept="image/*" capture="environment" hidden onchange={pick} /></label>
					<label class="btn small block" style="cursor:pointer"><Icon name="upload" size={18} />{t('Dalla libreria')}<input type="file" accept="image/*" hidden onchange={pick} /></label>
					{#if photo}
						<button class="btn small block" onclick={() => (offset = 0)}>{t('Centra')}</button>
						<button class="btn small danger block" onclick={() => { photo = ''; offset = 0 }}>{t('Rimuovi')}</button>
						<p class="faint tiny">{t('Trascina l\'anteprima per inquadrare.')}</p>
					{/if}
				</div>
			</div>
		</div>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Muscoli')}</span>
			{#if catalog}
				<p class="muted small" style="margin:0 4px 8px">{t('Sono quelli dell\'attrezzo del catalogo.')}</p>
				<div style="max-width:200px"><MuscleFigure muscles={linked?.muscles ?? []} view={linked?.view ?? ''} /></div>
			{:else}
				<p class="muted small" style="margin:0 4px 8px">{t('Tocca i muscoli che lavorano sulla figura.')}</p>
				<div style="max-width:220px"><MuscleFigure {muscles} view={view || 'front'} zoom={false} ontap={toggleMuscle} /></div>
				<button class="chip" style="margin:8px 4px 0" onclick={flipView}>{(view || 'front') === 'front' ? t('Vedi di schiena') : t('Vedi di fronte')}</button>
			{/if}
		</div>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Pittogramma')}</span>
			{#if catalog}
				<p class="muted small" style="margin:0 4px">{t('È quello dell\'attrezzo del catalogo.')}</p>
			{:else}
			<div class="chips">
				<button class="chip" class:on={glyph === ''} onclick={() => (glyph = '')}>{t('Nessuno')}</button>
				{#each GLYPHS as g}
					<button class="chip" class:on={glyph === g} onclick={() => (glyph = g)} title={GLYPH_TITLES[g]} aria-label={GLYPH_TITLES[g]} style="padding:0 10px">
						<span style="width:24px;height:24px;display:inline-block;vertical-align:middle"><Glyph name={g} /></span>
					</button>
				{/each}
			</div>
			{/if}
		</div>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Gruppo muscolare')}</span>
			<div class="tiles">
				{#each MUSCLE_GROUPS as g}
					<button class="chip" class:on={group === g} onclick={() => (group = g)} style="min-height:44px;border-radius:14px">{GROUP_NAMES[g]}</button>
				{/each}
			</div>
		</div>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Incremento del carico')}</span>
			<Stepper compact bind:value={step} step={0.25} min={0.25} max={50} unit="kg" />
		</div>

		<div class="field">
			<span class="sec-title" style="margin:0 4px 6px">{t('Esecuzione')}</span>
			<div class="seg"><button class:on={!unilateral} onclick={() => (unilateral = false)}>{group === 'gambe' ? t('Due gambe') : t('Due braccia')}</button><button class:on={unilateral} onclick={() => (unilateral = true)}>{group === 'gambe' ? t('Una gamba') : t('Un braccio')}</button></div>
		</div>

		{#if m.settings.restTimerEnabled}
			<div class="field">
				<span class="sec-title" style="margin:0 4px 6px">{t('Recupero')}</span>
				<Stepper bind:value={rest} step={15} min={0} max={600} unit={rest === 0 ? t('Come le impostazioni') : t('secondi')} />
			</div>
		{/if}

		<label class="field"><span>{t('Note')}</span><textarea class="textarea" bind:value={notes} maxlength="2000"></textarea></label>

		{#if !catalog}<label class="row between card"><span>{t('Macchinario mio, non è nel catalogo')}</span><input class="toggle" type="checkbox" bind:checked={own} /></label>{/if}
		<label class="row between card"><span>{t('Nascondi agli amici')}</span><input class="toggle" type="checkbox" bind:checked={hidden} /></label>

		{#if id}
			<label class="row between card"><span>{t('Archiviato')}</span><input class="toggle" type="checkbox" bind:checked={archived} /></label>
			<button class="btn danger block" onclick={del}><Icon name="trash" size={18} />{t('Elimina macchinario')}</button>
		{/if}
	</div>
</Sheet>
{/if}
