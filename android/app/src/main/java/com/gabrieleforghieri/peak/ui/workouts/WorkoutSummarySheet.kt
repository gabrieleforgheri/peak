package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.figureMuscles
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.AnatomyView
import com.gabrieleforghieri.peak.design.ConfettiBurst
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.LocalReduceMotion
import com.gabrieleforghieri.peak.design.MuscleFigureView
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakSheet
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.PrimaryButton
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.StatTile
import com.gabrieleforghieri.peak.design.bounce
import com.gabrieleforghieri.peak.design.enter
import com.gabrieleforghieri.peak.design.pop
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleId
import com.gabrieleforghieri.peak.domain.Plan
import com.gabrieleforghieri.peak.domain.Stats
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.util.SoundFX
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.delay

/**
 * **Firma condivisa** (iPhone: `Features/Workouts/WorkoutSummaryView.swift`). Proprietario: l'area **workouts**.
 * Usata da: workouts (fine della sessione) e da `PendingSummaryOverlay` (un allenamento chiuso fuori dalla sessione).
 *
 * Il riepilogo di un allenamento chiuso, con la festa: durata, volume, la settimana e l'obiettivo, le medaglie salite, i record. `onDone`
 * parte a foglio chiuso, anche con un gesto.
 */
@Composable
fun WorkoutSummarySheet(workoutId: String, onDone: () -> Unit) {
    val repository = LocalRepository.current
    val workout by repository.workout(workoutId).collectAsState(null)
    val workouts by repository.workouts.collectAsState(emptyList())
    val sets by repository.sets.collectAsState(emptyList())
    val exercises by repository.exercises.collectAsState(emptyList())
    val records by repository.records.collectAsState(emptyList())
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    var savingAsDay by remember { mutableStateOf(false) }

    val w = workout
    // fissata alla prima comparsa: i dati non cambiano mentre il riepilogo è aperto
    val snapshot = remember(w?.uuid, workouts.isNotEmpty(), exercises.isNotEmpty()) {
        w?.let { summarySnapshot(it, workouts, sets.groupBy { s -> s.workoutUuid.orEmpty() }, exercises, records, settings.weeklyGoal) }
    }
    val mine = remember(sets, workoutId) { sets.filter { it.workoutUuid == workoutId } }

    PeakSheet(onDismiss = onDone) { close ->
        if (w != null && snapshot != null) {
            WorkoutSummaryContent(
                workout = w, sets = mine, exercises = exercises, snapshot = snapshot, unit = settings.unit,
                onSaveAsDay = { savingAsDay = true }, onDone = close, feedback = rememberFeedback(), celebrated = remember { Celebrated(context) },
            )
        }
    }
    if (savingAsDay && w != null) {
        val known = exercises.map { it.uuid }.toSet()
        TemplateEditorSheet({ savingAsDay = false }, initialName = w.name, initialItems = sessionItems(w, mine, { it in known }, Plan.Mode.done))
    }
}

