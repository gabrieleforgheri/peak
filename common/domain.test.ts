import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  oneRepMax, beats, warmupPlan, nearest, nextLoad, progressionFor, loadPlates, plateMode, toDisplay, toKg,
  displayStep, defaultPlates, defaultBar, settingsForUnitChange, PhotoFraming, KG_PER_LB,
} from './domain.ts'
import { suggestedGlyph, SHAPES, GLYPH_TITLES, glyphSvg } from './glyph.ts'
import { GLYPHS } from './docs.ts'
import { readFileSync } from 'node:fs'
import type { CatalogEntry } from './catalog.ts'
import { weeklyVolume, volumeByGroup, topMovers, tickIndices, weeklyStreak, weekStart, weeklyGoal } from './stats.ts'
import * as fmt from './format.ts'

const CATALOG = JSON.parse(readFileSync(new URL('./catalog.json', import.meta.url), 'utf8')) as CatalogEntry[]
const near = (a: number, b: number, eps = 0.01) => assert.ok(Math.abs(a - b) <= eps, `${a} ≠ ${b}`)

test('1RM', () => {
  assert.equal(oneRepMax(100, 1), 100)
  near(oneRepMax(100, 5), 116.666)
  near(oneRepMax(80, 10), 106.666)
  assert.equal(oneRepMax(0, 5), 0)
  assert.equal(oneRepMax(100, 0), 0)
  assert.equal(oneRepMax(100, -3), 0)
  let prev = 0
  for (let r = 1; r <= 50; r++) { const v = oneRepMax(60, r); assert.ok(v > prev && v < 1000); prev = v }
})

test('confronto dei massimali', () => {
  assert.ok(beats(100, 1, null))
  assert.ok(beats(85, 3, { weight: 80, reps: 8 }))
  assert.ok(beats(80, 8, { weight: 80, reps: 5 }))
  assert.ok(!beats(80.005, 5, { weight: 80, reps: 5 }))
  assert.ok(!beats(0, 5, null))
})

test('riscaldamento', () => {
  const p = warmupPlan(100, 5)
  assert.deepEqual(p.map((s) => s.reps), [10, 6, 3])
  assert.deepEqual(p.map((s) => s.weight), [40, 60, 80])
  assert.deepEqual(warmupPlan(119, 10).map((s) => s.weight), [49, 69, 99])
  assert.deepEqual(warmupPlan(120, 2.5).map((s) => s.weight), [47.5, 72.5, 95])
  for (const w of warmupPlan(50, 3.75)) {
    const steps = (50 - w.weight) / 3.75
    near(steps, Math.round(steps), 0.0001)
    assert.ok(Math.abs(w.weight - 50 * w.percent) <= 3.75 / 2 + 0.0001)
  }
  const small = warmupPlan(8, 5)
  assert.equal(small[0].weight, 3)
  assert.ok(small.every((s) => s.weight > 0))
  assert.equal(nearest(0.5, 60, 3.75), 3.75)
  assert.deepEqual(warmupPlan(null, 5), [])
  assert.deepEqual(warmupPlan(0, 5), [])
  for (let base = 10; base <= 300; base += 7.5) for (const step of [1, 2.5, 5, 10]) {
    const w = warmupPlan(base, step).map((s) => s.weight)
    assert.deepEqual(w, [...w].sort((a, b) => a - b))
  }
})

test('doppia progressione', () => {
  const range: [number, number] = [8, 12]
  assert.deepEqual(nextLoad([{ weight: 60, reps: 12 }, { weight: 60, reps: 12 }, { weight: 60, reps: 13 }], 2.5, range), { weight: 62.5, reps: 8 })
  assert.deepEqual(nextLoad([{ weight: 60, reps: 12 }, { weight: 60, reps: 10 }], 2.5, range), { weight: 60, reps: 12 })
  assert.deepEqual(nextLoad([{ weight: 60, reps: 12 }, { weight: 50, reps: 6 }], 2.5, range), { weight: 62.5, reps: 8 })
  assert.deepEqual(nextLoad([{ weight: 80, reps: 5 }], 5, range), { weight: 80, reps: 8 })
  assert.equal(nextLoad([], 2.5, range), null)
})

