import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer, type Server } from 'node:http'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { AddressInfo } from 'node:net'
import { createApp } from './index.ts'

const U = (n: number) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`
const NOW = () => Date.now()

let dir: string, base: string, idp: Server, idpUrl: string
let app: ReturnType<typeof createApp>
// ciò che il finto authentik risponde al prossimo scambio del code
const next: { nonce: string; challenge: string; groups: string[]; sub: string; email: string } = { nonce: '', challenge: '', groups: ['svc-peak'], sub: 'sub-1', email: 'a@b.it' }

const listen = (s: Server) => new Promise<string>((ok) => s.listen(0, '127.0.0.1', () => ok(`http://127.0.0.1:${(s.address() as AddressInfo).port}`)))

before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'peak-api-'))
  mkdirSync(join(dir, 'dist'))
  writeFileSync(join(dir, 'dist/index.html'), '<!doctype html><title>Peak</title>')
  writeFileSync(join(dir, 'dist/sw.js'), '// sw')
  idp = createServer((req, res) => {
    const url = new URL(req.url!, idpUrl)
    if (url.pathname.endsWith('/.well-known/openid-configuration')) {
      res.setHeader('content-type', 'application/json')
      return void res.end(JSON.stringify({ issuer: `${idpUrl}/application/o/peak/`, authorization_endpoint: `${idpUrl}/authorize`, token_endpoint: `${idpUrl}/token` }))
    }
    if (url.pathname === '/token') {
      let body = ''
      req.on('data', (c) => (body += c))
      req.on('end', () => {
        const p = new URLSearchParams(body)
        const ok = createHash('sha256').update(p.get('code_verifier') ?? '').digest('base64url') === next.challenge
        res.setHeader('content-type', 'application/json')
        if (!ok || req.headers.authorization !== `Basic ${Buffer.from('cid:secret').toString('base64')}`) { res.statusCode = 400; return void res.end('{"error":"invalid_grant"}') }
        const claims = { iss: `${idpUrl}/application/o/peak/`, aud: 'cid', exp: Math.floor(Date.now() / 1000) + 600, nonce: next.nonce, sub: next.sub, name: 'Anna', preferred_username: 'anna', email: next.email, groups: next.groups }
        res.end(JSON.stringify({ id_token: `x.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.y` }))
      })
      return
    }
    res.statusCode = 404; res.end()
  })
  idpUrl = await listen(idp)
  app = createApp({
    dataDir: join(dir, 'data'), staticDir: join(dir, 'dist'),
    env: { BASE_URL: 'http://peak.test', OIDC_ISSUER: `${idpUrl}/application/o/peak/`, OIDC_CLIENT_ID: 'cid', OIDC_CLIENT_SECRET: 'secret' },
  })
  base = await listen(app.server)
})
after(() => { app.server.close(); idp.close(); app.store.close(); rmSync(dir, { recursive: true }) })

/** Un utente con sessione web: restituisce gli header per chiamare l'API da «browser». */
const login = (sub: string) => {
  const u = app.store.upsertUser(sub, `${sub}@x.it`, sub, sub)
  return { user: u, h: { cookie: `peak=${app.store.createSession(u.id, 1)}`, 'sec-fetch-site': 'same-origin' } as Record<string, string> }
}
const call = (path: string, init: { method?: string; h?: Record<string, string>; body?: unknown; raw?: BodyInit } = {}) =>
  fetch(base + path, {
    method: init.method ?? (init.body !== undefined || init.raw ? 'POST' : 'GET'), redirect: 'manual',
    headers: { ...(init.body !== undefined ? { 'content-type': 'application/json' } : {}), ...init.h },
    body: init.raw ?? (init.body !== undefined ? JSON.stringify(init.body) : undefined),
  })

const exDoc = (name: string, sortIndex = 0) => ({ name, muscleGroup: 'petto', notes: '', incrementStep: 5, sortIndex, isArchived: false, createdAt: '2026-01-01T00:00:00Z' })

