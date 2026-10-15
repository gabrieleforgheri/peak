# Peak — stato al 15 ottobre 2026 (leggere per primo)

Massimali diventa **Peak**: l'app web su `peak.yrb4g.com` (stessi dati dell'iPhone, sul server, col login di yG) e
l'iPhone che sincronizza con lo stesso server. Le decisioni prese con l'utente stanno nel piano approvato
(`~/.claude/plans/the-task-is-to-gentle-planet.md`, fuori dal repo).

## Fatto e verificato (ramo `main`)

| Pezzo | Dove | Prova |
|---|---|---|
| Logica condivisa web+server (1RM, record derivati, riscaldamento, progressione, dischi, statistiche, pittogrammi, catalogo da 98, formati) | `common/` | `npm test` |
| Server senza dipendenze (`node:http`, `node:sqlite`): login yG (OIDC+PKCE a mano), sync, foto, import/export backup, dispositivi, Web Push, portale per yG | `server/` | `npm test` (58 test, con un finto authentik) |
| Client web offline (Svelte 5, IndexedDB, service worker), tutte le schermate, IT+EN | `web/` (build committata in `web/dist`) | `cd web && npx svelte-check && node scripts/check-i18n.mjs && npx vite build`; prova nel browser con coda offline e secondo dispositivo |
| Pagina `/account/peak`, tile, blueprint 210, script dei segreti, selftest | repo **yG** (commit `c01f50a`, spinto e deployato il 14 ott) | `npm test` in yG, 100 controlli |
| Benvenuto web prima del login: `/` senza sessione → `/benvenuto.html` (5 scene animate, logo che passa in alto a sinistra, sfondo video fatto con `/brag`, «Accedi con yG») | `web/public/benvenuto.*`, `server/index.ts`; sfondo in `brag-output-sfondo/` (non tracciata) | `npm test` (anche il `Range` dei `.mp4`); Chrome headless a 390 px |
| App iPhone rinominata Peak (cartelle, target, nome visibile, 2.0) | `main` | compila per dispositivo (senza simulatore) |

Bundle id, nome dello store SwiftData, chiavi UserDefaults e **modulo Swift (`PRODUCT_MODULE_NAME: Massimali`)** non sono
cambiati di proposito: i dati degli utenti non si toccano.

## Da fare

