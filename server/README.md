# server

## Overview
Backend unico di Peak: `node:http` + `node:sqlite`, nessuna dipendenza npm. Serve il client compilato (`web/dist`), l'API per web e iPhone (sync, foto, backup, amici, push) e il login con yG (authentik, OIDC). Su Pelican ogni riavvio e' anche un deploy (git pull). Avvio: `npm start` -> `server/index.ts`. La logica di dominio sta in [`../common`](../common/README.md).

## Interface
Entry: `createApp({ dataDir, env, staticDir, send })` in `index.ts` ritorna `{ server, portal, store, auth, push, nightly }` senza metterlo in ascolto (i test lo usano). Porta `PORT`/`SERVER_PORT` (3016); il portale su `PORTAL_PORT` (3017).

Rotte (ordine in `route()`: `/auth/*`, poi `/api/*`, poi statici):
- Auth (`auth.ts`): `GET /auth/login` (avvia OIDC code flow con PKCE; `?app=<challenge>` per l'iPhone), `GET /auth/callback`, `POST /auth/app/conferma`, `POST /auth/app/token` (scambia codice+verifier con il token del dispositivo), `GET /auth/logout`, `GET /auth/dev` (utente `dev`, solo se OIDC non e' configurato).
- API (`api.ts`, tutte autenticate tranne `GET /api/health`): `GET /api/io`, `POST /api/errore`, `GET|POST /api/sync` (pull `?dopo=<rev>` / push fino a 1000 documenti), `GET|PUT /api/foto/<sha256>`, `POST /api/importa?modo=unisci|sostituisci[&primo=1]`, `GET /api/backup`, `GET /api/dispositivi`, `DELETE /api/dispositivi/<id>`, `POST|DELETE /api/push`, `POST|DELETE /api/recupero`.
- Social (`api.ts` + `social.ts`): `GET|POST /api/amici`, `GET /api/amici/cerca`, `POST /api/amici/<id>/accetta`, `GET|DELETE /api/amici/<id>`, `POST /api/inviti`, `POST /api/inviti/<codice>`, `GET /api/classifica/<catalog>`, `GET /api/lega`, `GET /api/novita`.
- Statici (`index.ts`): `/catalogo.json` (pubblico, con ETag da `catalog.ts`), `/invito/<codice>`, pagina dell'app solo con sessione (altrimenti 302 a `/benvenuto.html`), asset con `?v=<hash>` aggiunto alle pagine, Range per i `.mp4`.
- Portale per yG (`portale.ts`, server separato, Bearer `YG_PORTAL_TOKEN` >= 32 caratteri, confronto `timingSafeEqual`): `POST /portale/sincronizza`, `GET /portale/riepilogo`, `DELETE /portale/dispositivi/<id>`, `GET /portale/backup`. Senza token valido il portale non parte (`createPortal` ritorna `null`).
- Moduli: `store.ts` (`openStore`, schema SQLite, tutta la persistenza), `backup.ts` (`exportBackup`, `serializeBackup`, `parseBackup`, `planImport`, formato JSON v1 dell'app), `push.ts` (`createPush`, `endpointOk`), `social.ts` (`bestByCatalog`, `sharedView`, `activityOf`), `summary.ts` (`summarize` per yG), `http.ts` (`HttpError`, `readBody`, `readJson`, `sendJson`, `SECURITY_HEADERS`, `APP_CSP`), `catalog.ts` (`CATALOG`, `CATALOG_BODY`, `CATALOG_ETAG`).

## Invariants
- Auth API: Bearer del dispositivo (token in DB solo come sha256) oppure cookie `peak` con `Sec-Fetch-Site: same-origin`; per le pagine solo il cookie. Il gruppo `svc-peak` (o `OIDC_GROUP`) e' ricontrollato sul claim `groups` nel callback. `redirect_uri` si costruisce solo da `BASE_URL`, mai dagli header.
- Senza `OIDC_*`/`BASE_URL` il login ricade su `/auth/dev`: in produzione la configurazione deve esserci.
- Sync: ultimo scrittore vince su `m` (a parita' vince chi arriva dopo); i perdenti tornano in `rifiutati`. `rev` e' un contatore globale in memoria, sicuro perche' un solo processo con `DatabaseSync`. Ogni documento passa da `parseDoc` (`common/docs.ts`); `m` oltre 2 giorni nel futuro e' rifiutato. Le cancellazioni sono tombstone (`deleted = 1`).
- Foto: solo JPEG, indirizzate dal loro sha256 (verificato), max 1 MB e 500 per utente.
- Gli amici vedono solo `sharedView` (esercizi visibili, schede non nascoste, niente pesi di allenamento); ogni rotta amici controlla `areFriends`.
- Push: solo endpoint https dei servizi in lista (`ALLOWED` in `push.ts`), mai indirizzi scelti dall'utente; l'avviso si programma al massimo 1 ora avanti.
- `summarize` e l'export usano le stesse regole dell'app (`common/records.ts`, `common/stats.ts`).
- La riga stampata `Server avviato` e' quella che Wings cerca: non cambiarla.
- Il portale non crea mai utenti (404) e non scollega nessuno con un elenco email vuoto.

## Side Effects
- Disco, in `DATA_DIR` (default `data/`; non leggerne il contenuto): `peak.db` (SQLite WAL: users, sessions, devices, docs, photos, push_subs, friends, invites), `secret.key` (chiave AES-GCM per il cookie del flusso OIDC, creata a 0600), `vapid.json` (chiave EC dei Web Push, 0600), `peak-backup.db`.
- Backup: ogni notte alle 04:00 (`nightly`) `VACUUM INTO peak-backup.db` (copia coerente per il backup di Pelican) e `sweepPhotos` elimina le foto non richiamate da nessun esercizio vivo e piu' vecchie di 24 h. `GET /api/backup` e `/portale/backup` esportano il JSON v1 dell'utente con le foto; `/api/importa` scrive nel DB tramite `planImport` + `applyPlan`.
- Rete in uscita: authentik (discovery e token endpoint, con timeout) e POST ai servizi push (`fetch`, timeout 10 s) con JWT VAPID ES256; i timer di fine recupero sono `setTimeout` in memoria, ripianificati da `push.boot()` all'avvio.
- Migrazione all'apertura: `store.migrateCatalog()` collega al catalogo gli esercizi senza `catalog` e riscrive i loro documenti (`user_version` 2).
- Legge `.env` (`process.loadEnvFile`) e `.git/HEAD` (commit mostrato in `/api/health`); imposta `TZ=Europe/Rome` se assente. Scrive su console gli errori dei client (`/api/errore`).
- Mappa in memoria di codici app monouso (60 s, max 1000), `answered` per il doppio callback di Safari.

## Verification
- Run: `npm test` (`node --test common/ server/`; 107 test passano)
- Run: `npm run typecheck` (4 errori `TS2739` preesistenti su `ExerciseDoc` incompleto in `server/backup.ts` e `*.test.ts`)
