# web (Peak)

## Overview
Client web di Peak: PWA Svelte 5 + Vite che funziona offline e si sincronizza con l'app iPhone tramite il server Node in `server/`. Lo stato vive in IndexedDB; il server conserva i documenti e li smista tra i dispositivi (`/api/sync`). Accesso con l'account yG (`/auth/login`); la pagina `/` è servita dal server solo con sessione valida.

## Interface
- `index.html`: pagina dell'app; `sw.js`: modello del service worker (guscio in cache, foto `/api/foto/` in cache perpetua, Web Push per il recupero). Vite sostituisce `__SHELL__` e `__VERSION__` a ogni build (plugin `peak-sw` in `vite.config.ts`).
- `vite.config.ts`: alias, proxy di sviluppo, target `safari16`, uscita in `dist/`, costante `__APP_VERSION__` letta da `package.json`.
- `svelte.config.js`: silenzia `state_referenced_locally`. `tsconfig.json`: strict, `paths` per `@common/*`.
- `package.json` (`peak-web`): script `dev`, `build`, `check`. La versione va tenuta uguale a `MARKETING_VERSION` di `project.yml`.
- Sottodirectory: [src](./src/README.md), [public](./public/README.md), [scripts](./scripts/README.md). `dist/` è output di build, `node_modules/` dipendenze.

### Confine con `common/`
Il codice condiviso con l'app iPhone sta in `common/` ([contratto](../common/README.md)) alla radice del repo (`docs.ts`, `domain.ts`, `records.ts`, `stats.ts`, `medals.ts`, `glyph.ts`, `muscle-info.ts`, `catalog.json`, ...). Il web lo importa con l'alias `@common/*`:
- `vite.config.ts`: `resolve.alias['@common'] = ../common`, e `server.fs.allow: ['..']` per servirlo in sviluppo.
- `tsconfig.json`: `paths: { "@common/*": ["../common/*"] }` per svelte-check.
- Gli import hanno l'estensione (`@common/docs.ts`), come richiede `allowImportingTsExtensions`.
- Il web non modifica `common/`. Anche `server/` ([contratto](../server/README.md)) ne dipende, quindi un cambio lì cambia entrambi i lati del protocollo di sync. Test di `common/` (`*.test.ts`) alla radice.

## Invariants
- Il formato dei documenti sincronizzati è definito in `common/docs.ts`; il client non lo ridefinisce.
- `/api` e `/auth` in sviluppo vanno al server locale (`PEAK`, default `http://127.0.0.1:3016`), così il cookie vale come in produzione.
- `sw.js` esiste solo come modello: la versione in `dist/` ha cache `peak-guscio-<hash>` che cambia a ogni build.
- Testi italiani come chiavi, traduzioni in `src/lib/en.json`; niente emoji nella UI.

## Side Effects
- `npm run build` scrive `dist/` (svuotato a ogni build) incluso `sw.js` generato.
- A runtime: fetch verso `/api/*`, IndexedDB, Cache Storage, notifiche push.

## Verification
- Run: `npm --prefix web run check` (svelte-check)
- Run: `cd web && node scripts/check-i18n.mjs`
- Run: `npm run build` dalla radice del repo
