package com.example.mentzertracker.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Full-fidelity JSON backup/restore. Use this before uninstalling, switching phones,
 * or moving to a differently-signed build.
 */
object BackupManager {
    private const val APP_TAG = "MentzerTracker"
    private const val FORMAT = 1

    data class Summary(val exercises: Int, val templates: Int, val sets: Int, val sessions: Int)

    class BackupException(message: String) : Exception(message)

    suspend fun exportJson(db: AppDatabase): String {
        val exercises = db.exerciseDao().getAllOnce()
        val templates = db.templateDao().getAllTemplatesOnce()
        val links = db.templateDao().getAllTemplateExercisesOnce()
        val sets = db.workoutDao().getAllOnce()

        return JSONObject()
            .put("app", APP_TAG)
            .put("format", FORMAT)
            .put("exportedAt", System.currentTimeMillis())
            .put("weightsStoredIn", "lbs")
            .put("exercises", JSONArray().apply {
                exercises.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name)
                        .put("cue", it.cue).put("sortOrder", it.sortOrder))
                }
            })
            .put("templates", JSONArray().apply {
                templates.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder))
                }
            })
            .put("templateExercises", JSONArray().apply {
                links.forEach {
                    put(JSONObject().put("templateId", it.templateId)
                        .put("exerciseId", it.exerciseId).put("sortOrder", it.sortOrder))
                }
            })
            .put("sets", JSONArray().apply {
                sets.forEach {
                    put(JSONObject().put("id", it.id).put("exerciseId", it.exerciseId)
                        .put("sessionDate", it.sessionDate).put("setNumber", it.setNumber)
                        .put("weight", it.weight.toDouble()).put("reps", it.reps)
                        .put("notes", it.notes))
                }
            })
            .toString(2)
    }

    /** Parses everything first; only wipes + replaces data if the whole file is valid. */
    suspend fun importJson(db: AppDatabase, text: String): Summary {
        val root = try { JSONObject(text) } catch (e: JSONException) {
            throw BackupException("That file isn't a valid backup.")
        }
        if (root.optString("app") != APP_TAG) throw BackupException("That file isn't a Mentzer Tracker backup.")
        if (root.optInt("format", 0) > FORMAT) throw BackupException("This backup is from a newer app version. Update the app first.")

        try {
            val exercises = root.getJSONArray("exercises").mapObjects { o, i ->
                Exercise(id = o.getLong("id"), name = o.getString("name"),
                    cue = o.optString("cue", ""), sortOrder = o.optInt("sortOrder", i))
            }
            if (exercises.isEmpty()) throw BackupException("Backup contains no exercises.")
            val exIds = exercises.map { it.id }.toSet()

            val templates = root.optJSONArray("templates").mapObjects { o, i ->
                WorkoutTemplate(id = o.getLong("id"), name = o.getString("name"),
                    sortOrder = o.optInt("sortOrder", i))
            }
            val tIds = templates.map { it.id }.toSet()

            val links = root.optJSONArray("templateExercises").mapObjects { o, _ ->
                TemplateExercise(o.getLong("templateId"), o.getLong("exerciseId"), o.optInt("sortOrder", 0))
            }.filter { it.templateId in tIds && it.exerciseId in exIds }
                .distinctBy { it.templateId to it.exerciseId }

            val sets = root.optJSONArray("sets").mapObjects { o, _ ->
                WorkoutSet(id = o.getLong("id"), exerciseId = o.getLong("exerciseId"),
                    sessionDate = o.getLong("sessionDate"), setNumber = o.getInt("setNumber"),
                    weight = o.getDouble("weight").toFloat(), reps = o.getInt("reps"),
                    notes = o.optString("notes", ""))
            }.filter { it.exerciseId in exIds }

            db.withTransaction {
                db.workoutDao().deleteAll()
                db.templateDao().deleteAllTemplateExercises()
                db.templateDao().deleteAllTemplates()
                db.prDao().deleteAll()
                db.exerciseDao().deleteAll()

                db.exerciseDao().insertAll(exercises)
                if (templates.isNotEmpty()) db.templateDao().insertTemplates(templates)
                if (links.isNotEmpty()) db.templateDao().insertTemplateExercises(links)
                if (sets.isNotEmpty()) db.workoutDao().insertAll(sets)
            }
            return Summary(exercises.size, templates.size, sets.size, sets.map { it.sessionDate }.distinct().size)
        } catch (e: JSONException) {
            throw BackupException("Backup file is damaged: ${e.message}")
        }
    }

    private inline fun <T> JSONArray?.mapObjects(crossinline transform: (JSONObject, Int) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { i -> transform(getJSONObject(i), i) }
    }
}
