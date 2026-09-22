package com.example.mentzertracker.data

import androidx.room.*

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cue: String,
    val sortOrder: Int
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
    val sortOrder: Int
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
    val notes: String = ""
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
    val reps: Int, val notes: String
)

data class PRWithExercise(
    val id: Long, val exerciseId: Long, val exerciseName: String,
    val maxWeight: Float, val repsAtMaxWeight: Int,
    val dateAchieved: Long, val notes: String
)