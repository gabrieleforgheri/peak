// Le operazioni dell'utente sui documenti. Niente logica di record qui: i massimali automatici e i PR si
// derivano dalle serie (common/records.ts), quindi cancellare o modificare una serie basta a tenerli giusti.

import { isoSec, newUuid, MUSCLE_GROUPS, type DocDataMap, type ExerciseDoc, type Kind, type MuscleGroup, type SetDoc, type TemplateItem, type WorkoutDoc } from '@common/docs.ts'
import { settingsForUnitChange, type Unit } from '@common/domain.ts'
import { addItem, differsFromTemplate, moveItem, removeItem, replaceItem, sessionItems, type PlanSet } from '@common/plan.ts'
import type { AnyRecord } from '@common/records.ts'
import { batch, getModel, put, remove, rows } from './store.svelte.ts'
import type { Tpl, Wk } from './types.ts'

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

/** Con una scheda: lista già pronta (una copia), nome della scheda se non ne dai uno, gruppi presi dagli esercizi. */
export function startWorkout(name: string, focus: Iterable<MuscleGroup>, template?: Tpl): string {
	const id = newUuid()
	const exById = getModel().exById
	const plan = (template?.items ?? []).filter((i) => exById.has(i.exercise))
	const groups = template ? plan.map((i) => exById.get(i.exercise)!.muscleGroup) : focus
	put('workout', id, {
		date: now(), endedAt: null, note: '', name: name.trim() || template?.name || '', focus: focusRaw(groups),
		template: template?.uuid ?? '', plan,
	})
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

// ---- schede e lista dell'allenamento ------------------------------------------------------------------------

const planSets = (wk: Wk): PlanSet[] => wk.sets
const exists = (id: string) => getModel().exById.has(id)

/** La lista di oggi, in ordine, con gli obiettivi della scheda dove ci sono. */
export const todayItems = (wk: Wk): TemplateItem[] => sessionItems(wk.plan, planSets(wk), exists)

/** La lista di oggi scritta nel piano (anche gli esercizi fuori piano diventano righe). */
export function setPlan(workout: string, items: TemplateItem[]) { patchWorkout(workout, { plan: items }) }

export const addToPlan = (wk: Wk, exercise: string) => setPlan(wk.uuid, addItem(todayItems(wk), exercise))
export const removeFromPlan = (wk: Wk, exercise: string) => setPlan(wk.uuid, removeItem(todayItems(wk), exercise))
export const replaceInPlan = (wk: Wk, from: string, to: string) => setPlan(wk.uuid, replaceItem(todayItems(wk), from, to))
export const moveInPlan = (wk: Wk, exercise: string, dir: -1 | 1) => setPlan(wk.uuid, moveItem(todayItems(wk), exercise, dir))

/** Righe pronte per "salva come scheda": quello che si è fatto, dove si è fatto qualcosa. */
export const itemsFromWorkout = (wk: Wk): TemplateItem[] => sessionItems(wk.plan, planSets(wk), exists, 'done')

/** A fine allenamento: la lista è diversa dalla scheda da cui è partito? (la scheda può essere stata eliminata) */
export function templateChanged(wk: Wk): Tpl | null {
	const tpl = getModel().tplById.get(wk.template)
	return tpl && differsFromTemplate(sessionItems(wk.plan, planSets(wk), exists, 'targets'), tpl.items, exists) ? tpl : null
}

/** Aggiorna la scheda con la lista di oggi, tenendo gli obiettivi scritti. */
export function updateTemplateFromWorkout(tpl: Tpl, wk: Wk) {
	saveTemplate(tpl.uuid, tpl.name, sessionItems(wk.plan, planSets(wk), exists, 'targets'))
}

export function saveTemplate(id: string | null, name: string, items: TemplateItem[]): string {
	const uuid = id ?? newUuid()
	const prev = getModel().tplById.get(uuid)
	const top = getModel().templates.reduce((m, x) => Math.max(m, x.sortIndex), -1)
	put('template', uuid, { name: name.trim(), items, sortIndex: prev?.sortIndex ?? top + 1, createdAt: prev?.createdAt ?? now(), hidden: prev?.hidden ?? false })
	return uuid
}

export const deleteTemplate = (id: string) => remove('template', id)

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
