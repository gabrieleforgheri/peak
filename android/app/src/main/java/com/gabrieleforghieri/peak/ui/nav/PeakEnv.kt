package com.gabrieleforghieri.peak.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.data.PeakRepository
import com.gabrieleforghieri.peak.design.LocalPhotoStore
import com.gabrieleforghieri.peak.domain.AppSettings
import kotlinx.coroutines.launch

// L'«ambiente» delle schermate, come `@Environment(AppSettings.self)` e `@Environment(\.modelContext)` su iPhone: lo fornisce
// `PeakRoot` (e una prova di schermata, a mano). Proprietario: la base della fase 3.

/** L'app: i servizi (record, sincronizzazione, foto…). Una schermata non ne tiene riferimenti oltre la sua vita. */
val LocalApp = staticCompositionLocalOf<PeakApplication> { error("LocalApp non fornito: avvolgere in PeakEnvironment") }

/** La lettura comune dei dati (`Flow`). */
val LocalRepository = staticCompositionLocalOf<PeakRepository> { error("LocalRepository non fornito: avvolgere in PeakEnvironment") }

/** Le impostazioni di adesso (`settings` di SwiftUI). Per cambiarle `rememberUpdateSettings()`. */
val LocalAppSettings = compositionLocalOf { AppSettings() }

/** Per cambiare le impostazioni: `val update = rememberUpdateSettings(); update { copy(unit = WeightUnit.lb) }`. */
@Composable
fun rememberUpdateSettings(): (AppSettings.() -> AppSettings) -> Unit {
    val app = LocalApp.current
    val current by rememberUpdatedState(LocalAppSettings.current)
    val scope = rememberCoroutineScope()
    return { change -> scope.launch { app.settings.edit(current, change) } }
}

/** Fornisce l'ambiente alle schermate: l'app, la lettura dei dati, le impostazioni vive e le foto. Una prova di schermata lo usa come `PeakRoot`. */
@Composable
fun PeakEnvironment(app: PeakApplication, content: @Composable () -> Unit) {
    val settings by app.settings.settings.collectAsState(initial = AppSettings())
    CompositionLocalProvider(
        LocalApp provides app,
        LocalRepository provides app.repository,
        LocalAppSettings provides settings,
        LocalPhotoStore provides app.photos,
        content = content,
    )
}
