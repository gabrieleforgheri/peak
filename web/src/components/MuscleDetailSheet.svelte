<script lang="ts">
	import type { Ex } from '../lib/types.ts'
		import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import { loadCatalog } from '../lib/catalog.ts'
	import Sheet from './Sheet.svelte'
	import MuscleFigure from './MuscleFigure.svelte'
	import MuscleChips from './MuscleChips.svelte'
	import MuscleZoom from './MuscleZoom.svelte'
	import Icon from './Icon.svelte'

	let { ex, onclose }: { ex: Ex; onclose: () => void } = $props()

	let catalogNotes = $state('')
	$effect(() => {
		if (ex.catalog) {
			loadCatalog().then((cat) => {
				const item = cat.find((c) => c.id === ex.catalog)
				if (item) catalogNotes = item.notes
			})
		}
	})

	const notes = $derived(ex.notes.trim() || catalogNotes.trim())
	const muscles = $derived(ex.figure.muscles)
	/** La figura aperta a tutto schermo, se c'è. */
	let zoomSide = $state<'' | 'front' | 'back'>('')
</script>

<Sheet title={ex.name} {onclose}>
	<div class="stack" style="gap:16px">
		<!-- Intestazione con badge del gruppo -->
		<div class="row between" style="align-items:center">
			<span class="badge" style="background:color-mix(in srgb, var(--{ex.muscleGroup}) 22%, transparent);color:var(--{ex.muscleGroup});font-weight:800;letter-spacing:.05em;padding:4px 10px;border-radius:8px;border:1px solid color-mix(in srgb, var(--{ex.muscleGroup}) 40%, transparent)">
				{GROUP_NAMES[ex.muscleGroup]}
			</span>
		</div>

		<!-- Mappa muscolare completa fronte e retro -->
		<div class="card" style="padding:16px;background:var(--card);border-radius:18px">
			<div class="tiny muted" style="font-weight:800;text-transform:uppercase;letter-spacing:.06em;margin-bottom:12px;text-align:center">{t('Mappa muscolare')}</div>
			<div class="row center" style="gap:16px;justify-content:center">
				<div class="stack center" style="width:115px;height:230px;gap:6px">
					<button class="fig-btn grow" style="width:100%" onclick={() => (zoomSide = 'front')} aria-label={t('Ingrandisci')}>
						<MuscleFigure {muscles} view="front" zoom={false} />
						<span class="lens"><Icon name="zoom-in" size={16} /></span>
					</button>
					<span class="tiny muted" style="font-weight:700">{t('Fronte')}</span>
				</div>
				<div class="stack center" style="width:115px;height:230px;gap:6px">
					<button class="fig-btn grow" style="width:100%" onclick={() => (zoomSide = 'back')} aria-label={t('Ingrandisci')}>
						<MuscleFigure {muscles} view="back" zoom={false} />
						<span class="lens"><Icon name="zoom-in" size={16} /></span>
					</button>
					<span class="tiny muted" style="font-weight:700">{t('Retro')}</span>
				</div>
			</div>
		</div>

		<!-- Lista dei muscoli coinvolti -->
		{#if muscles.length}
			<div class="stack" style="gap:8px">
				<div class="tiny muted" style="font-weight:800;text-transform:uppercase;letter-spacing:.06em">{t('Muscoli coinvolti')}</div>
				<MuscleChips {muscles} />
			</div>
		{/if}

		<!-- Consigli e setup se presenti -->
		{#if notes}
			<div class="stack" style="gap:6px;background:rgba(255,255,255,0.04);border:1px solid var(--border);border-radius:14px;padding:12px 14px">
				<div class="tiny muted" style="font-weight:800;text-transform:uppercase;letter-spacing:.06em;color:var(--accent)">{t('Consigli e setup')}</div>
				<div class="small" style="line-height:1.5;color:var(--text)">{t(notes)}</div>
			</div>
		{/if}

		<button class="btn block" onclick={onclose}>{t('Chiudi')}</button>
	</div>
</Sheet>

{#if zoomSide}<MuscleZoom {muscles} side={zoomSide} onclose={() => (zoomSide = '')} />{/if}

<style>
	/* il tasto è la figura stessa, con la lente in un angolo che dice che si può toccare */
	.fig-btn { position: relative; display: block; background: none; border: 0; padding: 0; min-height: 0; color: inherit; cursor: zoom-in; }
	.lens { position: absolute; right: -4px; bottom: -4px; width: 26px; height: 26px; display: grid; place-items: center; border-radius: 50%; background: var(--card-strong); color: var(--text); pointer-events: none; }
</style>