/** Il contenuto del foglio, senza il foglio (si prova negli screenshot). */
@Composable
fun WorkoutSummaryContent(
    workout: WorkoutEntity,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>,
    snapshot: SummarySnapshot,
    unit: WeightUnit,
    onSaveAsDay: () -> Unit,
    onDone: () -> Unit,
    feedback: WorkoutFeedback = WorkoutFeedback.None,
    celebrated: Celebrated? = null,
) {
    val accent = LocalAccent.current
    val reduce = LocalReduceMotion.current
    val byUuid = remember(exercises) { exercises.associateBy { it.uuid } }
    val touched = remember(sets, byUuid) { exercisesInOrder(sets, byUuid) }
    val muscles = remember(touched) { touched.flatMap { it.figureMuscles }.distinct() }
    val snap = snapshot

    // la festa: ogni pezzo parte spento e la sequenza lo accende (una volta sola)
    var heroIn by remember { mutableStateOf(false) }
    var tilesIn by remember { mutableStateOf(false) }
    var counted by remember { mutableStateOf(reduce) }
    var daysFilled by remember { mutableIntStateOf(0) }
    var ringTick by remember { mutableIntStateOf(0) }
    var goalFilled by remember { mutableIntStateOf(0) }
    var goalReached by remember { mutableStateOf(false) }
    var flameTick by remember { mutableIntStateOf(0) }
    var streakRolled by remember { mutableStateOf(false) }
    var streakBadge by remember { mutableStateOf(false) }
    var medalsFilled by remember { mutableStateOf(false) }
    var tierUp by remember { mutableStateOf(false) }
    var recordsIn by remember { mutableStateOf(false) }
    var trophyTick by remember { mutableIntStateOf(0) }
    var heroBurst by remember { mutableIntStateOf(0) }
    var goalBurst by remember { mutableIntStateOf(0) }

    // La festa, a partire dalla comparsa (t in secondi): 0 eroe, coriandoli e suono · 0,25 tessere e numeri · 0,8 i giorni della settimana uno a
    // uno (ogni giorno una nota più alta) · le capsule dell'obiettivo · obiettivo raggiunto · serie di settimane · medaglie · record.
    LaunchedEffect(Unit) {
        // l'eroe di Allenamento non rifà la festa di quello che il riepilogo sta per mostrare
        celebrated?.let {
            val weekStart = Stats.weekStart(workout.date)
            if (weekStart == Stats.weekStart(System.currentTimeMillis())) {
                it.streak = snap.streakNow
                it.store(snap.thisWeek, weekStart)
            }
        }
        heroIn = true
        heroBurst += 1
        feedback.sound(SoundFX.Effect.finish)

        delay(250)
        tilesIn = true
        counted = true

        delay(550)
        for (k in snap.days.sorted().indices) {
            daysFilled = k + 1
            feedback.day(k)
            feedback.tap()
            delay(140)
        }
        ringTick += 1
        delay(200)

        val n = snap.thisWeek
        if (n > 0) {
            val step = minOf(100L, (700 / n).toLong())
            for (k in 1..n) { goalFilled = k; delay(step) }
        }
        if (snap.thisWeek >= snap.goal && snap.thisWeek - 1 < snap.goal) {
            delay(100)
            goalReached = true
            goalBurst += 1
            feedback.sound(SoundFX.Effect.goal)
            feedback.success()
            delay(500)
        }

        if (snap.streakNow > snap.streakBefore) {
            flameTick += 1
            streakBadge = true
            streakRolled = true
            feedback.sound(SoundFX.Effect.streak)
            feedback.commit()
            delay(550)
        }

        if (snap.moved.isNotEmpty()) {
            medalsFilled = true
            if (snap.moved.any { it.leveledUp }) {
                delay(500)
                tierUp = true
                feedback.sound(SoundFX.Effect.medal)
                feedback.commit()
                delay(500)
            } else delay(400)
        }

        if (snap.records.isNotEmpty()) {
            recordsIn = true
            trophyTick += 1
            feedback.sound(SoundFX.Effect.record)
            feedback.personalRecord()
        }
    }

    val countProgress by animateFloatAsState(if (counted) 1f else 0f, tween(if (reduce) 0 else 800), label = "count")

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // l'eroe
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (snap.records.isEmpty()) WorkoutsIcons.checkCircle else WorkoutsIcons.trophy, null,
                    Modifier.padding(top = 12.dp).size(44.dp).pop(heroIn, from = 0.6f, rotation = -12f).bounce(trophyTick, 1.2f, 8f),
                    tint = if (snap.records.isEmpty()) accent else PeakTheme.positive,
                )
                Text(
                    tr(if (snap.records.isEmpty()) "Allenamento concluso" else "Nuovi massimali!"), Modifier.enter(heroIn, 8f, 80),
                    style = PeakTheme.rounded(24, FontWeight.Bold), color = PeakTheme.textPrimary,
                )
                Text(WorkoutDates.dateTime(workout.date), Modifier.enter(heroIn, 8f, 140), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
            }

            // le tessere entrano a cascata (60 ms) e i numeri scorrono fino al valore vero
            val volume = sets.totalVolume
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(WorkoutsIcons.clock, "Durata", workout.durationSeconds?.let { Fmt.duration(it) } ?: "—", Modifier.weight(1f).enter(tilesIn, 12f, 0), tint = accent)
                StatTile(WorkoutsIcons.scale, "Volume", Fmt.volume(volume * countProgress, unit), Modifier.weight(1f).enter(tilesIn, 12f, 60), tint = accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(WorkoutsIcons.listNumber, "Serie", "${(sets.workingSetCount * countProgress).toInt()}", Modifier.weight(1f).enter(tilesIn, 12f, 120), tint = accent)
                StatTile(WorkoutsIcons.dumbbell, "Esercizi", "${(touched.size * countProgress).toInt()}", Modifier.weight(1f).enter(tilesIn, 12f, 180), tint = accent)
            }

            WeekCard(snap, accent, daysFilled, ringTick, goalFilled, goalReached, flameTick, streakRolled, streakBadge, reduce)
            if (snap.moved.isNotEmpty()) MedalsCard(snap.moved, accent, medalsFilled, tierUp)

            if (muscles.isNotEmpty()) {
                GlassCard {
                    SectionHeader("Muscoli lavorati")
                    // davanti e dietro affiancati: c'è posto per entrambi
                    Row(Modifier.padding(top = 12.dp).widthIn(max = 340.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuscleFigureView(muscles, Modifier.weight(1f), view = AnatomyView.front, zoom = false)
                        MuscleFigureView(muscles, Modifier.weight(1f), view = AnatomyView.back, zoom = false)
                    }
                }
            }

            if (snap.records.isNotEmpty()) RecordsCard(snap.records, byUuid, unit, recordsIn)

            if (sets.isNotEmpty()) SecondaryLabelButton(WorkoutsIcons.list, tr("Salva come giorno"), onSaveAsDay, Modifier.padding(top = 4.dp))
            PrimaryButton(tr("Fine"), onDone, Modifier.padding(top = 4.dp))
        }
        ConfettiBurst(heroBurst, Modifier.fillMaxSize(), Offset(0.5f, 0.06f))
        ConfettiBurst(goalBurst, Modifier.fillMaxSize(), Offset(0.5f, 0.5f))
    }
}

