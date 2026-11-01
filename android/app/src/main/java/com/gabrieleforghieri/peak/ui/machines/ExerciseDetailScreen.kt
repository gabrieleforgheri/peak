package com.gabrieleforghieri.peak.ui.machines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.figureMuscles
import com.gabrieleforghieri.peak.data.figureView
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.CountText
import com.gabrieleforghieri.peak.design.DeltaBadge
import com.gabrieleforghieri.peak.design.ExerciseCardBackground
import com.gabrieleforghieri.peak.design.ExerciseFigure
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MonthStack
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.glassSurface
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.workouts.MuscleDetailSheet
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.launch

/**
 * Il dettaglio di un macchinario (`ExerciseDetailView.swift`): massimale attuale, prossimo carico, riscaldamento, andamento, storico dei
 * massimali, serie fatte in allenamento e note. Torna indietro da solo se il macchinario viene eliminato.
 */
@Composable
fun ExerciseDetailScreen(exerciseId: String, modifier: Modifier = Modifier) {
    val repository = LocalRepository.current
    val nav = LocalPeakNav.current
    var exercise by remember(exerciseId) { mutableStateOf<ExerciseEntity?>(null) }
    LaunchedEffect(exerciseId) {
        repository.exercise(exerciseId).collect {
            // eliminato da qui (dal foglio di modifica): la schermata non ha più nulla da mostrare
            if (it == null && exercise != null) nav.back()
            exercise = it
        }
    }
    val records by repository.recordsOfExercise(exerciseId).collectAsState(emptyList())
    val sets by repository.setsOfExercise(exerciseId).collectAsState(emptyList())
    val workouts by repository.workouts.collectAsState(emptyList())
    val byId = remember(workouts) { workouts.associateBy { it.uuid } }
    exercise?.let { ExerciseDetailContent(it, records, sets, byId, onBack = { nav.back() }, modifier = modifier) }
        ?: Box(modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current)))
}

