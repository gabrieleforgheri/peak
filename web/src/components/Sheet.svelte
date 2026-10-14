<script lang="ts">
	import type { Snippet } from 'svelte'
	import { t } from '../lib/i18n.ts'
	import Icon from './Icon.svelte'

	let { title, onclose, children, action }: { title: string; onclose: () => void; children: Snippet; action?: Snippet } = $props()

	$effect(() => {
		const prev = document.body.style.overflow
		document.body.style.overflow = 'hidden'
		return () => { document.body.style.overflow = prev }
	})
</script>

<svelte:window onkeydown={(e) => e.key === 'Escape' && onclose()} />
<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div class="backdrop" onclick={(e) => e.target === e.currentTarget && onclose()}>
	<div class="sheet" role="dialog" aria-modal="true" aria-label={title}>
		<div class="grab"></div>
		<div class="sheet-head">
			<button class="icon-btn" onclick={onclose} aria-label={t('Chiudi')}><Icon name="x" /></button>
			<h2>{title}</h2>
			{#if action}{@render action()}{:else}<span style="width:40px"></span>{/if}
		</div>
		{@render children()}
	</div>
</div>
