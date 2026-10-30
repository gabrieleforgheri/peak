// Logica pura portata da Massimali/Model/*.swift. Sorgente di verità per web e server;
// i casi di prova stanno in domain.test.ts (porting di MassimaliTests/).
// Tutti i pesi qui sono numeri nell'unità che passa il chiamante: lo stoccaggio resta in kg,
// la conversione in lb si fa ai bordi (vedi sotto).

export type Unit = 'kg' | 'lb'

// ---- 1RM stimato -----------------------------------------------------------------------------

/** Epley. Solo una stima: non decide mai un record (vedi `beats`), ma ordina le classifiche. */
export function oneRepMax(weight: number, reps: number): number {
  if (!(weight > 0) || reps < 1) return 0
  return reps === 1 ? weight : weight * (1 + reps / 30)
}

// ---- confronto dei massimali -------------------------------------------------------------------

export const EPSILON = 0.01

/** Prima il peso, poi le ripetizioni. Senza termine di paragone vince qualsiasi serie valida. */
export function beats(weight: number, reps: number, other: { weight: number; reps: number } | null): boolean {
  if (!(weight > 0) || reps < 1) return false
  if (!other) return true
  if (weight > other.weight + EPSILON) return true
  if (weight < other.weight - EPSILON) return false
  return reps > other.reps
}

/** Il massimo vero: più carico, poi più ripetizioni, a parità esatta il più recente (`Exercise.bestRecord`). */
export function bestOf<T extends { weight: number; reps: number; date: number }>(records: T[]): T | null {
  let best: T | null = null
  for (const r of records) {
    if (!best) { best = r; continue }
    if (beats(r.weight, r.reps, best)) { best = r; continue }
    const same = Math.abs(r.weight - best.weight) < EPSILON && r.reps === best.reps
    if (same && r.date > best.date) best = r
  }
  return best
}

// ---- riscaldamento -----------------------------------------------------------------------------

export const WARMUP_RAMP: readonly (readonly [number, number])[] = [[0.4, 10], [0.6, 6], [0.8, 3]]

const roundHalfAway = (x: number): number => (x < 0 ? -Math.round(-x) : Math.round(x))

/** Il peso della griglia `anchor − n·step` più vicino a `weight`, mai ≤ 0. */
export function nearest(weight: number, anchor: number, step: number): number {
  if (!(step > 0)) return Math.max(0, roundHalfAway(weight * 2) / 2)
  const n = roundHalfAway((anchor - weight) / step)
  let candidate = anchor - n * step
  if (candidate <= 0.0001) candidate = anchor - Math.floor((anchor - 0.0001) / step) * step
  return roundHalfAway(candidate * 100) / 100
}

// ---- un braccio / due bracci ---------------------------------------------------------------------

/** Il peso della griglia `bar + k·step` più vicino a `weight`; a pari distanza il più basso. Mai sotto `bar`
 *  (né sotto un passo se `bar` è 0 e `weight` > 0). Pesi nell'unità mostrata: in lb il passo è quello nativo (5 lb). */
export function snapToGrid(weight: number, bar: number, step: number): number {
  if (!(weight > 0)) return weight
  if (!(step > 0)) return round2(Math.max(weight, bar))
  const k = (weight - bar) / step
  let n = Math.floor(k + 1e-6)
  if (k - n > 0.5 + 1e-6) n += 1
  return round2(bar + Math.max(n, bar > 0 ? 0 : 1) * step)
}

/** Passando da due bracci a uno: ricordo il peso di prima (`before`) e quello dopo la metà (`after`). */
export interface ArmMemo { before: number; after: number }

/** Secondi di recupero dopo una serie: dopo il riscaldamento una percentuale di quello normale (almeno 5 s, 0 % = nessun timer). 0 = niente timer. */
export function restAfter(baseSeconds: number, warmup: boolean, warmupPercent: number): number {
  if (!warmup) return baseSeconds
  return warmupPercent > 0 ? Math.max(5, Math.round(baseSeconds * warmupPercent / 100)) : 0
}

/** Il peso dopo un tocco sul selettore «2 | 1». A uno: metà, sulla griglia. Di nuovo a due: se il peso è ancora
 *  quello dimezzato torna esattamente il precedente, altrimenti il doppio sulla griglia. */
export function switchArm(
  toSingle: boolean, weight: number, memo: ArmMemo | null, bar: number, step: number,
): { weight: number; memo: ArmMemo | null } {
  if (toSingle) {
    const after = snapToGrid(weight / 2, bar, step)
    return { weight: after, memo: weight > 0 ? { before: weight, after } : null }
  }
  if (memo && Math.abs(weight - memo.after) < 0.005) return { weight: memo.before, memo: null }
  return { weight: snapToGrid(weight * 2, bar, step), memo: null }
}

export interface WarmupStep { percent: number; reps: number; weight: number }

