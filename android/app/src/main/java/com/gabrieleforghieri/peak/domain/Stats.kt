package com.gabrieleforghieri.peak.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Una serie vista dalle statistiche. */
data class StatSet(val exercise: String?, val weight: Double, val reps: Int, val isWarmup: Boolean)

/** Un allenamento visto dalle statistiche: la data (ms) e le sue serie. */
data class StatWorkout(val date: Long, val sets: List<StatSet>) {
    /** Volume totale (kg × ripetizioni), riscaldamento escluso. */
    val totalVolume: Double get() = sets.sumOf { if (it.isWarmup) 0.0 else it.weight * it.reps }
}

/**
 * Calcoli aggregati per la scheda Progressi (`Utils/Stats.swift`, `common/stats.ts`). Tutto in memoria. Le settimane partono
 * da lunedì, nel fuso passato (di base quello del telefono).
 */
object Stats {

    private const val DAY_MS = 86_400_000L

    data class WeeklyVolume(val weekStart: Long, val volume: Double)
    data class GroupVolume(val group: MuscleGroup, val volume: Double)
    data class Mover(val exercise: String, val previous: Double, val current: Double) {
        val deltaPercent: Double get() = if (previous > 0) (current - previous) / previous * 100 else 100.0
    }

    private fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private fun startOf(d: LocalDate, zone: ZoneId): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    /** L'inizio (lunedì, mezzanotte) della settimana che contiene `ms`. */
    fun weekStart(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long = startOf(monday(day(ms, zone)), zone)

    /** Volume per settimana nelle ultime `weeks`, solo quelle in cui ti sei allenato. */
    fun weeklyVolume(workouts: List<StatWorkout>, weeks: Int = 12, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<WeeklyVolume> {
        val thisWeek = monday(day(now, zone))
        val oldest = thisWeek.minusWeeks((weeks - 1).toLong())
        val buckets = HashMap<LocalDate, Double>()
        for (w in workouts) {
            val start = monday(day(w.date, zone))
            if (start >= oldest && start <= thisWeek) buckets[start] = (buckets[start] ?: 0.0) + w.totalVolume
        }
        return buckets.map { WeeklyVolume(startOf(it.key, zone), it.value) }.sortedBy { it.weekStart }
    }

    /** Ripartizione del volume per gruppo muscolare in un intervallo di giorni, riscaldamento escluso, dal più alto. */
    fun volumeByGroup(workouts: List<StatWorkout>, groupOf: (String) -> MuscleGroup?, days: Int = 30, now: Long = System.currentTimeMillis()): List<GroupVolume> {
        val cutoff = now - days * DAY_MS
        val totals = HashMap<MuscleGroup, Double>()
        for (w in workouts) {
            if (w.date < cutoff) continue
            for (s in w.sets) {
                if (s.isWarmup) continue
                val g = s.exercise?.let(groupOf) ?: continue
                totals[g] = (totals[g] ?: 0.0) + s.weight * s.reps
            }
        }
        return MuscleGroup.entries.mapNotNull { g -> totals[g]?.takeIf { it > 0 }?.let { GroupVolume(g, it) } }.sortedByDescending { it.volume }
    }

    /** Esercizi cresciuti di più: miglior carico negli ultimi `days` giorni contro il miglior carico precedente. `records` sono i massimali a due braccia. */
    fun topMovers(exercises: List<Pair<String, List<Pair<Double, Long>>>>, days: Int = 90, limit: Int = 5, now: Long = System.currentTimeMillis()): List<Mover> {
        val cutoff = now - days * DAY_MS
        return exercises.mapNotNull { (id, records) ->
            val recent = records.filter { it.second >= cutoff }.maxOfOrNull { it.first } ?: return@mapNotNull null
            val previous = records.filter { it.second < cutoff }.maxOfOrNull { it.first } ?: 0.0
            if (recent > previous) Mover(id, previous, recent) else null
        }.sortedByDescending { it.deltaPercent }.take(limit)
    }

    /** Fino a `max` indici equidistanti fra 0 e `count - 1`, primo e ultimo sempre inclusi. */
    fun tickIndices(count: Int, max: Int = 4): List<Int> {
        if (count <= 0 || max <= 0) return emptyList()
        if (count <= max) return (0 until count).toList()
        val step = (count - 1).toDouble() / (max - 1)
        return (0 until max).map { Swift.round(it * step).toInt() }
    }

    /** Numero di sessioni nell'intervallo indicato. */
    fun sessionCount(dates: List<Long>, days: Int, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - days * DAY_MS
        return dates.count { it >= cutoff }
    }

    /**
     * Serie di settimane consecutive con almeno un allenamento, fino a oggi. Finché in questa settimana non ti sei allenato la
     * serie è ancora viva: si conta da quella scorsa. Come `weeklyStreak` in common/stats.ts.
     */
    fun weeklyStreak(dates: List<Long>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val trained = dates.map { monday(day(it, zone)) }.toHashSet()
        var cursor = monday(day(now, zone))
        if (cursor !in trained) cursor = cursor.minusWeeks(1)
        var streak = 0
        while (cursor in trained) {
            streak += 1
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    /**
     * L'obiettivo della settimana: quanti allenamenti fai di solito. La media delle ultime otto settimane (questa esclusa),
     * arrotondata, fra 2 e 5; con meno di due settimane di storia, 3. Se la storia è più corta di otto settimane si divide per
     * quelle che ci sono. Come `weeklyGoal` in common/stats.ts.
     */
    fun weeklyGoal(dates: List<Long>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val thisWeek = monday(day(now, zone))
        val oldest = thisWeek.minusWeeks(8)
        val perWeek = HashMap<LocalDate, Int>()
        for (d in dates) {
            val start = monday(day(d, zone))
            if (start < thisWeek && start >= oldest) perWeek[start] = (perWeek[start] ?: 0) + 1
        }
        if (perWeek.size < 2) return 3
        val first = dates.minOrNull() ?: now
        val firstWeek = monday(day(first, zone))
        val weeks = maxOf(1L, minOf(8L, ChronoUnit.WEEKS.between(firstWeek, thisWeek))).toInt()
        val average = perWeek.values.sum().toDouble() / weeks
        return minOf(5, maxOf(2, Swift.round(average).toInt()))
    }
}

/**
 * Orizzonte temporale selezionabile per tutti i grafici. Gli intervalli disponibili dipendono dall'anzianità dei dati:
 * meno di 30 giorni solo «Tutto»; da 30 a 89 «1 mese» e «Tutto»; da 90 a 364 anche «3 mesi»; da 365 «3 mesi», «1 anno» e «Tutto».
 */
enum class ChartRange(val title: String, val days: Double?) {
    oneMonth("1 mese", 30.0), threeMonths("3 mesi", 90.0), year("1 anno", 365.0), all("Tutto", null);

    companion object {
        fun available(forSpanDays: Double): List<ChartRange> {
            val ranges = mutableListOf<ChartRange>()
            if (forSpanDays >= 30 && forSpanDays < 365) ranges += oneMonth
            if (forSpanDays >= 90) ranges += threeMonths
            if (forSpanDays >= 365) ranges += year
            ranges += all
            return ranges
        }
    }
}
