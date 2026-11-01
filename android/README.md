# Peak per Android (app nativa)

Kotlin + Jetpack Compose, dati locali con Room, `minSdk 26`, `targetSdk`/`compileSdk 36`. Package `com.gabrieleforghieri.peak`, `versionCode 2`
(sostituisce la TWA di Bubblewrap: stesso package e stessa chiave). Il piano e le fasi sono in `../PIANO-ANDROID.md`; la **fase 1 (fondamenta)**, la **fase 2 (account, sincronizzazione, backup)** e la **base della fase 3** (navigazione per aree, componenti, dati, prove) sono fatte. Come lavorano le sei aree della fase 3: `AREE.md`.

## Come si compila

Serve il JDK 17 e l'Android SDK (platform 36). Sul Mac di sviluppo:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
cd android && ./gradlew test assembleDebug      # test JVM + app di debug in app/build/outputs/apk/debug/
```

`android/local.properties` (ignorato da git) contiene `sdk.dir=…`. Non c'è bisogno di Android Studio. La chiave di firma sta fuori dal repo
(`~/.config/peak-android/`); la build firmata (`scripts/build_android.sh`) si aggiorna nella fase 5.

I file che derivano da dati condivisi con web e iPhone si rigenerano con `node android/scripts/gen-assets.ts` (dalla radice del repo): pittogrammi
(`domain/MachineGlyph.kt`), muscoli e contorni (`domain/MuscleId.kt`, `design/AnatomyShapes.kt`), testi (`res/values*/strings.xml`) e suoni (`res/raw/fx_*.m4a`).
Il catalogo (`common/catalog.json`) lo copia Gradle negli asset a ogni build (`copyCatalog`).

## Struttura (`app/src/main/java/com/gabrieleforghieri/peak/`)

| Cartella | Cosa c'è | Da cosa deriva |
|---|---|---|
| `domain/` | Regole in Kotlin puro, senza Android: `OneRepMax`, `MaxRanking`, `RecordRules`, `RecordService` (l'unico percorso di scrittura delle serie, sopra l'interfaccia `RecordStorage`), `Plates`, `Warmup`/`ArmSwitch`, `Progression`, `Medals`, `Limbs`, `Programs`/`TemplateIcon`, `Plan`, `Onboarding`, `Stats`, `Catalog`, `Fmt`, `Settings`, `PeakDates` | `Peak/Model`, `Peak/Store/RecordService`, `Peak/Utils`, `common/*.ts` |
| `data/` | Room: entità (`Entities.kt`), DAO (`Daos.kt`, e un DAO vuoto per area in `dao/`), `PeakDatabase`, `PeakRepository` (un `Flow` per ogni `@Query` dell'iPhone), `ExerciseExt` (le proprietà derivate di `Exercise.swift`), `RoomRecordStorage`, `SettingsRepository` | modelli SwiftData + `common/docs.ts` |
| `design/` | `PeakTheme` (colori, forme, font), `GlassCard`, `Components`, `MachineGlyphView`, `MuscleFigureView`, `PlateBarView`, `Celebration`, `Motion`, `PeakIcons`; fase 3: `PeakSheet` (l'unico foglio in basso), `ConfirmSheet`, `SideSegment`, `WarningBanner`, `ExerciseThumbnail`, `ExerciseCardBackground` (+ `ExercisePhoto`), `SearchField`, `ScreenPlaceholder` | `Peak/Design/` |
| `util/` | `Strings` (`tr("testo italiano")`), `SoundFX`, `Haptics` | `Utils/Lang`, `SoundFX`, `Haptics` |
| `ui/` | `PeakRoot`: barra delle schede e navigazione, che chiama le sei `<Area>Destinations`; `nav/` (rotte, contratto `PeakNav`, ambiente); una cartella per area (`workouts/`, `machines/`, `me/`, `friends/`, `start/`, `settings/`) con le schermate e i fogli condivisi (account, sincronizzazione e backup stanno in `settings/`) | `Features/` |
| `sync/` | Fase 2, Kotlin senza Android: `PeakApi` (+ `HttpPeakApi`, errori), `PeakLogin` (PKCE), `SyncDocs` (righe ⇄ documenti, hash), `SyncEngine` (push/pull, conflitti, guardie), `ConnectFlow` (scelte del primo collegamento), `BackupService`/`BackupCodec` (JSON dell'iPhone), `SyncService` (quando sincronizzare), `Snapshot`/`Changes` | `PeakAPI`, `PeakLogin`, `SyncService`, `SyncDocs`, `ConnectFlow`, `BackupService`, `BackupDTO` |
| `data/` (fase 2) | `RoomSyncStorage`, `SecretBox` + `KeystoreCredentialStore` (token cifrato con una chiave dell'Android Keystore), `ImageStore` (foto: byte in `files/photos/<sha256>.jpg`, lo sha nel database), `MediaStoreBackupFiles` (copie in Download/Peak) | `PeakKeychain`, `ImageStore`, `BackupService` |

Convenzioni dei dati (servono alla fase 2): uuid in maiuscolo; date in millisecondi dall'epoca (nei documenti e nei backup diventano ISO a secondi
interi con `PeakDates`); pesi sempre in kg; righe di scheda e di piano come testo JSON; le impostazioni in una tabella chiave → JSON (`SettingKeys`), quelle `device.*` restano del telefono.
I testi: la chiave è l'italiano, come `.tr` su iPhone (`tr("Allenamento")`, `tr("%lld allenamenti", 3)`); l'inglese viene da `Localizable.strings` + `en.json`.

## Account, sincronizzazione, backup (fase 2)

Il server è uno solo, `https://peak.yrb4g.com` (`BuildConfig.PEAK_BASE_URL`); **solo nei build di debug** si cambia con `-PpeakBaseUrl=…` (dall'emulatore il Mac è `10.0.2.2`, e il debug ammette HTTP in chiaro). Il login è quello dell'iPhone: `/auth/login?app=<sfida PKCE>&nome=<dispositivo>` in una Custom Tab → `peak://login?code=` (intent filter su `MainActivity`, `singleTask`) → `/auth/app/token`. Il token sta cifrato (AES-GCM, chiave nel Keystore) in `SharedPreferences`; lo stato della sincronizzazione (hash, cursore) in `files/peak-sync.json`.

**Prova contro il server vero in locale** (mai la produzione): con l'emulatore acceso (`emulator -avd peak_test -no-window`),

```bash
android/scripts/sync-e2e.sh                          # dalla radice del repo
SCREENSHOT=android/docs/fase2.png android/scripts/sync-e2e.sh   # in più, lo screenshot della schermata collegata
```

Avvia `PORT=3416 DATA_DIR=<cartella temporanea> node server/index.ts`, crea un token di dispositivo con `android/scripts/dev-device.ts` (il login vero con yG non si automatizza), compila il debug contro `10.0.2.2:3416`, e pilota l'app con i comandi di debug (`src/debug/…/DebugCommands.kt`, `adb shell am broadcast`): spinge dati e controlla che il server li veda, li riprende da un «altro dispositivo», prova un conflitto nei due versi, una cancellazione, il backup nei due sensi e il login (URL della Custom Tab, ritorno `peak://login?code=`). Il file di backup di prova (`app/src/test/resources/backup-server.json`) si rigenera con `node android/scripts/gen-backup-fixture.ts`.

## Test

`app/src/test/` ha le prove JUnit sul JVM: tradotte da `PeakTests/` e da `common/*.test.ts`, più i vettori condivisi `common/fixtures/records.json` e il catalogo vero. Quelle di `sync/` provano documenti e hash, regole di vittoria e conflitti con due telefoni finti su un server finto (`SyncTestSupport.kt`), il backup in andata e ritorno su un file scritto dal vero `server/backup.ts`, la rete contro un piccolo server HTTP locale, e le scelte del primo collegamento.
`RecordService` si prova con un database in memoria (`InMemoryStorage`).

**Room e schermate sul JVM, senza emulatore** (Robolectric + Roborazzi, fase 3):

- `data/RoomDaoTest.kt`: Room in memoria sotto Robolectric; esempio per provare le query di un DAO.
- `ui/ScreenshotTest.kt` + `ScreenshotSupport.kt`: `compose.screenshot("nome") { …schermata… }` disegna la schermata col tema e l'ambiente dell'app e salva il PNG in `android/docs/screens/nome.png`.

```bash
cd android
./gradlew recordRoborazziDebug     # (ri)registra gli screenshot in docs/screens/
./gradlew verifyRoborazziDebug     # controlla che gli screenshot non siano cambiati (fallisce se sì)
./gradlew test                     # tutte le prove (gli screenshot non si rigenerano)
```

La prima volta Robolectric scarica nella cache Maven (`~/.m2`) il suo android-all di Android 15 (circa 200 MB); le prove usano `sdk=35` (`src/test/resources/robolectric.properties`) perché è quello che Robolectric 4.16 conosce. I fogli (`PeakSheet`) stanno in una finestra a parte e non compaiono negli screenshot.

## Cosa manca

- Tutte le schermate vere (fase 3: le sei aree hanno la rotta, un segnaposto e i fogli condivisi in versione semplice; le impostazioni mostrano ancora la schermata provvisoria dell'account), la notifica dell'allenamento, Health Connect e i widget (fase 4), la build firmata (fase 5).
- Scelta e ritaglio delle foto (`ImageStore.prepare` c'è, nessuna schermata la usa), backup automatico (`backup-latest.json`), `Celebrated` (le feste già fatte), lingua scelta nell'app (ora segue il telefono).
- Il vetro smerigliato di iOS non c'è: `GlassCard` usa un velo bianco al 5,5 % sullo sfondo scuro. Il font «tondo» di iOS non c'è: si usa quello di sistema con le cifre tabellari.
