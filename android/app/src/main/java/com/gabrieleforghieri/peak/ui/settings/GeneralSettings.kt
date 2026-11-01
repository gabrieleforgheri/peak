package com.gabrieleforghieri.peak.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.color
import com.gabrieleforghieri.peak.domain.AccentPalette
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.Tab
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.SoundFX
import com.gabrieleforghieri.peak.util.tr

/** Impostazioni → Generale: lingua, unità, colore e suoni (`GeneralSettingsView`). */
@Composable
fun GeneralSettingsPage() {
    val settings = LocalAppSettings.current
    val update = rememberUpdateSettings()
    val app = LocalApp.current
    val nav = LocalPeakNav.current
    val view = LocalView.current
    val context = LocalContext.current
    val accent = LocalAccent.current

    SettingsPage {
        // lingua
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Lingua")
                SegmentedPicker(
                    listOf("" to tr("Telefono"), "it" to tr("Italiano"), "en" to tr("English")), settings.language,
                    { code -> update { copy(language = code) }; applyAppLanguage(context, code) },
                )
                if (canApplyLanguage(Build.VERSION.SDK_INT)) {
                    Hint(tr("Segue il tuo account: la stessa lingua sul web e sull'iPhone."))
                } else {
                    Hint(tr("Per scegliere la lingua dell'app serve Android 13 o più recente: qui Peak usa quella del telefono."), color = accent)
                }
                Hint(tr("Puoi cambiarla anche da Impostazioni di Android › App › Peak."))
                Text(
                    tr("Apri Impostazioni"),
                    Modifier.clickable(remember { MutableInteractionSource() }, null) { openAppSettings(context) },
                    style = PeakTheme.rounded(12, FontWeight.SemiBold), color = accent,
                )
            }
        }

        // unità
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Unità di misura")
                SegmentedPicker(WeightUnit.entries.map { it to it.symbol.uppercase() }, settings.unit, { unit -> update { withUnit(unit).first } })
                Hint(tr("I dati restano sempre salvati in chilogrammi: cambia solo come vengono mostrati."))
            }
        }

        // colore accento
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Colore accento")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (palette in AccentPalette.entries) {
                        val on = settings.accent == palette
                        Box(
                            Modifier.size(42.dp).clickable(remember { MutableInteractionSource() }, null) { update { copy(accent = palette) }; app.haptics.tap(view) }
                                .semantics { role = Role.RadioButton; selected = on; contentDescription = palette.title },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(42.dp).border(2.dp, Color.White.copy(alpha = if (on) 0.9f else 0f), CircleShape))
                            Box(Modifier.size(34.dp).background(palette.color, CircleShape))
                        }
                    }
                }
            }
        }

        // suoni
        GlassCard {
            SettingsSwitch(
                "Suoni", "Effetti sonori per serie, record e fine allenamento.", settings.soundsEnabled,
                { on ->
                    app.soundsOn.value = on
                    update { copy(soundsEnabled = on) }
                    if (on) app.sounds.play(SoundFX.Effect.set)
                },
            )
        }

        // tutorial
        GlassCard {
            SettingsTextAction(tr("Rivedi il tutorial"), SettingsIcons.help, accent) {
                update { copy(tutorialDone = false) }
                nav.goTab(Tab.workouts)
            }
        }
    }
}

/** Da Android 13 la lingua dell'app si cambia a voce (`LocaleManager`) e il sistema ricrea la schermata; `""` torna a quella del telefono. */
fun applyAppLanguage(context: Context, code: String) {
    if (!canApplyLanguage(Build.VERSION.SDK_INT)) return
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    val wanted = LocaleList.forLanguageTags(code)
    if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
}

private fun openAppSettings(context: Context) {
    val uri = Uri.parse("package:${context.packageName}")
    val intent = if (Build.VERSION.SDK_INT >= 33) Intent(Settings.ACTION_APP_LOCALE_SETTINGS, uri) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // l'impostazione per app non c'è su tutti i telefoni: si ripiega sulla scheda dell'app
        try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
