package com.gabrieleforghieri.peak.ui.workouts

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.data.catalogEntry
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.data.machineGlyph
import com.gabrieleforghieri.peak.domain.DayRef
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.Medal
import com.gabrieleforghieri.peak.domain.Medals
import com.gabrieleforghieri.peak.domain.MaxRanking
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.domain.Plan
import com.gabrieleforghieri.peak.domain.PlanSet
import com.gabrieleforghieri.peak.domain.Plates
import com.gabrieleforghieri.peak.domain.ProgramRef
import com.gabrieleforghieri.peak.domain.Programs
import com.gabrieleforghieri.peak.domain.Stats
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.domain.WeightUnit
import com.gabrieleforghieri.peak.domain.WorkoutFocus

// La logica pura dell'area Allenamenti: niente Android, niente Compose, niente database. Le schermate leggono i dati, passano da qui per
// decidere cosa mostrare e cosa scrivere; `WorkoutsActions` scrive. Tutto provato in `WorkoutsLogicTest`.
// Gemelli dei calcoli sparsi nelle viste di `Features/Workouts/` e in `Workout.swift`, `Exercise.swift`, `Limbs.swift`.

// ---- allenamento e serie --------------------------------------------------------------------

val WorkoutEntity.isActive: Boolean get() = endedAt == null

/** Durata in secondi di un allenamento chiuso (`Workout.duration`). */
val WorkoutEntity.durationSeconds: Double? get() = endedAt?.let { (it - date) / 1000.0 }

val WorkoutEntity.focusGroups: Set<MuscleGroup> get() = WorkoutFocus.decode(focus)

val WorkoutEntity.planItems: List<TemplateItem> get() = TemplateItems.decode(plan)

val WorkoutTemplateEntity.itemList: List<TemplateItem> get() = TemplateItems.decode(items)

/** Serie di lavoro: il riscaldamento non conta nei totali (`Workout.workingSetCount`). */
val List<WorkoutSetEntity>.workingSetCount: Int get() = count { !it.isWarmup }

/** Volume totale (kg × ripetizioni), riscaldamento escluso (`Workout.totalVolume`). */
val List<WorkoutSetEntity>.totalVolume: Double get() = sumOf { if (it.isWarmup) 0.0 else it.weight * it.reps }

/** Come si fa il macchinario (`Exercise.limbs`). */
val ExerciseEntity.limbsMode: Limbs get() = Limbs.limbsOf(limbs, isUnilateral, catalogID, name, muscleGroup)

/** Il peso non si dimezza passando a un arto (manubri, cavi): `Exercise.twoHandles`. */
val ExerciseEntity.twoHandles: Boolean get() = Limbs.twoHandles(catalogID, name, machineGlyph?.raw ?: "")

/** La serie o il massimale con questo flag stanno sulla linea a un arto di questo macchinario? (`Exercise.isOneLimbLine`) */
fun ExerciseEntity.isOneLimbLine(singleArm: Boolean): Boolean = Limbs.lineArm(limbsMode, singleArm)

/** Il carico è mostrato «solo dischi» (la sbarra tolta) in questo macchinario? (`weightDisplay == "relative"`, a dischi interi, con sbarra). */
val ExerciseEntity.showsRelative: Boolean get() = weightDisplay == "relative" && !perSide && barWeight > 0

fun WorkoutSetEntity.estimatedOneRepMax(): Double = OneRepMax.estimate(weight, reps)

/** Gli esercizi toccati, nell'ordine in cui compaiono le serie (`Workout.exercisesInOrder`). `sets` in qualsiasi ordine. */
fun exercisesInOrder(sets: List<WorkoutSetEntity>, byUuid: Map<String, ExerciseEntity>): List<ExerciseEntity> =
    sets.sortedBy { it.order }.mapNotNull { it.exerciseUuid }.distinct().mapNotNull { byUuid[it] }

/** I gruppi muscolari toccati, nell'ordine in cui compaiono, senza doppioni (`WorkoutRow.groups`). */
fun groupsInOrder(sets: List<WorkoutSetEntity>, byUuid: Map<String, ExerciseEntity>): List<MuscleGroup> =
    exercisesInOrder(sets, byUuid).map { it.group }.distinct()

/** Le serie di un esercizio, in ordine (`Workout.sets(for:)`). */
fun setsFor(sets: List<WorkoutSetEntity>, exercise: String): List<WorkoutSetEntity> =
    sets.filter { it.exerciseUuid == exercise }.sortedBy { it.order }

