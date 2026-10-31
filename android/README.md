# Peak per Android (app nativa)

Kotlin + Jetpack Compose, dati locali con Room, `minSdk 26`, `targetSdk`/`compileSdk 36`. Package `com.gabrieleforghieri.peak`, `versionCode 2`
(sostituisce la TWA di Bubblewrap: stesso package e stessa chiave). Il piano e le fasi sono in `../PIANO-ANDROID.md`; questa è la **fase 1 (fondamenta)**.

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
| `data/` | Room: entità (`Entities.kt`), DAO, `PeakDatabase`, `RoomRecordStorage`, `SettingsRepository` | modelli SwiftData + `common/docs.ts` |
| `design/` | `PeakTheme` (colori, forme, font), `GlassCard`, `Components`, `MachineGlyphView`, `MuscleFigureView`, `PlateBarView`, `Celebration`, `Motion`, `PeakIcons` | `Peak/Design/` |
| `util/` | `Strings` (`tr("testo italiano")`), `SoundFX`, `Haptics` | `Utils/Lang`, `SoundFX`, `Haptics` |
| `ui/` | `PeakRoot`: le quattro schede (Allenamento, Macchinari, Me, Amici) con un segnaposto ciascuna | `Features/Root/RootView.swift` |

Convenzioni dei dati (servono alla fase 2): uuid in maiuscolo; date in millisecondi dall'epoca (nei documenti e nei backup diventano ISO a secondi
interi con `PeakDates`); pesi sempre in kg; righe di scheda e di piano come testo JSON; le impostazioni in una tabella chiave → JSON (`SettingKeys`), quelle `device.*` restano del telefono.
I testi: la chiave è l'italiano, come `.tr` su iPhone (`tr("Allenamento")`, `tr("%lld allenamenti", 3)`); l'inglese viene da `Localizable.strings` + `en.json`.

## Test

`app/src/test/` ha 133 prove JUnit sul JVM: tradotte da `PeakTests/` e da `common/*.test.ts`, più i vettori condivisi `common/fixtures/records.json` e il catalogo vero.
`RecordService` si prova con un database in memoria (`InMemoryStorage`); `RoomRecordStorage` e il database non hanno prove (servirebbe Robolectric o un test strumentato).

## Cosa manca

- Tutte le schermate vere (fase 3), l'account, la sincronizzazione e i backup (fase 2), la notifica dell'allenamento, Health Connect e i widget (fase 4), la build firmata (fase 5).
- Foto dei macchinari (il campo `photo` c'è, i byte no), `Celebrated` (le feste già fatte), lingua scelta nell'app (ora segue il telefono).
- Il vetro smerigliato di iOS non c'è: `GlassCard` usa un velo bianco al 5,5 % sullo sfondo scuro. Il font «tondo» di iOS non c'è: si usa quello di sistema con le cifre tabellari.
