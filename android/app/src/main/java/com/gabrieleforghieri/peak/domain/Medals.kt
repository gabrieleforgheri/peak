package com.gabrieleforghieri.peak.domain

/**
 * Le medaglie: sette traguardi con tre livelli (bronzo, argento, oro). Si ricavano dai numeri che l'app ha già, niente da
 * salvare. Stessa logica di `common/medals.ts` e di `Medals.swift`.
 */
enum class MedalID(val title: String, val info: String, val tiers: List<Double>) {
    sessions("Costanza", "Ogni allenamento concluso conta.", listOf(5.0, 25.0, 100.0)),
    records("Cacciatore di record", "Ogni massimale battuto conta.", listOf(1.0, 10.0, 50.0)),
    streak("In serie", "Settimane di seguito con almeno un allenamento.", listOf(2.0, 6.0, 12.0)),
    volume("Tonnellate", "Tutto il peso sollevato, sommato.", listOf(1_000.0, 25_000.0, 250_000.0)),
    strength("Forza", "Il tuo miglior 1RM stimato, su un macchinario.", listOf(60.0, 100.0, 140.0)),
    variety("Esploratore", "Quanti macchinari diversi hai usato.", listOf(5.0, 12.0, 25.0)),
    friends("Squadra", "Quanti amici hai collegato.", listOf(1.0, 3.0, 10.0));

    val raw: String get() = name
}

data class MedalInput(
    val sessions: Int = 0,
    val records: Int = 0,
    val streak: Int = 0,
    val volumeKg: Double = 0.0,
    val bestOneRm: Double = 0.0,
    val machines: Int = 0,
    val friends: Int = 0,
) {
    fun value(id: MedalID): Double = when (id) {
        MedalID.sessions -> sessions.toDouble()
        MedalID.records -> records.toDouble()
        MedalID.streak -> streak.toDouble()
        MedalID.volume -> volumeKg
        MedalID.strength -> bestOneRm
        MedalID.variety -> machines.toDouble()
        MedalID.friends -> friends.toDouble()
    }
}

data class Medal(
    val kind: MedalID,
    val value: Double,
    /** 0 = ancora niente, 1 bronzo, 2 argento, 3 oro. */
    val tier: Int,
    /** Verso il livello dopo, da 0 a 1 (1 = oro già preso). */
    val progress: Double,
    val next: Double?,
) {
    /** Quanto del livello dopo hai fatto, da 0 a 1 (valore / soglia). Oro preso = 1. */
    val frac: Double get() = next?.let { minOf(1.0, value / it) } ?: 1.0
}

object Medals {
    /** Prima le più vicine al livello dopo, poi quelle d'oro; a parità resta l'ordine di sempre. */
    fun ordered(list: List<Medal>): List<Medal> {
        val open = list.withIndex().filter { it.value.next != null }
            .sortedWith { a, b -> if (a.value.frac != b.value.frac) b.value.frac.compareTo(a.value.frac) else a.index.compareTo(b.index) }
        return open.map { it.value } + list.filter { it.next == null }
    }

    /**
     * Quando ogni livello già preso è scattato la prima volta: scorre gli istanti `times` (in ordine qualsiasi) e li chiede a `at`
     * (le medaglie com'erano allora), fermandosi quando ha trovato tutto. Per i livelli non presi (`current`) o non trovati: null.
     * Vale il primo istante in cui il livello c'era, anche se poi è stato perso. Come `tierDates` in common/medals.ts.
     */
    fun tierDates(times: List<Long>, at: (Long) -> List<Medal>, current: List<Medal>): Map<MedalID, List<Long?>> {
        val out = current.associate { it.kind to MutableList<Long?>(3) { null } }
        val cap = current.associate { it.kind to it.tier }
        var left = current.sumOf { it.tier }
        for (time in times.toSortedSet()) {
            if (left == 0) break
            for (m in at(time)) {
                val c = cap[m.kind] ?: continue
                for (i in 0 until minOf(m.tier, c)) if (out[m.kind]!![i] == null) { out[m.kind]!![i] = time; left -= 1 }
            }
        }
        return out
    }

    fun list(input: MedalInput): List<Medal> = MedalID.entries.map { kind ->
        val value = input.value(kind)
        val tiers = kind.tiers
        val tier = tiers.count { value >= it }
        val next: Double? = if (tier < 3) tiers[tier] else null
        val base = if (tier == 0) 0.0 else tiers[tier - 1]
        val progress = next?.let { maxOf(0.0, minOf(1.0, (value - base) / (it - base))) } ?: 1.0
        Medal(kind, value, tier, progress, next)
    }

    data class Totals(val earned: Int, val total: Int)

    fun totals(list: List<Medal>): Totals = Totals(list.sumOf { it.tier }, list.size * 3)

    // ---- le medaglie com'erano a una certa data (MedalsAt.swift, `medalsAt` in medalinfo.ts) ----

