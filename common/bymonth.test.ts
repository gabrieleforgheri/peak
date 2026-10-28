import { test } from 'node:test'
import assert from 'node:assert/strict'
import { byMonth, notebookLine } from './format.ts'

test('byMonth: un gruppo per mese locale, ordine in ingresso, dicembre e gennaio separati', () => {
  const at = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime()
  const items = [at(2026, 10, 30), at(2026, 10, 1, 0), at(2026, 9, 30, 23), at(2026, 1, 5), at(2025, 1, 5)]
  const groups = byMonth(items, (x) => x)
  assert.deepEqual(groups.map((g) => [g.key, g.items.length]), [['2026-10', 2], ['2026-9', 1], ['2026-1', 1], ['2025-1', 1]])
  assert.equal(groups[0]!.ms, items[0])
  assert.deepEqual(byMonth([], (x: number) => x), [])
})

test('notebookLine: raggruppa per peso consecutivo, non per peso uguale ma separato', () => {
  const s = (weight: number, reps: number) => ({ weight, reps })
  assert.equal(notebookLine([s(71.5, 8), s(71.5, 6), s(54, 8)], 'kg'), '71,5 kg 8·6 · 54 kg 8')
  assert.equal(notebookLine([s(54, 8), s(71.5, 6), s(54, 8)], 'kg'), '54 kg 8 · 71,5 kg 6 · 54 kg 8')
  assert.equal(notebookLine([], 'kg'), '')
  // a un braccio: il gruppo si spezza anche a parità di peso e porta il prefisso
  const a = (w: number, r: number) => ({ weight: w, reps: r, singleArm: true })
  assert.equal(notebookLine([s(40, 8), a(40, 8), a(40, 7), s(70, 10)], 'kg'), '40 kg 8 · 1 br 40 kg 8·7 · 70 kg 10')
  assert.equal(notebookLine([a(40, 8)], 'kg', 'en'), '1 arm 40 kg 8')
  // gambe: il prefisso è «1 gb» / «1 leg»
  assert.equal(notebookLine([a(40, 8)], 'kg', 'it', 'gambe'), '1 gb 40 kg 8')
  assert.equal(notebookLine([a(40, 8)], 'kg', 'en', 'gambe'), '1 leg 40 kg 8')
})
