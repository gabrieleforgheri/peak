package com.gabrieleforghieri.peak.ui.nav

import kotlinx.serialization.Serializable

/**
 * Le rotte che si raggiungono da un'altra area, con i loro argomenti (tipati: Navigation Compose 2.9 + kotlinx.serialization).
 * Proprietario: la base della fase 3. **Dopo la base nessuna area modifica questo file** (né `PeakNav.kt`, né `PeakRoot.kt`).
 *
 * Inventario dell'iPhone, e dove sta qui (vedi anche `android/AREE.md`):
 *
 * | iPhone | Android | Chi la apre |
 * |---|---|---|
 * | `TabView` (`AppNav.Tab`) | le quattro radici [WorkoutsHome], [MachinesHome], [MeHome], [FriendsHome] | la barra in basso, `PeakNav.goTab` |
 * | `NavigationLink(value: Workout)` e l'avvio di un giorno | [WorkoutSession] | allenamenti (lista, avvio di un giorno), `PeakNav.startTemplate` |
 * | `ExerciseDetailView` (spinta da Macchinari, foglio da dentro la sessione) | [ExerciseDetail] | macchinari (lista), allenamenti (sessione) |
 * | `NavigationLink(value: FriendPerson)` | [FriendProfile] | amici (lista) |
 * | `fullScreenCover { SettingsView }` | [Settings] (la barra in basso si nasconde) | me (ingranaggio), `PeakNav.openSettings` |
 *
 * Tutto il resto dell'iPhone è un **foglio** (`.sheet`, `.fullScreenCover` locale) e qui è un composable che un'area apre con il
 * suo stato, dentro `PeakSheet` (`design/PeakSheet.kt`): i fogli usati da più aree hanno una firma stabile e un proprietario
 * (`ui/<area>/…Sheet.kt`, elenco in `AREE.md`). Le rotte che servono solo dentro un'area le registra l'area stessa nella sua
 * `<Area>Destinations` con i suoi tipi `@Serializable`: non passano di qui.
 */
object Routes {
    @Serializable data object WorkoutsHome
    @Serializable data object MachinesHome
    @Serializable data object MeHome
    @Serializable data object FriendsHome

    /** La sessione di un allenamento (uuid), in corso o già chiuso. */
    @Serializable data class WorkoutSession(val workoutId: String)

    /** Il dettaglio di un macchinario (uuid): grafico, massimali, serie. */
    @Serializable data class ExerciseDetail(val exerciseId: String)

    /** Il profilo di un amico (`FriendPerson`: l'`id` e il nome si rileggono dal server con lo `username`). */
    @Serializable data class FriendProfile(val username: String)

    /**
     * Le impostazioni, a tutto schermo. `category` è un `SettingsCategory.name` (general, training, account, health, backup, data) e apre
     * direttamente quella sottopagina; le sottopagine le gestisce l'area impostazioni, dentro questa rotta.
     */
    @Serializable data class Settings(val category: String? = null)
}
