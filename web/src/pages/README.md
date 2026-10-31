# pages

## Overview
Le schermate di Peak, una per percorso a hash. `../App.svelte` sceglie quale montare da `route.parts` (`../lib/router.svelte.ts`); le pagine compongono i componenti di [../components](../components/README.md) e leggono/scrivono tramite [../lib](../lib/README.md).

## Interface
Percorso -> pagina:
- `#/home` -> `Home`; `#/invito/<codice>` -> `Invite` (prop `code`).
- `#/macchinari` -> `Maxes`; `#/macchinari/<id>` -> `ExerciseDetail`.
- `#/allenamento` -> `Workouts`; `/<id>` -> `Session`; `/<id>/riepilogo` -> `Summary`.
- `#/me` -> `Me`; `#/amici` -> `Friends`; `#/amici/<id>` -> `FriendProfile`.
- `#/impostazioni` -> `Settings`; `#/impostazioni/archivio` -> `Archived`.
- `FirstRun` (account vuoto, prop `onstart`), non legata a un percorso.

## Invariants
- Le pagine non toccano IndexedDB né `fetch('/api/sync')`: scrivono solo con `../lib/actions.ts`.
- Testi sempre con `t()`/`tn()`; nessuna emoji.
- Il remount avviene a ogni cambio di percorso (`{#key route.parts.join('/')}` in `App.svelte`).

## Side Effects
- `Session` avvia il timer di recupero, suoni e Web Push; `Summary` e `Me` usano coriandoli e animazioni (`fx.ts`).
- `Friends`, `FriendProfile`, `Invite` chiamano le API sociali del server in `server/`.
- `Settings` può cambiare lingua (ricarica la pagina), esportare/importare dati e cancellare tutto (`eraseAll`).

## Verification
- Run: `npm --prefix web run check`
- Run: `cd web && node scripts/check-i18n.mjs`
