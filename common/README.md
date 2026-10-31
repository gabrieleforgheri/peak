# common

## Overview
Logica di dominio pura in TypeScript, condivisa da `server/` (Node 24, type stripping) e da `web/` (Vite). Nessuna dipendenza, nessun I/O: solo sintassi cancellabile (niente `enum`, niente parameter properties).

Attenzione, non confondere: `common/` (TypeScript, questa cartella) non e' `Shared/` (Swift, radice del repo, contiene solo `WorkoutActivityAttributes.swift` per le Live Activity). `common/` e' invece il *gemello TypeScript* della logica Swift in `Peak/Model`, `Peak/Utils`, `Peak/Design`: i file portano in testa il riferimento allo Swift corrispondente (es. `domain.ts` da `Massimali/Model`, `stats.ts` da `Utils/Stats.swift`, `plan.ts` da `WorkoutTemplate.swift`, `medals.ts` da `Medals.swift`, `records.ts` da `RecordRules.swift`).

## Interface
- `docs.ts`: i documenti sincronizzati e il loro confine di fiducia. `KINDS`, `parseDoc(kind, uuid, data)` (validatore, ritorna `{data}` o `{error}`), `upgradeDoc`, `KEEP_IF_ABSENT`, `SETTING_DEFAULTS`, `LEGACY_SETTINGS`, `split(docs)`, `LiveDoc`, `normUuid`, `newUuid`, `isoSec`, `parseIso`, `floorSec`, `cleanItems`, `cleanMuscles`, `isTemplateIcon`, `MUSCLE_GROUPS`, `GLYPHS`, `TEMPLATE_ICONS`, `ACCENTS`, `CATALOG_ID_RE`.
- `domain.ts`: `oneRepMax` (Epley), `beats`, `bestOf`, `EPSILON`, `warmupPlan`, `nextLoad`, `progressionFor`, `loadPlates`, `plateMode`, `toDisplay`/`toKg` (kg <-> lb, `KG_PER_LB`), `settingsForUnitChange`.
- `records.ts`: `deriveRecords(manual, sets, liveWorkouts)` ricava i PR automatici e i flag PR delle serie; `recordsByExercise`, `currentBest`.
- `stats.ts`: `weekStart` (settimane da lunedi'), `workoutVolume`, `weeklyVolume`, `volumeByGroup`, `topMovers`, `sessionCount`, `weeklyStreak`, `weeklyGoal`.
- `plan.ts`: `sessionItems`, `itemFromSets`, `addItem`/`removeItem`/`replaceItem`/`moveItem`, `differsFromTemplate`.
- `catalog.ts` + `catalog.json`: tipo `CatalogEntry`, `matchByName`, `searchCatalog`, `norm`. `catalog.json` e' la fonte unica del catalogo (id slug mai cambiati ne' riusati); `server/catalog.ts` lo legge e lo serve.
- `muscle-info.ts` (`MUSCLES`, `MUSCLE_INFO`, `isMuscle`), `muscles.ts` (`figureFor`, `viewFor`, `zoomFor`, `pieces`, `groupOf`), `anatomy.ts` (contorni, GENERATO da `scripts/gen-anatomy.ts`: non modificarlo a mano).
- `glyph.ts` (`SHAPES`, `suggestedGlyph`, `isGlyph`, `glyphOf`, `glyphSvg`), `template-icon.ts` (`suggestTemplateIcon`), `medals.ts` (`medals`, `medalTotals`), `format.ts` (`weight`, `volume`, `duration`, `byMonth`...), `install.ts` (`installTarget`), `qr.ts` (`qrMatrix`, `qrSvg`, versioni 1-10).
- `fixtures/`: vettori di prova condivisi con Swift (`records.json`), non codice.

## Invariants
- Invariante di allineamento con Swift: la stessa regola esiste due volte (TS qui, Swift in `Peak/`) e deve dare gli stessi risultati. Per i record e' verificato: `common/fixtures/records.json` e' letto sia da `records.test.ts` sia da `scripts/check_records.sh` (compila `Peak/Model/RecordRules.swift` con `swiftc`, senza Xcode). Per le altre regole l'allineamento e' per convenzione: cambiando una regola si cambia anche lo Swift gemello.
- I record automatici e il flag PR delle serie non si sincronizzano mai: si ricalcolano con `deriveRecords`, uguale su web, server e iPhone.
- I pesi sono sempre in kg nei dati; la conversione in lb avviene solo ai bordi (`toDisplay`/`toKg`).
- `parseDoc` e' l'unico confine di fiducia dei documenti in ingresso; i campi nati dopo un client vecchio stanno in `KEEP_IF_ABSENT` perche' non spariscano.
- Gli id di `MUSCLES` e del catalogo non si rinominano ne' si riusano (finiscono nei documenti).
- Le date dei documenti sono ISO a secondi interi (`isoSec`), gli uuid maiuscoli (`normUuid`), come nell'app iPhone.
- Ordine di rilascio: se cambia il formato dei documenti, prima il server (`common/` + `web/dist`), poi i client.

## Side Effects
- Nessuno: funzioni pure. Unica fonte di tempo implicito `Date.now()` come default di `now` in `stats.ts`; `weekStart` usa il fuso del runtime (il server imposta `TZ=Europe/Rome`).

## Verification
- Run: `npm test` (`node --test common/ server/`)
- Run: `npm run typecheck` (al momento segnala 4 errori `TS2739` su `ExerciseDoc` incompleto in `server/backup.ts` e in test del server: preesistenti)
- Record Swift vs TS: `scripts/check_records.sh`
