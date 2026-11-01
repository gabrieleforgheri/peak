package com.gabrieleforghieri.peak.ui.machines

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.displayName
import com.gabrieleforghieri.peak.data.group
import com.gabrieleforghieri.peak.data.machineGlyph
import com.gabrieleforghieri.peak.data.matches
import com.gabrieleforghieri.peak.domain.ChartRange
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MaxRanking
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.Progression
import com.gabrieleforghieri.peak.domain.Warmup
import com.gabrieleforghieri.peak.domain.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// La logica dell'area Macchinari che non è disegno: le proprietà derivate di `Exercise.swift` che servono qui, i raggruppamenti di
// `MaxesListView`, il grafico e la progressione di `ExerciseDetailView`. Kotlin puro (le entità sono dati), provato sul JVM.

/** Come si fa questo macchinario (`Exercise.limbs`). */
val ExerciseEntity.machineLimbs: Limbs get() = Limbs.limbsOf(limbs, isUnilateral, catalogID, name, muscleGroup)

/** Braccia o gambe, dedotto dal gruppo (`Exercise.worksLegs`). */
val ExerciseEntity.worksLegs: Boolean get() = group == MuscleGroup.gambe

/** Il peso non si dimezza passando a un arto: manubri, cavi (`Exercise.twoHandles`). */
val ExerciseEntity.twoHandles: Boolean get() = Limbs.twoHandles(catalogID, name, machineGlyph?.raw ?: "")

/** «absolute» se vuoto (`Exercise.weightDisplay`). */
val ExerciseEntity.shownWeightDisplay: String get() = weightDisplay.ifEmpty { "absolute" }

/** Il macchinario è a un arto solo: salva la scelta e il vecchio segno `isUnilateral` che la segue (`Exercise.setLimbs`). */
fun ExerciseEntity.withLimbs(value: Limbs): ExerciseEntity = copy(limbs = value.name, isUnilateral = value == Limbs.one)

/**
 * I massimali di un macchinario divisi nelle due linee: «il» massimale a due arti e, solo sui macchinari a scelta, quello a un arto
 * (`twoArmRecords`, `singleArmRecords`, `bestRecord`… di `Exercise.swift`). `records` sono tutti quelli del macchinario.
 */
class MachineLines(val exercise: ExerciseEntity, records: List<MaxRecordEntity>) {
    val limbs: Limbs = exercise.machineLimbs

    fun isOneLimbLine(singleArm: Boolean): Boolean = Limbs.lineArm(limbs, singleArm)

    val twoArm: List<MaxRecordEntity> = records.filter { !isOneLimbLine(it.singleArm) }
    val oneArm: List<MaxRecordEntity> = records.filter { isOneLimbLine(it.singleArm) }
    val twoArmByDateDesc: List<MaxRecordEntity> = twoArm.sortedByDescending { it.date }
    val oneArmByDateDesc: List<MaxRecordEntity> = oneArm.sortedByDescending { it.date }

    /** Il massimo vero (vedi `MaxRanking`): più carico, poi più ripetizioni, a parità il più recente. */
    val best: MaxRecordEntity? = best(twoArm)
    val oneArmBest: MaxRecordEntity? = best(oneArm)

    /** Miglior carico registrato prima di una certa data, per i badge di variazione (`bestWeight(before:)`). */
    fun bestWeightBefore(date: Long): Double? = twoArm.filter { it.date < date }.maxOfOrNull { it.weight }

    /** La variazione del massimale attuale sul migliore di prima, in percento; `null` se non c'è un prima o non è salito. */
    val deltaPercent: Double?
        get() {
            val b = best ?: return null
            val previous = bestWeightBefore(b.date) ?: return null
            return if (previous > 0 && b.weight > previous) (b.weight - previous) / previous * 100 else null
        }

    /** La rampa di riscaldamento sulla griglia dell'unità scelta (`warmupPlan(unit:)`): vuota finché non c'è un massimale. */
    fun warmupPlan(unit: WeightUnit, oneLimb: Boolean = false): List<Warmup.Step> =
        Warmup.plan((if (oneLimb) oneArmBest else best)?.weight, exercise.incrementStep, unit)

    /**
     * «Per gamba: 70 kg · a due 62,5 kg»: il massimale a un arto accanto alla metà di quello a due. Solo dove il peso è quello di un
     * arto solo e non di un manico (manubri e cavi), cioè la pila unica. `null` altrimenti. Restituisce (peso a un arto, metà del peso a due).
     */
    fun limbComparison(): Pair<Double, Double>? {
        if (exercise.twoHandles) return null
        val one = oneArmBest ?: return null
        val two = best ?: return null
        return one.weight to two.weight / 2
    }

