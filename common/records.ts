// Massimali derivati dalle serie. I record automatici ("da allenamento") e il flag PR delle serie
// non si sincronizzano mai: si ricalcolano da qui, uguale su web, server e iPhone (RecordRules.swift).
// Vettori di prova condivisi con Swift: common/fixtures/records.json.

import { beats, bestOf } from './domain.ts'
import { floorSec } from './docs.ts'

export interface RecSet {
  uuid: string; exercise: string | null; workout: string; weight: number; reps: number
  isWarmup: boolean; order: number; createdAt: number; noRecord: boolean; singleArm?: boolean
}
export interface RecManual { uuid: string; exercise: string; date: number; weight: number; reps: number; singleArm?: boolean }
export interface AutoRecord { uuid: string; exercise: string; date: number; weight: number; reps: number; singleArm: boolean }

interface Ev { t: number; manual: boolean; order: number; uuid: string; weight: number; reps: number; date: number }

/**
 * Per ogni esercizio, e separatamente per le serie a un braccio (peso sul singolo manico, non confrontabile con
 * quello a due braccia): si percorrono in ordine di tempo (a secondi interi) i massimali manuali e le serie
 * valide (non riscaldamento, non escluse, allenamento esistente). Un manuale che batte il migliore lo sostituisce;
 * una serie che lo batte è un PR e genera un record automatico con l'uuid della serie.
 */
export function deriveRecords(
  manual: RecManual[], sets: RecSet[], liveWorkouts: ReadonlySet<string>,
): { prSets: Set<string>; auto: AutoRecord[] } {
  const lines = new Map<string, { exercise: string; singleArm: boolean; events: Ev[] }>()
  const push = (ex: string, singleArm: boolean, ev: Ev) => {
    const key = `${ex}${singleArm ? '|1' : ''}`
    ;(lines.get(key) ?? lines.set(key, { exercise: ex, singleArm, events: [] }).get(key)!).events.push(ev)
  }
  for (const m of manual) push(m.exercise, m.singleArm === true, { t: floorSec(m.date), manual: true, order: 0, uuid: m.uuid, weight: m.weight, reps: m.reps, date: m.date })
  for (const s of sets) {
    if (!s.exercise || s.isWarmup || s.noRecord || !(s.weight > 0) || s.reps < 1 || !liveWorkouts.has(s.workout)) continue
    push(s.exercise, s.singleArm === true, { t: floorSec(s.createdAt), manual: false, order: s.order, uuid: s.uuid, weight: s.weight, reps: s.reps, date: s.createdAt })
  }
  const prSets = new Set<string>()
  const auto: AutoRecord[] = []
  for (const { exercise, singleArm, events } of lines.values()) {
    events.sort((a, b) => a.t - b.t || Number(!a.manual) - Number(!b.manual) || a.order - b.order || (a.uuid < b.uuid ? -1 : a.uuid > b.uuid ? 1 : 0))
    let best: { weight: number; reps: number } | null = null
    for (const e of events) {
      if (!beats(e.weight, e.reps, best)) continue
      best = { weight: e.weight, reps: e.reps }
      if (!e.manual) { prSets.add(e.uuid); auto.push({ uuid: e.uuid, exercise, date: e.date, weight: e.weight, reps: e.reps, singleArm }) }
    }
  }
  return { prSets, auto }
}

export interface AnyRecord { uuid: string; exercise: string; date: number; weight: number; reps: number; isAuto: boolean; singleArm: boolean }

/** Manuali + automatici, raggruppati per esercizio. Di base solo la linea a due braccia, quella che mostra "il" massimale; `singleArm` dà l'altra. */
export function recordsByExercise(manual: RecManual[], auto: AutoRecord[], singleArm = false): Map<string, AnyRecord[]> {
  const out = new Map<string, AnyRecord[]>()
  const add = (r: AnyRecord) => { if (r.singleArm === singleArm) (out.get(r.exercise) ?? out.set(r.exercise, []).get(r.exercise)!).push(r) }
  for (const m of manual) add({ ...m, isAuto: false, singleArm: m.singleArm === true })
  for (const a of auto) add({ ...a, isAuto: true })
  return out
}

export const currentBest = (records: AnyRecord[] | undefined): AnyRecord | null => bestOf(records ?? [])
