package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.ConfirmSheet
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Programs
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.tr
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

/**
 * Crea o modifica un giorno della scheda (`TemplateEditorView.swift`): nome, icona, scheda, giorno fisso e righe (esercizio, serie,
 * ripetizioni min–max, peso). Con `template == null` il giorno è nuovo; `initialItems` lo riempie («Salva come giorno»). Salva e chiude da sé.
 */
@Composable
fun TemplateEditorSheet(
    onDismiss: () -> Unit,
    template: WorkoutTemplateEntity? = null,
    initialName: String = "",
    initialItems: List<TemplateItem> = emptyList(),
    onSaved: (() -> Unit)? = null,
) {
    val app = LocalApp.current
    val repository = LocalRepository.current
    val exercises by repository.exercises.collectAsState(emptyList())
    val programs by repository.programs.collectAsState(emptyList())
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    val actions = remember(app) { WorkoutsActions(app.database, app.records) }

    PeakSheet(onDismiss) { close ->
        TemplateEditorContent(
            template = template, initialName = initialName, initialItems = initialItems,
            exercises = exercises, programs = programs, unit = settings.unit, activeProgramSetting = settings.activeProgram,
            onCancel = close,
            onSave = { name, items, hidden, icon, program, weekday ->
                scope.launch {
                    actions.saveTemplate(template, name, items, hidden, icon, program, weekday, settings.activeProgram)
                    onSaved?.invoke()
                    close()
                }
            },
            onDelete = { template?.let { t -> scope.launch { actions.deleteTemplate(t.uuid); close() } } },
        )
    }
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). */
@Composable
fun TemplateEditorContent(
    template: WorkoutTemplateEntity?,
    initialName: String,
    initialItems: List<TemplateItem>,
    exercises: List<ExerciseEntity>,
    programs: List<WorkoutProgramEntity>,
    unit: WeightUnit,
    activeProgramSetting: String,
    onCancel: () -> Unit,
    onSave: (name: String, items: List<TemplateItem>, hidden: Boolean, icon: String, program: String, weekday: Int) -> Unit,
    onDelete: () -> Unit,
) {
    val byUuid = remember(exercises) { exercises.associateBy { it.uuid } }
    var name by remember { mutableStateOf(template?.name ?: initialName) }
    // i pesi si scrivono nell'unità scelta: le righe nascono qui, con `unit`
    var rows by remember { mutableStateOf(templateRows(template?.itemList ?: initialItems, unit)) }
    var isHidden by remember { mutableStateOf(template?.isHidden ?: false) }
    var icon by remember { mutableStateOf(template?.icon ?: "") }
    var programRaw by remember { mutableStateOf(template?.program ?: "") }
    var weekday by remember { mutableStateOf(template?.weekday ?: -1) }
    var showingPicker by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var muscleDetail by remember { mutableStateOf<ExerciseEntity?>(null) }
    val view = LocalView.current
    fun tap() { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }

    val chosenProgram = programs.firstOrNull { it.uuid == programRaw } ?: activeProgram(programs, activeProgramSetting)

    Column(Modifier.imePadding()) {
        SheetBar(
            if (template == null) "Nuovo giorno" else "Giorno", onCancel, confirm = "Salva", confirmEnabled = name.isNotBlank(),
            onConfirm = {
                val program = programs.firstOrNull { it.uuid == programRaw }?.uuid ?: ""
                onSave(name.trim(), templateItems(rows, { it in byUuid }, unit), isHidden, icon, program, weekday)
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard {
                SectionHeader("Nome")
                Box(Modifier.padding(top = 12.dp)) { PeakTextField(name, { name = it }, "Es. Push, Pull 1, Gambe") }
            }

            GlassCard {
                SectionHeader("Icona")
                Row(Modifier.padding(top = 10.dp).scrollsSideways(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickChip(icon.isEmpty(), { icon = ""; tap() }) { PickChipText(tr("Automatica")) }
                    for (choice in WorkoutsIcons.templateChoices) {
                        val groupTint = com.gabrieleforghieri.peak.domain.TemplateIcon.group(choice)?.tint
                        PickChip(icon == choice, { icon = choice; tap() }) {
                            Icon(WorkoutsIcons.template(choice), null, Modifier.padding(horizontal = 2.dp).then(Modifier), tint = groupTint ?: androidx.compose.material3.LocalContentColor.current)
                        }
                    }
                }
            }

            if (programs.size > 1 || chosenProgram?.useDays == true) {
                GlassCard {
                    if (programs.size > 1) {
                        SectionHeader("Scheda")
                        Row(Modifier.padding(top = 10.dp).scrollsSideways(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (p in programs) PickChip(chosenProgram?.uuid == p.uuid, { programRaw = p.uuid; tap() }) { PickChipText(p.name) }
                        }
                    }
                    if (chosenProgram?.useDays == true) {
                        SectionHeader("Giorno fisso", Modifier.padding(top = if (programs.size > 1) 14.dp else 0.dp))
                        Row(Modifier.padding(top = 10.dp).scrollsSideways(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PickChip(weekday < 0, { weekday = -1; tap() }) { PickChipText(tr("Nessuno")) }
                            for (i in 0 until 7) PickChip(weekday == i, { weekday = i; tap() }) { PickChipText(tr(Programs.weekdays[i])) }
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                Text(
                    tr("Azzera i campi"),
                    Modifier.fillMaxWidth().clickableText { rows = rows.map { it.copy(sets = "", repsMin = "", repsMax = "", weight = "") }; tap() },
                    style = PeakTheme.rounded(13, FontWeight.SemiBold), color = LocalAccent.current,
                )
            }

            rows.forEachIndexed { index, row ->
                val exercise = byUuid[row.exercise] ?: return@forEachIndexed
                ExerciseGlassCard(exercise, padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Text(exercise.displayName(), Modifier.weight(1f), style = PeakTheme.rounded(16, FontWeight.SemiBold), color = PeakTheme.textPrimary, maxLines = 2)
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RoundIconButton(WorkoutsIcons.chevronUp, tr("Su"), { rows = rows.swapped(index, index - 1) }, enabled = index > 0)
                                    RoundIconButton(WorkoutsIcons.chevronDown, tr("Giù"), { rows = rows.swapped(index, index + 1) }, enabled = index < rows.size - 1)
                                    RoundIconButton(PeakIcons.close, tr("Togli"), { rows = rows.filterIndexed { i, _ -> i != index } })
                                }
                                MuscleMapButton(exercise, { muscleDetail = exercise })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fun change(update: (TemplateRow) -> TemplateRow) { rows = rows.mapIndexed { i, r -> if (i == index) update(r) else r } }
                            LabeledField(tr("Serie"), row.sets, { v -> change { it.copy(sets = v) } }, Modifier.weight(1f))
                            LabeledField(tr("Rip min"), row.repsMin, { v -> change { it.copy(repsMin = v) } }, Modifier.weight(1f))
                            LabeledField(tr("Rip max"), row.repsMax, { v -> change { it.copy(repsMax = v) } }, Modifier.weight(1f))
                            LabeledField(unit.symbol, row.weight, { v -> change { it.copy(weight = v) } }, Modifier.weight(1f), KeyboardType.Decimal, "–")
                        }
                    }
                }
            }

            SecondaryLabelButton(PeakIcons.plus, tr("Aggiungi esercizio"), { showingPicker = true })

            if (rows.isNotEmpty()) {
                Text(tr("Senza peso scritto si vede l'ultimo usato."), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            }

            GlassCard { ToggleRow("Nascondi agli amici", isHidden) { isHidden = it } }

            if (template != null) SecondaryLabelButton(WorkoutsIcons.trash, tr("Elimina giorno"), { confirmingDelete = true }, tint = PeakTheme.negative)
        }
    }

    if (showingPicker) {
        ExercisePickerSheet(
            onSelect = { exercise -> if (rows.none { it.exercise == exercise.uuid }) rows = rows + TemplateRow(exercise.uuid) },
            onDismiss = { showingPicker = false },
        )
    }
    muscleDetail?.let { MuscleDetailSheet(it) { muscleDetail = null } }
    if (confirmingDelete) {
        ConfirmSheet("Eliminare questo giorno? Gli allenamenti già fatti restano.", "Elimina giorno", onDelete, { confirmingDelete = false }, tint = PeakTheme.negative)
    }
}