test('senza credenziali 401; il cookie vale solo same-origin; il Bearer vale ovunque', async () => {
  assert.equal((await call('/api/io')).status, 401)
  const { user, h } = login('s1')
  assert.equal((await call('/api/io', { h })).status, 200)
  assert.equal((await call('/api/io', { h: { cookie: h.cookie } })).status, 401, 'un link di un altro sito non deve usare la sessione')
  assert.equal((await call('/api/io', { h: { cookie: h.cookie, 'sec-fetch-site': 'cross-site' } })).status, 401)
  const token = app.store.createDevice(user.id, 'Peak · test')
  assert.equal((await call('/api/io', { h: { authorization: `Bearer ${token}` } })).status, 200)
  assert.equal((await call('/api/io', { h: { authorization: 'Bearer sbagliato' } })).status, 401)
  assert.equal((await call('/api/health')).status, 200)
})

test('sync: push, pull, tombstone, uuid in maiuscolo, ultimo scrittore vince', async () => {
  const { h } = login('s2')
  const t = NOW()
  const lower = U(1).replace(/0/g, 'a').toLowerCase()
  let r = await (await call('/api/sync', { h, body: { docs: [
    { kind: 'exercise', uuid: lower, data: exDoc('Chest Press'), m: t, deleted: false },
    { kind: 'setting', uuid: 'unit', data: { value: 'lb' }, m: t, deleted: false },
  ] } })).json() as { rev: number; rifiutati: unknown[]; scartati: unknown[] }
  assert.equal(r.scartati.length, 0)
  const pulled = await (await call('/api/sync?dopo=0', { h })).json() as { rev: number; docs: { kind: string; uuid: string; m: number }[] }
  assert.equal(pulled.docs.length, 2)
  assert.equal(pulled.docs.find((d) => d.kind === 'exercise')!.uuid, lower.toUpperCase())
  const up = lower.toUpperCase()
  // un client in ritardo (m più vecchia) perde, e riceve la versione del server
  r = await (await call('/api/sync', { h, body: { docs: [{ kind: 'exercise', uuid: up, data: exDoc('Vecchio'), m: t - 1000, deleted: false }] } })).json() as typeof r
  assert.equal(r.rifiutati.length, 1)
  assert.equal((r.rifiutati[0] as { data: { name: string } }).data.name, 'Chest Press')
  // a parità di m vince chi arriva dopo
  await call('/api/sync', { h, body: { docs: [{ kind: 'exercise', uuid: up, data: exDoc('Pari'), m: t, deleted: false }] } })
  // cancellazione con m più nuova → tombstone, e il pull incrementale la porta
  const before = (await (await call('/api/sync?dopo=0', { h })).json() as { rev: number }).rev
  await call('/api/sync', { h, body: { docs: [{ kind: 'exercise', uuid: up, data: null, m: t + 5, deleted: true }] } })
  const inc = await (await call(`/api/sync?dopo=${before}`, { h })).json() as { docs: { deleted: boolean; data: unknown }[] }
  assert.equal(inc.docs.length, 1)
  assert.equal(inc.docs[0].deleted, true)
  assert.equal(inc.docs[0].data, null)
})

