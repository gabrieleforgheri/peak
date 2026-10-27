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
})
