package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.DeltaBadge
import com.gabrieleforghieri.peak.design.ExerciseThumbnail
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.ConfirmSheet
import com.gabrieleforghieri.peak.design.SearchField
import com.gabrieleforghieri.peak.design.WarningBanner
import com.gabrieleforghieri.peak.design.WarningOrange
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.sync.SyncEngine
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.ConnectSheet
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.Lang
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch

/**
 * La scheda Macchinari (`MaxesListView.swift`): tutti i macchinari con il massimale attuale, divisi per gruppo, con la ricerca, il filtro
 * per gruppo, gli avvisi sul collegamento a yG e sui macchinari fuori catalogo.
 */
@Composable
fun MachinesListScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepository.current
    val all by repository.exercises.collectAsState(emptyList())
    val records by repository.records.collectAsState(emptyList())
    val status by LocalApp.current.sync.engine.status.collectAsState()
    val connection = status.state
    MachinesListContent(all, records, showConnectionBanner = connection == SyncEngine.State.Disconnected || connection == SyncEngine.State.Expired, expired = connection == SyncEngine.State.Expired, modifier = modifier)
}

/** Il contenuto della lista dato i dati (separato per provarlo con degli screenshot). */
@Composable
fun MachinesListContent(
    all: List<ExerciseEntity>,
    records: List<MaxRecordEntity>,
    showConnectionBanner: Boolean,
    expired: Boolean,
    modifier: Modifier = Modifier,
    initialSearch: String = "",
    initialGroup: MuscleGroup? = null,
) {
    val nav = LocalPeakNav.current
    val settings = LocalAppSettings.current
    val accent = LocalAccent.current
    val app = LocalApp.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val italian = Lang.isItalian()

    var search by remember { mutableStateOf(initialSearch) }
    var selectedGroup by remember { mutableStateOf(initialGroup) }
    var creating by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf<ExerciseEntity?>(null) }

    val visible = remember(all, selectedGroup, search, italian) { visibleExercises(all, selectedGroup, search, italian) }
    val grouped = remember(visible) { groupedByMuscle(visible) }
    val live = remember(all) { all.filter { !it.isArchived } }
    val recordsOf = remember(records) { records.groupBy { it.exerciseUuid } }
    val unlinked = remember(all) { unlinkedExercises(all) }

    Box(modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LargeTitle("Macchinari") {
                Icon(
                    PeakIcons.plus, tr("Nuovo macchinario"),
                    Modifier.tourTarget("add-machine").size(36.dp).background(PeakTheme.cardStrong, CircleShape).pressable { creating = true }.padding(8.dp),
                    tint = PeakTheme.textPrimary,
                )
            }
            SearchField(search, { search = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), placeholder = "Cerca macchinario")
            if (showConnectionBanner) {
                WarningBanner(
                    icon = if (expired) MachinesIcons.link else MachinesIcons.cloudOff,
                    title = if (expired) "Collegamento a yG scaduto" else "Non sei collegato a yG",
                    message = if (expired) "I tuoi dati restano qui, ma non si sincronizzano più finché non accedi di nuovo."
                    else "I dati sono solo su questo telefono. Collegati per averli anche sul web e al sicuro sul server.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                    tint = accent, actionTitle = if (expired) "Accedi di nuovo" else "Collega a yG", onAction = { connecting = true },
                )
            }
            if (unlinked.isNotEmpty()) UnlinkedBanner(unlinked) { nav.openExercise(it.uuid) }
            when {
                live.isEmpty() -> EmptyCatalog { creating = true }
                grouped.isEmpty() -> NoResults(search)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item("filters") { FilterRow(selectedGroup) { selectedGroup = it } }
                    for ((group, items) in grouped) {
                        item("header-${group.name}") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                MuscleGroupChip(group)
                                Box(Modifier.weight(1f))
                                Text("${items.size}", style = PeakTheme.rounded(12, FontWeight.Bold), color = PeakTheme.textTertiary)
                            }
                        }
                        items(items, key = { it.uuid }) { exercise ->
                            val index = items.indexOf(exercise)
                            SwipeToArchive(onArchive = { archiving = exercise }) {
                                MachineRowCard(
                                    machineRow(exercise, recordsOf[exercise.uuid].orEmpty(), settings.unit), settings.unit,
                                    Modifier.appear(index, "ex-${exercise.uuid}").pressable { nav.openExercise(exercise.uuid) },
                                )
                            }
                        }
                    }
                    item("footer") {
                        val withMax = withMaxCount(all, records)
                        Text(
                            tr("%lld macchinari con massimale su %lld", withMax, live.size),
                            Modifier.fillMaxWidth().padding(top = 8.dp), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (creating) ExerciseEditorSheet(null) { creating = false }
        if (connecting) ConnectSheet { connecting = false }
        archiving?.let { e ->
            ConfirmSheet(
                title = "Archiviare il macchinario?", confirm = "Archivia", tint = WarningOrange,
                onConfirm = { scope.launch { app.database.areaMachines().archive(e.uuid); app.haptics.commit() } },
                onDismiss = { archiving = null },
            )
        }
    }
}

// ---- riga ----------------------------------------------------------------------------------

