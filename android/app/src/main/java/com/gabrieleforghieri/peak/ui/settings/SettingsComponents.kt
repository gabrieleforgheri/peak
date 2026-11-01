package com.gabrieleforghieri.peak.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.SecondaryButton
import com.gabrieleforghieri.peak.design.SheetDetent
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.util.tr

// I pezzi che le sottopagine delle Impostazioni condividono: `SettingsPage`, interruttori, stepper, segmenti, pulsanti con icona, fogli di scelta.

/** `SettingsPage`: scorre, col distacco di 14 tra le schede. */
@Composable
fun SettingsPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

/** Il titolo della schermata e il pulsante a sinistra (indietro nelle sottopagine) o «Fine» a destra (la radice, come `ToolbarItem`). */
@Composable
fun SettingsTopBar(title: String, onBack: (() -> Unit)?, onDone: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
        if (onBack != null) {
            Box(
                Modifier.align(Alignment.CenterStart).size(44.dp).pressable(onClick = onBack).semantics { role = Role.Button; contentDescription = "Indietro" },
                contentAlignment = Alignment.Center,
            ) { Icon(SettingsIcons.back, null, Modifier.size(28.dp), tint = LocalAccent.current) }
        }
        Text(
            tr(title), Modifier.align(Alignment.Center), style = PeakTheme.rounded(if (onBack != null) 17 else 20, FontWeight.Bold),
            color = PeakTheme.textPrimary, maxLines = 1,
        )
        if (onDone != null) {
            Text(
                tr("Fine"), Modifier.align(Alignment.CenterEnd).pressable(onClick = onDone).padding(horizontal = 10.dp, vertical = 10.dp),
                style = PeakTheme.rounded(16, FontWeight.Bold), color = LocalAccent.current,
            )
        }
    }
}

/** Una riga con titolo e, sotto, la spiegazione piccola: la base di interruttori e stepper (`VStack { Text; Text }`). */
@Composable
fun TitleBlock(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tr(title), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
        if (subtitle != null) Text(tr(subtitle), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
    }
}

/** `Toggle` con titolo e spiegazione, tinto con l'accento. */
@Composable
fun SettingsSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleBlock(title, subtitle, Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = accent, checkedBorderColor = accent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.8f), uncheckedTrackColor = Color.White.copy(alpha = 0.16f), uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** `Stepper(value:in:step:) { Text }`: il testo a sinistra, «−» e «+» a destra; si ferma agli estremi. */
@Composable
fun SettingsStepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textPrimary)
        Row(Modifier.background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape)) {
            StepperHalf(PeakIcons.minus, "Meno", enabled = value - step >= range.first) { onChange(maxOf(range.first, value - step)) }
            Box(Modifier.width(1.dp).height(30.dp).background(PeakTheme.stroke).align(Alignment.CenterVertically))
            StepperHalf(PeakIcons.plus, "Più", enabled = value + step <= range.last) { onChange(minOf(range.last, value + step)) }
        }
    }
}

@Composable
private fun StepperHalf(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 48.dp, height = 34.dp).alpha(if (enabled) 1f else 0.3f)
            .clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp), tint = PeakTheme.textPrimary) }
}

/** `Picker` con `.segmented`: le opzioni in fila, quella scelta evidenziata. `options` sono (valore, testo già tradotto). */
@Composable
fun <T> SegmentedPicker(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(9.dp)
    Row(modifier.fillMaxWidth().background(PeakTheme.cardStrong, shape).padding(2.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Text(
                label,
                Modifier.weight(1f)
                    .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { if (!on) onSelect(value) }
                    .semantics { this.selected = on; role = Role.RadioButton }
                    .padding(vertical = 7.dp),
                style = PeakTheme.rounded(13, if (on) FontWeight.Bold else FontWeight.Medium),
                color = if (on) PeakTheme.textPrimary else PeakTheme.textSecondary, textAlign = TextAlign.Center, maxLines = 1,
            )
        }
    }
}

/** Il filetto tra due gruppi di una scheda (`Divider().overlay(Theme.stroke)`). */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
}

/** Testo piccolo di spiegazione. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = PeakTheme.textTertiary, size: Int = 11) {
    Text(text, modifier, style = PeakTheme.rounded(size, FontWeight.Medium), color = color)
}

/** Il pulsante con icona e titolo (`Label` + `PrimaryButtonStyle`/`SecondaryButtonStyle`); disabilitato si attenua e non risponde. */
@Composable
fun SettingsButton(text: String, icon: ImageVector, onClick: () -> Unit, primary: Boolean, enabled: Boolean = true) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    val content = if (primary) PeakTheme.bg else PeakTheme.textPrimary
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
            .then(if (primary) Modifier.background(accent, shape) else Modifier.background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape))
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(vertical = if (primary) 15.dp else 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = content)
        Text(text, style = PeakTheme.rounded(if (primary) 16 else 15, if (primary) FontWeight.Bold else FontWeight.SemiBold), color = content)
    }
}

/** Una riga di azione senza sfondo, a tutta larghezza (`Button { Label } .buttonStyle(.plain)`). */
@Composable
fun SettingsTextAction(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(text, style = PeakTheme.rounded(14, FontWeight.SemiBold), color = tint)
    }
}

/** Un'azione di [ChoiceSheet]. */
class SheetAction(val label: String, val primary: Boolean = false, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * `confirmationDialog` con titolo, messaggio e più azioni: un foglio basso (`PeakSheet`), come `ConfirmSheet` ma con il testo sotto
 * il titolo. L'azione scelta parte a foglio chiuso. `title`, `message` e i `label` sono chiavi di traduzione.
 */
@Composable
fun ChoiceSheet(title: String, message: String?, actions: List<SheetAction>, onDismiss: () -> Unit, cancel: String = "Annulla") {
    var chosen by remember { mutableStateOf<SheetAction?>(null) }
    PeakSheet(onDismiss = { onDismiss(); chosen?.onClick?.invoke() }, detent = SheetDetent.Height(210.dp)) { close ->
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr(title), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            if (message != null) Text(tr(message), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (action in actions) {
                    val go = { chosen = action; close() }
                    when {
                        action.primary -> PrimaryButton(tr(action.label), go)
                        action.destructive -> SecondaryButton(tr(action.label), go, tint = PeakTheme.negative)
                        else -> SecondaryButton(tr(action.label), go)
                    }
                }
                SecondaryButton(tr(cancel), close)
            }
        }
    }
}

/** `.alert(title) { Button("Ok") } message:`. */
@Composable
fun AlertSheet(title: String, message: String, onDismiss: () -> Unit) {
    PeakSheet(onDismiss, detent = SheetDetent.Height(200.dp)) { close ->
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr(title), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center)
            Text(message, style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            SecondaryButton(tr("Ok"), close)
        }
    }
}
