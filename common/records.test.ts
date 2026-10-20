import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { deriveRecords, recordsByExercise, currentBest } from './records.ts'

interface Vector {
  name: string; workouts: string[]; pr: string[]
  manual: { uuid: string; exercise: string; weight: number; reps: number; at: string }[]
  sets: { uuid: string; exercise: string | null; workout: string; weight: number; reps: number; order: number; at: string; isWarmup?: boolean; noRecord?: boolean }[]
}
const vectors: Vector[] = JSON.parse(readFileSync(new URL('./fixtures/records.json', import.meta.url), 'utf8'))

for (const v of vectors) {
  test(`records: ${v.name}`, () => {
    const { prSets, auto } = deriveRecords(
      v.manual.map((m) => ({ uuid: m.uuid, exercise: m.exercise, date: Date.parse(m.at), weight: m.weight, reps: m.reps })),
      v.sets.map((s) => ({ uuid: s.uuid, exercise: s.exercise, workout: s.workout, weight: s.weight, reps: s.reps, isWarmup: s.isWarmup ?? false, order: s.order, createdAt: Date.parse(s.at), noRecord: s.noRecord ?? false })),
      new Set(v.workouts),
    )
    assert.deepEqual([...prSets].sort(), [...v.pr].sort())
    assert.deepEqual(auto.map((a) => a.uuid).sort(), [...v.pr].sort(), 'un record automatico per ogni serie PR, con il suo uuid')
  })
}

test('il massimale è il carico vero; a parità esatta vince il più recente', () => {
  const { auto } = deriveRecords([], [
    { uuid: 'S1', exercise: 'E', workout: 'W', weight: 100, reps: 5, isWarmup: false, order: 0, createdAt: 1_000_000, noRecord: false },
  ], new Set(['W']))
  const manual = [{ uuid: 'M', exercise: 'E', date: 2_000_000, weight: 100, reps: 5 }]
  const best = currentBest(recordsByExercise(manual, auto).get('E'))
  assert.equal(best?.uuid, 'M')
})

test('modifica di una serie esistente che diventa PR', () => {
  const sets = [
    { uuid: 'S1', exercise: 'E', workout: 'W', weight: 100, reps: 5, isWarmup: false, order: 0, createdAt: 1_000_000, noRecord: false },
    { uuid: 'S2', exercise: 'E', workout: 'W', weight: 90, reps: 5, isWarmup: false, order: 1, createdAt: 1_060_000, noRecord: false },
  ]
  const initial = deriveRecords([], sets, new Set(['W']))
  assert.deepEqual([...initial.prSets], ['S1'])

  // Modifica di S2 che supera S1: diventa PR
  const editedSets = [
    sets[0],
    { ...sets[1], weight: 110 },
  ]
  const updated = deriveRecords([], editedSets, new Set(['W']))
  assert.deepEqual([...updated.prSets].sort(), ['S1', 'S2'].sort())
  assert.equal(updated.auto.find((r) => r.uuid === 'S2')?.weight, 110)
})

test('intervalli andamento disponibili in base alla storia', () => {
  function getRanges(spanDays: number): ('1m' | '3m' | '1y' | 'all')[] {
    const out: ('1m' | '3m' | '1y' | 'all')[] = []
    if (spanDays >= 30 && spanDays < 365) out.push('1m')
    if (spanDays >= 90) out.push('3m')
    if (spanDays >= 365) out.push('1y')
    out.push('all')
    return out
  }

  assert.deepEqual(getRanges(10), ['all'])
  assert.deepEqual(getRanges(45), ['1m', 'all'])
  assert.deepEqual(getRanges(120), ['1m', '3m', 'all'])
  assert.deepEqual(getRanges(400), ['3m', '1y', 'all'])
})

test('serie precedenti escludono riscaldamento e prendono il workout precedente più recente', () => {
  interface SetItem { uuid: string; exercise: string; workout: string; weight: number; reps: number; isWarmup: boolean }
  interface WorkoutItem { uuid: string; date: number }

  const workouts: WorkoutItem[] = [
    { uuid: 'W_TODAY', date: 3_000_000 },
    { uuid: 'W_PAST_2', date: 2_000_000 },
    { uuid: 'W_PAST_1', date: 1_000_000 },
  ]

  const sets: SetItem[] = [
    // W_PAST_1
    { uuid: 'S1', exercise: 'E1', workout: 'W_PAST_1', weight: 50, reps: 10, isWarmup: false },
    // W_PAST_2 (riscaldamento + 2 serie allenanti)
    { uuid: 'S2_WARM', exercise: 'E1', workout: 'W_PAST_2', weight: 40, reps: 10, isWarmup: true },
    { uuid: 'S2_1', exercise: 'E1', workout: 'W_PAST_2', weight: 60, reps: 8, isWarmup: false },
    { uuid: 'S2_2', exercise: 'E1', workout: 'W_PAST_2', weight: 65, reps: 6, isWarmup: false },
    // W_TODAY
    { uuid: 'S3', exercise: 'E1', workout: 'W_TODAY', weight: 70, reps: 5, isWarmup: false },
  ]

  function previousWorkingSets(exerciseId: string, currentWorkoutId: string): SetItem[] {
    const cur = workouts.find((w) => w.uuid === currentWorkoutId)
    if (!cur) return []
    const past = workouts.filter((w) => w.uuid !== cur.uuid && w.date < cur.date).sort((a, b) => b.date - a.date)
    for (const w of past) {
      const working = sets.filter((s) => s.workout === w.uuid && s.exercise === exerciseId && !s.isWarmup)
      if (working.length > 0) return working
    }
    return []
  }

  const prev = previousWorkingSets('E1', 'W_TODAY')
  assert.equal(prev.length, 2)
  assert.deepEqual(prev.map((s) => s.uuid), ['S2_1', 'S2_2'])
})
