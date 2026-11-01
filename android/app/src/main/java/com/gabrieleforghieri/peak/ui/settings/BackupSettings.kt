package com.gabrieleforghieri.peak.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.PeakApplication
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.domain.PeakDates
import com.gabrieleforghieri.peak.sync.BackupException
import com.gabrieleforghieri.peak.sync.RestoreMode
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Impostazioni → Backup: esporta e importa (`BackupSettingsView`). Collegato, l'import passa dal server e il telefono si riallinea. */
@Composable
fun BackupSettingsPage() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by app.sync.engine.status.collectAsState()
    val connected = status.state == SyncEngine.State.Connected

    var alert by remember { mutableStateOf<Pair<String, String>?>(null) }
    var choice by remember { mutableStateOf<ByteArray?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var unsaved by remember { mutableStateOf<ByteArray?>(null) }

    fun fail(title: String, e: Exception) {
        app.haptics.warning()
        alert = title to translate(context, e.message ?: "")
    }

    // senza il FileProvider nel manifest il foglio di condivisione non c'è: si ripiega sul «salva con nome» di sistema
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val bytes = unsaved
        unsaved = null
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                app.haptics.success()
            } catch (e: Exception) { fail("Esportazione non riuscita", e) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                choice = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            } catch (e: Exception) { fail("Import non riuscito", e) }
        }
    }

    fun export() = scope.launch {
        try {
            val bytes = app.backup.data()
            if (ShareSheet.isAvailable(context)) {
                ShareSheet.share(context, ShareSheet.stage(context, backupFileName(), bytes), title = context.tr("Esporta backup"))
                app.haptics.success()
            } else {
                unsaved = bytes
                saver.launch(backupFileName())
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail("Esportazione non riuscita", e) }
    }

    fun performImport(data: ByteArray, mode: RestoreMode) {
        if (connected) {
            if (isImporting) return
            isImporting = true
            scope.launch {
                try {
                    importThroughServer(app, data, replace = mode == RestoreMode.Replace)
                    app.haptics.success()
                    alert = "Backup importato" to context.tr("I dati sono su Peak e su questo telefono.")
                } catch (e: CancellationException) { throw e } catch (e: Exception) { fail("Import non riuscito", e) }
                finally { isImporting = false }
            }
            return
        }
        scope.launch {
            try {
                val file = app.backup.restore(data, mode)
                app.haptics.success()
                val day = PeakDates.parse(file.exportedAt)?.let { shortDate(it) } ?: ""
                alert = "Backup importato" to context.tr("%lld macchinari e %lld allenamenti dal %@.", file.exercises.size, file.workouts.size, day)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { fail("Import non riuscito", e) }
        }
    }

    BackupSettingsContent(connected, onExport = { export() }, onImport = { picker.launch(arrayOf("*/*")) })

    choice?.let { data ->
        ChoiceSheet(
            "Come importare il backup?",
            "«Unisci» aggiunge solo ciò che manca. «Sostituisci» cancella i dati attuali e riparte dal file.",
            listOf(
                SheetAction("Unisci ai dati attuali", primary = true) { performImport(data, RestoreMode.Merge) },
                SheetAction("Sostituisci tutto", destructive = true) { performImport(data, RestoreMode.Replace) },
            ),
            onDismiss = { choice = null },
        )
    }
    alert?.let { (title, message) -> AlertSheet(title, message, onDismiss = { alert = null }) }
}

/**
 * Collegato, il backup passa dal server (che unisce i macchinari con lo stesso nome) e poi il telefono si riallinea. Prima del server:
 * dopo, il telefono non potrebbe più riallinearsi con un allenamento aperto.
 */
private suspend fun importThroughServer(app: PeakApplication, data: ByteArray, replace: Boolean) {
    if (app.sync.engine.hasActiveWorkout()) throw BackupException.WorkoutInProgress()
    app.sync.engine.writePreConnectionBackup()
    app.sync.syncNow() // le modifiche locali non ancora salite non si perdono nel riallineamento
    app.sync.engine.uploadBackup(data, replace = replace, first = false)
    app.sync.engine.replaceLocalWithServer()
}

/** La pagina: spiegazione secondo l'account, esporta, importa, e il backup automatico. */
@Composable
fun BackupSettingsContent(connected: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    SettingsPage {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Backup")

                Hint(
                    if (connected) tr("I dati sono anche su Peak, sul server. Un file ogni tanto, su Google Drive, resta comunque una copia in più.")
                    else tr("I dati vivono solo su questo telefono. Esporta un file ogni tanto e tienilo su Google Drive o sul computer: è l'unico modo per non perderli se l'app viene disinstallata."),
                    color = PeakTheme.textSecondary, size = 12,
                )

                SettingsButton(tr("Esporta backup"), SettingsIcons.upload, onExport, primary = true)
                SettingsButton(tr("Importa backup"), SettingsIcons.download, onImport, primary = false)

                SettingsDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("Backup automatico"), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                        Hint(tr("Non ancora disponibile su Android"))
                    }
                    Icon(SettingsIcons.folder, null, Modifier.size(20.dp), tint = PeakTheme.textTertiary)
                }
            }
        }
    }
}

private fun translate(context: android.content.Context, text: String) = if (text.isEmpty()) text else context.tr(text)
