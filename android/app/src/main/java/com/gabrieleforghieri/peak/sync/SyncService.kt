package com.gabrieleforghieri.peak.sync

import androidx.room.InvalidationTracker
import com.gabrieleforghieri.peak.data.PeakDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quando sincronizzare: dopo una modifica si aspetta un attimo (una raffica di salvataggi fa un solo giro), all'avvio e a richiesta.
 * Il posto di `ModelContext.didSave` di iPhone lo prende l'`InvalidationTracker` di Room. `scope` deve girare sul thread principale: è il
 * thread unico che [SyncEngine] pretende.
 */
class SyncService(
    val engine: SyncEngine,
    val connect: ConnectFlow,
    val backup: BackupService,
    private val db: PeakDatabase,
    private val scope: CoroutineScope,
) {
    private var debounce: Job? = null

    fun start() {
        engine.configure()
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(
            arrayOf("exercise", "max_record", "workout", "workout_set", "body_weight", "workout_template", "workout_program", "setting"),
        ) {
            override fun onInvalidated(tables: Set<String>) {
                scope.launch { if (!engine.noteSaveDuringRound()) requestSync() }
            }
        })
        requestSync()
    }

    fun requestSync() {
        if (!engine.isConnected || PeakDatabase.isFallback) return
        debounce?.cancel()
        debounce = scope.launch {
            delay(2_000)
            syncNow()
        }
    }

    /** Un giro adesso (anche a mano). Se durante il giro c'è stato un salvataggio, ne parte un altro. */
    suspend fun syncNow() {
        engine.syncNow()
        if (engine.takeNeedsAnotherRound()) requestSync()
    }
}
