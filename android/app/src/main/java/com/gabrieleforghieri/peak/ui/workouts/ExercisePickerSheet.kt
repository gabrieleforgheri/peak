package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.data.matches
import com.gabrieleforghieri.peak.design.ExerciseThumbnail
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SearchField
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.ui.machines.ExerciseEditorSheet
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Workouts/ExercisePickerSheet.swift`). Proprietario: l'area **workouts**, che ne riscrive il corpo
 * (gruppi di oggi con la scappatoia «mostra tutti», i recenti in cima, il tasto + per un macchinario nuovo); la firma non cambia.
 * Usata da: workouts (sessione, editor dei giorni).
 *
 * Il foglio per scegliere un macchinario tra i tuoi. `focus` = i gruppi di oggi (vuoto = tutti). `onSelect` riceve il macchinario; il
 * foglio si chiude da solo scegliendo.
 */
@Composable
fun ExercisePickerSheet(
    onSelect: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit,
    focus: Set<MuscleGroup> = emptySet(),
) {
    val repository = LocalRepository.current
    val all by repository.exercises.collectAsState(emptyList())
    val recentSets by repository.recentSets().collectAsState(emptyList())
    var search by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var newExercise by remember { mutableStateOf(false) }

    val filtering = focus.isNotEmpty() && !showAll
    val visible = all.filter { !it.isArchived && (!filtering || it.group in focus) }
    val byUuid = remember(all) { all.associateBy { it.uuid } }
    val recent = if (search.isEmpty()) {
        recentSets.mapNotNull { byUuid[it.exerciseUuid] }.filter { it in visible }.distinct().take(6)
    } else emptyList()
    val found = visible.filter { it.matches(search) }

    PeakSheet(onDismiss) { close ->
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Scegli il macchinario"), Modifier.weight(1f), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
                Icon(PeakIcons.plus, tr("Nuovo macchinario"), Modifier.size(24.dp).pressable { newExercise = true }, tint = LocalAccent.current)
            }
            SearchField(search, { search = it })
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (recent.isNotEmpty()) {
                    item("recent-title") { Text(tr("Recenti").uppercase(), style = PeakTheme.rounded(11, FontWeight.Bold), color = PeakTheme.textTertiary) }
                    items(recent, key = { "recent-" + it.uuid }) { PickerRow(it) { onSelect(it); close() } }
                }
                for (group in MuscleGroup.entries) {
                    val items = found.filter { it.group == group }
                    if (items.isEmpty()) continue
                    item("title-" + group.name) {
                        Text(tr(group.title).uppercase(), Modifier.padding(top = 8.dp), style = PeakTheme.rounded(11, FontWeight.Bold), color = PeakTheme.textTertiary)
                    }
                    items(items, key = { it.uuid }) { PickerRow(it) { onSelect(it); close() } }
                }
                if (filtering) item("all") {
                    Text(
                        tr("Mostra tutti i macchinari"), Modifier.fillMaxWidth().padding(vertical = 12.dp).pressable { showAll = true },
                        style = PeakTheme.rounded(14, FontWeight.Bold), color = LocalAccent.current,
                    )
                }
            }
        }
    }
    if (newExercise) ExerciseEditorSheet(exerciseId = null, onDismiss = { newExercise = false })
}

@Composable
private fun PickerRow(exercise: ExerciseEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PeakTheme.card, RoundedCornerShape(PeakTheme.radiusSmall)).pressable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(exercise)
        Text(exercise.displayName(), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
        MuscleGroupChip(exercise.group, compact = true)
    }
}