test('sync: documenti non validi scartati uno per uno, m dal futuro rifiutata, limiti', async () => {
  const { h } = login('s3')
  const r = await (await call('/api/sync', { h, body: { docs: [
    { kind: 'exercise', uuid: U(2), data: { ...exDoc(''), name: '' }, m: NOW(), deleted: false },
    { kind: 'exercise', uuid: 'non-uuid', data: exDoc('x'), m: NOW(), deleted: false },
    { kind: 'boh', uuid: U(3), data: {}, m: NOW(), deleted: false },
    { kind: 'exercise', uuid: U(4), data: exDoc('Futuro'), m: NOW() + 10 * 86_400_000, deleted: false },
    { kind: 'setting', uuid: 'watchEnabled', data: { value: true }, m: NOW(), deleted: false },
    { kind: 'setting', uuid: 'unit', data: { value: 'stone' }, m: NOW(), deleted: false },
    { kind: 'set', uuid: U(5), data: { workout: U(6), exercise: null, weight: 99999, reps: 5, createdAt: '2026-01-01T00:00:00Z' }, m: NOW(), deleted: false },
    { kind: 'exercise', uuid: U(7), data: { ...exDoc('Ok'), extra: 'ignorato' }, m: NOW(), deleted: false },
  ] } })).json() as { scartati: { errore: string }[] }
  assert.deepEqual(r.scartati.map((s) => s.errore), ['name', 'uuid', 'kind', 'm', 'uuid', 'value', 'weight'])
  const pulled = await (await call('/api/sync?dopo=0', { h })).json() as { docs: { data: Record<string, unknown> }[] }
  assert.equal(pulled.docs.length, 1)
  assert.ok(!('extra' in pulled.docs[0].data), 'solo i campi noti')
  assert.equal((await call('/api/sync', { h, body: { docs: 'no' } })).status, 400)
  assert.equal((await call('/api/sync', { h, body: { docs: Array(1001).fill({}) } })).status, 400)
  assert.equal((await call('/api/sync?dopo=-1', { h })).status, 400)
})

test('ogni utente vede solo i propri dati', async () => {
  const a = login('iso-a'), b = login('iso-b')
  await call('/api/sync', { h: a.h, body: { docs: [{ kind: 'exercise', uuid: U(20), data: exDoc('Mio'), m: NOW(), deleted: false }] } })
  const seen = await (await call('/api/sync?dopo=0', { h: b.h })).json() as { docs: unknown[] }
  assert.equal(seen.docs.length, 0)
  // b scrive lo stesso uuid: è un documento suo, non tocca quello di a
  await call('/api/sync', { h: b.h, body: { docs: [{ kind: 'exercise', uuid: U(20), data: exDoc('Suo'), m: NOW() - 99999, deleted: false }] } })
  const mine = await (await call('/api/sync?dopo=0', { h: a.h })).json() as { docs: { data: { name: string } }[] }
  assert.equal(mine.docs[0].data.name, 'Mio')
  assert.equal((await call('/api/foto/' + 'a'.repeat(64), { h: b.h })).status, 404)
})

test('catalogo: 98 macchinari una volta sola; poi 409', async () => {
  const { h } = login('cat')
  const r = await (await call('/api/catalogo', { h, method: 'POST' })).json() as { esercizi: number }
  assert.equal(r.esercizi, 98)
  assert.equal((await call('/api/catalogo', { h, method: 'POST' })).status, 409)
  const pulled = await (await call('/api/sync?dopo=0', { h })).json() as { docs: { data: { sortIndex: number } }[] }
  assert.equal(pulled.docs.length, 98)
})

test('importa: primo=1 su account non vuoto 409, file non valido 422, unisci e sostituisci', async () => {
  const { h } = login('imp')
  const file = {
    version: 1, exportedAt: '2026-10-07T16:54:28Z',
    exercises: [{ uuid: U(30), name: 'Lat Machine', muscleGroup: 'schiena', notes: '', incrementStep: 5, sortIndex: 0, isArchived: false, createdAt: '2026-09-01T08:00:00Z', records: [] }],
    workouts: [],
  }
  assert.equal((await call('/api/importa', { h, body: file })).status, 400, 'senza modo')
  assert.equal((await call('/api/importa?modo=unisci', { h, body: { version: 9, exercises: [], workouts: [] } })).status, 422)
  const first = await call('/api/importa?modo=unisci&primo=1', { h, body: file })
  assert.equal(first.status, 200)
  assert.equal((await call('/api/importa?modo=unisci&primo=1', { h, body: file })).status, 409)
  const merged = await (await call('/api/importa?modo=unisci', { h, body: file })).json() as { conteggi: { esercizi: number } }
  assert.equal(merged.conteggi.esercizi, 0)
  await call('/api/importa?modo=sostituisci', { h, body: { ...file, exercises: [] } })
  const pulled = await (await call('/api/sync?dopo=0', { h })).json() as { docs: { deleted: boolean }[] }
  assert.deepEqual(pulled.docs.map((d) => d.deleted), [true])
  // dopo aver cancellato tutto l'account è di nuovo «vuoto» per la prima scelta
  assert.equal((await call('/api/catalogo', { h, method: 'POST' })).status, 200)
})

