<script lang="ts">
	import { onMount } from 'svelte'
	import { t } from '../lib/i18n.ts'
	import { call, resync, syncNow } from '../lib/sync.ts'
	import Icon from '../components/Icon.svelte'

	let { onstart }: { onstart: () => void } = $props()
	let busy = $state('')
	let error = $state('')
	let waiting = $state(false)

	// mentre si aspettano i dati dall'iPhone si controlla il server ogni pochi secondi
	onMount(() => { const id = setInterval(() => { if (waiting) void syncNow() }, 5000); return () => clearInterval(id) })

	async function importFile(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0]
		if (!file) return
		busy = 'imp'; error = ''
		try {
			const res = await call('/api/importa?modo=unisci&primo=1', { method: 'POST', headers: { 'content-type': 'application/json' }, body: await file.text() })
			if (res.ok || res.status === 409) await resync()
			else if (res.status === 422) {
				const out = (await res.json()) as { error: string; version?: number }
				error = out.error === 'versione' ? t('Backup in versione {0}: serve una versione più recente di Peak.', out.version ?? 0) : t('Il file non è un backup di Massimali valido.')
			} else error = t('Non è andata: riprova.')
		} catch { error = t('Serve la connessione.') }
		busy = ''
	}
</script>

<main class="screen no-tabs">
	<div class="bar"><h1>{t('Benvenuto in Peak')}</h1></div>
	<p class="muted" style="margin-bottom:16px">{t('Il tuo account è vuoto. Da dove vuoi partire?')}</p>
	<div class="stack">
		<div class="card">
			<div class="row"><Icon name="phone" size={28} /><div class="grow"><div class="mid">{t('Ho i dati sull\'iPhone')}</div></div></div>
			<p class="muted small" style="margin:8px 0 12px">{t('Apri Peak sull\'iPhone, vai in Impostazioni → Account yG e tocca Collega. I dati arrivano qui da soli.')}</p>
			<button class="btn block" onclick={() => { waiting = true; void syncNow() }} disabled={waiting}>{waiting ? t('In attesa dei dati…') : t('Aspetta i dati dall\'iPhone')}</button>
		</div>
		<div class="card">
			<div class="row"><Icon name="upload" size={28} /><div class="grow"><div class="mid">{t('Importa un backup')}</div></div></div>
			<p class="muted small" style="margin:8px 0 12px">{t('Un file di backup esportato dall\'app Massimali.')}</p>
			<label class="btn block" class:primary={false} style="cursor:pointer">
				{busy === 'imp' ? t('Importo…') : t('Scegli il file')}
				<input type="file" accept="application/json,.json" hidden onchange={importFile} disabled={!!busy} />
			</label>
		</div>
		<div class="card">
			<div class="row"><Icon name="dumbbell" size={28} /><div class="grow"><div class="mid">{t('Parto da zero')}</div></div></div>
			<p class="muted small" style="margin:8px 0 12px">{t('Nessun macchinario: li aggiungi cercando nel catalogo.')}</p>
			<button class="btn primary block" onclick={onstart} disabled={!!busy}>{t('Inizia')}</button>
		</div>
		{#if error}<div class="banner warn">{error}</div>{/if}
	</div>
</main>