test('progressione: solo l\'ultima sessione conclusa, senza riscaldamento', () => {
  const s = (workout: string, workoutDate: number, weight: number, reps: number, over: Partial<{ isWarmup: boolean; workoutEnded: boolean }> = {}) =>
    ({ weight, reps, isWarmup: false, workout, workoutDate, workoutEnded: true, ...over })
  const sets = [s('A', 1, 50, 12), s('B', 2, 60, 12), s('B', 2, 20, 20, { isWarmup: true }), s('C', 3, 90, 5, { workoutEnded: false })]
  assert.deepEqual(progressionFor(sets, 2.5, [8, 12]), { weight: 62.5, reps: 8 })
  assert.equal(progressionFor([s('C', 3, 90, 5, { workoutEnded: false })], 2.5, [8, 12]), null)
})

test('dischi', () => {
  const standard = [25, 20, 15, 10, 5, 2.5, 1.25]
  assert.deepEqual(loadPlates(102.5, 20, standard), { perSide: [25, 15, 1.25], leftover: 0 })
  assert.deepEqual(loadPlates(90, 0, standard).perSide, [25, 20])
  assert.deepEqual(loadPlates(65, 20, [20, 10, 5]), { perSide: [20], leftover: 2.5 })
  assert.deepEqual(loadPlates(20, 20, standard).perSide, [])
  assert.deepEqual(loadPlates(15, 20, standard).perSide, [])
})

test('calcolatore dischi: bilanciere e plate loaded, non manubri né multipower', () => {
  assert.deepEqual(plateMode('barbell', 'Stacco da Terra'), { bar: true })
  assert.deepEqual(plateMode('benchFlat', 'Panca Piana Bilanciere'), { bar: true })
  assert.deepEqual(plateMode('benchIncline', 'Panca Inclinata Bilanciere'), { bar: true })
  assert.deepEqual(plateMode('squatRack', 'Squat con Bilanciere'), { bar: true })
  assert.deepEqual(plateMode('plateLoaded', 'Chest Press Plate Loaded'), { bar: false })
  assert.equal(plateMode('benchFlat', 'Panca Piana Manubri'), null)
  assert.equal(plateMode('benchIncline', 'Panca Inclinata Manubri'), null)
  assert.equal(plateMode('smithMachine', 'Squat al Multipower'), null)
  assert.equal(plateMode('legPress', 'Leg Press 45°'), null)
  // i nomi veri del catalogo: nessun manubri riceve il calcolatore
  for (const e of CATALOG) {
    const g = suggestedGlyph(e.it, e.group)
    if (e.it.toLowerCase().includes('manubri')) assert.equal(plateMode(g, e.it), null, e.it)
  }
})

test('unità: conversione e passi nativi in lb', () => {
  near(toDisplay(82.5, 'lb'), 181.88)
  near(toKg(toDisplay(82.5, 'lb'), 'lb'), 82.5, 0.0001)
  assert.equal(displayStep(2.5, 'kg'), 2.5)
  assert.equal(displayStep(2.5, 'lb'), 5)
  assert.equal(displayStep(1.25, 'lb'), 2.5)
  assert.equal(displayStep(10, 'lb'), 20)
  assert.equal(displayStep(0.5, 'lb'), 2.5)
  assert.equal(displayStep(7.5, 'lb'), 17.5)
  // dischi lb salvati in kg e riletti in lb
  assert.deepEqual(defaultPlates('lb').map((p) => Math.round(toDisplay(p, 'lb') * 100) / 100), [45, 35, 25, 10, 5, 2.5])
  near(defaultBar('lb'), 45 * KG_PER_LB, 1e-9)
})

test('cambio unità: dischi e bilanciere di serie seguono, quelli personalizzati restano', () => {
  const a = settingsForUnitChange('kg', 'lb', defaultPlates('kg'), defaultBar('kg'))
  assert.deepEqual(a.availablePlates, defaultPlates('lb'))
  near(a.barWeight, defaultBar('lb'), 1e-9)
  const b = settingsForUnitChange('kg', 'lb', [20, 10], 15)
  assert.deepEqual(b, { availablePlates: [20, 10], barWeight: 15 })
})