// ---- la lista di oggi -----------------------------------------------------------------------

/** Il piano visto dalla lista (`Workout.planSets`). */
fun planSets(sets: List<WorkoutSetEntity>): List<PlanSet> =
    sets.sortedBy { it.order }.map { PlanSet(it.exerciseUuid, it.weight, it.reps, it.isWarmup) }

/** La lista di oggi in ordine (`Workout.items`): le righe del piano che esistono ancora, poi gli esercizi con serie fuori piano. */
fun sessionItems(workout: WorkoutEntity, sets: List<WorkoutSetEntity>, exists: (String) -> Boolean, mode: Plan.Mode = Plan.Mode.view): List<TemplateItem> =
    Plan.session(workout.planItems, planSets(sets), exists, mode)

/** Il piano da scrivere dopo una modifica: la lista di oggi cambiata (anche gli esercizi fuori piano diventano righe). */
fun editedPlan(workout: WorkoutEntity, sets: List<WorkoutSetEntity>, exists: (String) -> Boolean, change: (List<TemplateItem>) -> List<TemplateItem>): String =
    TemplateItems.encode(change(sessionItems(workout, sets, exists)))

/**
 * La scheda di partenza, se la lista di oggi è diversa da lei (e lei esiste ancora): a fine allenamento si chiede se aggiornarla
 * (`changedTemplate`). Gli obiettivi non contano, solo esercizi e ordine.
 */
fun changedTemplate(workout: WorkoutEntity, sets: List<WorkoutSetEntity>, templates: List<WorkoutTemplateEntity>, exists: (String) -> Boolean): WorkoutTemplateEntity? {
    val id = Uuids.normalize(workout.template) ?: return null
    val template = templates.firstOrNull { it.uuid == id } ?: return null
    val today = sessionItems(workout, sets, exists, Plan.Mode.targets)
    return template.takeIf { Plan.differs(today, it.itemList, exists) }
}

/** Un allenamento nuovo da un giorno della scheda: lista già pronta, il suo nome, i gruppi presi dagli esercizi (`startWorkout(template:)`). */
fun workoutFromTemplate(template: WorkoutTemplateEntity, exercises: List<ExerciseEntity>, now: Long, uuid: String = Uuids.new()): WorkoutEntity {
    val known = exercises.associateBy { it.uuid }
    val items = template.itemList.filter { it.exercise in known }
    return WorkoutEntity(
        uuid = uuid, date = now, name = template.name, template = template.uuid,
        plan = TemplateItems.encode(items),
        focus = WorkoutFocus.encode(items.mapNotNull { known[it.exercise]?.group }.toSet()),
    )
}

/** Un allenamento nuovo scegliendo nome e gruppi (`startWorkout(name:focus:)`). */
fun workoutFromFocus(name: String, focus: Set<MuscleGroup>, now: Long, uuid: String = Uuids.new()): WorkoutEntity =
    WorkoutEntity(uuid = uuid, date = now, name = name, focus = WorkoutFocus.encode(focus))

// ---- «Scorso» di un esercizio ----------------------------------------------------------------

/** Le serie di lavoro dell'ultimo altro allenamento con questo esercizio (`previousWorkingSets`). `exerciseSets`: tutte le serie del macchinario. */
fun previousWorkingSets(exerciseSets: List<WorkoutSetEntity>, workout: String, workoutDates: Map<String, Long>): List<WorkoutSetEntity> {
    val others = exerciseSets.filter { !it.isWarmup && it.workoutUuid != workout && it.workoutUuid in workoutDates }
    val last = others.mapNotNull { it.workoutUuid }.distinct().maxByOrNull { workoutDates.getValue(it) } ?: return emptyList()
    return others.filter { it.workoutUuid == last }.sortedBy { it.order }
}

/** Un giorno di «Scorso»: la data dell'allenamento e le sue serie di lavoro. */
data class PreviousDay(val date: Long, val sets: List<WorkoutSetEntity>)

/** Gli ultimi 3 allenamenti con questo esercizio, escluso quello in corso, dal più recente; riscaldamento fuori (`previousDays`). */
fun previousDays(exerciseSets: List<WorkoutSetEntity>, workout: String, workoutDates: Map<String, Long>): List<PreviousDay> {
    val others = exerciseSets.filter { !it.isWarmup && it.workoutUuid != workout && it.workoutUuid in workoutDates }
    val days = others.mapNotNull { it.workoutUuid }.distinct().sortedByDescending { workoutDates.getValue(it) }.take(3)
    return days.map { id -> PreviousDay(workoutDates.getValue(id), others.filter { it.workoutUuid == id }.sortedBy { it.order }) }
}

