// La quota: ogni 100 kg sollevati valgono un metro di salita. Le vette sono le tappe, dal Vesuvio all'Everest e oltre.
// È un gioco: non misura niente di fisico, ma dà un posto dove andare a chi solleva ogni settimana.

export const KG_PER_METER = 100

export interface Summit { name: string; meters: number }
export const SUMMITS: readonly Summit[] = [
  { name: 'Vesuvio', meters: 1281 },
  { name: 'Etna', meters: 3357 },
  { name: 'Monte Bianco', meters: 4806 },
  { name: 'Kilimangiaro', meters: 5895 },
  { name: 'Everest', meters: 8849 },
  { name: 'Olympus Mons', meters: 21_900 },
]

export interface Climb {
  /** Metri saliti (interi). */
  meters: number
  /** La vetta che stai salendo; `null` dopo l'ultima. */
  next: Summit | null
  /** L'ultima vetta raggiunta, se ce n'è. */
  last: Summit | null
  /** Quanto hai fatto del tratto fra l'ultima vetta e la prossima, da 0 a 1. */
  progress: number
  /** Metri che mancano alla prossima vetta. */
  missing: number
  /** Vette raggiunte. */
  reached: number
}

export function climb(volumeKg: number): Climb {
  const meters = Math.floor(Math.max(0, volumeKg) / KG_PER_METER)
  const reached = SUMMITS.filter((s) => meters >= s.meters).length
  const last = reached ? SUMMITS[reached - 1]! : null
  const next = SUMMITS[reached] ?? null
  const from = last?.meters ?? 0
  return { meters, next, last, reached, missing: next ? next.meters - meters : 0, progress: next ? (meters - from) / (next.meters - from) : 1 }
}
