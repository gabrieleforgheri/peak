package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.figureMuscles
import com.gabrieleforghieri.peak.data.figureView
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.ExerciseCardBackground
import com.gabrieleforghieri.peak.design.ExerciseFigure
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.util.tr

// I pezzi che tornano in più schermate dell'area Allenamenti (`MiniStat`, le capsule di scelta, il campo di testo, i bottoni tondi…).

/** Un numero con la sua etichetta in riga (`MiniStat`). */
@Composable
fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        Text(value, style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
        Text(tr(label), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
    }
}

/** La capsula di scelta dei moduli (giorni, icone, gruppi): accesa con l'accento, spenta di vetro. */
@Composable
fun PickChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    val color = if (selected) accent else PeakTheme.textSecondary
    CompositionLocalProvider(LocalContentColor provides color) {
        Box(
            modifier
                .background(if (selected) accent.copy(alpha = 0.22f) else PeakTheme.card, CircleShape)
                .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else PeakTheme.stroke, CircleShape)
                .pressable(onClick = onClick)
                .semantics { role = Role.RadioButton; this.selected = selected }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
fun PickChipText(text: String) = Text(text, style = PeakTheme.rounded(13, FontWeight.SemiBold))

/** Un campo di testo di una riga, nello stile dell'app (il `TextField` dei moduli). `placeholder` è una chiave di traduzione. */
@Composable
fun PeakTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PeakTheme.rounded(17, FontWeight.SemiBold),
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    maxLines: Int = if (singleLine) 1 else 6,
    textAlign: TextAlign = TextAlign.Start,
) {
    Box(modifier) {
        if (value.isEmpty()) Text(tr(placeholder), style = style.copy(textAlign = textAlign), color = PeakTheme.textTertiary, modifier = Modifier.fillMaxWidth())
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = singleLine, maxLines = maxLines,
            textStyle = style.copy(color = PeakTheme.textPrimary, textAlign = textAlign),
            cursorBrush = SolidColor(LocalAccent.current),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        )
    }
}

/** Una riga con un interruttore (il `Toggle` dei moduli). `title` è una chiave di traduzione. */
@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LocalAccent.current, checkedThumbColor = PeakTheme.bg,
                uncheckedTrackColor = PeakTheme.cardStrong, uncheckedThumbColor = PeakTheme.textSecondary, uncheckedBorderColor = PeakTheme.stroke,
            ),
        )
    }
}

/**
 * La barra in cima a un foglio, al posto della `NavigationStack` con titolo e due tasti: «Annulla» a sinistra, il titolo, il tasto che
 * conferma a destra (se c'è). I testi sono chiavi di traduzione.
 */
@Composable
fun SheetBar(title: String, onCancel: () -> Unit, modifier: Modifier = Modifier, cancel: String = "Annulla", confirm: String? = null, confirmEnabled: Boolean = true, onConfirm: () -> Unit = {}) {
    val accent = LocalAccent.current
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr(cancel), Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null, onClick = onCancel).padding(vertical = 8.dp),
            style = PeakTheme.rounded(15, FontWeight.Medium), color = accent,
        )
        Text(tr(title), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1)
        Text(
            if (confirm != null) tr(confirm) else "",
            Modifier.weight(1f).then(if (confirm != null && confirmEnabled) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onConfirm) else Modifier).padding(vertical = 8.dp),
            style = PeakTheme.rounded(15, FontWeight.Bold), color = if (confirmEnabled) accent else PeakTheme.textTertiary, textAlign = TextAlign.End,
        )
    }
}

/** Il bottone tondo con un'icona (su, giù, togli, i tre puntini…). */
@Composable
fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, size: Int = 32, iconSize: Int = 14, tint: Color = PeakTheme.textSecondary) {
    Box(
        modifier.size(size.dp).background(PeakTheme.card, CircleShape)
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize.dp), tint = if (enabled) tint else tint.copy(alpha = 0.35f)) }
}