private val DAY_LETTERS = listOf("L", "M", "M", "G", "V", "S", "D")

@Composable
private fun WeekCard(
    snap: SummarySnapshot, accent: Color, daysFilled: Int, ringTick: Int, goalFilled: Int, goalReached: Boolean,
    flameTick: Int, streakRolled: Boolean, streakBadge: Boolean, reduce: Boolean,
) {
    val order = snap.days.sorted()
    val reached = snap.thisWeek >= snap.goal
    // se è stato questo allenamento a raggiungerlo, la scritta cambia al momento della festa
    val showReached = reached && (goalReached || snap.thisWeek - 1 >= snap.goal)
    val streakShown = if (snap.streakNow > snap.streakBefore && !streakRolled) snap.streakBefore else snap.streakNow
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(WorkoutsIcons.flame, null, Modifier.size(22.dp).bounce(flameTick, 1.3f, 10f), tint = accent)
            Text("$streakShown", style = PeakTheme.rounded(24, FontWeight.ExtraBold), color = PeakTheme.textPrimary)
            Text(tr(if (snap.streakNow == 1) "settimana di fila" else "settimane di fila"), Modifier.weight(1f), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textSecondary)
            if (snap.streakNow > snap.streakBefore) PRBadge(Modifier.pop(streakBadge, from = 0.6f), text = "+1")
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            for (i in 0 until 7) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayDot(snap.days.contains(i) && order.indexOf(i) < daysFilled, i == snap.today, ringTick, accent, reduce)
                    Text(tr(DAY_LETTERS[i]), style = PeakTheme.rounded(10, FontWeight.Medium), color = PeakTheme.textTertiary)
                }
            }
        }
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr(if (showReached) "Obiettivo raggiunto!" else "Obiettivo della settimana"), Modifier.weight(1f),
                    style = PeakTheme.rounded(12, FontWeight.Medium), color = if (showReached) PeakTheme.positive else PeakTheme.textSecondary,
                )
                Text("$goalFilled / ${snap.goal}", style = PeakTheme.tabular(13, FontWeight.Bold), color = PeakTheme.textPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in 0 until maxOf(1, snap.goal)) {
                    val filled = k < goalFilled
                    val fill by animateFloatAsState(if (filled || reduce) 1f else 0.3f, tween(if (reduce) 0 else 250), label = "capsule")
                    Box(Modifier.weight(1f).height(12.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                        if (filled) Box(Modifier.fillMaxWidth(fill).height(12.dp).background(accent, CircleShape))
                    }
                }
            }
            if (snap.thisWeek > snap.goal) {
                Text(
                    tr("+%lld oltre l'obiettivo", snap.thisWeek - snap.goal), Modifier.enter(goalFilled >= snap.thisWeek, 4f),
                    style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                )
            }
        }
    }
}

