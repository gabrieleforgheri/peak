package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
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
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MaxRanking
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.machines.ExerciseEditorSheet
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.tr

/**
 * **Firma condivisa** (iPhone: `Features/Workouts/ExercisePickerSheet.swift`). Proprietario: l'area **workouts**.
 * Usata da: workouts (sessione, editor dei giorni).
 *
 * Il foglio per scegliere un macchinario tra i tuoi: in cima i 6 usati di recente, poi i gruppi; `focus` = i gruppi di oggi (vuoto = tutti),
 * con la scappatoia «Mostra tutti i macchinari». La miniatura apre la foto, il resto della riga sceglie. `onSelect` riceve il macchinario;
 * il foglio si chiude da solo scegliendo, e `onSelect` parte a foglio chiuso.
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
    val records by repository.records.collectAsState(emptyList())
    val unit = LocalAppSettings.current.unit
    var newExercise by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<ExerciseEntity?>(null) }

    PeakSheet(onDismiss = { onDismiss(); picked?.let(onSelect) }) { close ->
        ExercisePickerContent(
            all = all, recentSets = recentSets, records = records, focus = focus, unit = unit,
            onSelect = { picked = it; close() }, onClose = close, onNew = { newExercise = true },
        )
    }
    if (newExercise) ExerciseEditorSheet(exerciseId = null, onDismiss = { newExercise = false })
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). */
@Composable
fun ExercisePickerContent(
    all: List<ExerciseEntity>,
    recentSets: List<WorkoutSetEntity>,
    records: List<MaxRecordEntity>,
    focus: Set<MuscleGroup>,
    unit: WeightUnit,
    onSelect: (ExerciseEntity) -> Unit,
    onClose: () -> Unit,
    onNew: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    // scappatoia per l'esercizio fuori programma: vale solo per questo foglio
    var showAll by remember { mutableStateOf(false) }
    val filtering = focus.isNotEmpty() && !showAll
    val visible = all.filter { !it.isArchived && (!filtering || it.group in focus) }
    val byUuid = remember(all) { all.associateBy { it.uuid } }
    val recent = if (search.isEmpty()) recentExercises(recentSets, byUuid, visible) else emptyList()
    val found = visible.filter { it.matches(search) }
    val maxes = remember(records, all) { currentMaxes(all, records) }

    Column(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Chiudi"), Modifier.weight(1f).clickableText(onClose),
                style = PeakTheme.rounded(15, FontWeight.Medium), color = LocalAccent.current,
            )
            Text(tr("Scegli macchinario"), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                RoundIconButton(PeakIcons.plus, tr("Nuovo macchinario"), onNew, size = 34, iconSize = 18, tint = LocalAccent.current)
            }
        }
        SearchField(search, { search = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(
            Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            if (recent.isNotEmpty()) {
                item("recent-title") { com.gabrieleforghieri.peak.design.SectionHeader("Usati di recente", Modifier.padding(top = 4.dp, start = 2.dp)) }
                items(recent, key = { "recent-" + it.uuid }) { PickerRow(it, maxes[it.uuid], unit) { onSelect(it) } }
            }
            for (group in MuscleGroup.entries) {
                val items = found.filter { it.group == group }
                if (items.isEmpty()) continue
                item("title-" + group.name) { MuscleGroupChip(group, Modifier.padding(top = 8.dp, start = 2.dp)) }
                items(items, key = { it.uuid }) { PickerRow(it, maxes[it.uuid], unit) { onSelect(it) } }
            }
            if (filtering) item("all") {
                Text(
                    tr("Mostra tutti i macchinari"), Modifier.fillMaxWidth().pressable { showAll = true }.padding(vertical = 12.dp),
                    style = PeakTheme.rounded(14, FontWeight.SemiBold), color = LocalAccent.current, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** I macchinari usati di recente (al più 6), senza ripetizioni, dal più recente; solo fra quelli visibili. */
fun recentExercises(recentSets: List<WorkoutSetEntity>, byUuid: Map<String, ExerciseEntity>, visible: List<ExerciseEntity>): List<ExerciseEntity> {
    val allowed = visible.map { it.uuid }.toSet()
    return recentSets.mapNotNull { it.exerciseUuid }.distinct().filter { it in allowed }.mapNotNull { byUuid[it] }.take(6)
}

/** Il massimale attuale (kg) di ogni macchinario: il carico vero a due braccia (`Exercise.currentMax`). */
fun currentMaxes(exercises: List<ExerciseEntity>, records: List<MaxRecordEntity>): Map<String, Double> {
    val byExercise = records.groupBy { it.exerciseUuid }
    val out = HashMap<String, Double>()
    for (e in exercises) {
        val line = byExercise[e.uuid].orEmpty().filter { !e.isOneLimbLine(it.singleArm) }
        MaxRanking.best(line, { it.weight }, { it.reps }, { it.date })?.let { out[e.uuid] = it.weight }
    }
    return out
}

@Composable
private fun PickerRow(exercise: ExerciseEntity, max: Double?, unit: WeightUnit, onClick: () -> Unit) {
    // la miniatura sta fuori dal bottone: toccarla apre la foto, non sceglie
    Row(
        Modifier.fillMaxWidth().glassSurface(PeakTheme.radiusSmall).padding(vertical = 10.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(exercise, size = 32.dp, zoomable = true)
        Row(Modifier.weight(1f).pressable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(exercise.displayName(), Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = PeakTheme.textPrimary)
            if (max != null) Text(Fmt.weight(max, unit), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
    }
}
