import { test } from 'node:test'
import assert from 'node:assert/strict'
import { medals, medalTotals, tierDates, type MedalInput } from './medals.ts'

const none: MedalInput = { sessions: 0, records: 0, streak: 0, volumeKg: 0, bestOneRm: 0, machines: 0, friends: 0 }
const byId = (i: MedalInput, id: string) => medals(i).find((m) => m.id === id)!

test('medaglie: livelli alle soglie, mai prima', () => {
  assert.equal(byId(none, 'sessions').tier, 0)
  assert.equal(byId({ ...none, sessions: 4 }, 'sessions').tier, 0)
  assert.equal(byId({ ...none, sessions: 5 }, 'sessions').tier, 1)
  assert.equal(byId({ ...none, sessions: 25 }, 'sessions').tier, 2)
  assert.equal(byId({ ...none, sessions: 100 }, 'sessions').tier, 3)
  assert.equal(byId({ ...none, friends: 1 }, 'friends').tier, 1)
})

test('medaglie: avanzamento verso il livello dopo, oro = completo', () => {
  const a = byId({ ...none, sessions: 15 }, 'sessions') // fra 5 e 25: metà strada
  assert.equal(a.next, 25)
  assert.ok(Math.abs(a.progress - 0.5) < 1e-9)
  assert.equal(byId({ ...none, sessions: 0 }, 'sessions').progress, 0)
  const gold = byId({ ...none, sessions: 300 }, 'sessions')
  assert.deepEqual([gold.next, gold.progress], [null, 1])
})

test('medaglie: totali', () => {
  assert.deepEqual(medalTotals(medals(none)), { earned: 0, total: 21 })
  assert.deepEqual(medalTotals(medals({ sessions: 100, records: 10, streak: 2, volumeKg: 0, bestOneRm: 0, machines: 0, friends: 0 })), { earned: 6, total: 21 })
})

test('date dei livelli: il primo istante in cui il livello c\'era, solo per quelli presi', () => {
  // sessioni = quanti istanti sono passati (1 per istante): bronzo a 5, argento a 25
  const times = Array.from({ length: 30 }, (_, i) => (i + 1) * 1000)
  const at = (ms: number) => medals({ ...none, sessions: ms / 1000 })
  const current = at(30_000)
  const d = tierDates([...times].reverse(), at, current) // l'ordine in ingresso non conta
  assert.deepEqual(d.sessions, [5000, 25_000, null]) // oro non preso: niente data
  assert.deepEqual(d.records, [null, null, null])
  assert.deepEqual(tierDates([], at, current).sessions, [null, null, null])
})

test('date dei livelli: una serie persa e riconquistata vale dal primo momento', () => {
  const vals = new Map([[1, 2], [2, 0], [3, 2]]) // settimane di fila nei tre istanti
  const at = (ms: number) => medals({ ...none, streak: vals.get(ms)! })
  assert.deepEqual(tierDates([1, 2, 3], at, at(3)).streak, [1, null, null])
})