test('foto: solo JPEG, lo sha256 deve coincidere, si legge con cache immutabile', async () => {
  const { h } = login('foto')
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xdb, 9, 9, 9, 9])
  const sha = createHash('sha256').update(jpeg).digest('hex')
  assert.equal((await call(`/api/foto/${sha}`, { h, method: 'PUT', raw: jpeg })).status, 200)
  assert.equal((await call(`/api/foto/${'0'.repeat(64)}`, { h, method: 'PUT', raw: jpeg })).status, 400)
  const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4])
  assert.equal((await call(`/api/foto/${createHash('sha256').update(png).digest('hex')}`, { h, method: 'PUT', raw: png })).status, 400)
  assert.equal((await call(`/api/foto/${sha}`, { h, method: 'PUT', raw: Buffer.alloc(1_100_000, 0xff) })).status, 413)
  const got = await call(`/api/foto/${sha}`, { h })
  assert.equal(got.headers.get('content-type'), 'image/jpeg')
  assert.match(got.headers.get('cache-control')!, /immutable/)
  assert.deepEqual(Buffer.from(await got.arrayBuffer()), jpeg)
})

test('backup: JSON scaricabile nel formato dell\'app', async () => {
  const { h } = login('bk')
  await call('/api/catalogo', { h, method: 'POST' })
  const r = await call('/api/backup', { h })
  assert.match(r.headers.get('content-disposition')!, /^attachment; filename="Peak-\d{4}-\d\d-\d\d\.json"$/)
  const file = await r.json() as { version: number; exercises: unknown[] }
  assert.equal(file.version, 1)
  assert.equal(file.exercises.length, 98)
})

test('dispositivi: si elencano e si revocano solo i propri', async () => {
  const a = login('dv-a'), b = login('dv-b')
  const tokA = app.store.createDevice(a.user.id, 'Peak · iPhone di A')
  const list = await (await call('/api/dispositivi', { h: a.h })).json() as { dispositivi: { id: number; name: string }[] }
  assert.equal(list.dispositivi.length, 1)
  const id = list.dispositivi[0].id
  assert.equal((await call(`/api/dispositivi/${id}`, { h: b.h, method: 'DELETE' })).status, 404)
  assert.equal((await call(`/api/dispositivi/${id}`, { h: a.h, method: 'DELETE' })).status, 200)
  assert.equal((await call('/api/io', { h: { authorization: `Bearer ${tokA}` } })).status, 401, 'il token revocato non vale più')
})

test('revokeAccessExcept: toglie sessioni e dispositivi a chi non è nell\'elenco, mai i dati', async () => {
  const keep = login('rv-keep'), drop = login('rv-drop')
  app.store.createDevice(drop.user.id, 'Peak · x')
  await call('/api/sync', { h: drop.h, body: { docs: [{ kind: 'exercise', uuid: U(40), data: exDoc('Resta'), m: NOW(), deleted: false }] } })
  app.store.revokeAccessExcept([`${keep.user.sub}@x.it`.toUpperCase()])
  assert.equal((await call('/api/io', { h: keep.h })).status, 200)
  assert.equal((await call('/api/io', { h: drop.h })).status, 401)
  assert.equal(app.store.live(drop.user.id).length, 1, 'i dati restano')
})