1. **Sito online dal 14 ott** (peak.yrb4g.com): server Pelican, proxy NPM, blueprint 210 + `svc-peak`, segreti, deploy di yG e cron notturno
   (`peak-sincronizza-accessi.sh --locale`, 03:30 sull'Oracle) sono a posto. Mancano due cose che solo l'utente può fare: **Restart del server
   in Pelican** (porta `/sw.js` con `no-store`: Cloudflare lo teneva 4 ore; poi `curl -sI https://peak.yrb4g.com/sw.js` non deve dire `HIT`)
   e **un primo login** con un utente in `svc-peak`. Dopo il login: `/account/peak` su yG deve mostrare i dati, e si lancia una volta a mano
   `peak-sincronizza-accessi.sh`. Il `sub` è lo stesso in yG e Peak (entrambi i provider usano `hashed_user_id`); l'email è solo il ripiego.
2. **iPhone** (ramo `ios-sync`): scritto e **compila per dispositivo** (app e target dei test, senza simulatore; i test non sono stati eseguiti).
   Collegato: `SyncService` in `PeakApp`/`RootView` (avvio, ritorno in primo piano, dopo ogni salvataggio), `WelcomeView` («Collega a yG»
   grande, «Salta» piccolo, una volta sola anche per chi aggiorna), `ConnectFlow`/`ConnectFlowView` (login, server vuoto, conflitto «Unisci /
   Usa i dati del server», copia `Peak-prima-del-collegamento.json` prima di toccare qualcosa; il token si salva solo a scelta finita), banner
   fisso nella lista, sezione «Account yG» nelle impostazioni, import via server da collegati, «Cancella tutto» anche per le pesate e fino al
   server. Correzioni: passi e dischi in lb (dischi americani, bilanciere 45 lb), calcolatore dischi anche per panche e rack (non «manubri»),
   pittogramma dedotto dal nome finché non lo scegli a mano, pesate modificabili e cancellabili, riscaldamento e progressione nell'unità
   scelta. **Da provare sul telefono vero** (dopo un backup): migrazione SwiftData, login, le due scelte del conflitto, una serie offline che
   si sincronizza, `RecordRulesTests`/`RecordServiceTests` (mai eseguiti: serve il via libera per il simulatore).
3. Rinominare il repo GitHub in `peak` **insieme** a `docs/apps.json` e a `lib/studio.js` di yG (GitHub Pages non fa redirect).
4. Tag `v2.0` (pubblica su AltStore) e installazione sul telefono vero **con un backup prima**.

## Trappole

- Il benvenuto è HTML/CSS/JS a parte (`web/public/benvenuto.*`), non Svelte: `APP_CSP` non ammette script inline. Il server legge `web/dist` all'avvio, quindi dopo `vite build` va riavviato. Un `.mp4` ha bisogno delle risposte `206` (Range), altrimenti Safari non lo riproduce. Il redirect di `/` senza sessione deve restare un redirect: il service worker non mette in cache i redirect, una pagina 200 finirebbe nel guscio offline.
- Per rifare lo sfondo: `brag-output-sfondo/composition` (`npx hyperframes check`, poi `render --output ../brag.mp4`) e copiare in `web/public/benvenuto.mp4`.

- Il codice TypeScript sta in `common/`, non in `shared/`: su macOS `shared/` e `Shared/` (Swift) sono la stessa cartella.
- Su Pelican (egg 1) ogni riavvio è anche un deploy: `git pull --ff-only` e `node server/index.ts`, **niente npm**. Il server stampa
  `Server avviato sulla porta N`: Wings lo cerca per segnare il server come avviato.
- Provato solo con Node 26; l'egg usa `nodejs_24`.
- Mai `xcodebuild test` o simulatore senza chiedere (blocca il Mac). La regola dei record in Swift si prova con
  `scripts/check_records.sh` (solo swiftc) sugli stessi vettori di `common/fixtures/records.json`.

---

# Handoff — Massimali 1.3 (ottobre 2026)

Stato del lavoro per chi riprende, persona o agente. Il "come funziona" sta nel README;
qui ci sono cosa è stato fatto, cosa è verificato e cosa resta aperto.

## Fatto (un commit per funzionalità, su `main`)

| Funzionalità | Dove | Interruttore |
|---|---|---|
| Riscaldamento sulla griglia del macchinario: dal massimale si scende di N passi fino al peso più vicino al calcolato | `Model/Warmup.swift` | — |
| Esecuzione mono/bilaterale per macchinario (editor, dettaglio, backup) | `Model/Exercise.swift` `isUnilateral` | — |
| Timer di recupero: globale + per macchinario, ±15 s, Live Activity, notifica locale | `Store/RestTimer.swift` | Impostazioni → Allenamento |
| Prossimo carico (doppia progressione, range rip nelle impostazioni) | `Model/Progression.swift` | idem |
| Calcolatore dischi animato (bilanciere / plate loaded, dal pittogramma) | `Model/Plates.swift`, `Design/PlateBarView.swift` | idem |
| App Watch: conta le ripetizioni sulle braccia, vibra, registra la serie sull'iPhone | `MassimaliWatch/`, `WatchShared/`, `Store/WatchBridge.swift` | Impostazioni → Apple Watch |
| Installazione con il Watch dal Mac | `scripts/install_device.sh` | — |

Le serie dall'iPhone e dal Watch passano dallo stesso `RecordService.addSet`.

## Decisioni prese con l'utente

- Riscaldamento: peso **più vicino** a quello calcolato, non per difetto.
- Mono/bilaterale: solo etichetta, più il Watch. Nessun effetto su volume o serie per lato.
  Braccia/gambe si deducono dal gruppo muscolare (`worksLegs`).
- Watch, modalità di default: via (dal telefono o dal Watch) e fine automatica dopo N s
  fermo (default 4). Le altre due: via e fine a mano, tutto automatico. Tutto regolabile.
- Tutte le funzioni nuove si possono disattivare nelle impostazioni.
- **AltStore resta senza Watch; l'utente usa la versione con il Watch installata dal Mac.**

## Perché il Watch non passa da AltStore (verificato, non teoria)

Prove fatte sui dispositivi reali con una build di diagnostica (log `PROBE`):

1. App Watch installata da sola, app iPhone senza `Watch/`: entrambe le parti rispondono
   `watchAppInstalled`/`companionInstalled=false`.
2. App iPhone con `Watch/`: l'iPhone non installa da solo l'app sull'orologio (le build
   di sviluppo non vengono spinte).
