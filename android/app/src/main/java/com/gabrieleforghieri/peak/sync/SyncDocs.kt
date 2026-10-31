package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.AccentPalette
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.PeakDates
import com.gabrieleforghieri.peak.domain.SettingKeys
import com.gabrieleforghieri.peak.domain.SettingsCodec
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.Uuids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Chiave → contenuto del documento. */
typealias Encoded = Map<String, JsonObject>

/**
 * I documenti sincronizzati con il server: come le righe del database diventano documenti ([encodeAll]) e come quelli che arrivano
 * diventano righe ([apply]). Traduzione di `SyncDocs.swift`, con i validatori di `parseDoc` in common/docs.ts.
 *
 * Cosa NON viaggia mai: i massimali «da allenamento» (`isEstimated`) e il flag `isPersonalRecord`: si ricalcolano dalle serie
 * (RecordService), uguali su ogni dispositivo. I pesi sono sempre in kg, le date a secondi interi (PeakDates), gli uuid in maiuscolo.
 */
object SyncDocs {

    /** Ordine di applicazione: prima i genitori (macchinari, allenamenti), poi ciò che li richiama. */
    val applyOrder = listOf("setting", "exercise", "program", "template", "workout", "record", "set", "bodyweight")

    fun key(kind: String, uuid: String) = "$kind:$uuid"

    // ---- codifica ---------------------------------------------------------------------------------------

