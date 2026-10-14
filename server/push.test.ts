import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createPublicKey, verify } from 'node:crypto'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { AddressInfo } from 'node:net'
import { createApp } from './index.ts'
import { endpointOk } from './push.ts'

const EP = 'https://web.push.apple.com/QWERTY1234'
let dir: string, base: string
let app: ReturnType<typeof createApp>
const sent: { endpoint: string; headers: Record<string, string> }[] = []
let status = 201

before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'peak-push-'))
  app = createApp({
    dataDir: join(dir, 'data'), staticDir: join(dir, 'dist'), env: { BASE_URL: 'https://peak.test' },
    send: async (endpoint, headers) => { sent.push({ endpoint, headers }); return status },
  })
  await new Promise<void>((ok) => app.server.listen(0, '127.0.0.1', ok))
  base = `http://127.0.0.1:${(app.server.address() as AddressInfo).port}`
})
after(() => { app.push.stop(); app.server.close(); app.store.close(); rmSync(dir, { recursive: true }) })

const login = (sub: string) => {
  const u = app.store.upsertUser(sub, `${sub}@x.it`, sub, sub)
  return { user: u, h: { cookie: `peak=${app.store.createSession(u.id, 1)}`, 'sec-fetch-site': 'same-origin', 'content-type': 'application/json' } }
}
const call = (path: string, h: Record<string, string>, method: string, body?: unknown) =>
  fetch(base + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) })
const wait = (ms: number) => new Promise((r) => setTimeout(r, ms))

test('solo i servizi push dei browser sono ammessi', () => {
  assert.ok(endpointOk('https://web.push.apple.com/abc'))
  assert.ok(endpointOk('https://fcm.googleapis.com/fcm/send/abc'))
  assert.ok(endpointOk('https://updates.push.services.mozilla.com/wpush/v2/abc'))
  assert.ok(endpointOk('https://wns2-par02p.notify.windows.com/?token=abc'))
  for (const bad of ['http://web.push.apple.com/x', 'https://evil.example/x', 'https://push.apple.com.evil.example/x', 'https://u:p@web.push.apple.com/x', 'file:///etc/passwd', '', 5, null, 'https://169.254.169.254/'])
    assert.ok(!endpointOk(bad), String(bad))
})

test('la chiave pubblica VAPID è in /api/io, 65 byte non compressi', async () => {
  const { h } = login('io')
  const io = (await (await call('/api/io', h, 'GET')).json()) as { vapid: string }
  const bytes = Buffer.from(io.vapid, 'base64url')
  assert.equal(bytes.length, 65)
  assert.equal(bytes[0], 4)
})

test('a fine recupero parte un avviso senza corpo, firmato VAPID con la chiave pubblica', async () => {
  const { h } = login('p1')
  sent.length = 0; status = 201
  assert.equal((await call('/api/push', h, 'POST', { endpoint: EP })).status, 200)
  assert.equal((await call('/api/recupero', h, 'POST', { endpoint: EP, fine: Date.now() + 150 })).status, 200)
  assert.equal(sent.length, 0, 'non subito')
  await wait(400)
  assert.equal(sent.length, 1)
  const { authorization, ttl, urgency } = sent[0].headers
  assert.equal(ttl, '120'); assert.equal(urgency, 'high')
  const [, jwt, k] = /^vapid t=([^,]+), k=(.+)$/.exec(authorization)!
  const [h64, p64, s64] = jwt.split('.')
  const pub = Buffer.from(k, 'base64url')
  const key = createPublicKey({ key: { kty: 'EC', crv: 'P-256', x: pub.subarray(1, 33).toString('base64url'), y: pub.subarray(33).toString('base64url') }, format: 'jwk' })
  assert.ok(verify('sha256', Buffer.from(`${h64}.${p64}`), { key, dsaEncoding: 'ieee-p1363' }, Buffer.from(s64, 'base64url')), 'firma ES256 valida')
  const claims = JSON.parse(Buffer.from(p64, 'base64url').toString())
  assert.equal(claims.aud, 'https://web.push.apple.com')
  assert.equal(claims.sub, 'https://peak.test')
  assert.ok(claims.exp * 1000 > Date.now() + 11 * 3600_000 && claims.exp * 1000 < Date.now() + 13 * 3600_000)
  assert.equal(k, ((await (await call('/api/io', h, 'GET')).json()) as { vapid: string }).vapid)
  await wait(100)
  assert.equal(sent.length, 1, 'una volta sola')
})

test('saltare o spostare il recupero: cancella o ripianifica l\'avviso', async () => {
  const { h } = login('p2')
  const ep = EP + 'b'
  sent.length = 0
  await call('/api/push', h, 'POST', { endpoint: ep })
  await call('/api/recupero', h, 'POST', { endpoint: ep, fine: Date.now() + 120 })
  assert.equal((await call('/api/recupero', h, 'DELETE', { endpoint: ep })).status, 200)
  await wait(300)
  assert.equal(sent.length, 0, 'saltato: nessun avviso')
  // +15 s: stessa iscrizione, nuovo orario, vale l'ultimo
  await call('/api/recupero', h, 'POST', { endpoint: ep, fine: Date.now() + 100 })
  await call('/api/recupero', h, 'POST', { endpoint: ep, fine: Date.now() + 400 })
  await wait(250)
  assert.equal(sent.length, 0)
  await wait(400)
  assert.equal(sent.length, 1)
})

test('un\'iscrizione scaduta (410) viene tolta; l\'orario è limitato; gli estranei non entrano', async () => {
  const a = login('p3'), b = login('p4')
  const ep = EP + 'c'
  sent.length = 0; status = 410
  await call('/api/push', a.h, 'POST', { endpoint: ep })
  assert.equal((await call('/api/recupero', a.h, 'POST', { endpoint: ep, fine: Date.now() + 2 * 3600_000 })).status, 400, 'oltre un\'ora no')
  assert.equal((await call('/api/recupero', a.h, 'POST', { endpoint: ep, fine: 'presto' })).status, 400)
  assert.equal((await call('/api/recupero', b.h, 'POST', { endpoint: ep, fine: Date.now() + 100 })).status, 400, 'non è la sua iscrizione')
  assert.equal((await call('/api/push', a.h, 'POST', { endpoint: 'https://evil.example/x' })).status, 400)
  await call('/api/recupero', a.h, 'POST', { endpoint: ep, fine: Date.now() + 100 })
  await wait(350)
  assert.equal(sent.length, 1)
  assert.equal((await call('/api/recupero', a.h, 'POST', { endpoint: ep, fine: Date.now() + 100 })).status, 400, 'iscrizione tolta dopo il 410')
  status = 201
  assert.equal((await fetch(base + '/api/push', { method: 'POST', body: '{}' })).status, 401)
})

test('all\'avvio gli avvisi in attesa si ripianificano, quelli vecchi si scartano', async () => {
  const u = app.store.upsertUser('p5', 'p5@x.it', 'p5', 'p5')
  app.store.db.prepare('INSERT INTO push_subs (user_id, endpoint, due_at) VALUES (?, ?, ?)').run(u.id, EP + 'd', Date.now() + 200)
  app.store.db.prepare('INSERT INTO push_subs (user_id, endpoint, due_at) VALUES (?, ?, ?)').run(u.id, EP + 'e', Date.now() - 120_000)
  sent.length = 0
  app.push.boot()
  await wait(500)
  assert.deepEqual(sent.map((s) => s.endpoint), [EP + 'd'])
  assert.equal(app.store.db.prepare('SELECT COUNT(*) AS n FROM push_subs WHERE due_at IS NOT NULL').get()!.n, 0)
})
