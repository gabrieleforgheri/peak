package com.gabrieleforghieri.peak.ui.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.ui.friends.social.SocialStore
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.CancellationException

/**
 * L'invito (`Features/Friends/InviteSheet.swift`): un link monouso, mostrato come QR e condivisibile. Chi lo apre diventa amico subito.
 * Il codice lo dà il server (`POST /api/inviti`); il QR lo disegna [Qr], senza librerie.
 */
@Composable
fun InviteSheet(social: SocialStore, onDismiss: () -> Unit) {
    PeakSheet(onDismiss) { close ->
        InviteContent(social, onClose = close)
    }
}

/** Il contenuto del foglio, senza il foglio (i fogli stanno in una finestra a parte e non entrano negli screenshot). */
@Composable
fun InviteContent(social: SocialStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalApp.current.haptics
    var link by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val api = social.client()
        if (api == null) { failed = true; return@LaunchedEffect }
        try { link = api.inviteLink(api.createInvite()) } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    }
    val matrix = remember(link) { link?.let { runCatching { Qr.matrix(it) }.getOrNull() } }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Chiudi"), Modifier.pressable(onClick = onClose).padding(vertical = 6.dp), style = PeakTheme.rounded(16, FontWeight.Medium), color = LocalAccent.current)
            Text(tr("Invita un amico"), Modifier.weight(1f), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            // lo spazio di «Chiudi», perché il titolo stia al centro
            Box(Modifier.padding(horizontal = 24.dp))
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                tr("Fagli inquadrare il QR, oppure mandagli il link. Vale una volta sola e per sette giorni."),
                style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
            )
            val url = link
            if (url != null && matrix != null) {
                val description = tr("Codice QR dell'invito")
                Canvas(
                    Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }
                        .background(Color.White, RoundedCornerShape(18.dp)).padding(14.dp),
                ) {
                    // due moduli di margine bianco attorno al disegno; i moduli cadono sui pixel interi, senza sfocare
                    val quiet = 2
                    val cells = matrix.size + quiet * 2
                    val cell = Math.floor((size.minDimension / cells).toDouble()).toFloat()
                    val origin = (size.minDimension - cell * cells) / 2
                    for (y in matrix.indices) for (x in matrix.indices) if (matrix[y][x]) {
                        drawRect(Color.Black, Offset(origin + (x + quiet) * cell, origin + (y + quiet) * cell), Size(cell, cell))
                    }
                }
                IconPrimaryButton(FriendsIcons.share, "Condividi", { share(context, url) })
                IconSecondaryButton(FriendsIcons.copy, "Copia il link", {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Peak", url))
                    haptics.success()
                })
            } else if (failed || (url != null && matrix == null)) {
                Text(tr("Serve la connessione. Riprova."), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textTertiary)
            } else {
                CircularProgressIndicator(Modifier.padding(top = 40.dp), color = LocalAccent.current)
            }
        }
    }
}

/** Il foglio di condivisione di Android con il messaggio e il link (`ShareLink`). */
private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.tr("Aggiungimi su Peak:") + "\n" + url)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
