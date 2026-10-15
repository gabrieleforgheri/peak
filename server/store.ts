// SQLite (node:sqlite, WAL): utenti, sessioni, dispositivi, documenti sincronizzati, foto.
// Un solo processo e DatabaseSync sincrono: il contatore `rev` in memoria è sicuro (vedi push()).

import { DatabaseSync } from 'node:sqlite'
import { createHash, randomBytes } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import {
  KINDS, LEGACY_SETTINGS, SETTING_DEFAULTS, normUuid, parseDoc, upgradeDoc, type DocDataMap, type Kind, type LiveDoc,
} from '../common/docs.ts'
import type { ImportPlan } from './backup.ts'

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY, sub TEXT NOT NULL UNIQUE, email TEXT NOT NULL DEFAULT '',
  username TEXT NOT NULL DEFAULT '', name TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS users_email ON users(email);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), expires_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS devices (
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, used_at INTEGER);
CREATE TABLE IF NOT EXISTS docs (
  user_id INTEGER NOT NULL, kind TEXT NOT NULL, uuid TEXT NOT NULL, data TEXT, m INTEGER NOT NULL,
  rev INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (user_id, kind, uuid)) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS docs_rev ON docs(user_id, rev);
CREATE TABLE IF NOT EXISTS photos (
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, sha TEXT NOT NULL, data BLOB NOT NULL,
  created_at INTEGER NOT NULL, UNIQUE(user_id, sha));
