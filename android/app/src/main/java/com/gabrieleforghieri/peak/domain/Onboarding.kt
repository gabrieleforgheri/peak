package com.gabrieleforghieri.peak.domain

/**
 * Il primo avvio: i macchinari che si propongono per ogni gruppo e la scheda che ne nasce. Come `common/onboarding.ts` e
 * `createStarterPlan` del web: stessi id del catalogo, stesso ordine. La parte che scrive nel database (`createPlan` di Swift)
 * è qui solo calcolata: [build] restituisce cosa creare, e chi ha il database lo scrive.
 */
object Onboarding {
    /** Cinque comuni in palestra, prima macchine e cavi che pesi liberi. */
    val suggested: Map<MuscleGroup, List<String>> = mapOf(
        MuscleGroup.petto to listOf("chest-press-machine", "pec-deck", "incline-chest-press-machine", "cable-fly", "bench-press-barbell"),
        MuscleGroup.schiena to listOf("lat-pulldown", "seated-cable-row", "row-machine", "assisted-pull-up", "back-extension-machine"),
        MuscleGroup.gambe to listOf("leg-press-45", "leg-extension", "seated-leg-curl", "standing-calf-machine", "hack-squat"),
        MuscleGroup.spalle to listOf("shoulder-press-machine", "lateral-raise-machine", "reverse-pec-deck", "cable-lateral-raise", "face-pull-rope"),
        MuscleGroup.braccia to listOf("curl-machine", "triceps-pushdown", "cable-curl", "triceps-machine", "preacher-curl-machine"),
        MuscleGroup.core to listOf("crunch-machine", "cable-crunch", "rotary-torso", "roman-chair", "hanging-leg-raise"),
    )

    /** Quanti, dall'inizio della lista, partono già scelti. */
    const val PRESELECTED = 3

    /** Un giorno della scheda: il gruppo, il suo nome e i macchinari del catalogo, nell'ordine. */
    data class Day(val group: MuscleGroup, val name: String, val machines: List<CatalogEntry>)

    /** Un macchinario che il primo avvio crea dal catalogo. */
    data class NewExercise(
        val uuid: String, val entry: CatalogEntry, val sortIndex: Int,
        val limbs: Limbs, val barWeight: Double, val plateLoaded: Boolean,
    )

    data class NewDay(val uuid: String, val name: String, val items: List<TemplateItem>, val sortIndex: Int, val icon: String)

    /** Cosa creare: la scheda in uso, i suoi giorni e i macchinari che non c'erano. `exerciseUuids` serve a disfare tutto. */
    data class Plan(val programUuid: String, val programName: String, val programSortIndex: Int, val days: List<NewDay>, val exercises: List<NewExercise>)

    /** Un macchinario che hai già (non archiviato), per non crearlo due volte. */
    data class Owned(val uuid: String, val catalogID: String, val isArchived: Boolean, val sortIndex: Int)

    /**
     * Una scheda con un giorno per gruppo, nell'ordine dato, righe senza serie né ripetizioni né peso. Il macchinario si crea dal
     * catalogo solo se non ce l'hai già (non archiviato). `topExercise`/`topDay`/`topProgram`: i sortIndex più alti che ci sono già.
     */
    fun build(
        name: String, days: List<Day>, owned: List<Owned>, unit: WeightUnit,
        topExercise: Int = owned.maxOfOrNull { it.sortIndex } ?: -1, topDay: Int = -1, topProgram: Int = -1,
        newUuid: () -> String = Uuids::new,
    ): Plan {
        val made = HashMap<String, String>()
        val created = mutableListOf<NewExercise>()
        var top = topExercise
        val newDays = days.mapIndexed { i, day ->
            val items = mutableListOf<TemplateItem>()
            for (entry in day.machines) {
                val id = made[entry.id] ?: owned.firstOrNull { it.catalogID == entry.id && !it.isArchived }?.uuid ?: run {
                    top += 1
                    val limbs = Limbs.limbsOf(null, false, entry.id, entry.name(true), entry.group)
                    val ex = NewExercise(
                        newUuid(), entry, top, limbs,
                        Plates.defaultBar(entry.machineGlyph, entry.nameIt, unit), Plates.guessPlateLoaded(entry.machineGlyph, entry.nameIt),
                    )
                    made[entry.id] = ex.uuid
                    created += ex
                    ex.uuid
                }
                if (items.none { it.exercise == id }) items += TemplateItem(id)
            }
            NewDay(newUuid(), day.name, items, topDay + 1 + i, "muscle:${day.group.raw}")
        }
        return Plan(newUuid(), name, topProgram + 1, newDays, created)
    }
}
