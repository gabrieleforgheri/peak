package com.gabrieleforghieri.peak.ui.start

import androidx.navigation.NavGraphBuilder
import com.gabrieleforghieri.peak.ui.nav.PeakNav

/**
 * Avvio: `Features/Root/` senza `RootView` (benvenuto, tutorial, idee, obiettivo, collegamento). Proprietario: l'area **start**.
 * `PeakRoot` chiama solo questa funzione.
 *
 * Non ha schermate di navigazione: tutto quello che apre l'avvio è sopra le altre schermate. I punti d'aggancio sono [WelcomeOverlay],
 * [TutorialOverlay] e [GoalOverlay] (`StartOverlays.kt`), e i fogli condivisi [ConnectSheet] e [GoalPickerSheet]. Se l'area ha bisogno
 * di una rotta sua (non pubblica) la registra qui, con un tipo `@Serializable` suo.
 */
fun NavGraphBuilder.startDestinations(nav: PeakNav) {}
