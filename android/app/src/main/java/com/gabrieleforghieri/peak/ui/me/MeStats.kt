package com.gabrieleforghieri.peak.ui.me

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MaxRanking
import com.gabrieleforghieri.peak.domain.Medal
import com.gabrieleforghieri.peak.domain.MedalID
import com.gabrieleforghieri.peak.domain.MedalInput
import com.gabrieleforghieri.peak.domain.Medals
import com.gabrieleforghieri.peak.domain.OneRepMax
import com.gabrieleforghieri.peak.domain.StatSet
import com.gabrieleforghieri.peak.domain.StatWorkout
import com.gabrieleforghieri.peak.domain.Stats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// I numeri di Me: `HomeStats` (MeSections.swift), `Progression1RM` (ProgressionCard.swift) e `Insights` (Store/Insights.swift). Kotlin puro
// sulle entità di Room: niente Android, così si provano sul JVM. I conti veri (serie, medaglie, volumi) stanno in `domain/`.

private const val DAY_MS = 86_400_000L

/** Come si fa questo macchinario: uno o due arti (`Exercise.limbs`). */
fun ExerciseEntity.limbsKind(): Limbs = Limbs.limbsOf(limbs, isUnilateral, catalogID, name, muscleGroup)

/** Il massimo vero fra i massimali (`MaxRanking`). */
fun bestOf(records: List<MaxRecordEntity>): MaxRecordEntity? = MaxRanking.best(records, { it.weight }, { it.reps }, { it.date })

/** I massimali a due braccia di un macchinario: la linea che mostra «il» massimale (`Exercise.twoArmRecords`). */
fun twoArmRecords(exercise: ExerciseEntity, records: List<MaxRecordEntity>): List<MaxRecordEntity> {
    val limbs = exercise.limbsKind()
    return records.filter { !Limbs.lineArm(limbs, it.singleArm) }
}

private fun estimate(r: MaxRecordEntity) = OneRepMax.estimate(r.weight, r.reps)

/** I numeri di Me, ricavati dagli allenamenti conclusi e dai macchinari (`HomeStats`). */
data class HomeStats(
    val finished: Int,
    val streak: Int,
    val totalVolume: Double,
    val recordCount: Int,
    val machinesUsed: Int,
    val bestOneRm: Double,
    /** Serie di lavoro per giorno. */
    val perDay: Map<LocalDate, Int>,
    /** Volume delle ultime 12 settimane, dalla più vecchia; anche le vuote. */
    val weeks: List<Week>,
    val groups: List<Stats.GroupVolume>,
    val last98Days: Int,
) {
    data class Week(val start: LocalDate, val volume: Double)

    fun medalInput(friends: Int) = MedalInput(
        sessions = finished, records = recordCount, streak = streak, volumeKg = totalVolume,
        bestOneRm = bestOneRm, machines = machinesUsed, friends = friends,
    )

    companion object {
        fun compute(
            workouts: List<WorkoutEntity>,
            sets: List<WorkoutSetEntity>,
            exercises: List<ExerciseEntity>,
            records: List<MaxRecordEntity>,
            now: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): HomeStats {
            val done = workouts.filter { it.endedAt != null }
            val setsOf = sets.groupBy { it.workoutUuid }
            val byExercise = sets.groupBy { it.exerciseUuid }
            val recordsOf = records.groupBy { it.exerciseUuid }

            fun working(w: WorkoutEntity) = (setsOf[w.uuid] ?: emptyList()).filter { !it.isWarmup }
            fun volume(w: WorkoutEntity) = working(w).sumOf { it.weight * it.reps }

            var recordCount = 0
            var machines = 0
            var best = 0.0
            for (e in exercises) {
                val rs = twoArmRecords(e, recordsOf[e.uuid] ?: emptyList())
                val ss = byExercise[e.uuid] ?: emptyList()
                recordCount += rs.size
                if (rs.isEmpty() && ss.isEmpty()) continue
                machines += 1
                val limbs = e.limbsKind()
                for (r in rs) best = maxOf(best, estimate(r))
                for (s in ss) if (!s.isWarmup && !Limbs.lineArm(limbs, s.singleArm)) best = maxOf(best, OneRepMax.estimate(s.weight, s.reps))
            }

            val perDay = HashMap<LocalDate, Int>()
            for (w in done) perDay.merge(day(w.date, zone), working(w).size, Int::plus)

            val groupOf = exercises.associate { it.uuid to it.group }
            val statWorkouts = done.map { w ->
                StatWorkout(w.date, (setsOf[w.uuid] ?: emptyList()).map { StatSet(it.exerciseUuid, it.weight, it.reps, it.isWarmup) })
            }
            val volumeByWeek = Stats.weeklyVolume(statWorkouts, 12, now, zone).associate { it.weekStart to it.volume }
            val thisMonday = Stats.weekStart(now, zone).let { day(it, zone) }
            val weeks = (0 until 12).map { i ->
                val start = thisMonday.minusWeeks((11 - i).toLong())
                Week(start, volumeByWeek[start.atStartOfDay(zone).toInstant().toEpochMilli()] ?: 0.0)
            }

            return HomeStats(
                finished = done.size,
                streak = Stats.weeklyStreak(done.map { it.date }, now, zone),
                totalVolume = done.sumOf { volume(it) },
                recordCount = recordCount,
                machinesUsed = machines,
                bestOneRm = best,
                perDay = perDay,
                weeks = weeks,
                groups = Stats.volumeByGroup(statWorkouts, { groupOf[it] }, 30, now),
                last98Days = done.count { it.date >= now - 98 * DAY_MS },
            )
        }
    }
}

