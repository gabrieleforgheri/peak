import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { openStore } from './store.ts'
import { exportBackup, parseBackup, planImport, serializeBackup, type BackupFile } from './backup.ts'

const U = (n: number) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xdb, 1, 2, 3, 4]).toString('base64')

/** Un backup come lo scrive l'app 1.x: record automatici inclusi, uuid maiuscoli, date a secondi. */
function sample(): BackupFile {
  return {
    version: 1, exportedAt: '2026-10-07T16:54:28Z',
    exercises: [
      { uuid: U(1), name: 'Chest Press', muscleGroup: 'petto', notes: '', incrementStep: 5, sortIndex: 0, isArchived: false, createdAt: '2026-09-01T08:00:00Z', photo: JPEG, photoOffset: 0.5, isUnilateral: true, restSeconds: 90, glyph: 'pecDeck',
        records: [
          { uuid: U(101), date: '2026-09-02T09:00:00Z', weight: 50, reps: 5, isEstimated: false, note: '' },
          { uuid: U(201), date: '2026-09-10T10:00:00Z', weight: 60, reps: 5, isEstimated: true, note: 'Da allenamento' },
        ] },
      { uuid: U(2), name: 'Lat Machine', muscleGroup: 'schiena', notes: 'n', incrementStep: 2.5, sortIndex: 1, isArchived: true, createdAt: '2026-09-01T08:00:00Z', records: [
        { uuid: U(202), date: '2026-09-15T10:00:00Z', weight: 99, reps: 9, isEstimated: true, note: 'Da allenamento' }, // orfano: nessuna serie
      ] },
    ],
    workouts: [
      { uuid: U(10), date: '2026-09-10T09:30:00Z', endedAt: '2026-09-10T10:30:00Z', note: 'ok', focus: 'petto', name: 'Push', sets: [
        { uuid: U(201), exerciseUUID: U(1), weight: 60, reps: 5, isWarmup: false, order: 0, createdAt: '2026-09-10T10:00:00Z', isPersonalRecord: true },
        { uuid: U(202 + 1000), exerciseUUID: U(1), weight: 20, reps: 10, isWarmup: true, order: 1, createdAt: '2026-09-10T10:05:00Z', isPersonalRecord: false },
        { uuid: U(204), weight: 30, reps: 8, isWarmup: false, order: 2, createdAt: '2026-09-10T10:06:00Z', isPersonalRecord: false },
      ] },
    ],
    bodyWeights: [{ uuid: U(300), date: '2026-09-11T07:00:00Z', weight: 78.4, fromHealth: false }],
  }
}

const store = () => { const dir = mkdtempSync(join(tmpdir(), 'peak-')); const s = openStore(dir); return { s, done: () => { s.close(); rmSync(dir, { recursive: true }) } } }
const parse = (raw: unknown) => { const r = parseBackup(raw); assert.ok('file' in r, JSON.stringify(r)); return r.file }
const photoOf = (s: ReturnType<typeof openStore>, uid: number) => (sha: string) => s.getPhoto(uid, sha)