CREATE TABLE IF NOT EXISTS push_subs (
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, endpoint TEXT NOT NULL UNIQUE, due_at INTEGER);
`

export interface User { id: number; sub: string; email: string; username: string; name: string }
export interface Device { id: number; name: string; created_at: number; used_at: number | null }
export interface WireDoc { kind: Kind; uuid: string; data: unknown; m: number; deleted: boolean }
export interface PushResult {
  rev: number
  rifiutati: WireDoc[]
  scartati: { kind: string; uuid: string; errore: string }[]
}

const DAY = 86_400_000
const hash = (token: string) => createHash('sha256').update(token).digest('hex')
const newToken = () => randomBytes(32).toString('base64url')
/** Una m dal futuro bloccherebbe per sempre il documento: si rifiuta oltre due giorni. */
const MAX_FUTURE_MS = 2 * DAY

export function openStore(dataDir: string) {
  mkdirSync(dataDir, { recursive: true })
  const db = new DatabaseSync(join(dataDir, 'peak.db'))
  db.exec('PRAGMA journal_mode = WAL')
  db.exec('PRAGMA busy_timeout = 5000')
  db.exec('PRAGMA foreign_keys = ON')
  db.exec(SCHEMA)
  db.exec('PRAGMA user_version = 1')

  let rev = Number((db.prepare('SELECT COALESCE(MAX(rev), 0) AS r FROM docs').get() as { r: number }).r)

  const q = {
    userBySub: db.prepare('SELECT * FROM users WHERE sub = ?'),
    userById: db.prepare('SELECT * FROM users WHERE id = ?'),
    userByEmail: db.prepare('SELECT * FROM users WHERE lower(email) = lower(?) AND email != \'\' ORDER BY id LIMIT 1'),
    insertUser: db.prepare('INSERT INTO users (sub, email, username, name, created_at) VALUES (?, ?, ?, ?, ?)'),
    updateUser: db.prepare('UPDATE users SET email = ?, username = ?, name = ? WHERE id = ?'),
    delExpired: db.prepare('DELETE FROM sessions WHERE expires_at < ?'),
    insertSession: db.prepare('INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)'),
    sessionUser: db.prepare('SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?'),
    delSession: db.prepare('DELETE FROM sessions WHERE token_hash = ?'),
    insertDevice: db.prepare('INSERT INTO devices (user_id, name, token_hash, created_at) VALUES (?, ?, ?, ?)'),
    delDeviceByName: db.prepare('DELETE FROM devices WHERE user_id = ? AND name = ?'),
    deviceUser: db.prepare('SELECT u.*, d.id AS device_id, d.used_at AS used_at FROM devices d JOIN users u ON u.id = d.user_id WHERE d.token_hash = ?'),
    touchDevice: db.prepare('UPDATE devices SET used_at = ? WHERE id = ?'),
    listDevices: db.prepare('SELECT id, name, created_at, used_at FROM devices WHERE user_id = ? ORDER BY created_at DESC'),
    delDevice: db.prepare('DELETE FROM devices WHERE id = ? AND user_id = ?'),
    getDoc: db.prepare('SELECT kind, uuid, data, m, deleted FROM docs WHERE user_id = ? AND kind = ? AND uuid = ?'),
    putDoc: db.prepare(`INSERT INTO docs (user_id, kind, uuid, data, m, rev, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (user_id, kind, uuid) DO UPDATE SET data = excluded.data, m = excluded.m, rev = excluded.rev, deleted = excluded.deleted`),
    pull: db.prepare('SELECT kind, uuid, data, m, deleted FROM docs WHERE user_id = ? AND rev > ? ORDER BY rev'),
    live: db.prepare('SELECT kind, uuid, data FROM docs WHERE user_id = ? AND deleted = 0'),
    hasData: db.prepare("SELECT 1 FROM docs WHERE user_id = ? AND deleted = 0 AND kind != 'setting' LIMIT 1"),
    insertPhoto: db.prepare('INSERT OR IGNORE INTO photos (user_id, sha, data, created_at) VALUES (?, ?, ?, ?)'),
    getPhoto: db.prepare('SELECT data FROM photos WHERE user_id = ? AND sha = ?'),
    countPhotos: db.prepare('SELECT COUNT(*) AS n FROM photos WHERE user_id = ?'),
    sweepPhotos: db.prepare(`DELETE FROM photos WHERE created_at < ? AND NOT EXISTS (
      SELECT 1 FROM docs d WHERE d.user_id = photos.user_id AND d.kind = 'exercise' AND d.deleted = 0
      AND json_extract(d.data, '$.photo') = photos.sha)`),
    allUsers: db.prepare('SELECT * FROM users'),
    delUserSessions: db.prepare('DELETE FROM sessions WHERE user_id = ?'),
    delUserDevices: db.prepare('DELETE FROM devices WHERE user_id = ?'),
  }

  const tx = <T>(fn: () => T): T => {
    db.exec('BEGIN IMMEDIATE')
    try { const out = fn(); db.exec('COMMIT'); return out } catch (e) { db.exec('ROLLBACK'); throw e }
  }

  const toWire = (r: Record<string, unknown>): WireDoc => ({
    kind: r.kind as Kind, uuid: r.uuid as string, data: r.data ? upgradeDoc(r.kind as Kind, JSON.parse(r.data as string)) : null,
    m: Number(r.m), deleted: r.deleted === 1,
  })

  const write = (userId: number, kind: Kind, uuid: string, data: unknown, m: number, deleted: boolean) => {
    q.putDoc.run(userId, kind, uuid, deleted ? null : JSON.stringify(data), m, ++rev, deleted ? 1 : 0)
  }

  return {
    db,
    close: () => db.close(),
    get rev() { return rev },

    // ---- utenti e sessioni ----
    userById: (id: number) => q.userById.get(id) as unknown as User | undefined,
    userBySub: (sub: string) => q.userBySub.get(sub) as unknown as User | undefined,
    /** Per sub; se non c'è, per email (il sub può cambiare fra provider authentik). Mai crea. */
    findUser(sub: string, email: string): User | undefined {
      return (q.userBySub.get(sub) as unknown as User | undefined)
        ?? (email ? (q.userByEmail.get(email) as unknown as User | undefined) : undefined)
    },
    upsertUser(sub: string, email: string, username: string, name: string, now = Date.now()): User {
      const found = q.userBySub.get(sub) as unknown as User | undefined
      if (found) { q.updateUser.run(email, username, name, found.id); return { ...found, email, username, name } }
      const id = Number(q.insertUser.run(sub, email, username, name, now).lastInsertRowid)
      return { id, sub, email, username, name }
    },
    createSession(userId: number, days: number, now = Date.now()): string {
      q.delExpired.run(now)
      const token = newToken()
      q.insertSession.run(hash(token), userId, now + days * DAY)
      return token
    },
    sessionUser: (token: string, now = Date.now()) => q.sessionUser.get(hash(token), now) as unknown as User | undefined,
    deleteSession: (token: string) => void q.delSession.run(hash(token)),

    createDevice(userId: number, name: string, now = Date.now()): string {
      q.delDeviceByName.run(userId, name)
      const token = newToken()
      q.insertDevice.run(userId, name, hash(token), now)
      return token
    },
    deviceUser(token: string, now = Date.now()): { user: User; deviceId: number } | undefined {
      const r = q.deviceUser.get(hash(token)) as unknown as (User & { device_id: number; used_at: number | null }) | undefined
      if (!r) return undefined
      if (!r.used_at || now - r.used_at > 60_000) q.touchDevice.run(now, r.device_id)
      return { user: { id: r.id, sub: r.sub, email: r.email, username: r.username, name: r.name }, deviceId: r.device_id }
    },
    listDevices: (userId: number) => q.listDevices.all(userId) as unknown as Device[],
    deleteDevice: (userId: number, id: number) => Number(q.delDevice.run(id, userId).changes) > 0,

    /** Toglie sessioni e dispositivi a chi non è fra le email date. Non tocca mai utenti o dati. */
    revokeAccessExcept(emails: Iterable<string>): { utenti: number } {
      const keep = new Set([...emails].map((e) => e.trim().toLowerCase()).filter(Boolean))
      let utenti = 0
      tx(() => {
        for (const u of q.allUsers.all() as unknown as User[]) {
          if (u.email && keep.has(u.email.toLowerCase())) continue
          const a = Number(q.delUserSessions.run(u.id).changes)
          const b = Number(q.delUserDevices.run(u.id).changes)
          if (a + b > 0) utenti++
        }
      })
      return { utenti }
    },

    // ---- documenti ----
    pull(userId: number, dopo: number): { rev: number; docs: WireDoc[] } {
      return { rev, docs: (q.pull.all(userId, dopo) as Record<string, unknown>[]).map(toWire) }
    },

    /**
     * Ultimo scrittore vince su `m` (a parità vince chi arriva dopo). Un documento perso torna al client in
     * `rifiutati` con la versione del server: senza, un client col cursore già oltre non la vedrebbe mai.
     */
    push(userId: number, docs: WireDoc[], now = Date.now()): PushResult {
      const out: PushResult = { rev: 0, rifiutati: [], scartati: [] }
      tx(() => {
        for (const d of docs) {
          const reject = (errore: string) => out.scartati.push({ kind: String(d?.kind), uuid: String(d?.uuid), errore })
          if (!d || !(KINDS as readonly unknown[]).includes(d.kind)) { reject('kind'); continue }
          if (d.kind === 'setting' && LEGACY_SETTINGS.includes(d.uuid)) continue
          const uuid = d.kind === 'setting' ? (d.uuid in SETTING_DEFAULTS ? d.uuid : null) : normUuid(d.uuid)
          if (!uuid) { reject('uuid'); continue }
          if (!Number.isInteger(d.m) || d.m < 0 || d.m > now + MAX_FUTURE_MS) { reject('m'); continue }
          let data: unknown = null
          if (!d.deleted) {
            const parsed = parseDoc(d.kind, uuid, d.data)
            if ('error' in parsed) { reject(parsed.error); continue }
            data = parsed.data
          }
          const row = q.getDoc.get(userId, d.kind, uuid) as Record<string, unknown> | undefined
          if (row && d.m < Number(row.m)) { out.rifiutati.push(toWire(row)); continue }
          write(userId, d.kind, uuid, data, d.m, !!d.deleted)
        }
      })
      out.rev = rev
      return out
    },

    live(userId: number): LiveDoc[] {
      return (q.live.all(userId) as Record<string, unknown>[]).map((r) => ({
        kind: r.kind as Kind, uuid: r.uuid as string, data: upgradeDoc(r.kind as Kind, JSON.parse(r.data as string)) as DocDataMap[Kind],
      }))
    },
    hasData: (userId: number) => q.hasData.get(userId) !== undefined,

    /** Applica un piano d'importazione (o il catalogo) in una transazione, con `m` sempre in avanti. */
    applyPlan(userId: number, plan: Pick<ImportPlan, 'put' | 'del' | 'photos'>, now = Date.now()) {
      tx(() => {
        const next = (kind: Kind, uuid: string) => {
          const row = q.getDoc.get(userId, kind, uuid) as { m: number } | undefined
          return Math.max(now, row ? Number(row.m) + 1 : 0)
        }
        for (const [sha, bytes] of plan.photos) q.insertPhoto.run(userId, sha, bytes, now)
        for (const p of plan.put) write(userId, p.kind, p.uuid, p.data, next(p.kind, p.uuid), false)
        for (const d of plan.del) write(userId, d.kind, d.uuid, null, next(d.kind, d.uuid), true)
      })
    },

    /** Scrive un solo documento lato server (es. lastExportDate). */
    setSetting(userId: number, key: string, value: unknown, now = Date.now()) {
      tx(() => {
        const row = q.getDoc.get(userId, 'setting', key) as { m: number } | undefined
        write(userId, 'setting', key, { value }, Math.max(now, row ? Number(row.m) + 1 : 0), false)
      })
    },

    // ---- foto ----
    savePhoto: (userId: number, sha: string, bytes: Uint8Array, now = Date.now()) => void q.insertPhoto.run(userId, sha, bytes, now),
    getPhoto: (userId: number, sha: string) => (q.getPhoto.get(userId, sha) as { data: Uint8Array } | undefined)?.data ?? null,
    photoCount: (userId: number) => Number((q.countPhotos.get(userId) as { n: number }).n),
    /** Foto che nessun esercizio vivo richiama e più vecchie di 24 h. */
    sweepPhotos: (now = Date.now()) => Number(q.sweepPhotos.run(now - DAY).changes),

    /** Copia coerente del database per il backup di Pelican (VACUUM INTO rifiuta un file esistente). */
    backupTo(path: string) { db.exec(`VACUUM INTO '${path.replace(/'/g, "''")}'`) },
  }
}

export type Store = ReturnType<typeof openStore>