/**
 * La serie da cui parte «Serie» e «Ripeti» (`lastSet`): l'ultima di lavoro di oggi, altrimenti l'ultima di lavoro mai fatta su questo
 * macchinario, altrimenti l'ultima di oggi (anche di riscaldamento).
 */
fun lastSet(todaySets: List<WorkoutSetEntity>, exerciseSets: List<WorkoutSetEntity>): WorkoutSetEntity? =
    todaySets.sortedBy { it.order }.lastOrNull { !it.isWarmup }
        ?: exerciseSets.filter { !it.isWarmup }.maxByOrNull { it.createdAt }
        ?: todaySets.maxByOrNull { it.order }

/** Il peso mostrato di una serie in un macchinario a «solo dischi»: la sbarra tolta. */
fun shownWeight(exercise: ExerciseEntity?, kilograms: Double): Double =
    if (exercise != null && exercise.showsRelative) maxOf(0.0, kilograms - exercise.barWeight) else kilograms

// ---- l'inizio, le statistiche, l'obiettivo ----------------------------------------------------

/** I numeri dell'eroe (`HomeStats`): allenamenti della settimana, settimane di fila, obiettivo. */
data class HomeStats(val weekSessions: Int, val streak: Int, val goal: Int)

/** `chosenGoal`: l'obiettivo scelto (0 = non ancora: si propone quello che fai di solito). `finished`: le date degli allenamenti conclusi. */
fun homeStats(finished: List<Long>, chosenGoal: Int, now: Long = System.currentTimeMillis()): HomeStats {
    val start = Stats.weekStart(now)
    return HomeStats(
        weekSessions = finished.count { it >= start },
        streak = Stats.weeklyStreak(finished, now),
        goal = if (chosenGoal > 0) chosenGoal else Stats.weeklyGoal(finished, now),
    )
}

/** La scheda in uso fra quelle che ci sono (`Programs.active`). */
fun activeProgram(programs: List<WorkoutProgramEntity>, activeSetting: String): WorkoutProgramEntity? =
    Programs.active(programs, { it.uuid }, activeSetting)

fun daysOf(program: WorkoutProgramEntity?, templates: List<WorkoutTemplateEntity>): List<WorkoutTemplateEntity> {
    val refs = Programs.daysOf(program?.let { ProgramRef(it.uuid, it.useDays) }, templates.map { DayRef(it.uuid, it.name, it.program, it.weekday) })
    val ids = refs.map { it.uuid }.toSet()
    return templates.filter { it.uuid in ids }
}

/** Il giorno fisso di oggi, solo se la scheda usa i giorni della settimana. */
fun todayTemplate(program: WorkoutProgramEntity?, templates: List<WorkoutTemplateEntity>, now: Long = System.currentTimeMillis()): WorkoutTemplateEntity? {
    val ref = Programs.today(program?.let { ProgramRef(it.uuid, it.useDays) }, templates.map { DayRef(it.uuid, it.name, it.program, it.weekday) }, now) ?: return null
    return templates.firstOrNull { it.uuid == ref.uuid }
}

// ---- l'inserimento di una serie ---------------------------------------------------------------

/**
 * Come il campo peso legge il carico (`AddSetSheet`): totale, «solo dischi» (tolta la sbarra) o per lato (totale = 2 x + sbarra).
 * `weight` resta sempre il totale in kg: cambia solo come lo si mostra e lo si digita.
 */
class LoadMode(val perSide: Boolean, val relative: Boolean, val plateBase: Double?, private val barWeight: Double) {
    /** La sbarra da mostrare e togliere (kg): per lato è quella del macchinario, altrimenti la base del calcolatore. */
    val barKg: Double get() = if (perSide) barWeight else plateBase ?: 0.0

    /** Totale in kg → valore del campo (e delle serie recenti). */
    fun fieldKg(total: Double): Double = when {
        perSide -> maxOf(0.0, (total - barWeight) / 2)
        relative -> maxOf(0.0, total - (plateBase ?: 0.0))
        else -> total
    }

    /** Valore del campo in kg → totale. */
    fun totalKg(field: Double): Double = when {
        perSide -> field * 2 + barWeight
        relative -> field + (plateBase ?: 0.0)
        else -> field
    }