test('import → export: stessi dati, record automatici rigenerati dalle serie', () => {
  const { s, done } = store()
  try {
    const uid = s.upsertUser('sub-1', 'a@b.it', 'a', 'A').id
    const plan = planImport(parse(sample()), [], 'sostituisci')
    s.applyPlan(uid, plan)
    assert.deepEqual(plan.conteggi, { esercizi: 2, allenamenti: 1, serie: 3, record: 1, pesate: 1, schede: 0 })
    assert.equal(plan.orfani, 1, 'il record automatico senza serie è scartato e contato')
    const out = exportBackup(s.live(uid), photoOf(s, uid), Date.parse('2026-10-14T12:00:00Z'))
    const ex = out.exercises[0]
    assert.equal(ex.photo, JPEG)
    assert.equal(ex.glyph, 'pecDeck'); assert.equal(ex.photoOffset, 0.5); assert.equal(ex.isUnilateral, true); assert.equal(ex.restSeconds, 90)
    assert.deepEqual(ex.records.map((r) => [r.uuid, r.isEstimated, r.weight]), [[U(101), false, 50], [U(201), true, 60]])
    assert.equal(out.exercises[1].records.length, 0)
    const sets = out.workouts[0].sets
    assert.deepEqual(sets.map((x) => x.isPersonalRecord), [true, false, false])
    assert.equal(sets[2].exerciseUUID, undefined, 'la serie senza esercizio resta senza')
    assert.equal(out.workouts[0].endedAt, '2026-09-10T10:30:00Z')
    assert.equal(out.bodyWeights?.length, 1)
    assert.equal(out.exportedAt, '2026-10-14T12:00:00Z')
    // senza chiavi inutili: l'app le legge come opzionali
    const text = serializeBackup(out)
    assert.ok(!text.includes('null'))
    assert.ok(!/\.\d{3}Z/.test(text), 'nessun millisecondo')
    assert.ok(!('glyph' in out.exercises[1]) && !('photo' in out.exercises[1]) && !('isUnilateral' in out.exercises[1]))
    // il file esportato si reimporta identico
    const again = planImport(parse(JSON.parse(text)), [], 'sostituisci')
    assert.equal(again.orfani, 0)
    assert.deepEqual(again.conteggi, plan.conteggi)
  } finally { done() }
})

test('backup vecchi senza i campi opzionali, date con millisecondi, uuid minuscoli', () => {
  const raw = JSON.parse(JSON.stringify(sample()))
  delete raw.bodyWeights
  for (const w of raw.workouts) { delete w.focus; delete w.name }
  for (const e of raw.exercises) { delete e.photo; delete e.glyph; delete e.photoOffset; delete e.isUnilateral; delete e.restSeconds }
  raw.exercises[0].createdAt = '2026-09-01T08:00:00.123Z'
  raw.exercises[0].uuid = raw.exercises[0].uuid.toLowerCase()
  raw.exercises[1].name = 'Pressa di casa'
  const f = parse(raw)
  assert.equal(f.exercises[0].createdAt, '2026-09-01T08:00:00Z')
  assert.equal(f.exercises[0].uuid, U(1))
  const plan = planImport(f, [], 'sostituisci')
  assert.equal(plan.conteggi.pesate, 0)
  assert.equal(plan.conteggi.allenamenti, 1)
  // collegato al catalogo per nome, oppure personalizzato con l'icona che il nome gli dava
  const ex = plan.put.filter((p) => p.kind === 'exercise').map((p) => p.data as { catalog: string; glyph: string })
  assert.deepEqual(ex.map((e) => [e.catalog, e.glyph]), [['chest-press-machine', ''], ['', 'legPress']])
})

test('schede e piano dell\'allenamento: round-trip, unione per nome, backup vecchi senza schede', () => {
  const { s, done } = store()
  try {
    const uid = s.upsertUser('s', 'a@b.it', 'a', 'A').id
    const f = sample()
    f.templates = [{ uuid: U(400), name: 'Push 1', sortIndex: 0, createdAt: '2026-09-20T08:00:00Z', items: [
      { exerciseUUID: U(1), sets: 4, repsMin: 8, repsMax: 10, weight: 60 },
      { exerciseUUID: U(2), sets: 3, repsMin: 10, repsMax: 12 },
      { exerciseUUID: U(999), sets: 3, repsMin: 10, repsMax: 12 }, // macchinario che non c'è: la riga sparisce
    ] }]
    f.programs = [{ uuid: U(500), name: 'Push Pull Legs', sortIndex: 0, createdAt: '2026-09-20T08:00:00Z', useDays: true }]
    f.templates[0].programID = U(500); f.templates[0].weekday = 2
    f.workouts[0].template = U(400)
    f.workouts[0].plan = [{ exerciseUUID: U(1), sets: 4, repsMin: 8, repsMax: 10, weight: 60 }]
    const plan = planImport(parse(f), [], 'sostituisci')
    assert.equal(plan.conteggi.schede, 1)
    s.applyPlan(uid, plan)
    const out = exportBackup(s.live(uid), photoOf(s, uid))
    assert.deepEqual(out.templates?.[0].items, [
      { exerciseUUID: U(1), sets: 4, repsMin: 8, repsMax: 10, weight: 60 },
      { exerciseUUID: U(2), sets: 3, repsMin: 10, repsMax: 12 },
    ])
    assert.deepEqual([out.programs?.[0].name, out.programs?.[0].useDays, out.templates?.[0].programID, out.templates?.[0].weekday], ['Push Pull Legs', true, U(500), 2])
    assert.equal(out.workouts[0].template, U(400))
    assert.equal(out.workouts[0].plan?.length, 1)
    const text = serializeBackup(out)
    assert.equal(planImport(parse(JSON.parse(text)), [], 'sostituisci').conteggi.schede, 1)
    // unione: la scheda già presente non si duplica
    assert.equal(planImport(parse(JSON.parse(text)), s.live(uid), 'unisci').conteggi.schede, 0)
    // un file vecchio, senza schede né piano, si legge come prima
    const old = JSON.parse(JSON.stringify(sample())); assert.equal(parse(old).templates?.length, 0)
    assert.equal(parse(old).workouts[0].plan, undefined)
  } finally { done() }
})