    data class AtWorkout(val uuid: String, val date: Long, val isActive: Boolean, val totalVolume: Double)
    data class AtRecord(val date: Long, val weight: Double, val reps: Int, val singleArm: Boolean)
    data class AtSet(val workout: String?, val isWarmup: Boolean, val weight: Double, val reps: Int, val singleArm: Boolean)
    data class AtExercise(val limbs: Limbs, val records: List<AtRecord>, val sets: List<AtSet>)

    /**
     * Le medaglie com'erano a una certa data: serve al riepilogo per dire quali ha fatto salire un allenamento. Gli amici non
     * cambiano con un allenamento: si passano da fuori.
     */
    fun at(workouts: List<AtWorkout>, exercises: List<AtExercise>, friends: Int, until: Long, inclusive: Boolean, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Medal> {
        fun cut(d: Long) = if (inclusive) d <= until else d < until
        val done = workouts.filter { !it.isActive && cut(it.date) }
        val ids = done.map { it.uuid }.toHashSet()
        var records = 0
        var machines = 0
        var best = 0.0
        for (e in exercises) {
            val rs = e.records.filter { !Limbs.lineArm(e.limbs, it.singleArm) && cut(it.date) }
            val ss = e.sets.filter { !it.isWarmup && it.workout != null && it.workout in ids }
            records += rs.size
            if (rs.isNotEmpty() || ss.isNotEmpty()) machines += 1
            for (r in rs) best = maxOf(best, OneRepMax.estimate(r.weight, r.reps))
            for (s in ss) if (!Limbs.lineArm(e.limbs, s.singleArm)) best = maxOf(best, OneRepMax.estimate(s.weight, s.reps))
        }
        return list(MedalInput(
            sessions = done.size, records = records,
            streak = Stats.weeklyStreak(done.map { it.date }, until, zone),
            volumeKg = done.sumOf { it.totalVolume },
            bestOneRm = best, machines = machines, friends = friends,
        ))
    }

    /**
     * Quando hai preso ogni livello (bronzo, argento, oro; null se non c'è o non si sa), ricavato dalla storia. Gli amici non
     * hanno una data.
     */
    fun dates(workouts: List<AtWorkout>, exercises: List<AtExercise>, friends: Int, current: List<Medal>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Map<MedalID, List<Long?>> {
        val times = workouts.filter { !it.isActive }.map { it.date } +
            exercises.flatMap { e -> e.records.filter { !Limbs.lineArm(e.limbs, it.singleArm) }.map { it.date } }
        val out = tierDates(times, { at(workouts, exercises, friends, it, true, zone) }, current).toMutableMap()
        out[MedalID.friends] = listOf(null, null, null)
        return out
    }
}

/** Le frasi dell'eroe in Allenamento: una diversa ogni giorno, poi a rotazione. */
object Motivation {
    val italian: List<String> = listOf(
        "Oggi è un buon giorno per battere il tuo record.",
        "Ogni vetta comincia con un primo passo.",
        "La costanza batte il talento.",
        "Il peso non si alza da solo.",
        "Niente scuse, solo serie.",
        "Sei più forte di ieri. Domani lo sarai di più.",
        "Fatica oggi, orgoglio domani.",
        "Un record nasce da una serie normale.",
        "Respira, spingi, ripeti.",
        "La vetta non si vede dal divano.",
        "Il tuo unico avversario è la scheda di ieri.",
        "Piccoli passi, grandi carichi.",
        "Ogni ripetizione conta.",
        "Chi si allena oggi ringrazia domani.",
        "Riposa se serve, ma non mollare.",
        "Un allenamento alla volta.",
        "Costruisci oggi, quello che sarai domani.",
        "La disciplina è la via, per ogni vetta.",
        "Non contare i giorni, fai contare i giorni.",
        "La forza cresce, quando pensi di non farcela.",
        "Focalizzati sull'obiettivo, dimentica il rumore.",
        "Ogni goccia di sudore, è un passo avanti.",
        "Vinci la mente, e il corpo seguirà.",
        "Il ferro non mente mai, ti dice sempre chi sei.",
        "Supera i tuoi limiti, un chilo alla volta.",
        "La determinazione, trasforma la fatica in forza.",
        "Non fermarti quando sei stanco, fermati quando hai finito.",
        "Non cercare scorciatoie, la salita fa parte del viaggio.",
        "La motivazione ti fa partire, l'abitudine ti fa andare avanti.",
        "Una serie in più oggi, un record in più domani.",
        "Fidati del processo, i risultati arriveranno.",
        "Non serve essere perfetti, basta essere costanti.",
    )

    /**
     * Da quale frase partire oggi: i giorni dall'inizio dell'era divisi per il numero di frasi. Su iPhone il giorno dell'era è
     * quello del calendario locale (Gregoriano/Giuliano); qui è quello gregoriano proleptico: stessa idea, un'altra rotazione.
     */
    fun startIndex(now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int {
        val epochDay = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        return Math.floorMod(epochDay + 719_163, italian.size.toLong()).toInt()
    }
}
