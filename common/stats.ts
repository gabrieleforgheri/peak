// Aggregati per la scheda Progressi (Utils/Stats.swift). Le settimane partono da lunedì, nel fuso del runtime.

import type { MuscleGroup } from './docs.ts'
import { MUSCLE_GROUPS } from './docs.ts'

export interface StatWorkout { date: number; sets: { exercise: string | null; weight: number; reps: number; isWarmup: boolean; singleArm?: boolean }[] }

const DAY = 86_400_000

export function weekStart(ms: number): number {
  const d = new Date(ms)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}
const weeksBefore = (ms: number, n: number): number => { const d = new Date(ms); d.setDate(d.getDate() - 7 * n); return d.getTime() }

/** Carico sollevato da una serie: a un braccio il peso è quello del singolo manico e le ripetizioni sono per lato, quindi conta il doppio. */
export const setVolume = (s: { weight: number; reps: number; singleArm?: boolean }): number => s.weight * s.reps * (s.singleArm ? 2 : 1)

export const workoutVolume = (w: StatWorkout): number =>
  w.sets.reduce((sum, s) => sum + (s.isWarmup ? 0 : setVolume(s)), 0)

/** Volume per settimana nelle ultime `weeks`, solo le settimane in cui ti sei allenato. */
export function weeklyVolume(workouts: StatWorkout[], weeks = 12, now = Date.now()): { weekStart: number; volume: number }[] {
  const thisWeek = weekStart(now)
  const oldest = weeksBefore(thisWeek, weeks - 1)
  const buckets = new Map<number, number>()
  for (const w of workouts) {
    const start = weekStart(w.date)
    if (start >= oldest && start <= thisWeek) buckets.set(start, (buckets.get(start) ?? 0) + workoutVolume(w))
  }
  return [...buckets].map(([ws, volume]) => ({ weekStart: ws, volume })).sort((a, b) => a.weekStart - b.weekStart)
}

/** Ripartizione del volume per gruppo nell'intervallo di giorni, riscaldamento escluso, dal più alto. */
export function volumeByGroup(
  workouts: StatWorkout[], groupOf: (exercise: string) => MuscleGroup | undefined, days = 30, now = Date.now(),
): { group: MuscleGroup; volume: number }[] {
  const cutoff = now - days * DAY
  const totals = new Map<MuscleGroup, number>()
  for (const w of workouts) {
    if (w.date < cutoff) continue
    for (const s of w.sets) {
      if (s.isWarmup || !s.exercise) continue
      const g = groupOf(s.exercise)
      if (g) totals.set(g, (totals.get(g) ?? 0) + setVolume(s))
    }
  }
  return MUSCLE_GROUPS.filter((g) => (totals.get(g) ?? 0) > 0).map((g) => ({ group: g, volume: totals.get(g)! })).sort((a, b) => b.volume - a.volume)
}

export interface Mover { exercise: string; previous: number; current: number; deltaPercent: number }

/** Migliore carico negli ultimi `days` contro il migliore precedente (0 = nuovo → +100%). */
export function topMovers(
  exercises: { id: string; records: { weight: number; date: number }[] }[], days = 90, limit = 5, now = Date.now(),
): Mover[] {
  const cutoff = now - days * DAY
  const movers: Mover[] = []
  for (const e of exercises) {
    const recent = Math.max(0, ...e.records.filter((r) => r.date >= cutoff).map((r) => r.weight))
    if (!e.records.some((r) => r.date >= cutoff)) continue
    const previous = Math.max(0, ...e.records.filter((r) => r.date < cutoff).map((r) => r.weight))
    if (!(recent > previous)) continue
    movers.push({ exercise: e.id, previous, current: recent, deltaPercent: previous > 0 ? ((recent - previous) / previous) * 100 : 100 })
  }
  return movers.sort((a, b) => b.deltaPercent - a.deltaPercent).slice(0, limit)
}

/** Fino a `max` indici equidistanti fra 0 e count−1, primo e ultimo inclusi. */
export function tickIndices(count: number, max = 4): number[] {
  if (count <= 0 || max <= 0) return []
  if (count <= max) return Array.from({ length: count }, (_, i) => i)
  const step = (count - 1) / (max - 1)
  return Array.from({ length: max }, (_, i) => Math.round(i * step))
}

export const sessionCount = (workouts: StatWorkout[], days: number, now = Date.now()): number =>
  workouts.filter((w) => w.date >= now - days * DAY).length

/** Settimane consecutive con almeno un allenamento, fino a questa. Finché in questa settimana non ti sei allenato la serie
 *  è ancora viva: si conta da quella scorsa (si spegne solo a fine settimana). */
export function weeklyStreak(workouts: { date: number }[], now = Date.now()): number {
  const trained = new Set(workouts.map((w) => weekStart(w.date)))
  let streak = 0
  let cursor = weekStart(now)
  if (!trained.has(cursor)) cursor = weeksBefore(cursor, 1)
  for (; trained.has(cursor); cursor = weeksBefore(cursor, 1)) streak++
  return streak
}

/**
 * L'obiettivo della settimana: quanti allenamenti fai di solito. La media delle ultime otto settimane (questa esclusa),
 * arrotondata, fra 2 e 5; con meno di due settimane di storia, 3. Se la storia è più corta di otto settimane si divide per quelle che ci sono.
 */
export function weeklyGoal(workouts: { date: number }[], now = Date.now()): number {
  const thisWeek = weekStart(now)
  const perWeek = new Map<number, number>()
  for (const w of workouts) {
    const ws = weekStart(w.date)
    if (ws < thisWeek && ws >= weeksBefore(thisWeek, 8)) perWeek.set(ws, (perWeek.get(ws) ?? 0) + 1)
  }
  if (perWeek.size < 2) return 3
  const first = workouts.reduce((a, w) => Math.min(a, w.date), Infinity)
  const weeks = Math.max(1, Math.min(8, Math.round((thisWeek - weekStart(first)) / (7 * DAY))))
  const avg = [...perWeek.values()].reduce((a, b) => a + b, 0) / weeks
  return Math.min(5, Math.max(2, Math.round(avg)))
}
