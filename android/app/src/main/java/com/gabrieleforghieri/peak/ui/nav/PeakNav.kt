package com.gabrieleforghieri.peak.ui.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Le schede dell'app, nello stesso ordine di `Features/Root/RootView.swift` (e di `AppNav.Tab`): Allenamento (si apre lì),
 * Macchinari, Me, Amici. Le impostazioni non sono una scheda: si aprono da dentro.
 */
enum class Tab(val title: String, val home: Any) {
    workouts("Allenamento", Routes.WorkoutsHome),
    machines("Macchinari", Routes.MachinesHome),
    me("Me", Routes.MeHome),
    friends("Amici", Routes.FriendsHome),
}

/**
 * Le richieste che passano da un'area all'altra senza essere una schermata (`AppNav.swift`: `templateToStart`, `resumeActive`,
 * `visibleSession`, `pendingSummary`). Ogni valore è uno stato osservabile: chi lo consuma lo rimette a vuoto.
 */
class NavRequests {
    /** Un giorno (uuid) da avviare appena si apre la scheda Allenamento: lo consuma la lista allenamenti. */
    val templateToStart = MutableStateFlow<String?>(null)

    /** Aprire la sessione in corso: la consuma la lista allenamenti. */
    val resumeActive = MutableStateFlow(false)

    /** L'allenamento (uuid) la cui sessione è a schermo adesso: la sessione lo scrive entrando e lo toglie uscendo. */
    val visibleSession = MutableStateFlow<String?>(null)

    /** Un allenamento chiuso fuori dalla sessione (notifica, widget) di cui va mostrato il riepilogo: lo prende la sessione se è a schermo, altrimenti `PendingSummaryOverlay`. */
    val pendingSummary = MutableStateFlow<String?>(null)
}

/**
 * Il contratto per navigare da un'area all'altra: un'area chiama questi metodi, non conosce le rotte delle altre. Si ottiene con
 * `LocalPeakNav.current` (o come parametro di `<Area>Destinations`). In una prova di schermata c'è [NoPeakNav], che non fa nulla.
 */
interface PeakNav {
    /** La scheda su cui si è (le schermate spinte appartengono alla scheda da cui sono partite). */
    val tab: StateFlow<Tab>
    val requests: NavRequests

    /** Va a una scheda e chiude quello che sta sopra (anche le impostazioni), come `AppNav.tab = …`. */
    fun goTab(tab: Tab)

    /** Torna indietro di una schermata; `false` se non c'è nulla da chiudere. */
    fun back(): Boolean

    fun openWorkout(workoutId: String)
    fun openExercise(exerciseId: String)
    fun openFriend(username: String)
    fun openSettings(category: String? = null)

    /** `AppNav.templateToStart = …`: va ad Allenamento e avvia quel giorno. */
    fun startTemplate(templateId: String) {
        requests.templateToStart.value = templateId
        goTab(Tab.workouts)
    }

    /** `AppNav.resumeActive = true`: va ad Allenamento e apre la sessione in corso. */
    fun resumeActiveWorkout() {
        requests.resumeActive.value = true
        goTab(Tab.workouts)
    }
}

/** La navigazione vera, sopra un [NavController]. */
class RealPeakNav(private val controller: NavController, override val tab: StateFlow<Tab>) : PeakNav {
    override val requests = NavRequests()

    override fun goTab(tab: Tab) {
        // di nuovo la scheda in cui si è: si torna alla sua radice (e si chiude quello che sta sopra, impostazioni comprese)
        if (tab == this.tab.value && controller.popBackStack(tab.home::class, inclusive = false)) return
        controller.navigate(tab.home) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    override fun back(): Boolean = controller.popBackStack()
    override fun openWorkout(workoutId: String) = controller.navigate(Routes.WorkoutSession(workoutId))
    override fun openExercise(exerciseId: String) = controller.navigate(Routes.ExerciseDetail(exerciseId))
    override fun openFriend(username: String) = controller.navigate(Routes.FriendProfile(username))
    override fun openSettings(category: String?) = controller.navigate(Routes.Settings(category))
}

/** Per le prove di schermata e le anteprime: nessuna navigazione. */
class NoPeakNav(initial: Tab = Tab.workouts) : PeakNav {
    override val tab = MutableStateFlow(initial)
    override val requests = NavRequests()
    override fun goTab(tab: Tab) { this.tab.value = tab }
    override fun back() = false
    override fun openWorkout(workoutId: String) {}
    override fun openExercise(exerciseId: String) {}
    override fun openFriend(username: String) {}
    override fun openSettings(category: String?) {}
}

val LocalPeakNav = compositionLocalOf<PeakNav> { NoPeakNav() }
