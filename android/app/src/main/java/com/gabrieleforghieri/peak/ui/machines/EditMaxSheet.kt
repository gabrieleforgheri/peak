package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.RepsStepper
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.SideSegment
import com.gabrieleforghieri.peak.design.WeightStepper
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Registra un nuovo massimale (`EditMaxSheet.swift`): il carico vero × ripetizioni, con il 1RM stimato come nota a margine.
 * Parte dall'ultimo record così di solito basta un tocco sul «+». Salva da sé nel database e ricalcola i record del macchinario.
 */
@Composable
fun EditMaxSheet(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    val repository = LocalRepository.current
    val data by produceState<Pair<List<MaxRecordEntity>, List<WorkoutSetEntity>>?>(null, exercise.uuid) {
        value = repository.recordsOfExercise(exercise.uuid).first() to repository.setsOfExercise(exercise.uuid).first()
    }
    PeakSheet(onDismiss) { close ->
        data?.let { (records, sets) -> EditMaxContent(exercise, MachineLines(exercise, records), sets, onCancel = close, onSaved = close) }
    }
}

/** Il corpo del foglio, dati i massimali e le serie del macchinario (`onSaved` parte dopo aver avviato il salvataggio). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMaxContent(
    exercise: ExerciseEntity,
    lines: MachineLines,
    sets: List<WorkoutSetEntity>,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val app = LocalApp.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = LocalAppSettings.current
    val tint = exercise.group.tint
    val accent = LocalAccent.current
    val unit = settings.unit
    val prefill = remember(exercise.uuid) { prefillMax(lines, sets) }
    var weight by remember { mutableStateOf(prefill.first) }
    var reps by remember { mutableStateOf(prefill.second) }
    var date by remember { mutableStateOf(now) }
    var note by remember { mutableStateOf("") }
    var singleArm by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    val estimated = OneRepMax.estimate(weight, reps)
    val previousBest = if (singleArm) lines.oneArmBest else lines.best
    val isRecord = isNewRecord(lines, weight, reps, singleArm)
    val canSave = isValidMaxWeight(weight)

    fun save() {
        val record = MaxRecordEntity(
            uuid = Uuids.new(), exerciseUuid = exercise.uuid, date = date, weight = weight, reps = reps,
            isEstimated = false, note = note, singleArm = Limbs.lineArm(lines.limbs, singleArm),
        )
        app.mainScope.launch {
            app.database.records().upsert(record)
            app.records.recompute(exercise.uuid)
            if (isRecord) app.haptics.personalRecord() else app.haptics.commit()
        }
        onSaved()
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Annulla"), Modifier.pressable(onClick = onCancel).padding(vertical = 8.dp), style = PeakTheme.rounded(16, FontWeight.Medium), color = accent)
            Text(exercise.displayName(), Modifier.weight(1f).padding(horizontal = 8.dp), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center, maxLines = 1)
            Text(
                tr("Salva"), Modifier.then(if (canSave) Modifier.pressable(onClick = ::save) else Modifier).padding(vertical = 8.dp),
                style = PeakTheme.rounded(16, FontWeight.Bold), color = if (canSave) accent else PeakTheme.textTertiary,
            )
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(padding = 18.dp, tint = tint) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Carico", Modifier.weight(1f))
                        if (lines.limbs == Limbs.either && exercise.group.hasSides) SideSegment(singleArm, { singleArm = it }, exercise.group)
                    }
                    WeightStepper(weight, { weight = it }, exercise.incrementStep, unit, tint = tint)
                }
            }
            GlassCard(padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionHeader("Ripetizioni")
                    RepsStepper(reps, { reps = it }, tint = tint)
                }
            }
            GlassCard(padding = 18.dp, tint = if (isRecord) PeakTheme.positive else null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Massimale", Modifier.weight(1f))
                        if (isRecord) PRBadge(text = tr("NUOVO RECORD"))
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Fmt.weight(weight, unit, includeSymbol = false), style = PeakTheme.display(42), color = if (isRecord) PeakTheme.positive else PeakTheme.textPrimary)
                        Text("${unit.symbol} × $reps", Modifier.padding(bottom = 6.dp), style = PeakTheme.rounded(16, FontWeight.SemiBold), color = PeakTheme.textSecondary)
                    }
                    if (reps > 1) Text(tr("≈ %@ di 1RM stimato", Fmt.weight(estimated, unit)), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                    previousBest?.let {
                        Text(
                            tr("Precedente: %@", Fmt.setLine(it.weight, it.reps, unit, singleArm, exercise.group, side = { s -> context.tr(s) })),
                            style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary,
                        )
                    }
                }
            }
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader("Dettagli")
                    Row(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { pickingDate = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Data"), Modifier.weight(1f), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textPrimary)
                        Text(
                            MachineDates.long(date), Modifier.background(PeakTheme.cardStrong, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            style = PeakTheme.rounded(14, FontWeight.Medium), color = accent,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(PeakTheme.stroke))
                    Box {
                        if (note.isEmpty()) Text(tr("Nota (facoltativa)"), style = PeakTheme.rounded(14, FontWeight.Normal), color = PeakTheme.textTertiary)
                        BasicTextField(
                            note, { note = it }, Modifier.fillMaxWidth(), maxLines = 3, cursorBrush = SolidColor(accent),
                            textStyle = PeakTheme.rounded(14, FontWeight.Normal).copy(color = PeakTheme.textPrimary),
                        )
                    }
                }
            }
        }
    }

    if (pickingDate) {
        val todayUtc = MachineDates.utcMidnight(now)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = MachineDates.utcMidnight(date),
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton({ state.selectedDateMillis?.let { date = MachineDates.dayAtNow(it, now) }; pickingDate = false }) { Text(tr("Fatto")) }
            },
            dismissButton = { TextButton({ pickingDate = false }) { Text(tr("Annulla")) } },
        ) { DatePicker(state) }
    }
}
