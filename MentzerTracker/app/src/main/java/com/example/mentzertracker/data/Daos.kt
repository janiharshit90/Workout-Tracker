package com.example.mentzertracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM exercises")
    suspend fun maxSortOrder(): Int?

    @Insert suspend fun insertAll(items: List<Exercise>)

    @Query("UPDATE exercises SET name = :name, cue = :cue WHERE id = :id")
    suspend fun update(id: Long, name: String, cue: String)

    @Query("UPDATE exercises SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    // template_exercises and workout_sets cascade-delete via FK.
    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}

@Dao
interface WorkoutDao {
    @Query("""
        SELECT s.id, s.exerciseId, e.name AS exerciseName, s.sessionDate,
               s.setNumber, s.weight, s.reps, s.notes
        FROM workout_sets s JOIN exercises e ON s.exerciseId = e.id
        ORDER BY s.sessionDate DESC, e.sortOrder ASC, s.setNumber ASC
    """)
    fun getAllWithExercise(): Flow<List<SetWithExercise>>

    @Query("""
        SELECT s.id, s.exerciseId, e.name AS exerciseName, s.sessionDate,
               s.setNumber, s.weight, s.reps, s.notes
        FROM workout_sets s JOIN exercises e ON s.exerciseId = e.id
        ORDER BY s.sessionDate ASC, e.sortOrder ASC, s.setNumber ASC
    """)
    suspend fun getAllWithExerciseOnce(): List<SetWithExercise>

    @Query("SELECT * FROM workout_sets ORDER BY sessionDate ASC, id ASC")
    suspend fun getAllOnce(): List<WorkoutSet>

    @Query("SELECT * FROM workout_sets WHERE sessionDate = :sessionDate")
    suspend fun getSetsForSession(sessionDate: Long): List<WorkoutSet>

    /** Heaviest set ever for an exercise; ties broken by more reps. */
    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY weight DESC, reps DESC LIMIT 1")
    suspend fun bestSetForExercise(exerciseId: Long): WorkoutSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<WorkoutSet>)

    @Query("DELETE FROM workout_sets WHERE sessionDate = :sessionDate")
    suspend fun deleteSession(sessionDate: Long)

    @Delete suspend fun deleteSet(set: WorkoutSet)
    @Update suspend fun updateSet(set: WorkoutSet)

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSetById(id: Long): WorkoutSet?

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAll()
}

/**
 * The personal_records table is kept only so the schema doesn't change (no migration
 * needed). PRs are now derived live from workout_sets in the ViewModel, so they stay
 * correct when you edit or delete logged sets.
 */
@Dao
interface PRDao {
    @Query("DELETE FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun deleteForExercise(exerciseId: Long)

    @Query("DELETE FROM personal_records")
    suspend fun deleteAll()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY sortOrder ASC")
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>

    @Query("SELECT * FROM workout_templates ORDER BY sortOrder ASC")
    suspend fun getAllTemplatesOnce(): List<WorkoutTemplate>

    @Query("SELECT * FROM template_exercises ORDER BY templateId ASC, sortOrder ASC")
    fun observeAllTemplateExercises(): Flow<List<TemplateExercise>>

    @Query("SELECT * FROM template_exercises ORDER BY templateId ASC, sortOrder ASC")
    suspend fun getAllTemplateExercisesOnce(): List<TemplateExercise>

    @Query("SELECT exerciseId FROM template_exercises WHERE templateId = :templateId ORDER BY sortOrder ASC")
    suspend fun getExerciseIdsForTemplate(templateId: Long): List<Long>

    @Query("SELECT MAX(sortOrder) FROM workout_templates")
    suspend fun maxSortOrder(): Int?

    @Insert suspend fun insertTemplate(t: WorkoutTemplate): Long
    @Insert suspend fun insertTemplates(items: List<WorkoutTemplate>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercises(items: List<TemplateExercise>)

    @Query("UPDATE workout_templates SET name = :name WHERE id = :id")
    suspend fun renameTemplate(id: Long, name: String)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun clearTemplateExercises(templateId: Long)

    // template_exercises cascade-deletes via FK.
    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM template_exercises")
    suspend fun deleteAllTemplateExercises()

    @Query("DELETE FROM workout_templates")
    suspend fun deleteAllTemplates()
}
