# src

## Overview
Sorgenti dell'app Peak (Svelte 5, runes). `main.ts` monta `App.svelte`, avvia la sincronizzazione e registra il service worker. `App.svelte` è il guscio: barra delle schede, scelta della pagina dal percorso, schermate di caricamento/offline/primo avvio, selettore dell'obiettivo e tutorial.

## Interface
- `main.ts`: entry point. Invia al server gli errori JS non gestiti (`POST /api/errore`, max 5 per pagina), chiama `boot()` (`lib/sync.ts`), `resumeRest()`, registra `/sw.js` solo in produzione.
- `App.svelte`: routing per schede (`home`, `allenamento`, `macchinari`, `me`, `amici`, `impostazioni`, `invito`); applica `data-accent` e la lingua dalle impostazioni sincronizzate.
- `env.d.ts`: dichiara `__APP_VERSION__` (iniettato da Vite da `web/package.json`).
- `app.css`: stile globale. `sounds/`: audio `.m4a` importati con `?url` da `lib/sound.ts`.
- Sottodirectory: [components](./components/README.md), [lib](./lib/README.md), [pages](./pages/README.md).

## Invariants
- Il codice importa la logica condivisa solo con `@common/<file>.ts` (mai percorsi relativi verso `common/`).
- Le pagine non aprono la UI finché `S.ready` e `S.synced` non permettono di sapere se l'account è vuoto (evita il primo avvio per errore).
- Le schede si mostrano solo nelle pagine principali, non nelle sottopagine né in Impostazioni.

## Side Effects
- Listener globali `error`/`unhandledrejection` e `visibilitychange`.
- Registra il service worker con scope `/`.
- Imposta `document.documentElement.dataset.accent` e `lang`.

## Verification
- Run: `npm --prefix web run check`
- Run: `cd web && node scripts/check-i18n.mjs`
