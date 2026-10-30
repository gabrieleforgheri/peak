import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { AddressInfo } from 'node:net'
import { createApp } from './index.ts'
import { sharedView } from './social.ts'
import type { LiveDoc } from '../common/docs.ts'

const U = (n: number) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const iso = (ms: number) => new Date(Math.floor(ms / 1000) * 1000).toISOString().replace('.000Z', 'Z')
const DAY = 86_400_000

let dir: string, base: string
let app: ReturnType<typeof createApp>

before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'peak-social-'))
  mkdirSync(join(dir, 'dist'))
  writeFileSync(join(dir, 'dist/index.html'), '<!doctype html><title>Peak</title>')
  app = createApp({ dataDir: join(dir, 'data'), staticDir: join(dir, 'dist'), env: { BASE_URL: 'http://x.test' } as never })
  await new Promise<void>((ok) => app.server.listen(0, '127.0.0.1', ok))
  base = `http://127.0.0.1:${(app.server.address() as AddressInfo).port}`
})
after(() => { app.server.close(); app.store.close(); rmSync(dir, { recursive: true, force: true }) })

const login = (name: string) => {
  const user = app.store.upsertUser(name, `${name}@x.it`, name, name.toUpperCase())
  return { user, h: { cookie: `peak=${app.store.createSession(user.id, 1)}`, 'sec-fetch-site': 'same-origin' } as Record<string, string> }
}
const call = (path: string, h: Record<string, string>, init: { method?: string; body?: unknown } = {}) =>
  fetch(base + path, {
    method: init.method ?? (init.body !== undefined ? 'POST' : 'GET'), redirect: 'manual',
    headers: { ...(init.body !== undefined ? { 'content-type': 'application/json' } : {}), ...h },
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  })

type Who = ReturnType<typeof login>
/** Un esercizio con una serie sola (reps × weight) in un allenamento di ieri. */
async function train(who: Who, n: number, ex: { name: string; catalog: string; hidden?: boolean; archived?: boolean }, weight: number, reps: number, daysAgo = 1) {
  const t = Date.now()
  const day = t - daysAgo * DAY
  const docs = [
    { kind: 'exercise', uuid: U(n), m: t, deleted: false, data: { name: ex.name, muscleGroup: 'petto', notes: '', incrementStep: 5, sortIndex: 0, isArchived: !!ex.archived, createdAt: iso(day), catalog: ex.catalog, hidden: !!ex.hidden } },
    { kind: 'workout', uuid: U(n + 100), m: t, deleted: false, data: { date: iso(day), endedAt: iso(day + 3600_000), note: '', name: '', focus: '' } },
    { kind: 'set', uuid: U(n + 200), m: t, deleted: false, data: { workout: U(n + 100), exercise: U(n), weight, reps, rpe: null, isWarmup: false, order: 0, createdAt: iso(day), noRecord: false, singleArm: false } },
  ]
  const out = await (await call('/api/sync', who.h, { body: { docs } })).json() as { scartati: unknown[] }
  assert.deepEqual(out.scartati, [])
}
const befriend = (a: Who, b: Who) => {
  app.store.requestFriend(a.user.id, b.user.id)
  app.store.acceptFriend(b.user.id, a.user.id)
}

