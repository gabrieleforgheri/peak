package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.WarningOrange
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.start.ConnectSheet
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch

/** Impostazioni → Account yG: collegamento, sincronizzazione, scollegamento (`AccountSettingsView`). Il collegamento è il foglio [ConnectSheet]. */
@Composable
fun AccountSettingsPage() {
    val sync = LocalApp.current.sync
    val status by sync.engine.status.collectAsState()
    val scope = rememberCoroutineScope()
    AccountSettingsContent(
        status,
        onSync = { scope.launch { sync.syncNow() } },
        onDisconnect = { sync.engine.disconnect() },
    )
}

/** La pagina per uno stato dato: la provano gli screenshot (senza account, collegato, scaduto, con errore). */
@Composable
fun AccountSettingsContent(status: SyncEngine.Status, onSync: () -> Unit, onDisconnect: () -> Unit) {
    var showingConnect by remember { mutableStateOf(false) }
    var showingDisconnect by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    SettingsPage {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Account yG", trailing = accountStatusTitle(status.state))

                when (status.state) {
                    SyncEngine.State.Connected, SyncEngine.State.Expired -> {
                        val expired = status.state == SyncEngine.State.Expired
                        Hint(
                            if (expired) tr("Il collegamento è scaduto: i dati restano qui ma non si sincronizzano.")
                            else tr("Collegato come %@. Allenamenti e massimali sono anche su peak.yrb4g.com.", accountDisplayName(status.credentials?.name, tr("il tuo account yG"))),
                            color = PeakTheme.textSecondary, size = 12,
                        )
                        status.lastSync?.let { Hint(tr("Ultima sincronizzazione %@", shortDateTime(it))) }
                        if (status.pendingCount > 0) Hint(tr("%lld modifiche in attesa di essere inviate", status.pendingCount))
                        status.lastError?.let { Hint(it, color = WarningOrange) }

                        if (expired) {
                            SettingsButton(tr("Accedi di nuovo"), SettingsIcons.signIn, { showingConnect = true }, primary = true)
                        } else {
                            SettingsButton(
                                if (status.isSyncing) tr("Sincronizzo…") else tr("Sincronizza adesso"), SettingsIcons.sync, onSync,
                                primary = false, enabled = !status.isSyncing,
                            )
                        }
                        SettingsTextAction(tr("Scollega questo telefono"), SettingsIcons.unlink, PeakTheme.negative) { showingDisconnect = true }
                    }
                    else -> {
                        Hint(
                            tr("Con l'account yG i tuoi dati si sincronizzano con il web e restano al sicuro sul server. Senza, Peak funziona solo su questo telefono."),
                            color = PeakTheme.textSecondary, size = 12,
                        )
                        SettingsButton(tr("Collega a yG"), SettingsIcons.signIn, { showingConnect = true }, primary = true)
                    }
                }
            }
        }

        // dall'account yG si gestiscono profilo, password, accessi e dispositivi
        GlassCard(padding = 6.dp) {
            ManageLink(tr("Il tuo account yG"), tr("Profilo, password, accessi e le altre app"), SettingsIcons.account) { uri.openUri(ACCOUNT_URL) }
            SettingsDivider(Modifier.padding(start = 52.dp))
            ManageLink(tr("Accessi di Peak"), tr("Dispositivi collegati e dati salvati"), SettingsIcons.phone) { uri.openUri(PEAK_ACCESS_URL) }
        }
    }

    if (showingConnect) ConnectSheet(onDismiss = { showingConnect = false })
    if (showingDisconnect) {
        ChoiceSheet(
            "Scollegare questo telefono?",
            "I dati restano su questo telefono e su Peak, ma non si sincronizzano più. Per togliere l'accesso a questo telefono usa anche yrb4g.com/account/peak.",
            listOf(SheetAction("Scollega", destructive = true, onClick = onDisconnect)),
            onDismiss = { showingDisconnect = false },
        )
    }
}

const val ACCOUNT_URL = "https://yrb4g.com/account"
const val PEAK_ACCESS_URL = "https://yrb4g.com/account/peak"

@Composable
private fun ManageLink(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = LocalAccent.current)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
            Text(subtitle, style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
        Icon(SettingsIcons.outward, null, Modifier.size(16.dp), tint = PeakTheme.textTertiary)
    }
}
