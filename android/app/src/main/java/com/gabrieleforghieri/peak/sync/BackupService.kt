package com.gabrieleforghieri.peak.sync

import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.Catalog
import com.gabrieleforghieri.peak.domain.Limbs
import com.gabrieleforghieri.peak.domain.MachineGlyph
import com.gabrieleforghieri.peak.domain.MuscleGroup
import com.gabrieleforghieri.peak.domain.PeakDates
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.domain.suggested
import java.util.Base64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

enum class RestoreMode {
    /** Cancella tutto e riparte dal file. */
    Replace,

    /** Aggiunge solo ciò che non è già presente (confronto per uuid). */
    Merge,
}

sealed class BackupException(message: String) : Exception(message) {
    class InvalidFile : BackupException("Il file non è un backup di Massimali valido.")
    class UnsupportedVersion(val version: Int) : BackupException("Backup in versione $version: serve una versione più recente dell'app.")
    class WorkoutInProgress : BackupException("Termina l'allenamento in corso prima di importare.")
}

// Il formato del file: BackupDTO.swift, lo stesso di server/backup.ts. Le chiavi opzionali si omettono se assenti; le date sono ISO a secondi interi.

@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: String,
    val exercises: List<ExerciseDTO>,
    val workouts: List<WorkoutDTO>,
    /** Opzionale: i backup scritti prima del peso corporeo restano leggibili. */
    val bodyWeights: List<BodyWeightDTO>? = null,
    /** Opzionale: i backup scritti prima delle schede restano leggibili. */
    val templates: List<TemplateDTO>? = null,
    /** Opzionale: i backup scritti prima delle schede generali restano leggibili. */
    val programs: List<ProgramDTO>? = null,
)

@Serializable
data class ExerciseDTO(
    val uuid: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val incrementStep: Double,
    val sortIndex: Int,
    val isArchived: Boolean,
    val createdAt: String,
    val glyph: String? = null,
    /** Foto in base64. */
    val photo: String? = null,
    val photoOffset: Double? = null,
    val isUnilateral: Boolean? = null,
    val restSeconds: Int? = null,
    val catalogID: String? = null,
    val isHidden: Boolean? = null,
    val muscles: List<String>? = null,
    val view: String? = null,
    val isCustom: Boolean? = null,
    val barWeight: Double? = null,
    val weightDisplay: String? = null,
    val plateLoaded: Boolean? = null,
    val perSide: Boolean? = null,
    val limbs: String? = null,
    val records: List<MaxRecordDTO>,
)

@Serializable
data class MaxRecordDTO(
    val uuid: String,
    val date: String,
    val weight: Double,
    val reps: Int,
    val isEstimated: Boolean,
    val note: String,
    val singleArm: Boolean? = null,
)

@Serializable
data class WorkoutDTO(
    val uuid: String,
    val date: String,
    val endedAt: String? = null,
    val note: String,
    val focus: String? = null,
    val name: String? = null,
    val template: String? = null,
    val plan: List<TemplateItemDTO>? = null,
    val sets: List<WorkoutSetDTO>,
)

@Serializable
data class TemplateDTO(
    val uuid: String,
    val name: String,
    val sortIndex: Int,
    val createdAt: String,
    val isHidden: Boolean? = null,
    val icon: String? = null,
    val programID: String? = null,
    val weekday: Int? = null,
    val items: List<TemplateItemDTO>,
)

@Serializable
data class ProgramDTO(
    val uuid: String,
    val name: String,
    val sortIndex: Int,
    val createdAt: String,
    val useDays: Boolean? = null,
)

/** Una riga di scheda. `weight` assente = si mostra l'ultimo usato. */
@Serializable
data class TemplateItemDTO(
    val exerciseUUID: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weight: Double? = null,
)

@Serializable
data class WorkoutSetDTO(
    val uuid: String,
    val exerciseUUID: String? = null,
    val weight: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isWarmup: Boolean,
    val order: Int,
    val createdAt: String,
    val isPersonalRecord: Boolean,
    val excludedFromRecords: Boolean? = null,
    val singleArm: Boolean? = null,
)

