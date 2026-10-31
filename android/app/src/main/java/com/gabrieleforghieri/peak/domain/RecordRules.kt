package com.gabrieleforghieri.peak.domain

/**
 * Quali serie sono un nuovo massimale. È la stessa regola di `common/records.ts` e di `RecordRules.swift`: i tre devono dare
 * lo stesso risultato, e lo controllano gli stessi vettori (`common/fixtures/records.json`).
 *
 * I massimali «da allenamento» non si salvano come fatti a sé: si ricalcolano dalle serie. Così cancellare o correggere
 * una serie, o un intero allenamento, lascia sempre i record giusti, e due dispositivi non possono mai litigare su un record.
 */
object RecordRules {

    data class ManualRecord(
        val uuid: String,
        val date: Long,
        val weight: Double,
        val reps: Int,
        val singleArm: Boolean = false,
    )

    data class SetEntry(
        val uuid: String,
        val createdAt: Long,
        val order: Int,
        val weight: Double,
        val reps: Int,
        val isWarmup: Boolean,
        val excludedFromRecords: Boolean,
        val singleArm: Boolean = false,
    )

    private class Event(val time: Long, val isManual: Boolean, val order: Int, val uuid: String, val weight: Double, val reps: Int)

    /** Secondi interi, come nei backup e sul server: due eventi nello stesso secondo si ordinano per regola, non per i millisecondi. */
    fun floorSeconds(ms: Long): Long = Math.floorDiv(ms, 1000L)

    /**
     * Gli uuid delle serie che battono il migliore fino a quel momento, in ordine di tempo. Si percorrono i massimali
     * inseriti a mano e le serie valide (non riscaldamento, non escluse, peso e ripetizioni sensati): un manuale che batte
     * il migliore lo sostituisce, una serie che lo batte è un record. `sets` va già filtrato a quelle di un allenamento
     * che esiste ancora.
     */
    fun personalRecordSets(manual: List<ManualRecord>, sets: List<SetEntry>): List<String> {
        val records = mutableListOf<String>()
        // Due linee separate: il peso a un braccio (sul singolo manico) non si confronta con quello a due braccia.
        for (singleArm in listOf(false, true)) {
            val events = mutableListOf<Event>()
            for (m in manual) if (m.singleArm == singleArm) events += Event(floorSeconds(m.date), true, 0, m.uuid, m.weight, m.reps)
            for (s in sets) {
                if (s.singleArm == singleArm && !s.isWarmup && !s.excludedFromRecords && s.weight > 0 && s.reps >= 1) {
                    events += Event(floorSeconds(s.createdAt), false, s.order, s.uuid, s.weight, s.reps)
                }
            }
            events.sortWith { a, b ->
                when {
                    a.time != b.time -> a.time.compareTo(b.time)
                    a.isManual != b.isManual -> if (a.isManual) -1 else 1
                    a.order != b.order -> a.order.compareTo(b.order)
                    else -> a.uuid.compareTo(b.uuid)
                }
            }
            var best: MaxRanking.Performance? = null
            for (e in events) {
                if (MaxRanking.beats(e.weight, e.reps, best)) {
                    best = MaxRanking.Performance(e.weight, e.reps)
                    if (!e.isManual) records += e.uuid
                }
            }
        }
        return records
    }
}
