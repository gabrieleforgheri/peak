package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PlateBarView
import com.gabrieleforghieri.peak.design.RepsStepper
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.SideSegment
import com.gabrieleforghieri.peak.design.WeightStepper
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.ArmSwitch
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.domain.Warmup
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.TourSheet
import com.gabrieleforghieri.peak.ui.start.TourState
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.tr

/** Inserimento o modifica di una serie (`AddSetSheet.Mode`). */
sealed interface SetMode {
    /** Nuova serie, precompilata con l'ultima fatta su questo esercizio; il selettore «2 | 1» (solo dove serve) parte come quella. */
    data class Add(val lastSet: WorkoutSetEntity?) : SetMode

    /** Correzione di una serie già registrata. */
    data class Edit(val set: WorkoutSetEntity) : SetMode
}

/**
 * Inserimento **o modifica** di una serie (`AddSetSheet.swift`). In aggiunta parte dai valori dell'ultima serie fatta su quel macchinario,
 * in modifica da quelli della serie stessa. `onSave`: peso (kg), ripetizioni, riscaldamento, un braccio (o una gamba). Il foglio si chiude da sé.
 */
@Composable
fun AddSetSheet(
    exercise: ExerciseEntity,
    mode: SetMode,
    onDismiss: () -> Unit,
    onSave: (weight: Double, reps: Int, isWarmup: Boolean, singleArm: Boolean) -> Unit,
) {
    val repository = LocalRepository.current
    val sets by repository.setsOfExercise(exercise.uuid).collectAsState(null)
    val records by repository.recordsOfExercise(exercise.uuid).collectAsState(null)
    val settings = LocalAppSettings.current
    val editing = mode is SetMode.Edit

    // il tutorial: «Salva» sta qui dentro (si mostra solo aggiungendo)
    TourSheet(TourState.Sheet.save, whenActive = !editing)

    PeakSheet(onDismiss) { close ->
        val s = sets
        val r = records
        if (s != null && r != null) {
            AddSetContent(
                exercise, mode, s, r, settings.unit, settings.plateCalculatorEnabled, settings.availablePlates,
                onCancel = close, onSave = { w, reps, warm, arm -> onSave(w, reps, warm, arm); close() },
            )
        }
    }
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). `exerciseSets` e `records` sono quelli del macchinario. */
@Composable
fun AddSetContent(
    exercise: ExerciseEntity,
    mode: SetMode,
    exerciseSets: List<WorkoutSetEntity>,
    records: List<MaxRecordEntity>,
    unit: WeightUnit,
    plateCalculatorEnabled: Boolean,
    availablePlates: List<Double>,
    onCancel: () -> Unit,
    onSave: (weight: Double, reps: Int, isWarmup: Boolean, singleArm: Boolean) -> Unit,
) {
    val tint = exercise.group.tint
    val accent = LocalAccent.current
    val editing = mode as? SetMode.Edit
    val load = remember(exercise, plateCalculatorEnabled) { LoadMode.of(exercise, plateCalculatorEnabled) }
    val plateBase = load.plateBase

    val allPrevious = remember(exerciseSets, editing?.set?.uuid) {
        exerciseSets.filter { !it.isWarmup && it.uuid != editing?.set?.uuid }.sortedByDescending { it.createdAt }
    }
    val twoArmRecords = remember(records, exercise) { records.filter { !exercise.isOneLimbLine(it.singleArm) } }

    // il modulo parte dai valori della serie (modifica), dell'ultima fatta, del massimale più recente o da quattro passi
    val start = remember {
        when (mode) {
            is SetMode.Edit -> Triple(mode.set.weight, mode.set.reps, mode.set.isWarmup) to exercise.isOneLimbLine(mode.set.singleArm)
            is SetMode.Add -> {
                val (w, r) = initialLoad(mode.lastSet, twoArmRecords.maxByOrNull { it.date }, exercise.incrementStep)
                Triple(w, r, false) to (mode.lastSet?.let { exercise.isOneLimbLine(it.singleArm) } ?: false)
            }
        }
    }
    var weight by remember { mutableStateOf(start.first.first) }
    var reps by remember { mutableStateOf(start.first.second) }
    var isWarmup by remember { mutableStateOf(start.first.third) }
    var singleArm by remember { mutableStateOf(start.second) }
    // il peso di prima del dimezzamento (in unità mostrata), per rimetterlo identico tornando a due bracci
    var armMemo by remember { mutableStateOf<ArmSwitch.Memo?>(null) }
    var recentExpanded by remember { mutableStateOf(false) }
    // giorni aperti nelle serie recenti: all'inizio solo il più recente
    var openDays by remember { mutableStateOf(allPrevious.firstOrNull()?.let { setOf(WorkoutDates.startOfDay(it.createdAt)) } ?: emptySet()) }

    // il flag che si salva: conta solo dove il macchinario si fa in tutti e due i modi
    val oneLimb = Limbs.lineArm(exercise.limbsMode, singleArm)
    val warmupPlan = Warmup.plan(bestWeight(records.filter { exercise.isOneLimbLine(it.singleArm) == oneLimb }), exercise.incrementStep, unit)
    val reference = referenceRecord(exercise, records, singleArm, editing?.set?.uuid)
    val isRecord = beatsRecord(weight, reps, isWarmup, reference)
    val previousWeight = (mode as? SetMode.Add)?.lastSet?.takeIf { exercise.isOneLimbLine(it.singleArm) == oneLimb }?.weight
        ?: allPrevious.firstOrNull { exercise.isOneLimbLine(it.singleArm) == oneLimb }?.weight
    val delta = weightDelta(weight, previousWeight, unit)
    val view = androidx.compose.ui.platform.LocalView.current
    fun tap() { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK) }

    // il tutorial porta in vista il controllo del passo: peso e ripetizioni stanno in cima, il tasto che salva in fondo
    val scroll = rememberScrollState()
    LaunchedEffect(TourState.step, TourState.phase) {
        if (editing != null || TourState.phase != TourState.Phase.marks) return@LaunchedEffect
        when (TourState.step) {
            TourState.Step.load -> scroll.animateScrollTo(0)
            TourState.Step.save -> scroll.animateScrollTo(scroll.maxValue)
            else -> {}
        }
    }

    Column {
        SheetBar(exercise.displayName(), onCancel)
        Column(Modifier.verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // carico e ripetizioni insieme, per il riquadro del tutorial
            Column(Modifier.tourTarget("load"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCard(padding = 18.dp, tint = tint) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(if (load.perSide) "Per lato" else if (load.relative) "Carico dischi" else "Carico", Modifier.weight(1f))
                        if (plateBase != null && plateBase > 0) {
                            Text(tr("Sbarra %@", Fmt.weight(plateBase, unit)), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                        }
                        if (exercise.limbsMode == Limbs.either && exercise.group.hasSides) {
                            SideSegment(singleArm, { newValue ->
                                // manubri e cavi: il peso è già quello di un manico, passando a un arto resta lo stesso
                                if (exercise.twoHandles) { armMemo = null; singleArm = newValue; return@SideSegment }
                                val step = unit.displayStep(exercise.incrementStep) * (if (load.perSide) 2 else 1)
                                val result = ArmSwitch.weight(newValue, unit.fromKilograms(weight), armMemo, unit.fromKilograms(load.barKg), step)
                                armMemo = result.memo
                                weight = unit.toKilograms(result.weight)
                                singleArm = newValue
                            }, exercise.group)
                        }
                    }
                    Box(Modifier.padding(top = 14.dp)) {
                        WeightStepper(load.fieldKg(weight), { weight = load.totalKg(it) }, exercise.incrementStep, unit, tint = tint)
                    }

                    if (load.perSide) {
                        Text(
                            tr("Totale %@", Fmt.weight(weight, unit)), Modifier.fillMaxWidth().padding(top = 14.dp),
                            style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary,
                        )
                    }

                    if (plateBase != null && plateBase > 0 && !load.perSide) {
                        val relKg = maxOf(0.0, weight - plateBase)
                        val perSideText = tr("%@ (%@ per lato)", Fmt.weight(relKg, unit), Fmt.weight(relKg / 2, unit))
                        Column(
                            Modifier.padding(top = 14.dp).fillMaxWidth().background(PeakTheme.card, RoundedCornerShape(10.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row {
                                Text(tr(if (load.relative) "Carico relativo (dischi)" else "Carico assoluto (totale)"), Modifier.weight(1f), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                                if (load.relative) Text(perSideText, style = PeakTheme.rounded(13, FontWeight.Bold), color = accent)
                                else Text(Fmt.weight(weight, unit), style = PeakTheme.rounded(13, FontWeight.Bold), color = PeakTheme.textPrimary)
                            }
                            Row {
                                Text(tr(if (load.relative) "Carico assoluto (totale con sbarra)" else "Carico relativo (dischi)"), Modifier.weight(1f), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
                                if (load.relative) Text(Fmt.weight(weight, unit), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                                else Text(perSideText, style = PeakTheme.rounded(13, FontWeight.SemiBold), color = accent)
                            }
                        }
                    }

                    if (delta != null) {
                        val color = if (delta > 0) PeakTheme.positive else PeakTheme.negative
                        val sign = if (delta > 0) "+" else ""
                        val perSide = if (plateBase != null || load.perSide) " ($sign${Fmt.weight(unit.toKilograms(delta / 2), unit)} ${tr("per lato")})" else ""
                        Row(
                            Modifier.padding(top = 14.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PeakIcons.arrow(delta > 0), null, Modifier.size(13.dp), tint = color)
                            Text(
                                tr("%@%@ rispetto alla serie precedente", sign + Fmt.weight(unit.toKilograms(delta), unit), perSide),
                                style = PeakTheme.rounded(12, FontWeight.SemiBold), color = color,
                            )
                        }
                    }
                }

                GlassCard(padding = 18.dp) {
                    SectionHeader("Ripetizioni")
                    Box(Modifier.padding(top = 16.dp)) { RepsStepper(reps, { reps = it }, tint = tint) }
                }
            }

            if (plateBase != null) {
                GlassCard(padding = 18.dp) {
                    SectionHeader("Dischi per lato")
                    Box(Modifier.padding(top = 12.dp)) { PlateBarView(Plates.load(weight, plateBase, availablePlates), unit) }
                }
            }

            GlassCard(padding = 16.dp, tint = if (isRecord) PeakTheme.positive else null) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            tr(if (isRecord) "QUESTA SERIE" else "MASSIMALE DA BATTERE"),
                            style = PeakTheme.rounded(10, FontWeight.Bold).copy(letterSpacing = 0.7.sp), color = PeakTheme.textTertiary,
                        )
                        val shown = when {
                            isRecord -> setLineText(weight, reps, unit, singleArm, exercise.group)
                            reference != null -> setLineText(reference.weight, reference.reps, unit, singleArm, exercise.group)
                            else -> "—"
                        }
                        Text(shown, style = PeakTheme.display(24), color = if (isRecord) PeakTheme.positive else PeakTheme.textPrimary)
                        if (reps > 1) {
                            Text(
                                tr("questa serie ≈ %@ di 1RM", Fmt.weight(OneRepMax.estimate(weight, reps), unit)),
                                style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                            )
                        }
                    }
                    if (isRecord) PRBadge(text = tr("NUOVO RECORD"))
                }
            }

            RecentSets(
                allPrevious, exercise, load, unit, tint, recentExpanded, { recentExpanded = !recentExpanded }, openDays,
                { day -> openDays = if (day in openDays) openDays - day else openDays + day },
            ) { prev ->
                weight = prev.weight
                reps = prev.reps
                isWarmup = false
                singleArm = exercise.isOneLimbLine(prev.singleArm)
                armMemo = null
                tap()
            }

            if (warmupPlan.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Riscaldamento consigliato")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (step in warmupPlan) {
                            val shape = RoundedCornerShape(10.dp)
                            Column(
                                Modifier.weight(1f).background(PeakTheme.card, shape).border(1.dp, PeakTheme.stroke, shape)
                                    .pressable { weight = step.weight; reps = step.reps; tap() }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(step.percentLabel, style = PeakTheme.rounded(10, FontWeight.Bold), color = PeakTheme.textTertiary)
                                Text(
                                    Fmt.setLine(load.fieldKg(step.weight), step.reps, unit), style = PeakTheme.rounded(12, FontWeight.Bold),
                                    color = PeakTheme.textPrimary, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            val canSave = weight > 0 && reps >= 1
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.weight(1f).height(52.dp).background(PeakTheme.card, shape).border(1.dp, PeakTheme.stroke, shape)
                        .then(if (canSave) Modifier.pressable { onSave(weight, reps, true, oneLimb) } else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val c = if (canSave) PeakTheme.textSecondary else PeakTheme.textTertiary
                    Icon(WorkoutsIcons.flame, null, Modifier.size(16.dp), tint = c)
                    Text(tr("Riscaldamento"), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = c, maxLines = 1)
                }
                Row(
                    Modifier.weight(1f).height(52.dp).tourTarget("save").background(if (canSave) tint else tint.copy(alpha = 0.35f), shape)
                        // in modifica una serie di riscaldamento resta tale
                        .then(if (canSave) Modifier.pressable { onSave(weight, reps, if (editing != null) isWarmup else false, oneLimb) } else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (editing != null) WorkoutsIcons.checkCircle else WorkoutsIcons.plusCircle, null, Modifier.size(18.dp), tint = Color.Black)
                    Text(tr(if (editing != null) "Salva modifica" else "Aggiungi serie"), style = PeakTheme.rounded(16, FontWeight.Bold), color = Color.Black, maxLines = 1)
                }
            }
        }
    }
}

/** Il carico di riferimento per il riscaldamento: il massimale di quella linea. */
private fun bestWeight(records: List<MaxRecordEntity>): Double? =
    com.gabrieleforghieri.peak.domain.MaxRanking.best(records, { it.weight }, { it.reps }, { it.date })?.weight

@Composable
private fun RecentSets(
    all: List<WorkoutSetEntity>, exercise: ExerciseEntity, load: LoadMode, unit: WeightUnit, tint: Color,
    expanded: Boolean, onToggle: () -> Unit, openDays: Set<Long>, onToggleDay: (Long) -> Unit, onPick: (WorkoutSetEntity) -> Unit,
) {
    GlassCard(padding = 16.dp) {
        Row(Modifier.fillMaxWidth().pressable(0.99f, onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(WorkoutsIcons.history, null, Modifier.size(16.dp), tint = tint)
            Text(tr("Ultime serie su questo macchinario"), Modifier.weight(1f), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textPrimary)
            if (all.isNotEmpty()) {
                Text("${all.size}", Modifier.background(PeakTheme.card, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp), style = PeakTheme.rounded(12, FontWeight.Bold), color = PeakTheme.textTertiary)
            }
            Icon(if (expanded) WorkoutsIcons.chevronUp else WorkoutsIcons.chevronDown, null, Modifier.size(16.dp), tint = PeakTheme.textSecondary)
        }
        AnimatedVisibility(expanded) {
            if (all.isEmpty()) {
                Text(tr("Nessuna serie precedente"), Modifier.fillMaxWidth().padding(top = 8.dp), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary)
            } else {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Tocca una serie per usarla nel modulo."), Modifier.padding(top = 4.dp), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                    // per giorno di calendario (ora locale), gli ultimi 5 giorni
                    val grouped = all.groupBy { WorkoutDates.startOfDay(it.createdAt) }
                    for (day in grouped.keys.sortedDescending().take(5)) {
                        val sets = grouped.getValue(day)
                        val best = bestSet(sets)
                        val open = day in openDays
                        Row(Modifier.fillMaxWidth().pressable(0.99f) { onToggleDay(day) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(relativeDay(day), style = PeakTheme.rounded(13, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                            Text(
                                tr("%lld serie · %@", sets.size, setLineText(load.fieldKg(best.weight), best.reps, unit, exercise.isOneLimbLine(best.singleArm), exercise.group)),
                                Modifier.weight(1f), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary, textAlign = TextAlign.End, maxLines = 1,
                            )
                            Icon(if (open) WorkoutsIcons.chevronUp else WorkoutsIcons.chevronDown, null, Modifier.size(14.dp), tint = PeakTheme.textSecondary)
                        }
                        AnimatedVisibility(open) {
                            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (prev in sets) RecentSetButton(prev, exercise, load, unit) { onPick(prev) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bestSet(sets: List<WorkoutSetEntity>): WorkoutSetEntity =
    com.gabrieleforghieri.peak.domain.MaxRanking.best(sets, { it.weight }, { it.reps }, { it.createdAt }) ?: sets.first()

@Composable
private fun RecentSetButton(prev: WorkoutSetEntity, exercise: ExerciseEntity, load: LoadMode, unit: WeightUnit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PeakTheme.card, RoundedCornerShape(10.dp)).pressable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                setLineText(load.fieldKg(prev.weight), prev.reps, unit, exercise.isOneLimbLine(prev.singleArm), exercise.group),
                style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary,
            )
            if (load.relative || load.perSide) {
                Text(tr("(tot. %@)", Fmt.weight(prev.weight, unit)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            }
            if (prev.isPersonalRecord) PRBadge()
        }
        if (prev.reps > 1) Text("≈ ${Fmt.weight(prev.estimatedOneRepMax(), unit)}", style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
    }
}