/** Scorri verso sinistra per archiviare (l'azione «Archivia» di `swipeActions`): chiede conferma, poi la riga torna a posto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToArchive(onArchive: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) onArchive(); false })
    SwipeToDismissBox(
        state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            // solo mentre si trascina: a riposo le card (semitrasparenti) lascerebbero vedere lo sfondo
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Row(
                Modifier.fillMaxSize().background(WarningOrange, RoundedCornerShape(PeakTheme.radius)).padding(end = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MachinesIcons.archive, null, Modifier.size(20.dp), tint = Color.White)
                Text(tr("Archivia"), style = PeakTheme.rounded(14, FontWeight.Bold), color = Color.White)
            }
        },
        content = { content() },
    )
}

@Composable
private fun MachineRowCard(row: MachineRow, unit: WeightUnit, modifier: Modifier = Modifier) {
    val exercise = row.exercise
    val best = row.best
    GlassCard(modifier, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumbnail(exercise, size = 42.dp, zoomable = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.displayName(), style = PeakTheme.rounded(16, FontWeight.SemiBold), color = PeakTheme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (best != null) {
                        // in palestra la cosa più utile a colpo d'occhio è con quanto scaldarsi, non quando è stato aggiornato il record
                        row.warmup?.let {
                            Text(tr("Risc. %@", Fmt.weight(it.weight, unit)), style = PeakTheme.rounded(12, FontWeight.Medium), color = exercise.group.tint.copy(alpha = 0.9f), maxLines = 1)
                            Text("·", style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                        }
                        Text(relativeDayText(best.date), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary, maxLines = 1)
                    } else {
                        Text(tr("Nessun massimale"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (best != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(Fmt.weight(best.weight, unit, includeSymbol = false), style = PeakTheme.display(21), color = PeakTheme.textPrimary)
                        Text("${unit.symbol} × ${best.reps}", Modifier.padding(bottom = 3.dp), style = PeakTheme.rounded(11, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                    }
                    row.deltaPercent?.let { DeltaBadge(it) }
                } else {
                    Icon(MachinesIcons.plusCircle, null, Modifier.size(22.dp), tint = PeakTheme.textTertiary)
                }
            }
        }
    }
}

/** «Oggi», «Ieri», altrimenti «gio 29 ott» (`Fmt.relativeDay`). */
@Composable
fun relativeDayText(date: Long): String = relativeDayWord(date)?.let { tr(it) } ?: MachineDates.weekdayDate(date)

// ---- filtro e avvisi -----------------------------------------------------------------------

@Composable
private fun FilterRow(selected: MuscleGroup?, onSelect: (MuscleGroup?) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("Tutti", selected == null, accent) { onSelect(null) }
        for (g in MuscleGroup.entries) FilterPill(g.title, selected == g, g.tint) { onSelect(if (selected == g) null else g) }
    }
}

@Composable
private fun FilterPill(title: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    Text(
        tr(title),
        Modifier.background(if (on) tint else PeakTheme.card, CircleShape).then(if (on) Modifier else Modifier.border(1.dp, PeakTheme.stroke, CircleShape))
            .pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (on) PeakTheme.bg else PeakTheme.textSecondary, maxLines = 1,
    )
}

/** I macchinari fuori catalogo (e non creati a mano di proposito) non entrano nelle classifiche con gli amici. */
@Composable
private fun UnlinkedBanner(unlinked: List<ExerciseEntity>, onOpen: (ExerciseEntity) -> Unit) {
    val count = if (unlinked.size == 1) tr("1 macchinario fuori catalogo") else tr("%lld macchinari fuori catalogo", unlinked.size)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr("%@: aprine uno → Modifica → Collega, così entra nelle classifiche con gli amici.", count),
            style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in unlinked) {
                Text(
                    e.displayName(),
                    Modifier.background(PeakTheme.card, CircleShape).border(1.dp, PeakTheme.stroke, CircleShape).pressable { onOpen(e) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textPrimary, maxLines = 1,
                )
            }
        }
    }
}

// ---- stati vuoti ---------------------------------------------------------------------------

@Composable
private fun EmptyCatalog(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Icon(PeakIcons.workouts, null, Modifier.size(44.dp), tint = PeakTheme.textTertiary)
        Text(tr("Nessun macchinario"), style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary)
        Text(
            tr("Cerca nel catalogo e aggiungi il primo macchinario: poi gli puoi dare un nome tuo."),
            style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center,
        )
        PrimaryButton(tr("Aggiungi macchinario"), onAdd, Modifier.padding(top = 6.dp), fullWidth = false)
    }
}

/** `ContentUnavailableView.search(text:)`. */
@Composable
private fun NoResults(search: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Icon(PeakIcons.search, null, Modifier.size(44.dp), tint = PeakTheme.textTertiary)
        Text(
            if (search.isEmpty()) tr("Nessun risultato") else tr("Nessun risultato per «%@»", search),
            style = PeakTheme.rounded(20, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center,
        )
        Text(tr("Controlla l'ortografia o prova una nuova ricerca."), style = PeakTheme.rounded(14, FontWeight.Medium), color = PeakTheme.textSecondary, textAlign = TextAlign.Center)
    }
}