test('file non valido o di una versione più nuova', () => {
  assert.deepEqual(parseBackup({ foo: 1 }), { error: 'invalido' })
  assert.deepEqual(parseBackup('x'), { error: 'invalido' })
  assert.deepEqual(parseBackup({ version: 2, exercises: [], workouts: [] }), { error: 'versione', version: 2 })
  const bad = sample(); bad.exercises[0].uuid = 'non-un-uuid'
  assert.deepEqual(parseBackup(bad), { error: 'invalido' })
})

test('unisci due volte non duplica nulla; sostituisci azzera', () => {
  const { s, done } = store()
  try {
    const uid = s.upsertUser('s', 'a@b.it', 'a', 'A').id
    s.applyPlan(uid, planImport(parse(sample()), [], 'unisci'))
    const n = s.live(uid).length
    const second = planImport(parse(sample()), s.live(uid), 'unisci')
    assert.equal(second.put.length, 0)
    s.applyPlan(uid, second)
    assert.equal(s.live(uid).length, n)
    // sostituisci con un file piccolo: il resto sparisce (tombstone), le impostazioni restano
    s.setSetting(uid, 'unit', 'lb')
    const small = sample(); small.exercises = [small.exercises[1]]; small.workouts = []; small.bodyWeights = []
    s.applyPlan(uid, planImport(parse(small), s.live(uid), 'sostituisci'))
    const live = s.live(uid)
    assert.deepEqual(live.map((d) => d.kind).sort(), ['exercise', 'setting'])
  } finally { done() }
})

test('unisci: stesso nome ma uuid diverso → un solo macchinario, serie e foto riportate', () => {
  const { s, done } = store()
  try {
    const uid = s.upsertUser('s', 'a@b.it', 'a', 'A').id
    // il catalogo del web: "Chest Press" con un altro uuid, senza foto
    s.applyPlan(uid, { photos: new Map(), del: [], put: [{ kind: 'exercise', uuid: U(900), data: {
      name: 'chest press ', muscleGroup: 'petto', notes: '', incrementStep: 5, sortIndex: 0, isArchived: false,
      createdAt: '2026-01-01T00:00:00Z', glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 0 } }] })
    const plan = planImport(parse(sample()), s.live(uid), 'unisci')
    s.applyPlan(uid, plan)
    const d = s.live(uid)
    assert.equal(d.filter((x) => x.kind === 'exercise').length, 2, 'Chest Press unito, Lat Machine nuovo')
    assert.ok(!d.some((x) => x.kind === 'exercise' && x.uuid === U(1)))
    const sets = d.filter((x) => x.kind === 'set')
    assert.deepEqual(sets.map((x) => (x.data as { exercise: string | null }).exercise).sort(), [null, U(900), U(900)].sort())
    const merged = d.find((x) => x.uuid === U(900))!.data as { photo: string }
    assert.match(merged.photo, /^[0-9a-f]{64}$/, 'la foto del file è copiata sull\'esistente')
    assert.ok(s.getPhoto(uid, merged.photo))
    assert.equal(d.filter((x) => x.kind === 'record').length, 1, 'il massimale manuale è riportato')
    // seconda unione: niente doppioni nemmeno senza uuid in comune per i massimali
    const again = planImport(parse(sample()), s.live(uid), 'unisci')
    assert.equal(again.put.filter((p) => p.kind === 'record').length, 0)
  } finally { done() }
})

