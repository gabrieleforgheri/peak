# Peak per Android, nativa

Stato: piano, niente codice ancora (31 ottobre 2026).

## Cosa si costruisce

Un'app Android nativa che fa **tutto quello che fa l'app iPhone** (`Peak/`, circa 19.000 righe di SwiftUI), con lo stesso aspetto e le stesse regole.

- **Kotlin + Jetpack Compose**, Material 3 solo come base: colori, forme e componenti si rifanno a `Peak/Design/`, non al look Google.
- **Dati come su iPhone**: tutto sul telefono (Room), l'app funziona senza account e offline. La sincronizzazione con peak.yrb4g.com è facoltativa e si attiva con l'account yG.
- **Extra di sistema**: notifica fissa durante l'allenamento con il timer di recupero (al posto della Live Activity), Health Connect (scrive gli allenamenti e legge il peso, come `HealthService`), widget nella Home (Glance, come `PeakWidgets/`).
- **Fuori da questa versione**: l'app per orologio (Wear OS).
- **Prove**: test JVM sul Mac e l'emulatore Android (via libera dell'utente il 31 ottobre). Il simulatore iOS resta vietato (vedi `HANDOFF.md`).

L'app sostituisce la TWA di `android/` (commit 4823e0d): stesso package `com.gabrieleforghieri.peak` e stessa chiave (`~/.config/peak-android/`), quindi si installa sopra quella già presente sul telefono.

## Cosa NON cambia

- **Server**: nessuna modifica. Il login è lo stesso dell'iPhone (`/auth/login?app=<challenge>&nome=` → `peak://login?code=` → `/auth/app/token` con il verifier PKCE). Su Android il login si apre in una Custom Tab e torna con un intent filter `peak://login`.
- **Sincronizzazione**: lo stesso protocollo di `Peak/Store/SyncService.swift` e `common/docs.ts`. Tipi di documento `setting, exercise, record, workout, set, bodyweight, template, program`, un hash per documento, vince la modifica più recente (`m` in ms), date ai secondi interi, i record nati dagli allenamenti non viaggiano.
- **Backup**: stesso JSON di `BackupDTO.swift`, così un backup passa da iPhone ad Android e viceversa.
- **Catalogo e regole**: il catalogo viene da `common/catalog.json`. Le regole di dominio (`Peak/Model/`) si traducono in Kotlin una per una, con i loro test (`PeakTests/`, `common/*.test.ts`, `common/fixtures/`).

## Fasi

Le fasi 1 e 2 vanno in ordine. Dopo, le fasi 3 e 4 si dividono in sessioni parallele, una per area, perché toccano file diversi.

### 1. Fondamenta

- Progetto Gradle nuovo in `android/` (via la TWA), con il version catalog, `minSdk 26` e `targetSdk 36`.
- Tema e componenti di base tradotti da `Peak/Design/`: `Theme`, `GlassCard`, `Components`, `MachineGlyph`, `MuscleFigure`, `PlateBarView`, `Celebration`, `Motion`.
- Modello Room che rispecchia i modelli SwiftData di `Peak/Model/`.
- Regole di dominio con i test JUnit: `OneRepMax`, `RecordRules` e `RecordService` (l'unico percorso di scrittura delle serie), `Plates`, `Warmup`, `Progression`, `Medals`, `MaxRanking`, `Limbs`, `Programs`, `Stats`, `Catalog`.
- Testi: l'italiano è la lingua di base (`values/`), l'inglese viene da `Peak/en.lproj/Localizable.strings` (`values-en/`). Niente emoji, solo icone vettoriali.
- Suoni (`Peak/Sounds/`) e vibrazioni (`Haptics`).
- Guscio con le schede e la navigazione (`Features/Root/RootView.swift`).

**Fatto quando** `./gradlew test assembleDebug` passa e l'app parte nell'emulatore mostrando le schede vuote.

### 2. Account, sincronizzazione, backup

- `PeakAPI`, `PeakLogin` (Custom Tab più `peak://login`) e il token salvato con Android Keystore.
- `SyncService` e `SyncDocs`, con la copia di sicurezza prima del collegamento come su iPhone.
- `BackupService`: esportazione e importazione compatibili con iPhone.

**Fatto quando** un backup dell'iPhone si importa senza differenze (test di andata e ritorno) e la sincronizzazione con un server locale (`node server/index.ts`) è provata.

### 3. Schermate (in parallelo, una sessione per area)

| Area | Da `Peak/Features/` |
|---|---|
| Allenamenti | `Workouts/`: lista, allenamento in corso, serie, scelta esercizio e focus, editor di giorni e schede, riepilogo, dettaglio muscoli |
| Massimali | `Maxes/`: lista, dettaglio, editor del macchinario, catalogo, foto |
| Me e progressi | `Progress/`: peso corporeo, progressione, medaglie, sezioni di Me |
| Amici | `Friends/`: lista, profilo, invito con QR (`PeakSocial`, `SocialStore`) |
| Avvio | `Root/`: benvenuto, tutorial, idee, obiettivo settimanale, collegamento |
| Impostazioni | `Settings/`: tutte le sottopagine tranne Watch |

**Fatto quando** ogni schermata ha tutti gli stati dell'iPhone (vuoto, caricamento, errore) ed è stata provata nell'emulatore.

### 4. Sistema

- Servizio in primo piano con la notifica dell'allenamento e il timer di recupero (`RestTimer`, `WorkoutActivity`).
- Health Connect (`HealthService`).
- Widget Glance (`PeakWidgets/`).

### 5. Uscita

- `scripts/build_android.sh` aggiornato per firmare l'app nativa; `versionCode` 2, così si installa sopra la TWA.
- README in `android/`, e `HANDOFF.md` aggiornato.
- L'eccezione Cloudflare per `assetlinks.json` non serve più: un'app nativa non ne ha bisogno.

## Regole per chi ci lavora

- Prima di tradurre una schermata, leggere il file Swift corrispondente e il suo README: stesso comportamento, stessi testi.
- Le regole di dominio si traducono in Kotlin, mai reinventate: se Swift e `common/` non sono d'accordo, chiedere.
- Commit in italiano, niente push senza richiesta.
