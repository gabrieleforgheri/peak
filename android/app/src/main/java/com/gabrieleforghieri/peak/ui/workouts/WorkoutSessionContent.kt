package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.design.CelebrationToastHost
import com.gabrieleforghieri.peak.design.ConfettiBurst
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MuscleGroupChip
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.bounce
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.start.TourState
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.delay

/** Quello che la sessione fa per conto dell'utente: la schermata li collega alle scritture, una prova li lascia vuoti. */
class SessionEvents(
    val onBack: () -> Unit = {},
    val onSelect: (ExerciseEntity) -> Unit = {},
    val onAddSet: (ExerciseEntity) -> Unit = {},
    val onRepeat: (ExerciseEntity) -> Unit = {},
    val onEditSet: (WorkoutSetEntity) -> Unit = {},
    val onDeleteSet: (WorkoutSetEntity) -> Unit = {},
    val onDetail: (ExerciseEntity) -> Unit = {},
    val onShowMuscles: (ExerciseEntity) -> Unit = {},
    val onMove: (ExerciseEntity, Int) -> Unit = { _, _ -> },
    val onReplace: (ExerciseEntity) -> Unit = {},
    val onRemove: (ExerciseEntity) -> Unit = {},
    val onAddExercise: () -> Unit = {},
    val onSaveAsDay: () -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onFinish: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onEditFocus: () -> Unit = {},
    val onRestShift: (Int) -> Unit = {},
    val onRestStop: () -> Unit = {},
)

/**
 * La sessione di allenamento (`WorkoutSessionView.swift`), senza i fogli e le scritture: la testata col tempo, il recupero, una card per
 * esercizio con le sue serie, le note e «Termina». `restEnd` è la fine del recupero (ms), `recordSet`/`recordTick` la festa dell'ultimo record.
 */
@Composable
fun WorkoutSessionContent(
    ui: SessionUi,
    unit: WeightUnit,
    note: String,
    restEnd: Long?,
    recordSet: String?,
    recordTick: Int,
    events: SessionEvents,
    modifier: Modifier = Modifier,
    now: Long? = null,
) {
    val accent = LocalAccent.current
    val workout = ui.workout
    val title = workoutTitle(workout.name, workout.date)
    val clock by produceState(now ?: System.currentTimeMillis(), now) {
        if (now != null) return@produceState
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            SessionTopBar(title, workout.isActive, events.onBack, events.onDelete)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 30.dp),
            ) {
                item("header") { Box(Modifier.appear(0)) { Header(ui, accent, clock, unit, events) } }

                if (restEnd != null) item("rest") { RestCard(restEnd, clock, accent, events) }

                itemsIndexed(ui.cards, key = { _, c -> "card-" + c.exercise.uuid }) { index, card ->
                    Box(Modifier.appear(index + 1)) { ExerciseSessionCard(card, unit, tourFirst = index == 0, recordSet = recordSet, recordTick = recordTick, events = events) }
                }

                if (ui.orphans.isNotEmpty()) item("orphans") {
                    GlassCard {
                        Text(tr("Esercizio rimosso"), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textSecondary)
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ui.orphans.forEachIndexed { i, set -> SessionSetRow(i, set, null, unit, 0, onEdit = {}, onDelete = { events.onDeleteSet(set) }) }
                        }
                    }
                }

                item("add") { SecondaryLabelButton(PeakIcons.plus, tr("Aggiungi esercizio"), events.onAddExercise) }
                if (ui.hasItems) item("save") { SecondaryLabelButton(WorkoutsIcons.list, tr("Salva come giorno"), events.onSaveAsDay) }

                item("note") {
                    GlassCard {
                        SectionHeader("Note")
                        Box(Modifier.padding(top = 10.dp)) {
                            PeakTextField(
                                note, events.onNoteChange, "Come è andata? Sensazioni, dolori, regolazioni del sedile…",
                                style = PeakTheme.rounded(14, FontWeight.Normal), singleLine = false, maxLines = 6,
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                            )
                        }
                    }
                }

                if (workout.isActive) item("finish") {
                    PrimaryLabelButton(WorkoutsIcons.checkCircle, tr("Termina allenamento"), events.onFinish, Modifier.padding(top = 4.dp))
                }
            }
        }
        ConfettiBurst(recordTick, Modifier.fillMaxSize())
        CelebrationToastHost("Nuovo massimale!", recordTick, Modifier.statusBarsPadding(), icon = WorkoutsIcons.trophy)
    }
}

