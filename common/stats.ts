// Aggregati per la scheda Progressi (Utils/Stats.swift). Le settimane partono da lunedì, nel fuso del runtime.

import type { MuscleGroup } from './docs.ts'
import { MUSCLE_GROUPS } from './docs.ts'

export interface StatWorkout { date: number; sets: { exercise: string | null; weight: number; reps: number; isWarmup: boolean }[] }

const DAY = 86_400_000

export function weekStart(ms: number): number {
  const d = new Date(ms)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}
const weeksBefore = (ms: number, n: number): number => { const d = new Date(ms); d.setDate(d.getDate() - 7 * n); return d.getTime() }

export const workoutVolume = (w: StatWorkout): number =>
  w.sets.reduce((sum, s) => sum + (s.isWarmup ? 0 : s.weight * s.reps), 0)

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
      if (g) totals.set(g, (totals.get(g) ?? 0) + s.weight * s.reps)
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

/** Settimane consecutive con almeno un allenamento, fino a questa; 0 se questa settimana niente. */
export function weeklyStreak(workouts: { date: number }[], now = Date.now()): number {
  const trained = new Set(workouts.map((w) => weekStart(w.date)))
  let streak = 0
  for (let cursor = weekStart(now); trained.has(cursor); cursor = weeksBefore(cursor, 1)) streak++
  return streak
}
