package com.gabrieleforghieri.peak.ui.workouts

import androidx.room.withTransaction
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.domain.TemplateItem
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.Uuids

/**
 * Tutte le scritture dell'area Allenamenti. Le serie e i massimali passano **solo** dal [RecordService]; il resto da qui, con le query
 * di `WorkoutsDao` o i DAO comuni. Niente Android: si prova sul JVM con un database in memoria (`WorkoutsActionsTest`).
 *
 * Gemello di quello che le viste di `Features/Workouts/` fanno con `context.insert`/`save` e `RecordService`.
 */
class WorkoutsActions(private val db: PeakDatabase, private val records: RecordService) {
    private val area get() = db.areaWorkouts()

    // ---- allenamento -------------------------------------------------------------------------

    /** Inizia una sessione (`begin`). */
    suspend fun begin(workout: WorkoutEntity) = db.workouts().upsert(workout)

    /** Chiude l'allenamento in corso. `true` se l'ha chiuso adesso, `false` se era già chiuso (`WorkoutFinisher.finish`). */
    suspend fun finish(workout: String, now: Long = System.currentTimeMillis()): Boolean = area.finish(workout, now) > 0

    /** Elimina l'allenamento con le sue serie; i record che aveva fatto nascere spariscono con lui. */
    suspend fun deleteWorkout(workout: String) = records.deleteWorkout(workout)

    suspend fun setNote(workout: String, note: String) = area.setNote(workout, note)

    suspend fun setNameAndFocus(workout: String, name: String, focus: String) = area.setNameAndFocus(workout, name, focus)

    suspend fun setPlan(workout: String, plan: String) = area.setPlan(workout, plan)

    // ---- serie -------------------------------------------------------------------------------

    /** Registra una serie in coda all'allenamento. `true` se è un nuovo massimale. */
    suspend fun addSet(workout: String, exercise: String, weight: Double, reps: Int, isWarmup: Boolean, singleArm: Boolean): Boolean =
        records.addSet(workout, exercise, weight, reps, isWarmup, singleArm)

    /**
     * Applica la correzione e ricalcola i record del macchinario: una serie corretta al ribasso non deve lasciarsi dietro il massimale
     * che aveva fatto scattare, e le serie dopo di lei si ricontrollano. `true` se la serie corretta è un massimale.
     */
    suspend fun updateSet(set: String, weight: Double, reps: Int, isWarmup: Boolean, singleArm: Boolean): Boolean = db.withTransaction {
        area.updateSet(set, weight, reps, isWarmup, singleArm)
        db.sets().get(set)?.exerciseUuid?.let { records.recompute(it) }
        db.sets().get(set)?.isPersonalRecord == true
    }

    suspend fun deleteSet(set: String) = records.deleteSet(set)

    // ---- giorni e schede ---------------------------------------------------------------------

    /** La scheda a cui aggiungere un giorno nuovo: quella in uso, o «La mia scheda» (creata) se non ce n'è nessuna (`Programs.current`). */
    suspend fun currentProgram(activeSetting: String): WorkoutProgramEntity {
        val all = db.programs().all().sortedBy { it.sortIndex }
        activeProgram(all, activeSetting)?.let { return it }
        return WorkoutProgramEntity(Uuids.DEFAULT_PROGRAM, name = "La mia scheda", createdAt = System.currentTimeMillis()).also { db.programs().upsert(it) }
    }

    /**
     * Salva un giorno (`TemplateEditorView.save`). `programChoice`: la scheda scelta nel modulo ("" = quella in uso). Con `existing` lo
     * aggiorna, senza ne crea uno in fondo. Il giorno fisso vale solo se la scheda usa i giorni.
     */
    suspend fun saveTemplate(
        existing: WorkoutTemplateEntity?, name: String, items: List<TemplateItem>, isHidden: Boolean, icon: String,
        programChoice: String, weekday: Int, activeSetting: String, now: Long = System.currentTimeMillis(),
    ): WorkoutTemplateEntity = db.withTransaction {
        val program = db.programs().get(programChoice) ?: currentProgram(activeSetting)
        val day = if (program.useDays) weekday else -1
        val saved = if (existing != null) {
            existing.copy(name = name, items = TemplateItems.encode(items), isHidden = isHidden, icon = icon, program = program.uuid, weekday = day)
        } else {
            val top = db.templates().all().maxOfOrNull { it.sortIndex } ?: -1
            WorkoutTemplateEntity(
                Uuids.new(), name = name, items = TemplateItems.encode(items), sortIndex = top + 1, createdAt = now,
                isHidden = isHidden, icon = icon, program = program.uuid, weekday = day,
            )
        }
        db.templates().upsert(saved)
        saved
    }

    suspend fun deleteTemplate(uuid: String) = db.templates().delete(uuid)

    /** «Aggiorna il giorno con le modifiche di oggi»: la lista di oggi, tenendo gli obiettivi scritti. */
    suspend fun updateTemplateItems(template: String, items: List<TemplateItem>) = area.setTemplateItems(template, TemplateItems.encode(items))

    /**
     * Salva una scheda (`ProgramEditorView.save`): nome, giorni fissi sì o no e il giorno scelto per ogni giorno della scheda. Ne ritorna
     * l'uuid. Una scheda nuova va in fondo.
     */
    suspend fun saveProgram(existing: WorkoutProgramEntity?, name: String, useDays: Boolean, weekdays: Map<String, Int>, now: Long = System.currentTimeMillis()): WorkoutProgramEntity =
        db.withTransaction {
            val saved = if (existing != null) existing.copy(name = name, useDays = useDays) else {
                val top = db.programs().all().maxOfOrNull { it.sortIndex } ?: -1
                WorkoutProgramEntity(Uuids.new(), name = name, sortIndex = top + 1, createdAt = now, useDays = useDays)
            }
            db.programs().upsert(saved)
            if (existing != null) {
                for (day in db.templates().all().filter { it.program == saved.uuid }) {
                    val wanted = if (useDays) weekdays[day.uuid] ?: day.weekday else -1
                    if (wanted != day.weekday) area.setTemplateWeekday(day.uuid, wanted)
                }
            }
            saved
        }

    /** Elimina la scheda e i suoi giorni; gli allenamenti già fatti restano. */
    suspend fun deleteProgram(uuid: String) = db.withTransaction {
        area.deleteTemplatesOfProgram(uuid)
        db.programs().delete(uuid)
    }
}
