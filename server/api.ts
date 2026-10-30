// API per web e iPhone: sincronizzazione, foto, importazione, backup, dispositivi.
// Tutto vale per l'utente autenticato; ogni documento in ingresso passa da parseDoc (confine di fiducia).

import { createHash } from 'node:crypto'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { isoSec } from '../common/docs.ts'
import { exportBackup, parseBackup, planImport, serializeBackup } from './backup.ts'
import type { Auth } from './auth.ts'
import { endpointOk, type Push } from './push.ts'
import { HttpError, readBody, readJson, sendJson, SECURITY_HEADERS } from './http.ts'
import type { Person, Store, WireDoc } from './store.ts'
import { activityOf, bestByCatalog, sharedView } from './social.ts'

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

    // gli errori JavaScript dei client finiscono nel log del server: sui telefoni degli altri non c'è una console da leggere
    if (path === '/api/errore' && method === 'POST') {
      const body = (await readJson(req, 8192)) as { msg?: unknown; stack?: unknown; url?: unknown; ua?: unknown }
      console.log(`[errore client] utente ${user.id} ${String(body?.url ?? '').slice(0, 200)} | ${String(body?.msg ?? '').slice(0, 500)} | ${String(body?.ua ?? '').slice(0, 200)}\n${String(body?.stack ?? '').slice(0, 3000)}`)
      res.writeHead(204).end()
      return true
    }

    if (path === '/api/sync' && method === 'GET') {
      const dopo = Number(query.get('dopo') ?? 0)
      if (!Number.isInteger(dopo) || dopo < 0) throw new HttpError(400, 'dopo')
      // cursore oltre la revisione del server: database ripristinato da un backup, il client deve rileggere tutto
      sendJson(res, 200, store.pull(user.id, dopo > store.rev ? 0 : dopo))
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
      // «sostituisci» cancella il resto: con documenti scartati lascerebbe riferimenti nel vuoto e dati persi in silenzio
      if (modo === 'sostituisci' && plan.scartati > 0) { sendJson(res, 422, { error: 'scartati', scartati: plan.scartati }); return true }
      const nuove = [...plan.photos.keys()].filter((sha) => !store.getPhoto(user.id, sha)).length
      if (nuove > 0 && store.photoCount(user.id) + nuove > MAX_PHOTOS_PER_USER) throw new HttpError(409, 'troppe foto')
      store.applyPlan(user.id, plan)
      sendJson(res, 200, { conteggi: plan.conteggi, orfani: plan.orfani, scartati: plan.scartati, rev: store.rev })
      return true
    }

    // ---- amici: ogni percorso controlla l'amicizia, i nascosti li toglie social.ts ----
    if (path === '/api/amici' && method === 'GET') { sendJson(res, 200, store.friends(user.id)); return true }
    if (path === '/api/amici/cerca' && method === 'GET') {
      const utenti = store.searchUsers(user.id, query.get('q') ?? '').map((p) => ({ ...p, stato: store.friendState(user.id, p.id) }))
      sendJson(res, 200, { utenti })
      return true
    }
    if (path === '/api/amici' && method === 'POST') {
      const { id } = (await readJson(req, 1024)) as { id?: unknown }
      if (!Number.isInteger(id) || id === user.id || !store.person(id as number)) throw new HttpError(404, 'utente')
      sendJson(res, 200, { stato: store.requestFriend(user.id, id as number) })
      return true
    }
    const friendRoute = /^\/api\/amici\/(\d+)(\/accetta)?$/.exec(path)
    if (friendRoute) {
      const other = Number(friendRoute[1])
      if (friendRoute[2] && method === 'POST') {
        if (!store.acceptFriend(user.id, other)) throw new HttpError(404, 'richiesta')
        sendJson(res, 200, { ok: true })
        return true
      }
      if (!friendRoute[2] && method === 'DELETE') { store.removeFriend(user.id, other); sendJson(res, 200, { ok: true }); return true }
      if (!friendRoute[2] && method === 'GET') {
        if (!store.areFriends(user.id, other)) throw new HttpError(403, 'non siete amici')
        const p = store.person(other)!
        sendJson(res, 200, { id: p.id, username: p.username, name: p.name, ...sharedView(store.live(other)) })
        return true
      }
    }
    if (path === '/api/inviti' && method === 'POST') {
      const { code, expiresAt } = store.createInvite(user.id)
      sendJson(res, 200, { codice: code, scade: expiresAt })
      return true
    }
    const invite = /^\/api\/inviti\/([A-Za-z0-9_-]{20,64})$/.exec(path)
    if (invite && method === 'POST') {
      const amico = store.useInvite(invite[1], user.id)
      if (!amico) throw new HttpError(404, 'invito')
      sendJson(res, 200, { amico: { id: amico.id, username: amico.username, name: amico.name } })
      return true
    }
    const board = /^\/api\/classifica\/([a-z0-9-]{1,80})$/.exec(path)
    if (board && method === 'GET') {
      const people: Person[] = [{ id: user.id, username: user.username, name: user.name }, ...store.friends(user.id).amici]
      const righe = people.flatMap((p) => {
        const b = bestByCatalog(store.live(p.id)).get(board[1])
        return b ? [{ id: p.id, username: p.username, tu: p.id === user.id, name: b.name, oneRm: b.oneRm, weight: b.weight, reps: b.reps, date: b.date }] : []
      }).sort((a, b) => b.oneRm - a.oneRm)
      sendJson(res, 200, { catalog: board[1], righe })
      return true
    }
    // la lega: tu e gli amici, con i numeri dell'allenamento (il client ordina per quello che si sceglie)
    if (path === '/api/lega' && method === 'GET') {
      const people: Person[] = [{ id: user.id, username: user.username, name: user.name }, ...store.friends(user.id).amici]
      sendJson(res, 200, { righe: people.map((p) => ({ id: p.id, username: p.username, tu: p.id === user.id, ...activityOf(store.live(p.id)) })) })
      return true
    }
    if (path === '/api/novita' && method === 'GET') {
      const since = Date.now() - 14 * 86_400_000
      const mine = bestByCatalog(store.live(user.id))
      const eventi = store.friends(user.id).amici.flatMap((p) =>
        [...bestByCatalog(store.live(p.id)).values()].filter((b) => b.date >= since).map((b) => ({
          id: p.id, username: p.username, catalog: b.catalog, name: b.name, oneRm: b.oneRm, weight: b.weight, reps: b.reps, date: b.date,
          sorpasso: (mine.get(b.catalog)?.oneRm ?? Infinity) < b.oneRm,
        })))
        .sort((a, b) => b.date - a.date).slice(0, 30)
      sendJson(res, 200, { eventi })
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
