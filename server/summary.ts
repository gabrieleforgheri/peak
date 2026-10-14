// Il riepilogo che yG mostra nell'area personale (/account/peak): ultimo allenamento, numeri dei 30 giorni,
// ultimi record. Stesse regole dell'app: contano solo gli allenamenti conclusi, i record si derivano dalle serie.

import { split, type LiveDoc } from '../common/docs.ts'
import { deriveRecords } from '../common/records.ts'
import { sessionCount, weeklyStreak, workoutVolume } from '../common/stats.ts'

const DAY = 86_400_000

export interface Summary {
  ultimo: { data: string; durataSec: number; volumeKg: number; record: number; nome: string } | null
  trentaGiorni: { sessioni: number; volumeKg: number; record: number; settimane: number }
  ultimiRecord: { esercizio: string; pesoKg: number; rip: number; data: string; auto: boolean }[]
}

export function summarize(docs: Iterable<LiveDoc>, now = Date.now()): Summary {
  const d = split(docs)
  const exName = new Map(d.exercises.map((e) => [e.uuid, e.data.name]))
  const wkIds = new Set(d.workouts.map((w) => w.uuid))
  const manual = d.records.filter((r) => exName.has(r.data.exercise))
    .map((r) => ({ uuid: r.uuid, exercise: r.data.exercise, date: Date.parse(r.data.date), weight: r.data.weight, reps: r.data.reps }))
  const sets = d.sets.filter((s) => wkIds.has(s.data.workout)).map((s) => ({
    uuid: s.uuid, exercise: s.data.exercise && exName.has(s.data.exercise) ? s.data.exercise : null, workout: s.data.workout,
    weight: s.data.weight, reps: s.data.reps, isWarmup: s.data.isWarmup, order: s.data.order, createdAt: Date.parse(s.data.createdAt), noRecord: s.data.noRecord,
  }))
  const { prSets, auto } = deriveRecords(manual, sets, wkIds)

  const finished = d.workouts.filter((w) => w.data.endedAt).map((w) => ({
    uuid: w.uuid, name: w.data.name, date: Date.parse(w.data.date), ended: Date.parse(w.data.endedAt!),
    sets: sets.filter((s) => s.workout === w.uuid),
  })).sort((a, b) => b.date - a.date)
  const stat = finished.map((w) => ({ date: w.date, sets: w.sets.map((s) => ({ exercise: s.exercise, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup })) }))

  const all = [...manual.map((r) => ({ ...r, auto: false })), ...auto.map((r) => ({ ...r, auto: true }))]
  const last = finished[0]
  return {
    ultimo: last ? {
      data: new Date(last.date).toISOString(), durataSec: Math.max(0, Math.round((last.ended - last.date) / 1000)),
      volumeKg: workoutVolume(stat[0]), record: last.sets.filter((s) => prSets.has(s.uuid)).length, nome: last.name,
    } : null,
    trentaGiorni: {
      sessioni: sessionCount(stat, 30, now),
      volumeKg: stat.filter((w) => w.date >= now - 30 * DAY).reduce((v, w) => v + workoutVolume(w), 0),
      record: all.filter((r) => r.date >= now - 30 * DAY).length,
      settimane: weeklyStreak(stat, now),
    },
    ultimiRecord: all.sort((a, b) => b.date - a.date).slice(0, 5)
      .map((r) => ({ esercizio: exName.get(r.exercise)!, pesoKg: r.weight, rip: r.reps, data: new Date(r.date).toISOString(), auto: r.auto })),
  }
}
