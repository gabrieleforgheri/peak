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

        /** Il database vero non si è aperto e si lavora su uno in memoria: la sincronizzazione e i backup restano spenti, per non spingere un «tutto cancellato». */
        @Volatile var isFallback = false
            private set

        fun get(context: Context): PeakDatabase = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext).also { instance = it }
        }

        private fun open(context: Context): PeakDatabase {
            val real = Room.databaseBuilder(context, PeakDatabase::class.java, "peak.db").build()
            return try {
                real.openHelper.writableDatabase // si apre (e si migra) adesso, non alla prima lettura
                real
            } catch (e: Exception) {
                android.util.Log.e("Peak", "Il database non si apre: ripiego in memoria", e)
                isFallback = true
                Room.inMemoryDatabaseBuilder(context, PeakDatabase::class.java).build()
            }
        }
    }
}
