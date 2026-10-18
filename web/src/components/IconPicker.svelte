<script lang="ts">
	import { BODY_ICONS, GLYPHS, MUSCLE_GROUPS, TEMPLATE_ICONS, type TemplateIcon } from '@common/docs.ts'
	import { GLYPH_TITLES } from '@common/glyph.ts'
	import { GROUP_NAMES } from '../lib/fmt.ts'
	import { t } from '../lib/i18n.ts'
	import Sheet from './Sheet.svelte'
	import TemplateIconView from './TemplateIcon.svelte'

	/** Tutti i simboli per una scheda: i muscoli, gli attrezzi, le icone generali. */
	let { current = '', onpick, onclose }: { current?: '' | TemplateIcon; onpick: (icon: TemplateIcon) => void; onclose: () => void } = $props()
	const pick = (i: TemplateIcon) => { onpick(i); onclose() }
</script>

<Sheet title={t('Icone')} {onclose}>
	<div class="stack">
		<div class="sec-title" style="margin:0 4px">{t('Muscoli')}</div>
		<div class="grid">
			{#each MUSCLE_GROUPS as g}
				<button class="cell" class:on={current === `muscle:${g}`} onclick={() => pick(`muscle:${g}`)} aria-label={GROUP_NAMES[g]} title={GROUP_NAMES[g]}><TemplateIconView icon={`muscle:${g}`} size={34} /></button>
			{/each}
		</div>
		<div class="sec-title" style="margin:8px 4px 0">{t('Corpo')}</div>
		<div class="grid">
			{#each BODY_ICONS as b}
				<button class="cell" class:on={current === `body:${b}`} onclick={() => pick(`body:${b}`)} aria-label={b}><TemplateIconView icon={`body:${b}`} size={34} /></button>
			{/each}
		</div>
		<div class="sec-title" style="margin:8px 4px 0">{t('Attrezzi')}</div>
		<div class="grid">
			{#each GLYPHS as g}
				<button class="cell" class:on={current === `glyph:${g}`} onclick={() => pick(`glyph:${g}`)} aria-label={GLYPH_TITLES[g]} title={GLYPH_TITLES[g]}><TemplateIconView icon={`glyph:${g}`} size={34} /></button>
			{/each}
		</div>
		<div class="sec-title" style="margin:8px 4px 0">{t('Generali')}</div>
		<div class="grid">
			{#each TEMPLATE_ICONS as i}
				<button class="cell" class:on={(current || 'dumbbell') === i} onclick={() => pick(i)} aria-label={i}><TemplateIconView icon={i} size={30} /></button>
			{/each}
		</div>
	</div>
</Sheet>

<style>
	.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(56px, 1fr)); gap: 8px; }
	.cell { aspect-ratio: 1; border-radius: 14px; border: 1px solid var(--stroke); background: var(--card); color: var(--text-2); display: grid; place-items: center; padding: 8px; }
	.cell.on { border-color: var(--accent); background: rgba(var(--accent-rgb), .16); color: var(--accent); }
</style>