test('amicizia: ricerca per username, richiesta, accettazione, rimozione', async () => {
  const ann = login('ann'), ben = login('ben')
  assert.deepEqual((await (await call('/api/amici/cerca?q=b', ann.h)).json() as { utenti: unknown[] }).utenti, [], 'sotto i 2 caratteri niente')
  const found = (await (await call('/api/amici/cerca?q=ben', ann.h)).json() as { utenti: { id: number; username: string; stato: string; email?: string }[] }).utenti
  assert.deepEqual(found.map((u) => [u.username, u.stato]), [['ben', 'nessuno']])
  assert.equal('email' in found[0], false, 'mai l\'email')
  assert.equal((await call('/api/amici/cerca?q=an', ann.h)).status, 200)
  assert.deepEqual((await (await call('/api/amici/cerca?q=an', ann.h)).json() as { utenti: unknown[] }).utenti, [], 'non trova sé stessa')

  assert.equal((await call('/api/amici', ann.h, { body: { id: ben.user.id } })).status, 200)
  assert.equal((await call('/api/amici', ann.h, { body: { id: ann.user.id } })).status, 404, 'non con sé stessi')
  assert.equal((await call('/api/amici', ann.h, { body: { id: 9999 } })).status, 404)
  let list = await (await call('/api/amici', ben.h)).json() as { ricevute: { username: string }[]; amici: unknown[] }
  assert.deepEqual(list.ricevute.map((p) => p.username), ['ann'])
  assert.equal((await call(`/api/amici/${ann.user.id}`, ben.h)).status, 403, 'una richiesta non è ancora amicizia')
  assert.equal((await call(`/api/amici/${ann.user.id}/accetta`, ann.h, { method: 'POST' })).status, 404, 'solo chi riceve accetta')
  assert.equal((await call(`/api/amici/${ann.user.id}/accetta`, ben.h, { method: 'POST' })).status, 200)
  list = await (await call('/api/amici', ann.h)).json() as typeof list
  assert.deepEqual((list.amici as { username: string }[]).map((p) => p.username), ['ben'])
  assert.equal((await call(`/api/amici/${ben.user.id}`, ann.h, { method: 'DELETE' })).status, 200)
  assert.equal((await call(`/api/amici/${ben.user.id}`, ann.h)).status, 403)
})

test('non amico: profilo, classifica degli altri e novità restano chiusi', async () => {
  const cat = login('cat'), dan = login('dan')
  await train(dan, 1, { name: 'Panca', catalog: 'bench-press-barbell' }, 100, 5)
  assert.equal((await call(`/api/amici/${dan.user.id}`, cat.h)).status, 403)
  const board = await (await call('/api/classifica/bench-press-barbell', cat.h)).json() as { righe: { username: string }[] }
  assert.deepEqual(board.righe, [], 'nemmeno nella classifica')
  assert.deepEqual((await (await call('/api/novita', cat.h)).json() as { eventi: unknown[] }).eventi, [])
  assert.equal((await call('/api/amici/1', { 'sec-fetch-site': 'same-origin' })).status, 401, 'senza sessione')
})

test('classifica: 1RM stimato dalle serie vere, tu e gli amici; nascosti, archiviati e personalizzati fuori', async () => {
  const eve = login('eve'), fay = login('fay'), gus = login('gus')
  befriend(eve, fay); befriend(eve, gus)
  await train(eve, 10, { name: 'La mia panca', catalog: 'bench-press-barbell' }, 80, 8) // 80 × 8 ≈ 101,3
  await train(fay, 11, { name: 'Bench', catalog: 'bench-press-barbell' }, 100, 1) // 100
  await train(gus, 12, { name: 'Bench', catalog: 'bench-press-barbell', hidden: true }, 200, 1) // nascosta: non c'è
  const board = await (await call('/api/classifica/bench-press-barbell', eve.h)).json() as { righe: { username: string; tu: boolean; oneRm: number }[] }
  assert.deepEqual(board.righe.map((r) => [r.username, r.tu]), [['eve', true], ['fay', false]])
  assert.ok(Math.abs(board.righe[0].oneRm - 80 * (1 + 8 / 30)) < 0.01)
  // l'amico che archivia o personalizza esce dalla classifica
  await train(fay, 11, { name: 'Bench', catalog: 'bench-press-barbell', archived: true }, 100, 1)
  assert.deepEqual((await (await call('/api/classifica/bench-press-barbell', eve.h)).json() as { righe: unknown[] }).righe.length, 1)
  assert.deepEqual((await (await call('/api/classifica/non-esiste', eve.h)).json() as { righe: unknown[] }).righe, [])
  assert.equal((await call('/api/classifica/Non Valido', eve.h)).status, 404)
})

