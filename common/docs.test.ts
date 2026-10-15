import { test } from 'node:test'
import assert from 'node:assert/strict'
import { cleanItems, parseDoc, upgradeDoc, LEGACY_SETTINGS, SETTING_DEFAULTS } from './docs.ts'

const U = (n: number) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const item = { exercise: U(1), sets: 4, repsMin: 8, repsMax: 10, weight: 60 }

test('scheda: nome obbligatorio, righe ripulite, uuid in maiuscolo', () => {
  const ok = parseDoc('template', U(5), { name: ' Push 1 ', items: [{ ...item, exercise: U(1).toLowerCase(), weight: undefined }], sortIndex: 2, createdAt: '2026-10-01T08:00:00Z' })
  assert.deepEqual(ok, { data: { name: 'Push 1', items: [{ ...item, weight: null }], sortIndex: 2, createdAt: '2026-10-01T08:00:00Z', hidden: false } })
  assert.deepEqual(parseDoc('template', U(5), { name: '  ', items: [], createdAt: '2026-10-01T08:00:00Z' }), { error: 'name' })
  assert.deepEqual(parseDoc('template', U(5), { name: 'x', items: 'no', createdAt: '2026-10-01T08:00:00Z' }), { error: 'items' })
})

test('righe: rip min ≤ max, intervalli, massimo di righe', () => {
  assert.equal(cleanItems([{ ...item, repsMin: 12, repsMax: 8 }]), null)
  assert.equal(cleanItems([{ ...item, sets: 21 }]), null)
  assert.equal(cleanItems([{ ...item, weight: -1 }]), null)
  assert.equal(cleanItems([{ ...item, exercise: 'x' }]), null)
  assert.equal(cleanItems(Array.from({ length: 61 }, () => item)), null)
  assert.deepEqual(cleanItems([{ exercise: U(1) }]), [{ exercise: U(1), sets: 0, repsMin: 0, repsMax: 0, weight: null }])
})

test('allenamento: i documenti vecchi, senza scheda né piano, restano validi', () => {
  const base = { date: '2026-10-01T08:00:00Z', endedAt: null, note: '', name: 'Push', focus: 'petto' }
  assert.deepEqual(parseDoc('workout', U(9), base), { data: { ...base, template: '', plan: [] } })
  assert.deepEqual(parseDoc('workout', U(9), { ...base, template: U(5), plan: [item] }), { data: { ...base, template: U(5), plan: [item] } })
  assert.deepEqual(parseDoc('workout', U(9), { ...base, template: 'no' }), { error: 'template' })
  assert.deepEqual(parseDoc('workout', U(9), { ...base, plan: [{ ...item, repsMin: 9, repsMax: 1 }] }), { error: 'plan' })
})

test('impostazioni tolte: restano in LEGACY_SETTINGS e non fra quelle vere', () => {
  for (const k of LEGACY_SETTINGS) assert.ok(!(k in SETTING_DEFAULTS), k)
  assert.deepEqual(parseDoc('setting', 'unit', { value: 'lb' }), { data: { value: 'lb' } })
})

test('upgradeDoc: un allenamento salvato prima delle schede prende scheda vuota e piano vuoto', () => {
  const old = { date: '2026-10-01T08:00:00Z', endedAt: null, note: '', name: '', focus: '' }
  assert.deepEqual(upgradeDoc('workout', old), { ...old, template: '', plan: [] })
  assert.deepEqual(upgradeDoc('exercise', old), { ...old, catalog: '', hidden: false })
  assert.deepEqual(upgradeDoc('template', old), { ...old, hidden: false })
  assert.deepEqual(upgradeDoc('bodyweight', old), old)
})

test('esercizio: id del catalogo e nascosto', () => {
  const base = { name: 'Lat', muscleGroup: 'schiena', incrementStep: 5, createdAt: '2026-10-01T08:00:00Z' }
  const ok = parseDoc('exercise', U(1), { ...base, catalog: 'lat-pulldown', hidden: true }) as { data: { catalog: string; hidden: boolean } }
  assert.deepEqual([ok.data.catalog, ok.data.hidden], ['lat-pulldown', true])
  const plain = parseDoc('exercise', U(1), base) as { data: { catalog: string; hidden: boolean } }
  assert.deepEqual([plain.data.catalog, plain.data.hidden], ['', false])
  for (const bad of ['Lat Pulldown', 'lat_pulldown', 'x'.repeat(81), 5]) assert.deepEqual(parseDoc('exercise', U(1), { ...base, catalog: bad }), { error: 'catalog' })
})
