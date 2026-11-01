package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.sync.PeakCredentials
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.screenshot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Le pagine delle Impostazioni, una per stato, in italiano (`docs/screens/settings-*.png`). Le pagine che leggono l'ambiente
 * (impostazioni, account) si provano passando lo stato a mano.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-" + RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun page(name: String, title: String, settings: AppSettings? = null, onBack: (() -> Unit)? = {}, content: @Composable () -> Unit) =
        compose.screenshot("settings-$name") {
            CompositionLocalProvider(LocalAppSettings provides (settings ?: LocalAppSettings.current)) {
                SettingsFrame(title, onBack, onDone = if (onBack == null) ({}) else null, content)
            }
        }

    private val connected = SyncEngine.Status(
        state = SyncEngine.State.Connected, credentials = PeakCredentials("t", "s", "Gabriele"), lastSync = 1_793_000_000_000, pendingCount = 0,
    )

    @Test fun root() = page("root", "Impostazioni", onBack = null) { SettingsRoot {} }

    @Test fun general() = page("generale", "Generale") { GeneralSettingsPage() }
    @Test fun generalPounds() = page("generale-lb", "Generale", AppSettings(unit = WeightUnit.lb, soundsEnabled = false)) { GeneralSettingsPage() }

    @Test fun training() = page("allenamento", "Allenamento", AppSettings(weeklyGoal = 4)) { TrainingSettingsPage() }
    @Test fun trainingAllOff() = page(
        "allenamento-spento", "Allenamento",
        AppSettings(restTimerEnabled = false, progressionEnabled = false, plateCalculatorEnabled = false),
    ) { TrainingSettingsPage() }
    @Test fun trainingPounds() = page("allenamento-lb", "Allenamento", AppSettings(unit = WeightUnit.lb, availablePlates = Plates.poundCatalog.take(4))) { TrainingSettingsPage() }

    @Test fun accountDisconnected() = page("account-senza-account", "Account yG") { AccountSettingsContent(SyncEngine.Status(), {}, {}) }
    @Test fun accountConnected() = page("account-collegato", "Account yG") { AccountSettingsContent(connected, {}, {}) }
    @Test fun accountPending() = page("account-collegato-in-attesa", "Account yG") {
        AccountSettingsContent(connected.copy(pendingCount = 3), {}, {})
    }
    @Test fun accountSyncing() = page("account-sincronizzo", "Account yG") { AccountSettingsContent(connected.copy(isSyncing = true), {}, {}) }
    @Test fun accountSyncError() = page("account-errore-di-sincronizzazione", "Account yG") {
        AccountSettingsContent(connected.copy(lastError = "Non riesco a raggiungere Peak: controlla la connessione.", pendingCount = 5), {}, {})
    }
    @Test fun accountExpired() = page("account-scaduto", "Account yG") { AccountSettingsContent(connected.copy(state = SyncEngine.State.Expired), {}, {}) }

    @Test fun health() = page("salute", "App Salute") { HealthSettingsPage() }

    @Test fun backupDisconnected() = page("backup-senza-account", "Backup") { BackupSettingsContent(false, {}, {}) }
    @Test fun backupConnected() = page("backup-collegato", "Backup") { BackupSettingsContent(true, {}, {}) }

    @Test fun data() = page("dati", "Dati") { DataSettingsContent(14, 2, {}, {}) }
    @Test fun dataEmpty() = page("dati-vuoto", "Dati") { DataSettingsContent(0, 0, {}, {}) }

    private val archivedList = listOf(
        ExerciseEntity("A", name = "Leg Press", muscleGroup = "gambe", isArchived = true),
        ExerciseEntity("B", name = "Curl con manubri", muscleGroup = "braccia", isArchived = true),
        ExerciseEntity("C", name = "Lat Machine", muscleGroup = "schiena", isArchived = true),
    )
    @Test fun archived() = page("archiviati", "Archiviati") { ArchivedExercisesContent(archivedList) {} }
    @Test fun archivedEmpty() = page("archiviati-vuoto", "Archiviati") { ArchivedExercisesContent(emptyList()) {} }
}