    companion object {
        /** Peso senza dischi, se l'esercizio si carica a dischi: bilanciere (anche panche e rack) o leva plate loaded (`plateBase`). */
        fun plateBase(exercise: ExerciseEntity, plateCalculatorEnabled: Boolean): Double? {
            if (!plateCalculatorEnabled) return null
            val glyph = exercise.machineGlyph ?: suggestedGlyph(exercise.name, exercise.group)
            return Plates.barBase(glyph, exercise.name, exercise.barWeight, exercise.plateLoaded)
        }

        fun of(exercise: ExerciseEntity, plateCalculatorEnabled: Boolean): LoadMode {
            val base = plateBase(exercise, plateCalculatorEnabled)
            return LoadMode(
                perSide = exercise.perSide,
                relative = !exercise.perSide && exercise.weightDisplay == "relative" && (base ?: 0.0) > 0,
                plateBase = base, barWeight = exercise.barWeight,
            )
        }
    }
}

/** Il massimale con cui si confronta la serie (`referenceRecord`): in modifica, senza il massimale della serie stessa. */
fun referenceRecord(exercise: ExerciseEntity, records: List<MaxRecordEntity>, singleArm: Boolean, editing: String?): MaxRecordEntity? {
    val line = records.filter { exercise.isOneLimbLine(it.singleArm) == singleArm && it.uuid != editing }
    return MaxRanking.best(line, { it.weight }, { it.reps }, { it.date })
}

/** Questa serie batterebbe il massimale (`isRecord`). */
fun beatsRecord(weight: Double, reps: Int, isWarmup: Boolean, reference: MaxRecordEntity?): Boolean =
    !isWarmup && MaxRanking.beats(weight, reps, reference?.let { MaxRanking.Performance(it.weight, it.reps) })

/** Differenza di carico con la serie di prima, nell'unità mostrata, a due decimali; `null` se è uguale (`delta`). */
fun weightDelta(weight: Double, previous: Double?, unit: WeightUnit): Double? {
    if (previous == null) return null
    val now = Math.round(unit.fromKilograms(weight) * 100) / 100.0
    val before = Math.round(unit.fromKilograms(previous) * 100) / 100.0
    return (now - before).takeIf { Math.abs(it) > 0.01 }
}

/** Il peso con cui parte il modulo di una serie nuova, se non ce n'è una da copiare (`prefill`): l'ultimo massimale a due braccia, altrimenti 4 passi. */
fun initialLoad(lastSet: WorkoutSetEntity?, latestRecord: MaxRecordEntity?, incrementStep: Double): Pair<Double, Int> = when {
    lastSet != null -> lastSet.weight to lastSet.reps
    latestRecord != null -> latestRecord.weight to maxOf(latestRecord.reps, 5)
    else -> incrementStep * 4 to 8
}

// ---- l'editor dei giorni ----------------------------------------------------------------------

/** Una riga in modifica: i campi sono testo, così restano vuoti finché non si scrive (`TemplateEditorView.Row`). */
data class TemplateRow(
    val exercise: String,
    val sets: String = "",
    val repsMin: String = "",
    val repsMax: String = "",
    val weight: String = "",
)

private fun numberText(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded == Math.rint(rounded)) rounded.toLong().toString() else rounded.toString()
}

/** I pesi si scrivono nell'unità scelta (`fillRows`). */
fun templateRows(items: List<TemplateItem>, unit: WeightUnit): List<TemplateRow> = items.map {
    TemplateRow(
        it.exercise,
        if (it.sets > 0) it.sets.toString() else "",
        if (it.repsMin > 0) it.repsMin.toString() else "",
        if (it.repsMax > 0) it.repsMax.toString() else "",
        it.weight?.let { w -> numberText(unit.fromKilograms(w)) } ?: "",
    )
}

/** Le righe come si salvano (`items()`): solo gli esercizi che ci sono, numeri nei limiti, basta una cifra per le ripetizioni. */
fun templateItems(rows: List<TemplateRow>, exists: (String) -> Boolean, unit: WeightUnit): List<TemplateItem> =
    rows.filter { exists(it.exercise) }.map { row ->
        fun int(text: String, max: Int) = minOf(max, maxOf(0, text.trim().toIntOrNull() ?: 0))
        var low = int(row.repsMin, 100)
        var high = int(row.repsMax, 100)
        if (low == 0) low = high
        if (high < low) high = low
        val typed = row.weight.replace(',', '.').trim().toDoubleOrNull()
        val weight = typed?.takeIf { it >= 0 }?.let { minOf(2000.0, unit.toKilograms(it)) }
        TemplateItem(row.exercise, int(row.sets, 20), low, high, weight)
    }

