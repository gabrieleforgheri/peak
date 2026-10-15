// Peak: server unico (node:http, node:sqlite, nessuna dipendenza). Serve il client compilato (web/dist),
// l'API per web e iPhone e il login con yG. Sull'egg Pelican ogni riavvio è anche un deploy: git pull e questo file.

import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { createHash } from 'node:crypto'
import { existsSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { dirname, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApi } from './api.ts'
import { createAuth, loadAuthConfig } from './auth.ts'
import { createPortal } from './portale.ts'
import { createPush, type Send } from './push.ts'
import { APP_CSP, cookies, HttpError, SECURITY_HEADERS, sendJson } from './http.ts'
import { openStore } from './store.ts'
import { CATALOG_BODY, CATALOG_ETAG } from './catalog.ts'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.mp4': 'video/mp4', '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.map': 'application/json',
}

function commitOf(): string {
  try {
    const head = readFileSync(join(ROOT, '.git/HEAD'), 'utf8').trim()
    if (!head.startsWith('ref: ')) return head.slice(0, 7)
    const ref = head.slice(5)
    const file = join(ROOT, '.git', ref)
    if (existsSync(file)) return readFileSync(file, 'utf8').trim().slice(0, 7)
    const line = readFileSync(join(ROOT, '.git/packed-refs'), 'utf8').split('\n').find((l) => l.endsWith(` ${ref}`))
    return line ? line.slice(0, 7) : 'sconosciuto'
  } catch { return 'sconosciuto' }
}

/** I file di web/dist in memoria, letti una volta: il percorso della richiesta non tocca mai il disco. */
function loadStatic(dir: string): Map<string, { body: Buffer; type: string; cache: string }> {
  const out = new Map<string, { body: Buffer; type: string; cache: string }>()
  const walk = (d: string, prefix: string) => {
    for (const name of readdirSync(d)) {
      const full = join(d, name)
      if (statSync(full).isDirectory()) { walk(full, `${prefix}${name}/`); continue }
      const url = `/${prefix}${name}`
      out.set(url, {
        body: readFileSync(full), type: TYPES[extname(name)] ?? 'application/octet-stream',
        cache: url.startsWith('/assets/') ? 'public, max-age=31536000, immutable' : 'no-cache',
      })
    }
  }
  if (existsSync(dir)) walk(dir, '')
  versionLinks(out)
  return out
}

/**
 * Cloudflare tiene in cache js, css e immagini senza hash (per ore, ignorando il nostro `no-cache`): dopo un
 * aggiornamento la pagina nuova trovava lo script vecchio. Nelle pagine ogni file locale senza hash (benvenuto.js,
 * .css, .mp4, icone) si chiama con `?v=<hash del contenuto>`: cambia il file, cambia l'indirizzo, e arriva subito.
 */
function versionLinks(files: Map<string, { body: Buffer; type: string; cache: string }>) {
  for (const [url, page] of files) {
    if (!url.endsWith('.html')) continue
    const html = page.body.toString('utf8').replace(/\b(href|src)="(\/[^"?#]+)"/g, (whole, attr: string, target: string) => {
      const file = files.get(target)
      if (!file || target.startsWith('/assets/') || target.endsWith('.html')) return whole
      return `${attr}="${target}?v=${createHash('sha256').update(file.body).digest('hex').slice(0, 10)}"`
    })
    page.body = Buffer.from(html)
  }
}

export interface AppOptions { dataDir: string; env?: NodeJS.ProcessEnv; staticDir?: string; /** Per i test: al posto della vera chiamata al servizio push. */ send?: Send }