test('profilo di un amico: massimali e schede visibili, senza nascosti né pesi', async () => {
  const hal = login('hal'), ivy = login('ivy')
  befriend(hal, ivy)
  await train(ivy, 20, { name: 'Panca di Ivy', catalog: 'bench-press-barbell' }, 60, 10)
  await train(ivy, 21, { name: 'Segreto', catalog: 'pec-deck', hidden: true }, 50, 10)
  await train(ivy, 22, { name: 'Mio attrezzo', catalog: '' }, 30, 10)
  const t = Date.now()
  const item = (n: number, weight: number | null) => ({ exercise: U(n), sets: 3, repsMin: 8, repsMax: 12, weight })
  const tpl = (n: number, name: string, hidden: boolean, items: unknown[]) => ({ kind: 'template', uuid: U(n), m: t, deleted: false, data: { name, items, sortIndex: n, createdAt: iso(t), hidden } })
  await call('/api/sync', ivy.h, { body: { docs: [tpl(30, 'Push', false, [item(20, 60), item(21, null), item(22, null)]), tpl(31, 'Privata', true, [item(20, null)])] } })
  const view = await (await call(`/api/amici/${ivy.user.id}`, hal.h)).json() as {
    username: string; maxes: { name: string; catalog: string }[]; templates: { name: string; rows: { name: string; weight?: number }[] }[]
  }
  assert.equal(view.username, 'ivy')
  assert.deepEqual(view.maxes.map((m) => m.name).sort(), ['Mio attrezzo', 'Panca di Ivy'])
  assert.deepEqual(view.templates.map((x) => x.name), ['Push'])
  assert.deepEqual(view.templates[0].rows.map((r) => r.name), ['Panca di Ivy', 'Mio attrezzo'], 'la riga con l\'esercizio nascosto non c\'è')
  assert.ok(view.templates[0].rows.every((r) => !('weight' in r)), 'niente pesi')
})

test('novità: record degli ultimi 14 giorni, con il sorpasso solo se ce l\'hai anche tu', async () => {
  const jon = login('jon'), kim = login('kim')
  befriend(jon, kim)
  await train(jon, 40, { name: 'Lat', catalog: 'lat-pulldown' }, 60, 10, 2)
  await train(kim, 41, { name: 'Lat', catalog: 'lat-pulldown' }, 80, 10, 3) // recente e più forte
  await train(kim, 42, { name: 'Pec', catalog: 'pec-deck' }, 50, 10, 3) // recente, tu non l'hai
  await train(kim, 43, { name: 'Vecchio', catalog: 'leg-extension' }, 90, 10, 30) // troppo vecchio
  const { eventi } = await (await call('/api/novita', jon.h)).json() as { eventi: { username: string; catalog: string; sorpasso: boolean }[] }
  assert.deepEqual(eventi.map((e) => [e.username, e.catalog, e.sorpasso]).sort(), [['kim', 'lat-pulldown', true], ['kim', 'pec-deck', false]])
})

test('invito: monouso, non il proprio, e il link rimanda al login e poi all\'app', async () => {
  const lea = login('lea'), max = login('max'), nia = login('nia')
  const { codice } = await (await call('/api/inviti', lea.h, { method: 'POST' })).json() as { codice: string }
  assert.match(codice, /^[A-Za-z0-9_-]{20,64}$/)
  assert.equal((await call(`/api/inviti/${codice}`, lea.h, { method: 'POST' })).status, 404, 'non il proprio')
  const ok = await call(`/api/inviti/${codice}`, max.h, { method: 'POST' })
  assert.equal(ok.status, 200)
  assert.equal(((await ok.json()) as { amico: { username: string } }).amico.username, 'lea')
  assert.ok(app.store.areFriends(lea.user.id, max.user.id), 'amici subito')
  assert.equal((await call(`/api/inviti/${codice}`, nia.h, { method: 'POST' })).status, 404, 'monouso')
  assert.ok(!app.store.areFriends(lea.user.id, nia.user.id))
  assert.equal((await call(`/api/inviti/${'x'.repeat(30)}`, nia.h, { method: 'POST' })).status, 404)
  // scaduto
  const old = app.store.createInvite(lea.user.id, Date.now() - 8 * DAY)
  assert.equal(app.store.useInvite(old.code, nia.user.id), null)
  // il codice non è nel database in chiaro
  const rows = app.store.db.prepare('SELECT code_hash FROM invites').all() as { code_hash: string }[]
  assert.ok(rows.every((r) => r.code_hash !== codice))

  // il link: senza sessione ricorda il codice e va al benvenuto; col cookie, dopo il login, riporta all'app
  const guest = await fetch(`${base}/invito/${codice}`, { redirect: 'manual' })
  assert.equal(guest.status, 302)
  assert.equal(guest.headers.get('location'), '/benvenuto.html')
  assert.match(guest.headers.get('set-cookie')!, /^peak_invito=/)
  const back = await fetch(`${base}/`, { redirect: 'manual', headers: { cookie: `peak=${app.store.createSession(nia.user.id, 1)}; peak_invito=${codice}` } })
  assert.equal(back.headers.get('location'), `/#/invito/${codice}`)
  const member = await fetch(`${base}/invito/${codice}`, { redirect: 'manual', headers: { cookie: `peak=${app.store.createSession(nia.user.id, 1)}` } })
  assert.equal(member.headers.get('location'), `/#/invito/${codice}`)
})

