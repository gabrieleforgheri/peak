import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { AddressInfo } from 'node:net'
import { createApp } from './index.ts'
import { summarize } from './summary.ts'
import { isoSec, newUuid } from '../common/docs.ts'

const TOKEN = 'x'.repeat(40)
let dir: string, base: string
let app: ReturnType<typeof createApp>
const DAY = 86_400_000

before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'peak-portale-'))
  app = createApp({ dataDir: join(dir, 'data'), staticDir: join(dir, 'dist'), env: { YG_PORTAL_TOKEN: TOKEN } })
  assert.ok(app.portal)
  await new Promise<void>((ok) => app.portal!.listen(0, '127.0.0.1', ok))
  base = `http://127.0.0.1:${(app.portal!.address() as AddressInfo).port}`
})
after(() => { app.portal!.close(); app.store.close(); rmSync(dir, { recursive: true }) })

const get = (path: string, token = TOKEN, init: RequestInit = {}) => fetch(base + path, { ...init, headers: { authorization: `Bearer ${token}`, ...(init.headers as object) } })

/** Un utente con un allenamento concluso ieri (un PR) e uno vecchio di 60 giorni. */
function seed(sub: string, email: string) {
  const u = app.store.upsertUser(sub, email, sub, sub)
  const ex = newUuid(), w1 = newUuid(), w2 = newUuid()
  const now = Date.now()
  const at = (ms: number) => isoSec(ms)
  app.store.applyPlan(u.id, { photos: new Map(), del: [], put: [
    { kind: 'exercise', uuid: ex, data: { name: 'Chest Press', muscleGroup: 'petto', notes: '', incrementStep: 5, sortIndex: 0, isArchived: false, createdAt: at(now - 90 * DAY), glyph: '', photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 0 } },
    { kind: 'workout', uuid: w2, data: { date: at(now - 60 * DAY), endedAt: at(now - 60 * DAY + 3600_000), note: '', name: 'Vecchio', focus: '' } },
    { kind: 'set', uuid: newUuid(), data: { workout: w2, exercise: ex, weight: 50, reps: 5, rpe: null, isWarmup: false, order: 0, createdAt: at(now - 60 * DAY + 600_000), noRecord: false } },
    { kind: 'workout', uuid: w1, data: { date: at(now - DAY), endedAt: at(now - DAY + 2700_000), note: '', name: 'Push', focus: 'petto' } },
    { kind: 'set', uuid: newUuid(), data: { workout: w1, exercise: ex, weight: 60, reps: 5, rpe: null, isWarmup: false, order: 0, createdAt: at(now - DAY + 600_000), noRecord: false } },
    { kind: 'set', uuid: newUuid(), data: { workout: w1, exercise: ex, weight: 20, reps: 10, rpe: null, isWarmup: true, order: 1, createdAt: at(now - DAY + 700_000), noRecord: false } },
  ] }, now)
  return u
}

test('summarize: ultimo allenamento, numeri dei 30 giorni, ultimi record', () => {
  const u = seed('sum', 'sum@x.it')
  const s = summarize(app.store.live(u.id))
  assert.equal(s.ultimo?.nome, 'Push')
  assert.equal(s.ultimo?.durataSec, 2700)
  assert.equal(s.ultimo?.volumeKg, 300, 'il riscaldamento non conta')
  assert.equal(s.ultimo?.record, 1)
  assert.deepEqual(s.trentaGiorni, { sessioni: 1, volumeKg: 300, record: 1, settimane: 1 })
  assert.deepEqual(s.ultimiRecord.map((r) => [r.esercizio, r.pesoKg, r.auto]), [['Chest Press', 60, true], ['Chest Press', 50, true]])
  assert.deepEqual(summarize([]), { unita: 'kg', ultimo: null, trentaGiorni: { sessioni: 0, volumeKg: 0, record: 0, settimane: 0 }, ultimiRecord: [] })
})

test('portale: token obbligatorio, utente cercato per sub e poi per email, mai creato', async () => {
  seed('por', 'Por@X.it')
  assert.equal((await fetch(base + '/portale/riepilogo?sub=por')).status, 401)
  assert.equal((await get('/portale/riepilogo?sub=por', 'y'.repeat(40))).status, 401)
  assert.equal((await get('/portale/riepilogo?sub=por', 'corto')).status, 401)
  const ok = await get('/portale/riepilogo?sub=por')
  assert.equal(ok.status, 200)
  assert.equal(((await ok.json()) as { ultimo: { nome: string } }).ultimo.nome, 'Push')
  // il sub può essere diverso fra provider: si ripiega sull'email, senza badare alle maiuscole
  assert.equal((await get('/portale/riepilogo?sub=altro&email=por@x.it')).status, 200)
  assert.equal((await get('/portale/riepilogo?sub=sconosciuto&email=nessuno@x.it')).status, 404)
  assert.equal(app.store.userBySub('sconosciuto'), undefined, 'non crea utenti')
})

test('portale: backup scaricabile (segna lastExportDate), revoca dispositivo, scollegamento per email', async () => {
  const u = seed('por2', 'por2@x.it')
  const tok = app.store.createDevice(u.id, 'Peak · iPhone')
  const backup = await get('/portale/backup?sub=por2')
  assert.match(backup.headers.get('content-disposition')!, /attachment; filename="Peak-/)
  assert.equal(((await backup.json()) as { exercises: unknown[] }).exercises.length, 1)
  assert.ok(app.store.live(u.id).some((d) => d.kind === 'setting' && d.uuid === 'lastExportDate'))
  const id = app.store.listDevices(u.id)[0].id
  assert.equal((await get(`/portale/dispositivi/${id}?sub=por2`, TOKEN, { method: 'DELETE' })).status, 200)
  assert.equal(app.store.deviceUser(tok), undefined)

  // sincronizza: un elenco vuoto non scollega nessuno
  const keep = seed('keep', 'keep@x.it'); const drop = seed('drop', 'drop@x.it')
  const kt = app.store.createDevice(keep.id, 'Peak · a'); const dt = app.store.createDevice(drop.id, 'Peak · b')
  const post = (emails: unknown) => get('/portale/sincronizza', TOKEN, { method: 'POST', body: JSON.stringify({ emails }), headers: { 'content-type': 'application/json' } })
  assert.equal((await post([])).status, 400)
  assert.ok(app.store.deviceUser(dt))
  assert.equal((await post(['KEEP@x.it', 'por@x.it', 'por2@x.it'])).status, 200)
  assert.ok(app.store.deviceUser(kt))
  assert.equal(app.store.deviceUser(dt), undefined)
})

test('portale: senza YG_PORTAL_TOKEN (o corto) non esiste', () => {
  const dir2 = mkdtempSync(join(tmpdir(), 'peak-p2-'))
  const a = createApp({ dataDir: dir2, staticDir: dir2, env: {} })
  const b = createApp({ dataDir: join(dir2, 'b'), staticDir: dir2, env: { YG_PORTAL_TOKEN: 'troppo corto' } })
  assert.equal(a.portal, null); assert.equal(b.portal, null)
  a.store.close(); b.store.close(); rmSync(dir2, { recursive: true })
})
