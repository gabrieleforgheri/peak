package com.gabrieleforghieri.peak.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.util.tr
import java.time.Instant
import java.time.ZoneId

// I componenti di base di `Design/Components.swift`. Le icone sono vettoriali (`ImageVector` o risorse), mai emoji.

/** Chip del gruppo muscolare: l'icona del gruppo e, se non è compatto, il nome in maiuscolo. */
@Composable
fun MuscleGroupChip(group: MuscleGroup, modifier: Modifier = Modifier, compact: Boolean = false) {
    val tint = group.tint
    Row(
        modifier
            .background(tint.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 5.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PeakIcons.group(group), null, Modifier.size(if (compact) 12.dp else 14.dp), tint = tint)
        if (!compact) Text(tr(group.title).uppercase(), style = PeakTheme.rounded(11, FontWeight.Bold).copy(letterSpacing = 0.6.sp), color = tint)
    }
}

/** Il badge «PR» dei nuovi massimali. */
@Composable
fun PRBadge(modifier: Modifier = Modifier, text: String = "PR") {
    Text(
        text, modifier.background(PeakTheme.positive, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
        style = PeakTheme.rounded(10, FontWeight.ExtraBold).copy(letterSpacing = 0.8.sp), color = PeakTheme.bg,
    )
}

/** Variazione percentuale colorata, con freccia. */
@Composable
fun DeltaBadge(percent: Double, modifier: Modifier = Modifier) {
    val positive = percent >= 0
    val color = if (positive) PeakTheme.positive else PeakTheme.negative
    Row(
        modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PeakIcons.arrow(positive), null, Modifier.size(11.dp), tint = color)
        Text(Fmt.delta(percent), style = PeakTheme.rounded(11, FontWeight.Bold), color = color)
    }
}

/** Tessera statistica: icona, titolo in maiuscolo, valore grande, didascalia. */
@Composable
fun StatTile(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier, caption: String? = null, tint: Color = Color.White) {
    GlassCard(modifier, padding = 14.dp, radius = PeakTheme.radiusSmall) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(13.dp), tint = tint.copy(alpha = 0.9f))
            Text(tr(title).uppercase(), style = PeakTheme.rounded(10, FontWeight.Bold).copy(letterSpacing = 0.7.sp), color = tint.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(8.dp))
        Text(value, style = PeakTheme.display(24), color = PeakTheme.textPrimary, maxLines = 1)
        if (caption != null) Text(caption, Modifier.padding(top = 4.dp), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary, maxLines = 1)
    }
}

// ---- stepper -------------------------------------------------------------------------------

@Composable
private fun StepperButton(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(52.dp).background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = tint) }
}

/**
 * Stepper del carico. Il valore è sempre in kg; la conversione nell'unità scelta avviene solo per la visualizzazione e per
 * l'input. `step` è il passo del macchinario in kg: nell'unità scelta diventa il passo nativo (2,5 kg → 5 lb); `nativeStep` è
 * già nell'unità scelta (la pesata corporea).
 */