test('inquadratura foto', () => {
  near(PhotoFraming.overflow(4 / 3, 100), 33.33)
  near(PhotoFraming.overflow(3 / 4, 100), 33.33)
  near(PhotoFraming.offsetPoints(1, 4 / 3, 100), 16.67)
  near(PhotoFraming.offsetPoints(-1, 4 / 3, 100), -16.67)
  near(PhotoFraming.offsetPoints(5, 4 / 3, 100), 16.67)
  assert.equal(PhotoFraming.overflow(1, 100), 0)
  assert.equal(PhotoFraming.normalizedDelta(10, 1, 100), 0)
  near(PhotoFraming.normalizedDelta(16.67, 4 / 3, 100), 1)
  assert.equal(PhotoFraming.overflow(0, 100), 0)
})

test('pittogrammi', () => {
  const g = suggestedGlyph
  assert.equal(g('Leg Press 45°', 'gambe'), 'legPress')
  assert.equal(g('Lat Machine', 'schiena'), 'latPulldown')
  assert.equal(g('Pulley Basso', 'schiena'), 'seatedRow')
  assert.equal(g('Pectoral Machine', 'petto'), 'pecDeck')
  assert.equal(g('Croci ai Cavi', 'petto'), 'cableCross')
  assert.equal(g('Sedia Romana', 'core'), 'romanChair')
  assert.equal(g('Squat al Multipower', 'gambe'), 'smithMachine')
  assert.equal(g('Rematore Machine', 'schiena'), 'seatedRow')
  assert.equal(g('Rematore Bilanciere', 'schiena'), 'barbell')
  assert.equal(g('Rematore con Manubrio', 'schiena'), 'dumbbell')
  assert.equal(g('Rematore ai Cavi', 'schiena'), 'cableStation')
  assert.equal(g('Hack Squat', 'gambe'), 'legPress')
  assert.equal(g('Squat con Bilanciere', 'gambe'), 'squatRack')
  assert.equal(g('Chest Press Plate Loaded', 'petto'), 'plateLoaded')
  assert.equal(g('LEG PRESS', 'gambe'), 'legPress')
  assert.equal(g('lat machine presa stretta', 'schiena'), 'latPulldown')
  assert.equal(g('Attrezzo Sconosciuto', 'core'), 'bodyweight')
  assert.equal(g('Attrezzo Sconosciuto', 'gambe'), 'legLever')
  assert.equal(g('Attrezzo Sconosciuto', 'schiena'), 'seatedRow')
  assert.equal(g('', 'petto'), 'seatedPush')
  for (const e of CATALOG) { const x = g(e.it, e.group); assert.equal(x, e.glyph, e.it); assert.ok(SHAPES[x].length > 0, e.it); assert.ok(GLYPH_TITLES[x]) }
  assert.equal(GLYPHS.length, 22)
  for (const name of GLYPHS) {
    for (const s of SHAPES[name]) {
      const n = s as number[] | (string | number)[]
      const [, a, b, c2, d] = n as number[]
      const c = s[0] === 'rect' ? [a, b, a + c2, b + d]
        : s[0] === 'dot' || s[0] === 'ring' ? [a - c2, b - c2, a + c2, b + c2]
          : [a, b, c2, d]
      for (const v of c) assert.ok(v >= 0 && v <= 1, `${name} esce dal riquadro`)
    }
    assert.ok(glyphSvg(name).startsWith('<'))
  }
})

