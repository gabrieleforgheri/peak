package com.gabrieleforghieri.peak.ui.friends

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.Motion
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.drawIn
import com.gabrieleforghieri.peak.design.growUp
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.friends.social.NewsEvent
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr

// I pezzi di Amici che le schermate condividono (`FriendsView.swift`: ProgressBar, Podium, NewsRow) e i pulsanti con l'icona.

/** Il pulsante principale con un'icona davanti (`Label` + `PrimaryButtonStyle`). `text` è una chiave di traduzione. */
@Composable
fun IconPrimaryButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalAccent.current) {
    Row(
        modifier.fillMaxWidth().background(tint, RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = PeakTheme.bg)
        Text(tr(text), Modifier.padding(start = 8.dp), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg)
    }
}

/** Il pulsante secondario con un'icona davanti. */
@Composable
fun IconSecondaryButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = PeakTheme.textPrimary) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape).pressable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(tr(text), Modifier.padding(start = 8.dp), style = PeakTheme.rounded(15), color = tint)
    }
}

/** Un testo discreto di una sezione vuota o in errore (`note` / il testo grigio di `FriendsView`). */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, centered: Boolean = true) {
    Text(
        text, modifier.fillMaxWidth(), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
}

/** Una barra orizzontale piena per la frazione data (`ProgressBar`). */
@Composable
fun ProgressBar(fraction: Double, tint: Color, modifier: Modifier = Modifier) {
    // cambiando metrica la barra scorre
    val shown by animateFloatAsState(fraction.coerceIn(0.0, 1.0).toFloat(), Motion.out(Motion.ENTER), label = "bar")
    BoxWithConstraints(modifier.fillMaxWidth().height(10.dp)) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(PeakTheme.cardStrong, CircleShape))
        Box(Modifier.width(maxOf(6.dp, maxWidth * shown)).height(10.dp).drawIn().background(tint, CircleShape))
    }
}

/** Una voce del podio (`PodiumEntry`): `amount` è il valore numerico, per l'altezza della barra. */
data class PodiumEntry(val id: Int, val name: String, val initial: String, val value: String, val amount: Double, val isMe: Boolean)

private fun podiumColors(rank: Int): Pair<Color, Color> = when (rank) {
    1 -> Color(0xFFFFE69A) to Color(0xFFC9921A)
    2 -> Color(0xFFF4F6FA) to Color(0xFF8F98A8)
    else -> Color(0xFFF1B27A) to Color(0xFF94562A)
}

/** I primi tre sul podio: il primo al centro, il più alto (`Podium`). */
@Composable
fun Podium(entries: List<PodiumEntry>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    // l'ordine sul podio è 2°, 1°, 3°
    val slots = listOf(1, 0, 2).filter { it in entries.indices }.map { entries[it] to it + 1 }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        for ((entry, rank) in slots) {
            val (hi, lo) = podiumColors(rank)
            // proporzionale al primo, con un minimo perché la barra contenga ancora il numero
            val height = maxOf(28.0, 112 * FriendsLogic.barFraction(entry.amount, entries.first().amount)).dp
            Column(
                Modifier.weight(1f, fill = false).widthIn(max = 120.dp).pressable { if (!entry.isMe) onPick(entry.id) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(48.dp).background(Brush.radialGradient(listOf(hi, lo), center = androidx.compose.ui.geometry.Offset(14f, 12f), radius = 100f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(entry.initial.take(1).uppercase(), style = PeakTheme.rounded(20, FontWeight.ExtraBold), color = Color(0xFF1A1A1A)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (entry.isMe) Icon(FriendsIcons.star, null, Modifier.size(11.dp), tint = accent)
                    Text(
                        entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = PeakTheme.rounded(14, FontWeight.Bold),
                        color = if (entry.isMe) accent else PeakTheme.textPrimary,
                    )
                }
                Text(entry.value, style = PeakTheme.tabular(13, FontWeight.Medium), color = PeakTheme.textSecondary)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = height).growUp(rank)
                        .background(Brush.verticalGradient(listOf(hi, lo)), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter,
                ) { Text("$rank", style = PeakTheme.rounded(24, FontWeight.Black), color = Color.Black.copy(alpha = 0.55f)) }
            }
        }
    }
}

/** Una riga di novità: chi, su cosa, quanto. Anche nella scheda Amici (`NewsRow`). */
@Composable
fun NewsRow(event: NewsEvent, unit: WeightUnit, modifier: Modifier = Modifier) {
    val italian = Lang.isItalian()
    val today = tr("Oggi")
    val yesterday = tr("Ieri")
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "@${event.username} · ${Catalog.entry(event.catalog)?.name(italian) ?: event.name}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary,
            )
            Text(FriendsLogic.relativeDay(event.date, today, yesterday), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(Fmt.setLine(event.weight, event.reps, unit), style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary)
            if (event.passedYou) Text(tr("Ti ha superato"), style = PeakTheme.rounded(11, FontWeight.SemiBold), color = PeakTheme.negative)
        }
    }
}