fun day(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

/** Un punto della progressione: il miglior 1RM stimato di un giorno. */
data class ProgressPoint(val date: Long, val oneRm: Double)

/** L'andamento del 1RM stimato di un macchinario (almeno due giorni). */
data class Series(val exercise: ExerciseEntity, val points: List<ProgressPoint>) {
    val id: String get() = exercise.uuid
}

/** Un macchinario cresciuto negli ultimi 90 giorni. */
data class MoverRow(val exercise: ExerciseEntity, val previous: Double, val current: Double, val deltaPercent: Double)

/** `Progression1RM.points`: un punto per giorno (il migliore fra massimali e serie di lavoro), dal più vecchio. */
fun progressionPoints(
    exercise: ExerciseEntity,
    records: List<MaxRecordEntity>,
    sets: List<WorkoutSetEntity>,
    workouts: Map<String, WorkoutEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ProgressPoint> {
    val best = HashMap<LocalDate, Double>()
    for (r in twoArmRecords(exercise, records)) best.merge(day(r.date, zone), estimate(r), ::maxOf)
    val limbs = exercise.limbsKind()
    for (s in sets) {
        val w = s.workoutUuid?.let(workouts::get) ?: continue
        if (s.isWarmup || Limbs.lineArm(limbs, s.singleArm) || w.endedAt == null) continue
        best.merge(day(w.date, zone), OneRepMax.estimate(s.weight, s.reps), ::maxOf)
    }
    return best.map { ProgressPoint(it.key.atStartOfDay(zone).toInstant().toEpochMilli(), it.value) }.sortedBy { it.date }
}

/** Tutto ciò che Me mostra oltre al peso corporeo, calcolato una volta sola (`Insights`). */
data class MeData(
    val stats: HomeStats,
    val movers: List<MoverRow>,
    val progression: List<Series>,
    /** L'ultimo massimale di ogni macchinario, dal più recente: i primi 4, per «Record recenti». */
    val recentByExercise: List<Pair<ExerciseEntity, MaxRecordEntity>>,
    val hasRecords: Boolean,
    val medals: List<Medal>,
) {
    /** Con niente di tutto questo Me mostra solo il peso e un invito (`hasTrainingData`). */
    val hasTrainingData: Boolean get() = stats.finished > 0 || hasRecords

    companion object {
        fun compute(
            workouts: List<WorkoutEntity>,
            sets: List<WorkoutSetEntity>,
            exercises: List<ExerciseEntity>,
            records: List<MaxRecordEntity>,
            friends: Int,
            now: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): MeData {
            val stats = HomeStats.compute(workouts, sets, exercises, records, now, zone)
            val recordsOf = records.groupBy { it.exerciseUuid }
            val setsOf = sets.groupBy { it.exerciseUuid }
            val workoutById = workouts.associateBy { it.uuid }
            val two = exercises.associate { it.uuid to twoArmRecords(it, recordsOf[it.uuid] ?: emptyList()) }

            val movers = Stats.topMovers(
                exercises.map { e -> e.uuid to (two[e.uuid] ?: emptyList()).map { it.weight to it.date } }, 90, 5, now,
            ).mapNotNull { m -> exercises.firstOrNull { it.uuid == m.exercise }?.let { MoverRow(it, m.previous, m.current, m.deltaPercent) } }

            val progression = exercises.filter { !it.isArchived }.mapNotNull { e ->
                val points = progressionPoints(e, recordsOf[e.uuid] ?: emptyList(), setsOf[e.uuid] ?: emptyList(), workoutById, zone)
                if (points.size >= 2) Series(e, points) else null
            }.sortedByDescending { s -> bestOf(two[s.exercise.uuid] ?: emptyList())?.let(::estimate) ?: 0.0 }

            val recent = exercises.mapNotNull { e -> (two[e.uuid] ?: emptyList()).maxByOrNull { it.date }?.let { e to it } }
                .sortedByDescending { it.second.date }.take(4)

            // una linea a un arto non conta: `records` è già filtrata per macchinario, quelle senza macchinario contano
            val exerciseIds = exercises.map { it.uuid }.toSet()
            val hasRecords = two.values.any { it.isNotEmpty() } || records.any { it.exerciseUuid == null || it.exerciseUuid !in exerciseIds }

            return MeData(stats, movers, progression, recent, hasRecords, Medals.list(stats.medalInput(friends)))
        }
    }
}

/** Le medaglie com'erano nel tempo, per la data di ogni livello nel foglio di una medaglia (`Medals.dates`). */
fun medalDates(
    workouts: List<WorkoutEntity>,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>,
    records: List<MaxRecordEntity>,
    friends: Int,
    current: List<Medal>,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<MedalID, List<Long?>> {
    val setsOf = sets.groupBy { it.workoutUuid }
    val atWorkouts = workouts.map { w ->
        Medals.AtWorkout(w.uuid, w.date, w.endedAt == null, (setsOf[w.uuid] ?: emptyList()).filter { !it.isWarmup }.sumOf { it.weight * it.reps })
    }
    val recordsOf = records.groupBy { it.exerciseUuid }
    val byExercise = sets.groupBy { it.exerciseUuid }
    val atExercises = exercises.map { e ->
        Medals.AtExercise(
            e.limbsKind(),
            (recordsOf[e.uuid] ?: emptyList()).map { Medals.AtRecord(it.date, it.weight, it.reps, it.singleArm) },
            (byExercise[e.uuid] ?: emptyList()).map { Medals.AtSet(it.workoutUuid, it.isWarmup, it.weight, it.reps, it.singleArm) },
        )
    }
    return Medals.dates(atWorkouts, atExercises, friends, current, zone)
}

/** Quanto sono lontane due pesate vicine: la variazione di un mese (`BodyWeightCard.monthDelta`). `entries` dalla più recente. */
fun monthDelta(entries: List<BodyWeightEntity>): Double? {
    val latest = entries.firstOrNull() ?: return null
    val cutoff = latest.date - 30 * DAY_MS
    val previous = entries.firstOrNull { it.date <= cutoff } ?: return null
    return latest.weight - previous.weight
}

/** L'intervallo scelto, se ancora disponibile, altrimenti il primo (`activeRange`). */
fun activeRange(range: ChartRange, available: List<ChartRange>) =
    if (range in available) range else available.firstOrNull() ?: ChartRange.all

/** Gli intervalli disponibili per dati che cominciano a `earliest` (null = nessun dato). */
fun availableRanges(earliest: Long?, now: Long = System.currentTimeMillis()): List<ChartRange> {
    if (earliest == null) return listOf(ChartRange.all)
    return ChartRange.available(maxOf(0.0, (now - earliest) / DAY_MS.toDouble()))
}

/** Le pesate dell'intervallo, dalla più vecchia: solo quelle vere, una per passo (`BodyWeightCard.points`). */
fun bodyWeightPoints(entries: List<BodyWeightEntity>, range: ChartRange, now: Long = System.currentTimeMillis()): List<BodyWeightEntity> {
    val cutoff = range.days?.let { now - (it * DAY_MS).toLong() }
    return entries.filter { cutoff == null || it.date >= cutoff }.reversed()
}
