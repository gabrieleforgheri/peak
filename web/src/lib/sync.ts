// Sincronizzazione: prima le foto in coda, poi i documenti modificati, poi la pull. Mai due giri insieme.
// Se non c'è rete o la sessione è scaduta la coda resta com'è e si riprova al prossimo segnale.

import type { Kind } from '@common/docs.ts'
import * as idb from './idb.ts'
import { S, applyRemote, countDirty, getModel, load, onWrite, put, rows, wipeLocal } from './store.svelte.ts'
import { loadCatalog } from './catalog.ts'
import { lang } from './i18n.ts'

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
	if (import.meta.env.DEV && S.user?.sub === 'dev') {
		S.online = true
		S.auth = 'ok'
		S.error = ''
		return Promise.resolve()
	}
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

async function seedDevData() {
	const cat = await loadCatalog()
	const isIt = lang === 'it'
	const legEx = cat.find((e) => e.id === 'leg-extension')
	const latEx = cat.find((e) => e.id === 'lat-pulldown')
	const benchEx = cat.find((e) => e.id === 'bench-press-barbell')
	const smithEx = cat.find((e) => e.id === 'squat-smith')

	const ex1Id = 'A0000000-0000-0000-0000-000000000001'
	const ex2Id = 'A0000000-0000-0000-0000-000000000002'
	const ex3Id = 'A0000000-0000-0000-0000-000000000003'
	const ex4Id = 'A0000000-0000-0000-0000-000000000004'

	put('exercise', ex1Id, {
		name: isIt ? 'Leg Extension' : 'Leg Extension',
		muscleGroup: legEx?.group ?? 'gambe',
		notes: isIt ? 'Punta i piedi leggermente verso l\'esterno.' : 'Point your toes slightly outward.',
		incrementStep: 5, sortIndex: 0, isArchived: false, createdAt: new Date().toISOString(),
		glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 90,
		catalog: legEx?.id ?? 'leg-extension', muscles: legEx?.muscles ?? ['quadriceps'], view: legEx?.view ?? 'front', hidden: false, custom: false,
		barWeight: 0,
		weightDisplay: 'absolute',
	})

	if (latEx) {
		put('exercise', ex2Id, {
			name: isIt ? latEx.it : latEx.en,
			muscleGroup: latEx.group,
			notes: isIt ? 'Tira la sbarra verso il petto alto mantenendo i gomiti verso il basso.' : 'Pull the bar to your upper chest keeping your elbows down.',
			incrementStep: 5, sortIndex: 1, isArchived: false, createdAt: new Date().toISOString(),
			glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 120,
			catalog: latEx.id, muscles: latEx.muscles, view: latEx.view, hidden: false, custom: false,
			barWeight: 0,
			weightDisplay: 'absolute',
		})
	}

	if (benchEx) {
		put('exercise', ex3Id, {
			name: isIt ? benchEx.it : benchEx.en,
			muscleGroup: benchEx.group,
			notes: isIt ? 'Mantieni le scapole addotte e i piedi stabili a terra.' : 'Keep shoulder blades retracted and feet flat on the floor.',
			incrementStep: 2.5, sortIndex: 2, isArchived: false, createdAt: new Date().toISOString(),
			glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 120,
			catalog: benchEx.id, muscles: benchEx.muscles, view: benchEx.view, hidden: false, custom: false,
			barWeight: 20,
			weightDisplay: 'absolute',
		})
	}

	if (smithEx) {
		put('exercise', ex4Id, {
			name: isIt ? smithEx.it : smithEx.en,
			muscleGroup: smithEx.group,
			notes: isIt ? 'Piedi leggermente avanti rispetto alla sbarra.' : 'Feet slightly ahead of the bar.',
			incrementStep: 2.5, sortIndex: 3, isArchived: false, createdAt: new Date().toISOString(),
			glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 120,
			catalog: smithEx.id, muscles: smithEx.muscles, view: smithEx.view, hidden: false, custom: false,
			barWeight: 15,
			weightDisplay: 'absolute',
		})
	}

	// Allenamento scorso (con 3 serie di leg extension e 3 serie di panca piana)
	const prevWkId = 'B0000000-0000-0000-0000-000000000001'
	put('workout', prevWkId, {
		date: new Date(Date.now() - 3 * 86400000).toISOString(),
		endedAt: new Date(Date.now() - 3 * 86400000 + 3600000).toISOString(),
		name: isIt ? 'Gambe & Petto' : 'Legs & Chest',
		focus: 'gambe,petto',
		note: isIt ? 'Ottime sensazioni, carichi stabili.' : 'Great feeling, steady weights.',
		template: '',
		plan: [
			{ exercise: ex1Id, sets: 3, repsMin: 8, repsMax: 10, weight: 60 },
			...(benchEx ? [{ exercise: ex3Id, sets: 3, repsMin: 8, repsMax: 10, weight: 70 }] : []),
		],
	})
	put('set', 'C0000000-0000-0000-0000-000000000001', { workout: prevWkId, exercise: ex1Id, weight: 50, reps: 12, rpe: null, isWarmup: false, order: 0, createdAt: new Date(Date.now() - 3 * 86400000).toISOString(), noRecord: false })
	put('set', 'C0000000-0000-0000-0000-000000000002', { workout: prevWkId, exercise: ex1Id, weight: 55, reps: 10, rpe: null, isWarmup: false, order: 1, createdAt: new Date(Date.now() - 3 * 86400000).toISOString(), noRecord: false })
	put('set', 'C0000000-0000-0000-0000-000000000003', { workout: prevWkId, exercise: ex1Id, weight: 60, reps: 8, rpe: null, isWarmup: false, order: 2, createdAt: new Date(Date.now() - 3 * 86400000).toISOString(), noRecord: false })
	if (benchEx) {
		put('set', 'C0000000-0000-0000-0000-000000000010', { workout: prevWkId, exercise: ex3Id, weight: 60, reps: 10, rpe: null, isWarmup: false, order: 3, createdAt: new Date(Date.now() - 3 * 86400000).toISOString(), noRecord: false })
		put('set', 'C0000000-0000-0000-0000-000000000011', { workout: prevWkId, exercise: ex3Id, weight: 65, reps: 8, rpe: null, isWarmup: false, order: 4, createdAt: new Date(Date.now() - 3 * 86400000).toISOString(), noRecord: false })
		put('set', 'C0000000-0000-0000-0000-000000000012', { workout: prevWkId, exercise: ex3Id, weight: 70, reps: 6, rpe: null, isWarmup: false, order: 5, createdAt: new Date(Date.now() - 3 * 86400000).toISOString(), noRecord: false })
	}

	// Allenamento in corso di oggi
	const todayWkId = 'B0000000-0000-0000-0000-000000000002'
	put('workout', todayWkId, {
		date: new Date().toISOString(),
		endedAt: null,
		name: isIt ? 'Allenamento di oggi' : 'Today\'s Workout',
		focus: 'gambe,petto',
		note: '',
		template: '',
		plan: [
			{ exercise: ex1Id, sets: 4, repsMin: 8, repsMax: 10, weight: 60 },
			...(benchEx ? [{ exercise: ex3Id, sets: 3, repsMin: 8, repsMax: 10, weight: 70 }] : []),
			...(smithEx ? [{ exercise: ex4Id, sets: 3, repsMin: 8, repsMax: 10, weight: 55 }] : []),
			...(latEx ? [{ exercise: ex2Id, sets: 3, repsMin: 8, repsMax: 12, weight: 55 }] : []),
		],
	})
	put('set', 'C0000000-0000-0000-0000-000000000004', { workout: todayWkId, exercise: ex1Id, weight: 55, reps: 10, rpe: null, isWarmup: false, order: 0, createdAt: new Date().toISOString(), noRecord: false })
	if (benchEx) {
		put('set', 'C0000000-0000-0000-0000-000000000013', { workout: todayWkId, exercise: ex3Id, weight: 65, reps: 8, rpe: null, isWarmup: false, order: 1, createdAt: new Date().toISOString(), noRecord: false })
	}
}

