package com.gabrieleforghieri.peak.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.ui.nav.PeakEnvironment
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Lo screenshot di una schermata senza emulatore (Roborazzi + Robolectric): tema, ambiente (app, database, impostazioni) e sfondo come
 * nell'app vera; il PNG finisce in `android/docs/screens/<name>.png`. `./gradlew recordRoborazziDebug` lo registra,
 * `./gradlew verifyRoborazziDebug` controlla che non sia cambiato (comandi in `android/README.md`).
 *
 * `content` è la schermata (per esempio il corpo di una `composable<…>` di un'area); la navigazione è [com.gabrieleforghieri.peak.ui.nav.NoPeakNav]
 * finché non si fornisce `LocalPeakNav`. I fogli (`PeakSheet`) stanno in una finestra a parte e non entrano nello screenshot: si provano
 * a parte, con il loro contenuto.
 */
fun ComposeContentTestRule.screenshot(name: String, content: @Composable () -> Unit) {
    val app = ApplicationProvider.getApplicationContext<PeakApplication>()
    setContent {
        PeakEnvironment(app) {
            PeakTheme {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) { content() }
            }
        }
    }
    onRoot().captureRoboImage("../docs/screens/$name.png")
}