test('pagina e statici: senza sessione si va al benvenuto; gli asset sono pubblici', async () => {
  const r = await call('/')
  assert.equal(r.status, 302)
  assert.equal(r.headers.get('location'), '/benvenuto.html')
  const { h } = login('pg')
  const page = await call('/', { h: { cookie: h.cookie } })
  assert.equal(page.status, 200)
  assert.match(page.headers.get('content-security-policy')!, /default-src 'self'/)
  const sw = await call('/sw.js')
  assert.equal(sw.status, 200)
  assert.equal(sw.headers.get('cache-control'), 'no-store', 'il service worker non va mai in cache')
  assert.equal((await call('/segreto.txt')).status, 404)
  assert.equal((await call('/..%2f..%2fpackage.json')).status, 404)
  assert.equal((await call('/api/inesistente', { h })).status, 404)
})

// ---- login con yG (OIDC) -------------------------------------------------------------------------

test('login web: PKCE, nonce, gruppo; poi il cookie apre la pagina', async () => {
  const start = await call('/auth/login')
  assert.equal(start.status, 302)
  const auth = new URL(start.headers.get('location')!)
  assert.equal(auth.searchParams.get('redirect_uri'), 'http://peak.test/auth/callback', 'solo da BASE_URL')
  assert.equal(auth.searchParams.get('code_challenge_method'), 'S256')
  next.nonce = auth.searchParams.get('nonce')!; next.challenge = auth.searchParams.get('code_challenge')!
  next.groups = ['svc-peak']; next.sub = 'oidc-1'
  const flow = start.headers.getSetCookie().find((c) => c.startsWith('peak_flow='))!.split(';')[0]
  // state sbagliato → si riparte dal login, senza sessione
  const wrong = await call('/auth/callback?code=c&state=sbagliato', { h: { cookie: flow } })
  assert.equal(wrong.headers.get('location'), '/auth/login')
  const done = await call(`/auth/callback?code=c&state=${auth.searchParams.get('state')}`, { h: { cookie: flow } })
  assert.equal(done.status, 302)
  assert.equal(done.headers.get('location'), '/')
  const session = done.headers.getSetCookie().find((c) => c.startsWith('peak='))!
  assert.match(session, /HttpOnly; SameSite=Lax/)
  assert.equal((await call('/', { h: { cookie: session.split(';')[0] } })).status, 200)
  assert.equal(app.store.userBySub('oidc-1')!.email, 'a@b.it')
})

test('login web: chi non è nel gruppo vede la pagina 403 di Peak, nessuna sessione', async () => {
  const start = await call('/auth/login')
  const auth = new URL(start.headers.get('location')!)
  next.nonce = auth.searchParams.get('nonce')!; next.challenge = auth.searchParams.get('code_challenge')!
  next.groups = ['altro']; next.sub = 'oidc-2'
  const flow = start.headers.getSetCookie().find((c) => c.startsWith('peak_flow='))!.split(';')[0]
  const r = await call(`/auth/callback?code=c&state=${auth.searchParams.get('state')}`, { h: { cookie: flow } })
  assert.equal(r.status, 403)
  assert.ok(!r.headers.getSetCookie().some((c) => c.startsWith('peak=') && !c.includes('Max-Age=0')))
  assert.equal(app.store.userBySub('oidc-2'), undefined)
})

test('login iPhone: conferma obbligatoria, codice monouso, verifier PKCE, poi Bearer', async () => {
  const verifier = 'v'.repeat(64)
  const challenge = createHash('sha256').update(verifier).digest('base64url')
  const start = await call(`/auth/login?app=${challenge}&nome=iPhone%20di%20Anna`)
  const auth = new URL(start.headers.get('location')!)
  next.nonce = auth.searchParams.get('nonce')!; next.challenge = auth.searchParams.get('code_challenge')!
  next.groups = ['svc-peak']; next.sub = 'oidc-3'
  const flow = start.headers.getSetCookie().find((c) => c.startsWith('peak_flow='))!.split(';')[0]
  const cb = `/auth/callback?code=c&state=${auth.searchParams.get('state')}`
  const confirm = await call(cb, { h: { cookie: flow } })
  assert.equal(confirm.status, 200, 'mai in silenzio: prima la pagina di conferma')
  const html = await confirm.text()
  assert.match(html, /iPhone di Anna/)
  const code = /name="code" value="([^"]+)"/.exec(html)![1]
  // Safari che ricarica il callback riceve la stessa pagina (stesso codice), non un errore
  const reload = await call(cb, { h: { cookie: flow } })
  assert.equal(reload.status, 200)
  assert.equal(/name="code" value="([^"]+)"/.exec(await reload.text())![1], code)
  // il form deve arrivare dal sito stesso; un tentativo da fuori brucia il codice
  const form = (c: string, site?: string) => call('/auth/app/conferma', { method: 'POST', h: { 'content-type': 'application/x-www-form-urlencoded', ...(site ? { 'sec-fetch-site': site } : {}) }, raw: `code=${c}` })
  assert.equal((await form(code, 'cross-site')).status, 400)
  assert.equal((await form(code, 'same-origin')).status, 400, 'il codice è monouso')
})

