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

/** v4 -> v5: workout_sets.durationSec (whole-session length, 0 = unknown). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN durationSec INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5 -> v6: exercises.stepLbs (per-exercise weight jump) and workout_sets.isWarmup. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN stepLbs REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN isWarmup INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v6 -> v7: beyond-failure techniques on sets, the template a session came from, superset links in
 * templates, and a body-weight log. Column additions only (safe defaults) plus one new table, whose
 * SQL must match what Room generates for [BodyWeight] exactly.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN negatives INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN restPauseReps INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN holdSec INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN templateId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE template_exercises ADD COLUMN supersetNext INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_weights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` INTEGER NOT NULL, `weightLbs` REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_weights_date` ON `body_weights` (`date`)")
    }
}

@Database(
    entities = [Exercise::class, WorkoutTemplate::class, TemplateExercise::class,
                WorkoutSet::class, PersonalRecord::class, BodyWeight::class],
    version = 7, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun prDao(): PRDao
    abstract fun templateDao(): TemplateDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "mentzer.db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { INSTANCE = it }
            }
    }
}
