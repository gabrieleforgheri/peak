package com.gabrieleforghieri.peak.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class, MaxRecordEntity::class, WorkoutEntity::class, WorkoutSetEntity::class,
        BodyWeightEntity::class, WorkoutTemplateEntity::class, WorkoutProgramEntity::class, SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PeakDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun records(): MaxRecordDao
    abstract fun workouts(): WorkoutDao
    abstract fun sets(): WorkoutSetDao
    abstract fun bodyWeights(): BodyWeightDao
    abstract fun templates(): TemplateDao
    abstract fun programs(): ProgramDao
    abstract fun settings(): SettingDao

    companion object {
        @Volatile private var instance: PeakDatabase? = null

        fun get(context: Context): PeakDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PeakDatabase::class.java, "peak.db").build().also { instance = it }
        }
    }
}
