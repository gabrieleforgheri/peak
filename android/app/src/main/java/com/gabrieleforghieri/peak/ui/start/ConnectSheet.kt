package com.gabrieleforghieri.peak.ui.start

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.SecondaryButton
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.sync.ConnectFlow.Stage
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch

/**
 * **Firma condivisa** (iPhone: `ConnectSheet` in `Features/Root/ConnectFlowView.swift`). Proprietario: l'area **start**; la firma non cambia.
 * Usata da: machines (banner della lista), friends (banner della lista), settings (Account yG).
 *
 * Il percorso per collegare l'account yG. Si chiude da solo a collegamento fatto o annullato; `onDismiss` parte a foglio chiuso.
 * Su iPhone il foglio non si chiude col gesto (`interactiveDismissDisabled`); qui un foglio non può impedirlo: se lo si tira giù a
 * metà login o alla scelta, il collegamento si annulla (a dati già in viaggio no: finisce da solo).
 */
@Composable
fun ConnectSheet(onDismiss: () -> Unit) {
    val connect = LocalApp.current.sync.connect
    PeakSheet(onDismiss = {
        val s = connect.stage.value
        if (s !is Stage.Done && !(s is Stage.Working && s.message != LOGIN_MESSAGE)) connect.cancel()
        onDismiss()
    }) { close ->
        ConnectFlowContent(onFinish = close, onCancel = close, modifier = Modifier.fillMaxSize())
    }
}

/** Il messaggio mentre si aspetta la pagina di yG (`ConnectFlow.begin`): l'unico momento in cui si può annullare senza danni. */
private const val LOGIN_MESSAGE = "Accesso a yG…"

/**
 * Il collegamento a yG, passo dopo passo (`ConnectFlowView`): login, poi (se serve) la scelta su cosa fare dei dati. Sta dentro la
 * pagina di benvenuto e dentro il foglio che si apre da banner e impostazioni. La logica è `sync/ConnectFlow.kt` (`app.sync.connect`).
 *
 * `firstRun`: dal primo avvio, niente scelte sui dati (né backup da importare); account vuoto = si parte da zero, con i dati di
 * questo telefono se ce ne sono.
 */
@Composable
fun ConnectFlowContent(onFinish: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, firstRun: Boolean = false) {
    val app = LocalApp.current
    val connect = app.sync.connect
    val scope = app.mainScope   // la sincronizzazione gira sul thread principale e non deve morire con il foglio
    val context = LocalContext.current
    val stage by connect.stage.collectAsState()
    var started by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun login() {
        notice = null
        scope.launch {
            val url = connect.begin() ?: return@launch
            try {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            } catch (_: ActivityNotFoundException) {
                // nessun browser sul telefono
                connect.cancel()
                notice = context.tr("Non trovo un browser per il login.")
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        notice = null
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            scope.launch { connect.importBackup(bytes) }
        } catch (e: Exception) {
            notice = e.message
        }
    }

    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        connect.dismiss()
        if (connect.stage.value is Stage.Idle) login()
    }
    LaunchedEffect(stage) {
        val s = stage
        if (s is Stage.Done) { onFinish(); connect.dismiss() }
        if (firstRun && s is Stage.EmptyServer) scope.launch { if (s.hasLocalData) connect.uploadLocalData() else connect.startEmpty() }
    }

    ConnectFlowBody(
        stage = if (started) stage else Stage.Working(LOGIN_MESSAGE), firstRun = firstRun, notice = notice, modifier = modifier,
        onRetry = { login() },
        onCancel = { connect.cancel(); onCancel() },
        onUploadLocal = { scope.launch { connect.uploadLocalData() } },
        onStartEmpty = { scope.launch { connect.startEmpty() } },
        onImport = { importer.launch(arrayOf("*/*")) },
        onMerge = { scope.launch { connect.merge() } },
        onUseServer = { scope.launch { connect.useServerData() } },
    )
}

/** Il corpo senza logica: ogni stato del collegamento si disegna da solo (le prove di screenshot lo passano a mano). */
@Composable
fun ConnectFlowBody(
    stage: Stage,
    firstRun: Boolean,
    notice: String?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
    onUploadLocal: () -> Unit = {},
    onStartEmpty: () -> Unit = {},
    onImport: () -> Unit = {},
    onMerge: () -> Unit = {},
    onUseServer: () -> Unit = {},
) {
    val accent = LocalAccent.current
    Column(
        modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (stage) {
            is Stage.Idle -> {
                Message(PeakIcons.me, "Accesso annullato", "Il collegamento non è stato fatto. Puoi riprovare quando vuoi.")
                notice?.let { ErrorText(it) }
                RetryAndCancel(onRetry, onCancel)
            }
            is Stage.Working -> {
                CircularProgressIndicator(Modifier.size(36.dp), color = accent, strokeWidth = 3.dp)
                Text(tr(stage.message), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
                // Android non sa quando la pagina di login si chiude senza una risposta: un modo per uscire c'è sempre
                if (stage.message == LOGIN_MESSAGE) CancelButton(onCancel)
            }
            is Stage.EmptyServer -> if (firstRun) {
                CircularProgressIndicator(Modifier.size(36.dp), color = accent, strokeWidth = 3.dp)
            } else {
                Message(
                    StartIcons.tray, "Il tuo account Peak è vuoto",
                    if (stage.hasLocalData) "Puoi portarci i dati di questo telefono oppure partire da un backup."
                    else "Parti da zero (i macchinari li aggiungi cercando nel catalogo) o importa un backup.",
                )
                if (stage.hasLocalData) Choice("Carica i dati di questo telefono", primary = true, onClick = onUploadLocal)
                else Choice("Parti da zero", primary = true, onClick = onStartEmpty)
                Choice("Importa un backup", primary = false, onClick = onImport)
                notice?.let { ErrorText(it) }
                CancelButton(onCancel)
            }
            is Stage.Conflict -> {
                Message(
                    StartIcons.merge, "Ci sono dati qui e su Peak",
                    "Prima di continuare una copia dei dati di questo telefono resta nella cartella Download/Peak (Peak-prima-del-collegamento.json).",
                )
                Choice("Unisci", primary = true, detail = "Aggiunge i dati di questo telefono a quelli di Peak. I macchinari con lo stesso nome si uniscono.", onClick = onMerge)
                Choice("Usa i dati del server", primary = false, detail = "Questo telefono riparte dai dati di Peak.", onClick = onUseServer)
                CancelButton(onCancel)
            }
            is Stage.Failed -> {
                Message(PeakIcons.warning, "Collegamento non riuscito", stage.message)
                RetryAndCancel(onRetry, onCancel)
            }
            is Stage.Done -> CircularProgressIndicator(Modifier.size(28.dp), color = accent, strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun RetryAndCancel(onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Choice("Riprova", primary = true, onClick = onRetry)
        CancelButton(onCancel)
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    Text(
        tr("Annulla"), Modifier.padding(top = 4.dp).pressable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 8.dp),
        style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textTertiary,
    )
}

@Composable
private fun Message(icon: ImageVector, title: String, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = LocalAccent.current)
        Text(tr(title), style = PeakTheme.rounded(22, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
        Text(tr(text), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.negative, textAlign = TextAlign.Center)
}

@Composable
private fun Choice(title: String, primary: Boolean, detail: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (primary) PrimaryButton(tr(title), onClick) else SecondaryButton(tr(title), onClick)
        if (detail != null) Text(tr(detail), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary, textAlign = TextAlign.Center)
    }
}
