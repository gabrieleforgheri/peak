package com.gabrieleforghieri.peak.ui.workouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.design.ConfirmSheet
import com.gabrieleforghieri.peak.design.LocalAccent
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.domain.Plan
import com.gabrieleforghieri.peak.ui.nav.LocalApp
import com.gabrieleforghieri.peak.ui.nav.LocalAppSettings
import com.gabrieleforghieri.peak.ui.nav.LocalPeakNav
import com.gabrieleforghieri.peak.ui.nav.LocalRepository
import com.gabrieleforghieri.peak.ui.start.TourState
import com.gabrieleforghieri.peak.util.SoundFX
import com.gabrieleforghieri.peak.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La sessione di allenamento: si aggiungono esercizi e serie, i record scattano da soli (`WorkoutSessionView`). Legge i dati, tiene i fogli
 * aperti e collega ogni gesto alla sua scrittura (`WorkoutsActions`); il disegno è in [WorkoutSessionContent].
 */
@Composable
fun WorkoutSessionScreen(workoutId: String) {
    val app = LocalApp.current
    val repository = LocalRepository.current
    val nav = LocalPeakNav.current
    val settings = LocalAppSettings.current
    val feedback = rememberFeedback()
    val scope = rememberCoroutineScope()
    val actions = remember(app) { WorkoutsActions(app.database, app.records) }

    val workout by repository.workout(workoutId).collectAsState(null)
    val sets by repository.setsOfWorkout(workoutId).collectAsState(null)
    val exercises by repository.exercises.collectAsState(emptyList())
    val allSets by repository.sets.collectAsState(emptyList())
    val workouts by repository.workouts.collectAsState(emptyList())
    val templates by repository.templates.collectAsState(emptyList())
    val restEnd by RestTimer.end.collectAsState()
    val pendingSummary by nav.requests.pendingSummary.collectAsState()

    // l'esercizio che stai facendo adesso (quello evidenziato)
    var currentExercise by remember { mutableStateOf<String?>(null) }
    var addSetTarget by remember { mutableStateOf<ExerciseEntity?>(null) }
    var editingSet by remember { mutableStateOf<WorkoutSetEntity?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var templateToUpdate by remember { mutableStateOf<WorkoutTemplateEntity?>(null) }
    var showFocus by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var muscleDetail by remember { mutableStateOf<ExerciseEntity?>(null) }
    // la serie dell'ultimo record e quante feste di record ci sono state (il contatore fa ripartire coriandoli, toast e riga)
    var recordSet by remember { mutableStateOf<String?>(null) }
    var recordTick by remember { mutableIntStateOf(0) }
    // le serie che c'erano già: una serie che compare e non è qui dentro è nuova
    var knownSets by remember { mutableStateOf<Set<String>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val byUuid = exercises.associateBy { it.uuid }
    val exists: (String) -> Boolean = { it in byUuid }

    fun celebrateRecord(set: String) {
        recordSet = set
        recordTick += 1
        feedback.sound(SoundFX.Effect.record)
    }

    // la sessione è a schermo: lo sa il resto dell'app e il tutorial
    DisposableEffect(workoutId) {
        nav.requests.visibleSession.value = workoutId
        onDispose { if (nav.requests.visibleSession.value == workoutId) nav.requests.visibleSession.value = null }
    }
    val active = workout?.isActive == true
    LaunchedEffect(active) { if (active) TourState.inSession = true }
    DisposableEffect(Unit) { onDispose { TourState.inSession = false } }

    // il solo punto in cui si accorge di una serie nuova, da qualsiasi parte arrivi (+ Serie, ripeti): record o serie normale
    LaunchedEffect(sets) {
        val current = sets ?: return@LaunchedEffect
        val known = knownSets
        knownSets = current.map { it.uuid }.toSet()
        if (known == null) return@LaunchedEffect
        val added = current.filter { it.uuid !in known }
        if (workout?.isActive != true || added.isEmpty()) return@LaunchedEffect
        val record = added.firstOrNull { it.isPersonalRecord }
        if (record != null) celebrateRecord(record.uuid) else feedback.sound(SoundFX.Effect.set)
    }

    // il recupero: finito il tempo, una vibrazione e via
    LaunchedEffect(restEnd) {
        val end = restEnd ?: return@LaunchedEffect
        delay(maxOf(0L, end - System.currentTimeMillis()))
        feedback.success()
        RestTimer.stop()
    }

    // la nota si salva a fine digitazione, non a ogni tasto: ogni salvataggio fa rileggere le schede e parte la sincronizzazione
    LaunchedEffect(workout?.uuid) { if (note == null) workout?.let { note = it.note } }
    LaunchedEffect(note) {
        val text = note ?: return@LaunchedEffect
        if (text == workout?.note) return@LaunchedEffect
        delay(1000)
        actions.setNote(workoutId, text)
    }

    fun presentSummary() { scope.launch { delay(350); showSummary = true } }

    // dopo la chiusura: se la lista è cambiata si chiede di aggiornare il giorno, poi il riepilogo
    fun afterFinish() {
        scope.launch {
            delay(350)
            val w = workout ?: return@launch
            val changed = changedTemplate(w, sets.orEmpty(), templates, exists)
            if (changed != null) templateToUpdate = changed else showSummary = true
        }
    }

    // «Termina» da fuori (la notifica, il widget) ha già chiuso l'allenamento: qui resta il resto del congedo
    LaunchedEffect(pendingSummary, active, workout?.uuid) {
        if (pendingSummary == workoutId && workout != null && !active) {
            nav.requests.pendingSummary.value = null
            afterFinish()
        }
    }

    fun startRest(exercise: ExerciseEntity, warmup: Boolean) {
        val seconds = settings.restDuration(exercise.restSeconds, warmup)
        if (settings.restTimerEnabled && workout?.isActive == true && seconds > 0) RestTimer.start(seconds)
    }

    fun select(exercise: ExerciseEntity) {
        currentExercise = exercise.uuid
        feedback.tap()
    }

    fun editPlan(change: (List<com.gabrieleforghieri.peak.domain.TemplateItem>) -> List<com.gabrieleforghieri.peak.domain.TemplateItem>) {
        val w = workout ?: return
        scope.launch { actions.setPlan(workoutId, editedPlan(w, sets.orEmpty(), exists, change)) }
    }

    fun addSet(exercise: ExerciseEntity, weight: Double, reps: Int, warmup: Boolean, arm: Boolean) {
        scope.launch {
            val isRecord = actions.addSet(workoutId, exercise.uuid, weight, reps, warmup, arm)
            startRest(exercise, warmup)
            currentExercise = exercise.uuid
            if (isRecord) feedback.personalRecord() else feedback.commit()
        }
    }

    fun lastSetOf(exercise: ExerciseEntity): WorkoutSetEntity? =
        lastSet(setsFor(sets.orEmpty(), exercise.uuid), allSets.filter { it.exerciseUuid == exercise.uuid })

    val w = workout
    val s = sets
    if (w == null || s == null) {
        Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current)))
        return
    }

    val ui = sessionUi(w, s, exercises, allSets, workouts.associate { it.uuid to it.date }, settings.unit, currentExercise)
    Box(Modifier.fillMaxSize().then(PeakTheme.backgroundModifier(LocalAccent.current))) {
        WorkoutSessionContent(
            ui = ui, unit = settings.unit, note = note ?: w.note, restEnd = restEnd, recordSet = recordSet, recordTick = recordTick,
            events = SessionEvents(
                onBack = { nav.back() },
                onSelect = ::select,
                onAddSet = { addSetTarget = it },
                onRepeat = { ex -> lastSetOf(ex)?.let { addSet(ex, it.weight, it.reps, it.isWarmup, ex.isOneLimbLine(it.singleArm)) } },
                onEditSet = { editingSet = it },
                onDeleteSet = { set ->
                    scope.launch {
                        actions.deleteSet(set.uuid)
                        feedback.warning()
                        feedback.sound(SoundFX.Effect.remove)
                    }
                },
                onDetail = { nav.openExercise(it.uuid) },
                onShowMuscles = { muscleDetail = it },
                onMove = { ex, step -> editPlan { Plan.moving(it, ex.uuid, step) } },
                onReplace = { replacing = it },
                onRemove = { ex ->
                    editPlan { Plan.removing(it, ex.uuid) }
                    if (currentExercise == ex.uuid) currentExercise = null
                },
                onAddExercise = { showPicker = true },
                onSaveAsDay = { showSaveTemplate = true },
                onNoteChange = { note = it },
                onFinish = { showFinishConfirm = true },
                onDelete = { showDeleteConfirm = true },
                onEditFocus = { showFocus = true },
                onRestShift = { seconds -> RestTimer.shift(seconds); feedback.tap() },
                onRestStop = { RestTimer.stop() },
            ),
        )
    }

    // conferme in un foglio basso nello stile dell'app
    if (showFinishConfirm) {
        ConfirmSheet(
            "Terminare l'allenamento?", "Termina", {
                scope.launch {
                    actions.finish(workoutId)
                    RestTimer.stop()
                    feedback.success()
                    afterFinish()
                }
            }, { showFinishConfirm = false }, cancel = "Continua ad allenarti", tint = LocalAccent.current,
        )
    }
    if (showDeleteConfirm) {
        ConfirmSheet(
            "Eliminare questo allenamento e le sue serie?", "Elimina allenamento", {
                RestTimer.stop()
                feedback.warning()
                nav.back()
                // la cancellazione vera dopo che la sessione è sparita: finché è a schermo legge ancora i dati
                app.mainScope.launch { delay(350); actions.deleteWorkout(workoutId) }
            }, { showDeleteConfirm = false }, tint = PeakTheme.negative,
        )
    }

    if (showPicker) {
        ExercisePickerSheet(
            focus = w.focusGroups, onDismiss = { showPicker = false },
            onSelect = { exercise ->
                editPlan { com.gabrieleforghieri.peak.domain.Plan.adding(it, exercise.uuid) }
                // solo la scelta: peso e ripetizioni li decidi dopo, con "+ Serie"
                select(exercise)
            },
        )
    }
    replacing?.let { old ->
        ExercisePickerSheet(
            focus = w.focusGroups, onDismiss = { replacing = null },
            onSelect = { new ->
                if (ui.cards.any { it.exercise.uuid == new.uuid }) feedback.warning()
                else {
                    editPlan { Plan.replacing(it, old.uuid, new.uuid) }
                    select(new)
                }
            },
        )
    }
    if (showSaveTemplate) {
        TemplateEditorSheet({ showSaveTemplate = false }, initialName = w.name, initialItems = sessionItems(w, s, exists, Plan.Mode.done))
    }
    addSetTarget?.let { ex ->
        AddSetSheet(ex, SetMode.Add(lastSetOf(ex)), { addSetTarget = null }) { weight, reps, warmup, arm -> addSet(ex, weight, reps, warmup, arm) }
    }
    editingSet?.let { set ->
        val ex = set.exerciseUuid?.let { byUuid[it] }
        if (ex != null) {
            AddSetSheet(ex, SetMode.Edit(set), { editingSet = null }) { weight, reps, warmup, arm ->
                scope.launch {
                    val isRecord = actions.updateSet(set.uuid, weight, reps, warmup, arm)
                    if (isRecord) celebrateRecord(set.uuid) // una correzione che è massimale: festa
                }
            }
        }
    }
    muscleDetail?.let { MuscleDetailSheet(it) { muscleDetail = null } }
    if (showFocus) {
        FocusPickerSheet(confirmTitle = "Salva", onDismiss = { showFocus = false }, initialName = w.name, initial = w.focusGroups) { name, focus ->
            scope.launch { actions.setNameAndFocus(workoutId, name, com.gabrieleforghieri.peak.domain.WorkoutFocus.encode(focus)) }
        }
    }
    templateToUpdate?.let { template ->
        ConfirmSheet(
            tr("Aggiornare il giorno «%@» con le modifiche di oggi?", template.name), "Aggiorna",
            { scope.launch { actions.updateTemplateItems(template.uuid, sessionItems(w, s, exists, Plan.Mode.targets)) } },
            { templateToUpdate = null; presentSummary() }, cancel = "No",
        )
    }
    if (showSummary) WorkoutSummarySheet(workoutId) { showSummary = false; nav.back() }
}
