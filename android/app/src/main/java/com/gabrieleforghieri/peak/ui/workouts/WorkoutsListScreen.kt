package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.ConfirmSheet
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Stats
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.nav.Tab
import com.gabrieleforghieri.peak.ui.nav.rememberUpdateSettings
import com.gabrieleforghieri.peak.ui.start.GoalPickerSheet
import kotlinx.coroutines.launch

/**
 * La scheda Allenamento (`WorkoutsListView`): legge i dati, tiene i fogli aperti (cosa alleni oggi, giorno, scheda, obiettivo, conferma) e
 * collega ogni gesto alla sua scrittura. Il disegno è in [WorkoutsListContent].
 */
@Composable
fun WorkoutsListScreen() {
    val app = LocalApp.current
    val repository = LocalRepository.current
    val nav = LocalPeakNav.current
    val settings = LocalAppSettings.current
    val update = rememberUpdateSettings()
    val feedback = rememberFeedback()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actions = remember(app) { WorkoutsActions(app.database, app.records) }
    val celebrated = remember { Celebrated(context) }

    val workouts by repository.workouts.collectAsState(emptyList())
    val templates by repository.templates.collectAsState(emptyList())
    val programs by repository.programs.collectAsState(emptyList())
    val exercises by repository.exercises.collectAsState(emptyList())
    val sets by repository.sets.collectAsState(emptyList())
    val syncStatus by app.sync.engine.status.collectAsState()
    val templateToStart by nav.requests.templateToStart.collectAsState()
    val resumeActive by nav.requests.resumeActive.collectAsState()

    var showingFocusPicker by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<WorkoutTemplateEntity?>(null) }
    var showingNewTemplate by remember { mutableStateOf(false) }
    var editingProgram by remember { mutableStateOf<WorkoutProgramEntity?>(null) }
    var showingNewProgram by remember { mutableStateOf(false) }
    var choosingGoal by remember { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }

    val active = workouts.firstOrNull { it.isActive }
    val finished = workouts.filter { !it.isActive }
    val setsByWorkout = remember(sets) { sets.groupBy { it.workoutUuid.orEmpty() } }
    val program = activeProgram(programs, settings.activeProgram)
    val finishedDates = finished.map { it.date }
    val name = syncStatus.credentials?.name.orEmpty().let { full -> full.split(' ').firstOrNull { it.isNotEmpty() } ?: "Peak" }

    fun begin(workout: WorkoutEntity) {
        scope.launch {
            actions.begin(workout)
            feedback.commit()
            nav.openWorkout(workout.uuid)
        }
    }
    fun startWorkout(template: WorkoutTemplateEntity) = begin(workoutFromTemplate(template, exercises, System.currentTimeMillis()))
    fun startWorkout(name: String, focus: Set<MuscleGroup>) = begin(workoutFromFocus(name, focus, System.currentTimeMillis()))

    // dall'esterno (il tutorial, un collegamento) possono chiedere di riprendere la sessione o di avviare un giorno: si fa appena questa scheda è in vista
    LaunchedEffect(resumeActive, active?.uuid) {
        if (!resumeActive) return@LaunchedEffect
        nav.requests.resumeActive.value = false
        active?.let { nav.openWorkout(it.uuid) }
    }
    LaunchedEffect(templateToStart, templates) {
        val id = templateToStart ?: return@LaunchedEffect
        nav.requests.templateToStart.value = null
        templates.firstOrNull { it.uuid == id }?.let { startWorkout(it) } ?: run { showingFocusPicker = true }
    }

    val data = WorkoutsListData(
        name = name,
        stats = homeStats(finishedDates, settings.weeklyGoal),
        active = active, activeSets = active?.let { setsByWorkout[it.uuid] }.orEmpty(),
        today = todayTemplate(program, templates), programs = programs, program = program, days = daysOf(program, templates),
        finished = finished, setsByWorkout = setsByWorkout, exercises = exercises.associateBy { it.uuid },
    )
    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) {
        WorkoutsListContent(
            data, settings.unit, celebrated = celebrated, feedback = feedback,
            events = WorkoutsListEvents(
                onAvatar = { nav.goTab(Tab.me) },
                onChangeGoal = { choosingGoal = true },
                onResume = { nav.openWorkout(it.uuid) },
                onStartTemplate = ::startWorkout,
                onPickFocus = { showingFocusPicker = true },
                onSelectProgram = { p -> update { copy(activeProgram = p.uuid) }; feedback.tap() },
                onEditProgram = { editingProgram = it },
                onNewProgram = { showingNewProgram = true },
                onEditTemplate = { editingTemplate = it },
                onNewTemplate = { showingNewTemplate = true },
                onOpenWorkout = { nav.openWorkout(it.uuid) },
                onDeleteWorkout = { workoutToDelete = it },
            ),
        )
    }

    if (choosingGoal) GoalPickerSheet(settings.weeklyGoal, Stats.weeklyGoal(finishedDates)) { choosingGoal = false }
    if (showingFocusPicker) {
        FocusPickerSheet(
            confirmTitle = "Inizia", onDismiss = { showingFocusPicker = false },
            onStartTemplate = { startWorkout(it) }, onConfirm = { name, focus -> startWorkout(name, focus) },
        )
    }
    editingTemplate?.let { TemplateEditorSheet({ editingTemplate = null }, template = it) }
    if (showingNewTemplate) TemplateEditorSheet({ showingNewTemplate = false })
    editingProgram?.let { ProgramEditorSheet(it) { editingProgram = null } }
    if (showingNewProgram) ProgramEditorSheet(null) { showingNewProgram = false }
    workoutToDelete?.let { workout ->
        ConfirmSheet(
            "Eliminare questo allenamento e le sue serie?", "Elimina",
            { scope.launch { actions.deleteWorkout(workout.uuid); feedback.warning() } },
            { workoutToDelete = null }, tint = PeakTheme.negative,
        )
    }
}
