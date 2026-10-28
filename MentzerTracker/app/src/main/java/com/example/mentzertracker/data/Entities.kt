package com.example.mentzertracker.data

import androidx.room.*

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cue: String,
    val sortOrder: Int,
    // Added in v6 (see MIGRATION_5_6). Weight jump for this exercise, in lbs (the storage unit);
    // 0 = use the default (5 lb / 2.5 kg).
    val stepLbs: Float = 0f
)

@Entity(tableName = "workout_templates")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "template_exercises",
    primaryKeys = ["templateId", "exerciseId"],
    foreignKeys = [
        ForeignKey(entity = WorkoutTemplate::class, parentColumns = ["id"],
            childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"],
            childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class TemplateExercise(
    val templateId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    // Added in v7 (see MIGRATION_6_7). Done back-to-back with the NEXT exercise in the template,
    // with no rest in between (Heavy Duty pre-exhaust: isolation move straight into a compound).
    val supersetNext: Boolean = false
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = Exercise::class, parentColumns = ["id"],
        childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId"), Index("sessionDate")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val sessionDate: Long,
    val setNumber: Int,
    val weight: Float,
    val reps: Int,
    val notes: String = "",
    // Added in v4 (see MIGRATION_3_4). Defaults keep existing rows valid.
    val toFailure: Boolean = false,
    val forcedReps: Int = 0,
    // Added in v5 (see MIGRATION_4_5). Whole-session length in seconds, stored on every set
    // of that session; 0 = unknown (older sessions, or the timer wasn't trustworthy).
    val durationSec: Int = 0,
    // Added in v6 (see MIGRATION_5_6). Warm-up sets are logged but never count toward PRs,
    // "last time" or the progression target.
    val isWarmup: Boolean = false,
    // Added in v7 (see MIGRATION_6_7). "Beyond failure" work done after the set hit failure.
    // Like forced reps, none of these count toward reps, PRs or progression.
    /** Extra lowering-only reps after failure (a partner lifts, you lower slowly). */
    val negatives: Int = 0,
    /** Extra reps squeezed out after a short (10-20 s) pause at failure. */
    val restPauseReps: Int = 0,
    /** Seconds held in the strongest position at failure (static hold). */
    val holdSec: Int = 0,
    /** Which template this session was started from, stored on every set; 0 = custom / unknown. */
    val templateId: Long = 0
)

/** A body-weight reading. Stored in lbs like every other weight. */
@Entity(tableName = "body_weights", indices = [Index("date")])
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val weightLbs: Float
)

@Entity(tableName = "personal_records",
    indices = [Index(value = ["exerciseId"], unique = true)])
data class PersonalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val maxWeight: Float,
    val repsAtMaxWeight: Int,
    val dateAchieved: Long,
    val notes: String = ""
)

data class SetWithExercise(
    val id: Long, val exerciseId: Long, val exerciseName: String,
    val sessionDate: Long, val setNumber: Int, val weight: Float,
    val reps: Int, val notes: String,
    val toFailure: Boolean = false, val forcedReps: Int = 0,
    val durationSec: Int = 0,
    val isWarmup: Boolean = false,
    val negatives: Int = 0,
    val restPauseReps: Int = 0,
    val holdSec: Int = 0,
    val templateId: Long = 0
)

data class PRWithExercise(
    val id: Long, val exerciseId: Long, val exerciseName: String,
    val maxWeight: Float, val repsAtMaxWeight: Int,
    val dateAchieved: Long, val notes: String
)