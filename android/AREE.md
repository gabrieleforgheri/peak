# Fase 3: le sei aree e le loro regole

Sei sessioni lavorano in parallelo, una per area, ognuna **solo sui propri file**. Il terreno comune (navigazione, componenti, dati, prove) l'ha preparato la base della fase 3; da qui in poi le aree non lo toccano. Piano: `../PIANO-ANDROID.md`; com'è fatta l'app: `README.md`.

Ogni area traduce le schermate di `Peak/Features/<cartella>/` in Compose: stesso comportamento, stessi testi, stessi stati (vuoto, caricamento, errore). Prima di scrivere una schermata, leggere il file Swift. Le regole di dominio non si reinventano: se Swift e `common/` non concordano, si chiede.

| Area | Da `Peak/Features/` | Cartella Android | DAO |
|---|---|---|---|
| **workouts** | `Workouts/` | `ui/workouts/` | `data/dao/WorkoutsDao.kt` |
| **machines** | `Maxes/` | `ui/machines/` | `data/dao/MachinesDao.kt` |
| **me** | `Progress/` | `ui/me/` | `data/dao/MeDao.kt` |
| **friends** | `Friends/` | `ui/friends/` | `data/dao/FriendsDao.kt` |
| **start** | `Root/` tranne `RootView` (benvenuto, tutorial, idee, obiettivo, collegamento) | `ui/start/` | `data/dao/StartDao.kt` |
| **settings** | `Settings/` senza Watch; prende il posto di `ui/AccountScreen.kt` | `ui/settings/` | `data/dao/SettingsDao.kt` |

## I file di un'area

