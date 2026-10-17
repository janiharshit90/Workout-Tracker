package com.example.mentzertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * IMPORTANT: `fallbackToDestructiveMigration()` has been removed on purpose.
 * With it, any future schema change without a written Migration would silently
 * WIPE all your logged history on update. Now, if you ever change an @Entity:
 *   1. bump `version` below,
 *   2. add a Migration(old, new) and pass it to .addMigrations(...).
 * Forgetting step 2 crashes on launch instead of deleting data - much safer.
 */
/** v3 -> v4: workout_sets.toFailure and workout_sets.forcedReps for HIT-style logging. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN toFailure INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN forcedReps INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Exercise::class, WorkoutTemplate::class, TemplateExercise::class,
                WorkoutSet::class, PersonalRecord::class],
    version = 4, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun prDao(): PRDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "mentzer.db"
                ).addMigrations(MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
