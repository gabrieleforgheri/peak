// API per web e iPhone: sincronizzazione, foto, importazione, catalogo, backup, dispositivi.
// Tutto vale per l'utente autenticato; ogni documento in ingresso passa da parseDoc (confine di fiducia).

import { createHash } from 'node:crypto'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { isoSec, newUuid, type Kind, type MuscleGroup } from '../common/docs.ts'
import { CATALOG } from '../common/catalog.ts'
import { exportBackup, parseBackup, planImport, serializeBackup } from './backup.ts'
import type { Auth } from './auth.ts'
import { endpointOk, type Push } from './push.ts'
import { HttpError, readBody, readJson, sendJson, SECURITY_HEADERS } from './http.ts'
import type { Store, WireDoc } from './store.ts'

const MAX_SYNC_BODY = 4_000_000
const MAX_SYNC_DOCS = 1000
const MAX_IMPORT_BODY = 32_000_000
const MAX_PHOTO = 1_000_000
const MAX_PHOTOS_PER_USER = 500

export function createApi(store: Store, auth: Auth, push: Push, commit: () => string) {
  /** Gestisce /api/*. Restituisce false se il percorso non è suo. */
  return async function handle(req: IncomingMessage, res: ServerResponse, path: string, query: URLSearchParams): Promise<boolean> {
    if (!path.startsWith('/api/')) return false
    const method = req.method ?? 'GET'

    if (path === '/api/health' && method === 'GET') { sendJson(res, 200, { ok: true, commit: commit() }); return true }

    const who = auth.userOf(req, 'api')
    if (!who) { sendJson(res, 401, { error: 'non autenticato' }); return true }
    const { user } = who

    if (path === '/api/io' && method === 'GET') {
      sendJson(res, 200, { sub: user.sub, nome: user.name || user.username, username: user.username, vapid: push.publicKey })
      return true
    }

    if (path === '/api/sync' && method === 'GET') {
      const dopo = Number(query.get('dopo') ?? 0)
      if (!Number.isInteger(dopo) || dopo < 0) throw new HttpError(400, 'dopo')
      sendJson(res, 200, store.pull(user.id, dopo))
      return true
    }

    if (path === '/api/sync' && method === 'POST') {
      const body = (await readJson(req, MAX_SYNC_BODY)) as { docs?: unknown }
      if (!Array.isArray(body?.docs) || body.docs.length > MAX_SYNC_DOCS) throw new HttpError(400, 'docs')
      sendJson(res, 200, store.push(user.id, body.docs as WireDoc[]))
      return true
    }

    const foto = /^\/api\/foto\/([0-9a-f]{64})$/.exec(path)
    if (foto && method === 'GET') {
      const bytes = store.getPhoto(user.id, foto[1])
      if (!bytes) throw new HttpError(404, 'foto')
      // indirizzata dal suo sha256: il contenuto non cambia mai
      res.writeHead(200, { 'content-type': 'image/jpeg', 'cache-control': 'private, max-age=31536000, immutable', ...SECURITY_HEADERS })
      res.end(bytes)
      return true
    }
    if (foto && method === 'PUT') {
      const bytes = await readBody(req, MAX_PHOTO)
      if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) throw new HttpError(400, 'non è un JPEG')
      if (createHash('sha256').update(bytes).digest('hex') !== foto[1]) throw new HttpError(400, 'sha256 diverso')
      if (!store.getPhoto(user.id, foto[1]) && store.photoCount(user.id) >= MAX_PHOTOS_PER_USER) throw new HttpError(409, 'troppe foto')
      store.savePhoto(user.id, foto[1], bytes)
      sendJson(res, 200, { ok: true })
      return true
    }

    // Unico percorso per ogni operazione in blocco: importa un file, carica i dati di un iPhone, unisci.
    if (path === '/api/importa' && method === 'POST') {
      const modo = query.get('modo')
      if (modo !== 'unisci' && modo !== 'sostituisci') throw new HttpError(400, 'modo')
      const parsed = parseBackup(await readJson(req, MAX_IMPORT_BODY))
      if ('error' in parsed) { sendJson(res, 422, parsed); return true }
      // la prima scelta su un account vuoto: due dispositivi non possono farla entrambi
      if (query.get('primo') === '1' && store.hasData(user.id)) { sendJson(res, 409, { error: 'account non vuoto' }); return true }
      const plan = planImport(parsed.file, store.live(user.id), modo)
      store.applyPlan(user.id, plan)
      sendJson(res, 200, { conteggi: plan.conteggi, orfani: plan.orfani, scartati: plan.scartati, rev: store.rev })
      return true
    }

    if (path === '/api/catalogo' && method === 'POST') {
      if (store.hasData(user.id)) { sendJson(res, 409, { error: 'account non vuoto' }); return true }
      const createdAt = isoSec(Date.now())
      const put = CATALOG.map((e, i) => ({
        kind: 'exercise' as Kind, uuid: newUuid(),
        data: {
          name: e.name, muscleGroup: e.group as MuscleGroup, notes: e.notes, incrementStep: e.step, sortIndex: i, isArchived: false,
          createdAt, glyph: '' as const, photo: '', photoOffset: 0, isUnilateral: false, restSeconds: 0,
        },
      }))
      store.applyPlan(user.id, { put, del: [], photos: new Map() })
      sendJson(res, 200, { esercizi: put.length, rev: store.rev })
      return true
    }

    if (path === '/api/backup' && method === 'GET') {
      const now = Date.now()
      const text = serializeBackup(exportBackup(store.live(user.id), (sha) => store.getPhoto(user.id, sha), now))
      res.writeHead(200, {
        'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store',
        'content-disposition': `attachment; filename="Peak-${isoSec(now).slice(0, 10)}.json"`, ...SECURITY_HEADERS,
      })
      res.end(text)
      return true
    }

    if (path === '/api/dispositivi' && method === 'GET') { sendJson(res, 200, { dispositivi: store.listDevices(user.id) }); return true }
    const dev = /^\/api\/dispositivi\/(\d+)$/.exec(path)
    if (dev && method === 'DELETE') {
      if (!store.deleteDevice(user.id, Number(dev[1]))) throw new HttpError(404, 'dispositivo')
      sendJson(res, 200, { ok: true })
      return true
    }

    // Web Push: iscrizione del browser e orario di fine del recupero
    if (path === '/api/push' && (method === 'POST' || method === 'DELETE')) {
      const { endpoint } = (await readJson(req, 4096)) as { endpoint?: unknown }
      if (!endpointOk(endpoint)) throw new HttpError(400, 'endpoint')
      if (method === 'POST') push.subscribe(user.id, endpoint)
      else push.unsubscribe(user.id, endpoint)
      sendJson(res, 200, { ok: true })
      return true
    }
    if (path === '/api/recupero' && (method === 'POST' || method === 'DELETE')) {
      const { endpoint, fine } = (await readJson(req, 4096)) as { endpoint?: unknown; fine?: unknown }
      if (!endpointOk(endpoint)) throw new HttpError(400, 'endpoint')
      if (method === 'DELETE') { push.cancel(user.id, endpoint); sendJson(res, 200, { ok: true }); return true }
      if (typeof fine !== 'number' || !push.schedule(user.id, endpoint, fine)) throw new HttpError(400, 'fine')
      sendJson(res, 200, { ok: true })
      return true
    }

    throw new HttpError(404, 'non trovato')
  }
}
