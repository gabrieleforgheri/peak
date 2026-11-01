package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.gabrieleforghieri.peak.util.tr as contextTr
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.GlassCard
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.MonthStack
import com.gabrieleforghieri.peak.design.PRBadge
import com.gabrieleforghieri.peak.design.PeakIcons
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.SectionHeader
import com.gabrieleforghieri.peak.design.appear
import com.gabrieleforghieri.peak.design.pressable
import com.gabrieleforghieri.peak.design.tint
import com.gabrieleforghieri.peak.domain.Fmt
import com.gabrieleforghieri.peak.domain.Programs
import com.gabrieleforghieri.peak.domain.TemplateIcon
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.ui.start.tourTarget
import com.gabrieleforghieri.peak.util.tr

/** Quello che la lista allenamenti legge (lo costruisce [WorkoutsListScreen]). */
data class WorkoutsListData(
    val name: String,
    val stats: HomeStats?,
    val active: WorkoutEntity?,
    val activeSets: List<WorkoutSetEntity>,
    val today: WorkoutTemplateEntity?,
    val programs: List<WorkoutProgramEntity>,
    val program: WorkoutProgramEntity?,
    val days: List<WorkoutTemplateEntity>,
    /** Gli allenamenti conclusi, dal più recente. */
    val finished: List<WorkoutEntity>,
    val setsByWorkout: Map<String, List<WorkoutSetEntity>>,
    val exercises: Map<String, ExerciseEntity>,
)

class WorkoutsListEvents(
    val onAvatar: () -> Unit = {},
    val onChangeGoal: () -> Unit = {},
    val onResume: (WorkoutEntity) -> Unit = {},
    val onStartTemplate: (WorkoutTemplateEntity) -> Unit = {},
    val onPickFocus: () -> Unit = {},
    val onSelectProgram: (WorkoutProgramEntity) -> Unit = {},
    val onEditProgram: (WorkoutProgramEntity) -> Unit = {},
    val onNewProgram: () -> Unit = {},
    val onEditTemplate: (WorkoutTemplateEntity) -> Unit = {},
    val onNewTemplate: () -> Unit = {},
    val onOpenWorkout: (WorkoutEntity) -> Unit = {},
    val onDeleteWorkout: (WorkoutEntity) -> Unit = {},
)

/**
 * La scheda Allenamento (`WorkoutsListView.swift`): l'eroe con la montagna, la sessione in corso o il pulsante per partire, la scheda con i
 * suoi giorni e lo storico per mese. Niente fogli e niente scritture: stanno in [WorkoutsListScreen].
 */
@Composable
fun WorkoutsListContent(
    data: WorkoutsListData,
    unit: WeightUnit,
    events: WorkoutsListEvents,
    modifier: Modifier = Modifier,
    celebrated: Celebrated? = null,
    feedback: WorkoutFeedback = WorkoutFeedback.None,
    hour: Int = java.time.LocalTime.now().hour,
) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        ) {
            data.stats?.let { s ->
                item("hero") {
                    Box(Modifier.appear(0)) {
                        WorkoutHero(data.name, s.streak, s.weekSessions, s.goal, events.onAvatar, events.onChangeGoal, celebrated = celebrated, feedback = feedback, hour = hour)
                    }
                }
            }

            item("start") { Box(Modifier.appear(1).padding(bottom = 4.dp)) { StartCard(data, unit, events) } }

            item("program-title") { SectionHeaderAction("Scheda", "Nuova scheda", events.onNewProgram, Modifier.padding(top = 6.dp, start = 2.dp)) }
            if (data.programs.size > 1) item("programs") {
                Row(Modifier.appear(2).scrollsSideways(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in data.programs) {
                        val on = p.uuid == data.program?.uuid
                        Text(
                            p.name,
                            Modifier.background(if (on) accent.copy(alpha = 0.22f) else PeakTheme.card, CircleShape).pressable { events.onSelectProgram(p) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = PeakTheme.rounded(13, FontWeight.SemiBold), color = if (on) accent else PeakTheme.textSecondary,
                        )
                    }
                }
            }
            data.program?.let { p ->
                item("program") {
                    Row(Modifier.appear(2).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1)
                        Text(tr("Modifica scheda"), Modifier.clickableText { events.onEditProgram(p) }, style = PeakTheme.rounded(13, FontWeight.Bold), color = accent)
                    }
                }
            }
            if (data.days.isEmpty()) {
                item("no-days") {
                    Text(
                        tr("Un giorno è una lista di esercizi con serie e ripetizioni: lo scegli quando inizi e li trovi già pronti."),
                        style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary,
                    )
                }
            } else {
                data.days.forEachIndexed { i, template ->
                    item("day-" + template.uuid) { Box(Modifier.appear(3 + i, "day-" + template.uuid)) { DayRow(template, data.program, events) } }
                }
            }
            item("new-day") { SecondaryLabelButton(PeakIcons.plus, tr("Nuovo giorno"), events.onNewTemplate, Modifier.tourTarget("new-template")) }

            if (data.finished.isEmpty()) {
                item("no-history") {
                    Text(
                        tr("Gli allenamenti conclusi finiscono qui, con volume e record della giornata."),
                        Modifier.fillMaxWidth().padding(top = 40.dp), style = PeakTheme.rounded(13, FontWeight.Medium), color = PeakTheme.textTertiary, textAlign = TextAlign.Center,
                    )
                }
            } else {
                item("history-title") { SectionHeader("Storico", Modifier.padding(top = 14.dp), trailing = "${data.finished.size}") }
                item("history") {
                    MonthStack(
                        data.finished, { it.date },
                        { n -> if (n == 1) context.contextTr("1 allenamento") else context.contextTr("%lld allenamenti", n) },
                    ) { workout ->
                        Box(Modifier.appear(0, "wk-" + workout.uuid)) {
                            WorkoutRow(workout, data.setsByWorkout[workout.uuid].orEmpty(), data.exercises, unit, { events.onOpenWorkout(workout) }, { events.onDeleteWorkout(workout) })
                        }
                    }
                }
            }
        }
    }
}

