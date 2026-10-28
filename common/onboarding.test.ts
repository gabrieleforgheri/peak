import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import type { CatalogEntry } from './catalog.ts'
import { MUSCLE_GROUPS } from './docs.ts'
import { SUGGESTED_MACHINES, SUGGESTED_PRESELECTED } from './onboarding.ts'

const CATALOG = new Map((JSON.parse(readFileSync(new URL('./catalog.json', import.meta.url), 'utf8')) as CatalogEntry[]).map((e) => [e.id, e]))

test('primo avvio: cinque macchinari per gruppo, tutti nel catalogo e del loro gruppo', () => {
  const seen = new Set<string>()
  for (const g of MUSCLE_GROUPS) {
    const ids = SUGGESTED_MACHINES[g]
    assert.equal(ids.length, 5, g)
    assert.ok(SUGGESTED_PRESELECTED <= ids.length)
    for (const id of ids) {
      assert.ok(CATALOG.has(id), `${id} non è nel catalogo`)
      assert.equal(CATALOG.get(id)!.group, g, `${id} non è del gruppo ${g}`)
      assert.ok(!seen.has(id), `${id} è doppio`)
      seen.add(id)
    }
  }
})
