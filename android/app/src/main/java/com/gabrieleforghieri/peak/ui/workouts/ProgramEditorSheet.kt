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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.ConfirmSheet
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.domain.Programs
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch

/**
 * Crea o modifica una scheda (`ProgramEditorView.swift`): nome e, se vuoi, un giorno fisso della settimana per ogni giorno della scheda.
 * `program == null` = scheda nuova. Salva e chiude da sé.
 */
@Composable
fun ProgramEditorSheet(program: WorkoutProgramEntity?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val repository = LocalRepository.current
    val programs by repository.programs.collectAsState(emptyList())
    val templates by repository.templates.collectAsState(emptyList())
    val settings = LocalAppSettings.current
    val update = rememberUpdateSettings()
    val scope = rememberCoroutineScope()
    val actions = remember(app) { WorkoutsActions(app.database, app.records) }
    val days = if (program != null) daysOf(program, templates) else emptyList()

    PeakSheet(onDismiss) { close ->
        ProgramEditorContent(
            program = program, days = days, canDelete = program != null && programs.size > 1,
            onCancel = close,
            onSave = { name, useDays, weekdays ->
                scope.launch {
                    val saved = actions.saveProgram(program, name, useDays, weekdays)
                    if (program == null) update { copy(activeProgram = saved.uuid) }
                    close()
                }
            },
            onDelete = {
                scope.launch {
                    val doomed = program ?: return@launch
                    if (settings.activeProgram.uppercase() == doomed.uuid) update { copy(activeProgram = "") }
                    actions.deleteProgram(doomed.uuid)
                    close()
                }
            },
        )
    }
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). */
@Composable
fun ProgramEditorContent(
    program: WorkoutProgramEntity?,
    days: List<WorkoutTemplateEntity>,
    canDelete: Boolean,
    onCancel: () -> Unit,
    onSave: (name: String, useDays: Boolean, weekdays: Map<String, Int>) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(program?.name.orEmpty()) }
    var useDays by remember { mutableStateOf(program?.useDays ?: false) }
    // il giorno fisso scelto per ogni giorno della scheda (uuid → 0…6, −1 = nessuno); si applica con Salva
    var weekdays by remember(days) { mutableStateOf(days.associate { it.uuid to it.weekday }) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(Modifier.imePadding()) {
        SheetBar(
            if (program == null) "Nuova scheda" else "Scheda", onCancel, confirm = "Salva", confirmEnabled = name.isNotBlank(),
            onConfirm = { onSave(name.trim(), useDays, weekdays) },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard {
                SectionHeader("Nome")
                Box(Modifier.padding(top = 12.dp)) { PeakTextField(name, { name = it }, "Es. Push Pull Legs") }
            }
            GlassCard { ToggleRow("Giorni fissi della settimana", useDays) { useDays = it } }
            if (useDays) {
                for (day in days) {
                    GlassCard {
                        Text(day.name, style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp).scrollsSideways(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PickChip(weekdays[day.uuid] == -1, { weekdays = weekdays + (day.uuid to -1) }) { PickChipText(tr("Nessuno")) }
                            for (i in 0 until 7) {
                                PickChip(weekdays[day.uuid] == i, { weekdays = weekdays + (day.uuid to i) }) { PickChipText(tr(Programs.weekdays[i])) }
                            }
                        }
                    }
                }
            }
            if (canDelete) SecondaryLabelButton(WorkoutsIcons.trash, tr("Elimina scheda"), { confirmingDelete = true }, tint = PeakTheme.negative)
        }
    }
    if (confirmingDelete) {
        ConfirmSheet(
            "Eliminare questa scheda e i suoi giorni? Gli allenamenti già fatti restano.", "Elimina scheda", onDelete, { confirmingDelete = false },
            tint = PeakTheme.negative,
        )
    }
}
