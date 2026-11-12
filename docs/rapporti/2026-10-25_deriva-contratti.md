# Drift report — contratti EGM (2026-10-25)

Controllo dei 30 contratti generati da `egm-bootstrap` contro il codice attuale.
Due voci riguardano il codice, non la documentazione, e vanno guardate per prime:

- **Sicurezza** — `server/auth.ts:171`: `GET /auth/dev` non controlla `cfg`. Anche con OIDC configurato crea l'utente `dev` e una sessione di 30 giorni a chiunque apra l'URL, saltando authentik e il gruppo `svc-peak`. Solo `/auth/login` (`auth.ts:179`) ci reindirizza quando `cfg` è nullo.
- **Perdita dati** — `Peak/Features/Workouts/WorkoutSessionView.swift:524`: `finish()` chiama `BackupService.writeAutoBackup` senza `!AppStore.isFallback`; con lo store di emergenza in memoria sovrascrive `backup-latest.json` buono con un file quasi vuoto (il motivo è scritto in `RootView.swift:106`, che invece controlla).

## Peak/README.md

- L'invariante "Model non dipende da Store" è falsa: `Peak/Model/Catalog.swift:138` usa `PeakAPI.base` (Store); `Peak/Model/Programs.swift:88,104` prende un `ModelContext` e salva.

## Peak/Design/README.md

- "Nessun accesso a dati" / "Nessuno oltre al disegno" falsi: `Celebration.swift:323-352` (`Celebrated`) legge/scrive UserDefaults; `Components.swift:126,161,176,184` chiama `Haptics.tap()`; `Celebration.swift:20` legge `AppSettings`.
- `MachineGlyphShapes` non è un tipo: il file contiene un'estensione di `enum MachineGlyph`; `GlyphShape` sta in `MachineGlyph.swift:6`.
- `Theme` non contiene l'accento: solo `Theme.background(accent:)` (riga 43) lo riceve.
- Mancano `FigurePath` (`MuscleFigureView.swift:66`) e `anatomyView(for:)` (`MuscleFigureView.swift:92`).

## Peak/Model/README.md

- "Regole pure, senza ModelContext" contraddetta da `Programs.adoptLoose(context:name:)` e `Programs.current(context:settings:)` (`Programs.swift:88,104`), che leggono, inseriscono e salvano.
- "Il resto non ha effetti" falso: oltre a `Programs`, `Catalog.refresh` (`Catalog.swift:136-155`) scrive `catalog.json` su disco ed ETag/build in UserDefaults.
- Manca `TemplateIconView` (`Programs.swift:48`), una vista SwiftUI dentro Model.

## Peak/Store/README.md

- `RecordService` non è l'unico percorso di scrittura delle serie: le creano anche `BackupService.swift:298-308` (ripristino) e `SyncDocs.swift:370` (sync); modifiche in place in `SyncDocs.swift:376-377` e `WorkoutSessionView.swift:454-455`.
- "Con isFallback niente backup automatico" non vale per `WorkoutSessionView.swift:524` (vedi in testa); `writeAutoBackup` (`BackupService.swift:193`) non ha una guardia propria.
- Mancano `RecordService.recomputeAll`, `delete(...)`, `personalRecords(in:)`.

## Peak/Utils/README.md

- "La conversione avviene solo in WeightUnit": anche `Model/Plates.swift:9-18` ha un proprio `kilogramsPerPound` per dischi e bilanciere.
- `Localized.swift:43-64` estende anche `View` (`navigationTitle`, `accessibilityLabel`, `searchable`, `alert`, `confirmationDialog`).
- Effetti mancanti: `SoundFX` legge UserDefaults (`SoundFX.swift:33`).

## Peak/Features/README.md

- "Solo viste SwiftUI; logica e dati in Model/Store/Utils" falso: `Root/ConnectFlow.swift` è una macchina a stati, `HomeStats` (`Home/HomeSections.swift:7`) e `Progression1RM` (`Progress/ProgressionCard.swift:5`) sono logica, e diverse viste scrivono SwiftData direttamente.

## Peak/Features/Friends/README.md

- "Nessuna persistenza locale" falso: `FriendProfileView.copy(_:)` (`FriendProfileView.swift:138-169`) inserisce `Exercise` e `WorkoutTemplate` e salva ("copia il giorno di un amico").
- Effetti di rete mancanti: rimozione amico (`FriendProfileView.swift:78`), caricamento profilo (`:69`).

## Peak/Features/Home/README.md

- "Sola lettura" falso: `HomeSections.swift:187-188` scrive `Celebrated` in UserDefaults; suoni e aptica (righe 205-225); `HomeView.swift:61` fa `social.refresh()` (rete).
- Dipendenze mancanti: `SyncService.shared.credentials` (`HomeView.swift:14,25`), `SocialStore`.

## Peak/Features/Maxes/README.md

- La proprietà è `catalogID`, non `catalogId` (`Exercise.swift:31`).
- `MaxesListView` non usa `MaxRanking`: ordina per `sortIndex` (riga 9); `MaxRanking` è usato in `EditMaxSheet.swift:28`.
- `ExerciseDetailView` non mostra medaglie; mostra storico, suggerimento di progressione, riscaldamento e classifica.
- "I record da allenamento non si modificano a mano": il cestino in `ExerciseDetailView.swift:429-430,500-503` cancella anche i record `isEstimated`.
- Effetti mancanti: `api.board(catalog:)` (`ExerciseDetailView.swift:110-112`), archiviazione (`MaxesListView.swift:213-215`), cancellazione (`ExerciseEditorView.swift:735`).

