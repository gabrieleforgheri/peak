// Le schermate della pagina di presentazione (yrb4g.com/studio/peak, repo yG): un Peak di prova con otto settimane
// di allenamenti, tre schede e due amici, fotografato a 390×844 (×2) in italiano e in inglese: la schermata, la
// pagina intera piena e vuota (senza i grafici) e un JSON con dove stanno i pezzi che le clip fanno comparire.
// La pagina mostra sempre l'ultima versione dell'app: a ogni rilascio si rilancia e si copiano i file in yG.
// Uso: npm i --no-save playwright-core   (una volta; usa il Chrome di sistema)
//      node scripts/studio-shots.ts            (scrive in studio-clips/shots/, poi: node studio-clips/schermate.ts)
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createApp } from '../server/index.ts'
import { isoSec } from '../common/docs.ts'
import type { WireDoc } from '../server/store.ts'

const out = process.argv[2] ?? 'studio-clips/shots'
mkdirSync(out, { recursive: true })
process.env.TZ = 'Europe/Rome'
const { server, store } = createApp({ dataDir: mkdtempSync(join(tmpdir(), 'peak-shots-')), env: {} })

const DAY = 86_400_000
const now = Date.now()
const today = new Date(now); today.setHours(0, 0, 0, 0)
const monday = today.getTime() - ((today.getDay() + 6) % 7) * DAY
let n = 0
const uuid = () => `00000000-0000-4000-8000-${(++n).toString(16).toUpperCase().padStart(12, '0')}`
const iso = (ms: number) => isoSec(ms)

type Ex = { id: string; catalog: string; name: string; group: string; glyph: string; start: number; step: number; reps: number }
const ex = (catalog: string, name: string, group: string, glyph: string, start: number, step: number, reps: number): Ex =>
  ({ id: uuid(), catalog, name, group, glyph, start, step, reps })

/** Un utente con i suoi esercizi, schede e allenamenti; `scale` sposta i pesi per gli amici. */
function seed(sub: string, username: string, name: string, scale: number, days: number[], live: boolean) {
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
  for (const [k, v] of Object.entries({ weeklyGoal: 4, tutorialDone: true, tourSeen: 2, accent: 'lime', unit: 'kg' })) put('setting', k, { value: v })

  // Otto settimane piene e questa a metà; il peso sale ogni due settimane, l'ultima settimana porta un record.
  const kg = (e: Ex, week: number) => Math.round(((e.start + e.step * Math.floor(week / 2)) * scale) / e.step) * e.step
  // l'allenamento in corso: l'ultimo giorno della scheda non oltre oggi, a qualunque ora si lanci
  const liveDay = Math.max(...days.filter((d) => monday + d * DAY <= now))
  let w = 0
  for (let week = 0; week <= 8; week++) {
    for (const d of days) {
      const start = monday - (8 - week) * 7 * DAY + d * DAY + 18.5 * 3600_000
      const isLive = live && week === 8 && d === liveDay
      if (start > now && !isLive) continue
      const p = plans[w++ % plans.length]
      const begin = isLive ? now - 38 * 60_000 : start
      const id = uuid()
      put('workout', id, {
        date: iso(begin), endedAt: isLive ? null : iso(begin + 62 * 60_000), note: '', name: p.name, focus: '', template: p.id,
        plan: p.items.map((e) => ({ exercise: e.id, sets: 3, repsMin: e.reps - 2, repsMax: e.reps + 2, weight: kg(e, week) })),
      })
      let order = 0
      p.items.forEach((e, i) => {
        if (isLive && i > 1) return
        const top = kg(e, week) + (week === 8 && i === 0 ? e.step * (isLive ? 3 : 2) : 0)
        const sets = isLive && i === 1 ? 1 : 3
        for (let s = 0; s < sets; s++) put('set', uuid(), {
          workout: id, exercise: e.id, weight: top, reps: e.reps - (s === 2 ? 1 : 0), rpe: null, isWarmup: false,
          order: order++, createdAt: iso(begin + (i * 15 + s * 4 + 3) * 60_000), noRecord: false,
        })
      })
    }
  }
  for (let week = 0; week <= 8; week++) put('bodyweight', uuid(), { date: iso(monday - (8 - week) * 7 * DAY + 7.5 * 3600_000), weight: Math.round((72.4 - week * 0.35) * scale * 10) / 10, fromHealth: false })
  const r = store.push(user.id, docs)
  if (r.scartati.length) throw new Error(`${username}: ${JSON.stringify(r.scartati.slice(0, 3))}`)
  return user
}

