<script lang="ts">
	import type { Ex } from '../lib/types.ts'
	import { MUSCLE_INFO } from '@common/muscle-info.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t, lang } from '../lib/i18n.ts'
	import { loadCatalog } from '../lib/catalog.ts'
	import Sheet from './Sheet.svelte'
	import MuscleFigure from './MuscleFigure.svelte'

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
					<div class="grow" style="width:100%"><MuscleFigure {muscles} view="front" zoom={false} /></div>
					<span class="tiny muted" style="font-weight:700">{t('Fronte')}</span>
				</div>
				<div class="stack center" style="width:115px;height:230px;gap:6px">
					<div class="grow" style="width:100%"><MuscleFigure {muscles} view="back" zoom={false} /></div>
					<span class="tiny muted" style="font-weight:700">{t('Retro')}</span>
				</div>
			</div>
		</div>

		<!-- Lista dei muscoli coinvolti -->
		{#if muscles.length}
			<div class="stack" style="gap:8px">
				<div class="tiny muted" style="font-weight:800;text-transform:uppercase;letter-spacing:.06em">{t('Muscoli coinvolti')}</div>
				<div class="row wrap" style="gap:8px">
					{#each muscles as m}
						<div class="muscle-chip" style="border-color:color-mix(in srgb, var(--{MUSCLE_INFO[m].group}) 45%, transparent);background:color-mix(in srgb, var(--{MUSCLE_INFO[m].group}) 20%, transparent)">
							<span class="dot" style="background:var(--{MUSCLE_INFO[m].group})"></span>
							<span style="font-weight:700;font-size:13px;color:var(--text)">{lang === 'it' ? MUSCLE_INFO[m].it : MUSCLE_INFO[m].en}</span>
						</div>
					{/each}
				</div>
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

<style>
	.muscle-chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 5px 10px;
		border-radius: 8px;
		border-width: 1px;
		border-style: solid;
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		flex-shrink: 0;
	}
</style>