export async function boot() {
	onWrite(schedule)
	let sub = localStorage.getItem('peak.sub')
	let info: { sub: string; nome: string; username: string; vapid: string } | null = null
	try {
		const res = await fetch('/api/io', { credentials: 'same-origin' })
		if (res.status === 401) {
			// niente sessione: con rete si va al login, senza si resta sulla copia locale se c'è
			S.auth = 'expired'
			if (!sub && !import.meta.env.DEV) { location.href = '/auth/login'; return }
		} else if (res.ok) info = await res.json()
	} catch { S.online = false }
	if (info) { sub = info.sub; localStorage.setItem('peak.sub', sub); S.user = info }
	if (!sub && import.meta.env.DEV) {
		sub = 'dev'
		localStorage.setItem('peak.sub', sub)
		info = { sub: 'dev', nome: 'Gabriele', username: 'gabriele', vapid: '' }
		S.user = info
	}
	if (!sub) { S.error = 'offline'; S.ready = true; return }
	await load(sub)
	cursor = (await idb.getMeta<number>('cursor')) ?? 0
	S.synced = (await idb.getMeta<boolean>('synced')) ?? (import.meta.env.DEV ? true : false)
	S.ready = true

	const devSeedLang = localStorage.getItem('peak.dev-seed-lang')
	const devSeedVer = localStorage.getItem('peak.dev-seed-ver')
	if (import.meta.env.DEV && (getModel().exercises.length === 0 || devSeedLang !== lang || devSeedVer !== '3')) {
		await seedDevData()
		localStorage.setItem('peak.dev-seed-lang', lang)
		localStorage.setItem('peak.dev-seed-ver', '3')
	}

	addEventListener('online', () => void syncNow())
	addEventListener('offline', () => { S.online = false })
	document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') void syncNow() })
	setInterval(() => { if (document.visibilityState === 'visible') void syncNow() }, 60_000)
	void syncNow()
}

/** Fetch con la stessa gestione della sessione, per le chiamate in blocco (import, catalogo, backup). */
export const call = api
