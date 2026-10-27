// Popola l'account dev locale con esercizi dal catalogo, schede e 8 settimane di allenamenti per testare l'interfaccia.
// Uso: node scripts/seed-dev.ts [cartellaDati]
import { openStore, type WireDoc } from '../server/store.ts'
import { isoSec } from '../common/docs.ts'

const dataDir = process.argv[2] ?? 'data'
const store = openStore(dataDir)
process.env.TZ = 'Europe/Rome'

const DAY = 86_400_000
const now = Date.now()
const today = new Date(now)
today.setHours(0, 0, 0, 0)
const monday = today.getTime() - ((today.getDay() + 6) % 7) * DAY

let n = 0
const uuid = () => `00000000-0000-4000-8000-${(++n).toString(16).toUpperCase().padStart(12, '0')}`
const iso = (ms: number) => isoSec(ms)

type Ex = { id: string; catalog: string; name: string; group: string; glyph: string; start: number; step: number; reps: number }
const ex = (catalog: string, name: string, group: string, glyph: string, start: number, step: number, reps: number): Ex =>
  ({ id: uuid(), catalog, name, group, glyph, start, step, reps })

function seed(sub: string, username: string, name: string, scale: number, days: number[]) {
  const user = store.upsertUser(sub, `${sub}@example.com`, username, name)
  const E = {
    panca: ex('bench-press-barbell', 'Panca Piana Bilanciere', 'petto', 'benchFlat', 60, 2.5, 8),
    spalle: ex('shoulder-press-machine', 'Shoulder Press', 'spalle', 'seatedPush', 35, 2.5, 10),
    corda: ex('triceps-pushdown-rope', 'Pushdown con Corda', 'braccia', 'cableRope', 20, 1.25, 12),
    lat: ex('lat-pulldown', 'Lat Machine', 'schiena', 'latPulldown', 55, 2.5, 10),
    pulley: ex('seated-row-wide-grip', 'Pulley Presa Larga', 'schiena', 'seatedRow', 50, 2.5, 10),
    curl: ex('barbell-curl', 'Curl Bilanciere', 'braccia', 'barbell', 25, 1.25, 10),
    press: ex('leg-press-45', 'Leg Press 45°', 'gambe', 'legPress', 140, 5, 10),
    squat: ex('squat-barbell', 'Squat con Bilanciere', 'gambe', 'squatRack', 70, 2.5, 6),
    legcurl: ex('lying-leg-curl', 'Leg Curl Sdraiato', 'gambe', 'legCurlLying', 35, 2.5, 12),
    hip: ex('hip-thrust', 'Hip Thrust', 'gambe', 'hipThrust', 80, 5, 10),
  }
  const all = Object.values(E)
  const docs: WireDoc[] = []
  const put = (kind: WireDoc['kind'], id: string, data: unknown) => docs.push({ kind, uuid: id, data, m: now - 60_000, deleted: false })
  all.forEach((e, i) => put('exercise', e.id, {
    name: e.name, muscleGroup: e.group, notes: '', incrementStep: e.step, sortIndex: i, isArchived: false,
    createdAt: iso(monday - 60 * DAY), glyph: e.glyph, photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 0,
    catalog: e.catalog, hidden: false,
  }))
  const plans = [
    { name: 'Push', items: [E.panca, E.spalle, E.corda] },
    { name: 'Pull', items: [E.lat, E.pulley, E.curl] },
    { name: 'Gambe', items: [E.press, E.squat, E.legcurl, E.hip] },
  ].map((p, i) => ({ ...p, id: uuid(), sort: i }))
  for (const p of plans) put('template', p.id, {
    name: p.name, sortIndex: p.sort, createdAt: iso(monday - 50 * DAY), hidden: false,
    items: p.items.map((e) => ({ exercise: e.id, sets: 3, repsMin: e.reps - 2, repsMax: e.reps + 2, weight: null })),
  })
  for (const [k, v] of Object.entries({ weeklyGoal: 4, tutorialDone: true, tourSeen: 3, accent: 'lime', unit: 'kg' })) put('setting', k, { value: v })

  const kg = (e: Ex, week: number) => Math.round(((e.start + e.step * Math.floor(week / 2)) * scale) / e.step) * e.step
  let w = 0
  for (let week = 0; week <= 8; week++) {
    for (const d of days) {
      const start = monday - (8 - week) * 7 * DAY + d * DAY + 18.5 * 3600_000
      if (start > now) continue
      const p = plans[w++ % plans.length]
      const id = uuid()
      put('workout', id, {
        date: iso(start), endedAt: iso(start + 62 * 60_000), note: '', name: p.name, focus: '', template: p.id,
        plan: p.items.map((e) => ({ exercise: e.id, sets: 3, repsMin: e.reps - 2, repsMax: e.reps + 2, weight: kg(e, week) })),
      })
      let order = 0
      p.items.forEach((e, i) => {
        const top = kg(e, week) + (week === 8 && i === 0 ? e.step * 2 : 0)
        for (let s = 0; s < 3; s++) put('set', uuid(), {
          workout: id, exercise: e.id, weight: top, reps: e.reps - (s === 2 ? 1 : 0), rpe: null, isWarmup: false,
          order: order++, createdAt: iso(start + (i * 15 + s * 4 + 3) * 60_000), noRecord: false,
        })
      })
    }
  }
  for (let week = 0; week <= 8; week++) put('bodyweight', uuid(), { date: iso(monday - (8 - week) * 7 * DAY + 7.5 * 3600_000), weight: Math.round((75.0 - week * 0.3) * scale * 10) / 10, fromHealth: false })
  const r = store.push(user.id, docs)
  if (r.scartati.length) console.warn(`${username}: scartati`, r.scartati)
  return user
}

const dev = seed('dev', 'dev', 'Dev', 1, [0, 1, 3, 5])
const marco = seed('dev-marco', 'marco', 'Marco', 1.08, [0, 2, 4])
const sara = seed('dev-sara', 'sara', 'Sara', 0.9, [1, 3, 5])

for (const f of [marco, sara]) {
  try {
    store.db.prepare("INSERT OR IGNORE INTO friends (a, b, status, created_at) VALUES (?, ?, 'amici', ?)").run(dev.id, f.id, now)
  } catch {}
}

const session = store.createSession(dev.id, 30)
console.log(`Account dev pronto! Sessione creata: ${session}`)
store.close()
