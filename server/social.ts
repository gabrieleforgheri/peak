// Quello che un amico può vedere di te: massimali, schede e classifica. Tutto si ricava dai documenti, al momento:
// niente tabelle di eventi. Gli esercizi e le schede nascosti, archiviati o cancellati non escono mai da qui.
// ponytail: si rilegge tutto l'archivio dell'utente a ogni richiesta; una cache per utente se gli utenti diventano centinaia.

import { limbsOf, lineArm, oneRepMax } from '../common/domain.ts'
import { split, type LiveDoc } from '../common/docs.ts'
import { setVolume, weeklyStreak } from '../common/stats.ts'

/** Il migliore di un esercizio: l'1RM stimato più alto fra le serie vere e i massimali scritti a mano. */
export interface Best { oneRm: number; weight: number; reps: number; date: number }
export interface SharedMax extends Best { catalog: string; name: string }
export interface SharedRow { catalog: string; name: string; sets: number; repsMin: number; repsMax: number }
export interface SharedTemplate { uuid: string; name: string; rows: SharedRow[] }
export interface Shared { maxes: SharedMax[]; templates: SharedTemplate[] }

const better = (a: Best | undefined, b: Best): Best => (!a || b.oneRm > a.oneRm ? b : a)

/** Per ogni esercizio vivo, il suo migliore. Le serie contano solo se valide (non riscaldamento, non escluse, allenamento esistente). */
function bestPerExercise(docs: LiveDoc[]): { d: ReturnType<typeof split>; best: Map<string, Best> } {
  const d = split(docs)
  const liveWk = new Set(d.workouts.map((w) => w.uuid))
  const best = new Map<string, Best>()
  const add = (exercise: string | null, weight: number, reps: number, date: number) => {
    const oneRm = oneRepMax(weight, reps)
    if (exercise && oneRm > 0) best.set(exercise, better(best.get(exercise), { oneRm, weight, reps, date }))
  }
  // la linea «a un braccio» resta fuori; conta solo dove il macchinario si fa nei due modi (vedi lineArm)
  const limbs = new Map(d.exercises.map((e) => [e.uuid, limbsOf(e.data)]))
  const oneLine = (exercise: string | null, singleArm: boolean) => lineArm(limbs.get(exercise ?? '') ?? 'both', singleArm)
  for (const r of d.records) if (!oneLine(r.data.exercise, r.data.singleArm)) add(r.data.exercise, r.data.weight, r.data.reps, Date.parse(r.data.date))
  for (const s of d.sets) {
    const x = s.data
    if (x.isWarmup || x.noRecord || oneLine(x.exercise, x.singleArm) || !liveWk.has(x.workout)) continue
    add(x.exercise, x.weight, x.reps, Date.parse(x.createdAt))
  }
  return { d, best }
}

/** Gli esercizi che si possono mostrare: non archiviati, non nascosti. */
const shown = (e: LiveDoc<'exercise'>) => !e.data.isArchived && !e.data.hidden

/** Il migliore per voce del catalogo (fra più esercizi con lo stesso id vince il più alto). Solo esercizi collegati e visibili. */
export function bestByCatalog(docs: LiveDoc[]): Map<string, SharedMax> {
  const { d, best } = bestPerExercise(docs)
  const out = new Map<string, SharedMax>()
  for (const e of d.exercises) {
    const b = best.get(e.uuid)
    if (!b || !e.data.catalog || !shown(e)) continue
    const prev = out.get(e.data.catalog)
    if (!prev || b.oneRm > prev.oneRm) out.set(e.data.catalog, { ...b, catalog: e.data.catalog, name: e.data.name })
  }
  return out
}

/** Il profilo che vede un amico: massimali (anche degli esercizi personalizzati) e schede visibili, senza i pesi. */
export function sharedView(docs: LiveDoc[]): Shared {
  const { d, best } = bestPerExercise(docs)
  const visible = new Map(d.exercises.filter(shown).map((e) => [e.uuid, e]))
  const maxes: SharedMax[] = []
  for (const [uuid, e] of visible) {
    const b = best.get(uuid)
    if (b) maxes.push({ ...b, catalog: e.data.catalog, name: e.data.name })
  }
  maxes.sort((a, b) => b.date - a.date)
  const templates: SharedTemplate[] = d.templates
    .filter((t) => !t.data.hidden)
    .sort((a, b) => a.data.sortIndex - b.data.sortIndex)
    .map((t) => ({
      uuid: t.uuid, name: t.data.name,
      rows: t.data.items.flatMap((i) => {
        const e = visible.get(i.exercise)
        return e ? [{ catalog: e.data.catalog, name: e.data.name, sets: i.sets, repsMin: i.repsMin, repsMax: i.repsMax }] : []
      }),
    }))
  return { maxes, templates }
}

/** Come ti stai allenando: serve alla lega fra amici. Solo allenamenti conclusi, riscaldamento escluso. */
export interface Activity { volume30: number; sessions30: number; streak: number; last: number | null }

export function activityOf(docs: LiveDoc[], now = Date.now()): Activity {
  const d = split(docs)
  const volumeOf = new Map<string, number>()
  // le serie di esercizi nascosti o archiviati non escono: il volume non deve tradirli
  const visible = new Set(d.exercises.filter(shown).map((e) => e.uuid))
  for (const s of d.sets) if (!s.data.isWarmup && visible.has(s.data.exercise ?? "")) volumeOf.set(s.data.workout, (volumeOf.get(s.data.workout) ?? 0) + setVolume(s.data))
  const done = d.workouts.filter((w) => w.data.endedAt).map((w) => ({ uuid: w.uuid, date: Date.parse(w.data.date) }))
  const recent = done.filter((w) => w.date >= now - 30 * 86_400_000)
  return {
    volume30: recent.reduce((v, w) => v + (volumeOf.get(w.uuid) ?? 0), 0),
    sessions30: recent.length,
    streak: weeklyStreak(done, now),
    last: done.reduce<number | null>((a, w) => (a === null || w.date > a ? w.date : a), null),
  }
}
