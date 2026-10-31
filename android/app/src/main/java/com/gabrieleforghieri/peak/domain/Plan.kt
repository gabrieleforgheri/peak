package com.gabrieleforghieri.peak.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Una riga di scheda. Serie e ripetizioni 0 = non indicato; peso in kg, `null` = si mostra l'ultimo usato.
 * Stesso formato del documento `template` del server (`TemplateItem` in common/docs.ts). L'uuid dell'esercizio è maiuscolo.
 */
data class TemplateItem(
    val exercise: String,
    val sets: Int = 0,
    val repsMin: Int = 0,
    val repsMax: Int = 0,
    val weight: Double? = null,
)

/** Conversioni delle righe: un testo JSON per il disco e per il backup, lo stesso array del documento del server. */
object TemplateItems {
    fun json(items: List<TemplateItem>): JsonArray = JsonArray(items.map {
        JsonObject(mapOf(
            "exercise" to JsonPrimitive(it.exercise), "sets" to JsonPrimitive(it.sets),
            "repsMin" to JsonPrimitive(it.repsMin), "repsMax" to JsonPrimitive(it.repsMax),
            "weight" to (it.weight?.let(::JsonPrimitive) ?: JsonNull),
        ))
    })

    fun encode(items: List<TemplateItem>): String = if (items.isEmpty()) "" else json(items).toString()

    fun decode(raw: String): List<TemplateItem> {
        if (raw.isEmpty()) return emptyList()
        return try { parse(Json.parseToJsonElement(raw)) } catch (_: Exception) { emptyList() }
    }

    /** Dal documento del server. Una riga rotta si salta, le altre restano. */
    fun parse(value: JsonElement?): List<TemplateItem> {
        val array = value as? JsonArray ?: return emptyList()
        return array.mapNotNull { row ->
            val o = row as? JsonObject ?: return@mapNotNull null
            val id = Uuids.normalize((o["exercise"] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: return@mapNotNull null
            fun int(k: String) = (o[k] as? JsonPrimitive)?.intOrNull ?: 0
            TemplateItem(id, int("sets"), int("repsMin"), int("repsMax"), (o["weight"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull)
        }
    }
}

/** Una serie vista dal piano: basta l'esercizio, il carico, le ripetizioni e se è di riscaldamento. */
data class PlanSet(val exercise: String?, val weight: Double, val reps: Int, val isWarmup: Boolean)

/** La lista degli esercizi di un allenamento e il suo rapporto con la scheda di partenza. Stessi conti di `common/plan.ts`. */
object Plan {
    /**
     * `view`: le righe fuori piano sono vuote. `targets`: prendono ciò che si è fatto (per aggiornare la scheda senza toccare
     * gli obiettivi scritti). `done`: ogni riga con serie prende ciò che si è fatto.
     */
    enum class Mode { view, targets, done }

    fun blank(exercise: String): TemplateItem = TemplateItem(exercise)

    /** Serie di lavoro, ripetizioni min–max e peso più alto (kg) di un esercizio. */
    fun item(from: List<PlanSet>, exercise: String): TemplateItem {
        val work = from.filter { it.exercise == exercise && !it.isWarmup && it.reps > 0 }
        if (work.isEmpty()) return blank(exercise)
        val reps = work.map { it.reps }
        return TemplateItem(
            exercise = exercise,
            sets = minOf(20, work.size),
            repsMin = minOf(100, reps.min()),
            repsMax = minOf(100, reps.max()),
            weight = minOf(2000.0, work.maxOf { it.weight }),
        )
    }

    /** La lista di oggi, in ordine: le righe del piano il cui esercizio esiste ancora, poi gli esercizi con serie che nel piano non ci sono. */
    fun session(plan: List<TemplateItem>, sets: List<PlanSet>, exists: (String) -> Boolean, mode: Mode = Mode.view): List<TemplateItem> {
        val out = mutableListOf<TemplateItem>()
        val seen = HashSet<String>()
        for (p in plan) {
            if (p.exercise in seen || !exists(p.exercise)) continue
            seen += p.exercise
            if (mode == Mode.done) {
                val done = item(sets, p.exercise)
                out += if (done.sets > 0) done else p
            } else {
                out += p
            }
        }
        for (s in sets) {
            val id = s.exercise ?: continue
            if (id in seen || !exists(id)) continue
            seen += id
            out += if (mode == Mode.view) blank(id) else item(sets, id)
        }
        return out
    }

    fun adding(list: List<TemplateItem>, exercise: String): List<TemplateItem> =
        if (list.any { it.exercise == exercise }) list else list + blank(exercise)

    fun removing(list: List<TemplateItem>, exercise: String): List<TemplateItem> = list.filter { it.exercise != exercise }

    /** Al posto di `from` mette `to`: stesse serie e ripetizioni, il peso torna «ultimo usato». */
    fun replacing(list: List<TemplateItem>, from: String, to: String): List<TemplateItem> {
        if (list.any { it.exercise == to }) return list
        return list.map { if (it.exercise == from) it.copy(exercise = to, weight = null) else it }
    }

    fun moving(list: List<TemplateItem>, exercise: String, step: Int): List<TemplateItem> {
        val i = list.indexOfFirst { it.exercise == exercise }
        if (i < 0) return list
        val j = i + step
        if (j !in list.indices) return list
        val out = list.toMutableList()
        out[i] = list[j]
        out[j] = list[i]
        return out
    }

    /** La lista di oggi è diversa da quella della scheda (esercizi o ordine)? Gli obiettivi non contano. */
    fun differs(today: List<TemplateItem>, template: List<TemplateItem>, exists: (String) -> Boolean): Boolean =
        today.map { it.exercise } != template.filter { exists(it.exercise) }.map { it.exercise }
}

/** I gruppi scelti a inizio sessione («cosa alleni oggi»), come testo separato da virgola in ordine canonico (`cleanFocus` in common/docs.ts). */
object WorkoutFocus {
    fun decode(raw: String): Set<MuscleGroup> = raw.split(',').mapNotNull { MuscleGroup.of(it) }.toSet()

    fun encode(groups: Set<MuscleGroup>): String = MuscleGroup.entries.filter { it in groups }.joinToString(",") { it.raw }
}