3. App iPhone con `Watch/` e **poi** app Watch installata direttamente: collegate.
   Contesto ricevuto, app aperta da HealthKit, serie 42,5 × 7 arrivata all'iPhone.
4. App iPhone sostituita con una senza `Watch/`: il Watch torna a `companionInstalled=false`.

AltSign (`rileytestut/AltSign`, `ALTSigner.mm` + `ldid.cpp` riga ~2240) considera
annidati solo `Frameworks/*.framework` e `PlugIns/*.appex`. Così rifirma l'eseguibile in
`Watch/` senza entitlement e l'installazione fallisce. Si sblocca solo con un account a
pagamento (TestFlight/App Store) o se AltStore risolve le issue #229 e #684.

## Verificato / non verificato

- ✅ Test unitari fino al calcolatore dischi: 66 test verdi (Warmup, Progression, Plates,
  backup con i nuovi campi, ecc.).
- ✅ `RepCounter`: algoritmo provato con uno script sul Mac su segnali sintetici
  (10 rip verticali, 8 diagonali, polso fermo = 0, sensibilità, rip veloci da 1,2 s).
- ✅ Collegamento iPhone ↔ Watch end-to-end sui dispositivi (prova 3 sopra).
- ⚠️ `RepCounterTests.swift` e la suite completa dopo i commit Watch **non sono stati
  eseguiti nel simulatore**: l'utente ha chiesto di non lanciare test (il Mac si blocca).
  L'app iPhone e il Watch compilano.
- ⚠️ **Precisione del conteggio su esercizi veri: da provare in palestra.** La manopola
  è la sensibilità nelle impostazioni. Se sbaglia sistematicamente, il passo successivo è
  una soglia adattiva sull'ampiezza delle prime ripetizioni (commento `ponytail:` in
  `RepCounter.swift`).
- ⚠️ Il riferimento fisso per l'accelerazione (`WatchModel.handle`) usa la trasposta di
  `attitude.rotationMatrix`. Non è verificato che z sia davvero verticale; l'asse
  principale lo rende comunque robusto.
- ⚠️ Live Activity: a recupero finito con l'app in background resta su 0:00 finché l'app
  non la aggiorna (commento `ponytail:`).

## Da sapere prima di toccare qualcosa

- **Non lanciare test o simulatore senza chiedere**: su questo Mac lo bloccano. Per
  controllare che compili, build per `generic/platform=watchOS Simulator` o per il
  dispositivo. Per la logica pura basta uno script `swiftc` sul Mac.
- Il simulatore iPhone 17 Pro del README non esiste più: c'è `iPhone 18 Pro`.
- Bundle id: l'app AltStore è `com.gabrieleforghieri.massimali.HKR6AKCLPA`, il Watch è
  `….HKR6AKCLPA.watchkitapp` (fisso in `project.yml`). Se cambia team va cambiato lì,
  e lo script si ferma se non corrisponde.
- Nessun nuovo entitlement o chiave privacy nell'app iPhone, quindi `docs/apps.json`
  non è cambiato. HealthKit e Motion sono solo nel target Watch, che non va su AltStore.
- Su AltStore è pubblicata la 1.3.2 (tag `v1.3.2`, CI `altstore-release.yml`) **senza Watch**. È identica alla
  1.3.1: serve solo a far **rifirmare** l'app. Il refresh del 9 ottobre aveva scaricato un profilo senza HealthKit
  mentre l'app era firmata con HealthKit, e iOS non la apriva più («Profile Missing»); il refresh rinnova il
  profilo, non la firma. Finché l'App ID non riottiene HealthKit (build Xcode, serve l'Apple ID in Xcode),
  su AltStore la parte Salute non funziona.
- L'utente è tornato alla versione AltStore sull'iPhone: per lui la versione con il Watch **non è ancora pronta** (problemi da raccogliere con l'utente prima di riprendere).

## Prossimi passi possibili

1. Prova in palestra del conteggio (curl, spinte, lat machine, rematore) e taratura della
   sensibilità o soglia adattiva.
2. Eseguire la suite di test quando il Mac è libero
   (`xcodebuild test … -destination 'platform=iOS Simulator,name=iPhone 18 Pro'`).
3. Ricordare che aggiornare Massimali da AltStore toglie il Watch: dopo, rilanciare
   `install_device.sh`.
4. Eventuale: countdown del recupero anche sul Watch, vibrazione a fine recupero al polso.
