package com.gabrieleforghieri.peak.ui.start

import androidx.room.withTransaction
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.PeakDatabase
import com.gabrieleforghieri.peak.data.WorkoutProgramEntity
import com.gabrieleforghieri.peak.data.WorkoutTemplateEntity
import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.domain.Onboarding
import com.gabrieleforghieri.peak.domain.TemplateItems
import com.gabrieleforghieri.peak.domain.Uuids
import com.gabrieleforghieri.peak.util.Lang

/**
 * Le scritture dell'area Avvio sul database (`Onboarding.createPlan`/`undoPlan`, `Programs.adoptLoose`, `Onboarding.hasData` di Swift).
 * Il calcolo di cosa creare è `domain/Onboarding.build` (puro); qui si scrive, tutto in una transazione.
 */
class StartActions(private val db: PeakDatabase, private val now: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.areaStart()

    /** L'app ha già qualcosa dell'utente? Allora il primo avvio non serve (`Onboarding.hasData`). */
    suspend fun hasData(): Boolean = dao.userDataCount() > 0

    /**
     * `Onboarding.createPlan`: una scheda in uso con un giorno per gruppo, nell'ordine dato, righe senza serie né ripetizioni né peso.
     * Il macchinario si crea dal catalogo solo se non ce l'hai già. `activeProgram` lo imposta chi chiama (con il `programUuid` che torna).
     */
    suspend fun createPlan(name: String, days: List<Onboarding.Day>, settings: AppSettings): Onboarding.Plan = db.withTransaction {
        val owned = dao.owned().map { Onboarding.Owned(it.uuid, it.catalogID, it.isArchived, it.sortIndex) }
        val plan = Onboarding.build(
            name, days, owned, settings.unit,
            topExercise = dao.topExercise() ?: -1, topDay = dao.topTemplate() ?: -1, topProgram = dao.topProgram() ?: -1,
        )
        val at = now()
        val italian = Lang.isItalian()
        for (e in plan.exercises) {
            db.exercises().upsert(
                ExerciseEntity(
                    uuid = e.uuid, name = e.entry.name(italian), muscleGroup = e.entry.muscleGroup.raw, notes = e.entry.notes,
                    incrementStep = e.entry.step, sortIndex = e.sortIndex, createdAt = at, catalogID = e.entry.id,
                    limbs = e.limbs.name, barWeight = e.barWeight, plateLoaded = e.plateLoaded,
                ),
            )
        }
        db.programs().upsert(WorkoutProgramEntity(plan.programUuid, plan.programName, plan.programSortIndex, at))
        for (d in plan.days) {
            db.templates().upsert(WorkoutTemplateEntity(d.uuid, d.name, TemplateItems.encode(d.items), d.sortIndex, at, icon = d.icon, program = plan.programUuid))
        }
        plan
    }

    /** `Onboarding.undoPlan`: toglie quello che ha fatto [createPlan] (si torna indietro dalla schermata finale). */
    suspend fun undoPlan(plan: Onboarding.Plan) = db.withTransaction {
        dao.deleteTemplatesOf(plan.programUuid)
        dao.deleteProgram(plan.programUuid)
        dao.deleteExercises(plan.exercises.map { it.uuid })
    }

    /** `Programs.adoptLoose`: i giorni fuori da ogni scheda finiscono in «La mia scheda» (stesso uuid del web, una sola volta). */
    suspend fun adoptLoose(name: String) = db.withTransaction {
        if (dao.looseTemplates() == 0) return@withTransaction
        if (db.programs().get(Uuids.DEFAULT_PROGRAM) == null) db.programs().upsert(WorkoutProgramEntity(Uuids.DEFAULT_PROGRAM, name, createdAt = now()))
        dao.adoptTemplates(Uuids.DEFAULT_PROGRAM)
    }
}
