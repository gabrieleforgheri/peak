// Web Push per la fine del recupero. Senza dipendenze: il messaggio non ha corpo (il testo lo mette il service
// worker, tradotto) e si firma con un JWT VAPID (ES256) via node:crypto.
// Il server tiene l'orario di fine per ogni iscrizione e manda l'avviso con un setTimeout; all'avvio ripianifica.
// ponytail: se Apple scartasse i messaggi senza corpo, aggiungere la cifratura aes128gcm (RFC 8291) con p256dh e auth.

import { createPrivateKey, generateKeyPairSync, sign, type KeyObject } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Store } from './store.ts'

/** Solo i servizi push dei browser: il server non deve mai chiamare un indirizzo scelto dall'utente. */
const ALLOWED = [/(^|\.)push\.apple\.com$/, /^fcm\.googleapis\.com$/, /^updates\.push\.services\.mozilla\.com$/, /\.notify\.windows\.com$/]

export function endpointOk(endpoint: unknown): endpoint is string {
  if (typeof endpoint !== 'string' || endpoint.length > 600) return false
  try {
    const u = new URL(endpoint)
    return u.protocol === 'https:' && !u.username && !u.password && ALLOWED.some((re) => re.test(u.hostname))
  } catch { return false }
}

const b64u = (b: Buffer | string) => Buffer.from(b).toString('base64url')

export type Send = (endpoint: string, headers: Record<string, string>) => Promise<number>
const defaultSend: Send = async (endpoint, headers) => (await fetch(endpoint, { method: 'POST', headers, signal: AbortSignal.timeout(10_000) })).status

export function createPush(store: Store, dataDir: string, baseUrl: string, send: Send = defaultSend) {
  const file = join(dataDir, 'vapid.json')
  if (!existsSync(file)) {
    const { privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' })
    writeFileSync(file, JSON.stringify(privateKey.export({ format: 'jwk' })), { mode: 0o600 })
  }
  const jwk = JSON.parse(readFileSync(file, 'utf8')) as { x: string; y: string; d: string }
  const key: KeyObject = createPrivateKey({ key: { kty: 'EC', crv: 'P-256', ...jwk }, format: 'jwk' })
  /** La chiave pubblica non compressa (0x04 ‖ x ‖ y), come la vuole pushManager.subscribe. */
  const publicKey = b64u(Buffer.concat([Buffer.from([4]), Buffer.from(jwk.x, 'base64url'), Buffer.from(jwk.y, 'base64url')]))

  const q = {
    upsert: store.db.prepare('INSERT INTO push_subs (user_id, endpoint, due_at) VALUES (?, ?, NULL) ON CONFLICT (endpoint) DO UPDATE SET user_id = excluded.user_id'),
    find: store.db.prepare('SELECT id, user_id, endpoint, due_at FROM push_subs WHERE endpoint = ?'),
    due: store.db.prepare('UPDATE push_subs SET due_at = ? WHERE id = ?'),
    del: store.db.prepare('DELETE FROM push_subs WHERE id = ?'),
    pending: store.db.prepare('SELECT id, endpoint, due_at FROM push_subs WHERE due_at IS NOT NULL'),
  }
  type Sub = { id: number; user_id: number; endpoint: string; due_at: number | null }
  const timers = new Map<number, ReturnType<typeof setTimeout>>()

  function jwt(audience: string): string {
    const h = b64u(JSON.stringify({ typ: 'JWT', alg: 'ES256' }))
    const p = b64u(JSON.stringify({ aud: audience, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: baseUrl }))
    return `${h}.${p}.${b64u(sign('sha256', Buffer.from(`${h}.${p}`), { key, dsaEncoding: 'ieee-p1363' }))}`
  }

  async function fire(id: number, endpoint: string) {
    timers.delete(id)
    q.due.run(null, id)
    try {
      const status = await send(endpoint, {
        authorization: `vapid t=${jwt(new URL(endpoint).origin)}, k=${publicKey}`, ttl: '120', urgency: 'high', 'content-length': '0',
      })
      if (status === 404 || status === 410) q.del.run(id) // iscrizione scaduta: il browser non la riconosce più
      else if (status >= 400) console.error('push', status, new URL(endpoint).hostname)
    } catch (e) { console.error('push', String(e)) }
  }

  function plan(id: number, endpoint: string, dueAt: number, now = Date.now()) {
    clearTimeout(timers.get(id))
    timers.set(id, setTimeout(() => void fire(id, endpoint), Math.max(0, dueAt - now)))
  }

  return {
    publicKey,
    subscribe(userId: number, endpoint: string) { q.upsert.run(userId, endpoint) },
    unsubscribe(userId: number, endpoint: string) {
      const s = q.find.get(endpoint) as Sub | undefined
      if (s && s.user_id === userId) { clearTimeout(timers.get(s.id)); timers.delete(s.id); q.del.run(s.id) }
    },
    /** Pianifica l'avviso per `endsAt` (ms). Chi lo chiede deve essere il proprietario dell'iscrizione. */
    schedule(userId: number, endpoint: string, endsAt: number, now = Date.now()): boolean {
      const s = q.find.get(endpoint) as Sub | undefined
      if (!s || s.user_id !== userId || !(endsAt > now - 5000) || endsAt > now + 3600_000) return false
      q.due.run(endsAt, s.id)
      plan(s.id, s.endpoint, endsAt, now)
      return true
    },
    cancel(userId: number, endpoint: string) {
      const s = q.find.get(endpoint) as Sub | undefined
      if (!s || s.user_id !== userId) return
      clearTimeout(timers.get(s.id)); timers.delete(s.id)
      q.due.run(null, s.id)
    },
    /** All'avvio: gli avvisi ancora in attesa si ripianificano, quelli scaduti da più di un minuto si lasciano perdere. */
    boot(now = Date.now()) {
      for (const s of q.pending.all() as { id: number; endpoint: string; due_at: number }[]) {
        if (s.due_at < now - 60_000) q.due.run(null, s.id)
        else plan(s.id, s.endpoint, s.due_at, now)
      }
    },
    stop() { for (const t of timers.values()) clearTimeout(t); timers.clear() },
  }
}
export type Push = ReturnType<typeof createPush>
