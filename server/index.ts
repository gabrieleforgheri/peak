// Peak: server unico (node:http, node:sqlite, nessuna dipendenza). Serve il client compilato (web/dist),
// l'API per web e iPhone e il login con yG. Sull'egg Pelican ogni riavvio è anche un deploy: git pull e questo file.

import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { existsSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { dirname, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createApi } from './api.ts'
import { createAuth, loadAuthConfig } from './auth.ts'
import { APP_CSP, HttpError, SECURITY_HEADERS, sendJson } from './http.ts'
import { openStore } from './store.ts'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.map': 'application/json',
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
  return out
}

export interface AppOptions { dataDir: string; env?: NodeJS.ProcessEnv; staticDir?: string }

/** Costruisce il server senza metterlo in ascolto (i test lo avviano su una porta a caso). */
export function createApp({ dataDir, env = process.env, staticDir = join(ROOT, 'web/dist') }: AppOptions) {
  const store = openStore(dataDir)
  const auth = createAuth(store, loadAuthConfig(env), dataDir)
  const api = createApi(store, auth, commitOf)
  const files = loadStatic(staticDir)

  async function route(req: IncomingMessage, res: ServerResponse) {
    const url = new URL(req.url ?? '/', 'http://x')
    const path = url.pathname
    if (await auth.handle(req, res, path, url.searchParams)) return
    if (await api(req, res, path, url.searchParams)) return
    if (req.method !== 'GET' && req.method !== 'HEAD') throw new HttpError(405, 'metodo')

    // la pagina dell'app solo con una sessione: senza, al login. Il resto (asset, manifest, sw) è pubblico.
    const isShell = path === '/' || path === '/index.html'
    if (isShell && !auth.userOf(req, 'page')) {
      res.writeHead(302, { location: '/auth/login', 'cache-control': 'no-store', ...SECURITY_HEADERS })
      res.end()
      return
    }
    const file = files.get(isShell ? '/index.html' : path)
    if (!file) {
      if (files.size === 0) throw new HttpError(503, 'client non compilato: npm run build')
      throw new HttpError(404, 'non trovato')
    }
    res.writeHead(200, {
      'content-type': file.type, 'cache-control': isShell ? 'no-store' : file.cache,
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

  return { server, store, auth, nightly }
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
  const { server, nightly } = createApp({ dataDir })
  const port = Number(process.env.PORT ?? process.env.SERVER_PORT ?? 3016)
  server.listen(port, '0.0.0.0', () => {
    // La stringa «Server avviato» è quella che Wings cerca per segnare il server come avviato (egg 1): non cambiarla.
    console.log(`Server avviato sulla porta ${port} (commit ${commitOf()})`)
  })
  const tick = () => setTimeout(() => { try { nightly() } catch (e) { console.error('notturno', e) } tick() }, msUntil(4)).unref()
  tick()
  for (const sig of ['SIGTERM', 'SIGINT'] as const) process.on(sig, () => server.close(() => process.exit(0)))
}
