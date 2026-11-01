package com.gabrieleforghieri.peak.ui.machines

import com.gabrieleforghieri.peak.data.ExerciseEntity
import com.gabrieleforghieri.peak.data.MaxRecordEntity
import com.gabrieleforghieri.peak.data.WorkoutEntity
import com.gabrieleforghieri.peak.data.WorkoutSetEntity

/** I dati d'esempio per gli screenshot dell'area: date relative a `now`, così «Oggi» e «Ieri» tornano sempre. */
class MachinesSamples(val now: Long = System.currentTimeMillis()) {
    private val day = 86_400_000L

    val legExtension = ExerciseEntity("E1", name = "Leg Extension", muscleGroup = "gambe", catalogID = "leg-extension", notes = "Sedile al foro 4, schienale dritto", incrementStep = 2.5, sortIndex = 0)
    val benchPress = ExerciseEntity("E2", name = "Panca piana", muscleGroup = "petto", catalogID = "bench-press-barbell", barWeight = 20.0, weightDisplay = "relative", incrementStep = 2.5, sortIndex = 1)
    val dumbbellCurl = ExerciseEntity("E3", name = "Curl con manubri", muscleGroup = "braccia", catalogID = "dumbbell-curl", incrementStep = 1.0, sortIndex = 2)
    val customPress = ExerciseEntity("E4", name = "Pressa mia", muscleGroup = "gambe", glyph = "legPress", isCustom = true, incrementStep = 5.0, sortIndex = 3)
    val unlinked = ExerciseEntity("E5", name = "Cavi vecchi", muscleGroup = "schiena", incrementStep = 2.5, sortIndex = 4)
    val latMachine = ExerciseEntity("E6", name = "Lat Machine", muscleGroup = "schiena", catalogID = "lat-pulldown", incrementStep = 5.0, sortIndex = 5)
    val all = listOf(legExtension, benchPress, dumbbellCurl, customPress, unlinked, latMachine)

    private fun r(id: String, exercise: String, daysAgo: Int, weight: Double, reps: Int, arm: Boolean = false, estimated: Boolean = false) =
        MaxRecordEntity(id, exercise, now - daysAgo * day, weight, reps, estimated, singleArm = arm)

    val legRecords = listOf(
        r("l1", "E1", 150, 55.0, 8), r("l2", "E1", 120, 60.0, 8), r("l3", "E1", 95, 65.0, 6, estimated = true), r("l4", "E1", 70, 70.0, 6),
        r("l5", "E1", 40, 77.5, 5), r("l6", "E1", 14, 82.5, 5, estimated = true), r("l7", "E1", 1, 87.5, 4),
        r("l8", "E1", 30, 40.0, 8, arm = true), r("l9", "E1", 9, 45.0, 6, arm = true),
    )
    val benchRecords = listOf(r("b1", "E2", 60, 70.0, 5), r("b2", "E2", 20, 80.0, 3), r("b3", "E2", 0, 85.0, 3))
    val curlRecords = listOf(r("c1", "E3", 3, 14.0, 8))
    val latRecords = listOf(r("t1", "E6", 2, 62.5, 10), r("t2", "E6", 25, 60.0, 10))
    val records = legRecords + benchRecords + curlRecords + latRecords

    val workouts = mapOf(
        "W1" to WorkoutEntity("W1", date = now - 7 * day, endedAt = now - 7 * day + 3_600_000, name = "Gambe"),
        "W2" to WorkoutEntity("W2", date = now - day, endedAt = now - day + 3_000_000, name = "Gambe"),
    )
    val legSets = listOf(
        WorkoutSetEntity("s1", "W1", "E1", 40.0, 10, isWarmup = true, createdAt = now - 7 * day),
        WorkoutSetEntity("s2", "W1", "E1", 80.0, 8, createdAt = now - 7 * day + 60_000),
        WorkoutSetEntity("s3", "W2", "E1", 42.5, 10, isWarmup = true, createdAt = now - day),
        WorkoutSetEntity("s4", "W2", "E1", 87.5, 4, isPersonalRecord = true, createdAt = now - day + 60_000),
        WorkoutSetEntity("s5", "W2", "E1", 45.0, 6, singleArm = true, createdAt = now - day + 120_000),
    )
    val benchSets = listOf(
        WorkoutSetEntity("s6", "W1", "E2", 60.0, 8, createdAt = now - 7 * day),
        WorkoutSetEntity("s7", "W2", "E2", 85.0, 3, isPersonalRecord = true, createdAt = now - day),
    )
}