/** Il contenuto, dati i dati (per provarlo con degli screenshot). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailContent(
    exercise: ExerciseEntity,
    records: List<MaxRecordEntity>,
    sets: List<WorkoutSetEntity>,
    workouts: Map<String, WorkoutEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val settings = LocalAppSettings.current
    val unit = settings.unit
    val accent = LocalAccent.current
    val tint = exercise.group.tint
    val context = LocalContext.current
    val app = LocalApp.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var showingNewRecord by remember { mutableStateOf(false) }
    var showingEditor by remember { mutableStateOf(false) }
    var showingMuscles by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(ChartRange.all) }

    val lines = remember(exercise, records) { MachineLines(exercise, records) }
    val recordList = lines.twoArmByDateDesc
    val ranges = remember(recordList, now) { availableRanges(recordList, now) }
    val points = remember(recordList, range, unit, now) { chartPoints(recordList, range, unit, now) }
    val warmup = remember(lines, unit) { lines.warmupPlan(unit) }
    val recentSets = remember(sets) { sets.sortedByDescending { it.createdAt } }
    val next = remember(exercise, sets, workouts, settings.repRange, unit) { if (settings.progressionEnabled) nextProgression(exercise, sets, workouts, settings.repRange, unit) else null }
    val side: (String) -> String = { context.tr(it) }

    fun deleteRecord(record: MaxRecordEntity) {
        scope.launch { app.records.deleteRecord(record.uuid) }
        app.haptics.warning()
    }

    Box(modifier.fillMaxSize().then(PeakTheme.backgroundModifier(accent))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // la barra del titolo, come la barra di navigazione con il titolo in linea
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BarButton(MachinesIcons.chevronLeft, tr("Indietro"), onBack)
                Text(exercise.displayName(), Modifier.weight(1f).padding(horizontal = 8.dp), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, textAlign = TextAlign.Center, maxLines = 1)
                BarButton(MachinesIcons.sliders, tr("Modifica macchinario")) { showingEditor = true }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ---- testata
                Box(Modifier.fillMaxWidth().appear(0).glassSurface(PeakTheme.radius, tint)) {
                    ExerciseCardBackground(exercise, Modifier.matchParentSize())
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                    MuscleGroupChip(exercise.group)
                                    if (lines.limbs == Limbs.one) Caption(if (exercise.worksLegs) "Una gamba" else "Un braccio")
                                    if (exercise.perSide) Caption("Per lato")
                                    if (exercise.barWeight > 0) Caption(tr("Sbarra %@", Fmt.weight(exercise.barWeight, unit)), verbatim = true)
                                    if (exercise.barWeight > 0 && exercise.shownWeightDisplay == "relative" && !exercise.perSide) Caption("Dischi")
                                    if (lines.best?.isEstimated == true) Caption("da allenamento", tertiary = true)
                                }
                                val best = lines.best
                                if (best != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            CountText(best.weight, style = PeakTheme.display(52).copy(color = PeakTheme.textPrimary), format = { Fmt.weight(it, unit, includeSymbol = false) })
                                            Text(unit.symbol, Modifier.padding(bottom = 8.dp), style = PeakTheme.rounded(18, FontWeight.SemiBold), color = PeakTheme.textSecondary)
                                            Text("× ${best.reps}", Modifier.padding(bottom = 8.dp), style = PeakTheme.rounded(18, FontWeight.SemiBold), color = PeakTheme.textSecondary)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(tr("Massimale attuale · %@", MachineDates.date(best.date)), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                                            lines.bestWeightBefore(best.date)?.takeIf { it > 0 }?.let { DeltaBadge((best.weight - it) / it * 100) }
                                        }
                                        if (best.reps > 1) Text(tr("≈ %@ di 1RM stimato", Fmt.weight(OneRepMax.estimate(best.weight, best.reps), unit)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("—", style = PeakTheme.display(52), color = PeakTheme.textTertiary)
                                        Text(tr("Nessun massimale registrato"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                                    }
                                }
                            }
                            val muscles = exercise.figureMuscles
                            if (muscles.isNotEmpty()) {
                                Column(Modifier.pressable { showingMuscles = true }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    ExerciseFigure(muscles, exercise.group, view = AnatomyView.entries.firstOrNull { it.name == exercise.figureView }, size = 68.dp)
                                    Row(
                                        Modifier.background(tint.copy(alpha = 0.14f), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(tr("Muscoli"), style = PeakTheme.rounded(11, FontWeight.SemiBold), color = tint)
                                        Icon(MachinesIcons.arrowUpRight, tr("Mappa muscolare"), Modifier.size(10.dp), tint = tint)
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(tint, RoundedCornerShape(16.dp)).pressable { showingNewRecord = true }.padding(vertical = 15.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(MachinesIcons.arrowUpCircle, null, Modifier.size(20.dp), tint = PeakTheme.bg)
                            Text(tr("Aggiorna massimale"), style = PeakTheme.rounded(16, FontWeight.Bold), color = PeakTheme.bg)
                        }
                    }
                }

                // ---- prossima volta
                next?.let {
                    GlassCard(Modifier.appear(1), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader("Prossima volta", Modifier.weight(1f))
                            Text(Fmt.setLine(it.weight, it.reps, unit), style = PeakTheme.rounded(17, FontWeight.Bold), color = tint)
                        }
                    }
                }

                // ---- classifica con gli amici: solo per i macchinari del catalogo (la classifica vera arriva con l'area amici)
                if (exercise.catalogID.isEmpty()) {
                    GlassCard(Modifier.appear(2), padding = 16.dp) {
                        Text(tr("Per la classifica con gli amici collega questo macchinario al catalogo (Modifica → Catalogo)."), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                }

                // ---- riscaldamento
                if (warmup.isNotEmpty()) GlassCard(Modifier.appear(3)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Riscaldamento consigliato", trailing = "sul massimale reale")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (step in warmup) {
                                Column(
                                    Modifier.weight(1f).background(tint.copy(alpha = 0.10f), RoundedCornerShape(13.dp)).border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(13.dp)).padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(step.percentLabel, style = PeakTheme.rounded(10, FontWeight.Bold), color = tint)
                                    Text(Fmt.weight(step.weight, unit, includeSymbol = false), style = PeakTheme.display(22), color = PeakTheme.textPrimary, maxLines = 1)
                                    Text("${unit.symbol} × ${step.reps}", style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                                }
                            }
                        }
                        Text(
                            tr("Pesi sulla griglia del macchinario, a passi (%@).", Fmt.weight(unit.toKilograms(unit.displayStep(exercise.incrementStep)), unit)),
                            style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                        )
                    }
                }

                // ---- andamento
                if (recordList.size >= 2) GlassCard(Modifier.appear(4)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader("Andamento")
                        if (ranges.size > 1) {
                            val active = if (range in ranges) range else ranges.first()
                            Row(Modifier.fillMaxWidth().background(PeakTheme.cardStrong, RoundedCornerShape(9.dp)).padding(2.dp)) {
                                for (r in ranges) {
                                    Text(
                                        tr(r.title),
                                        Modifier.weight(1f).background(if (r == active) Color.White.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(7.dp)).pressable { range = r }.padding(vertical = 6.dp),
                                        style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (r == active) PeakTheme.textPrimary else PeakTheme.textTertiary, textAlign = TextAlign.Center, maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (points.size >= 2) {
                            ProgressChart(points, tint)
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                LegendDot(tint, "Registrato")
                                LegendDot(tint.copy(alpha = 0.55f), "Da allenamento")
                            }
                        } else {
                            Text(
                                tr("Dati non sufficienti per questo intervallo"), Modifier.fillMaxWidth().height(120.dp).wrapContentCenter(),
                                style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // ---- storico dei massimali
                if (recordList.isNotEmpty()) RecordsCard("Storico massimali", recordList, lines, exercise, ::deleteRecord, side, Modifier.appear(5))
                // una linea a parte, solo se ci sono massimali a un braccio
                if (lines.limbs == Limbs.either && lines.oneArm.isNotEmpty()) {
                    Column(Modifier.appear(6), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordsCard(if (exercise.worksLegs) "Massimale a una gamba" else "Massimale a un braccio", lines.oneArmByDateDesc, lines, exercise, ::deleteRecord, side)
                        lines.limbComparison()?.let { (one, half) ->
                            val limb = tr(if (exercise.worksLegs) "Per gamba" else "Per braccio")
                            Text(
                                "$limb: ${Fmt.weight(one, unit)} · ${tr("a due")} ${Fmt.weight(half, unit)}",
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary,
                            )
                        }
                    }
                }

                // ---- serie in allenamento
                if (recentSets.isNotEmpty()) GlassCard(Modifier.appear(7)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Serie in allenamento")
                        MonthStack(recentSets, { it.createdAt }, { n -> if (n == 1) context.tr("1 serie") else context.tr("%lld serie", n) }) { set ->
                            val oneLimb = lines.isOneLimbLine(set.singleArm)
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (exercise.shownWeightDisplay == "relative" && !exercise.perSide && exercise.barWeight > 0) {
                                    val relWeight = maxOf(0.0, set.weight - exercise.barWeight)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(Fmt.setLine(relWeight, set.reps, unit, oneLimb, exercise.group, side), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                                        Text(tr("(tot. %@)", Fmt.weight(set.weight, unit)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                                    }
                                } else {
                                    Text(Fmt.setLine(set.weight, set.reps, unit, oneLimb, exercise.group, side), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                                }
                                if (set.isPersonalRecord) PRBadge()
                                if (set.isWarmup) Text(tr("riscaldamento"), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                                Box(Modifier.weight(1f))
                                Text(relativeDayText(set.createdAt), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                            }
                        }
                    }
                }

                // ---- note
                if (exercise.notes.isNotEmpty()) GlassCard(Modifier.appear(8)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader("Note")
                        Text(exercise.notes, style = PeakTheme.rounded(14, FontWeight.Normal), color = PeakTheme.textSecondary)
                    }
                }
            }
        }
        if (showingNewRecord) EditMaxSheet(exercise) { showingNewRecord = false }
        if (showingEditor) ExerciseEditorSheet(exercise.uuid) { showingEditor = false }
        if (showingMuscles) MuscleDetailSheet(exercise) { showingMuscles = false }
    }
}

// ---- pezzi ---------------------------------------------------------------------------------

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = PeakTheme.textPrimary)
    }
}

@Composable
private fun Caption(text: String, tertiary: Boolean = false, verbatim: Boolean = false) {
    Text(
        if (verbatim) text else tr(text), style = PeakTheme.rounded(11, if (tertiary) FontWeight.Medium else FontWeight.SemiBold),
        color = if (tertiary) PeakTheme.textTertiary else PeakTheme.textSecondary,
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(tr(label), style = PeakTheme.rounded(10, FontWeight.Medium), color = PeakTheme.textTertiary)
    }
}

private fun Modifier.wrapContentCenter(): Modifier = this.then(Modifier.padding(top = 48.dp))

/** Un elenco di massimali per mese, con il cestino per cancellarne uno. */
@Composable
private fun RecordsCard(
    title: String,
    records: List<MaxRecordEntity>,
    lines: MachineLines,
    exercise: ExerciseEntity,
    onDelete: (MaxRecordEntity) -> Unit,
    side: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val unit = LocalAppSettings.current.unit
    val tint = exercise.group.tint
    val context = LocalContext.current
    GlassCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title, trailing = "${records.size}")
            MonthStack(records, { it.date }, { n -> if (n == 1) context.tr("1 massimale") else context.tr("%lld massimali", n) }, dividers = true) { record ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (record.isEstimated) tint.copy(alpha = 0.45f) else tint, CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(Fmt.setLine(record.weight, record.reps, unit, lines.isOneLimbLine(record.singleArm), exercise.group, side), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                        Text(MachineDates.date(record.date) + if (record.isEstimated) " · ${tr("da allenamento")}" else "", style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                    }
                    if (record.reps > 1) Text("≈ ${Fmt.weight(OneRepMax.estimate(record.weight, record.reps), unit)}", style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                    Icon(MachinesIcons.trash, tr("Elimina"), Modifier.size(32.dp).pressable { onDelete(record) }.padding(9.dp), tint = PeakTheme.textTertiary)
                }
            }
        }
    }
}
