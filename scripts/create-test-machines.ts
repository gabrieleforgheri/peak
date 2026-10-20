// Script per creare i macchinari e i dati di test per verificare le nuove funzionalità
import { openStore, type WireDoc } from '../server/store.ts'
import { isoSec } from '../common/docs.ts'

const dataDir = process.argv[2] ?? 'data'
const store = openStore(dataDir)
const user = store.userBySub('dev')
if (!user) {
  console.error("Utente dev non trovato! Avvia prima seed-dev.")
  process.exit(1)
}

const now = Date.now()
const DAY = 86_400_000
let n = 1000
const uuid = () => `00000000-0000-4000-9000-${(++n).toString(16).toUpperCase().padStart(12, '0')}`
const iso = (ms: number) => isoSec(ms)

const docs: WireDoc[] = []
const put = (kind: WireDoc['kind'], id: string, data: unknown) => docs.push({ kind, uuid: id, data, m: now, deleted: false })

// 1. Macchinario con storico < 30 giorni (Range dinamico: solo "Tutto")
const exShort = uuid()
put('exercise', exShort, {
  name: 'Bicipiti Scott (Test < 30gg)', muscleGroup: 'braccia', notes: 'Test selettore andamento: storico < 30gg, solo Tutto',
  incrementStep: 1.25, sortIndex: 20, isArchived: false, createdAt: iso(now - 15 * DAY),
  glyph: 'preacherBench', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 90,
  catalog: 'bicep-curl-machine', hidden: false,
})
// Record di 14 giorni fa e oggi
put('record', uuid(), {
  exercise: exShort, date: iso(now - 14 * DAY), weight: 30, reps: 10, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exShort, date: iso(now - 2 * DAY), weight: 32.5, reps: 8, isEstimated: false, note: '',
})

// 2. Macchinario con storico ~45 giorni (Range dinamico: "1 mese" e "Tutto")
const exMonth = uuid()
put('exercise', exMonth, {
  name: 'Leg Extension (Test 45gg)', muscleGroup: 'gambe', notes: 'Test selettore andamento: storico 45gg, mostra 1 mese e Tutto',
  incrementStep: 2.5, sortIndex: 21, isArchived: false, createdAt: iso(now - 50 * DAY),
  glyph: 'legLever', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 90,
  catalog: 'leg-extension', hidden: false,
})
put('record', uuid(), {
  exercise: exMonth, date: iso(now - 45 * DAY), weight: 45, reps: 12, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exMonth, date: iso(now - 20 * DAY), weight: 50, reps: 10, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exMonth, date: iso(now - 2 * DAY), weight: 55, reps: 10, isEstimated: false, note: '',
})

// 3. Macchinario con storico ~120 giorni (Range dinamico: "1 mese", "3 mesi", "Tutto")
const exThreeMonths = uuid()
put('exercise', exThreeMonths, {
  name: 'Calf Machine (Test 120gg)', muscleGroup: 'gambe', notes: 'Test selettore andamento: storico 120gg, mostra 1m, 3m e Tutto',
  incrementStep: 5, sortIndex: 22, isArchived: false, createdAt: iso(now - 130 * DAY),
  glyph: 'calfPlatform', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 60,
  catalog: 'standing-calf-raise-machine', hidden: false,
})
put('record', uuid(), {
  exercise: exThreeMonths, date: iso(now - 120 * DAY), weight: 60, reps: 15, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exThreeMonths, date: iso(now - 60 * DAY), weight: 70, reps: 12, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exThreeMonths, date: iso(now - 5 * DAY), weight: 80, reps: 10, isEstimated: false, note: '',
})