test('statistiche', () => {
  const now = Date.now()
  const DAY = 86_400_000
  const wk = (date: number, sets: { exercise: string | null; weight: number; reps: number; isWarmup: boolean }[]) => ({ date, sets })
  const weekly = weeklyVolume([wk(now, [{ exercise: 'E', weight: 100, reps: 5, isWarmup: false }])], 8, now)
  assert.equal(weekly.length, 1)
  near(weekly[0].volume, 500, 0.001)
  const groups = volumeByGroup(
    [wk(now, [{ exercise: 'C', weight: 60, reps: 10, isWarmup: false }, { exercise: 'C', weight: 20, reps: 20, isWarmup: true }])],
    () => 'petto', 30, now)
  assert.deepEqual(groups, [{ group: 'petto', volume: 600 }])
  const movers = topMovers([
    { id: 'a', records: [{ weight: 40, date: now - 120 * DAY }, { weight: 60, date: now - 3 * DAY }] },
    { id: 'b', records: [{ weight: 50, date: now - 120 * DAY }, { weight: 55, date: now - 3 * DAY }] },
    { id: 'c', records: [{ weight: 70, date: now - 120 * DAY }] },
    { id: 'd', records: [{ weight: 30, date: now - 2 * DAY }] },
  ], 90, 5, now)
  assert.deepEqual(movers.map((m) => m.exercise), ['d', 'a', 'b'])
  near(movers[1].deltaPercent, 50)
  near(movers[2].deltaPercent, 10)
  assert.equal(movers[0].deltaPercent, 100)
  assert.equal(weeklyStreak([{ date: now }, { date: now - 7 * DAY }, { date: now - 14 * DAY }, { date: now - 28 * DAY }], now), 3)
  assert.equal(weeklyStreak([{ date: now - 7 * DAY }], now), 0)
  assert.equal(new Date(weekStart(now)).getDay(), 1)
  assert.deepEqual(tickIndices(10, 4), [0, 3, 6, 9])
  assert.deepEqual(tickIndices(3, 4), [0, 1, 2])
  assert.deepEqual(tickIndices(0), [])
})

test('formati', () => {
  assert.equal(fmt.weight(82.5, 'kg', 'it'), '82,5 kg')
  assert.equal(fmt.weight(1.25, 'kg', 'it'), '1,25 kg')
  assert.equal(fmt.weight(100, 'lb', 'it'), '220,46 lb')
  assert.equal(fmt.volume(12_400, 'kg', 'it'), '12,4 t')
  assert.equal(fmt.volume(800, 'kg', 'it'), '800 kg')
  assert.equal(fmt.clock(90), '1:30')
  assert.equal(fmt.duration(45 * 60), '45 min')
  assert.equal(fmt.duration(80 * 60), '1 h 20 min')
  assert.equal(fmt.delta(4.2, 'it'), '+4,2%')
  assert.equal(fmt.delta(-1, 'it'), '-1%')
  assert.equal(fmt.setLine(80, 5, 'kg', 'it'), '80 kg × 5')
})

test('obiettivo della settimana: la tua media, fra 2 e 5, 3 se non c\'è storia', () => {
  const now = Date.now()
  const WEEK = 7 * 86_400_000
  const mk = (weeksAgo: number, n: number) => Array.from({ length: n }, (_, i) => ({ date: weekStart(now) - weeksAgo * WEEK + (i + 1) * 3_600_000 }))
  assert.equal(weeklyGoal([], now), 3)
  assert.equal(weeklyGoal(mk(1, 4), now), 3, 'una sola settimana di storia')
  // quattro allenamenti a settimana per otto settimane
  assert.equal(weeklyGoal([1, 2, 3, 4, 5, 6, 7, 8].flatMap((w) => mk(w, 4)), now), 4)
  // sei a settimana: si ferma a 5; uno a settimana: sale a 2
  assert.equal(weeklyGoal([1, 2, 3, 4, 5, 6, 7, 8].flatMap((w) => mk(w, 6)), now), 5)
  assert.equal(weeklyGoal([1, 2, 3, 4, 5, 6, 7, 8].flatMap((w) => mk(w, 1)), now), 2)
  // questa settimana non conta
  assert.equal(weeklyGoal([...mk(0, 5), ...mk(1, 3), ...mk(2, 3)].concat([1, 2, 3, 4, 5, 6].flatMap((w) => mk(w + 2, 3))), now), 3)
})
