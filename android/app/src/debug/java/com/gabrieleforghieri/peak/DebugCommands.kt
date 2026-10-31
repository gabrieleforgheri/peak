package com.gabrieleforghieri.peak

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gabrieleforghieri.peak.data.BodyWeightEntity
import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.domain.PeakDates
import com.gabrieleforghieri.peak.sync.PeakCredentials
import com.gabrieleforghieri.peak.sync.RestoreMode
import java.io.File
import kotlinx.coroutines.launch

/**
 * Solo nei build di debug: comandi da adb per provare la sincronizzazione contro un server locale senza passare dal login vero
 * (che non si può automatizzare). Si usano da `android/scripts/sync-e2e.sh`:
 *
 *   adb shell am broadcast -n com.gabrieleforghieri.peak/.DebugCommands --es cmd connect --es token T --es sub S
 *
 * Comandi: disconnect, connect (token, sub, nome), choice (which = upload|empty|server|merge), sync, seed, rename (uuid, nome), delete-exercise (uuid),
 * restore (file, mode = merge|replace), export (file), dump. Il risultato va nel log con il tag PeakDebug.
 */
class DebugCommands : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PeakApplication
        val pending = goAsync()
        app.mainScope.launch {
            try {
                run(app, intent)
            } catch (e: Exception) {
                Log.e(TAG, "ERRORE ${intent.getStringExtra("cmd")}: ${e::class.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun run(app: PeakApplication, i: Intent) {
        val sync = app.sync
        when (val cmd = i.getStringExtra("cmd")) {
            "connect" -> {
                sync.connect.connectWith(PeakCredentials(i.getStringExtra("token")!!, i.getStringExtra("sub")!!, i.getStringExtra("nome") ?: "Prova"))
                Log.i(TAG, "OK connect stage=${sync.connect.stage.value}")
            }
            "choice" -> {
                val c = sync.connect
                when (val which = i.getStringExtra("which")) {
                    "upload" -> c.uploadLocalData()
                    "empty" -> c.startEmpty()
                    "server" -> c.useServerData()
                    "merge" -> c.merge()
                    else -> error("scelta sconosciuta: $which")
                }
                Log.i(TAG, "OK choice stage=${c.stage.value}")
            }
            "sync" -> { sync.syncNow(); sync.engine.status.value.let { Log.i(TAG, "OK sync state=${it.state} error=${it.lastError} pending=${it.pendingCount}") } }
            "disconnect" -> { sync.engine.disconnect(); Log.i(TAG, "OK disconnect") }
            "seed" -> seed(app)
            "rename" -> {
                val db = app.database
                val e = db.exercises().get(i.getStringExtra("uuid")!!)!!
                db.exercises().upsert(e.copy(name = i.getStringExtra("nome")!!))
                Log.i(TAG, "OK rename")
            }
            "delete-exercise" -> { app.database.exercises().delete(i.getStringExtra("uuid")!!); Log.i(TAG, "OK delete-exercise") }
            "restore" -> {
                val file = app.backup.restore(File(app.filesDir, i.getStringExtra("file")!!).readBytes(), if (i.getStringExtra("mode") == "replace") RestoreMode.Replace else RestoreMode.Merge)
                Log.i(TAG, "OK restore ${file.exercises.size} macchinari")
            }
            "export" -> { File(app.filesDir, i.getStringExtra("file")!!).writeBytes(app.backup.data()); Log.i(TAG, "OK export") }
            "dump" -> dump(app)
            else -> Log.e(TAG, "comando sconosciuto: $cmd")
        }
    }

    /** Un macchinario, un allenamento chiuso con tre serie e una pesata, con uuid fissi: i conflitti si preparano a colpo sicuro. */
    private suspend fun seed(app: PeakApplication) {
        val db = app.database
        val now = System.currentTimeMillis()
        db.exercises().upsert(
            ExerciseEntity(uuid = EXERCISE, name = "Leg Press", muscleGroup = "gambe", incrementStep = 10.0, createdAt = PeakDates.floorSec(now - 86_400_000L * 30)),
        )
        db.workouts().upsert(WorkoutEntity(uuid = WORKOUT, date = PeakDates.floorSec(now - 3_600_000), endedAt = PeakDates.floorSec(now - 600_000), note = "Gambe pesanti"))
        app.records.addSet(WORKOUT, EXERCISE, 160.0, 8, isWarmup = false, now = now - 3_000_000, uuid = SET_1)
        app.records.addSet(WORKOUT, EXERCISE, 180.0, 5, isWarmup = false, now = now - 2_000_000, uuid = SET_2)
        db.bodyWeights().upsert(BodyWeightEntity(uuid = WEIGHT, date = PeakDates.floorSec(now - 1_000_000), weight = 78.4))
        Log.i(TAG, "OK seed")
    }

    private suspend fun dump(app: PeakApplication) {
        val s = app.syncStorage.snapshot()
        val st = app.sync.engine.status.value
        val names = s.exercises.joinToString("|") { it.name }
        Log.i(
            TAG,
            "DUMP exercises=${s.exercises.size} names=[$names] records=${s.records.size} estimated=${s.records.count { it.isEstimated }} " +
                "workouts=${s.workouts.size} sets=${s.sets.size} weights=${s.bodyWeights.size} state=${st.state} pending=${st.pendingCount} " +
                "error=${st.lastError} stage=${app.sync.connect.stage.value}",
        )
    }

    companion object {
        const val TAG = "PeakDebug"
        const val EXERCISE = "0000AAAA-0000-4000-8000-000000000001"
        const val WORKOUT = "0000AAAA-0000-4000-8000-000000000002"
        const val SET_1 = "0000AAAA-0000-4000-8000-000000000003"
        const val SET_2 = "0000AAAA-0000-4000-8000-000000000004"
        const val WEIGHT = "0000AAAA-0000-4000-8000-000000000005"
    }
}
