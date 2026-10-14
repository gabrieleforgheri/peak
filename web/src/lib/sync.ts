// Sincronizzazione: prima le foto in coda, poi i documenti modificati, poi la pull. Mai due giri insieme.
// Se non c'è rete o la sessione è scaduta la coda resta com'è e si riprova al prossimo segnale.

import type { Kind } from '@common/docs.ts'
import * as idb from './idb.ts'
import { S, applyRemote, countDirty, load, onWrite, rows, wipeLocal } from './store.svelte.ts'

const PUSH_BATCH = 500
let running: Promise<void> | null = null
let timer: ReturnType<typeof setTimeout> | undefined
let cursor = 0

interface Wire { kind: Kind; uuid: string; data: unknown; m: number; deleted: boolean }

class Unauthorized extends Error {}

async function api(path: string, init?: RequestInit): Promise<Response> {
	const res = await fetch(path, { credentials: 'same-origin', ...init })
	if (res.status === 401) throw new Unauthorized()
	return res
}

async function uploadPhotos() {
	for (const p of await idb.allPhotos()) {
		if (p.uploaded) continue
		const res = await api(`/api/foto/${p.sha}`, { method: 'PUT', body: p.blob, headers: { 'content-type': 'image/jpeg' } })
		if (res.ok || res.status === 409) await idb.putPhoto({ ...p, uploaded: true })
		else if (res.status >= 500) throw new Error(`foto ${res.status}`)
	}
}

/** Scarica le foto richiamate dagli esercizi e non ancora sul dispositivo. */
async function downloadPhotos() {
	const have = new Set((await idb.allPhotos()).map((p) => p.sha))
	for (const r of rows.values()) {
		const sha = (r.data as { photo?: string } | null)?.photo
		if (r.kind !== 'exercise' || r.deleted || !sha || have.has(sha)) continue
		have.add(sha)
		const res = await api(`/api/foto/${sha}`)
		if (res.ok) await idb.putPhoto({ sha, blob: await res.blob(), uploaded: true })
	}
}

async function push() {
	const dirty = [...rows.values()].filter((r) => r.dirty)
	for (let i = 0; i < dirty.length; i += PUSH_BATCH) {
		const chunk = dirty.slice(i, i + PUSH_BATCH)
		const body: Wire[] = chunk.map((r) => ({ kind: r.kind, uuid: r.uuid, data: r.data, m: r.m, deleted: r.deleted }))
		const res = await api('/api/sync', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ docs: body }) })
		if (!res.ok) throw new Error(`push ${res.status}`)
		const out = (await res.json()) as { rifiutati: Wire[]; scartati: { kind: string; uuid: string; errore: string }[] }
		const lost = new Set(out.rifiutati.map((d) => `${d.kind}:${d.uuid}`))
		const cleaned: idb.Row[] = []
		for (const sent of chunk) {
			const now = rows.get(sent.key)
			// toccato mentre il push era in volo: resta sporco e riparte al prossimo giro
			if (!now || now.m !== sent.m || lost.has(sent.key)) continue
			const clean = { ...now, dirty: false }
			rows.set(sent.key, clean)
			cleaned.push(clean)
		}
		for (const bad of out.scartati) console.warn('documento scartato dal server', bad)
		for (const bad of out.scartati) {
			const key = `${bad.kind}:${bad.uuid}`
			const row = rows.get(key)
			if (row?.dirty) { const clean = { ...row, dirty: false }; rows.set(key, clean); cleaned.push(clean) }
		}
		if (cleaned.length) await idb.writeRows(cleaned)
		// perso contro una versione più nuova: il server ci rimanda la sua
		if (out.rifiutati.length) applyRemote(out.rifiutati, true)
	}
}

async function pull() {
	const res = await api(`/api/sync?dopo=${cursor}`)
	if (!res.ok) throw new Error(`pull ${res.status}`)
	const out = (await res.json()) as { rev: number; docs: Wire[] }
	applyRemote(out.docs)
	cursor = out.rev
	await idb.setMeta('cursor', cursor)
	S.synced = true
	await idb.setMeta('synced', true)
}

export function syncNow(): Promise<void> {
	if (running) return running
	if (!S.ready || S.auth === 'none') return Promise.resolve()
	S.syncing = true
	running = (async () => {
		try {
			await uploadPhotos()
			await push()
			await pull()
			await downloadPhotos()
			S.online = true
			S.auth = 'ok'
			S.error = ''
			S.lastSync = Date.now()
		} catch (e) {
			if (e instanceof Unauthorized) S.auth = 'expired'
			else if (e instanceof TypeError) S.online = false // fetch senza rete
			else S.error = String(e)
		} finally {
			S.syncing = false
			S.dirty = countDirty()
			running = null
		}
	})()
	return running
}

/** Dopo una scrittura si aspetta un attimo, così una raffica di modifiche fa un solo giro. */
function schedule() {
	clearTimeout(timer)
	timer = setTimeout(() => void syncNow(), 1500)
}

/** Dopo un'operazione in blocco sul server: si butta la copia locale e si riparte da zero. */
export async function resync() {
	await running
	await wipeLocal()
	cursor = 0
	await idb.setMeta('cursor', 0)
	await syncNow()
}

export async function boot() {
	onWrite(schedule)
	let sub = localStorage.getItem('peak.sub')
	let info: { sub: string; nome: string; username: string } | null = null
	try {
		const res = await fetch('/api/io', { credentials: 'same-origin' })
		if (res.status === 401) {
			// niente sessione: con rete si va al login, senza si resta sulla copia locale se c'è
			S.auth = 'expired'
			if (!sub) { location.href = '/auth/login'; return }
		} else if (res.ok) info = await res.json()
	} catch { S.online = false }
	if (info) { sub = info.sub; localStorage.setItem('peak.sub', sub); S.user = info }
	if (!sub) { S.error = 'offline'; S.ready = true; return }
	await load(sub)
	cursor = (await idb.getMeta<number>('cursor')) ?? 0
	S.synced = (await idb.getMeta<boolean>('synced')) ?? false
	S.ready = true
	addEventListener('online', () => void syncNow())
	addEventListener('offline', () => { S.online = false })
	document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void syncNow() })
	setInterval(() => { if (document.visibilityState === 'visible') void syncNow() }, 60_000)
	void syncNow()
}

/** Fetch con la stessa gestione della sessione, per le chiamate in blocco (import, catalogo, backup). */
export const call = api