    /** Le impostazioni che seguono l'account: le sole `SettingKeys.synced`, col valore di partenza se non scelto. */
    fun settingDocs(values: Map<String, String>): Encoded {
        val settings = SettingsCodec.decode(values)
        val out = LinkedHashMap<String, JsonObject>()
        for (name in SettingKeys.synced) {
            val text = SettingsCodec.encode(name, settings) ?: continue
            out[key("setting", name)] = JsonObject(mapOf("value" to tidy(Json.parseToJsonElement(text))))
        }
        return out
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to json(v) })

    private fun json(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Double -> number(v)
        else -> error("tipo non previsto: ${v::class}")
    }

    /** Un numero intero si scrive senza «.0» (come lo scrive l'iPhone): 60 e non 60.0. */
    private fun number(v: Double): JsonPrimitive = if (v == Math.floor(v) && Math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

    /** Dappertutto, i numeri interi senza «.0». */
    fun tidy(e: JsonElement): JsonElement = when (e) {
        is JsonArray -> JsonArray(e.map(::tidy))
        is JsonObject -> JsonObject(e.mapValues { tidy(it.value) })
        is JsonPrimitive -> if (!e.isString && e !is JsonNull) e.doubleOrNull?.let(::number) ?: e else e
        else -> e
    }

    private fun strings(csv: String) = JsonArray(csv.split(',').filter { it.isNotEmpty() }.map(::JsonPrimitive))

    /** Tutto il resto. */
    fun encodeModels(s: Snapshot): Encoded {
        val out = LinkedHashMap<String, JsonObject>()
        for (e in s.exercises) {
            val fields = mutableMapOf(
                "name" to json(e.name), "muscleGroup" to json(e.muscleGroup), "notes" to json(e.notes),
                "incrementStep" to json(e.incrementStep), "sortIndex" to json(e.sortIndex), "isArchived" to json(e.isArchived),
                "createdAt" to json(PeakDates.string(e.createdAt)), "glyph" to json(e.glyph), "photo" to json(e.photo),
                "photoOffset" to json(e.photoOffset), "isUnilateral" to json(e.isUnilateral), "restSeconds" to json(e.restSeconds),
                "catalog" to json(e.catalogID), "hidden" to json(e.isHidden), "muscles" to strings(e.muscles), "view" to json(e.view),
                "custom" to json(e.isCustom), "barWeight" to json(e.barWeight), "weightDisplay" to json(e.weightDisplay),
                "plateLoaded" to json(e.plateLoaded), "perSide" to json(e.perSide),
            )
            // solo se scelto: assente = di base, e un client vecchio non lo cancella (KEEP_IF_ABSENT)
            if (e.limbs != null) fields["limbs"] = json(e.limbs)
            out[key("exercise", e.uuid)] = JsonObject(fields)
        }
        // solo i massimali inseriti a mano: gli altri si ricalcolano dalle serie
        for (r in s.records) {
            if (r.isEstimated || r.exerciseUuid == null) continue
            out[key("record", r.uuid)] = obj(
                "exercise" to r.exerciseUuid, "date" to PeakDates.string(r.date), "weight" to r.weight, "reps" to r.reps,
                "note" to r.note, "singleArm" to r.singleArm,
            )
        }
        for (w in s.workouts) {
            out[key("workout", w.uuid)] = obj(
                "date" to PeakDates.string(w.date), "endedAt" to w.endedAt?.let(PeakDates::string), "note" to w.note, "name" to w.name,
                "focus" to w.focus, "template" to w.template, "plan" to TemplateItems.json(TemplateItems.decode(w.plan)),
            )
        }
        for (x in s.sets) {
            if (x.workoutUuid == null) continue
            out[key("set", x.uuid)] = obj(
                "workout" to x.workoutUuid, "exercise" to x.exerciseUuid, "weight" to x.weight, "reps" to x.reps, "rpe" to x.rpe,
                "isWarmup" to x.isWarmup, "order" to x.order, "createdAt" to PeakDates.string(x.createdAt),
                "noRecord" to x.excludedFromRecords, "singleArm" to x.singleArm,
            )
        }
        for (t in s.templates) {
            out[key("template", t.uuid)] = obj(
                "name" to t.name, "items" to TemplateItems.json(TemplateItems.decode(t.items)), "sortIndex" to t.sortIndex,
                "createdAt" to PeakDates.string(t.createdAt), "hidden" to t.isHidden, "icon" to t.icon, "program" to t.program,
                "weekday" to t.weekday,
            )
        }
        for (p in s.programs) {
            out[key("program", p.uuid)] = obj(
                "name" to p.name, "sortIndex" to p.sortIndex, "createdAt" to PeakDates.string(p.createdAt), "useDays" to p.useDays,
            )
        }
        for (b in s.bodyWeights) {
            out[key("bodyweight", b.uuid)] = obj("date" to PeakDates.string(b.date), "weight" to b.weight, "fromHealth" to b.fromHealth)
        }
        return out
    }

    fun encodeAll(s: Snapshot): Encoded = settingDocs(s.settings) + encodeModels(s)

    /** Il JSON del documento, compatto e a chiavi ordinate: lo stesso contenuto dà sempre gli stessi byte. */
    fun bytes(doc: JsonObject): ByteArray = StringBuilder().also { canonical(doc, it) }.toString().encodeToByteArray()

    private fun canonical(e: JsonElement, out: StringBuilder) {
        when (e) {
            is JsonObject -> {
                out.append('{')
                e.entries.sortedBy { it.key }.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    out.append(JsonPrimitive(k)).append(':')
                    canonical(v, out)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                e.forEachIndexed { i, v -> if (i > 0) out.append(','); canonical(v, out) }
                out.append(']')
            }
            else -> out.append(e.toString())
        }
    }

    /** Hash del contenuto (sha256 di [bytes]). */
    fun hash(doc: JsonObject): String = sha256Hex(bytes(doc))

    // ---- applicazione -----------------------------------------------------------------------------------

    class Applied {
        val upserted = mutableSetOf<String>()
        val deleted = mutableSetOf<String>()

        /** Documenti che non si possono applicare (il genitore non c'è): si ricordano, ma non si cancellano mai. */
        val skipped = mutableSetOf<String>()
    }

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull
    private fun JsonObject.int(k: String): Int? = num(k)?.toInt()
    private fun JsonObject.flag(k: String): Boolean = (this[k] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.booleanOrNull ?: false
    private fun JsonObject.date(k: String): Long? = PeakDates.parse(text(k))

    /** Il valore di un'impostazione, già pulito e come testo JSON; `null` se non vale (resta quello di prima). Come `cleanSetting` in common/docs.ts. */
    fun cleanSetting(name: String, value: JsonElement?): String? {
        val p = value as? JsonPrimitive
        val text = p?.takeIf { it.isString }?.content
        val number = p?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull
        fun int(lo: Int, hi: Int) = number?.takeIf { it == Math.floor(it) && it >= lo && it <= hi }?.toInt()?.toString()
        fun bool() = p?.takeIf { !it.isString && it !is JsonNull }?.booleanOrNull?.toString()
        fun string(s: String) = JsonPrimitive(s).toString()
        return when (name) {
            SettingKeys.UNIT -> text?.takeIf { it == "kg" || it == "lb" }?.let(::string)
            SettingKeys.ACCENT -> text?.takeIf { AccentPalette.of(it) != null }?.let(::string)
            SettingKeys.REST_TIMER_ENABLED, SettingKeys.PROGRESSION_ENABLED, SettingKeys.PLATE_CALCULATOR_ENABLED,
            SettingKeys.TUTORIAL_DONE, SettingKeys.MACHINE_TIPS_SEEN -> bool()
            SettingKeys.REST_SECONDS -> int(15, 600)
            SettingKeys.WARMUP_REST_PERCENT -> int(0, 100)
            SettingKeys.ACTIVE_PROGRAM -> text?.let { if (it.isEmpty()) string("") else Uuids.normalize(it)?.let(::string) }
            SettingKeys.LANGUAGE -> text?.takeIf { it == "" || it == "it" || it == "en" }?.let(::string)
            SettingKeys.WEEKLY_GOAL -> int(0, 7)
            SettingKeys.TOUR_SEEN -> int(1, 10)
            SettingKeys.REP_RANGE_MIN, SettingKeys.REP_RANGE_MAX -> int(1, 30)
            SettingKeys.AVAILABLE_PLATES -> (value as? JsonArray)?.takeIf { a ->
                a.size <= 20 && a.all { e -> ((e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: -1.0) in 0.01..100.0 }
            }?.toString()
            else -> null
        }
    }

    /**
     * Applica i documenti arrivati dal server alle righe di [s]. Restituisce cosa è successo a ogni chiave e cosa scrivere ([Changes]); non
     * tocca il database e non ricalcola i massimali: lo fa il chiamante. `hasPhoto(sha)` dice se i byte di una foto sono già sul telefono.
     */
    fun apply(docs: List<WireDoc>, s: Snapshot, hasPhoto: (String) -> Boolean): Pair<Applied, Changes> {
        val result = Applied()
        val exercises = s.exercises.associateByTo(LinkedHashMap()) { it.uuid }
        val workouts = s.workouts.associateByTo(LinkedHashMap()) { it.uuid }
        val templates = s.templates.associateByTo(LinkedHashMap()) { it.uuid }
        val programs = s.programs.associateByTo(LinkedHashMap()) { it.uuid }
        val records = s.records.associateByTo(LinkedHashMap()) { it.uuid }
        val sets = s.sets.associateByTo(LinkedHashMap()) { it.uuid }
        val weights = s.bodyWeights.associateByTo(LinkedHashMap()) { it.uuid }
        val touched = mutableMapOf<String, MutableSet<String>>() // tipo → uuid scritti
        val gone = mutableMapOf<String, MutableSet<String>>()
        val settings = LinkedHashMap<String, String>()
        fun touch(kind: String, uuid: String) { touched.getOrPut(kind) { mutableSetOf() } += uuid; gone[kind]?.remove(uuid) }

        val ordered = docs.sortedBy { applyOrder.indexOf(it.kind).let { i -> if (i < 0) 99 else i } }
        for (doc in ordered) {
            val uuid = Uuids.normalize(doc.uuid)
            val key = doc.key

            if (doc.deleted) {
                if (uuid != null) {
                    val removed = when (doc.kind) {
                        "exercise" -> exercises.remove(uuid) != null
                        "workout" -> workouts.remove(uuid) != null
                        "template" -> templates.remove(uuid) != null
                        "program" -> programs.remove(uuid) != null
                        "record" -> records.remove(uuid) != null
                        "set" -> sets.remove(uuid) != null
                        "bodyweight" -> weights.remove(uuid) != null
                        else -> false
                    }
                    if (removed) { gone.getOrPut(doc.kind) { mutableSetOf() } += uuid; touched[doc.kind]?.remove(uuid) }
                }
                result.deleted += key
                continue
            }
            val d = doc.data
            if (d == null) { result.skipped += key; continue }

            when (doc.kind) {
                "setting" -> {
                    val clean = cleanSetting(doc.uuid, d["value"])
                    if (clean != null && doc.uuid in SettingKeys.synced) { settings[doc.uuid] = clean; result.upserted += key } else result.skipped += key
                }

                "exercise" -> {
                    val name = d.text("name")
                    val created = d.date("createdAt")
                    if (uuid == null || name == null || created == null) { result.skipped += key; continue }
                    val base = exercises[uuid] ?: ExerciseEntity(uuid = uuid)
                    val sha = d.text("photo") ?: ""
                    exercises[uuid] = base.copy(
                        name = name,
                        muscleGroup = MuscleGroup.of(d.text("muscleGroup"))?.raw ?: MuscleGroup.petto.raw,
                        notes = d.text("notes") ?: "",
                        incrementStep = d.num("incrementStep") ?: 2.5,
                        sortIndex = d.int("sortIndex") ?: 0,
                        isArchived = d.flag("isArchived"),
                        createdAt = created,
                        glyph = MachineGlyph.of(d.text("glyph"))?.raw ?: "",
                        photoOffset = d.num("photoOffset") ?: 0.0,
                        isUnilateral = d.flag("isUnilateral"),
                        restSeconds = d.int("restSeconds") ?: 0,
                        catalogID = d.text("catalog") ?: "",
                        isHidden = d.flag("hidden"),
                        // i campi dei muscoli: un server vecchio non li manda, e allora restano quelli che ci sono
                        muscles = (d["muscles"] as? JsonArray)?.takeIf { a -> a.all { (it as? JsonPrimitive)?.isString == true } }
                            ?.joinToString(",") { (it as JsonPrimitive).content } ?: base.muscles,
                        view = d.text("view")?.takeIf { it == "" || it == "front" || it == "back" } ?: base.view,
                        isCustom = (d["custom"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: base.isCustom,
                        barWeight = d.num("barWeight") ?: base.barWeight,
                        weightDisplay = d.text("weightDisplay")?.takeIf { it == "absolute" || it == "relative" } ?: base.weightDisplay,
                        plateLoaded = (d["plateLoaded"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: base.plateLoaded,
                        perSide = (d["perSide"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: base.perSide,
                        limbs = d.text("limbs")?.takeIf { l -> Limbs.entries.any { it.name == l } } ?: base.limbs,
                        photo = if (sha.isEmpty()) "" else if (hasPhoto(sha)) sha else base.photo,
                    )
                    touch("exercise", uuid)
                    result.upserted += key
                }

                "workout" -> {
                    val start = d.date("date")
                    if (uuid == null || start == null) { result.skipped += key; continue }
                    val base = workouts[uuid] ?: WorkoutEntity(uuid = uuid)
                    workouts[uuid] = base.copy(
                        date = start, endedAt = d.date("endedAt"), note = d.text("note") ?: "", name = d.text("name") ?: "",
                        focus = d.text("focus") ?: "", template = d.text("template") ?: "",
                        plan = TemplateItems.encode(TemplateItems.parse(d["plan"])),
                    )
                    touch("workout", uuid)
                    result.upserted += key
                }

                "template" -> {
                    val name = d.text("name")
                    val created = d.date("createdAt")
                    if (uuid == null || name == null || created == null) { result.skipped += key; continue }
                    val base = templates[uuid] ?: WorkoutTemplateEntity(uuid = uuid)
                    templates[uuid] = base.copy(
                        name = name, items = TemplateItems.encode(TemplateItems.parse(d["items"])), sortIndex = d.int("sortIndex") ?: 0,
                        createdAt = created, isHidden = d.flag("hidden"), icon = d.text("icon") ?: "",
                        program = d.text("program")?.uppercase() ?: "", weekday = d.int("weekday") ?: -1,
                    )
                    touch("template", uuid)
                    result.upserted += key
                }

                "program" -> {
                    val name = d.text("name")
                    val created = d.date("createdAt")
                    if (uuid == null || name == null || created == null) { result.skipped += key; continue }
                    val base = programs[uuid] ?: WorkoutProgramEntity(uuid = uuid)
                    programs[uuid] = base.copy(name = name, sortIndex = d.int("sortIndex") ?: 0, createdAt = created, useDays = d.flag("useDays"))
                    touch("program", uuid)
                    result.upserted += key
                }

                "record" -> {
                    val `when` = d.date("date")
                    val weight = d.num("weight")
                    val reps = d.int("reps")
                    val exercise = Uuids.normalize(d.text("exercise"))?.takeIf { it in exercises }
                    if (uuid == null || `when` == null || weight == null || reps == null || exercise == null) { result.skipped += key; continue }
                    val base = records[uuid] ?: MaxRecordEntity(uuid = uuid)
                    records[uuid] = base.copy(
                        exerciseUuid = exercise, date = `when`, weight = weight, reps = reps, isEstimated = false,
                        note = d.text("note") ?: "", singleArm = d.flag("singleArm"),
                    )
                    touch("record", uuid)
                    result.upserted += key
                }

                "set" -> {
                    val created = d.date("createdAt")
                    val weight = d.num("weight")
                    val reps = d.int("reps")
                    val workout = Uuids.normalize(d.text("workout"))?.takeIf { it in workouts }
                    if (uuid == null || created == null || weight == null || reps == null || workout == null) { result.skipped += key; continue }
                    val base = sets[uuid] ?: WorkoutSetEntity(uuid = uuid)
                    sets[uuid] = base.copy(
                        workoutUuid = workout, exerciseUuid = Uuids.normalize(d.text("exercise"))?.takeIf { it in exercises },
                        weight = weight, reps = reps, rpe = d.num("rpe"), isWarmup = d.flag("isWarmup"), order = d.int("order") ?: 0,
                        createdAt = created, excludedFromRecords = d.flag("noRecord"), singleArm = d.flag("singleArm"),
                    )
                    touch("set", uuid)
                    result.upserted += key
                }

                "bodyweight" -> {
                    val `when` = d.date("date")
                    val weight = d.num("weight")
                    if (uuid == null || `when` == null || weight == null) { result.skipped += key; continue }
                    val base = weights[uuid] ?: BodyWeightEntity(uuid = uuid)
                    weights[uuid] = base.copy(date = `when`, weight = weight, fromHealth = d.flag("fromHealth"))
                    touch("bodyweight", uuid)
                    result.upserted += key
                }

                else -> result.skipped += key
            }
        }

        fun <T> pick(map: Map<String, T>, kind: String) = touched[kind].orEmpty().mapNotNull { map[it] }
        return result to Changes(
            exercises = pick(exercises, "exercise"), records = pick(records, "record"), workouts = pick(workouts, "workout"),
            sets = pick(sets, "set"), bodyWeights = pick(weights, "bodyweight"), templates = pick(templates, "template"),
            programs = pick(programs, "program"),
            gone = Gone(
                exercises = gone["exercise"].orEmpty(), records = gone["record"].orEmpty(), workouts = gone["workout"].orEmpty(),
                sets = gone["set"].orEmpty(), bodyWeights = gone["bodyweight"].orEmpty(), templates = gone["template"].orEmpty(),
                programs = gone["program"].orEmpty(),
            ),
            settings = settings,
        )
    }
}