@Serializable
data class BodyWeightDTO(
    val uuid: String,
    val date: String,
    val weight: Double,
    val fromHealth: Boolean,
)

/** Il file di backup: scrivere ([encode]), leggere ([parse]) e decidere cosa cambia nel database ([plan]). Logica pura, provata sul JVM. */
object BackupCodec {
    const val CURRENT_VERSION = 1

    private val lenient = Json { ignoreUnknownKeys = true }
    private val writer = Json { encodeDefaults = false; explicitNulls = false }

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** JSON indentato e a chiavi ordinate, come lo scrive l'app. */
    fun encode(file: BackupFile): ByteArray {
        fun sort(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associate { it.key to sort(it.value) })
            is JsonArray -> JsonArray(e.map(::sort))
            else -> e
        }
        return pretty.encodeToString(JsonElement.serializer(), sort(SyncDocs.tidy(writer.encodeToJsonElement(BackupFile.serializer(), file)))).encodeToByteArray()
    }

    /** Legge un file. Una versione più nuova della nostra si riconosce prima di tutto il resto (come sul server). */
    fun parse(data: ByteArray): BackupFile {
        val root = try { lenient.parseToJsonElement(data.decodeToString()) as? JsonObject } catch (_: Exception) { null } ?: throw BackupException.InvalidFile()
        val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (version != null && version > CURRENT_VERSION) throw BackupException.UnsupportedVersion(version)
        return try { lenient.decodeFromJsonElement(BackupFile.serializer(), root) } catch (_: Exception) { throw BackupException.InvalidFile() }
    }

    // ---- esportazione -----------------------------------------------------------------------------------

    private fun cmp(a: String, b: String) = a.compareTo(b)

    /** Il database come file. `photo(sha)` dà i byte della foto, o `null` se non ci sono. */
    fun snapshot(s: Snapshot, photo: (String) -> ByteArray?, now: Long): BackupFile {
        val live = s.exercises.mapTo(HashSet()) { it.uuid }
        fun items(raw: String) = TemplateItems.decode(raw).filter { it.exercise in live }
            .map { TemplateItemDTO(it.exercise, it.sets, it.repsMin, it.repsMax, it.weight) }

        val exercises = s.exercises.sortedWith(compareBy<ExerciseEntity>({ it.sortIndex }, { it.createdAt }, { it.uuid })).map { e ->
            ExerciseDTO(
                uuid = e.uuid, name = e.name, muscleGroup = e.muscleGroup, notes = e.notes, incrementStep = e.incrementStep,
                sortIndex = e.sortIndex, isArchived = e.isArchived, createdAt = PeakDates.string(e.createdAt),
                glyph = e.glyph.ifEmpty { null },
                photo = if (e.photo.isEmpty()) null else photo(e.photo)?.let { Base64.getEncoder().encodeToString(it) },
                photoOffset = e.photoOffset.takeIf { it != 0.0 },
                isUnilateral = true.takeIf { e.isUnilateral },
                restSeconds = e.restSeconds.takeIf { it != 0 },
                catalogID = e.catalogID.ifEmpty { null },
                isHidden = true.takeIf { e.isHidden },
                muscles = e.muscles.split(',').filter { it.isNotEmpty() }.ifEmpty { null },
                view = e.view.ifEmpty { null },
                isCustom = true.takeIf { e.isCustom },
                barWeight = e.barWeight.takeIf { it > 0 },
                weightDisplay = e.weightDisplay.takeIf { it != "absolute" },
                plateLoaded = true.takeIf { e.plateLoaded },
                perSide = true.takeIf { e.perSide },
                limbs = e.limbs,
                records = s.records.filter { it.exerciseUuid == e.uuid }.sortedWith(compareBy<MaxRecordEntity>({ it.date }, { it.uuid })).map { r ->
                    MaxRecordDTO(r.uuid, PeakDates.string(r.date), r.weight, r.reps, r.isEstimated, r.note, true.takeIf { r.singleArm })
                },
            )
        }

        val workouts = s.workouts.sortedWith(compareBy<WorkoutEntity>({ it.date }, { it.uuid })).map { w ->
            WorkoutDTO(
                uuid = w.uuid, date = PeakDates.string(w.date), endedAt = w.endedAt?.let(PeakDates::string), note = w.note,
                focus = w.focus, name = w.name, template = w.template.ifEmpty { null }, plan = items(w.plan).ifEmpty { null },
                sets = s.sets.filter { it.workoutUuid == w.uuid }.sortedWith(compareBy<WorkoutSetEntity>({ it.order }, { it.uuid })).map { x ->
                    WorkoutSetDTO(
                        uuid = x.uuid, exerciseUUID = x.exerciseUuid, weight = x.weight, reps = x.reps, rpe = x.rpe, isWarmup = x.isWarmup,
                        order = x.order, createdAt = PeakDates.string(x.createdAt), isPersonalRecord = x.isPersonalRecord,
                        excludedFromRecords = true.takeIf { x.excludedFromRecords }, singleArm = true.takeIf { x.singleArm },
                    )
                },
            )
        }

        val bodyWeights = s.bodyWeights.sortedWith(compareBy<BodyWeightEntity>({ it.date }, { it.uuid })).map {
            BodyWeightDTO(it.uuid, PeakDates.string(it.date), it.weight, it.fromHealth)
        }

        val templates = s.templates.sortedWith(compareBy<WorkoutTemplateEntity>({ it.sortIndex }, { it.createdAt }, { it.uuid })).map { t ->
            TemplateDTO(
                uuid = t.uuid, name = t.name, sortIndex = t.sortIndex, createdAt = PeakDates.string(t.createdAt),
                isHidden = true.takeIf { t.isHidden }, icon = t.icon.ifEmpty { null },
                programID = Uuids.normalize(t.program), weekday = t.weekday.takeIf { it >= 0 }, items = items(t.items),
            )
        }

        val programs = s.programs.sortedWith(compareBy<WorkoutProgramEntity>({ it.sortIndex }, { it.createdAt }, { it.uuid })).map {
            ProgramDTO(it.uuid, it.name, it.sortIndex, PeakDates.string(it.createdAt), true.takeIf { _ -> it.useDays })
        }

        return BackupFile(CURRENT_VERSION, PeakDates.string(now), exercises, workouts, bodyWeights, templates, programs.ifEmpty { null })
    }

    // ---- ripristino -------------------------------------------------------------------------------------

    class Plan(val changes: Changes, /** Le foto del file, per sha256: da salvare prima di scrivere il database. */ val photos: Map<String, ByteArray>)

    private fun date(text: String?): Long = PeakDates.parse(text) ?: throw BackupException.InvalidFile()
    private fun uuid(text: String?): String = Uuids.normalize(text) ?: throw BackupException.InvalidFile()

    /**
     * Cosa scrivere nel database per ripristinare [file] sopra [before]. In `Replace` si riparte dal file (le impostazioni restano); in
     * `Merge` si aggiunge solo ciò che non c'è (confronto per uuid): un macchinario già presente non si tocca, gli si aggiungono i
     * massimali che mancano. I massimali «da allenamento» del file non si importano: si ricalcolano dalle serie.
     */
    fun plan(file: BackupFile, before: Snapshot, mode: RestoreMode): Plan {
        val replace = mode == RestoreMode.Replace
        val base = if (replace) Snapshot(settings = before.settings) else before

        val exercises = base.exercises.associateByTo(LinkedHashMap()) { it.uuid }
        val newExercises = mutableListOf<ExerciseEntity>()
        val newRecords = mutableListOf<MaxRecordEntity>()
        val recordUuids = base.records.mapTo(HashSet()) { it.uuid }
        val photos = LinkedHashMap<String, ByteArray>()

        for (dto in file.exercises) {
            val id = uuid(dto.uuid)
            val existing = exercises[id]
            // In merge un esercizio già presente non viene sovrascritto: si aggiungono solo i record che mancano.
            if (existing != null) {
                addMissingRecords(dto, id, recordUuids, newRecords)
                continue
            }
            val group = MuscleGroup.of(dto.muscleGroup)?.raw ?: MuscleGroup.petto.raw
            // un backup vecchio non ha l'id: lo si ricava dal nome; se resta personalizzato tiene l'icona che vedeva.
            // Un macchinario fatto a mano di proposito (isCustom) non si ricollega al catalogo per nome: come server/backup.ts (planImport).
            // ponytail: `RestoreMode` di BackupService.swift lo ricollega comunque per nome; qui si segue il server, vedi HANDOFF.md
            val catalogID = if (dto.isCustom == true) "" else dto.catalogID ?: Catalog.match(dto.name)?.id ?: ""
            var glyph = MachineGlyph.of(dto.glyph)?.raw ?: ""
            if (dto.catalogID == null && catalogID.isEmpty() && glyph.isEmpty()) {
                glyph = MachineGlyph.suggested(dto.name, MuscleGroup.of(group) ?: MuscleGroup.petto).raw
            }
            val bytes = dto.photo?.let { try { Base64.getMimeDecoder().decode(it) } catch (_: IllegalArgumentException) { null } }?.takeIf { it.isNotEmpty() }
            val sha = bytes?.let { sha256Hex(it).also { s -> photos[s] = it } } ?: ""
            val entity = ExerciseEntity(
                uuid = id, name = dto.name, muscleGroup = group, notes = dto.notes, incrementStep = dto.incrementStep,
                sortIndex = dto.sortIndex, isArchived = dto.isArchived, glyph = glyph, photo = sha, photoOffset = dto.photoOffset ?: 0.0,
                isUnilateral = dto.isUnilateral ?: false, restSeconds = dto.restSeconds ?: 0, createdAt = date(dto.createdAt),
                catalogID = catalogID, isHidden = dto.isHidden ?: false, muscles = (dto.muscles ?: emptyList()).joinToString(","),
                view = dto.view ?: "", isCustom = dto.isCustom ?: false, barWeight = dto.barWeight ?: 0.0,
                weightDisplay = dto.weightDisplay ?: "absolute", plateLoaded = dto.plateLoaded ?: false, perSide = dto.perSide ?: false,
                limbs = dto.limbs?.takeIf { l -> Limbs.entries.any { it.name == l } },
            )
            exercises[id] = entity
            newExercises += entity
            addMissingRecords(dto, id, recordUuids, newRecords)
        }

        val workoutUuids = base.workouts.mapTo(HashSet()) { it.uuid }
        val setUuids = base.sets.mapTo(HashSet()) { it.uuid }
        val newWorkouts = mutableListOf<WorkoutEntity>()
        val newSets = mutableListOf<WorkoutSetEntity>()
        fun items(list: List<TemplateItemDTO>?): List<TemplateItem> =
            (list ?: emptyList()).map { TemplateItem(uuid(it.exerciseUUID), it.sets, it.repsMin, it.repsMax, it.weight) }.filter { it.exercise in exercises }

        for (dto in file.workouts) {
            val id = uuid(dto.uuid)
            if (id in workoutUuids) continue
            workoutUuids += id
            newWorkouts += WorkoutEntity(
                uuid = id, date = date(dto.date), endedAt = dto.endedAt?.let(::date), note = dto.note, name = dto.name ?: "",
                focus = dto.focus ?: "", template = dto.template?.let(Uuids::normalize) ?: "", plan = TemplateItems.encode(items(dto.plan)),
            )
            for (x in dto.sets) {
                val sid = uuid(x.uuid)
                if (!setUuids.add(sid)) continue
                newSets += WorkoutSetEntity(
                    uuid = sid, workoutUuid = id, exerciseUuid = x.exerciseUUID?.let(Uuids::normalize)?.takeIf { it in exercises },
                    weight = x.weight, reps = x.reps, rpe = x.rpe, isWarmup = x.isWarmup, order = x.order, createdAt = date(x.createdAt),
                    excludedFromRecords = x.excludedFromRecords ?: false, singleArm = x.singleArm ?: false,
                )
            }
        }

        val weightUuids = base.bodyWeights.mapTo(HashSet()) { it.uuid }
        val newWeights = (file.bodyWeights ?: emptyList()).filter { weightUuids.add(uuid(it.uuid)) }.map {
            BodyWeightEntity(uuid(it.uuid), date(it.date), it.weight, it.fromHealth)
        }

        val programUuids = base.programs.mapTo(HashSet()) { it.uuid }
        val newPrograms = (file.programs ?: emptyList()).filter { programUuids.add(uuid(it.uuid)) }.map {
            WorkoutProgramEntity(uuid(it.uuid), it.name, it.sortIndex, date(it.createdAt), it.useDays ?: false)
        }

        val templateUuids = base.templates.mapTo(HashSet()) { it.uuid }
        val newTemplates = (file.templates ?: emptyList()).filter { templateUuids.add(uuid(it.uuid)) }.map { t ->
            WorkoutTemplateEntity(
                uuid = uuid(t.uuid), name = t.name, items = TemplateItems.encode(items(t.items)), sortIndex = t.sortIndex,
                createdAt = date(t.createdAt), isHidden = t.isHidden ?: false, icon = t.icon ?: "",
                program = t.programID?.let(Uuids::normalize)?.takeIf { it in programUuids } ?: "",
                weekday = t.weekday?.takeIf { it in 0..6 } ?: -1,
            )
        }

        return Plan(
            Changes(
                wipe = replace, exercises = newExercises, records = newRecords, workouts = newWorkouts, sets = newSets,
                bodyWeights = newWeights, templates = newTemplates, programs = newPrograms,
            ),
            photos,
        )
    }

    private fun addMissingRecords(dto: ExerciseDTO, exercise: String, known: MutableSet<String>, out: MutableList<MaxRecordEntity>) {
        for (r in dto.records) {
            if (r.isEstimated) continue
            val id = uuid(r.uuid)
            if (!known.add(id)) continue
            out += MaxRecordEntity(id, exercise, date(r.date), r.weight, r.reps, isEstimated = false, note = r.note, singleArm = r.singleArm ?: false)
        }
    }
}

