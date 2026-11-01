package com.gabrieleforghieri.peak.ui.start

import com.gabrieleforghieri.peak.domain.AppSettings

/**
 * Le condizioni con cui `RootView.swift` decide cosa mettere sopra tutto: quando si vedono il benvenuto, l'obiettivo e il tutorial, e
 * quando no. Kotlin puro, senza Compose: le prove sul JVM (`StartRulesTest`) le leggono da qui.
 */
object StartRules {
    /** Che cosa fare, una volta sola all'apertura, con un'app che non ha ancora visto il benvenuto. */
    enum class FirstRun {
        /** Il benvenuto lo ha già visto, o c'è lo store di emergenza: niente da fare. */
        Nothing,

        /** L'app ha già dei dati (un aggiornamento, un account ritrovato): il benvenuto si segna come visto e non si mostra. */
        MarkSeen,

        /** App vuota: parte il primo avvio a tutto schermo. */
        Onboard,
    }

    /** `RootView.decide()`: un'app vuota parte dal primo avvio; una con dei dati (o con lo store di emergenza) no. */
    fun firstRun(didSeeWelcome: Boolean, isFallback: Boolean, hasData: Boolean): FirstRun = when {
        didSeeWelcome || isFallback -> FirstRun.Nothing
        hasData -> FirstRun.MarkSeen
        else -> FirstRun.Onboard
    }

    /**
     * `RootView.wantTutorial`: il tutorial parte da solo (dopo il primo avvio, o da «Rivedi il tutorial») quando la sincronizzazione ha
     * già detto la sua e l'obiettivo c'è. Dopo il primo giro non riparte; si rilancia mettendo `tutorialDone = false` o `tourSeen < 2`.
     */
    fun wantTutorial(synced: Boolean, s: AppSettings, isFallback: Boolean): Boolean =
        synced && s.didSeeWelcome && s.weeklyGoal > 0 && (!s.tutorialDone || s.tourSeen < 2) && !isFallback

    /** `fullScreenCover(isPresented: .constant(settings.didSeeWelcome && settings.weeklyGoal == 0))`: l'obiettivo la prima volta. */
    fun showGoal(s: AppSettings): Boolean = s.didSeeWelcome && s.weeklyGoal == 0

    /** Il velo di sfondo (`Theme.background`) che copre le schede finché non si è deciso se mostrare il benvenuto. */
    fun coversTabs(decided: Boolean, s: AppSettings): Boolean = !decided && !s.didSeeWelcome

    /** Il riquadro del tutorial sopra le schede: solo nei passi dopo le idee, e mai con lo store di emergenza. */
    fun showsMarks(phase: TourState.Phase, isFallback: Boolean): Boolean =
        (phase == TourState.Phase.marks || phase == TourState.Phase.cheer) && !isFallback
}

/**
 * La sequenza del tutorial (`TourDriver` di `TourOverlay.swift`) come funzioni pure: chi guida il giro le chiama quando cambia
 * qualcosa (un foglio si apre, la sessione parte, una serie si salva), le prove ne percorrono la sequenza.
 */
object TourFlow {
    /** Il passo da prendere, se diverso; `goWorkouts` = serve tornare alla scheda Allenamento; `waitForSession` = la sessione sta per aprirsi, si aspetta. */
    data class Next(val step: TourState.Step, val goWorkouts: Boolean = false, val waitForSession: Boolean = false)

    /** Quanto aspetta la sessione dopo la scelta del giorno, prima di tornare a «Inizia da qui». */
    const val SESSION_WAIT_MS = 1500L

    /** Quanto resta la festa della prima serie. */
    const val CHEER_MS = 3600L

    /**
     * `TourDriver.reconcile()`: ogni passo che apre un foglio ha il suo: si apre e si passa a quello, si chiude senza finire e si torna
     * al passo di prima. `onWorkouts` = la scheda attiva è Allenamento; `hasActive` = c'è un allenamento in corso.
     */
    fun reconcile(
        phase: TourState.Phase, step: TourState.Step, sheet: TourState.Sheet?, inSession: Boolean, hasActive: Boolean, onWorkouts: Boolean,
    ): Next? {
        if (phase != TourState.Phase.marks) return null
        return when (step) {
            TourState.Step.tabs -> null
            TourState.Step.start, TourState.Step.pick -> {
                val go = !onWorkouts
                when {
                    inSession -> Next(TourState.Step.set, go)
                    sheet == TourState.Sheet.days -> Next(TourState.Step.pick, go)
                    // il giorno scelto: la sessione sta per aprirsi, il passo la aspetta; se non arriva si torna a «Inizia da qui»
                    step == TourState.Step.pick -> if (!hasActive) Next(TourState.Step.start, go) else Next(step, go, waitForSession = true)
                    else -> Next(step, go)
                }
            }
            TourState.Step.set, TourState.Step.load, TourState.Step.save -> when {
                sheet == TourState.Sheet.save -> if (step == TourState.Step.set) Next(TourState.Step.load) else null
                step != TourState.Step.set -> Next(TourState.Step.set)
                !inSession -> Next(TourState.Step.start)
                else -> null
            }
        }
    }

    /** Dopo l'attesa della sessione: se non è arrivata (nessun foglio, nessuna sessione) si torna a «Inizia da qui». */
    fun afterWait(step: TourState.Step, sheet: TourState.Sheet?, inSession: Boolean): TourState.Step? =
        if (step == TourState.Step.pick && sheet == null && !inSession) TourState.Step.start else null

    /** Il risultato di `TourDriver.watch()`: la serie di partenza da ricordare (−1 = nessuna) e se il giro è finito. */
    data class Watched(val base: Int, val finished: Boolean)

    /**
     * L'ultimo passo si chiude da solo quando nell'allenamento compare una serie in più. `count` = le serie di adesso (`null` = nessun
     * allenamento in corso o fuori dai passi che guardano).
     */
    fun watch(phase: TourState.Phase, step: TourState.Step, count: Int?, base: Int): Watched {
        val watching = phase == TourState.Phase.marks && (step == TourState.Step.set || step == TourState.Step.load || step == TourState.Step.save)
        if (!watching || count == null) return Watched(-1, false)
        if (base < 0 || count < base) return Watched(count, false)
        if (count > base) return Watched(-1, true)
        return Watched(base, false)
    }
}
