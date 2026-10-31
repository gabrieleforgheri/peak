package com.gabrieleforghieri.peak.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.SecondaryButton
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.sync.ConnectFlow.Stage
import com.gabrieleforghieri.peak.sync.RestoreMode
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.util.tr
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * La schermata provvisoria della scheda Me: stato dell'account, collegamento, sincronizzazione e backup. Serve a provare la fase 2; le
 * Impostazioni vere arrivano con la fase 3 (`Settings/`, `Root/ConnectFlow`).
 */
@Composable
fun AccountScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as PeakApplication
    val sync = app.sync
    val scope = rememberCoroutineScope()
    val status by sync.engine.status.collectAsState()
    val stage by sync.connect.stage.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    var confirmReplace by remember { mutableStateOf<ByteArray?>(null) }

    fun report(block: suspend () -> String?) = scope.launch {
        note = try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { e.message }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) report {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(app.backup.data()) }
            context.tr("Backup esportato.")
        }
    }
    var restoreMode by remember { mutableStateOf(RestoreMode.Merge) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) report {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            if (restoreMode == RestoreMode.Replace) { confirmReplace = bytes; null } else restore(app, bytes, RestoreMode.Merge)
        }
    }
    val importForConnection = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { sync.connect.importBackup(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }) }
    }

    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current)).padding(padding)) {
        Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(PeakTheme.padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(tr("Me"), Modifier.appear(0), style = PeakTheme.display(34), color = PeakTheme.textPrimary)

            SectionHeader("Account yG")
            GlassCard(Modifier.appear(1)) {
                Body(accountLine(status))
                status.lastSync?.let { Hint(tr("Ultima sincronizzazione: %@", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))) }
                if (status.pendingCount > 0) Hint(tr("Da inviare: %lld", status.pendingCount))
                status.lastError?.let { Hint(it, error = true) }
                Spacer(Modifier.height(12.dp))
                when {
                    stage is Stage.Working -> {
                        Body((stage as Stage.Working).message)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(tr("Annulla"), { sync.connect.cancel() })
                    }
                    stage is Stage.EmptyServer -> {
                        Body(tr("Su Peak non ci sono ancora dati."))
                        Spacer(Modifier.height(8.dp))
                        if ((stage as Stage.EmptyServer).hasLocalData) {
                            PrimaryButton(tr("Carica i dati di questo telefono"), { scope.launch { sync.connect.uploadLocalData() } })
                            Spacer(Modifier.height(8.dp))
                        }
                        SecondaryButton(tr("Importa un backup"), { importForConnection.launch(arrayOf("*/*")) })
                        Spacer(Modifier.height(8.dp))
                        // «Parti da zero» sostituirebbe i dati del telefono con un account vuoto: solo se non ce ne sono
                        if (!(stage as Stage.EmptyServer).hasLocalData) {
                            SecondaryButton(tr("Parti da zero"), { scope.launch { sync.connect.startEmpty() } })
                            Spacer(Modifier.height(8.dp))
                        }
                        SecondaryButton(tr("Annulla"), { sync.connect.cancel() })
                    }
                    stage is Stage.Conflict -> {
                        Body(tr("Su Peak ci sono già dei dati, e anche questo telefono ne ha di suoi."))
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(tr("Usa i dati di Peak"), { scope.launch { sync.connect.useServerData() } })
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(tr("Unisci"), { scope.launch { sync.connect.merge() } })
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(tr("Annulla"), { sync.connect.cancel() })
                    }
                    stage is Stage.Failed -> {
                        Hint((stage as Stage.Failed).message, error = true)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(tr("Chiudi"), { sync.connect.dismiss() })
                    }
                    status.state == SyncEngine.State.Connected -> {
                        PrimaryButton(tr("Sincronizza ora"), { scope.launch { sync.syncNow() } })
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(tr("Scollega"), { sync.engine.disconnect() })
                    }
                    else -> {
                        PrimaryButton(
                            if (status.state == SyncEngine.State.Expired) tr("Collega di nuovo") else tr("Collega l'account yG"),
                            {
                                scope.launch {
                                    val url = sync.connect.begin() ?: return@launch
                                    Log.i("PeakLogin", "Custom Tab: $url")
                                    try {
                                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                                    } catch (_: ActivityNotFoundException) {
                                        // nessun browser sul telefono
                                        sync.connect.cancel()
                                        note = context.tr("Non trovo un browser per il login.")
                                    }
                                }
                            },
                        )
                        if (status.state == SyncEngine.State.Expired) {
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton(tr("Scollega"), { sync.engine.disconnect() })
                        }
                    }
                }
            }

            SectionHeader("Backup")
            GlassCard(Modifier.appear(2)) {
                Body(tr("Un file JSON, lo stesso dell'iPhone e di Peak sul web."))
                Spacer(Modifier.height(12.dp))
                PrimaryButton(tr("Esporta backup"), { export.launch("Peak-${java.time.LocalDate.now()}.json") })
                Spacer(Modifier.height(8.dp))
                SecondaryButton(tr("Importa backup (unisci)"), { restoreMode = RestoreMode.Merge; importFile.launch(arrayOf("*/*")) })
                Spacer(Modifier.height(8.dp))
                SecondaryButton(tr("Importa backup (sostituisci tutto)"), { restoreMode = RestoreMode.Replace; importFile.launch(arrayOf("*/*")) })
                note?.let { Spacer(Modifier.height(8.dp)); Hint(it) }
            }
        }
    }

    confirmReplace?.let { bytes ->
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text(tr("Sostituire tutti i dati?")) },
            text = { Text(tr("I dati di questo telefono saranno cancellati e sostituiti con quelli del file.")) },
            confirmButton = { TextButton({ confirmReplace = null; report { restore(app, bytes, RestoreMode.Replace) } }) { Text(tr("Sostituisci")) } },
            dismissButton = { TextButton({ confirmReplace = null }) { Text(tr("Annulla")) } },
        )
    }
}

private suspend fun restore(app: PeakApplication, bytes: ByteArray, mode: RestoreMode): String {
    val file = app.backup.restore(bytes, mode)
    return "Importato: ${file.exercises.size} macchinari, ${file.workouts.size} allenamenti."
}

@Composable
private fun accountLine(status: SyncEngine.Status): String = when (status.state) {
    SyncEngine.State.Disconnected -> tr("Nessun account collegato. L'app funziona sul telefono, senza account.")
    SyncEngine.State.Onboarding -> tr("Collegamento in corso…")
    SyncEngine.State.Connected -> tr("Collegato come %@", status.credentials?.name ?: "")
    SyncEngine.State.Expired -> tr("Il collegamento a yG è scaduto: collega di nuovo.")
}

@Composable
private fun Body(text: String) = Text(text, style = PeakTheme.rounded(15, FontWeight.Medium), color = PeakTheme.textPrimary)

@Composable
private fun Hint(text: String, error: Boolean = false) =
    Text(text, Modifier.padding(top = 4.dp), style = PeakTheme.rounded(13, FontWeight.Medium), color = if (error) PeakTheme.negative else PeakTheme.textSecondary)