export function warmupPlan(base: number | null | undefined, step: number): WarmupStep[] {
  if (!base || !(base > 0)) return []
  // con basi piccole e passi grandi i pesi si ripetono o arrivano al carico vero: quelli non servono come riscaldamento
  const out: WarmupStep[] = []
  for (const [percent, reps] of WARMUP_RAMP) {
    const weight = nearest(base * percent, base, step)
    if (weight < base && !out.some((s) => s.weight === weight)) out.push({ percent, reps, weight })
  }
  return out
}

// ---- doppia progressione -----------------------------------------------------------------------------

export interface Suggestion { weight: number; reps: number }

export function nextLoad(
  lastSession: { weight: number; reps: number }[], step: number, range: [number, number],
): Suggestion | null {
  const top = Math.max(0, ...lastSession.map((s) => s.weight))
  if (!(top > 0)) return null
  const topReps = lastSession.filter((s) => Math.abs(s.weight - top) < EPSILON).map((s) => s.reps)
  if (topReps.every((r) => r >= range[1])) return { weight: top + step, reps: range[0] }
  const reps = Math.min(Math.max(Math.max(...topReps) + 1, range[0]), range[1])
  return { weight: top, reps }
}

export interface ProgressionSet {
  weight: number; reps: number; isWarmup: boolean; workout: string; workoutDate: number; workoutEnded: boolean
}

/** Sull'ultima sessione **conclusa** con serie allenanti su questo esercizio (`Exercise.progression`). */
export function progressionFor(sets: ProgressionSet[], step: number, range: [number, number]): Suggestion | null {
  const working = sets.filter((s) => !s.isWarmup && s.workoutEnded)
  if (!working.length) return null
  let last = working[0]
  for (const s of working) if (s.workoutDate > last.workoutDate) last = s
  return nextLoad(working.filter((s) => s.workout === last.workout), step, range)
}

// ---- dischi --------------------------------------------------------------------------------------

export interface PlateLoad { perSide: number[]; leftover: number }

/** Avido dal disco più pesante; se lascia un resto (set di dischi su misura: 100 con [25, 20] darebbe 25 e 15 mancanti)
 *  si cerca fra le combinazioni con al massimo 6 dischi per taglia quella col resto minore e meno dischi.
 *  ponytail: la ricerca si ferma a 200 000 passi, poi vale il migliore trovato. */
export function loadPlates(total: number, bar: number, plates: number[]): PlateLoad {
  const start = Math.max(0, (total - bar) / 2)
  const sorted = [...new Set(plates)].filter((p) => p > 0).sort((a, b) => b - a)
  let remaining = start
  let perSide: number[] = []
  for (const plate of sorted) {
    while (remaining + 0.0001 >= plate) { perSide.push(plate); remaining -= plate }
  }
  if (remaining >= 0.0001) {
    let bestLeft = remaining, bestPlates = perSide, budget = 200_000
    const walk = (i: number, rem: number, chosen: number[]) => {
      if (budget-- <= 0) return
      if (bestLeft < 0.0001 && chosen.length >= bestPlates.length) return
      if (i === sorted.length) {
        if (rem < bestLeft - 0.0001 || (Math.abs(rem - bestLeft) <= 0.0001 && chosen.length < bestPlates.length)) { bestLeft = rem; bestPlates = [...chosen] }
        return
      }
      const p = sorted[i]
      let max = 0
      while (max < 6 && rem + 0.0001 >= p * (max + 1)) max++
      for (let k = max; k >= 0; k--) {
        for (let j = 0; j < k; j++) chosen.push(p)
        walk(i + 1, rem - k * p, chosen)
        chosen.length -= k
      }
    }
    walk(0, start, [])
    remaining = bestLeft
    perSide = bestPlates
  }
  return { perSide, leftover: remaining < 0.0001 ? 0 : Math.round(remaining * 100) / 100 }
}

export type PlateMode = { bar: true } | { bar: false } | null

export function defaultBarWeightFor(glyph: string, name = ''): number {
  const n = name.toLowerCase()
  if (n.includes('manubri') || n.includes('dumbbell')) return 0
  // «Curl Machine», «Preacher Curl Machine»: una macchina non ha il bilanciere, qualunque sia il pittogramma (il Multipower ha il suo)
  if (/machine|macchina/.test(n) && glyph !== 'smithMachine' && !n.includes('smith') && !n.includes('multipower')) return 0
  if (['barbell', 'benchFlat', 'benchIncline', 'squatRack', 'hipThrust', 'landmine', 'preacherBench'].includes(glyph)) return 20
  if (glyph === 'smithMachine') return 15
  if (glyph === 'ezBar') return 10
  if (glyph === 'trapBar') return 25
  if (n.includes('bilanciere') || n.includes('barbell')) return 20
  if (n.includes('multipower') || n.includes('smith')) return 15
  if (n.includes('ez')) return 10
  if (n.includes('trap bar') || n.includes('quadra')) return 25
  return 0
}

