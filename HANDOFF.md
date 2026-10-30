# Watch come l'app Allenamento (31 ottobre 2026)

Solo Watch (+ due campi opzionali nel messaggio dall'iPhone). Installazione: NON da AltStore (l'ipa non contiene il Watch), ma dal Mac con `scripts/install_device.sh`, poi si rinnova ogni 7 giorni. Codice scritto da un subagente Sonnet, da rivedere. Provato solo compilando e con un banco di prova iOS: nessun simulatore watchOS installato e nessun orologio vero.

| Fatto | Dove |
|---|---|
| Pagine da scorrere come in Allenamento: Comandi (Termina rosso, Pausa/Riprendi giallo, Nuova serie verde) · Numeri (tempo giallo `mm:ss.cc`, kcal attive e totali, battito con cuore che pulsa, esercizio, obiettivo, serie fatte) · Serie (peso con Digital Crown, ripetizioni, riscaldamento, Salva) · Musica (`NowPlayingView`) | `ContentView.swift`, `WorkoutPages.swift`, `LogPage.swift` |
| Misure in diretta con `HKLiveWorkoutBuilder` (battito, calorie attive, basali). A fine sessione l'allenamento si SCARTA (`discardWorkout()`): in Salute lo scrive già l'iPhone, salvarlo anche qui lo duplicherebbe. Prezzo: battito e kcal del Watch non restano in Salute | `WorkoutController.swift` |
| Recupero dopo «Salva»: anello a tutto schermo che si svuota, «Salta», vibrazione alla fine. Durata dall'iPhone: nuovi campi opzionali `restSeconds` / `warmupRestSeconds` in `WatchExercise` (vecchi messaggi si leggono lo stesso; senza `restSeconds` il Watch non recupera). Il riscaldamento recupera la metà (`// TODO warmupRestPercent` in `WatchBridge.swift`, da collegare quando c'è l'impostazione) | `WatchModel.swift`, `WatchMessages.swift`, `WatchBridge.swift` |
| Riepilogo a fine sessione (tempo, kcal, battito medio, serie) e «Fine». Conto alla rovescia 3-2-1 quando parte dall'iPhone | `SummaryView.swift`, `ContentView.swift` |
| Senza allenamento: scheda «Allenamento di forza»; il tocco avvia la sessione sul polso e dice di aprire un esercizio sull'iPhone. Schermo attenuato (`isLuminanceReduced`): niente centesimi, niente puntini | `ContentView.swift` |
| Termina sul Watch chiude solo la sessione del polso: l'allenamento dell'iPhone resta aperto. L'iPhone che chiude l'allenamento porta il Watch al riepilogo (solo se prima c'era un esercizio: un contesto vuoto vecchio non chiude più la sessione appena partita) | `WatchModel.swift` |
| `NSHealthShareUsageDescription` del Watch ora cita battito e calorie (`project.yml` + `Info.plist`). `docs/apps.json` invariato: il Watch non è nell'ipa di AltStore | — |
| Aperto: la sessione non si recupera se il sistema chiude l'app a metà (`handleActiveWorkoutRecovery`); sullo schermo vero vanno controllati spazi del 41 mm e comportamento della corona dentro il `TabView` | — |

---

# Benvenuto nuovo, medaglie, animazioni, zoom dei muscoli (29 ottobre 2026 — Peak 3.4, build 16)

Onboarding, medaglie e animazioni solo sul web per ora (iPhone dopo); zoom e dimezzamento a un braccio su web + iPhone. Codice scritto da subagenti Sonnet, rivisto e unito a mano.

| Fatto | Dove |
|---|---|
| Primo avvio a tutta pagina: montagna disegnata come un grafico, giorni a settimana, gruppi (un giorno per gruppo nella scheda «La mia scheda»), macchinari proposti; niente import per i nuovi (resta nelle Impostazioni) | `Onboarding.svelte`, `SummitDraw.svelte`, `common/onboarding.ts` |
| Tutorial misto: 4 idee animate, poi riquadri sui controlli veri fino alla prima serie salvata (scelta giorno, «+ Serie», peso e ripetizioni, «Salva la serie»); saltabile | `Ideas.svelte`, `Tour.svelte`, `AddSetSheet.svelte` |
| Medaglie in Me: tre righe vicine al livello dopo, «Mostra tutte», foglio con anello, percorso e date | `Me.svelte`, `MedalSheet.svelte`, `Medal.svelte`, `common/medals.ts` `tierDates` |
| Animazioni sobrie: pagine, contenuti, numeri, grafici, fogli; rispettano «riduci movimento» | `app.css`, `fx.ts`, `App.svelte`, `Sheet.svelte` |
| Scheda Muscoli: tocca una figura per ingrandirla | `MuscleZoomView.swift`, `MuscleZoom.svelte` |
| Da 2 a 1 braccio il peso si dimezza sulla griglia del macchinario (pari verso il basso); tornando a 2 si ripristina o raddoppia. Macchine senza sbarra di default | `common/domain.ts` `switchArm`, `Warmup.swift` `ArmSwitch` |
| Aperto: sbarra di default di Preacher Curl (iPhone) e EZ-Bar Preacher Curl (web) non allineate | — |

---

# «Scorso» a due pagine e serie a un braccio (28 ottobre 2026 — Peak 3.3, build 14)

Web + iPhone in parità. Codice scritto da subagenti Sonnet, rivisto e unito a mano.

| Fatto | Dove |
|---|---|
| «Scorso» nella card dell'esercizio: due pagine da scorrere (ultime serie / ultimi 3 allenamenti in riga da taccuino `71,5 kg 8·6 · 54 kg 8`), puntini sotto | `WorkoutSessionView.swift`, `Session.svelte`, `Fmt.notebookLine`, `common/format.ts` `notebookLine` |
| Serie «Un braccio» (`singleArm` su serie e massimali manuali): peso del singolo manico, rip per lato; linea di massimali separata («Massimale a un braccio» nel dettaglio), tutto il resto usa solo le due braccia; volume ×2; segno «1 br» | `common/docs.ts` (+`KEEP_IF_ABSENT`), `common/records.ts`, `RecordRules.swift`, `AddSetSheet`, `EditMaxSheet`, `WatchMessages.swift` |
| 3.3.1: controllo «2 \| 1» accanto a «Peso» (non più la card), parole secondo il gruppo (gambe «1 gb», core senza controllo), le serie nuove partono sempre a 2, Watch senza segno | `SideSegment.swift`, `SideSegment.svelte`, `MuscleGroup.side`, `sideText` in `common/format.ts` |
| Primo sync dopo l'aggiornamento: l'iPhone rimanda una volta tutte le serie e i record (nuovo campo `singleArm`) | `SyncDocs.swift` |
| Manichino 3D scartato e archiviato in `scripts/archive/gen-muscle3d.py` (Z-Anatomy CC BY-SA 4.0, serve Blender) | — |

Ordine di rilascio: prima il server (un server vecchio scarta `singleArm`), poi l'iPhone.

---

# Macchinario nuovo passo per passo, barre amici in proporzione (27 ottobre 2026 — Peak 3.2, build 12)

Web + iPhone in parità. Codice scritto da subagenti Sonnet, rivisto e unito a mano.

| Fatto | Dove |
|---|---|
| Podio amici: altezza in proporzione al primo (minimo 28); le barre delle righe passano dalla stessa `barFraction` | `FriendsView.swift` (`Podium.barFraction`), `Podium.svelte`, `fmt.ts`, `Friends.svelte` |
| Bug: scelto il gruppo, la lista del catalogo era vuota (taglio a 40 prima del filtro). Ora `search(query, group:)` filtra prima; ricerca vuota + gruppo = tutto il gruppo | `Catalog.swift`, `common/catalog.ts` (+ test) |
| Macchinario nuovo in 3 passi: gruppo → lista (già piena, con ricerca) → nome + incremento, con «Altro» chiuso (sbarra, dischi, foto, recupero, note). La modifica resta la pagina di prima | `ExerciseEditorView.swift`, `CatalogPickerSheet.swift`, `ExerciseEditor.svelte`, `CatalogPicker.svelte` |
| Suggerimenti della prima volta sui controlli veri (uno per passo), solo se `machineTipsSeen` è falso e non hai macchinari; setting sincronizzato | `TourOverlay.swift` (`machineTip`), `Tip.svelte`, `AppSettings`, `SyncDocs`, `common/docs.ts` |
| `seed-dev.ts` scriveva l'amicizia nei due versi (il server ne tiene una per coppia): amici doppi nei dati di prova | `scripts/seed-dev.ts` |
| 3.2.1: tolto il titolo grande «Allenamenti» in cima alla scheda Allenamento (c'è già la montagna) | `WorkoutsListView.swift`, `Workouts.svelte` |
| Aperto: «Rivedi il tutorial» non azzera `machineTipsSeen` | — |

Ordine di rilascio: prima il server (un server vecchio scarta `machineTipsSeen`), poi l'iPhone.

---

# Via la Home: montagna in Allenamento, record recenti in Me (27 ottobre 2026 — PUBBLICATO: Peak 3.1, server 3414196, AltStore v3.1, pagina studio)

Web + iPhone in parità. Codice scritto da subagenti Sonnet, rivisto e unito a mano.

| Fatto | Dove |
|---|---|
| Schede: Allenamento (si apre lì), Macchinari, Me, Amici. Il vecchio `#/home` porta ad Allenamento | `AppNav.swift`, `RootView.swift`, `App.svelte`, `router.svelte.ts` |
| In cima ad Allenamento la montagna, ~30% più bassa (altezza minima 320 → 224), con le feste di settimana e serie | `Workouts/WorkoutHero.swift`, `SummitHero.svelte`, `Workouts.svelte` |
| Pulsante: con i giorni fissi e un giorno oggi «Avvia X» + link «oppure scegli tu»; altrimenti (anche a riposo) «Avvia allenamento» che apre la scelta. Tolti `Programs.next`/`nextDay`, nuovi `Programs.today`/`todayDay` | `Programs.swift`, `derive.ts`, `ProgramTests.swift` |
| Me: «Record recenti» (uno per macchinario) dopo Panoramica; tolto il vecchio «Ultimi record». Novità degli amici solo in Amici | `MeView.swift`, `Me.svelte` |
| Tutorial: passi spostati su Allenamento e Me | `TourOverlay.swift`, `Tour.svelte` |
| Verifiche: `swiftc -typecheck` ok, svelte-check 0, i18n 0 mancanti, `npm test` 107/107, prova in Chrome (build vera su :3016) con e senza il giorno di oggi. | Rilascio: 85/85 test sul telefono vero, installata su iPhone e Watch (il Watch non era nel profilo: registrato con una build di `PeakWatch` verso l'orologio), avvio con i dati veri senza errori nel log; i README di documentazione esclusi dai target in `project.yml`; pagina studio rifatta (clip «home» ora è Allenamento) e testi aggiornati in yG (`lib/seeds.js` + `data/site` sul server, backup `*.bak-peak31-*`) | — |
| Aperto: la serie di settimane vale 0 finché nella settimana in corso non ti alleni (`weeklyStreak`, voluto da `domain.test.ts:207`?) — da decidere | — |

---

# Carico a dischi, peso per lato, storici per mese e banner Installa (24 ottobre 2026 — PUBBLICATO: PR #7, server 5708d13, AltStore v3.0, pagina studio)

Web + iPhone in parità (tranne il banner, solo web). Codice scritto da subagenti Sonnet, rivisto e unito a mano. Pubblicato: server riavviato (commit 5708d13), tag `v3.0` → AltStore 3.0, clip della pagina studio rifatte e yG pubblicato. Da provare: la 3.0 sul telefono vero (migrazione SwiftData con `plateLoaded`/`perSide`).

| Fatto | Dove |
|---|---|
| **Carico a dischi**: campo esercizio `plateLoaded`; `plateMode`/`Plates.barBase` mostrano il calcolatore senza sbarra anche con sbarra 0 se il flag è acceso, il pittogramma è plate loaded o il nome contiene «plate» (es. «Plated row»). Interruttore nell'editor quando la sbarra è 0. Limite: il nome in OR col flag, l'interruttore non spegne un nome con «plate» | `common/domain.ts`, `Peak/Model/Plates.swift`, `ExerciseEditor.svelte`, `ExerciseEditorView.swift` |
| **Peso per lato**: campo `perSide`; nel foglio serie si inserisce un lato, si salva sempre il totale (2 x + sbarra) e sotto c'è «Totale X». Record, 1RM, amici invariati | `AddSetSheet.svelte`, `AddSetSheet.swift`, badge «Per lato» nei dettagli |
| **Serie recenti per giorno** nel foglio serie, ultimi 5 giorni, l'ultimo aperto | come sopra |
| **Storici per mese** (allenamenti, massimali, serie del macchinario, pesate «Tutte le pesate»): mese più recente aperto, tolti i limiti 8/12 | `common/format.ts` `byMonth`, `MonthGroups.svelte`, `Components.swift` `MonthSections`/`MonthStack` |
| **Banner Installa Peak** in Home: istruzioni per iPhone Safari/altri, Android (prompt nativo, Samsung, Firefox, altri), desktop col prompt; chiuso = 30 giorni | `common/install.ts`, `web/src/lib/install.svelte.ts`, `InstallBanner.svelte` |
| Suono: l'interruttore esiste già (Impostazioni → Generale), nessuna modifica | — |
| Verifiche: `npm test` 107/107, svelte-check 0, i18n 0 mancanti, `swiftc -typecheck` di tutto `Peak/` ok, prova in Chrome (build vera su :3016) di banner, storici, Plated row per lato con calcolatore e serie per giorno. iPhone non provato a schermo | — |

Nota sviluppo: in `vite dev` l'utente `dev` non sincronizza (`syncNow` esce subito): per provare con i dati del server usare la build (`vite build`) servita dal server, riavviandolo dopo ogni build.

---

# Visualizzazione serie precedenti, orizzonte temporale grafici, PR retroattivi e UI (20 ottobre 2026 — su `feature/workout-enhancements`, PR #2 aperta)

Ramo di sviluppo `feature/workout-enhancements` (creata la Pull Request #2 verso `main`). Comprende miglioramenti su allenamenti, grafici temporali, visualizzazione schede e gestione record, mantenendo la parità completa tra **Web (Svelte 5)** e **iOS (SwiftUI)**, con traduzioni integrali italiano/inglese e copertura test unitari e corner case.

| Fatto | Dove |
|---|---|
| **Serie precedenti dell'ultimo allenamento**: nella card dell'esercizio durante la sessione vengono mostrate le serie allenanti (escluso il riscaldamento) dell'ultima sessione conclusa in cui è stato eseguito quel macchinario, anziché solo il massimale. Rimossa la dicitura duplicata di carico/ripetizioni. | `web/src/pages/Session.svelte`, `web/src/components/ExerciseCard.svelte`, `Peak/Features/Workouts/WorkoutSessionView.swift`, `Peak/Design/Components.swift` |
| **Titolo esercizio posizionato in cima**: sia nella sessione di allenamento sia nell'editor delle schede (`TemplateEditor`), il nome del macchinario è posizionato sopra le miniature del corpo e del macchinario con pulsanti di azione allineati a destra, garantendo visibilità immediata in ogni risoluzione e viewport. | `web/src/components/TemplateEditor.svelte`, `Peak/Features/Templates/TemplateEditorView.swift` |
| **Pulsante "Vedi macchinario" nei tre puntini**: aggiunta opzione nel menu contestuale a 3 puntini della card esercizio in sessione per aprire direttamente la scheda del macchinario. | `web/src/pages/Session.svelte`, `Peak/Features/Workouts/WorkoutSessionView.swift` |
| **Orizzonte temporale dinamico su TUTTI i grafici**: selettore (`1 mese`, `3 mesi`, `1 anno`, `Tutto`) calcolato dinamicamente in base all'anzianità dei dati (`<30gg` nasconde il selettore, `30..89gg` offre 1m/Tutto, `90..364gg` offre 1m/3m/Tutto, `≥365gg` offre 3m/1y/Tutto). Esteso a: Dettaglio Esercizio, Peso Corporeo, Progressione 1RM (Me / ProgressionCard) e LiftChartCard. Con fallback *"Dati non sufficienti per questo intervallo"* se un intervallo ha < 2 punti. | `Peak/Utils/Stats.swift` (`ChartRange`), `Peak/Features/Maxes/ExerciseDetailView.swift`, `Peak/Features/Progress/BodyWeightCard.swift`, `Peak/Features/Progress/ProgressionCard.swift`, `Peak/Features/Home/HomeSections.swift`, `web/src/pages/ExerciseDetail.svelte`, `web/src/components/BodyWeightCard.svelte`, `web/src/pages/Me.svelte` |
| **PR retroattivo alla modifica serie**: modificare una serie (anche da non-PR a PR o viceversa) ricalcola istantaneamente i massimali automatici e aggiorna il badge verde Personal Record. | `web/src/pages/Session.svelte`, `Peak/Features/Workouts/WorkoutSessionView.swift`, `common/records.test.ts` |
| **Menu a tendina storico serie passate**: menu/foglio a comparsa per visualizzare tutte le serie precedenti eseguite con il macchinario durante l'inserimento o modifica di una serie. | `web/src/pages/Session.svelte`, `Peak/Features/Workouts/WorkoutSessionView.swift` |
| **Localizzazione e testi**: aggiunte traduzioni per tutte le nuove etichette in `en.json` e `Localizable.strings`. | `web/src/lib/en.json`, `Peak/en.lproj/Localizable.strings` |
| **Script di test per macchinari**: creato script per generare macchinari di test che coprono corner case temporali (<30gg, 45gg, 120gg, >1 anno, serie precedenti, PR retroattivo). | `scripts/create-test-machines.ts` |
| **Verifiche e build**: `npm test` (105/105 passati), `npm run check --prefix web` (0 errori, 0 avvisi), `npm run build --prefix web` (dist aggiornato), Xcode build (compilazione iOS Simulator completata con successo). | Verificato in ambiente locale |

---

# Frasi motivazionali ampliate e rotazione a 7s (18 ottobre 2026, sera — su `main`, PR #1 mergiata)

Unite su `main` tramite PR #1. Aggiunte 16 nuove frasi motivazionali (32 totali) con traduzioni complete in italiano e inglese e virgola singola per l'a capo armonioso sia su web che su iOS. Intervallo di rotazione accelerato a 7 secondi (prima 10s). Ambiente locale verificato con Google Chrome su account `dev` con dati di test realistici (8 settimane di allenamenti, medaglie e record). **Installato con successo su iPhone e Apple Watch via wireless** (`scripts/install_device.sh`).

| Fatto | Dove |
|---|---|
| 16 nuove frasi motivazionali (32 totali) con rotazione a 7 secondi (era 10s) | `web/src/components/SummitHero.svelte`, `web/src/lib/en.json` |
| Parità iOS: 16 nuove frasi nell'enum `Motivation`, rotazione a 7s nel task della Home e traduzioni inglesi | `Peak/Model/Medals.swift`, `Peak/Features/Home/HomeSections.swift`, `Peak/en.lproj/Localizable.strings` |
| Login rapido locale `/auth/dev` (attivo solo se OIDC non è configurato) che rilascia il cookie di sessione per l'utente dev | `server/auth.ts` |
| Script per popolare l'account dev locale con 8 settimane di allenamenti, macchinari dal catalogo, schede e amici | `scripts/seed-dev.ts` |
| Verifiche: `npm test` (102/102 verdi), `svelte-check` (0 errori/avvisi), `check-i18n` (0 mancanti), `vite build` | Eseguite in locale |
| Installazione su dispositivi fisici: iPhone e Watch aggiornati via wireless | `scripts/install_device.sh` |

---

# Lag, web a tutto schermo, animazioni e suoni (18 ottobre 2026, sera — su `main`, NON pushato né pubblicato)

**iPhone installato sul telefono e sul Watch** (`scripts/install_device.sh`) con tutto quello qui sotto. Il web è unito ma `web/dist` NON è ricostruito e niente è online: per pubblicare servono build (`cd web && npx svelte-check && node scripts/check-i18n.mjs && npx vite build`), commit di `web/dist`, push e deploy, **ognuno con l'OK dell'utente**. Dopo il deploy: purge Cloudflare di `/sw.js` (con OK).

| Fatto | Dove |
|---|---|
| Lag iPhone: Home e Me leggono i numeri da `Insights` (ricalcolati una volta per raffica di salvataggi, subito dopo una cancellazione); record ricalcolati per differenza; sessione e riepilogo senza conti ripetuti; note salvate a fine digitazione; figure dei muscoli appiattite | `Peak/Store/Insights.swift`, `RecordService.swift`, `MeView.swift`, `HomeView.swift`, `ProgressionCard.swift`, `WorkoutSessionView.swift`, `WorkoutSummaryView.swift`, `MuscleFigureView.swift` |
| Sincronizzazione iPhone fuori dal main: un'istantanea in background (contesto nuovo) per giro, hash uguali a prima, giro in più se si salva durante la sincronizzazione; `commit` resta sul main | `Peak/Store/SyncService.swift`, `SyncDocs.swift` |
| Frasi della Home tradotte anche con la virgola | `Peak/Model/Medals.swift` (`Motivation`) |
| Suoni: 14 file (set, record, finish, streak, goal, medal, remove, day1–7) uguali su web e iPhone; interruttore «Suoni» (del dispositivo) in Impostazioni → Generale; iPhone sessione `.ambient` | `scripts/make-sounds.mjs` (sintetizzati), `web/src/sounds/`, `Peak/Sounds/`, `web/src/lib/sound.ts`, `Peak/Utils/SoundFX.swift` |
| Animazioni iPhone: fine allenamento (coriandoli, numeri, giorni con note che salgono, obiettivo, serie di settimane, medaglie, record), record in sessione (badge, coriandoli, avviso), Home che festeggia solo novità non viste e solo quando si vede | `Peak/Design/Celebration.swift`, `WorkoutSummaryView.swift`, `WorkoutSessionView.swift`, `HomeSections.swift` |
| Web a tutto schermo ≥900px (binario a sinistra, pagine a 2–3 colonne, fogli come finestre); barra in basso che arriva al bordo; fascia piena sotto la barra di stato (meta `black-translucent`) al posto del blur | `web/src/app.css`, `App.svelte`, pagine principali, `index.html` |

**In corso / da fare**
- Suoni scelti dall'utente (18 ott): serie = Kenney `drop_002`, record = Pixabay `notification`, gli altri restano quelli sintetizzati (crediti in `web/src/sounds/CREDITS.md`; `make-sounds.mjs` non li sovrascrive). Pagina di ascolto: `scratchpad/suoni-prova/index.html` della sessione 7e2d33df.
- Animazioni web: unite su `main` (9a4f224), check e build ok; `web/dist` da ricostruire prima del deploy.
- Da provare sul telefono: lag di Allenamento e Me, animazioni, barra in basso e blur in alto della web app (solo dopo il deploy).
- Riga vecchia più sotto: «Impostazioni → Apple Watch» ora esiste davvero (interruttore «Usa l'Apple Watch»).

# Web: ME, scheda generale, icone (18 ottobre 2026, ramo feature/figure-muscoli, non committato)

Web e server pubblicati il 18 ott. **iPhone portato** (scheda generale, Me, riepilogo, macchinario per gruppo, passo 0,25, dischi su due lati): compila (`swiftc -typecheck`), **non provato su telefono né con i test** (da chiedere prima). Manca sull'iPhone: inglese (l'app ha solo italiano; la lingua si sincronizza ma non cambia nulla), icone SVG (qui simboli di sistema), selettore icone «Tutte», montagna della Home.

| Pezzo | Dove |
|---|---|
| Tab **Me** (dopo Macchinari): profilo, panoramica, medaglie, tutti i grafici; la Home resta veloce (medaglie a carosello `Medal.svelte`) | `web/src/pages/Me.svelte`, `Home.svelte`, `lib/medalinfo.ts` (medaglie a una data: «cosa hai guadagnato oggi») |
| Riepilogo: la tua settimana (giorni, obiettivo, settimane di fila) e medaglie salite | `Summary.svelte` |
| Assi veri su attività e volume settimanale | `Heatmap.svelte`, `WeekBars.svelte` |
| **Scheda generale** (kind `program`) che contiene i **giorni** (i vecchi `template`): `program` e `weekday` sul giorno, `useDays` sulla scheda, impostazione `activeProgram`; i giorni di prima finiscono in «La mia scheda» (id fisso `DEFAULT_PROGRAM`, una volta sola dopo la sync) | `common/docs.ts`, `server/backup.ts` (`programs`, `programID`, `weekday`), `lib/actions.ts`, `lib/derive.ts` (`nextDay`), `Workouts.svelte`, `ProgramEditor.svelte`, `TemplateEditor.svelte` |
| Icona del giorno: automatica dal nome (`common/template-icon.ts`) o scelta (muscoli, corpo, attrezzi, generali) | `TemplateIcon.svelte`, `IconPicker.svelte`, `solid-icons.ts` |
| Macchinario creato a mano di proposito (`custom`) non entra nel banner «fuori catalogo» | `Maxes.svelte`, `ExerciseEditor.svelte` |
| Lingua in Impostazioni (impostazione `language`, sincronizzata) | `i18n.ts`, `Settings.svelte`, `App.svelte` |
| Nuovo macchinario: prima il gruppo, poi la ricerca, poi a mano | `CatalogPicker.svelte` |
| Incremento del carico: − / + da 0,25 | `Stepper.svelte` (`compact`) |

Da sapere:
- **Licenze icone**: Lucide (ISC) in `Icon.svelte`, game-icons.net (**CC BY 3.0, serve citare**) in `solid-icons.ts`; i testi in `web/src/components/*LICENSE.txt` e riga nelle Impostazioni.
- **Ordine di rilascio**: prima il server (nuovo kind `program`, nuovi campi). Un iPhone vecchio salta il kind `program` (`SyncDocs.apply` → `skipped`, mai cancellato): letto nel codice, non provato. `KEEP_IF_ABSENT` protegge `program`, `weekday`, `icon`, `custom`.
- Nei testi italiani «scheda» = la generale, «giorno» = il vecchio piano; in inglese Program / Plan.
- Corretto un `XX` che compariva nel selettore dei gruppi (`FocusPicker.svelte`).
- Verifica: `npm test` 102; `svelte-check` 0; `check-i18n` ok; `vite build`; prova nel browser con dati finti.

---

# Figure dei muscoli e Home (18 ottobre 2026, ramo feature/figure-muscoli)

Stato da leggere per primo. Non è ancora su main né pubblicato.

| Pezzo | Dove |
|---|---|
| Disegno del corpo (fronte e schiena) con 16 muscoli, dai contorni MIT di react-muscle-highlighter | `scripts/anatomy-src/` (sorgente e LICENSE.txt, © My Muscle Contributors), generato in `common/anatomy.ts` e `Peak/Design/MuscleFigureShapes.swift` da `node scripts/gen-anatomy.ts` |
| Muscoli e gruppi, nomi IT/EN | `common/muscle-info.ts` (id mai rinominati) |
| Zoom sulla zona che lavora e scelta della vista (regola identica web e iPhone) | `common/muscles.ts` (`zoomFor`, `viewFor`), `AnatomyZoom` e `anatomyView` nel Swift |
| Muscoli principali delle 291 voci del catalogo + vista forzata | `scripts/catalog-muscles.txt` → `common/catalog.json` (campi `muscles`, `view`) con `node scripts/gen-catalog-muscles.ts` |
| Macchinari personalizzati: muscoli toccati sulla figura | documento `exercise` (`muscles`, `view`) in `common/docs.ts`, `KEEP_IF_ABSENT`, backup del server, `SyncDocs`/`BackupDTO` su iPhone |
| Figura nel web | `web/src/components/MuscleFigure.svelte`, `FigThumb.svelte`; posti: sessione, scheda, lista macchinari, dettaglio, riepilogo, editor |
| Figura nel iPhone | `Peak/Design/MuscleFigureView.swift` (`MuscleFigureView`, `ExerciseFigure`); posti: sessione, scheda, lista, dettaglio, riepilogo, editor |
| Home: medaglie come elenco con livelli e cosa manca; obiettivo e sezioni spiegati | `web/src/pages/Home.svelte`, `Peak/Features/Home/` |
| Tutorial: due passi nuovi (medaglie e obiettivo, figure) per chi l'ha già finito | setting `tourSeen` (default 1, sincronizzato); `Tour.svelte`, `TourOverlay.swift` |

Da sapere prima di toccare:
- **Ordine di rilascio**: prima il server (`web/dist` e `common/`), poi i client. Il server vecchio scarta `muscles`/`view`
  e il setting `tourSeen`; l'iPhone con AltStore 2.1 non li manda e `KEEP_IF_ABSENT` tiene quelli che ci sono.
- Il catalogo sul iPhone è una copia nell'app: `Catalog.swift` decodifica `muscles` e `view` con un default, così una copia vecchia non rompe il decode.
- Le figure non sono le foto dei macchinari (quei disegni sono del costruttore): sono i contorni MIT colorati per gruppo.

Verifica fatta (18 ottobre): `npm test` 101 verdi; `svelte-check` 0 errori e 0 avvisi; `check-i18n` ok; `vite build` ok;
`swiftc -typecheck` dell'app ok (i test XCTest non si caricano senza Xcode). Nel browser, con dati di prova: Home, lista
macchinari, dettaglio, riepilogo con fronte e retro.

Non fatto: `xcodebuild test` sul telefono (telefono offline, era stato approvato); l'editor del macchinario personalizzato
in modalità «tocco» non è stato provato nel browser; l'app Watch non è toccata.

---

# Peak 3.0 — stato al 15 ottobre 2026 (leggere per primo)

Il piano è `~/.claude/plans/semplifica-l-app-implementiamo-una-goofy-coral.md`. **Tutto è su `main`, niente è pubblicato**: web, server e iPhone
compilano e i test passano (`npm test`: 94; iPhone: `build-for-testing` per dispositivo, i test Swift non sono mai stati eseguiti).

| Pezzo | Dove |
|---|---|
| Tolti: formula 1RM (solo Epley), sottopagina Schede, promemoria backup, Catalogo/«La tua palestra», conteggio ripetizioni del Watch, codice di migrazione 2.0; `SettingsView.swift` diviso in un file per sottopagina | `Peak/Features/Settings/`, `common/domain.ts`, `PeakWatch/` |
| **Catalogo con id**: 291 esercizi, IT+EN+sinonimi, id slug stabili; ricerca, collegamento per nome, `catalog` sull'esercizio | `common/catalog.json` (fonte unica), `common/catalog.ts`, `server/catalog.ts`, `Peak/Model/Catalog.swift`, `web/src/components/CatalogPicker.svelte` |
| **Amici**: richieste per username, invito monouso (link + QR), lega (volume/allenamenti/settimane di fila), classifica per esercizio sull'1RM, profilo con schede copiabili, «nascondi agli amici» | `server/social.ts`, `server/api.ts` (`/api/amici`, `/api/inviti`, `/api/lega`, `/api/classifica`, `/api/novita`), `web/src/pages/Friends.svelte`, `Peak/Features/Friends/` |
| **Nuova navigazione** a 6 schede: Home, Allenamento, Progressi, Macchinari, Amici, Impostazioni | `web/src/App.svelte`, `Peak/Features/Root/RootView.swift` |
| **Home**: montagna con frasi a rotazione, barra dell'obiettivo, attività, grafici, medaglie | `web/src/pages/Home.svelte`, `Peak/Features/Home/`, `common/medals.ts` |
| **Obiettivo settimanale** scelto all'apertura (schermata a tutto schermo) e **tutorial** sulle schermate vere; impostazioni `weeklyGoal` e `tutorialDone` sincronizzate | `GoalPicker.svelte`, `Tour.svelte`, `GoalPickerView.swift`, `TourOverlay.swift` |
| **Icone per attrezzo** (16 ott): 26 pittogrammi nuovi (48 in tutto), ogni voce del catalogo riassegnata a mano; un collegato mostra sempre l'attrezzo del catalogo, un personalizzato nessuna immagine finché non la scegli; i personalizzati di prima tengono l'icona dedotta dal nome (fissata una volta: sul server `migrateCatalog` con `user_version` 2, sull'iPhone `freezeGlyphsOnce`, e all'import di backup vecchi). Calcolatore dischi anche per hip thrust, EZ, trap bar e ogni «plate loaded» | `common/glyph.ts` (fonte), `scripts/gen-glyphs.ts` → `Peak/Design/MachineGlyphShapes.swift` (test di parità in `npm test`) |

Da sapere:
- Un client vecchio (AltStore 2.1) che non manda `catalog`/`hidden` non li cancella (`KEEP_IF_ABSENT`, `server/store.ts`); le impostazioni tolte restano accettate e ignorate (`LEGACY_SETTINGS`).
- Il server collega al catalogo gli esercizi senza id all'avvio (`migrateCatalog`), l'iPhone una volta (`linkCatalogOnce`).
- **Mai emoji nell'interfaccia**: SVG sul web, SF Symbols su iPhone.
- Versione 3.0 (build 9) in `project.yml` e `web/package.json`: tenerle uguali.

Da fare, con il via libera dell'utente: **riavviare il server prima di installare l'iPhone** (il server vecchio rifiuta i nomi dei pittogrammi nuovi); provare l'app sul telefono vero **dopo un backup** (migrazione SwiftData con i nuovi campi, sopra quella delle schede mai provata), installare (e rimettere il Watch senza conteggio), deploy/riavvio su Pelican, tag `v3.0` e AltStore.
Non fatto di proposito: link `peak://invito/` nell'app (il link d'invito è https e passa dal web), pagina `/portale` invariata.

---

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
| **Schede di allenamento** (nome, esercizi con serie / rip min–max / peso; si scelgono all'avvio, si cambiano in sessione, «Salva come scheda», domanda di aggiornamento a fine allenamento; Watch e Live Activity mostrano obiettivo e prossimo esercizio) | documento `template` + `plan`/`template` nel `workout` (`common/docs.ts`, `common/plan.ts`), backup (`server/backup.ts`), web (`TemplateEditor.svelte`, `Session.svelte`), iPhone (`WorkoutTemplate.swift`, `TemplateEditorView.swift`, `Plan`) | `npm test` (anche `common/plan.test.ts`), prova nel browser, `PeakTests/TemplateTests` (78 test sul telefono, tutti verdi) |
| Impostazioni a sottopagine (Generale, Allenamento, Schede, Account, Backup, Catalogo; l'iPhone anche Watch e Salute) e tre interruttori sincronizzati: serie / rip / peso nelle schede | `web/src/pages/Settings.svelte`, `SettingsView.swift`, `SETTING_DEFAULTS` | idem |
| Benvenuto a sei scene (Schede terza) sul web e in SwiftUI (con l'icona dell'app); video di lancio nuovo | `web/public/benvenuto.*`, `WelcomeView.swift`, `brag-output-peak/` (non tracciata) | Chrome headless a 390 px; `npx hyperframes check` e render |

Bundle id, nome dello store SwiftData, chiavi UserDefaults e **modulo Swift (`PRODUCT_MODULE_NAME: Massimali`)** non sono
cambiati di proposito: i dati degli utenti non si toccano.

## Da fare

1. **Sito online dal 14 ott** (peak.yrb4g.com): server Pelican, proxy NPM, blueprint 210 + `svc-peak`, segreti, deploy di yG e cron notturno
   (`peak-sincronizza-accessi.sh --locale`, 03:30 sull'Oracle) sono a posto. Mancano due cose che solo l'utente può fare: **Restart del server
   in Pelican** (porta `/sw.js` con `no-store`: Cloudflare lo teneva 4 ore; poi `curl -sI https://peak.yrb4g.com/sw.js` non deve dire `HIT`)
   e **un primo login** con un utente in `svc-peak`. Dopo il login: `/account/peak` su yG deve mostrare i dati, e si lancia una volta a mano
   `peak-sincronizza-accessi.sh`. Il `sub` è lo stesso in yG e Peak (entrambi i provider usano `hashed_user_id`); l'email è solo il ripiego.
2. **iPhone** (tutto su `main`): compila e i 78 test di `PeakTests` passano sul telefono (15 ott; un test vecchio, `testEditingASetRechecksTheLaterOnes`, leggeva `recordList` prima del salvataggio ed è stato sistemato). Il 15 ott l'app con le schede è stata installata sul telefono vero con `SENZA_WATCH=1 scripts/install_device.sh` (bundle id `…massimali.HKR6AKCLPA`): **la migrazione SwiftData sui dati veri e le schede a mano le deve ancora provare l'utente**. **App Watch**: il tentativo di installarla è scaduto (tunnel) dopo che lo script aveva già tolto la vecchia: serve rilanciare `scripts/install_device.sh` con l'orologio sbloccato. Sul telefono c'è anche l'app `…massimali` (senza suffisso) messa dall'esecuzione dei test: è una copia a parte, con dati suoi. Sotto, la descrizione della parte di sincronizzazione scritta prima.
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
