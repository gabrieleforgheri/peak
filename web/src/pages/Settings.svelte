<script lang="ts">
	import { onMount } from 'svelte'
	import { ACCENTS } from '@common/docs.ts'
	import { PLATES_KG_ALL, PLATES_LB_ALL, round2, toDisplay } from '@common/domain.ts'
	import { changeUnit, eraseAll, setSetting } from '../lib/actions.ts'
	import { fromUnit, toUnit, unit } from '../lib/fmt.ts'
	import { t, tn } from '../lib/i18n.ts'
	import { go, route } from '../lib/router.svelte.ts'
	import { S, getModel } from '../lib/store.svelte.ts'
	import { disablePush, enablePush, pushState, type PushState } from '../lib/push.ts'
	import { call, resync, syncNow } from '../lib/sync.ts'
	import { toast } from '../lib/toast.svelte.ts'
	import Icon from '../components/Icon.svelte'
	import Sheet from '../components/Sheet.svelte'
	import Stepper from '../components/Stepper.svelte'

	const m = $derived(getModel())
	const s = $derived(m.settings)
	const ACCENT_RGB: Record<string, string> = { lime: '184,240,77', ember: '255,148,61', ice: '115,204,255', violet: '173,140,255', coral: '255,115,115' }
	const ACCENT_NAME: Record<string, string> = { lime: 'Lime', ember: 'Brace', ice: 'Ghiaccio', violet: 'Viola', coral: 'Corallo' }

	let devices = $state<{ id: number; name: string; created_at: number; used_at: number | null }[]>([])
	const loadDevices = async () => { try { const r = await call('/api/dispositivi'); if (r.ok) devices = (await r.json()).dispositivi } catch { /* offline */ } }
	let push = $state<PushState>('unsupported')
	onMount(() => { void loadDevices(); void pushState().then((p) => (push = p)) })
	async function togglePush(on: boolean) {
		try { push = on ? await enablePush(S.user?.vapid ?? '') : await disablePush() } catch { toast(t('Non è andata: riprova.')); push = await pushState() }
	}

	// ---- backup ----
	let busy = $state(false)

	async function exportNow() {
		busy = true
		try {
			const res = await call('/api/backup')
			if (!res.ok) throw new Error(String(res.status))
			const blob = await res.blob()
			const name = `Peak-${new Date().toISOString().slice(0, 10)}.json`
			const file = new File([blob], name, { type: 'application/json' })
			// il foglio di condivisione dice se è finito davvero; il download no
			if (navigator.canShare?.({ files: [file] })) { await navigator.share({ files: [file] }) }
			else { const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 10_000) }
		} catch (e) { if ((e as Error).name !== 'AbortError') toast(t('Esportazione non riuscita.')) }
		busy = false
	}

	// ---- importazione ----
	let importing = $state<string | null>(null)
	let result = $state<string>('')
	function chooseFile(e: Event) {
		const input = e.target as HTMLInputElement
		const file = input.files?.[0]
		input.value = ''
		if (!file) return
		file.text().then((text) => { importing = text })
	}
	async function runImport(mode: 'unisci' | 'sostituisci') {
		if (!importing) return
		if (m.workouts.some((w) => w.isActive)) { toast(t('Termina l\'allenamento in corso prima di importare.')); return }
		if (mode === 'sostituisci' && !confirm(t('Sostituire tutto? Cancella i dati su yrb4g.com e su ogni dispositivo collegato.'))) return
		busy = true
		try {
			await syncNow()
			const res = await call(`/api/importa?modo=${mode}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: importing })
			const out = await res.json()
			if (res.status === 422) result = out.error === 'versione' ? t('Backup in versione {0}: serve una versione più recente di Peak.', out.version) : t('Il file non è un backup di Massimali valido.')
			else if (!res.ok) result = t('Non è andata: riprova.')
			else {
				const c = out.conteggi
				result = t('{0} macchinari e {1} allenamenti importati.', c.esercizi, c.allenamenti) + (out.orfani ? ' ' + tn(out.orfani, '1 massimale automatico senza serie è stato scartato.', '{0} massimali automatici senza serie sono stati scartati.') : '')
				await resync()
			}
		} catch { result = t('Serve la connessione.') }
		importing = null
		busy = false
	}

	// ---- dati ----
	function erase() {
		if (!confirm(t('Cancellare tutti i dati? Spariscono anche da yrb4g.com e da ogni dispositivo collegato. Il backup che hai esportato resta.'))) return
		eraseAll()
		void syncNow()
	}
	async function revoke(id: number) {
		if (!confirm(t('Scollegare questo dispositivo?'))) return
		await call(`/api/dispositivi/${id}`, { method: 'DELETE' })
		await loadDevices()
	}

	const plateChips = $derived(m.settings.unit === 'kg' ? PLATES_KG_ALL : PLATES_LB_ALL)
	const hasPlate = (p: number) => s.availablePlates.some((x) => Math.abs(round2(toDisplay(x, s.unit)) - p) < 0.01)
	function togglePlate(p: number) {
		const next = hasPlate(p) ? s.availablePlates.filter((x) => Math.abs(round2(toDisplay(x, s.unit)) - p) >= 0.01) : [...s.availablePlates, fromUnit(p)]
		setSetting('availablePlates', next.sort((a, b) => b - a))
	}
	// le categorie sono sottopagine: #/impostazioni/<id>
	const CATS = [
		{ id: 'generale', icon: 'sliders', label: t('Generale') },
		{ id: 'allenamento', icon: 'dumbbell', label: t('Allenamento') },
		{ id: 'account', icon: 'cloud', label: t('Account yG') },
		{ id: 'backup', icon: 'download', label: t('Backup') },
		{ id: 'dati', icon: 'archive', label: t('Dati') },
	]
	const cat = $derived(CATS.find((c) => c.id === route.parts[1]))
	const stepBar = $derived(s.unit === 'kg' ? 2.5 : 5)
</script>

<main class="screen" class:no-tabs={!!cat}>
{#if !cat}
	<div class="bar"><h1>{t('Impostazioni')}</h1></div>

	<div class="card list">
		{#each CATS as c (c.id)}
			<button class="item" onclick={() => go(`impostazioni/${c.id}`)}><Icon name={c.icon} size={22} /><div class="grow">{c.label}</div><Icon name="right" size={20} /></button>
		{/each}
	</div>

	<p class="faint small" style="text-align:center;margin-top:24px">Peak {__APP_VERSION__}</p>
{:else}
	<div class="bar">
		<button class="icon-btn" onclick={() => go('impostazioni')} aria-label={t('Indietro')}><Icon name="left" /></button>
		<h1 class="small">{cat.label}</h1>
		<span style="width:40px"></span>
	</div>

	{#if cat.id === 'generale'}
	<div class="card stack">
		<div class="seg"><button class:on={s.unit === 'kg'} onclick={() => changeUnit('kg')}>kg</button><button class:on={s.unit === 'lb'} onclick={() => changeUnit('lb')}>lb</button></div>
		<div class="field"><span class="sec-title" style="margin:0 4px 6px">{t('Colore')}</span>
			<div class="row" style="gap:14px">
				{#each ACCENTS as a}
					<button aria-label={ACCENT_NAME[a]} onclick={() => setSetting('accent', a)} style="width:38px;height:38px;border-radius:50%;border:3px solid {s.accent === a ? '#fff' : 'transparent'};background:rgb({ACCENT_RGB[a]})"></button>
				{/each}
			</div></div>
	</div>

	{:else if cat.id === 'allenamento'}
	<div class="card stack">
		<label class="row between"><span>{t('Timer di recupero')}</span><input class="toggle" type="checkbox" checked={s.restTimerEnabled} onchange={(e) => setSetting('restTimerEnabled', e.currentTarget.checked)} /></label>
		{#if s.restTimerEnabled}<Stepper value={s.restSeconds} step={15} min={15} max={600} unit={t('secondi')} onchange={(v) => setSetting('restSeconds', v)} />
			{#if push !== 'unsupported'}
				<label class="row between"><span>{t('Avviso a fine recupero')}<br /><span class="small muted">{push === 'needs-install' ? t('Su iPhone serve Peak aggiunta alla schermata Home (Condividi → Aggiungi alla schermata Home).') : push === 'denied' ? t('Le notifiche sono bloccate: riattivale dalle impostazioni del browser.') : t('Arriva anche a telefono bloccato. Con la rete assente suona solo con la pagina aperta.')}</span></span>
					<input class="toggle" type="checkbox" checked={push === 'on'} disabled={push === 'needs-install' || push === 'denied'} onchange={(e) => togglePush(e.currentTarget.checked)} /></label>
			{/if}{/if}
		<hr style="border:0;border-top:1px solid var(--stroke);width:100%;margin:0" />
		<label class="row between"><span>{t('Prossimo carico consigliato')}</span><input class="toggle" type="checkbox" checked={s.progressionEnabled} onchange={(e) => setSetting('progressionEnabled', e.currentTarget.checked)} /></label>
		{#if s.progressionEnabled}
			<div class="row"><div class="grow"><div class="small muted">{t('Ripetizioni minime')}</div><Stepper value={s.repRangeMin} step={1} min={1} max={30} onchange={(v) => { setSetting('repRangeMin', v); if (v > s.repRangeMax) setSetting('repRangeMax', v) }} /></div></div>
			<div class="row"><div class="grow"><div class="small muted">{t('Ripetizioni massime')}</div><Stepper value={s.repRangeMax} step={1} min={s.repRangeMin} max={30} onchange={(v) => setSetting('repRangeMax', v)} /></div></div>
		{/if}
		<hr style="border:0;border-top:1px solid var(--stroke);width:100%;margin:0" />
		<label class="row between"><span>{t('Calcolatore dischi')}</span><input class="toggle" type="checkbox" checked={s.plateCalculatorEnabled} onchange={(e) => setSetting('plateCalculatorEnabled', e.currentTarget.checked)} /></label>
		{#if s.plateCalculatorEnabled}
			<div><div class="small muted">{t('Peso del bilanciere')}</div><Stepper value={toUnit(s.barWeight)} step={stepBar} min={0} max={toUnit(30)} unit={unit()} onchange={(v) => setSetting('barWeight', fromUnit(v))} /></div>
			<div><div class="small muted" style="margin-bottom:6px">{t('Dischi disponibili')}</div>
				<div class="chips">{#each plateChips as p}<button class="chip" class:on={hasPlate(p)} onclick={() => togglePlate(p)}>{p} {unit()}</button>{/each}</div></div>
		{/if}
	</div>


	{:else if cat.id === 'account'}
	<div class="card stack">
		<div class="row"><Icon name="cloud" size={24} /><div class="grow"><b>{S.user?.nome ?? '—'}</b><div class="small muted">{S.dirty ? tn(S.dirty, '1 modifica da sincronizzare', '{0} modifiche da sincronizzare') : t('Tutto sincronizzato')}{S.online ? '' : ' · ' + t('offline')}</div></div>
			<button class="btn small" onclick={() => syncNow()} disabled={S.syncing}>{S.syncing ? '…' : t('Sincronizza')}</button></div>
		{#if devices.length}
			<div class="list">
				{#each devices as d (d.id)}
					<div class="item"><Icon name="phone" size={22} /><div class="grow"><div style="font-weight:600">{d.name}</div><div class="small muted">{d.used_at ? new Date(d.used_at).toLocaleString() : t('Mai usato')}</div></div><button class="btn small danger" onclick={() => revoke(d.id)}>{t('Scollega')}</button></div>
				{/each}
			</div>
		{/if}
		<a class="btn block" href="/auth/logout">{t('Esci')}</a>
	</div>

	<div class="sec-title">{t('Gestione account')}</div>
	<div class="card list">
		<a class="item" href="https://yrb4g.com/account" target="_blank" rel="noopener"><Icon name="cloud" size={22} /><div class="grow"><b>{t('Il tuo account yG')}</b><div class="small muted">{t('Profilo, password, accessi e le altre app')}</div></div><Icon name="right" size={20} /></a>
		<a class="item" href="https://yrb4g.com/account/peak" target="_blank" rel="noopener"><Icon name="phone" size={22} /><div class="grow"><b>{t('Accessi di Peak')}</b><div class="small muted">{t('Dispositivi collegati e dati salvati')}</div></div><Icon name="right" size={20} /></a>
	</div>

	{:else if cat.id === 'backup'}
	<div class="card stack">
		<button class="btn block" onclick={exportNow} disabled={busy}><Icon name="download" size={18} />{t('Esporta backup')}</button>
		<label class="btn block" style="cursor:pointer"><Icon name="upload" size={18} />{t('Importa backup')}<input type="file" accept="application/json,.json" hidden onchange={chooseFile} /></label>
		{#if result}<div class="banner">{result}</div>{/if}
	</div>

	{:else if cat.id === 'dati'}
	<div class="card list">
		<button class="item" onclick={() => go('impostazioni/archivio')}><div class="grow">{t('Archiviati')} ({m.exercises.filter((e) => e.isArchived).length})</div><Icon name="right" size={20} /></button>
		<button class="item" onclick={erase}><div class="grow neg">{t('Cancella tutti i dati')}</div></button>
	</div>
	{/if}
{/if}
</main>

{#if importing}
	<Sheet title={t('Importa backup')} onclose={() => (importing = null)}>
		<div class="stack">
			<button class="btn primary block" onclick={() => runImport('unisci')} disabled={busy}>{t('Unisci')}</button>
			<p class="small muted">{t('Aggiunge solo ciò che manca: i macchinari con lo stesso nome si uniscono, gli allenamenti già presenti si saltano.')}</p>
			<button class="btn danger block" onclick={() => runImport('sostituisci')} disabled={busy}>{t('Sostituisci tutto')}</button>
			<p class="small muted">{t('Cancella i dati attuali e riparte dal file, su yrb4g.com e su ogni dispositivo collegato.')}</p>
		</div>
	</Sheet>
{/if}
