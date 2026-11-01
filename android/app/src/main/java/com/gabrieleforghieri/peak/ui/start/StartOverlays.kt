package com.gabrieleforghieri.peak.ui.start

import androidx.compose.runtime.Composable

// I punti che l'iPhone apre sopra tutto, in `RootView`: il benvenuto, il tutorial, l'obiettivo. `PeakRoot` li chiama in ordine sopra
// la navigazione; ognuno è un aggancio già pronto, vuoto, che l'area **start** riempie (proprietario: start). Non hanno argomenti:
// leggono impostazioni, navigazione e dati dall'ambiente (`LocalAppSettings`, `LocalPeakNav`, `LocalRepository`).

/**
 * Il primo avvio a tutto schermo (`OnboardingView`), e i compiti d'avvio che `RootView.task` fa una volta per apertura: backup
 * automatico, collegamento al catalogo (`linkCatalogOnce`), pittogrammi fissati (`freezeGlyphsOnce`), `Programs.adoptLoose`, la prima
 * sincronizzazione e `Catalog.refresh`. Va deciso una volta sola all'apertura: un'app con dei dati non lo mostra.
 */
@Composable
fun WelcomeOverlay() {}

/**
 * Le idee di Peak a tutto schermo (`IdeasView`), poi il giro sulle schermate vere (`TourDriver`, `TourOverlay`, `TourCoach`),
 * guidato da [TourState] e dai [tourTarget] segnati dalle altre aree. Parte da solo quando serve (`wantTutorial`) o da «Rivedi il tutorial».
 */
@Composable
fun TutorialOverlay() {}

/** L'obiettivo settimanale la prima volta (`fullScreenCover { GoalPickerView }` di `RootView`): si apre quando `didSeeWelcome && weeklyGoal == 0`. Usa [GoalPickerSheet]. */
@Composable
fun GoalOverlay() {}