/** L'icona di un giorno della scheda, nel suo colore (`TemplateIconView`). */
@Composable
fun TemplateIconView(icon: String, name: String, modifier: Modifier = Modifier, size: Int = 20) {
    val resolved = com.gabrieleforghieri.peak.domain.TemplateIcon.resolved(icon, name)
    val tint = com.gabrieleforghieri.peak.domain.TemplateIcon.group(resolved)?.tint ?: PeakTheme.textPrimary
    Icon(WorkoutsIcons.template(resolved), null, modifier.size(size.dp), tint = tint)
}

/** La figura del macchinario con «Muscoli ↗» sotto: apre il dettaglio dei muscoli (sessione e righe dell'editor). */
@Composable
fun MuscleMapButton(exercise: ExerciseEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muscles = exercise.figureMuscles
    if (muscles.isEmpty()) return
    val tint = exercise.group.tint
    val view = when (exercise.figureView) { "front" -> AnatomyView.front; "back" -> AnatomyView.back; else -> null }
    val label = tr("Mappa muscolare")
    Column(
        modifier.pressable(onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ExerciseFigure(muscles, exercise.group, view = view, size = 68.dp)
        Row(
            Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Muscoli"), style = PeakTheme.rounded(11, FontWeight.SemiBold), color = tint)
            Icon(WorkoutsIcons.arrowUpRight, null, Modifier.size(9.dp), tint = tint)
        }
    }
}

/** Il titolo di una sezione con un pulsante a destra («Scheda» / «Nuova scheda»). */
@Composable
fun SectionHeaderAction(title: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tr(title).uppercase(), Modifier.weight(1f),
            style = PeakTheme.rounded(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = PeakTheme.textTertiary,
        )
        Text(
            tr(action), Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onAction).padding(vertical = 4.dp),
            style = PeakTheme.rounded(13, FontWeight.Bold), color = LocalAccent.current,
        )
    }
}

/** Un rettangolo di campo con titolo in maiuscolo sopra (`field` di `TemplateEditorView`). */
@Composable
fun LabeledField(title: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Number, placeholder: String = "") {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = PeakTheme.rounded(10, FontWeight.Bold).copy(letterSpacing = 0.6.sp), color = PeakTheme.textTertiary)
        Box(Modifier.fillMaxWidth().background(PeakTheme.card, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) {
            PeakTextField(value, onChange, placeholder, style = PeakTheme.tabular(16), keyboardType = keyboardType, capitalization = KeyboardCapitalization.None)
        }
    }
}

/** Il pulsante principale con un'icona davanti (`Label("Avvia allenamento", systemImage: "bolt.fill")` con `PrimaryButtonStyle`). */
@Composable
fun PrimaryLabelButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalAccent.current, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().background(if (enabled) tint else tint.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier).padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = PeakTheme.bg)
        Text(text, style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg)
    }
}

/** Il pulsante secondario con un'icona davanti (`SecondaryButtonStyle`): vetro con bordo. */
@Composable
fun SecondaryLabelButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = PeakTheme.textPrimary, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().background(PeakTheme.cardStrong, shape).border(1.dp, PeakTheme.stroke, shape)
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = if (enabled) tint else tint.copy(alpha = 0.4f))
        Text(text, style = PeakTheme.rounded(15), color = if (enabled) tint else tint.copy(alpha = 0.4f))
    }
}

/** Una riga che scorre di lato (la `ScrollView(.horizontal)` delle capsule). */
@Composable
fun Modifier.scrollsSideways(): Modifier = this.horizontalScroll(rememberScrollState())

/** La card di vetro di un macchinario con la sua foto (o il pittogramma) sfocata dietro (`GlassCard` + `ExerciseCardBackground`). */
@Composable
fun ExerciseGlassCard(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().glassSurface(PeakTheme.radius, tint)) {
        ExerciseCardBackground(exercise, Modifier.matchParentSize())
        Column(Modifier.padding(padding), content = content)
    }
}

/** Un testo che si tocca, senza increspature. */
@Composable
fun Modifier.clickableText(onClick: () -> Unit): Modifier = this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(vertical = 2.dp)
