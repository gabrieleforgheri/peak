// Lo stato dell'app: tutti i documenti dell'utente in memoria, salvati in IndexedDB e sincronizzati col server.
// Si scrive solo con put()/remove(); ogni lettura passa dal modello derivato (getModel), che ricalcola record
// e statistiche da zero a ogni modifica.
// ponytail: ricalcolo completo a ogni scrittura, va bene per migliaia di serie; incrementale oltre le ~20k.

import { SETTING_DEFAULTS, split, type DocDataMap, type Kind, type LiveDoc } from '@common/docs.ts'
import { glyphOf } from '@common/glyph.ts'
import { bestOf } from '@common/domain.ts'
import { deriveRecords, recordsByExercise } from '@common/records.ts'
import * as idb from './idb.ts'
import type { Bw, Ex, Model, SetItem, Settings, Wk } from './types.ts'

export type AuthState = 'ok' | 'expired' | 'none'

export const S = $state({
	ready: false,
	ver: 0,
	/** Almeno una pull è andata a buon fine: prima non si sa se l'account è vuoto. */
	synced: false,
	online: true,
	syncing: false,
	auth: 'ok' as AuthState,
	user: null as { sub: string; nome: string; username: string; vapid: string } | null,
	lastSync: 0,
	dirty: 0,
	error: '' as string,
})

export const rows = new Map<string, idb.Row>()
const keyOf = (kind: Kind, uuid: string) => `${kind}:${uuid}`

let writeHook: () => void = () => {}
export const onWrite = (fn: () => void) => { writeHook = fn }

let depth = 0
let touched = new Map<string, idb.Row>()

function commit() {
	if (depth > 0 || touched.size === 0) return
	const batch = [...touched.values()]
	touched = new Map()
	S.dirty = countDirty()
	S.ver++
	idb.writeRows(batch).catch((e) => { S.error = String(e) })
	writeHook()
}

export const countDirty = (): number => { let n = 0; for (const r of rows.values()) if (r.dirty) n++; return n }

/** Raggruppa più scritture in una sola modifica (un solo ricalcolo, un solo salvataggio). */
export function batch<T>(fn: () => T): T {
	depth++
	try { return fn() } finally { depth--; commit() }
}

/** Scrive un documento locale. `m` cresce sempre: una modifica batte la versione che il client ha già visto. */
export function put<K extends Kind>(kind: K, uuid: string, data: DocDataMap[K]) {
	const prev = rows.get(keyOf(kind, uuid))
	const row: idb.Row = { key: keyOf(kind, uuid), kind, uuid, data, m: Math.max(Date.now(), (prev?.m ?? 0) + 1), deleted: false, dirty: true }
	rows.set(row.key, row)
	touched.set(row.key, row)
	commit()
}

export function remove(kind: Kind, uuid: string) {
	const prev = rows.get(keyOf(kind, uuid))
	if (!prev || prev.deleted) return
	const row: idb.Row = { ...prev, data: null, m: Math.max(Date.now(), prev.m + 1), deleted: true, dirty: true }
	rows.set(row.key, row)
	touched.set(row.key, row)
	commit()
}

/** Applica ciò che arriva dal server: un documento locale modificato dopo vince, gli altri si sostituiscono. */
export function applyRemote(docs: { kind: Kind; uuid: string; data: unknown; m: number; deleted: boolean }[], force = false) {
	batch(() => {
		for (const d of docs) {
			const key = keyOf(d.kind, d.uuid)
			const local = rows.get(key)
			if (!force && local?.dirty && local.m > d.m) continue
			const row: idb.Row = { key, kind: d.kind, uuid: d.uuid, data: d.deleted ? null : d.data, m: d.m, deleted: d.deleted, dirty: false }
			rows.set(key, row)
			touched.set(key, row)
		}
	})
}

export async function load(sub: string) {
	await idb.openDb(sub)
	rows.clear()
	for (const r of await idb.allRows()) rows.set(r.key, r)
	S.dirty = countDirty()
	S.ver++
}

/** Svuota la copia locale (dopo un'operazione in blocco sul server, prima di una pull completa). */
export async function wipeLocal() {
	rows.clear()
	touched = new Map()
	await idb.clearAll()
	S.synced = false
	S.dirty = 0
	S.ver++
}

// ---- modello derivato --------------------------------------------------------------------------------------