test('l\'allenamento eliminato non lascia record; il record automatico segue le serie', () => {
  const { s, done } = store()
  try {
    const uid = s.upsertUser('s', 'a@b.it', 'a', 'A').id
    s.applyPlan(uid, planImport(parse(sample()), [], 'sostituisci'))
    s.push(uid, [{ kind: 'workout', uuid: U(10), data: null, m: Date.now() + 1000, deleted: true }])
    const out = exportBackup(s.live(uid), () => null)
    assert.equal(out.workouts.length, 0)
    assert.ok(out.exercises.every((e) => e.records.every((r) => !r.isEstimated)), 'nessun record automatico senza serie')
  } finally { done() }
})

test('figura dei muscoli: il macchinario personalizzato la tiene nel backup', () => {
  const { s, done } = store()
  try {
    const uid = s.upsertUser('sub-m', 'm@b.it', 'm', 'M').id
    const file = sample()
    file.exercises[1].muscles = ['deltoids']
    file.exercises[1].view = 'back'
    s.applyPlan(uid, planImport(parse(file), [], 'sostituisci'))
    const out = exportBackup(s.live(uid), photoOf(s, uid), Date.parse('2026-10-14T12:00:00Z'))
    const lat = out.exercises.find((e) => e.name === 'Lat Machine')!
    assert.deepEqual(lat.muscles, ['deltoids'])
    assert.equal(lat.view, 'back')
    assert.equal(out.exercises.find((e) => e.name === 'Chest Press')!.muscles, undefined, 'nessun muscolo: niente campo')
  } finally { done() }
})

test('un macchinario personalizzato non si ricollega al catalogo per nome; nel catalogo sì', () => {
  const f = sample()
  f.exercises = [
    { ...f.exercises[0], name: 'Leg Press', isCustom: true, records: [] },
    { ...f.exercises[1], name: 'Leg Press', records: [] },
  ]
  f.workouts = []
  const [custom, linked] = planImport(parse(f), [], 'sostituisci').put.filter((p) => p.kind === 'exercise').map((p) => p.data as { catalog: string; custom: boolean })
  assert.equal(custom.catalog, '')
  assert.equal(custom.custom, true)
  assert.notEqual(linked.catalog, '', 'senza isCustom un backup vecchio si ricollega per nome')
})

test('sostituisci: valori fuori scala e testi lunghi si riportano nei limiti, nessun documento scartato', () => {
  const f = sample()
  f.exercises[0].name = 'N'.repeat(200); f.exercises[0].notes = 'n'.repeat(5000); f.exercises[0].incrementStep = 0; f.exercises[0].restSeconds = 99999
  f.exercises[0].records[0].weight = 5000; f.exercises[0].records[0].reps = 5000
  f.workouts[0].note = 'x'.repeat(3000); f.workouts[0].name = 'y'.repeat(300); f.workouts[0].focus = 'boh'
  f.workouts[0].sets[0].weight = 9999; f.workouts[0].sets[0].rpe = 15
  f.bodyWeights![0].weight = 5
  const plan = planImport(parse(f), [], 'sostituisci')
  assert.equal(plan.scartati, 0)
  assert.deepEqual(plan.conteggi, { esercizi: 2, allenamenti: 1, serie: 3, record: 1, pesate: 1, schede: 0 })
  const ex = plan.put.find((p) => p.kind === 'exercise')!.data as { name: string; notes: string; incrementStep: number; restSeconds: number }
  assert.deepEqual([ex.name.length, ex.notes.length, ex.incrementStep, ex.restSeconds], [80, 2000, 0.01, 3600])
})
