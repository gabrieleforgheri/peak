import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { ANATOMY, MUSCLE_BOX } from './anatomy.ts'
import { MUSCLES, MUSCLE_INFO, isMuscle, type Muscle } from './muscle-info.ts'
import { figureFor, muscleViews, viewFor, zoomFor } from './muscles.ts'
import { parseTable, check, TABLE_FILE } from '../scripts/gen-catalog-muscles.ts'
import { tsSource, swiftSource, viewPieces, SWIFT_FILE, TS_FILE } from '../scripts/gen-anatomy.ts'

test('anatomia: common/anatomy.ts e il Swift sono quelli che il generatore scrive oggi', () => {
  const views = { front: viewPieces('front'), back: viewPieces('back') }
  assert.equal(readFileSync(TS_FILE, 'utf8'), tsSource(views), 'lancia: node scripts/gen-anatomy.ts')
  assert.equal(readFileSync(SWIFT_FILE, 'utf8'), swiftSource(views), 'lancia: node scripts/gen-anatomy.ts')
})

test('anatomia: ogni muscolo ha un pezzo e un rettangolo in almeno una vista; i percorsi sono solo M L C Z', () => {
  for (const m of MUSCLES) assert.ok(muscleViews(m).length >= 1, m)
  for (const v of ['front', 'back'] as const) {
    for (const p of ANATOMY[v]) {
      for (const d of p.paths) assert.match(d, /^[MLCZ\d.\- ]+$/, `${v} ${p.slug}`)
    }
  }
})

test('tabella dei muscoli del catalogo: una riga per voce, muscoli validi, catalog.json aggiornato', () => {
  const catalog = JSON.parse(readFileSync(new URL('./catalog.json', import.meta.url), 'utf8')) as { id: string; muscles: Muscle[]; view: string }[]
  const rows = parseTable(readFileSync(TABLE_FILE, 'utf8'))
  assert.deepEqual(check(rows, catalog.map((e) => e.id)), [])
  for (const r of rows) {
    const e = catalog.find((x) => x.id === r.id)!
    assert.deepEqual(e.muscles, r.muscles, r.id)
    assert.equal(e.view, r.view, r.id)
  }
  assert.ok(rows.every((r) => r.muscles.every(isMuscle)))
})

test('vista: quella con più muscoli principali; a parità di fronte; una vista forzata vince', () => {
  assert.equal(viewFor(['lowerBack', 'gluteal']), 'back')
  assert.equal(viewFor(['chest']), 'front')
  assert.equal(viewFor(['deltoids']), 'front') // a parità, di fronte
  assert.equal(figureFor(['deltoids'], 'back').view, 'back')
  assert.equal(figureFor(['chest'], '').view, 'front')
})

test('zoom: il rettangolo contiene i muscoli attivi e ha una misura minima; senza muscoli in quella vista, tutto il corpo', () => {
  const [x0, y0, x1, y1] = zoomFor(['chest'], 'front')
  const c = MUSCLE_BOX.front.chest!
  assert.ok(x0 <= c[0] && y0 <= c[1] && x1 >= c[2] && y1 >= c[3], 'il petto sta dentro lo zoom')
  assert.ok(x1 - x0 >= 240 && y1 - y0 >= 240, 'misura minima')
  assert.ok(zoomFor(['chest'], 'front')[2] < 724, 'non è tutto il corpo')
  assert.deepEqual(zoomFor(['chest'], 'back'), [0, 0, 724, 1448])
  assert.deepEqual(zoomFor([], 'front'), [0, 0, 724, 1448])
})

test('info: ogni muscolo ha gruppo e nomi in italiano e inglese', () => {
  for (const m of MUSCLES) {
    assert.ok(MUSCLE_INFO[m].it && MUSCLE_INFO[m].en, m)
  }
})