/** Dove scrivere le copie leggibili dall'utente (la cartella Download su Android). */
interface BackupFiles {
    fun write(name: String, bytes: ByteArray)
}

/** Esporta, importa e copia di sicurezza dei dati. Traduzione di `BackupService.swift`. */
class BackupService(
    private val storage: SyncStorage,
    private val photos: PhotoStore,
    private val files: BackupFiles,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Il database come file di backup. */
    suspend fun data(): ByteArray = BackupCodec.encode(BackupCodec.snapshot(storage.snapshot(), photos::read, clock()))

    /** Ripristina un file. Un file non valido o un allenamento in corso (in `Replace`) lasciano tutto com'era. */
    suspend fun restore(data: ByteArray, mode: RestoreMode): BackupFile {
        val file = BackupCodec.parse(data)
        val before = storage.snapshot()
        // sostituire tutto con un allenamento in corso lo cancellerebbe sotto gli occhi della sessione
        if (mode == RestoreMode.Replace && before.hasActiveWorkout) throw BackupException.WorkoutInProgress()
        val plan = BackupCodec.plan(file, before, mode)
        for ((sha, bytes) in plan.photos) photos.writeAs(sha, bytes)
        storage.commit(plan.changes)
        // I record «da allenamento» non si importano: si ricalcolano dalle serie.
        storage.recomputeRecords()
        return file
    }

    /** Prima di toccare qualcosa: una copia leggibile accanto ai file dell'utente. Se non si riesce a scriverla, non si va avanti. */
    suspend fun writePreConnectionBackup() = files.write(PRE_CONNECTION_FILE, data())

    companion object {
        const val PRE_CONNECTION_FILE = "Peak-prima-del-collegamento.json"
    }
}