// 4. Macchinario con storico > 1 anno (Range dinamico: "3 mesi", "1 anno", "Tutto")
const exYear = uuid()
put('exercise', exYear, {
  name: 'Trazioni Sbarra (Test > 1 anno)', muscleGroup: 'schiena', notes: 'Test selettore andamento: storico > 1 anno, mostra 3m, 1y e Tutto',
  incrementStep: 1, sortIndex: 23, isArchived: false, createdAt: iso(now - 420 * DAY),
  glyph: 'pullUpBar', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 120,
  catalog: 'pull-up', hidden: false,
})
put('record', uuid(), {
  exercise: exYear, date: iso(now - 400 * DAY), weight: 75, reps: 6, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exYear, date: iso(now - 200 * DAY), weight: 80, reps: 8, isEstimated: false, note: '',
})
put('record', uuid(), {
  exercise: exYear, date: iso(now - 40 * DAY), weight: 85, reps: 7, isEstimated: false, note: '',
})

// 5. Macchinario per Test PR in modifica serie
const exPR = uuid()
put('exercise', exPR, {
  name: 'Test Macchinario PR', muscleGroup: 'petto', notes: 'Modifica la serie da 60kg a 75kg per vedere il badge verde NUOVO RECORD',
  incrementStep: 2.5, sortIndex: 24, isArchived: false, createdAt: iso(now - 30 * DAY),
  glyph: 'seatedPush', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 90,
  catalog: 'chest-press-machine', hidden: false,
})
// Record manuale precedente di 70kg x 5
put('record', uuid(), {
  exercise: exPR, date: iso(now - 10 * DAY), weight: 70, reps: 5, isEstimated: false, note: 'Record precedente di riferimento',
})

// 6. Allenamento di ieri per popolare le "Serie dello scorso allenamento" su Test Macchinario PR
const pastWorkoutId = uuid()
put('workout', pastWorkoutId, {
  date: iso(now - 1 * DAY), endedAt: iso(now - 1 * DAY + 50 * 60_000), note: 'Workout di ieri per test serie precedenti',
  name: 'Push Test', focus: 'petto', template: null,
  plan: [{ exercise: exPR, sets: 3, repsMin: 8, repsMax: 10, weight: 65 }],
})
// Serie di ieri: 1 riscaldamento (40kg x 10) e 2 serie di lavoro (60kg x 10, 65kg x 8)
put('set', uuid(), {
  workout: pastWorkoutId, exercise: exPR, weight: 40, reps: 10, isWarmup: true,
  order: 0, createdAt: iso(now - 1 * DAY + 10 * 60_000), noRecord: false,
})
put('set', uuid(), {
  workout: pastWorkoutId, exercise: exPR, weight: 60, reps: 10, isWarmup: false,
  order: 1, createdAt: iso(now - 1 * DAY + 15 * 60_000), noRecord: false,
})
put('set', uuid(), {
  workout: pastWorkoutId, exercise: exPR, weight: 65, reps: 8, isWarmup: false,
  order: 2, createdAt: iso(now - 1 * DAY + 20 * 60_000), noRecord: false,
})

// 7. Allenamento ATTIVO di oggi in corso!
const liveWorkoutId = uuid()
put('workout', liveWorkoutId, {
  date: iso(now - 20 * 60_000), endedAt: null, note: 'Sessione di test in corso',
  name: 'Sessione Test Funzionalità', focus: 'petto,braccia,gambe', template: null,
  plan: [
    { exercise: exPR, sets: 3, repsMin: 8, repsMax: 10, weight: 65 },
    { exercise: exShort, sets: 3, repsMin: 10, repsMax: 12, weight: 30 },
  ],
})
// Nel workout attivo di oggi inseriamo una serie da 60kg x 5 per Test Macchinario PR:
// Modificandola a 75kg scatta il PR!
put('set', uuid(), {
  workout: liveWorkoutId, exercise: exPR, weight: 60, reps: 5, isWarmup: false,
  order: 0, createdAt: iso(now - 10 * 60_000), noRecord: false,
})

const r = store.push(user.id, docs)
console.log(`Caricati ${docs.length} documenti di test! Scartati:`, r.scartati)
store.close()
