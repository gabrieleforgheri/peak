# lib

## Overview
Logica non visuale di Peak: stato locale offline-first, sincronizzazione col server, azioni di scrittura, formattazione, i18n, effetti. Le regole di dominio (record, statistiche, medaglie, formato dei documenti) sono in `common/` e arrivano con l'alias `@common/*`; qui si compongono con lo stato dell'app.

## Interface
- `store.svelte.ts`: stato reattivo `S` (ready, synced, online, auth, user, dirty, error...), `rows` (mappa chiave `kind:uuid` -> riga), `put`, `remove`, `batch`, `applyRemote`, `load`, `wipeLocal`, `onWrite`, `getModel()` (modello derivato: esercizi, allenamenti, schede, peso corporeo, impostazioni).
- `types.ts`: tipi del modello (`Ex`, `Wk`, `Tpl`, `Prg`, `Bw`, `Settings`, `Model`, `SetItem`) costruiti sui tipi di `@common/docs.ts`.
- `sync.ts`: `boot()`, `syncNow()`, `resync()`, `call` (fetch con gestione sessione). Giro: foto in coda, push dei documenti sporchi (`POST /api/sync`), pull (`GET /api/sync?dopo=`), download foto.
- `actions.ts`: tutte le scritture (`startWorkout`, `addSet`, `saveTemplate`, `saveProgram`, `saveExercise`, `addRecord`, `setSetting`, `changeUnit`, `eraseAll`, ...).
- `idb.ts`: IndexedDB (`docs`, `meta`, `photos`); `photo.ts`: `preparePhoto`, `photoUrl`.
- `derive.ts`: suggerimenti, riscaldamento, programma attivo, giorno successivo. `catalog.ts`: `loadCatalog()` (import dinamico di `common/catalog.json`), `entryName`. `medalinfo.ts`, `fmt.ts` (pesi/date nell'unità e nella lingua scelte).
- `i18n.ts`: `t`, `tn`, `lang`, `locale`, `applyLang`; traduzioni in `en.json`.
- `router.svelte.ts`: `route`, `go`, `back` (percorsi a hash `#/...`).
- `social.svelte.ts` (amici, lega, classifiche via `/api/amici`, `/api/lega`, ...), `push.ts` (Web Push, promemoria recupero), `timer.svelte.ts` (recupero), `install.svelte.ts` (installazione PWA), `toast.svelte.ts`, `fx.ts` (animazioni, coriandoli), `sound.ts`.

## Invariants
- Si scrive solo con `put()`/`remove()`; ogni lettura passa da `getModel()`, che ricalcola tutto a ogni modifica (`S.ver`).
- Il campo `m` di un documento cresce a ogni modifica; in conflitto vince la versione più recente e il server rimanda la propria (`rifiutati`).
- Una sola sincronizzazione alla volta (`running`); un documento toccato durante il push resta sporco.
- Ogni documento rispetta lo schema di `@common/docs.ts` (`upgradeDoc`, `split`).
- Ogni chiave passata a `t()` esiste in `en.json` (`scripts/check-i18n.mjs` segnala anche le voci inutilizzate).
- In `DEV` con utente `dev` la sincronizzazione è simulata e si carica un seme di dati.

## Side Effects
- Rete: `/api/io`, `/api/sync`, `/api/foto/<sha>`, `/api/amici*`, `/api/lega`, `/api/classifica/*`, `/api/inviti*`, sottoscrizioni push.
- Disco del browser: IndexedDB, `localStorage` (`peak.sub`, `peak-lang`, chiavi di seme dev).
- `boot()` reindirizza a `/auth/login` senza sessione; `applyLang` ricarica la pagina.
- Timer (`setInterval` di sync ogni 60 s, recupero), notifiche, audio.

## Verification
- Run: `npm --prefix web run check`
- Run: `cd web && node scripts/check-i18n.mjs`
- La logica condivisa è testata in `common/` (vedi il repo root).