    private fun best(records: List<MaxRecordEntity>) = MaxRanking.best(records, { it.weight }, { it.reps }, { it.date })
}

// ---- elenco --------------------------------------------------------------------------------

/** I macchinari non archiviati, filtrati per gruppo e per ricerca (`visibleExercises`). */
fun visibleExercises(all: List<ExerciseEntity>, group: MuscleGroup?, search: String, italian: Boolean): List<ExerciseEntity> =
    all.filter { e ->
        when {
            e.isArchived -> false
            group != null && e.group != group -> false
            search.isEmpty() -> true
            else -> e.matches(search, italian)
        }
    }

/** I gruppi nell'ordine dei muscoli, solo quelli con qualcosa (`grouped`). */
fun groupedByMuscle(visible: List<ExerciseEntity>): List<Pair<MuscleGroup, List<ExerciseEntity>>> =
    MuscleGroup.entries.mapNotNull { g -> visible.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it } }

/** Fuori catalogo e non creati a mano di proposito: non entrano nelle classifiche con gli amici (`unlinkedBanner`). */
fun unlinkedExercises(all: List<ExerciseEntity>): List<ExerciseEntity> = all.filter { !it.isArchived && it.catalogID.isEmpty() && !it.isCustom }

/** Quanti macchinari hanno un massimale a due arti (`withMaxCount`). */
fun withMaxCount(all: List<ExerciseEntity>, records: List<MaxRecordEntity>): Int {
    val byExercise = records.groupBy { it.exerciseUuid }
    return all.count { !it.isArchived && MachineLines(it, byExercise[it.uuid].orEmpty()).twoArm.isNotEmpty() }
}

/** Cosa mostra la riga di un macchinario nell'elenco. */
data class MachineRow(
    val exercise: ExerciseEntity,
    val best: MaxRecordEntity?,
    /** Il primo passo del riscaldamento: con quanto scaldarsi (`warmupPlan(unit:).first`). */
    val warmup: Warmup.Step?,
    val deltaPercent: Double?,
)

fun machineRow(exercise: ExerciseEntity, records: List<MaxRecordEntity>, unit: WeightUnit): MachineRow {
    val lines = MachineLines(exercise, records)
    return MachineRow(exercise, lines.best, lines.warmupPlan(unit).firstOrNull(), lines.deltaPercent)
}

// ---- progressione --------------------------------------------------------------------------

/**
 * Il prossimo carico consigliato dalla doppia progressione (`Exercise.progression(range:unit:)`): sull'ultima sessione **conclusa** con
 * serie allenanti qui, sulla linea dell'ultima serie fatta. `sets` sono le serie del macchinario in allenamenti che esistono ancora.
 * Il risultato è in kg.
 */
fun nextProgression(
    exercise: ExerciseEntity,
    sets: List<WorkoutSetEntity>,
    workouts: Map<String, WorkoutEntity>,
    range: IntRange,
    unit: WeightUnit,
): Progression.Suggestion? {
    val limbs = exercise.machineLimbs
    val session = sets.sortedBy { it.createdAt }.mapNotNull { s ->
        val w = workouts[s.workoutUuid] ?: return@mapNotNull null
        Progression.SessionSet(
            weight = Math.round(unit.fromKilograms(s.weight) * 100) / 100.0, reps = s.reps, isWarmup = s.isWarmup,
            workout = w.uuid, workoutDate = w.date, workoutEnded = w.endedAt != null, oneLimbLine = Limbs.lineArm(limbs, s.singleArm),
        )
    }
    val next = Progression.forExercise(session, unit.displayStep(exercise.incrementStep), range) ?: return null
    return Progression.Suggestion(unit.toKilograms(next.weight), next.reps)
}

// ---- grafico -------------------------------------------------------------------------------

/** Una misurazione sul grafico: la posizione è un passo, i giorni senza allenamento non esistono (`ChartPoint`). */
data class ChartPoint(val index: Int, val date: Long, val value: Double, val isEstimated: Boolean)

private const val DAY_MS = 86_400_000.0

/** Gli orizzonti selezionabili secondo l'anzianità dei dati (`availableRanges`). */
fun availableRanges(records: List<MaxRecordEntity>, now: Long): List<ChartRange> {
    val earliest = records.minOfOrNull { it.date } ?: return listOf(ChartRange.all)
    return ChartRange.available(maxOf(0.0, (now - earliest) / DAY_MS))
}