@Composable
private fun SessionTopBar(title: String, active: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(WorkoutsIcons.back, tr("Indietro"), onBack, size = 40, iconSize = 20, tint = PeakTheme.textPrimary)
        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = PeakTheme.rounded(17, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (active) {
            Box {
                RoundIconButton(WorkoutsIcons.more, tr("Altro"), { menu = true }, size = 40, iconSize = 20, tint = PeakTheme.textPrimary)
                DropdownMenu(menu, { menu = false }, containerColor = PeakTheme.bgRaised) {
                    DropdownMenuItem(
                        text = { Text(tr("Elimina allenamento"), style = PeakTheme.rounded(15, FontWeight.Medium), color = PeakTheme.negative) },
                        leadingIcon = { Icon(WorkoutsIcons.trash, null, Modifier.size(18.dp), tint = PeakTheme.negative) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        } else Spacer(Modifier.size(40.dp))
    }
}

// ---- testata ---------------------------------------------------------------------------------

@Composable
private fun Header(ui: SessionUi, accent: Color, now: Long, unit: WeightUnit, events: SessionEvents) {
    val workout = ui.workout
    GlassCard(padding = 18.dp, tint = if (workout.isActive) accent else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (workout.isActive) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(accent, CircleShape))
                    Text(tr("IN CORSO"), style = PeakTheme.rounded(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = accent)
                }
            } else {
                Text(tr("CONCLUSO"), style = PeakTheme.rounded(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = PeakTheme.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            Text(WorkoutDates.dateTime(workout.date), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
        }
        if (workout.isActive) {
            Text(Fmt.duration(maxOf(0L, now - workout.date) / 1000.0), Modifier.padding(top = 14.dp), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
        } else workout.durationSeconds?.let {
            Text(Fmt.duration(it), Modifier.padding(top = 14.dp), style = PeakTheme.display(34), color = PeakTheme.textPrimary)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MiniStat("${ui.workingSets}", "serie")
            MiniStat(Fmt.volume(ui.volume, unit), "volume")
            if (ui.recordCount > 0) MiniStat("${ui.recordCount}", "record")
        }
        // cosa si allena oggi: filtra il selettore degli esercizi
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val focus = MuscleGroup.entries.filter { it in workout.focusGroups }
            if (focus.isEmpty()) {
                Text(tr("Tutti i gruppi"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
            } else {
                for (g in focus) MuscleGroupChip(g, compact = focus.size > 3)
            }
            Spacer(Modifier.weight(1f))
            if (workout.isActive) {
                Text(tr("Modifica"), Modifier.clickableText(events.onEditFocus), style = PeakTheme.rounded(13, FontWeight.Bold), color = accent)
            }
        }
    }
}

// ---- recupero --------------------------------------------------------------------------------

@Composable
private fun RestCard(end: Long, now: Long, accent: Color, events: SessionEvents) {
    val left = maxOf(0L, end - now)
    GlassCard(padding = 16.dp, tint = accent) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("Recupero"), style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                Text(Fmt.clock(((left + 999) / 1000).toInt()), style = PeakTheme.tabular(30, FontWeight.Bold), color = accent)
            }
            RestButton("−15") { events.onRestShift(-15) }
            RestButton("+15") { events.onRestShift(15) }
            RestButton(tr("Salta"), events.onRestStop)
        }
    }
}

@Composable
private fun RestButton(text: String, onClick: () -> Unit) {
    Text(
        text, Modifier.background(PeakTheme.cardStrong, RoundedCornerShape(10.dp)).pressable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        style = PeakTheme.rounded(14, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1,
    )
}

// ---- card di un esercizio ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseSessionCard(card: SessionCard, unit: WeightUnit, tourFirst: Boolean, recordSet: String?, recordTick: Int, events: SessionEvents) {
    val exercise = card.exercise
    val tint = exercise.group.tint
    var menu by remember { mutableStateOf(false) }

    ExerciseGlassCard(exercise, tint = if (card.isCurrent) tint else null) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                // sinistra: titolo, IN CORSO, obiettivo e «Scorso»; destra: il menu e la figura dei muscoli
                Column(Modifier.weight(1f).pressable(1f) { events.onSelect(exercise) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(exercise.displayName(), style = PeakTheme.rounded(22, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 2)
                        if (card.isCurrent) Text(tr("IN CORSO"), style = PeakTheme.rounded(10, FontWeight.Bold).copy(letterSpacing = 0.7.sp), color = tint)
                    }
                    if (card.goal.isNotEmpty()) Text(card.goal, style = PeakTheme.rounded(12, FontWeight.SemiBold), color = PeakTheme.textSecondary)
                    PreviousBlock(card, unit)
                }
                Column(Modifier.width(86.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box {
                        RoundIconButton(WorkoutsIcons.more, tr("Altro"), { menu = true }, size = 34, iconSize = 16)
                        DropdownMenu(menu, { menu = false }, containerColor = PeakTheme.bgRaised) {
                            MenuItem("Vedi macchinario", WorkoutsIcons.info) { menu = false; events.onDetail(exercise) }
                            if (card.canMove) {
                                MenuItem("Su", WorkoutsIcons.chevronUp) { menu = false; events.onMove(exercise, -1) }
                                MenuItem("Giù", WorkoutsIcons.chevronDown) { menu = false; events.onMove(exercise, 1) }
                            }
                            if (card.canReplace) MenuItem("Sostituisci", WorkoutsIcons.swap) { menu = false; events.onReplace(exercise) }
                            if (card.canRemove) MenuItem("Togli dalla lista", WorkoutsIcons.trash, PeakTheme.negative) { menu = false; events.onRemove(exercise) }
                        }
                    }
                    MuscleMapButton(exercise, { events.onShowMuscles(exercise) })
                }
            }

            // le serie di oggi
            if (card.sets.isEmpty()) {
                Text(tr("Nessuna serie registrata"), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.sets.forEachIndexed { index, set ->
                        SessionSetRow(index, set, exercise, unit, if (set.uuid == recordSet) recordTick else 0, onEdit = { events.onEditSet(set) }, onDelete = { events.onDeleteSet(set) })
                    }
                }
            }

            // i pulsanti: Serie e Ripeti
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.weight(1f).then(if (tourFirst) Modifier.tourTarget("add-set") else Modifier)
                        .background(tint.copy(alpha = 0.20f), RoundedCornerShape(11.dp)).pressable { events.onAddSet(exercise) }.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PeakIcons.plus, null, Modifier.size(14.dp), tint = tint)
                    Text(tr("Serie"), style = PeakTheme.rounded(13, FontWeight.Bold), color = tint)
                }
                card.sets.lastOrNull()?.let { last ->
                    Row(
                        Modifier.weight(1f).background(PeakTheme.card, RoundedCornerShape(11.dp)).pressable { events.onRepeat(exercise) }.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(WorkoutsIcons.redo, null, Modifier.size(13.dp), tint = PeakTheme.textSecondary)
                        Text(
                            setLineText(shownWeight(exercise, last.weight), last.reps, unit, exercise.isOneLimbLine(last.singleArm), exercise.group),
                            style = PeakTheme.rounded(13, FontWeight.SemiBold), color = PeakTheme.textSecondary, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = PeakTheme.textPrimary, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(tr(text), style = PeakTheme.rounded(15, FontWeight.Medium), color = color) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = color) },
        onClick = onClick,
    )
}

/** «Scorso»: due pagine da scorrere, le ultime serie (fino a 3) e gli ultimi 3 allenamenti in riga da taccuino. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviousBlock(card: SessionCard, unit: WeightUnit) {
    val exercise = card.exercise
    val shown = card.previousSets.take(3)
    if (shown.isEmpty()) {
        Text(tr("Nessun allenamento precedente"), Modifier.padding(top = 2.dp), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textTertiary)
        return
    }
    val context = LocalContext.current
    // parte dall'ultima scelta (per tutte le card, su questo telefono) e la riscrive quando scorri
    val prefs = remember { context.applicationContext.getSharedPreferences("peak-ui", android.content.Context.MODE_PRIVATE) }
    val pager = rememberPagerState(initialPage = prefs.getInt("peak.prevPage", 0).coerceIn(0, 1)) { 2 }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { prefs.edit().putInt("peak.prevPage", it).apply() } }
    val relative = exercise.showsRelative
    val side = rememberTr()

    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(tr("Scorso:"), style = PeakTheme.rounded(11, FontWeight.Bold), color = PeakTheme.textTertiary)
        HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            if (page == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.forEachIndexed { idx, prev ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${idx + 1}.", Modifier.width(16.dp), style = PeakTheme.rounded(11, FontWeight.SemiBold), color = PeakTheme.textTertiary)
                            Text(
                                Fmt.setLine(shownWeight(exercise, prev.weight), prev.reps, unit, exercise.isOneLimbLine(prev.singleArm), exercise.group, side),
                                style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (day in card.previousDays) {
                        val line = Fmt.notebookLine(
                            day.sets.map { Fmt.NotebookSet(if (relative) maxOf(0.0, it.weight - exercise.barWeight) else it.weight, it.reps, exercise.isOneLimbLine(it.singleArm)) },
                            unit, exercise.group, side,
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = PeakTheme.textTertiary)) { append(WorkoutDates.shortDate(day.date)) }
                                append("  $line")
                            },
                            style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 2) Box(Modifier.size(5.dp).background(PeakTheme.textTertiary.copy(alpha = if (pager.currentPage == i) 0.9f else 0.35f), CircleShape))
        }
    }
}

// ---- una riga di serie ------------------------------------------------------------------------

/**
 * Una riga di serie. Quando `pulse` sale (è la serie di un nuovo massimale) lo sfondo verde si accende e torna in ~0,6 s e il badge PR
 * rimbalza. Tocco = modifica, tocco lungo = menu (modifica / elimina).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionSetRow(index: Int, set: WorkoutSetEntity, exercise: ExerciseEntity?, unit: WeightUnit, pulse: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) { glow.animateTo(1f, tween(150)); glow.animateTo(0f, tween(450)) }
    }
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val relative = exercise?.showsRelative == true
    val group = exercise?.group
    val oneLimb = exercise?.isOneLimbLine(set.singleArm) ?: false
    Box {
        Row(
            Modifier.fillMaxWidth()
                .background(if (set.isPersonalRecord) PeakTheme.positive.copy(alpha = 0.10f) else Color.Transparent, shape)
                .background(PeakTheme.positive.copy(alpha = 0.32f * glow.value), shape)
                .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
                .padding(vertical = 5.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${index + 1}", Modifier.width(18.dp), style = PeakTheme.rounded(11, FontWeight.Bold), color = PeakTheme.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            val color = if (set.isWarmup) PeakTheme.textSecondary else PeakTheme.textPrimary
            if (relative) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(setLineText(maxOf(0.0, set.weight - exercise!!.barWeight), set.reps, unit, oneLimb, group), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = color)
                    Text(tr("(tot. %@)", Fmt.weight(set.weight, unit)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                }
            } else {
                Text(setLineText(set.weight, set.reps, unit, oneLimb, group), style = PeakTheme.rounded(15, FontWeight.SemiBold), color = color)
            }
            if (set.isPersonalRecord) PRBadge(Modifier.bounce(pulse, 1.35f))
            if (set.isWarmup) Text(tr("risc."), style = PeakTheme.rounded(10, FontWeight.SemiBold), color = PeakTheme.textTertiary)
            Spacer(Modifier.weight(1f))
            if (set.reps > 1 && !set.isWarmup) {
                Text("≈ ${Fmt.weight(set.estimatedOneRepMax(), unit)}", style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            }
        }
        DropdownMenu(menu, { menu = false }, containerColor = PeakTheme.bgRaised) {
            if (exercise != null) MenuItem("Modifica serie", WorkoutsIcons.pencil) { menu = false; onEdit() }
            MenuItem("Elimina serie", WorkoutsIcons.trash, PeakTheme.negative) { menu = false; onDelete() }
        }
    }
}
