// Le operazioni dell'utente sui documenti. Niente logica di record qui: i massimali automatici e i PR si
// derivano dalle serie (common/records.ts), quindi cancellare o modificare una serie basta a tenerli giusti.

import { isoSec, newUuid, MUSCLE_GROUPS, type DocDataMap, type ExerciseDoc, type Kind, type MuscleGroup, type SetDoc, type WorkoutDoc } from '@common/docs.ts'
import { CATALOG } from '@common/catalog.ts'
import { settingsForUnitChange, type Unit } from '@common/domain.ts'
import type { AnyRecord } from '@common/records.ts'
import { batch, getModel, put, remove, rows } from './store.svelte.ts'

const docOf = <K extends Kind>(kind: K, uuid: string): DocDataMap[K] | undefined => {
	const r = rows.get(`${kind}:${uuid}`)
	return r && !r.deleted ? (r.data as DocDataMap[K]) : undefined
}

const now = () => isoSec(Date.now())

// ---- allenamenti ---------------------------------------------------------------------------------------------

export const focusRaw = (groups: Iterable<MuscleGroup>): string => {
	const set = new Set(groups)
	return MUSCLE_GROUPS.filter((g) => set.has(g)).join(',')
}

export function startWorkout(name: string, focus: Iterable<MuscleGroup>): string {
	const id = newUuid()
	put('workout', id, { date: now(), endedAt: null, note: '', name: name.trim(), focus: focusRaw(focus) })
	return id
}

export function patchWorkout(id: string, patch: Partial<WorkoutDoc>) {
	const d = docOf('workout', id)
	if (d) put('workout', id, { ...d, ...patch })
}

export const endWorkout = (id: string) => patchWorkout(id, { endedAt: now() })

/** Elimina l'allenamento e le sue serie; i record automatici spariscono da soli (si ricalcolano). */
export function deleteWorkout(id: string) {
	batch(() => {
		for (const s of getModel().wkById.get(id)?.sets ?? []) remove('set', s.uuid)
		remove('workout', id)
	})
}

export function addSet(workout: string, exercise: string, weight: number, reps: number, isWarmup: boolean): { uuid: string; isPR: boolean } {
	const wk = getModel().wkById.get(workout)
	const order = (wk?.sets.reduce((m, s) => Math.max(m, s.order), -1) ?? -1) + 1
	const uuid = newUuid()
	put('set', uuid, { workout, exercise, weight, reps, rpe: null, isWarmup, order, createdAt: now(), noRecord: false })
	return { uuid, isPR: getModel().wkById.get(workout)?.sets.find((s) => s.uuid === uuid)?.isPR ?? false }
}

export function patchSet(id: string, patch: Partial<SetDoc>): boolean {
	const d = docOf('set', id)
	if (!d) return false
	put('set', id, { ...d, ...patch })
	for (const w of getModel().workouts) { const s = w.sets.find((x) => x.uuid === id); if (s) return s.isPR }
	return false
}

export const deleteSet = (id: string) => remove('set', id)

// ---- macchinari --------------------------------------------------------------------------------------------------

export function saveExercise(id: string | null, d: Omit<ExerciseDoc, 'createdAt' | 'sortIndex'> & Partial<Pick<ExerciseDoc, 'createdAt' | 'sortIndex'>>): string {
	const uuid = id ?? newUuid()
	const prev = docOf('exercise', uuid)
	const top = getModel().exercises.reduce((m, e) => Math.max(m, e.sortIndex), -1)
	put('exercise', uuid, { ...d, createdAt: d.createdAt ?? prev?.createdAt ?? now(), sortIndex: d.sortIndex ?? prev?.sortIndex ?? top + 1 })
	return uuid
}

export function patchExercise(id: string, patch: Partial<ExerciseDoc>) {
	const d = docOf('exercise', id)
	if (d) put('exercise', id, { ...d, ...patch })
}

/** Elimina il macchinario e i suoi massimali; le serie restano negli allenamenti ("Esercizio rimosso"). */
export function deleteExercise(id: string) {
	batch(() => {
		for (const r of rows.values()) if (r.kind === 'record' && !r.deleted && (r.data as { exercise: string }).exercise === id) remove('record', r.uuid)
		remove('exercise', id)
	})
}

export function addRecord(exercise: string, weight: number, reps: number, dateMs: number, note: string) {
	put('record', newUuid(), { exercise, date: isoSec(dateMs), weight, reps, note })
}

/** Un massimale manuale si elimina; uno automatico torna "non conta" sulla serie che l'ha fatto nascere. */
export function deleteRecord(r: AnyRecord) {
	if (r.isAuto) patchSet(r.uuid, { noRecord: true })
	else remove('record', r.uuid)
}

/** Aggiunge le voci del catalogo che mancano (nome uguale, senza maiuscole). */
export function seedMissing(): number {
	const have = new Set(getModel().exercises.map((e) => e.name.trim().toLowerCase()))
	const missing = CATALOG.filter((c) => !have.has(c.name.toLowerCase()))
	const top = getModel().exercises.reduce((m, e) => Math.max(m, e.sortIndex), -1)
	batch(() => missing.forEach((c, i) => put('exercise', newUuid(), {
		name: c.name, muscleGroup: c.group, notes: c.notes, incrementStep: c.step, sortIndex: top + 1 + i, isArchived: false,
		createdAt: now(), glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 0,
	})))
	return missing.length
}

// ---- peso corporeo -------------------------------------------------------------------------------------------------

export const addBodyWeight = (kg: number, dateMs: number) => put('bodyweight', newUuid(), { date: isoSec(dateMs), weight: kg, fromHealth: false })
export function patchBodyWeight(id: string, kg: number, dateMs: number) {
	const d = docOf('bodyweight', id)
	if (d) put('bodyweight', id, { ...d, weight: kg, date: isoSec(dateMs) })
}
export const deleteBodyWeight = (id: string) => remove('bodyweight', id)

// ---- impostazioni e dati -------------------------------------------------------------------------------------------

export const setSetting = (key: string, value: unknown) => put('setting', key, { value })

/** Cambiando unità, dischi e bilanciere di serie seguono quelli dell'unità nuova. */
export function changeUnit(to: Unit) {
	const s = getModel().settings
	if (s.unit === to) return
	const next = settingsForUnitChange(s.unit, to, s.availablePlates, s.barWeight)
	batch(() => {
		setSetting('unit', to)
		setSetting('availablePlates', next.availablePlates)
		setSetting('barWeight', next.barWeight)
	})
}

/** Cancella tutto, pesate comprese. Le impostazioni restano. Su server e su ogni dispositivo collegato. */
export function eraseAll() {
	batch(() => { for (const r of [...rows.values()]) if (r.kind !== 'setting' && !r.deleted) remove(r.kind, r.uuid) })
}
