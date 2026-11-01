package com.gabrieleforghieri.peak.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.tr

/** Le scelte dell'obiettivo: quante volte a settimana, come si chiama, che cosa vuol dire (`options` di `GoalPickerView`). */
private data class GoalOption(val n: Int, val name: String, val text: String)

private val options = listOf(
    GoalOption(2, "Leggero", "Per cominciare, senza fretta."),
    GoalOption(3, "Regolare", "Il passo giusto per crescere."),
    GoalOption(4, "Impegnato", "Una routine seria."),
    GoalOption(5, "Intenso", "Quasi tutti i giorni."),
    GoalOption(6, "Da campione", "Per chi vive in palestra."),
)

/** Quale obiettivo è scelto all'apertura: quello di adesso, o (la prima volta) il consigliato fra 2 e 6 (`onAppear` di `GoalPickerView`). */
fun initialGoalPick(current: Int, suggested: Int): Int = if (current > 0) current else maxOf(2, minOf(6, suggested))

/**
 * **Firma condivisa** (iPhone: `Features/Root/GoalPickerView.swift`). Proprietario: l'area **start**; la firma non cambia.
 * Usata da: workouts (cambiare l'obiettivo), start (`GoalOverlay`, la prima volta, a tutto schermo).
 *
 * Sceglie quanti allenamenti a settimana. Salva `weeklyGoal` da sé; `onClose` parte a foglio chiuso. `current` = l'obiettivo di adesso
 * (0 = prima volta), `suggested` = quello che l'app propone (`Stats.weeklyGoal`).
 */
@Composable
fun GoalPickerSheet(current: Int, suggested: Int, onClose: () -> Unit) {
    PeakSheet(onClose) { close ->
        GoalPickerContent(current, suggested, close, Modifier.fillMaxSize())
    }
}

/**
 * La scelta dell'obiettivo, a tutto schermo (`GoalPickerView`). `current > 0` = si può annullare. `onClose` parte dopo il salvataggio o
 * l'annullamento; la prima volta non fa niente (la schermata sparisce da sé, perché l'obiettivo non è più zero).
 */
@Composable
fun GoalPickerContent(current: Int, suggested: Int, onClose: () -> Unit, modifier: Modifier = Modifier, initialPick: Int = initialGoalPick(current, suggested)) {
    val accent = LocalAccent.current
    val update = rememberUpdateSettings()
    val haptics = LocalApp.current.haptics
    var pick by remember { mutableIntStateOf(initialPick) }
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(StartIcons.mountain, null, Modifier.padding(top = 36.dp).size(60.dp).shadow(18.dp, CircleShape, ambientColor = accent.copy(alpha = 0.5f), spotColor = accent.copy(alpha = 0.5f), clip = false), tint = accent)
            Text(
                tr("Quanti allenamenti a settimana?"),
                style = PeakTheme.rounded(28, FontWeight.ExtraBold).copy(letterSpacing = (-0.8f).sp), color = PeakTheme.textPrimary, textAlign = TextAlign.Center,
            )
            Text(
                tr("Scegli un obiettivo che puoi mantenere. Lo cambi quando vuoi."),
                style = PeakTheme.rounded(15, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
            )
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (o in options) GoalRow(o, pick == o.n) { pick = o.n; haptics.tap() }
            }
            PrimaryButton(
                tr("Continua"),
                { update { copy(weeklyGoal = pick) }; haptics.success(); onClose() },
                Modifier.widthIn(max = 520.dp).padding(top = 14.dp),
            )
        }
        if (current > 0) {
            Text(
                tr("Annulla"), Modifier.padding(20.dp).pressable(onClick = onClose),
                style = PeakTheme.rounded(15, FontWeight.SemiBold), color = accent,
            )
        }
    }
}

@Composable
private fun GoalRow(o: GoalOption, on: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .background(if (on) accent.copy(alpha = 0.12f) else PeakTheme.card, shape)
            .border(2.dp, if (on) accent else PeakTheme.stroke, shape)
            .semantics { selected = on }
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (k in 0 until 6) Box(Modifier.width(7.dp).size(7.dp, 22.dp).background(if (k < o.n) accent else Color.White.copy(alpha = 0.14f), RoundedCornerShape(3.dp)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("%lld a settimana", o.n), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary)
            Text("${tr(o.name)} · ${tr(o.text)}", style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary)
        }
        Box(Modifier.size(22.dp).border(2.dp, if (on) accent else PeakTheme.stroke, CircleShape).padding(5.dp).background(if (on) accent else Color.Transparent, CircleShape))
    }
}