function compute(): Model {
	const live: LiveDoc[] = []
	for (const r of rows.values()) if (!r.deleted) live.push({ kind: r.kind, uuid: r.uuid, data: r.data as DocDataMap[Kind] })
	const d = split(live)

	const exIds = new Set(d.exercises.map((e) => e.uuid))
	const wkIds = new Set(d.workouts.map((w) => w.uuid))
	const manual = d.records.filter((r) => exIds.has(r.data.exercise))
		.map((r) => ({ uuid: r.uuid, exercise: r.data.exercise, date: Date.parse(r.data.date), weight: r.data.weight, reps: r.data.reps }))
	const recSets = d.sets.filter((s) => wkIds.has(s.data.workout)).map((s) => ({
		uuid: s.uuid, exercise: s.data.exercise && exIds.has(s.data.exercise) ? s.data.exercise : null, workout: s.data.workout,
		weight: s.data.weight, reps: s.data.reps, isWarmup: s.data.isWarmup, order: s.data.order,
		createdAt: Date.parse(s.data.createdAt), noRecord: s.data.noRecord,
	}))
	const { prSets, auto } = deriveRecords(manual, recSets, wkIds)
	const byEx = recordsByExercise(manual, auto)

	const rpeOf = new Map(d.sets.map((s) => [s.uuid, s.data.rpe]))
	const items: SetItem[] = recSets.map((s) => ({
		uuid: s.uuid, workout: s.workout, exercise: s.exercise, weight: s.weight, reps: s.reps, rpe: rpeOf.get(s.uuid) ?? null,
		isWarmup: s.isWarmup, order: s.order, createdAt: s.createdAt, noRecord: s.noRecord, isPR: prSets.has(s.uuid),
	}))

	const setsByEx = new Map<string, SetItem[]>()
	const setsByWk = new Map<string, SetItem[]>()
	for (const it of items) {
		if (it.exercise) (setsByEx.get(it.exercise) ?? setsByEx.set(it.exercise, []).get(it.exercise)!).push(it)
		;(setsByWk.get(it.workout) ?? setsByWk.set(it.workout, []).get(it.workout)!).push(it)
	}

	const exercises: Ex[] = d.exercises.map((e) => {
		const records = (byEx.get(e.uuid) ?? []).sort((a, b) => b.date - a.date)
		return {
			...e.data, uuid: e.uuid, glyphName: glyphOf(e.data), records, best: bestOf(records),
			sets: (setsByEx.get(e.uuid) ?? []).sort((a, b) => b.createdAt - a.createdAt || b.order - a.order),
		}
	}).sort((a, b) => a.sortIndex - b.sortIndex || a.name.localeCompare(b.name))

	const workouts: Wk[] = d.workouts.map((w) => {
		const sets = (setsByWk.get(w.uuid) ?? []).sort((a, b) => a.order - b.order)
		const working = sets.filter((s) => !s.isWarmup)
		return {
			...w.data, uuid: w.uuid, dateMs: Date.parse(w.data.date), endedMs: w.data.endedAt ? Date.parse(w.data.endedAt) : null,
			isActive: !w.data.endedAt, sets, volume: working.reduce((v, s) => v + s.weight * s.reps, 0),
			workingSets: working.length, prCount: sets.filter((s) => s.isPR).length,
		}
	}).sort((a, b) => b.dateMs - a.dateMs)

	const bodyweights: Bw[] = d.bodyweights.map((b) => ({ uuid: b.uuid, dateMs: Date.parse(b.data.date), weight: b.data.weight, fromHealth: b.data.fromHealth }))
		.sort((a, b) => a.dateMs - b.dateMs)

	const settings: Record<string, unknown> = { ...SETTING_DEFAULTS, availablePlates: [...SETTING_DEFAULTS.availablePlates] }
	for (const s of d.settings) settings[s.uuid] = s.data.value

	return {
		exercises, exById: new Map(exercises.map((e) => [e.uuid, e])), workouts, wkById: new Map(workouts.map((w) => [w.uuid, w])),
		bodyweights, settings: settings as unknown as Settings, hasData: d.exercises.length + d.workouts.length + d.records.length + d.bodyweights.length > 0,
	}
}

const model = $derived.by(() => { S.ver; return compute() })
export const getModel = (): Model => model
