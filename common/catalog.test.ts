import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { CATALOG_ID_RE, matchByName, norm, searchCatalog, type CatalogEntry } from './catalog.ts'
import { GLYPHS, MUSCLE_GROUPS } from './docs.ts'

const CATALOG = JSON.parse(readFileSync(new URL('./catalog.json', import.meta.url), 'utf8')) as CatalogEntry[]

test('catalogo: id unici e ben formati, campi validi', () => {
  const ids = new Set<string>()
  for (const e of CATALOG) {
    assert.match(e.id, CATALOG_ID_RE, e.id)
    assert.ok(!ids.has(e.id), `id doppio ${e.id}`); ids.add(e.id)
    assert.ok(e.it && e.en, e.id)
    assert.ok((MUSCLE_GROUPS as readonly string[]).includes(e.group), e.id)
    assert.ok((GLYPHS as readonly string[]).includes(e.glyph), e.id)
    assert.ok(e.step > 0 && e.step <= 100, e.id)
    assert.ok(Array.isArray(e.alias) && typeof e.notes === 'string', e.id)
  }
  assert.ok(CATALOG.length >= 250)
})

test('catalogo: nessun nome (it, en, sinonimo) appartiene a due voci', () => {
  const owner = new Map<string, string>()
  for (const e of CATALOG) for (const n of new Set([e.it, e.en, ...e.alias].map(norm))) {
    const other = owner.get(n)
    assert.ok(!other || other === e.id, `«${n}» è di ${other} e di ${e.id}`)
    owner.set(n, e.id)
  }
})

test('catalogo: ogni gruppo ha almeno 20 voci e i vecchi 98 nomi italiani ci sono tutti', () => {
  for (const g of MUSCLE_GROUPS) assert.ok(CATALOG.filter((e) => e.group === g).length >= 20, g)
  for (const name of ['Chest Press', 'Panca Piana Bilanciere', 'Leg Press 45°', 'Stacco da Terra', 'Plank', 'Curl ai Polsi']) {
    assert.ok(matchByName(CATALOG, name), name)
  }
})

test('norm: accenti, maiuscole, punteggiatura', () => {
  assert.equal(norm('  Leg Press 45° '), 'leg press 45')
  assert.equal(norm('Farmer\'s Walk'), 'farmer s walk')
  assert.equal(norm('Perché'), 'perche')
})

test('matchByName: nome italiano, inglese o sinonimo; mai a metà', () => {
  assert.equal(matchByName(CATALOG, 'panca piana bilanciere')?.id, 'bench-press-barbell')
  assert.equal(matchByName(CATALOG, 'Barbell Bench Press')?.id, 'bench-press-barbell')
  assert.equal(matchByName(CATALOG, 'bench press')?.id, 'bench-press-barbell')
  assert.equal(matchByName(CATALOG, 'Panca'), null)
  assert.equal(matchByName(CATALOG, 'Il mio macchinario'), null)
  assert.equal(matchByName(CATALOG, '   '), null)
})

test('searchCatalog: più parole in qualsiasi ordine, prima chi inizia con la ricerca', () => {
  assert.equal(searchCatalog(CATALOG, 'bilanciere panca')[0].id, 'bench-press-barbell')
  const butterfly = searchCatalog(CATALOG, 'butterfly')
  assert.equal(butterfly[0].id, 'pec-deck')
  const pec = searchCatalog(CATALOG, 'pec')
  assert.ok(pec.some((e) => e.id === 'pec-deck'))
  assert.deepEqual(searchCatalog(CATALOG, 'xyzzy'), [])
  assert.equal(searchCatalog(CATALOG, '', 5).length, 5)
  assert.ok(searchCatalog(CATALOG, 'curl').length <= 40)
})
