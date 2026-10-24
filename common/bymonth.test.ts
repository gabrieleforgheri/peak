import { test } from 'node:test'
import assert from 'node:assert/strict'
import { byMonth } from './format.ts'

test('byMonth: un gruppo per mese locale, ordine in ingresso, dicembre e gennaio separati', () => {
  const at = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime()
  const items = [at(2026, 10, 30), at(2026, 10, 1, 0), at(2026, 9, 30, 23), at(2026, 1, 5), at(2025, 1, 5)]
  const groups = byMonth(items, (x) => x)
  assert.deepEqual(groups.map((g) => [g.key, g.items.length]), [['2026-10', 2], ['2026-9', 1], ['2026-1', 1], ['2025-1', 1]])
  assert.equal(groups[0]!.ms, items[0])
  assert.deepEqual(byMonth([], (x: number) => x), [])
})