/** Un giorno della settimana: il cerchio si riempie con un piccolo rimbalzo quando `on` diventa vero; l'anello di oggi pulsa. */
@Composable
private fun DayDot(on: Boolean, isToday: Boolean, ringTick: Int, accent: Color, reduce: Boolean) {
    val scale by animateFloatAsState(if (on || reduce) 1f else 0.4f, tween(if (reduce) 0 else 250), label = "dot")
    Box(
        Modifier.size(40.dp).then(if (isToday) Modifier.border(2.dp, accent.copy(alpha = 0.8f), CircleShape) else Modifier).bounce(ringTick, 1.25f).padding(3.dp)
            .background(if (on) accent else Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(PeakIcons.check, null, Modifier.size(16.dp * scale), tint = Color.Black.copy(alpha = 0.8f))
    }
}

@Composable
private fun MedalsCard(moved: List<MovedMedal>, accent: Color, filled: Boolean, tierUp: Boolean) {
    GlassCard {
        SectionHeader("Medaglie")
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            moved.forEachIndexed { index, pair ->
                val from = if (pair.leveledUp) 0.0 else pair.before.progress
                val shown by animateFloatAsState(
                    (if (filled) pair.after.progress else from).toFloat(), tween(600, delayMillis = 150 * index), label = "medal",
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(WorkoutsIcons.medal(pair.after.kind), null, Modifier.size(18.dp), tint = accent)
                        Text(tr(pair.after.kind.title), Modifier.weight(1f), style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary)
                        if (pair.leveledUp) PRBadge(Modifier.pop(tierUp, from = 0.6f), text = tr(listOf("", "BRONZO", "ARGENTO", "ORO")[pair.after.tier]))
                    }
                    // la barra sale dal valore di prima a quello di adesso, una dopo l'altra
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val r = CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = 0.1f), Offset.Zero, size, r)
                        drawRoundRect(accent.copy(alpha = 0.55f), Offset.Zero, Size(size.width * from.toFloat(), size.height), r)
                        drawRoundRect(accent, Offset.Zero, Size(size.width * shown, size.height), r)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsCard(records: List<WorkoutSetEntity>, byUuid: Map<String, ExerciseEntity>, unit: WeightUnit, recordsIn: Boolean) {
    GlassCard(tint = PeakTheme.positive) {
        SectionHeader("Record della giornata", trailing = "${records.size}")
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            records.forEachIndexed { index, set ->
                val exercise = set.exerciseUuid?.let { byUuid[it] }
                val delay = 60 * index
                Row(Modifier.fillMaxWidth().enter(recordsIn, 12f, delay), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).pop(recordsIn, from = 0.6f, delayMs = delay + 100).background(PeakTheme.positive.copy(alpha = 0.14f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(exercise?.let { PeakIcons.group(it.group) } ?: WorkoutsIcons.dumbbell, null, Modifier.size(14.dp), tint = PeakTheme.positive) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(exercise?.displayName() ?: tr("Esercizio rimosso"), style = PeakTheme.rounded(14, FontWeight.SemiBold), color = PeakTheme.textPrimary)
                        Text(
                            setLineText(set.weight, set.reps, unit, exercise?.isOneLimbLine(set.singleArm) ?: false, exercise?.group),
                            style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary,
                        )
                    }
                    Text(Fmt.weight(set.weight, unit, includeSymbol = false), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.positive)
                }
            }
        }
    }
}
