package com.gabrieleforghieri.peak.ui.machines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ImageStore
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.ui.start.TourTargets
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// I pezzi di interfaccia che l'area Macchinari usa in più schermate. Quelli generali (vetro, bottoni, chip del gruppo) sono in `design/`.

/** Una pillola di scelta (il `stepChip` dell'editor e i filtri): attiva = tinta piena in trasparenza. */
@Composable
fun ChoiceChip(title: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalAccent.current, verbatim: Boolean = false) {
    Text(
        if (verbatim) title else tr(title),
        modifier.background(if (on) tint.copy(alpha = 0.22f) else PeakTheme.card, RoundedCornerShape(10.dp)).pressable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        style = PeakTheme.rounded(13, FontWeight.Bold), color = if (on) tint else PeakTheme.textSecondary, textAlign = TextAlign.Center, maxLines = 1,
    )
}

/** Una riga con titolo, spiegazione e interruttore (`Toggle` con un `VStack` di due testi). */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val accent = LocalAccent.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr(title), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
            if (subtitle != null) Text(tr(subtitle), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PeakTheme.bg, checkedTrackColor = accent, uncheckedThumbColor = PeakTheme.textSecondary,
                uncheckedTrackColor = PeakTheme.cardStrong, uncheckedBorderColor = PeakTheme.stroke,
            ),
        )
    }
}

/** Il titolo grande di una scheda (il `navigationTitle` grande di iOS) con un controllo a destra. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), Modifier.weight(1f), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
        trailing()
    }
}

// ---- foto dal Photo Picker -----------------------------------------------------------------

/**
 * Apre il selettore di foto di sistema (Photo Picker: non chiede permessi) e dà i byte già ridotti (`ImageStore.prepare`, lato lungo
 * 640 px, JPEG al 75 %) con l'orientamento EXIF applicato. Prende il posto di `PhotosPicker` e `CameraPicker` dell'iPhone.
 */
@Composable
fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { decodePhoto(context, uri) }?.let(onPicked) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** La foto scelta, letta con un lato massimo di ~2000 px (non si tiene in memoria quella intera), raddrizzata e ridotta. */
private fun decodePhoto(context: Context, uri: Uri): ByteArray? = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2000) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    decoded?.let { ImageStore.prepare(upright(it, exifOrientation(bytes))) }
} catch (_: Exception) {
    null
}

private fun exifOrientation(bytes: ByteArray): Int = try {
    android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
} catch (_: Exception) {
    android.media.ExifInterface.ORIENTATION_NORMAL
}

private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
    val m = Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}

// ---- suggerimenti della prima volta ---------------------------------------------------------

/**
 * Un suggerimento sul controllo `target` (segnato con `Modifier.tourTarget`), dentro la schermata stessa (`machineTip` di
 * `TourOverlay.swift`). Va messo in un `Box` che riempie la schermata, sopra il resto: non blocca i tocchi, solo l'anello attorno al
 * controllo e un riquadro con «Ok» (passo dopo passo) o «Fine» (ultimo), più «Salta» per spegnerli per sempre.
 */
@Composable
fun MachineTip(target: String, show: Boolean, title: String, text: String, onNext: () -> Unit, modifier: Modifier = Modifier, last: Boolean = false) {
    val rect = TourTargets.rects[target]
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val accent = LocalAccent.current
    val update = rememberUpdateSettings()
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); size = it.size }) {
        if (!show || rect == null || size == IntSize.Zero) return@Box
        val density = LocalDensity.current
        val pad = with(density) { 8.dp.toPx() }
        val left = rect.left - origin.x - pad
        val top = rect.top - origin.y - pad
        val width = rect.width + 2 * pad
        val height = rect.height + 2 * pad
        val below = top + height / 2 < size.height / 2
        Box(
            Modifier.offset(with(density) { left.toDp() }, with(density) { top.toDp() })
                .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .border(6.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .border(2.dp, accent, RoundedCornerShape(16.dp)),
        )
        val gap = with(density) { 12.dp.toPx() }
        Column(
            Modifier.align(if (below) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    top = if (below) with(density) { (top + height + gap).toDp() } else 0.dp,
                    bottom = if (below) 0.dp else with(density) { (size.height - top + gap).toDp() },
                    start = 16.dp, end = 16.dp,
                )
                .widthIn(max = 440.dp)
                .shadow(20.dp, RoundedCornerShape(18.dp))
                .background(Color(0xFF151A28), RoundedCornerShape(18.dp))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tr(title), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text(tr(text), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Salta"), Modifier.pressable { update { copy(machineTipsSeen = true) } }.padding(vertical = 6.dp),
                    style = PeakTheme.rounded(13, FontWeight.Bold), color = accent,
                )
                Box(Modifier.weight(1f))
                PrimaryButton(tr(if (last) "Fine" else "Ok"), { if (last) update { copy(machineTipsSeen = true) }; onNext() }, fullWidth = false)
            }
        }
    }
}