## Peak/Features/Progress/README.md

- `MeView` mostra anche medaglie, volume settimanale, top movers e record (`MeView.swift:20-21,110-201`) da `HomeStats`/`Medals`: dipende da `Features/Home`.
- `ProgressionCard` non usa `OneRepMax` ma `estimatedOneRepMax` di `MaxRecord`/`WorkoutSet` (righe 12, 15).
- Effetto mancante: cancellazione delle pesate (`BodyWeightCard.swift:311`).

## Peak/Features/Root/README.md

- Il backup all'avvio è condizionato (`RootView.swift:105-111`: salta con `isFallback` o account collegato).
- Mancano le migrazioni una tantum `linkCatalogOnce()` (riga 83) e `freezeGlyphsOnce()` (riga 96).
- `Peak-prima-del-collegamento.json` si scrive solo se `sync.localHasExercises` (`ConnectFlow.swift:49-50`).

## Peak/Features/Settings/README.md

- Effetti mancanti: cancella tutto + `sync.permitMassDeletion()` (`DataSettingsView.swift:74-83`); carica un backup sul server e sostituisce i dati locali (`BackupSettingsView.swift:168-170`); `WatchBridge.enabledChanged()` (`WatchSettingsView.swift:24`).

## Peak/Features/Workouts/README.md

- Oltre a `addSet`, le serie si modificano in place (`WorkoutSessionView.swift:454-457`) e si cancellano (`RecordService.delete`, riga 493).
- Effetti mancanti: `Celebrated.streak` in UserDefaults (`WorkoutSummaryView.swift:107-109`), backup automatico a fine sessione (`WorkoutSessionView.swift:524`), inserimento `Workout` (`WorkoutsListView.swift:265`).
- Dipendenze mancanti: `Medals`, `OneRepMax`, `Programs`, `Catalog`.

## PeakWatch/README.md

No drift detected.

## PeakWidgets/README.md

No drift detected.

## Shared/README.md

No drift detected.

## WatchShared/README.md

No drift detected.

## common/README.md

- I 4 errori di `npm run typecheck` non sono tutti TS2739 né tutti in `server/`: `common/domain.test.ts(125,8)` TS2345, `server/backup.test.ts(159,92)` TS2322, `server/portale.test.ts(34,35)` TS2322, `server/backup.ts(328,37)` TS2739.

## server/README.md

- `GET /auth/dev` non è limitato a "OIDC non configurato" (vedi in testa).
- Mancano il cookie `peak_invito` (1 h) e il redirect a `/#/invito/<codice>` dopo il login (`index.ts:98-102`), la scadenza di 7 giorni degli inviti (`store.ts:228`) e il limite di 20 inviti per utente.
- Elenco degli errori di typecheck impreciso (come sopra).

## web/README.md

No drift detected.

## web/public/README.md

No drift detected.

## web/scripts/README.md

No drift detected.

## web/src/README.md

- Effetti mancanti: `clearRestNotification()` a ogni ritorno visibile (`main.ts:4,20`); `adoptLooseTemplates(...)` scrive nello store (`App.svelte:42`).

## web/src/components/README.md

- `Tour` e `Heatmap` non importano `fx.ts` né `sound.ts`: li usano solo `SummitHero.svelte:3` (`countUp`) e `AddSetSheet.svelte:8` (`play`).

## web/src/lib/README.md

- Chiavi localStorage mancanti: `peak.push` (`push.ts:6`), `peak.rest` (`timer.svelte.ts:7`), `peak.sounds` (`sound.ts:23`), `peak.install-dismissed` (`install.svelte.ts:7`), `peak.celebrated.*` (`fx.ts:164-165`).
- Endpoint mancanti: `/api/novita` (`social.svelte.ts:28`), `/api/recupero` (`push.ts:58,65`), `/api/push` (`push.ts:31,41`).
- Il router rinomina i vecchi percorsi `massimali`/`allenamenti`/`progressi` (`router.svelte.ts:9-11`).
- Export mancante: `countDirty` (`store.svelte.ts:50`).

## web/src/pages/README.md

- `Me` non usa `fx.ts`; lo usano `Summary`, `Home` e `Session`.
- `FirstRun` e `Settings` chiamano direttamente il server con `call()` (`/api/importa`, `/api/backup`, `/api/dispositivi`) e `syncNow`/`resync`; `Settings` usa `enablePush`/`disablePush`.
- Anche `Home`, `Me` ed `ExerciseDetail` usano `lib/social.svelte.ts`.
- `Session` non avvia Web Push direttamente: passa da `timer.svelte.ts`.

## scripts/README.md

- Mancano `SENZA_WATCH=1` e gli override `IPHONE_ID`/`WATCH_ID` di `install_device.sh` (righe 22-24, 46-48) e il prerequisito `npm i --no-save playwright-core` di `studio-page.sh` (riga 6).

## scripts/check_records/README.md

No drift detected.

## studio-clips/README.md

- Tutte le clip scritte a mano sono 780x1688, non solo l'intro (`sync.html:45`, `record.html:54`).
- Anche `schermata-*.html` è in `.gitignore`.

## README.md

- Global Side Effects mescola tre script: solo `build_ipa.sh` riscrive `build/`; `install_device.sh` lavora in `mktemp`; `studio-page.sh` cancella `studio-clips/shots` e `renders`.