// Ven/Sab per la persona di prova; lun-mar-gio-sab = obiettivo 4, questa settimana a 3 su 4.
const me = seed('studio', 'giulia', 'Giulia', 1, [0, 1, 3, 4], true)
const friends = [seed('studio-marco', 'marco', 'Marco', 1.12, [0, 2, 4], false), seed('studio-sara', 'sara', 'Sara', 0.86, [1, 3, 5], false)]
for (const f of friends) store.db.prepare("INSERT INTO friends (a, b, status, created_at) VALUES (?, ?, 'amici', ?)").run(me.id, f.id, now)
const cookie = store.createSession(me.id, 1)
const live = (store.pull(me.id, 0).docs.find((d) => d.kind === 'workout' && !(d.data as { endedAt: string | null }).endedAt))!.uuid
const legPress = store.pull(me.id, 0).docs.find((d) => d.kind === 'exercise' && (d.data as { catalog: string }).catalog === 'leg-press-45')!.uuid

await new Promise<void>((ok) => server.listen(0, '127.0.0.1', ok))
const base = `http://127.0.0.1:${(server.address() as { port: number }).port}`
const { chromium } = await import('playwright-core')
const browser = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' })
// La coreografia delle clip (studio-clips/schermate.ts): per ogni schermata cosa compare, da dove e quando (secondi).
// up = cresce dal basso, right = si riempie da sinistra, pop = salta fuori, fade = sale e appare.
// `scroll` porta l'elemento a `offset` px dal bordo alto; `callouts` cerchia l'elemento e ci punta una freccia.
type Beat = { sel: string; kind: 'up' | 'right' | 'pop' | 'fade'; at: number; step?: number; dur?: number }
type Spec = { name: string; hash: string; beats: Beat[]; scroll?: { sel: string; at: number; dur: number; offset?: number }[]; callouts?: { sel: string; at: number }[] }
const shots: Spec[] = [
  // la montagna in cima ad Allenamento, poi il pulsante per partire e i giorni della scheda
  { name: 'home', hash: '#/allenamento', beats: [
    { sel: '.peak .mount', kind: 'up', at: 0.3, dur: 0.9 },
    { sel: '.peak .segs i.on', kind: 'right', at: 1.0, step: 0.22, dur: 0.3 },
    { sel: '.peak .pill', kind: 'pop', at: 1.8, dur: 0.45 },
    { sel: '.card.list .item', kind: 'fade', at: 2.4, step: 0.15, dur: 0.45 },
  ], callouts: [{ sel: '.peak .pill', at: 2.0 }] },
  { name: 'schede', hash: '#/allenamento', beats: [
    { sel: '.card.list .item', kind: 'fade', at: 1.3, step: 0.15, dur: 0.45 },
    { sel: 'main .stack > .card', kind: 'fade', at: 2.0, step: 0.15, dur: 0.45 },
  ], scroll: [{ sel: '.card.list', at: 0.3, dur: 0.9, offset: 140 }], callouts: [{ sel: '.card.list .item', at: 2.6 }] },
  { name: 'sessione', hash: `#/allenamento/${live}`, beats: [
    { sel: 'main .list .item', kind: 'fade', at: 0.4, step: 0.2, dur: 0.4 },
    { sel: 'main .badge.pos', kind: 'pop', at: 1.4, step: 0.15, dur: 0.45 },
  ], scroll: [{ sel: 'main .card:nth-of-type(3)', at: 3.0, dur: 1.0, offset: 120 }], callouts: [{ sel: 'main .badge.pos', at: 1.7 }] },
  { name: 'esercizio', hash: `#/macchinari/${legPress}`, beats: [
    { sel: 'main .big.num', kind: 'fade', at: 0.3, dur: 0.5 },
    { sel: 'main .badge.pos', kind: 'pop', at: 0.7, dur: 0.45 },
    { sel: 'main .card .stack > button', kind: 'right', at: 1.3, step: 0.18, dur: 0.5 },
    { sel: 'svg.chart', kind: 'right', at: 3.8, dur: 1.4 },
  ], scroll: [{ sel: 'svg.chart', at: 2.6, dur: 1.1, offset: 320 }], callouts: [{ sel: 'main .badge.pos', at: 1.0 }] },
  { name: 'progressi', hash: '#/progressi', beats: [
    { sel: '.tiles .tile', kind: 'pop', at: 0.3, step: 0.12, dur: 0.45 },
    { sel: 'svg.chart', kind: 'right', at: 0.9, dur: 1.2 },
    { sel: 'main .bars > div', kind: 'up', at: 3.0, step: 0.08, dur: 0.45 },
    { sel: 'main .hbar > div', kind: 'right', at: 5.2, step: 0.15, dur: 0.5 },
    { sel: 'main .card.list .badge', kind: 'pop', at: 6.0, step: 0.12, dur: 0.4 },
  ], scroll: [{ sel: 'main .bars', at: 2.0, dur: 1.0, offset: 200 }, { sel: 'main .hbar', at: 4.2, dur: 1.0, offset: 240 }],
  callouts: [{ sel: '.tiles .tile:nth-child(4)', at: 1.2 }] },
  { name: 'amici', hash: '#/amici', beats: [
    { sel: '.podium .ped', kind: 'up', at: 0.4, step: 0.25, dur: 0.6 },
    { sel: '.podium .who', kind: 'pop', at: 0.8, step: 0.25, dur: 0.45 },
    { sel: 'main .card.list .hbar > div', kind: 'right', at: 3.3, step: 0.18, dur: 0.5 },
  ], scroll: [{ sel: 'main .card.list', at: 2.3, dur: 1.0, offset: 300 }], callouts: [{ sel: '.podium .you', at: 1.6 }] },
]
const hide = (sels: string[]) => `${sels.join(',')}{visibility:hidden!important}`
for (const lang of ['it', 'en']) {
  // movimento ridotto: la frase della montagna non cambia fra una foto e l'altra
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: lang === 'it' ? 'it-IT' : 'en-GB', timezoneId: 'Europe/Rome', reducedMotion: 'reduce' })
  await ctx.addCookies([{ name: 'peak', value: cookie, url: base }])
  for (const spec of shots) {
    // una pagina nuova per schermata: i percorsi sono a hash, e gli stili iniettati resterebbero
    const page = await ctx.newPage()
    const file = (suffix: string) => join(out, `${spec.name}-${lang}${suffix}`)
    await page.goto(`${base}/${spec.hash}`)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1200)
    await page.screenshot({ path: file('.png') })
    const data = await page.evaluate((spec) => {
      const r = (e: Element) => { const b = e.getBoundingClientRect(); return [b.x, b.y + scrollY, b.width, b.height].map((v) => Math.round(v * 10) / 10) }
      const all = (s: string) => [...document.querySelectorAll(s)].filter((e) => e.getBoundingClientRect().width > 0)
      const tab = document.querySelector('.tabbar')
      return {
        height: document.documentElement.scrollHeight, tab: tab ? r(tab) : null,
        beats: spec.beats.map((b) => ({ ...b, rects: all(b.sel).map(r) })),
        scroll: (spec.scroll ?? []).map((s) => ({ ...s, y: all(s.sel)[0] ? r(all(s.sel)[0]!)[1]! - (s.offset ?? 100) : 0 })),
        callouts: (spec.callouts ?? []).map((c) => ({ ...c, rect: all(c.sel)[0] ? r(all(c.sel)[0]!) : null })),
      }
    }, spec)
    for (const b of data.beats) if (!b.rects.length) console.warn(`  ${spec.name}-${lang}: nessun elemento per ${b.sel}`)
    // la barra delle schede da sola: è semitrasparente, e nella clip la pagina sotto scorre
    if (data.tab) {
      const vuota = await page.addStyleTag({ content: 'main{visibility:hidden!important}' })
      const [x, y, width, height] = data.tab
      await page.screenshot({ path: file('-tab.png'), clip: { x: x!, y: y!, width: width!, height: height! } })
      await vuota.evaluate((e) => e.remove())
    }
    await page.addStyleTag({ content: '.tabbar{display:none!important}' })
    await page.screenshot({ path: file('-full.png'), fullPage: true })
    await page.addStyleTag({ content: hide(spec.beats.map((b) => b.sel)) })
    await page.screenshot({ path: file('-empty.png'), fullPage: true })
    writeFileSync(file('.json'), JSON.stringify(data))
    console.log(`${spec.name}-${lang}`, data.beats.map((b) => b.rects.length).join('/'), `h=${data.height}`)
    await page.close()
  }
  await ctx.close()
}
await browser.close()
server.close()
store.close()