test('login iPhone: percorso completo', async () => {
  const verifier = 'w'.repeat(64)
  const challenge = createHash('sha256').update(verifier).digest('base64url')
  const start = await call(`/auth/login?app=${challenge}&nome=iPhone`)
  const auth = new URL(start.headers.get('location')!)
  next.nonce = auth.searchParams.get('nonce')!; next.challenge = auth.searchParams.get('code_challenge')!
  next.groups = ['svc-peak']; next.sub = 'oidc-4'
  const flow = start.headers.getSetCookie().find((c) => c.startsWith('peak_flow='))!.split(';')[0]
  const html = await (await call(`/auth/callback?code=c&state=${auth.searchParams.get('state')}`, { h: { cookie: flow } })).text()
  const code = /name="code" value="([^"]+)"/.exec(html)![1]
  const conferma = await call('/auth/app/conferma', { method: 'POST', h: { 'content-type': 'application/x-www-form-urlencoded', 'sec-fetch-site': 'same-origin' }, raw: `code=${code}` })
  assert.equal(conferma.status, 302)
  const target = new URL(conferma.headers.get('location')!)
  assert.equal(target.protocol, 'peak:')
  const final = target.searchParams.get('code')!
  const tok = (c: string, v: string) => call('/auth/app/token', { body: { code: c, verifier: v } })
  assert.equal((await tok(final, 'x'.repeat(64))).status, 400, 'verifier sbagliato')
  assert.equal((await tok(final, verifier)).status, 400, 'il codice sbagliato è comunque bruciato')
  // nuovo giro, stavolta giusto
  const s2 = await call(`/auth/login?app=${challenge}&nome=iPhone`)
  const a2 = new URL(s2.headers.get('location')!)
  next.nonce = a2.searchParams.get('nonce')!; next.challenge = a2.searchParams.get('code_challenge')!
  const f2 = s2.headers.getSetCookie().find((c) => c.startsWith('peak_flow='))!.split(';')[0]
  const h2 = await (await call(`/auth/callback?code=c&state=${a2.searchParams.get('state')}`, { h: { cookie: f2 } })).text()
  const c2 = /name="code" value="([^"]+)"/.exec(h2)![1]
  const r2 = await call('/auth/app/conferma', { method: 'POST', h: { 'content-type': 'application/x-www-form-urlencoded', 'sec-fetch-site': 'same-origin' }, raw: `code=${c2}` })
  const ok = await tok(new URL(r2.headers.get('location')!).searchParams.get('code')!, verifier)
  assert.equal(ok.status, 200)
  assert.equal(ok.headers.get('cache-control'), 'no-store')
  const { token, sub } = await ok.json() as { token: string; sub: string }
  assert.equal(sub, 'oidc-4')
  assert.equal((await call('/api/io', { h: { authorization: `Bearer ${token}` } })).status, 200)
  const devices = app.store.listDevices(app.store.userBySub('oidc-4')!.id)
  assert.deepEqual(devices.map((d) => d.name), ['Peak · iPhone'], 'un nuovo login dallo stesso dispositivo sostituisce il token')
})
