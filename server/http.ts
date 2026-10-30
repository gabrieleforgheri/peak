// Piccoli attrezzi HTTP: corpo con limite, JSON, cookie, escape, intestazioni di sicurezza.

import type { IncomingMessage, ServerResponse } from 'node:http'

export class HttpError extends Error {
  status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}

/** Legge il corpo fino a `limit` byte; oltre, 413 senza tenerlo in memoria. */
export function readBody(req: IncomingMessage, limit: number): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const declared = Number(req.headers['content-length'] ?? 0)
    if (declared > limit) { req.resume(); return reject(new HttpError(413, 'troppo grande')) }
    const chunks: Buffer[] = []
    let size = 0
    req.on('data', (c: Buffer) => {
      size += c.length
      if (size > limit) { reject(new HttpError(413, 'troppo grande')); req.destroy(); return }
      chunks.push(c)
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

export async function readJson(req: IncomingMessage, limit: number): Promise<unknown> {
  let body: unknown
  try { body = JSON.parse((await readBody(req, limit)).toString('utf8')) } catch (e) {
    if (e instanceof HttpError) throw e
    throw new HttpError(400, 'JSON non valido')
  }
  // `null` farebbe lanciare le destrutturazioni dei chiamanti: 500 al posto di 400
  if (body === null) throw new HttpError(400, 'JSON non valido')
  return body
}

export function sendJson(res: ServerResponse, status: number, body: unknown, extra: Record<string, string> = {}) {
  const text = JSON.stringify(body)
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...extra })
  res.end(text)
}

export function cookies(req: IncomingMessage): Record<string, string> {
  const out: Record<string, string> = {}
  for (const part of (req.headers.cookie ?? '').split(';')) {
    const i = part.indexOf('=')
    if (i > 0) { try { out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim()) } catch { /* cookie rotto: ignorato */ } }
  }
  return out
}

export const escapeHtml = (s: string): string =>
  s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)

/** Italiano salvo che la prima lingua del browser sia un'altra. */
export function langOf(req: IncomingMessage): 'it' | 'en' {
  const first = String(req.headers['accept-language'] ?? '').split(',')[0]?.trim().toLowerCase() ?? ''
  return first === '' || first.startsWith('it') ? 'it' : 'en'
}

export const SECURITY_HEADERS: Record<string, string> = {
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'same-origin',
  'cross-origin-opener-policy': 'same-origin',
  'strict-transport-security': 'max-age=31536000',
}

/** CSP dell'app (statica): niente script esterni, immagini da noi, blob e data. */
export const APP_CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; connect-src 'self'; "
  + "manifest-src 'self'; worker-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
/** CSP delle pagine del server: nessuno script; senza form-action perché la conferma rimanda a peak://. */
export const PAGE_CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'"
