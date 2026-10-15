import { test } from 'node:test'
import assert from 'node:assert/strict'
import { climb, SUMMITS } from './altitude.ts'

test('quota: 100 kg = 1 m, tappe in ordine', () => {
  assert.equal(climb(0).meters, 0)
  assert.equal(climb(99).meters, 0)
  assert.equal(climb(10_000).meters, 100)
  assert.ok(SUMMITS.every((s, i) => i === 0 || s.meters > SUMMITS[i - 1]!.meters))
})

test('quota: prossima vetta, avanzamento e arrivo', () => {
  const a = climb(64_050) // 640 m, a metà del Vesuvio
  assert.deepEqual([a.next?.name, a.last, a.reached, a.missing], ['Vesuvio', null, 0, 641])
  assert.ok(Math.abs(a.progress - 640 / 1281) < 1e-9)
  const b = climb(130_000) // 1300 m: Vesuvio fatto, si sale verso l'Etna
  assert.deepEqual([b.last?.name, b.next?.name, b.reached], ['Vesuvio', 'Etna', 1])
  assert.ok(b.progress > 0 && b.progress < 0.02)
  const top = climb(10_000_000)
  assert.deepEqual([top.next, top.progress, top.missing, top.reached], [null, 1, 0, SUMMITS.length])
  assert.equal(climb(-5).meters, 0)
})