/** La sessione in corso in una card; altrimenti il pulsante per partire. */
@Composable
private fun StartCard(data: WorkoutsListData, unit: WeightUnit, events: WorkoutsListEvents) {
    val accent = LocalAccent.current
    val active = data.active
    val today = data.today
    when {
        active != null -> GlassCard(padding = 18.dp, tint = accent) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Text(tr("SESSIONE IN CORSO"), Modifier.weight(1f), style = PeakTheme.rounded(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = accent)
                Text(WorkoutDates.dateTime(active.date), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            }
            if (active.name.isNotEmpty()) Text(active.name, Modifier.padding(top = 14.dp), style = PeakTheme.rounded(19, FontWeight.Bold), color = PeakTheme.textPrimary)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat("${data.activeSets.workingSetCount}", "serie")
                MiniStat(Fmt.volume(data.activeSets.totalVolume, unit), "volume")
                MiniStat("${data.activeSets.mapNotNull { it.exerciseUuid }.distinct().size}", "esercizi")
            }
            PrimaryLabelButton(WorkoutsIcons.play, tr("Riprendi allenamento"), { events.onResume(active) }, Modifier.padding(top = 14.dp).tourTarget("start"))
        }
        today != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NextWorkoutBar(
                tr("Avvia %@", today.name), tr(exerciseCountText(today.itemList.size)), WorkoutsIcons.template(TemplateIcon.resolved(today.icon, today.name)),
                Modifier.tourTarget("start"),
            ) { events.onStartTemplate(today) }
            Text(tr("oppure scegli tu"), Modifier.clickableText(events.onPickFocus), style = PeakTheme.rounded(13, FontWeight.Bold), color = accent)
        }
        else -> PrimaryLabelButton(WorkoutsIcons.bolt, tr("Avvia allenamento"), events.onPickFocus, Modifier.tourTarget("start"))
    }
}

@Composable
private fun DayRow(template: WorkoutTemplateEntity, program: WorkoutProgramEntity?, events: WorkoutsListEvents) {
    val accent = LocalAccent.current
    GlassCard(Modifier.pressable { events.onEditTemplate(template) }, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { TemplateIconView(template.icon, template.name, size = 20) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(template.name, style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary)
                Text(tr(exerciseCountText(template.itemList.size)), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
            }
            if (program?.useDays == true) {
                Text(
                    if (template.weekday >= 0) tr(Programs.weekdays[template.weekday]) else tr("Nessuno"),
                    style = PeakTheme.rounded(11, FontWeight.Bold), color = if (template.weekday >= 0) accent else PeakTheme.textTertiary,
                )
            }
            Icon(PeakIcons.chevronRight, null, Modifier.size(14.dp), tint = PeakTheme.textTertiary)
        }
    }
}

/** Una riga dello storico; scorrendola da destra si elimina (con la conferma). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutRow(workout: WorkoutEntity, sets: List<WorkoutSetEntity>, exercises: Map<String, ExerciseEntity>, unit: WeightUnit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
        false
    })
    SwipeToDismissBox(
        state, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(PeakTheme.negative, RoundedCornerShape(PeakTheme.radius)).padding(end = 22.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(WorkoutsIcons.trash, tr("Elimina"), Modifier.size(22.dp), tint = Color.White)
            }
        },
    ) {
        val prCount = sets.count { it.isPersonalRecord }
        val groups = groupsInOrder(sets, exercises)
        GlassCard(Modifier.pressable(onClick = onOpen), padding = 14.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(workoutTitle(workout.name, workout.date), style = PeakTheme.rounded(15, FontWeight.Bold), color = PeakTheme.textPrimary, maxLines = 1)
                    if (workout.name.isNotEmpty()) Text(relativeDay(workout.date), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary)
                }
                if (prCount > 0) PRBadge(text = if (prCount == 1) "1 PR" else "$prCount PR")
                Spacer(Modifier.weight(1f))
                workout.durationSeconds?.let { Text(Fmt.duration(it), style = PeakTheme.rounded(11, FontWeight.Medium), color = PeakTheme.textTertiary) }
            }
            if (workout.note.isNotEmpty()) {
                Text(workout.note, Modifier.padding(top = 10.dp), style = PeakTheme.rounded(12, FontWeight.Medium), color = PeakTheme.textSecondary, maxLines = 2)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniStat("${sets.workingSetCount}", "serie")
                MiniStat(Fmt.volume(sets.totalVolume, unit), "volume")
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (g in groups.take(4)) Icon(PeakIcons.group(g), null, Modifier.size(13.dp), tint = g.tint)
                }
            }
        }
    }
}
