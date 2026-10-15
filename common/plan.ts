// La lista degli esercizi di un allenamento (il "piano") e il suo rapporto con la scheda di partenza.
// Funzioni pure: le usa il web; l'iPhone fa gli stessi conti in Swift (Peak/Model/WorkoutTemplate.swift).

import type { TemplateItem } from './docs.ts'

export interface PlanSet { exercise: string | null; weight: number; reps: number; isWarmup: boolean }

export const blankItem = (exercise: string): TemplateItem => ({ exercise, sets: 0, repsMin: 0, repsMax: 0, weight: null })

/** Cosa si è fatto su un esercizio: serie di lavoro, ripetizioni min–max, peso più alto (kg). */
export function itemFromSets(exercise: string, sets: PlanSet[]): TemplateItem {
  const work = sets.filter((s) => s.exercise === exercise && !s.isWarmup && s.reps > 0)
  if (!work.length) return blankItem(exercise)
  const reps = work.map((s) => s.reps)
  return {
    exercise, sets: Math.min(20, work.length), repsMin: Math.min(100, Math.min(...reps)), repsMax: Math.min(100, Math.max(...reps)),
    weight: Math.min(2000, Math.max(...work.map((s) => s.weight))),
  }
}

/**
 * La lista di oggi, in ordine: le righe del piano il cui esercizio esiste ancora, poi gli esercizi con serie che nel
 * piano non ci sono. `view`: le righe fuori piano sono vuote. `targets`: prendono ciò che si è fatto (per aggiornare la
 * scheda senza toccare gli obiettivi scritti). `done`: ogni riga con serie prende ciò che si è fatto (salva come scheda).
 */
export function sessionItems(plan: TemplateItem[], sets: PlanSet[], exists: (id: string) => boolean, mode: 'view' | 'targets' | 'done' = 'view'): TemplateItem[] {
  const out: TemplateItem[] = []
  const seen = new Set<string>()
  for (const p of plan) {
    if (seen.has(p.exercise) || !exists(p.exercise)) continue
    seen.add(p.exercise)
    out.push(mode === 'done' ? withDone(p, sets) : p)
  }
  for (const s of sets) {
    if (!s.exercise || seen.has(s.exercise) || !exists(s.exercise)) continue
    seen.add(s.exercise)
    out.push(mode === 'view' ? blankItem(s.exercise) : itemFromSets(s.exercise, sets))
  }
  return out
}

const withDone = (p: TemplateItem, sets: PlanSet[]): TemplateItem => {
  const done = itemFromSets(p.exercise, sets)
  return done.sets ? done : p
}

export const addItem = (list: TemplateItem[], exercise: string): TemplateItem[] =>
  list.some((i) => i.exercise === exercise) ? list : [...list, blankItem(exercise)]

export const removeItem = (list: TemplateItem[], exercise: string): TemplateItem[] => list.filter((i) => i.exercise !== exercise)

/** Al posto di `from` mette `to`: stesse serie e ripetizioni, il peso torna "ultimo usato" (era per l'altro macchinario). */
export const replaceItem = (list: TemplateItem[], from: string, to: string): TemplateItem[] =>
  list.some((i) => i.exercise === to) ? list : list.map((i) => (i.exercise === from ? { ...i, exercise: to, weight: null } : i))

export function moveItem(list: TemplateItem[], exercise: string, dir: -1 | 1): TemplateItem[] {
  const i = list.findIndex((x) => x.exercise === exercise)
  const j = i + dir
  if (i < 0 || j < 0 || j >= list.length) return list
  const out = [...list]
  ;[out[i], out[j]] = [out[j]!, out[i]!]
  return out
}

/** La lista di oggi è diversa da quella della scheda (esercizi o ordine)? Gli obiettivi non contano: in sessione non si cambiano. */
export function differsFromTemplate(today: TemplateItem[], template: TemplateItem[], exists: (id: string) => boolean): boolean {
  const a = today.map((i) => i.exercise)
  const b = template.filter((i) => exists(i.exercise)).map((i) => i.exercise)
  return a.length !== b.length || a.some((id, k) => id !== b[k])
}