/** Scambia due righe vicine (le frecce su e giù). */
fun <T> List<T>.swapped(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    return toMutableList().also { it[a] = this[b]; it[b] = this[a] }
}

// ---- il pittogramma dedotto dal nome ----------------------------------------------------------

private val GLYPH_KEYWORDS: List<Pair<String, MachineGlyph>> = listOf(
    "leg press" to MachineGlyph.legPress, "pressa" to MachineGlyph.legPress, "hack squat" to MachineGlyph.legPress,
    "leg extension" to MachineGlyph.legLever, "leg curl" to MachineGlyph.legLever,
    "calf" to MachineGlyph.calfPlatform,
    "adductor" to MachineGlyph.machine, "abductor" to MachineGlyph.machine, "glute machine" to MachineGlyph.machine,
    "multipower" to MachineGlyph.smithMachine,
    "squat" to MachineGlyph.squatRack, "hip thrust" to MachineGlyph.barbell, "step up" to MachineGlyph.dumbbell,
    "affondi" to MachineGlyph.dumbbell, "bulgarian" to MachineGlyph.dumbbell,
    "lat machine" to MachineGlyph.latPulldown,
    "pulley" to MachineGlyph.seatedRow,
    "rematore bilanciere" to MachineGlyph.barbell, "rematore con manubrio" to MachineGlyph.dumbbell,
    "rematore ai cavi" to MachineGlyph.cableStation, "rematore t-bar" to MachineGlyph.barbell,
    "rematore" to MachineGlyph.seatedRow,
    "trazioni" to MachineGlyph.pullUpBar, "dip" to MachineGlyph.pullUpBar,
    "stacco" to MachineGlyph.barbell, "good morning" to MachineGlyph.barbell,
    "iperestensioni" to MachineGlyph.hyperBench,
    "pullover machine" to MachineGlyph.machine,
    "plate loaded" to MachineGlyph.plateLoaded,
    "pectoral" to MachineGlyph.pecDeck, "butterfly" to MachineGlyph.pecDeck,
    "croci ai cavi" to MachineGlyph.cableCross,
    "panca piana" to MachineGlyph.benchFlat, "panca stretta" to MachineGlyph.benchFlat,
    "panca inclinata" to MachineGlyph.benchIncline, "panca declinata" to MachineGlyph.benchIncline,
    "panca scott" to MachineGlyph.benchIncline,
    "panca addominali" to MachineGlyph.abBench,
    "chest press" to MachineGlyph.seatedPush, "shoulder press" to MachineGlyph.seatedPush,
    "lento avanti" to MachineGlyph.barbell,
    "sedia romana" to MachineGlyph.romanChair, "leg raise" to MachineGlyph.romanChair,
    "crunch machine" to MachineGlyph.machine, "rotary" to MachineGlyph.machine,
    "plank" to MachineGlyph.bodyweight, "piegamenti" to MachineGlyph.bodyweight,
    "russian" to MachineGlyph.bodyweight, "ab wheel" to MachineGlyph.bodyweight,
    "crunch a terra" to MachineGlyph.bodyweight,
    "cavi" to MachineGlyph.cableStation, "cable" to MachineGlyph.cableStation,
    "pushdown" to MachineGlyph.cableStation, "face pull" to MachineGlyph.cableStation,
    "kickback" to MachineGlyph.cableStation,
    "machine" to MachineGlyph.machine,
    "manubri" to MachineGlyph.dumbbell, "manubrio" to MachineGlyph.dumbbell,
    "bilanciere" to MachineGlyph.barbell, "sbarra" to MachineGlyph.pullUpBar,
    "alzate" to MachineGlyph.dumbbell, "scrollate" to MachineGlyph.dumbbell, "curl" to MachineGlyph.barbell,
    "press" to MachineGlyph.seatedPush,
)

/**
 * Il pittogramma dedotto dal nome (`MachineGlyph.suggested(for:group:)`, `suggestedGlyph` in common/glyph.ts): serve al calcolatore dei
 * dischi per un macchinario personalizzato senza icona. Il disegno non si mostra.
 */
