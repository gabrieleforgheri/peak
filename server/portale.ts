// L'API che yG usa lato server (yrb4g.com/account/peak): riepilogo, dispositivi, backup, accessi.
// Sta su una porta sua, che NPM non pubblica: la raggiunge solo il sito, con un token (YG_PORTAL_TOKEN, 32+ caratteri).
// Modellata su ruscello/src/portale.ts. Non crea mai utenti: se Peak non conosce la persona, risponde 404.

import { timingSafeEqual } from 'node:crypto'
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { exportBackup, serializeBackup } from './backup.ts'
import { HttpError, readJson, sendJson, SECURITY_HEADERS } from './http.ts'
import { summarize } from './summary.ts'
import type { Store } from './store.ts'
import { isoSec } from '../common/docs.ts'

export const tokenOk = (given: string, expected: string): boolean => {
  const a = Buffer.from(given), b = Buffer.from(expected)
  return a.length === b.length && timingSafeEqual(a, b)
}

/** Mette in piedi il server del portale, o niente se il token manca o è corto (configurazione sbagliata, non un accesso). */
export function createPortal(store: Store, token: string | undefined) {
  if (!token || token.length < 32) return null

  async function route(req: IncomingMessage, res: ServerResponse) {
    const url = new URL(req.url ?? '/', 'http://x')
    const bearer = /^Bearer (.+)$/.exec(String(req.headers.authorization ?? ''))
    if (!bearer || !tokenOk(bearer[1], token!)) throw new HttpError(401, 'token')
    const path = url.pathname
    const method = req.method ?? 'GET'

    if (path === '/portale/sincronizza' && method === 'POST') {
      const body = (await readJson(req, 1_000_000)) as { emails?: unknown }
      const emails = Array.isArray(body?.emails) ? body.emails.filter((e): e is string => typeof e === 'string') : []
      // un elenco vuoto è quasi certamente authentik che non risponde: non si scollega nessuno
      if (emails.length === 0) throw new HttpError(400, 'elenco vuoto')
      sendJson(res, 200, store.revokeAccessExcept(emails))
      return
    }

    const user = store.findUser(url.searchParams.get('sub') ?? '', url.searchParams.get('email') ?? '')
    if (!user) throw new HttpError(404, 'utente sconosciuto')

    if (path === '/portale/riepilogo' && method === 'GET') {
      sendJson(res, 200, { ...summarize(store.live(user.id)), dispositivi: store.listDevices(user.id), nome: user.name || user.username })
      return
    }
    const dev = /^\/portale\/dispositivi\/(\d+)$/.exec(path)
    if (dev && method === 'DELETE') {
      if (!store.deleteDevice(user.id, Number(dev[1]))) throw new HttpError(404, 'dispositivo')
      sendJson(res, 200, { ok: true })
      return
    }
    if (path === '/portale/backup' && method === 'GET') {
      const now = Date.now()
      const text = serializeBackup(exportBackup(store.live(user.id), (sha) => store.getPhoto(user.id, sha), now))
      store.setSetting(user.id, 'lastExportDate', isoSec(now), now) // qui il file lo scarica davvero: vale come backup
      res.writeHead(200, {
        'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store',
        'content-disposition': `attachment; filename="Peak-${isoSec(now).slice(0, 10)}.json"`, ...SECURITY_HEADERS,
      })
      res.end(text)
      return
    }
    throw new HttpError(404, 'non trovato')
  }

  return createServer((req, res) => {
    route(req, res).catch((err) => {
      if (res.headersSent) return void res.end()
      if (err instanceof HttpError) return sendJson(res, err.status, { error: err.message })
      console.error('portale', req.method, req.url, err)
      sendJson(res, 500, { error: 'errore interno' })
    })
  })
}