/**
 * Il calcolatore dischi compare dove c'è un bilanciere (anche panche, rack, hip thrust, EZ, trap bar) e per le plate loaded
 * (senza bilanciere). Con i manubri no: un vecchio «Panca Piana Manubri» personalizzato ha il pittogramma della panca.
 */
export function plateMode(glyph: string, exerciseName: string, barWeight?: number, plateLoaded?: boolean): PlateMode {
  const name = exerciseName.toLowerCase()
  if (barWeight !== undefined && barWeight > 0) return { bar: true }
  // ponytail: l'euristica sul nome è in OR con il flag, quindi l'interruttore non può spegnere il calcolatore di un macchinario con «plate» nel nome
  if (plateLoaded || glyph === 'plateLoaded' || /\bplate/.test(name)) return { bar: false }
  if (['barbell', 'benchFlat', 'benchIncline', 'squatRack', 'hipThrust', 'ezBar', 'trapBar'].includes(glyph)
    && !name.includes('manubri')) return { bar: true }
  return null
}

/** Euristica iniziale del flag «Carico a dischi» (nuovo esercizio o scelta dal catalogo). */
export const guessPlateLoaded = (glyph: string, exerciseName: string): boolean =>
  glyph === 'plateLoaded' || /\bplate/.test(exerciseName.toLowerCase())

// ---- unità -------------------------------------------------------------------------------------------

export const KG_PER_LB = 0.45359237

export const toDisplay = (kg: number, unit: Unit): number => (unit === 'lb' ? kg / KG_PER_LB : kg)
export const toKg = (value: number, unit: Unit): number => (unit === 'lb' ? value * KG_PER_LB : value)
/** Due decimali: un disco da 1,25 kg resta 1,25. */
export const round2 = (x: number): number => Math.round(x * 100) / 100

const LB_STEPS: Record<string, number> = { '1.25': 2.5, '2': 5, '2.5': 5, '5': 10, '10': 20 }

/** Il passo del macchinario (salvato in kg) nel passo nativo dell'unità scelta. */
export function displayStep(stepKg: number, unit: Unit): number {
  if (unit === 'kg') return stepKg
  return LB_STEPS[String(stepKg)] ?? Math.max(2.5, Math.round(stepKg / KG_PER_LB / 2.5) * 2.5)
}

export const BODYWEIGHT_STEP: Record<Unit, number> = { kg: 0.1, lb: 0.2 }
export const PLATES_KG_ALL = [25, 20, 15, 10, 5, 2.5, 1.25, 0.5]
export const PLATES_LB_ALL = [45, 35, 25, 10, 5, 2.5]

/** Dischi disponibili di serie, in kg (i dischi in lb sono salvati convertiti). */
export const defaultPlates = (unit: Unit): number[] =>
  unit === 'kg' ? [25, 20, 15, 10, 5, 2.5, 1.25] : PLATES_LB_ALL.map((p) => p * KG_PER_LB)
export const defaultBar = (unit: Unit): number => (unit === 'kg' ? 20 : 45 * KG_PER_LB)
/** Una sbarra di serie (in kg) come la vorrebbe l'unità: in lb si porta al multiplo di 5 lb più vicino (20 kg → 45 lb, non 44,09). */
export const barForUnit = (kg: number, unit: Unit): number => (unit === 'kg' || !(kg > 0) ? kg : Math.round(kg / KG_PER_LB / 5) * 5 * KG_PER_LB)

const sameList = (a: number[], b: number[]) =>
  a.length === b.length && [...a].sort((x, y) => x - y).every((v, i) => Math.abs(v - [...b].sort((x, y) => x - y)[i]) < 0.001)

/** Cambiando unità, dischi e bilanciere ancora di serie dell'unità vecchia passano a quelli di serie della nuova. */
export function settingsForUnitChange(
  from: Unit, to: Unit, plates: number[], bar?: number,
): { availablePlates: number[]; barWeight?: number } {
  return {
    availablePlates: sameList(plates, defaultPlates(from)) ? defaultPlates(to) : plates,
    ...(bar !== undefined ? { barWeight: Math.abs(bar - defaultBar(from)) < 0.001 ? defaultBar(to) : bar } : {}),
  }
}

// ---- inquadratura foto (Design/PhotoFraming.swift) --------------------------------------------------

export const PhotoFraming = {
  overflow(aspect: number, side: number): number {
    if (!(aspect > 0) || !(side > 0)) return 0
    return aspect > 1 ? side * (aspect - 1) : side * (1 / aspect - 1)
  },
  offsetPoints(normalized: number, aspect: number, side: number): number {
    return (Math.min(Math.max(normalized, -1), 1) * PhotoFraming.overflow(aspect, side)) / 2
  },
  isVertical: (aspect: number): boolean => aspect <= 1,
  normalizedDelta(dragPoints: number, aspect: number, side: number): number {
    const total = PhotoFraming.overflow(aspect, side) / 2
    return total > 0 ? dragPoints / total : 0
  },
}