fun suggestedGlyph(name: String, group: MuscleGroup): MachineGlyph {
    val needle = name.lowercase()
    GLYPH_KEYWORDS.firstOrNull { needle.contains(it.first) }?.let { return it.second }
    return when (group) {
        MuscleGroup.petto, MuscleGroup.spalle -> MachineGlyph.seatedPush
        MuscleGroup.schiena -> MachineGlyph.seatedRow
        MuscleGroup.gambe -> MachineGlyph.legLever
        MuscleGroup.braccia -> MachineGlyph.dumbbell
        MuscleGroup.core -> MachineGlyph.bodyweight
    }
}

/** L'esercizio ha una voce di catalogo con note (le legge `MuscleDetailSheet`). */
val ExerciseEntity.catalogNotes: String get() = catalogEntry?.notes?.trim().orEmpty()

// ---- il riepilogo ---------------------------------------------------------------------------

/** Una medaglia che l'allenamento ha fatto salire: com'era dopo e com'era prima. */
data class MovedMedal(val after: Medal, val before: Medal) {
    val leveledUp: Boolean get() = after.tier > before.tier
}

/**
 * Tutto quello che il riepilogo mostra e che costa un giro sul database (`WorkoutSummaryView.Snapshot`): si calcola una volta sola, perché
 * i dati non cambiano mentre il riepilogo è aperto.
 */
data class SummarySnapshot(
    /** I giorni della settimana con un allenamento (0 = lunedì) e il giorno di questo allenamento. */
    val days: Set<Int>,
    val today: Int,
    val goal: Int,
    /** Gli allenamenti della settimana, questo compreso. */
    val thisWeek: Int,
    val streakNow: Int,
    val streakBefore: Int,
    val moved: List<MovedMedal>,
    /** Le serie di questo allenamento che sono un nuovo massimale. */
    val records: List<WorkoutSetEntity>,
)

/**
 * Il riepilogo di un allenamento chiuso. `workouts` sono tutti (anche quelli di dopo: contano solo quelli fino a `workout.date`);
 * `setsByWorkout` le serie di ognuno; `records` i massimali; `weeklyGoal` l'obiettivo scelto (0 = quello che fai di solito).
 */
fun summarySnapshot(
    workout: WorkoutEntity,
    workouts: List<WorkoutEntity>,
    setsByWorkout: Map<String, List<WorkoutSetEntity>>,
    exercises: List<ExerciseEntity>,
    records: List<MaxRecordEntity>,
    weeklyGoal: Int,
    friends: Int = 0,
): SummarySnapshot {
    val done = workouts.filter { !it.isActive && it.date <= workout.date }
    val start = Stats.weekStart(workout.date)
    val week = done.filter { it.date >= start }
    val doneDates = done.map { it.date }

    val atWorkouts = workouts.map { Medals.AtWorkout(it.uuid, it.date, it.isActive, setsByWorkout[it.uuid].orEmpty().totalVolume) }
    val allSets = setsByWorkout.values.flatten().groupBy { it.exerciseUuid }
    val recordsBy = records.groupBy { it.exerciseUuid }
    val atExercises = exercises.map { e ->
        Medals.AtExercise(
            e.limbsMode,
            recordsBy[e.uuid].orEmpty().map { Medals.AtRecord(it.date, it.weight, it.reps, it.singleArm) },
            allSets[e.uuid].orEmpty().map { Medals.AtSet(it.workoutUuid, it.isWarmup, it.weight, it.reps, it.singleArm) },
        )
    }
    val before = Medals.at(atWorkouts, atExercises, friends, workout.date, inclusive = false)
    val after = Medals.at(atWorkouts, atExercises, friends, workout.date, inclusive = true)

    return SummarySnapshot(
        days = week.map { WorkoutDates.weekday(it.date) }.toSet(),
        today = WorkoutDates.weekday(workout.date),
        goal = if (weeklyGoal > 0) weeklyGoal else Stats.weeklyGoal(doneDates, workout.date),
        thisWeek = week.size,
        streakNow = Stats.weeklyStreak(doneDates, workout.date),
        streakBefore = Stats.weeklyStreak(done.filter { it.uuid != workout.uuid }.map { it.date }, workout.date),
        moved = after.zip(before).filter { (a, b) -> a.value > b.value }.map { (a, b) -> MovedMedal(a, b) },
        records = setsByWorkout[workout.uuid].orEmpty().filter { it.isPersonalRecord }.sortedBy { it.order },
    )
}
