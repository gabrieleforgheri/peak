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