/** Costruisce il server senza metterlo in ascolto (i test lo avviano su una porta a caso). */
export function createApp({ dataDir, env = process.env, staticDir = join(ROOT, 'web/dist'), send }: AppOptions) {
  const store = openStore(dataDir)
  store.migrateCatalog() // collega al catalogo gli esercizi che non hanno ancora l'id (una volta per esercizio)
  const auth = createAuth(store, loadAuthConfig(env), dataDir)
  const push = createPush(store, dataDir, (env.BASE_URL ?? 'https://peak.yrb4g.com').replace(/\/+$/, ''), send)
  const api = createApi(store, auth, push, commitOf)
  const files = loadStatic(staticDir)
  const portal = createPortal(store, env.YG_PORTAL_TOKEN)

  async function route(req: IncomingMessage, res: ServerResponse) {
    const url = new URL(req.url ?? '/', 'http://x')
    const path = url.pathname
    if (await auth.handle(req, res, path, url.searchParams)) return
    if (await api(req, res, path, url.searchParams)) return
    if (req.method !== 'GET' && req.method !== 'HEAD') throw new HttpError(405, 'metodo')

    // la pagina dell'app solo con una sessione: senza, al benvenuto (con «Accedi con yG»). Il resto (asset, manifest, sw) è pubblico.
    const isShell = path === '/' || path === '/index.html'
    const me = auth.userOf(req, 'page')
    const go = (to: string, setCookie?: string) => {
      res.writeHead(302, { location: to, 'cache-control': 'no-store', ...(setCookie ? { 'set-cookie': setCookie } : {}), ...SECURITY_HEADERS })
      res.end()
    }
    // il link d'invito: con la sessione va dritto all'app, senza si ricorda il codice (un'ora) e lo riprende dopo il login
    const invite = /^\/invito\/([A-Za-z0-9_-]{20,64})$/.exec(path)
    if (invite) return me ? go(`/#/invito/${invite[1]}`) : go('/benvenuto.html', auth.cookie('peak_invito', invite[1], '/', 3600))
    const pending = cookies(req).peak_invito
    if (isShell && me && pending && /^[A-Za-z0-9_-]{20,64}$/.test(pending)) return go(`/#/invito/${pending}`, auth.cookie('peak_invito', '', '/', 0))
    if (isShell && !me) {
      res.writeHead(302, { location: '/benvenuto.html', 'cache-control': 'no-store', ...SECURITY_HEADERS })
      res.end()
      return
    }
    // il catalogo per i client (l'iPhone se lo tiene aggiornato): pubblico, con ETag
    if (path === '/catalogo.json') {
      const headers = { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-cache', etag: CATALOG_ETAG, ...SECURITY_HEADERS }
      if (req.headers['if-none-match'] === CATALOG_ETAG) { res.writeHead(304, headers); return void res.end() }
      res.writeHead(200, headers)
      return void res.end(req.method === 'HEAD' ? undefined : CATALOG_BODY)
    }
    const file = files.get(isShell ? '/index.html' : path)
    if (!file) {
      if (files.size === 0) throw new HttpError(503, 'client non compilato: npm run build')
      throw new HttpError(404, 'non trovato')
    }
    // Safari riproduce un video solo se il server sa rispondere a un intervallo di byte (Range)
    const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? '')
    if (range && file.type === 'video/mp4') {
      const size = file.body.length
      const start = range[1] ? Number(range[1]) : size - Number(range[2])
      const end = range[1] && range[2] ? Math.min(Number(range[2]), size - 1) : size - 1
      if (!(start >= 0 && start <= end)) {
        res.writeHead(416, { 'content-range': `bytes */${size}`, ...SECURITY_HEADERS })
        return void res.end()
      }
      res.writeHead(206, {
        'content-type': file.type, 'cache-control': file.cache, 'content-range': `bytes ${start}-${end}/${size}`, 'accept-ranges': 'bytes',
        'content-length': end - start + 1, ...SECURITY_HEADERS,
      })
      return void res.end(req.method === 'HEAD' ? undefined : file.body.subarray(start, end + 1))
    }
    res.writeHead(200, {
      // sw.js mai in cache, nemmeno su Cloudflare (che ignora `no-cache` sui .js): un aggiornamento deve arrivare subito
      'content-type': file.type, 'cache-control': isShell || path === '/sw.js' ? 'no-store' : file.cache,
      ...(file.type === 'video/mp4' ? { 'accept-ranges': 'bytes' } : {}),
      'content-security-policy': APP_CSP, ...SECURITY_HEADERS,
      ...(path === '/sw.js' ? { 'service-worker-allowed': '/' } : {}),
    })
    res.end(req.method === 'HEAD' ? undefined : file.body)
  }

  const server = createServer((req, res) => {
    route(req, res).catch((err) => {
      if (res.headersSent) return void res.end()
      if (err instanceof HttpError) return sendJson(res, err.status, { error: err.message })
      console.error('errore', req.method, req.url, err)
      sendJson(res, 500, { error: 'errore interno' })
    })
  })

  /** Ogni notte: copia coerente del database per il backup di Pelican, e foto orfane. */
  function nightly() {
    const copy = join(dataDir, 'peak-backup.db')
    rmSync(copy, { force: true })
    store.backupTo(copy)
    return store.sweepPhotos()
  }

  return { server, portal, store, auth, push, nightly }
}

function msUntil(hour: number, now = new Date()): number {
  const next = new Date(now)
  next.setHours(hour, 0, 0, 0)
  if (next <= now) next.setDate(next.getDate() + 1)
  return next.getTime() - now.getTime()
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (existsSync(join(ROOT, '.env'))) process.loadEnvFile(join(ROOT, '.env'))
  process.env.TZ ??= 'Europe/Rome'
  const dataDir = process.env.DATA_DIR ?? join(ROOT, 'data')
  const { server, portal, push, nightly } = createApp({ dataDir })
  push.boot()
  const port = Number(process.env.PORT ?? process.env.SERVER_PORT ?? 3016)
  server.listen(port, '0.0.0.0', () => {
    // La stringa «Server avviato» è quella che Wings cerca per segnare il server come avviato (egg 1): non cambiarla.
    console.log(`Server avviato sulla porta ${port} (commit ${commitOf()})`)
  })
  const tick = () => setTimeout(() => { try { nightly() } catch (e) { console.error('notturno', e) } tick() }, msUntil(4)).unref()
  tick()
  // il portale per yG: una porta a parte, che NPM non pubblica (solo se YG_PORTAL_TOKEN è impostato)
  const portalPort = Number(process.env.PORTAL_PORT ?? 3017)
  portal?.listen(portalPort, '0.0.0.0', () => console.log(`Portale per yG sulla porta ${portalPort}`))
  for (const sig of ['SIGTERM', 'SIGINT'] as const) process.on(sig, () => { portal?.close(); server.close(() => process.exit(0)) })
}