/** I punti del grafico: i massimali dell'orizzonte scelto (o il primo disponibile se quello non c'è più), dal più vecchio. */
fun chartPoints(records: List<MaxRecordEntity>, range: ChartRange, unit: WeightUnit, now: Long): List<ChartPoint> {
    val available = availableRanges(records, now)
    val active = if (range in available) range else available.first()
    val cutoff = active.days?.let { now - (it * DAY_MS).toLong() }
    return records.filter { cutoff == null || it.date >= cutoff }.sortedBy { it.date }
        .mapIndexed { i, r -> ChartPoint(i, r.date, unit.fromKilograms(r.weight), r.isEstimated) }
}

/** Un quarto di passo di respiro ai lati: senza, l'etichetta dell'ultima misurazione finisce mezza fuori (`xDomain`). */
fun chartXDomain(count: Int): ClosedFloatingPointRange<Double> = -0.5..(maxOf(count - 1, 1) + 0.5)

fun chartYDomain(points: List<ChartPoint>): ClosedFloatingPointRange<Double> {
    val values = points.map { it.value }
    val min = values.minOrNull() ?: return 0.0..100.0
    val max = values.maxOrNull() ?: return 0.0..100.0
    if (min == max) return (min * 0.9)..(max * 1.1 + 1)
    val padding = (max - min) * 0.18
    return maxOf(0.0, min - padding)..(max + padding)
}

// ---- date ----------------------------------------------------------------------------------

/** «Oggi» / «Ieri» (chiavi di traduzione) se il giorno è quello, altrimenti `null` (`Fmt.relativeDay`). */
fun relativeDayWord(date: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> null
    }
}

object MachineDates {
    private fun fmt(pattern: String, locale: Locale) = DateTimeFormatter.ofPattern(pattern, locale)

    /** «29 ott 2026» (`Fmt.date`). */
    fun date(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        fmt("d MMM yyyy", locale).format(Instant.ofEpochMilli(ms).atZone(zone)).replace(".", "")

    /** «29 ott» (`Fmt.shortDate`). */
    fun shortDate(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        fmt("d MMM", locale).format(Instant.ofEpochMilli(ms).atZone(zone)).replace(".", "")

    /** «gio 29 ott» (la data breve di `Fmt.relativeDay` fuori da oggi e ieri). */
    fun weekdayDate(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        fmt("EEE d MMM", locale).format(Instant.ofEpochMilli(ms).atZone(zone)).replace(".", "")

    /** La data estesa per il selettore della data. */
    fun long(ms: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(Instant.ofEpochMilli(ms).atZone(zone))

    /**
     * Il giorno scelto nel selettore (mezzanotte UTC, come lo dà Material) diventa un istante con l'ora di adesso, mai nel futuro:
     * così due massimali dello stesso giorno restano in ordine (il `DatePicker` di iPhone parte da `Date()`).
     */
    fun dayAtNow(pickedUtcMidnight: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = Instant.ofEpochMilli(pickedUtcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        return minOf(now, day.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    fun utcMidnight(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun today(now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}

// ---- nuovo massimale -----------------------------------------------------------------------

/** Da dove parte il foglio «Aggiorna massimale»: l'ultimo record, altrimenti l'ultima serie vera, altrimenti quattro passi (`EditMaxSheet.prefill`). */
fun prefillMax(lines: MachineLines, sets: List<WorkoutSetEntity>): Pair<Double, Int> {
    lines.twoArmByDateDesc.firstOrNull()?.let { return it.weight to it.reps }
    sets.filter { !it.isWarmup }.maxByOrNull { it.createdAt }?.let { return it.weight to it.reps }
    return lines.exercise.incrementStep * 4 to 1
}

/** I limiti del server: 0,01–2000 kg (fuori, il massimale verrebbe scartato). */
fun isValidMaxWeight(kilograms: Double): Boolean = kilograms in 0.01..2000.0

/** Il confronto è dentro la propria linea: a un braccio solo con i massimali a un braccio. */
fun isNewRecord(lines: MachineLines, weight: Double, reps: Int, singleArm: Boolean): Boolean {
    val previous = if (singleArm) lines.oneArmBest else lines.best
    return MaxRanking.beats(weight, reps, previous?.let { MaxRanking.Performance(it.weight, it.reps) })
}

/** Il nome mostrato dell'esercizio, per i titoli. */
fun ExerciseEntity.title(italian: Boolean): String = displayName(italian)
