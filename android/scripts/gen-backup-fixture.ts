// Genera android/app/src/test/resources/backup-server.json: un backup nel formato dell'iPhone (BackupDTO.swift), scritto dal vero
// server/backup.ts (importa un file di prova, poi lo riesporta: record automatici, flag PR, chiavi ordinate, date a secondi).
// Le prove Android lo reimportano e lo riesportano e vogliono lo stesso file.
// Uso (dalla radice del repo): node android/scripts/gen-backup-fixture.ts
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { openStore } from '../../server/store.ts'
import { exportBackup, parseBackup, planImport, serializeBackup, type BackupFile } from '../../server/backup.ts'

const U = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xdb, 1, 2, 3, 4]).toString('base64')

const file: BackupFile = {
  version: 1, exportedAt: '2026-10-07T16:54:28Z',
  exercises: [
    { uuid: U(1), name: 'Chest Press', muscleGroup: 'petto', notes: 'sedile 4', incrementStep: 5, sortIndex: 0, isArchived: false, createdAt: '2026-09-01T08:00:00Z',
      photo: JPEG, photoOffset: 0.5, isUnilateral: true, restSeconds: 90, glyph: 'pecDeck', limbs: 'either', barWeight: 20, plateLoaded: true,
      records: [
        { uuid: U(101), date: '2026-09-02T09:00:00Z', weight: 50, reps: 5, isEstimated: false, note: 'primo' },
        { uuid: U(102), date: '2026-09-03T09:00:00Z', weight: 30, reps: 8, isEstimated: false, note: '', singleArm: true },
      ] },
    { uuid: U(2), name: 'Lat Machine', muscleGroup: 'schiena', notes: '', incrementStep: 2.5, sortIndex: 1, isArchived: true, createdAt: '2026-09-01T08:00:00Z',
      isCustom: true, muscles: ['deltoids'], view: 'back', weightDisplay: 'relative', perSide: true, records: [] },
    { uuid: U(3), name: 'Pressa di casa', muscleGroup: 'gambe', notes: '', incrementStep: 10, sortIndex: 2, isArchived: false, createdAt: '2026-09-05T08:00:00Z', isHidden: true, records: [] },
  ],
  workouts: [
    { uuid: U(10), date: '2026-09-10T09:30:00Z', endedAt: '2026-09-10T10:30:00Z', note: 'ok', focus: 'petto', name: 'Push', template: U(400),
      plan: [{ exerciseUUID: U(1), sets: 4, repsMin: 8, repsMax: 10, weight: 60 }, { exerciseUUID: U(2), sets: 3, repsMin: 10, repsMax: 12 }],
      sets: [
        { uuid: U(201), exerciseUUID: U(1), weight: 60, reps: 5, isWarmup: false, order: 0, createdAt: '2026-09-10T10:00:00Z', isPersonalRecord: false },
        { uuid: U(202), exerciseUUID: U(1), weight: 20, reps: 10, isWarmup: true, order: 1, createdAt: '2026-09-10T10:05:00Z', isPersonalRecord: false },
        { uuid: U(203), exerciseUUID: U(1), weight: 35, reps: 8, isWarmup: false, order: 2, createdAt: '2026-09-10T10:07:00Z', isPersonalRecord: false, singleArm: true },
        { uuid: U(204), weight: 30, reps: 8, isWarmup: false, order: 3, createdAt: '2026-09-10T10:06:00Z', isPersonalRecord: false },
        { uuid: U(205), exerciseUUID: U(2), weight: 99, reps: 9, isWarmup: false, order: 4, createdAt: '2026-09-10T10:08:00Z', isPersonalRecord: false, excludedFromRecords: true, rpe: 8.5 },
      ] },
    { uuid: U(11), date: '2026-09-17T09:30:00Z', endedAt: '2026-09-17T10:15:00Z', note: '', focus: '', name: '', sets: [
      { uuid: U(211), exerciseUUID: U(1), weight: 65, reps: 5, isWarmup: false, order: 0, createdAt: '2026-09-17T10:00:00Z', isPersonalRecord: false },
    ] },
  ],
  bodyWeights: [{ uuid: U(300), date: '2026-09-11T07:00:00Z', weight: 78.4, fromHealth: false }, { uuid: U(301), date: '2026-09-18T07:00:00Z', weight: 78.1, fromHealth: true }],
  templates: [{ uuid: U(400), name: 'Push 1', sortIndex: 0, createdAt: '2026-09-20T08:00:00Z', icon: 'flame', programID: U(500), weekday: 2, items: [
    { exerciseUUID: U(1), sets: 4, repsMin: 8, repsMax: 10, weight: 60 }, { exerciseUUID: U(2), sets: 3, repsMin: 10, repsMax: 12 },
  ] }],
  programs: [{ uuid: U(500), name: 'Push Pull Legs', sortIndex: 0, createdAt: '2026-09-20T08:00:00Z', useDays: true }],
}

const dir = mkdtempSync(join(tmpdir(), 'peak-fixture-'))
try {
  const store = openStore(dir)
  const uid = store.upsertUser('fixture', 'f@example.com', 'f', 'F').id
  const parsed = parseBackup(file)
  if (!('file' in parsed)) throw new Error(JSON.stringify(parsed))
  store.applyPlan(uid, planImport(parsed.file, [], 'sostituisci'))
  const out = exportBackup(store.live(uid), (sha) => store.getPhoto(uid, sha), Date.parse('2026-10-14T12:00:00Z'))
  const target = fileURLToPath(new URL('../app/src/test/resources/backup-server.json', import.meta.url))
  writeFileSync(target, serializeBackup(out) + '\n')
  console.log(`scritto ${target}`)
  store.close()
} finally { rmSync(dir, { recursive: true }) }
