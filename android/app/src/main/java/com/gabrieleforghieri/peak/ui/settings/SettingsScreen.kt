package com.gabrieleforghieri.peak.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.util.tr

/** Le sottopagine: le sei categorie e, dentro «Dati», l'elenco degli archiviati (`ArchivedExercisesView`). */
private const val ARCHIVED = "archived"

/**
 * `SettingsView`: l'elenco delle categorie e, sopra, la sottopagina aperta (le sottopagine stanno qui dentro, non nel grafo di
 * navigazione; il tasto indietro le chiude una alla volta). `category` apre subito quella sottopagina (`Routes.Settings`).
 * `onClose` chiude le impostazioni («Fine», o indietro dalla radice).
 */
@Composable
fun SettingsScreen(category: String?, onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsCategory.of(category)?.name) }
    BackHandler(enabled = page != null) { page = if (page == ARCHIVED) SettingsCategory.`data`.name else null }

    val title = when (page) {
        null -> "Impostazioni"
        ARCHIVED -> "Archiviati"
        else -> SettingsCategory.of(page)?.title ?: "Impostazioni"
    }
    SettingsFrame(
        title,
        onBack = page?.let { { page = if (it == ARCHIVED) SettingsCategory.`data`.name else null } },
        onDone = if (page == null) onClose else null,
    ) {
        when (page) {
            null -> SettingsRoot { page = it.name }
            SettingsCategory.general.name -> GeneralSettingsPage()
            SettingsCategory.training.name -> TrainingSettingsPage()
            SettingsCategory.account.name -> AccountSettingsPage()
            SettingsCategory.health.name -> HealthSettingsPage()
            SettingsCategory.backup.name -> BackupSettingsPage()
            SettingsCategory.`data`.name -> DataSettingsPage(onOpenArchived = { page = ARCHIVED })
            ARCHIVED -> ArchivedExercisesPage()
            else -> SettingsRoot { page = it.name }
        }
    }
}

/** Lo sfondo dell'app, la barra col titolo e, sotto, la pagina. Senza barra in basso (le impostazioni sono a tutto schermo): si prende da sé i margini di sistema. */
@Composable
fun SettingsFrame(title: String, onBack: (() -> Unit)?, onDone: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SettingsTopBar(title, onBack, onDone)
            content()
        }
    }
}

/** L'elenco delle categorie e la versione (`categoryList` e `infoCard`). */
@Composable
fun SettingsRoot(onOpen: (SettingsCategory) -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
    }
    SettingsPage {
        GlassCard(padding = 6.dp) {
            SettingsCategory.entries.forEachIndexed { index, category ->
                Row(
                    Modifier.fillMaxWidth().pressable { onOpen(category) }.padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SettingsIcons.of(category), null, Modifier.size(22.dp), tint = LocalAccent.current)
                    Spacer(Modifier.width(18.dp))
                    Text(tr(category.title), Modifier.weight(1f), style = PeakTheme.rounded(16, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                    Icon(PeakIcons.chevronRight, null, Modifier.size(18.dp), tint = PeakTheme.textTertiary)
                }
                if (index < SettingsCategory.entries.lastIndex) SettingsDivider(Modifier.padding(start = 52.dp))
            }
        }
        Text(
            "Peak $version", Modifier.fillMaxWidth(), style = PeakTheme.rounded(11, FontWeight.Medium),
            color = PeakTheme.textTertiary, textAlign = TextAlign.Center,
        )
    }
}