@Composable
fun WeightStepper(kilograms: Double, onChange: (Double) -> Unit, step: Double, unit: WeightUnit, modifier: Modifier = Modifier, tint: Color = Color.White, nativeStep: Double? = null) {
    val view = LocalView.current
    fun bump(direction: Int) {
        val next = unit.fromKilograms(kilograms) + direction * (nativeStep ?: unit.displayStep(step))
        onChange(unit.toKilograms(maxOf(0.0, Math.round(next * 100) / 100.0)))
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    }
    var text by remember(kilograms, unit) { mutableStateOf(Fmt.weight(kilograms, unit, includeSymbol = false)) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        StepperButton(PeakIcons.minus, tint) { bump(-1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(
                text, { new ->
                    text = new
                    new.replace(',', '.').toDoubleOrNull()?.let { onChange(maxOf(0.0, unit.toKilograms(it))) }
                },
                textStyle = PeakTheme.display(38).copy(color = PeakTheme.textPrimary, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                cursorBrush = SolidColor(tint),
            )
            Text(unit.symbol, style = PeakTheme.rounded(12), color = PeakTheme.textTertiary)
        }
        StepperButton(PeakIcons.plus, tint) { bump(1) }
    }
}

/** Stepper delle ripetizioni, con scorciatoie ai valori più usati. */
@Composable
fun RepsStepper(reps: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, tint: Color = Color.White, quickValues: List<Int> = listOf(1, 3, 5, 8, 10, 12)) {
    val view = LocalView.current
    fun tap() = view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StepperButton(PeakIcons.minus, tint) { onChange(maxOf(1, reps - 1)); tap() }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$reps", style = PeakTheme.display(38), color = PeakTheme.textPrimary)
                Text(tr(if (reps == 1) "ripetizione" else "ripetizioni"), style = PeakTheme.rounded(12), color = PeakTheme.textTertiary)
            }
            StepperButton(PeakIcons.plus, tint) { onChange(minOf(50, reps + 1)); tap() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in quickValues) {
                val on = reps == value
                Text(
                    "$value",
                    Modifier.weight(1f)
                        .background(if (on) tint.copy(alpha = 0.22f) else PeakTheme.card, RoundedCornerShape(10.dp))
                        .clickable(remember { MutableInteractionSource() }, null) { onChange(value); tap() }
                        .padding(vertical = 7.dp),
                    style = PeakTheme.rounded(13, FontWeight.Bold), color = if (on) tint else PeakTheme.textSecondary, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---- pulsanti ------------------------------------------------------------------------------

/** Il pulsante principale: tinta piena, testo scuro. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalAccent.current, fullWidth: Boolean = true) {
    Box(
        modifier.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .background(tint, RoundedCornerShape(16.dp)).pressable(onClick = onClick)
            .padding(vertical = 15.dp, horizontal = if (fullWidth) 0.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg) }
}

/** Il pulsante secondario: vetro con bordo. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = PeakTheme.textPrimary) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.fillMaxWidth().background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape).pressable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = PeakTheme.rounded(15), color = tint) }
}

// ---- intestazioni --------------------------------------------------------------------------

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title).uppercase(), Modifier.weight(1f), style = PeakTheme.rounded(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = PeakTheme.textTertiary)
        if (trailing != null) Text(tr(trailing), style = PeakTheme.rounded(11), color = PeakTheme.textTertiary)
    }
}

// ---- storici per mese ----------------------------------------------------------------------

/** Un mese di calendario (ora locale) con i suoi elementi. */
class MonthGroup<T>(val id: String, val date: Long, val items: List<T>) {
    /** "Ottobre 2026". */
    fun title(locale: java.util.Locale = java.util.Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(date).atZone(zone)
        val month = d.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        return "${month.replaceFirstChar { it.titlecase(locale) }} ${d.year}"
    }
}

/** Raggruppa per mese mantenendo l'ordine in ingresso: dal più recente se gli elementi lo sono (`byMonth` di Components.swift). */
fun <T> byMonth(items: List<T>, date: (T) -> Long, zone: ZoneId = ZoneId.systemDefault()): List<MonthGroup<T>> {
    val order = mutableListOf<String>()
    val firsts = HashMap<String, Long>()
    val buckets = LinkedHashMap<String, MutableList<T>>()
    for (item in items) {
        val ms = date(item)
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val key = "${d.year}-${d.monthValue}"
        if (key !in buckets) { order += key; firsts[key] = ms }
        buckets.getOrPut(key) { mutableListOf() } += item
    }
    return order.map { MonthGroup(it, firsts.getValue(it), buckets.getValue(it)) }
}

/** Intestazione di un mese: tocca per aprire o chiudere. */
@Composable
fun MonthHeader(title: String, count: String, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onToggle).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PeakIcons.chevronRight, null, Modifier.size(14.dp).rotate(if (open) 90f else 0f), tint = PeakTheme.textSecondary)
        Text(title, Modifier.weight(1f), style = PeakTheme.rounded(13, FontWeight.Bold), color = PeakTheme.textSecondary)
        Text(tr(count), style = PeakTheme.rounded(11), color = PeakTheme.textSecondary)
    }
}

/** I mesi uno sotto l'altro, il più recente aperto (se `openFirst`) e gli altri chiusi; con `dividers` un filetto fra le righe. */
@Composable
fun <T> MonthStack(items: List<T>, date: (T) -> Long, count: (Int) -> String, modifier: Modifier = Modifier, openFirst: Boolean = true, dividers: Boolean = false, row: @Composable (T) -> Unit) {
    var flipped by remember { mutableStateOf(setOf<String>()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        byMonth(items, date).forEachIndexed { index, group ->
            val open = (openFirst && index == 0) != (group.id in flipped)
            MonthHeader(group.title(), count(group.items.size), open, { flipped = if (group.id in flipped) flipped - group.id else flipped + group.id })
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.items.forEachIndexed { i, item ->
                        row(item)
                        if (dividers && i < group.items.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
                    }
                }
            }
        }
    }
}
