// Login con yG (authentik, OIDC code flow con PKCE) per il browser e per l'app iPhone.
// Scritto a mano e senza librerie (sull'egg Pelican ogni riavvio è anche un deploy: niente npm all'avvio).
// Il token arriva dall'endpoint di authentik su TLS: OIDC Core 3.1.3.7 permette di fidarsi del canale al posto
// della firma, quindi si controllano iss, aud, exp e nonce. Come in yG/lib/oidc.js, `redirect_uri` si costruisce
// solo da BASE_URL, mai dagli header della richiesta.
//
// CHI ENTRA: il gruppo `svc-peak`, ricontrollato qui sul claim `groups`. L'application su authentik non ha una
// policy sul gruppo, di proposito: un utente normale non deve vedere pagine di authentik tranne il login.

import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Store, User } from './store.ts'
import { cookies, escapeHtml, langOf, PAGE_CSP, readBody, SECURITY_HEADERS } from './http.ts'

export interface AuthConfig {
  baseUrl: string; issuer: string; clientId: string; clientSecret: string; group: string; sessionDays: number
}

export function loadAuthConfig(env: NodeJS.ProcessEnv = process.env): AuthConfig | null {
  const baseUrl = env.BASE_URL?.replace(/\/+$/, '')
  const issuer = env.OIDC_ISSUER?.replace(/\/*$/, '/')
  if (!baseUrl || !issuer || !env.OIDC_CLIENT_ID || !env.OIDC_CLIENT_SECRET) return null
  return {
    baseUrl, issuer, clientId: env.OIDC_CLIENT_ID, clientSecret: env.OIDC_CLIENT_SECRET,
    group: env.OIDC_GROUP || 'svc-peak', sessionDays: Number(env.SESSION_DAYS) || 30,
  }
}

const SESSION = 'peak'
const FLOW = 'peak_flow'
const HOME = 'https://yrb4g.com/account'
const APP_CALLBACK = 'peak://login'
const CODE_MS = 60_000

const T = {
  it: {
    notConfigured: ['Il login non è configurato', 'Mancano le credenziali del login di yrb4g.com.'],
    down: ['Il login non risponde', 'Il servizio di accesso di yrb4g.com non risponde. Riprova fra qualche minuto.'],
    failed: 'Accesso non riuscito', retry: 'Riprova', back: 'Torna a yrb4g.com', cancel: 'Annulla',
    noGroup: ['Peak non è attivo per te', 'Il tuo account yrb4g.com non ha ancora Peak. Chiedi all\'amministratore di attivarlo.'],
    confirm: 'Collegare questo iPhone?', connect: 'Collega',
    confirmText: (n: string) => `«${n}» potrà leggere e scrivere i tuoi allenamenti. Lo puoi scollegare quando vuoi da yrb4g.com/account/peak.`,
    expired: 'Il collegamento è scaduto: riprova dall\'app.',
    out: 'Sei uscito da Peak', outText: 'Il tuo account yrb4g.com resta aperto.', again: 'Rientra',
  },
  en: {
    notConfigured: ['Sign-in is not configured', 'The yrb4g.com sign-in credentials are missing.'],
    down: ['Sign-in is not responding', 'The yrb4g.com sign-in service is not responding. Try again in a few minutes.'],
    failed: 'Sign-in failed', retry: 'Try again', back: 'Back to yrb4g.com', cancel: 'Cancel',
    noGroup: ['Peak is not enabled for you', 'Your yrb4g.com account does not have Peak yet. Ask the administrator to enable it.'],
    confirm: 'Connect this iPhone?', connect: 'Connect',
    confirmText: (n: string) => `“${n}” will be able to read and write your workouts. You can disconnect it any time from yrb4g.com/account/peak.`,
    expired: 'The link expired: try again from the app.',
    out: 'You left Peak', outText: 'Your yrb4g.com account stays signed in.', again: 'Sign in again',
  },
}

interface Discovery { authorization_endpoint: string; token_endpoint: string; issuer: string }
interface Claims {
  iss: string; aud: string | string[]; exp: number; nonce?: string; sub: string
  name?: string; preferred_username?: string; email?: string; groups?: string[]
}
interface Flow { state: string; nonce: string; verifier: string; app?: string; name?: string; exp: number }

const b64 = (b: Buffer) => b.toString('base64url')
const challengeOk = (c: unknown): c is string => typeof c === 'string' && /^[A-Za-z0-9_-]{43}$/.test(c)
const deviceName = (n: unknown) => String(n ?? '').replace(/[\x00-\x1f\x7f]/g, '').trim().slice(0, 24) || 'iPhone'

class OidcError extends Error {}

export function createAuth(store: Store, cfg: AuthConfig | null, dataDir: string) {
  mkdirSync(dataDir, { recursive: true })
  const keyFile = join(dataDir, 'secret.key')
  if (!existsSync(keyFile)) writeFileSync(keyFile, randomBytes(32).toString('hex'), { mode: 0o600 })
  const key = Buffer.from(readFileSync(keyFile, 'utf8').trim(), 'hex')
  const secure = !!cfg?.baseUrl.startsWith('https:')

  const seal = (v: object) => {
    const iv = randomBytes(12)
    const c = createCipheriv('aes-256-gcm', key, iv)
    const body = Buffer.concat([c.update(JSON.stringify(v), 'utf8'), c.final()])
    return [iv, c.getAuthTag(), body].map((x) => x.toString('base64')).join('.')
  }
  const unseal = <T extends { exp: number }>(raw: string | undefined): T | undefined => {
    if (!raw) return undefined
    try {
      const [iv, tag, body] = raw.split('.').map((p) => Buffer.from(p, 'base64'))
      const d = createDecipheriv('aes-256-gcm', key, iv)
      d.setAuthTag(tag)
      const v = JSON.parse(Buffer.concat([d.update(body), d.final()]).toString('utf8')) as T
      return v.exp > Date.now() ? v : undefined
    } catch { return undefined }
  }

  const cookie = (name: string, value: string, path: string, maxAgeS: number) =>
    `${name}=${encodeURIComponent(value)}; Path=${path}; HttpOnly; SameSite=Lax${secure ? '; Secure' : ''}; Max-Age=${maxAgeS}`

  function page(req: IncomingMessage, res: ServerResponse, status: number, title: string, text: string,
    links: [string, string][], form?: { action: string; field: string; value: string; button: string }) {
    res.writeHead(status, {
      'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store', 'content-security-policy': PAGE_CSP, ...SECURITY_HEADERS,
    })
    res.end(`<!doctype html><html lang="${langOf(req)}"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escapeHtml(title)}</title>`
      + '<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0b0d14;color:#fff;font:16px/1.5 ui-rounded,system-ui,sans-serif}'
      + 'main{max-width:420px;padding:24px;text-align:center}h1{font-size:22px}p{color:#aab}'
      + 'a,button{display:inline-block;margin:6px;padding:10px 18px;border:0;border-radius:999px;background:#b8f04d;color:#0b0d14;font:600 16px ui-rounded,system-ui,sans-serif;text-decoration:none;cursor:pointer}'
      + 'a+a{background:#2a2e3b;color:#fff}</style>'
      + `<main><h1>${escapeHtml(title)}</h1><p>${escapeHtml(text)}</p>`
      + (form ? `<form method="post" action="${escapeHtml(form.action)}"><input type="hidden" name="${escapeHtml(form.field)}" value="${escapeHtml(form.value)}"><button>${escapeHtml(form.button)}</button></form>` : '')
      + links.map(([href, label]) => `<a href="${escapeHtml(href)}">${escapeHtml(label)}</a>`).join('') + '</main></html>')
  }

  // ---- OIDC ----
  let discovery: Promise<Discovery> | undefined
  const discover = (): Promise<Discovery> => {
    discovery ??= fetch(`${cfg!.issuer}.well-known/openid-configuration`, { signal: AbortSignal.timeout(5000) })
      .then((r) => { if (!r.ok) throw new Error(`discovery ${r.status}`); return r.json() as Promise<Discovery> })
      .catch((e) => { discovery = undefined; throw e })
    return discovery
  }
  const redirectUri = () => `${cfg!.baseUrl}/auth/callback`

  async function exchange(code: string, verifier: string, nonce: string): Promise<Claims> {
    const d = await discover()
    const r = await fetch(d.token_endpoint, {
      method: 'POST', signal: AbortSignal.timeout(8000),
      headers: {
        'content-type': 'application/x-www-form-urlencoded',
        authorization: `Basic ${Buffer.from(`${encodeURIComponent(cfg!.clientId)}:${encodeURIComponent(cfg!.clientSecret)}`).toString('base64')}`,
      },
      body: new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: redirectUri(), code_verifier: verifier }),
    })
    const body = (await r.json()) as { id_token?: string; error?: string }
    if (!r.ok || !body.id_token) throw new Error(body.error ?? `token ${r.status}`)
    const claims = JSON.parse(Buffer.from(body.id_token.split('.')[1] ?? '', 'base64url').toString('utf8')) as Claims
    const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud]
    if (claims.iss !== d.issuer || !aud.includes(cfg!.clientId)) throw new OidcError('token di un altro emittente')
    if (claims.exp * 1000 < Date.now() || claims.nonce !== nonce) throw new OidcError('token scaduto o riusato')
    return claims
  }

  // ---- codici one-shot per l'app ----
  const codes = new Map<string, { userId: number; challenge: string; name: string; confirmed: boolean; exp: number }>()
  /** state già risposto → codice in attesa. Safari può caricare il callback due volte: il secondo caricamento
   *  spenderebbe di nuovo il code di authentik e fallirebbe coprendo la pagina di conferma. */
  const answered = new Map<string, string>()
  function newCode(userId: number, challenge: string, name: string, confirmed: boolean, now = Date.now()): string {
    for (const [k, v] of codes) if (v.exp < now || v.userId === userId) codes.delete(k) // un codice per utente
    if (codes.size >= 1000) codes.delete(codes.keys().next().value!)
    const code = b64(randomBytes(32))
    codes.set(code, { userId, challenge, name, confirmed, exp: now + CODE_MS })
    return code
  }
  const confirmPage = (req: IncomingMessage, res: ServerResponse, name: string, code: string) => {
    const t = T[langOf(req)]
    page(req, res, 200, t.confirm, t.confirmText(name), [[HOME, t.cancel]], { action: '/auth/app/conferma', field: 'code', value: code, button: t.connect })
  }

  const redirect = (res: ServerResponse, to: string, setCookies: string[] = []) => {
    res.writeHead(302, { location: to, 'cache-control': 'no-store', 'set-cookie': setCookies, ...SECURITY_HEADERS })
    res.end()
  }

  /** Gestisce /auth/*. Restituisce false se il percorso non è suo. */
  async function handle(req: IncomingMessage, res: ServerResponse, path: string, query: URLSearchParams): Promise<boolean> {
    const t = T[langOf(req)]
    const method = req.method ?? 'GET'

    if (path === '/auth/dev' && method === 'GET') {
      const user = store.upsertUser('dev', 'dev@example.com', 'dev', 'Dev')
      const token = store.createSession(user.id, 30)
      redirect(res, '/', [cookie(SESSION, token, '/', Math.round(30 * 86_400))])
      return true
    }

    if (path === '/auth/login' && method === 'GET') {
      if (!cfg) { redirect(res, '/auth/dev'); return true }
      try {
        const d = await discover()
        const flow: Flow = { state: b64(randomBytes(16)), nonce: b64(randomBytes(16)), verifier: b64(randomBytes(32)), exp: Date.now() + 10 * 60_000 }
        const app = query.get('app')
        if (challengeOk(app)) { flow.app = app; flow.name = deviceName(query.get('nome')) }
        const url = new URL(d.authorization_endpoint)
        url.search = new URLSearchParams({
          response_type: 'code', client_id: cfg.clientId, redirect_uri: redirectUri(), scope: 'openid profile email',
          state: flow.state, nonce: flow.nonce, code_challenge: b64(createHash('sha256').update(flow.verifier).digest()), code_challenge_method: 'S256',
        }).toString()
        redirect(res, url.toString(), [cookie(FLOW, seal(flow), '/auth', 600)])
      } catch { page(req, res, 503, t.down[0], t.down[1], [['/auth/login', t.retry]]) }
      return true
    }

    if (path === '/auth/callback' && method === 'GET') {
      const flow = unseal<Flow>(cookies(req)[FLOW])
      const clear = cookie(FLOW, '', '/auth', 0)
      const fail = (text: string, status = 400) => {
        res.setHeader('set-cookie', clear)
        page(req, res, status, t.failed, text, [['/auth/login', t.retry], [HOME, t.back]])
      }
      if (!cfg) { redirect(res, '/auth/login', [clear]); return true }
      // ritorno da un login lasciato a metà in un'altra scheda: si riparte
      if (!flow || query.get('state') !== flow.state) { redirect(res, '/auth/login', [clear]); return true }
      if (query.get('error')) { fail(query.get('error_description') ?? query.get('error') ?? '', 403); return true }
      const again = flow.app ? answered.get(flow.state) : undefined
      const pending = again ? codes.get(again) : undefined
      if (again && pending && pending.challenge === flow.app && !pending.confirmed && pending.exp > Date.now()) {
        res.setHeader('set-cookie', clear); confirmPage(req, res, pending.name, again); return true
      }
      try {
        const claims = await exchange(query.get('code') ?? '', flow.verifier, flow.nonce)
        if (!claims.groups?.includes(cfg.group)) {
          res.setHeader('set-cookie', clear)
          page(req, res, 403, t.noGroup[0], t.noGroup[1], [[HOME, t.back]])
          return true
        }
        const user = store.upsertUser(claims.sub, claims.email ?? '', claims.preferred_username ?? '', claims.name ?? claims.preferred_username ?? '')
        if (flow.app) {
          // Si chiede sempre, mai in silenzio: qualunque app può avviare questo flusso su un telefono dove il login è già aperto.
          const name = flow.name ?? 'iPhone'
          const code = newCode(user.id, flow.app, name, false)
          for (const [k, v] of answered) if (!codes.has(v)) answered.delete(k)
          answered.set(flow.state, code)
          res.setHeader('set-cookie', clear)
          confirmPage(req, res, name, code)
          return true
        }
        const token = store.createSession(user.id, cfg.sessionDays)
        redirect(res, '/', [clear, cookie(SESSION, token, '/', Math.round(cfg.sessionDays * 86_400))])
      } catch (err) {
        // non 502: Cloudflare mostrerebbe la sua pagina al posto della nostra
        fail(err instanceof OidcError ? err.message : t.down[1], err instanceof OidcError ? 400 : 503)
      }
      return true
    }

    // il bottone della pagina di conferma: solo un form inviato da questo sito
    if (path === '/auth/app/conferma' && method === 'POST') {
      const form = new URLSearchParams((await readBody(req, 4096)).toString('utf8'))
      const code = form.get('code') ?? ''
      const entry = codes.get(code)
      codes.delete(code)
      if (req.headers['sec-fetch-site'] !== 'same-origin' || !entry || entry.confirmed || entry.exp < Date.now()) {
        page(req, res, 400, t.failed, t.expired, [[HOME, t.back]])
        return true
      }
      redirect(res, `${APP_CALLBACK}?code=${newCode(entry.userId, entry.challenge, entry.name, true)}`)
      return true
    }

    if (path === '/auth/app/token' && method === 'POST') {
      let body: Record<string, unknown> = {}
      try { body = JSON.parse((await readBody(req, 4096)).toString('utf8')) } catch { /* vuoto → codice errato */ }
      const code = typeof body.code === 'string' ? body.code : ''
      const verifier = typeof body.verifier === 'string' ? body.verifier : ''
      const entry = codes.get(code)
      if (entry) codes.delete(code) // monouso, giusto o sbagliato
      const proof = verifier.length >= 43 && verifier.length <= 128 ? createHash('sha256').update(verifier).digest('base64url') : ''
      const user = entry?.confirmed && entry.exp > Date.now() && proof === entry.challenge ? store.userById(entry.userId) : undefined
      res.setHeader('cache-control', 'no-store')
      if (!user || !entry) { res.writeHead(400, { 'content-type': 'application/json' }); res.end('{"error":"codice"}'); return true }
      // un nuovo login dallo stesso dispositivo sostituisce il suo token
      const token = store.createDevice(user.id, `Peak · ${entry.name}`)
      res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' })
      res.end(JSON.stringify({ token, sub: user.sub, nome: user.name || user.username }))
      return true
    }

    // Solo la sessione di Peak: quella di yrb4g.com resta aperta.
    if (path === '/auth/logout' && method === 'GET') {
      const token = cookies(req)[SESSION]
      if (token) store.deleteSession(token)
      res.setHeader('set-cookie', cookie(SESSION, '', '/', 0))
      page(req, res, 200, t.out, t.outText, [['/auth/login', t.again], [HOME, t.back]])
      return true
    }
    return false
  }

  /**
   * Chi fa la richiesta. API: token del dispositivo (Bearer) oppure cookie con `Sec-Fetch-Site: same-origin`
   * (il cookie è Lax, quindi arriverebbe anche da un link di un altro sito). Pagina: solo il cookie.
   */
  function userOf(req: IncomingMessage, mode: 'api' | 'page'): { user: User; via: 'device' | 'cookie'; deviceId?: number } | null {
    const bearer = /^Bearer (.+)$/.exec(String(req.headers.authorization ?? ''))
    if (bearer && mode === 'api') {
      const r = store.deviceUser(bearer[1])
      return r ? { user: r.user, via: 'device', deviceId: r.deviceId } : null
    }
    if (mode === 'api' && req.headers['sec-fetch-site'] !== 'same-origin') return null
    const token = cookies(req)[SESSION]
    const user = token ? store.sessionUser(token) : undefined
    return user ? { user, via: 'cookie' } : null
  }

  return { handle, userOf, configured: !!cfg, cookie }
}

export type Auth = ReturnType<typeof createAuth>
