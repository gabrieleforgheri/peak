package com.gabrieleforghieri.peak.data

import androidx.room.withTransaction
import com.gabrieleforghieri.peak.domain.RecordService
import com.gabrieleforghieri.peak.sync.Changes
import com.gabrieleforghieri.peak.sync.Snapshot
import com.gabrieleforghieri.peak.sync.SyncStorage

/** Il database vero dietro la sincronizzazione e i backup. */
class RoomSyncStorage(private val db: PeakDatabase, private val records: RecordService) : SyncStorage {

    override val isFallback: Boolean get() = PeakDatabase.isFallback

    override suspend fun snapshot(): Snapshot = db.withTransaction {
        Snapshot(
            exercises = db.exercises().all(), records = db.records().all(), workouts = db.workouts().all(), sets = db.sets().all(),
            bodyWeights = db.bodyWeights().all(), templates = db.templates().all(), programs = db.programs().all(),
            settings = db.settings().all().associate { it.key to it.value },
        )
    }

    override suspend fun commit(changes: Changes) = db.withTransaction {
        if (changes.wipe) {
            db.sets().deleteAll(); db.workouts().deleteAll(); db.records().deleteAll(); db.exercises().deleteAll()
            db.bodyWeights().deleteAll(); db.templates().deleteAll(); db.programs().deleteAll()
        }
        val gone = changes.gone
        gone.sets.forEach { db.sets().delete(it) }
        gone.records.forEach { db.records().delete(it) }
        gone.workouts.forEach { db.workouts().delete(it) }
        gone.exercises.forEach { db.exercises().delete(it) }
        gone.templates.forEach { db.templates().delete(it) }
        gone.programs.forEach { db.programs().delete(it) }
        gone.bodyWeights.forEach { db.bodyWeights().delete(it) }
        // prima i genitori (macchinari, allenamenti), poi ciò che li richiama
        changes.exercises.forEach { db.exercises().upsert(it) }
        changes.programs.forEach { db.programs().upsert(it) }
        changes.templates.forEach { db.templates().upsert(it) }
        changes.workouts.forEach { db.workouts().upsert(it) }
        changes.records.forEach { db.records().upsert(it) }
        changes.sets.forEach { db.sets().upsert(it) }
        changes.bodyWeights.forEach { db.bodyWeights().upsert(it) }
        changes.settings.forEach { (key, value) -> db.settings().put(SettingEntity(key, value)) }
    }

    override suspend fun recomputeRecords() = records.recomputeAll()
}