test('gli inviti per utente sono al massimo 20', () => {
  const u = login('ola').user
  for (let i = 0; i < 25; i++) app.store.createInvite(u.id)
  assert.equal((app.store.db.prepare('SELECT COUNT(*) AS n FROM invites WHERE user_id = ?').get(u.id) as { n: number }).n, 20)
})

test('lega: tu e gli amici con volume, allenamenti e settimane di fila; i non amici fuori', async () => {
  const pam = login('pam'), rex = login('rex'), zed = login('zed')
  befriend(pam, rex)
  await train(pam, 50, { name: 'Panca', catalog: 'bench-press-barbell' }, 100, 10, 0.1) // 1000 kg, oggi
  await train(rex, 51, { name: 'Panca', catalog: 'bench-press-barbell' }, 50, 10, 45) // fuori dai 30 giorni
  await train(zed, 52, { name: 'Panca', catalog: 'bench-press-barbell' }, 50, 10, 1)
  const { righe } = await (await call('/api/lega', pam.h)).json() as { righe: { username: string; tu: boolean; volume30: number; sessions30: number; streak: number; last: number }[] }
  assert.deepEqual(righe.map((r) => r.username).sort(), ['pam', 'rex'])
  const me = righe.find((r) => r.tu)!, him = righe.find((r) => !r.tu)!
  assert.deepEqual([me.volume30, me.sessions30], [1000, 1])
  assert.ok(me.streak >= 1)
  assert.deepEqual([him.volume30, him.sessions30, him.streak], [0, 0, 0])
  assert.ok(him.last < Date.now() - 40 * DAY)
  assert.equal((await call('/api/lega', { 'sec-fetch-site': 'same-origin' })).status, 401)
})

test('a un braccio: non entra nel massimale mostrato agli amici', () => {
  const day = Date.now() - DAY
  const docs = [
    { kind: 'exercise', uuid: U(1), data: { name: 'Row', muscleGroup: 'schiena', notes: '', incrementStep: 5, sortIndex: 0, isArchived: false, createdAt: iso(day), catalog: '', hidden: false } },
    { kind: 'workout', uuid: U(2), data: { date: iso(day), endedAt: iso(day + 3600_000), note: '', name: '', focus: '' } },
    { kind: 'set', uuid: U(3), data: { workout: U(2), exercise: U(1), weight: 60, reps: 5, rpe: null, isWarmup: false, order: 0, createdAt: iso(day), noRecord: false, singleArm: false } },
    { kind: 'set', uuid: U(4), data: { workout: U(2), exercise: U(1), weight: 90, reps: 8, rpe: null, isWarmup: false, order: 1, createdAt: iso(day), noRecord: false, singleArm: true } },
  ] as unknown as LiveDoc[]
  assert.deepEqual(sharedView(docs).maxes.map((m) => [m.weight, m.reps]), [[60, 5]])
})

test('lega: il volume non conta le serie di esercizi nascosti o archiviati', async () => {
  const kim = login('lola')
  await train(kim, 70, { name: 'Panca', catalog: 'bench-press-barbell' }, 100, 10, 0.1) // 1000 kg visibili
  await train(kim, 71, { name: 'Segreto', catalog: '', hidden: true }, 100, 10, 0.1)
  await train(kim, 72, { name: 'Vecchio', catalog: '', archived: true }, 100, 10, 0.1)
  const { righe } = await (await call('/api/lega', kim.h)).json() as { righe: { volume30: number; sessions30: number }[] }
  assert.deepEqual([righe[0].volume30, righe[0].sessions30], [1000, 3])
})