Sono tuoi: tutto `ui/<area>/` (compresi `<Area>Destinations.kt` e i fogli condivisi che ci stanno dentro), il tuo DAO, le tue prove in `app/src/test/java/…/ui/<area>/` e `…/data/` (screenshot `docs/screens/<area>-*.png`), i tuoi testi in `texts/<area>.json`, e i file che crei nuovi dentro queste cartelle. Icone solo tue: `ui/<area>/<Area>Icons.kt` con `PeakIcons.icon(…)`. Se serve un `ViewModel`, sta in `ui/<area>/` (c'è `lifecycle-viewmodel-compose`). `ui/settings/` può cancellare `ui/AccountScreen.kt` quando la sostituisce.

## I file comuni

**Vietati** (si usano, non si modificano; `git diff` su questi deve essere vuoto):
`ui/PeakRoot.kt`, tutto `ui/nav/`, `data/Daos.kt`, `data/PeakDatabase.kt`, `data/Entities.kt` (lo schema non cambia nella fase 3), `gradle/libs.versions.toml`, `build.gradle.kts` e `app/build.gradle.kts`, `res/values*/strings.xml` (si generano), `AndroidManifest.xml`.

**Comuni** (si toccano solo se indispensabile, e si dichiara): `design/` (componenti e tema), `domain/`, `sync/`, `util/`, `data/PeakRepository.kt`, `data/ExerciseExt.kt`, `PeakApplication.kt`, `MainActivity.kt`.

Quando una modifica a un file comune è **indispensabile**: falla piccola, in un **commit a parte** (titolo che comincia con `COMUNE:`), e nel resoconto metti una sezione «Modifiche a file comuni» con file, cosa cambia, perché non si poteva fare nella tua cartella, e il diff. Chi unisce le sei aree decide. Il caso tipico è una dipendenza nuova o un permesso nel manifest: dichiaralo, non farlo.

## La navigazione

- Le rotte raggiungibili da un'altra area sono in `ui/nav/Routes.kt` (le quattro radici, `WorkoutSession`, `ExerciseDetail`, `FriendProfile`, `Settings`); si naviga col contratto `PeakNav` (`LocalPeakNav.current`: `goTab`, `openWorkout`, `openExercise`, `openFriend`, `openSettings`, `startTemplate`, `resumeActiveWorkout`, `requests` = `AppNav.swift`). Non si conoscono le rotte di un'altra area.
- Ogni area registra le sue schermate nella sua `NavGraphBuilder.<area>Destinations(nav)`; `PeakRoot` chiama solo quelle. Una rotta che serve **solo dentro** l'area si registra lì con un tipo `@Serializable` tuo.
- Tutto ciò che su iPhone è un **foglio** (`.sheet`, `.fullScreenCover` locale) è un composable aperto con lo stato locale dentro `PeakSheet` (`design/PeakSheet.kt`: l'unico componente per i fogli, mai `ModalBottomSheet` diretto). Conferme: `ConfirmSheet`.
- Gli agganci sopra tutto sono funzioni vuote da riempire: start → `WelcomeOverlay`, `TutorialOverlay`, `GoalOverlay` (`ui/start/StartOverlays.kt`); workouts → `PendingSummaryOverlay`. Il tutorial vede le altre aree attraverso `Modifier.tourTarget("id")` e `TourSheet(…)` (`ui/start/TourState.kt`): workouts, machines e friends li mettono dove li mette l'iPhone.
- La barra delle schede si nasconde solo su `Settings`. Lo spazio della barra lo dà `PeakRoot`; una schermata senza barra (le impostazioni) si prende da sé `navigationBarsPadding()`.

## Firme condivise e proprietari

La firma non cambia mai; solo il proprietario riscrive il corpo. Ogni file ha il commento «Firma condivisa» con il proprietario.

| Firma | File | Proprietario | La usano |
|---|---|---|---|
| `CatalogPickerSheet(onPick, onDismiss, title, allowsCustom)` e `CatalogPickerContent` | `ui/machines/CatalogPickerSheet.kt` | machines | machines, start, friends |
| `ExerciseEditorSheet(exerciseId?, onDismiss)` | `ui/machines/ExerciseEditorSheet.kt` | machines | machines, workouts |
| `ExercisePickerSheet(onSelect, onDismiss, focus)` | `ui/workouts/ExercisePickerSheet.kt` | workouts | workouts |
| `MuscleDetailSheet(exercise, onDismiss)` e `MuscleChipGrid` | `ui/workouts/MuscleDetailSheet.kt` | workouts | workouts, machines |
| `WorkoutSummarySheet(workoutId, onDone)` | `ui/workouts/WorkoutSummarySheet.kt` | workouts | workouts |
| `ConnectSheet(onDismiss)` | `ui/start/ConnectSheet.kt` | start | machines, friends, settings |
| `GoalPickerSheet(current, suggested, onClose)` | `ui/start/GoalPickerSheet.kt` | start | workouts, start |
| `TourState`, `TourTargets`, `Modifier.tourTarget`, `TourSheet` | `ui/start/TourState.kt` | start | workouts, machines, friends |

Queste prime versioni sono semplici ma funzionano (le ultime quattro sono segnaposto): il proprietario le porta alla schermata dell'iPhone.

## I dati

- Lettura: `LocalRepository.current` (`data/PeakRepository.kt`) ha un `Flow` per ogni `@Query` dell'iPhone: `exercises`, `workouts`, `activeWorkout`, `setsOfWorkout`, `sets`, `setsOfExercise`, `recentSets`, `records`, `recordsOfExercise`, `templates`, `programs`, `bodyWeights`. Le proprietà derivate di `Exercise.swift` (nome mostrato, ricerca, pittogramma, muscoli) sono in `data/ExerciseExt.kt`.
- Scrittura: le serie e i massimali **solo** dal `RecordService` (`LocalApp.current.records`); le impostazioni con `rememberUpdateSettings()` (leggile da `LocalAppSettings.current`); il resto con i DAO.
- Query tue: nel tuo DAO (`PeakDatabase.areaWorkouts()`, `areaMachines()`, `areaMe()`, `areaFriends()`, `areaStart()`, `areaSettings()`), mai in `Daos.kt`. Lo schema non cambia: niente entità nuove, niente migrazioni.
- Foto: `ExerciseThumbnail` e `ExerciseCardBackground` le leggono da sole; per salvarne una, `ImageStore` (`LocalApp.current.photos`).

## I testi

Si scrive il testo italiano: `tr("Nuovo macchinario")`, `tr("%lld allenamenti", 3)`, `Text(tr(…))`. I testi dell'iPhone ci sono già (le chiavi sono le stesse). Un testo che su iPhone non c'è si scrive uguale; senza traduzione resta in italiano. Per l'inglese, in `android/texts/<area>.json` metti `{"italiano": "english"}` (un file per area, mai lo stesso: niente conflitti). **`strings.xml` non si tocca**: chi unisce le aree lancia dalla radice `node android/scripts/gen-assets.ts`, che li rigenera da iPhone, web e dai `texts/*.json`.

## Le prove

Niente emulatore, niente simulatore. Test sul JVM: JUnit, Robolectric per Room e per le schermate, Roborazzi per gli screenshot (`ui/ScreenshotTest.kt` e `ui/ScreenshotSupport.kt` sono l'esempio; `data/RoomDaoTest.kt` per i DAO). Per ogni schermata che traduci: uno screenshot di ogni stato (vuoto, con dati, errore) in `docs/screens/<area>-<schermata>-<stato>.png`, e lo guardi. I fogli (`PeakSheet`) stanno in una finestra a parte e non entrano nello screenshot: si prova il loro contenuto. Comandi in `README.md` (`recordRoborazziDebug`, `verifyRoborazziDebug`, `test`).

Prima di consegnare: `./gradlew test assembleDebug` verde dalla cartella `android/`; nessuna emoji nell'interfaccia (icone vettoriali, `PeakIcons`); commit piccoli in italiano, nessun push.

## Il resoconto

In breve: branch e commit; le schermate fatte e quelle no; le firme condivise che hai riscritto; **«Modifiche a file comuni»** (vedi sopra, anche se vuota); i testi nuovi; cosa hai lasciato fuori e perché.
