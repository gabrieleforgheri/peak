package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.plateColor
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.tr

/** Impostazioni → Allenamento: obiettivo, timer di recupero, progressione e calcolatore dischi (`TrainingSettingsView`). */
@Composable
fun TrainingSettingsPage() {
    val settings = LocalAppSettings.current
    val update = rememberUpdateSettings()
    val app = LocalApp.current
    val view = LocalView.current

    SettingsPage {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Allenamento")

                val goal = maxOf(1, settings.weeklyGoal)
                SettingsStepper(tr("Obiettivo della settimana: %lld allenamenti", goal), goal, 1..7) { n -> update { copy(weeklyGoal = n) } }

                SettingsDivider()

                SettingsSwitch(
                    "Timer di recupero", "Parte dopo ogni serie e avvisa quando è ora di ricominciare.", settings.restTimerEnabled,
                    { on -> update { copy(restTimerEnabled = on) } },
                )
                if (settings.restTimerEnabled) {
                    SettingsStepper(
                        tr("Recupero predefinito: %@", Fmt.duration(settings.restSeconds.toDouble())), settings.restSeconds, 15..600, step = 15,
                    ) { n -> update { copy(restSeconds = n) } }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Recupero dopo il riscaldamento"), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textPrimary)
                        SegmentedPicker(
                            WARMUP_REST_PERCENTS.map { it to "$it%" }, settings.warmupRestPercent, { p -> update { copy(warmupRestPercent = p) } },
                        )
                        Hint(tr("Percentuale del recupero normale"))
                    }
                }

                SettingsDivider()

                SettingsSwitch(
                    "Suggerisci il prossimo carico",
                    "Doppia progressione: quando tutte le serie arrivano in cima al range, +1 passo e si riparte dal fondo.",
                    settings.progressionEnabled, { on -> update { copy(progressionEnabled = on) } },
                )
                if (settings.progressionEnabled) {
                    SettingsStepper(tr("Ripetizioni minime: %lld", settings.repRangeMin), settings.repRangeMin, 1..30) { n -> update { copy(repRangeMin = n) } }
                    SettingsStepper(
                        tr("Ripetizioni massime: %lld", settings.repRangeMax), settings.repRangeMax, settings.repRangeMin..30,
                    ) { n -> update { copy(repRangeMax = n) } }
                }

                SettingsDivider()

                SettingsSwitch(
                    "Calcolatore dischi", "Sui bilancieri e sulle plate loaded mostra quali dischi mettere per lato.",
                    settings.plateCalculatorEnabled, { on -> update { copy(plateCalculatorEnabled = on) } },
                )
                if (settings.plateCalculatorEnabled) {
                    Text(tr("Dischi disponibili"), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                    PlateGrid(Plates.catalog(settings.unit), settings.availablePlates, { plate ->
                        update { copy(availablePlates = togglePlate(availablePlates, plate)) }
                        app.haptics.tap(view)
                    }, label = { Fmt.weight(it, settings.unit, includeSymbol = false) })
                }
            }
        }
    }
}

/** Le percentuali di `Picker("Recupero dopo il riscaldamento")`. */
val WARMUP_REST_PERCENTS = listOf(0, 25, 50, 75, 100)

/** `LazyVGrid(columns: [.adaptive(minimum: 58)], spacing: 8)`: tante colonne quante ne stanno con almeno 58 dp l'una. */
@Composable
private fun PlateGrid(catalog: List<Double>, selected: List<Double>, onToggle: (Double) -> Unit, label: (Double) -> String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = maxOf(1, ((maxWidth.value + 8) / (58 + 8)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in catalog.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (plate in row) {
                        val on = hasPlate(selected, plate)
                        Text(
                            label(plate),
                            Modifier.weight(1f)
                                .background(if (on) plateColor(plate).copy(alpha = 0.3f) else PeakTheme.card, RoundedCornerShape(10.dp))
                                .pressable { onToggle(plate) }
                                .padding(vertical = 8.dp),
                            style = PeakTheme.rounded(13, FontWeight.Bold), color = if (on) PeakTheme.textPrimary else PeakTheme.textTertiary,
                            textAlign = TextAlign.Center, maxLines = 1,
                        )
                    }
                    // le colonne mancanti dell'ultima riga restano vuote, della stessa larghezza
                    repeat(columns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
