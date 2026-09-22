package com.example.mentzertracker

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.mentzertracker.data.*
import com.example.mentzertracker.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class SetInput(val weight: String = "", val reps: String = "") {
    val isEmpty: Boolean get() = weight.isBlank() && reps.isBlank()
    val isComplete: Boolean get() =
        parseWeight(weight)?.let { it >= 0f } == true && (reps.toIntOrNull() ?: 0) > 0
}

/** One-shot message for the snackbar, optionally with an action (e.g. Undo). */
data class UiEvent(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

/** A personal record derived from logged sets (weights in lbs). */
data class PrInfo(val exerciseId: Long, val weight: Float, val reps: Int, val date: Long) {
    val e1rm: Float get() = estimated1RM(weight, reps)
}

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>()
    private val db = AppDatabase.get(app)
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val prDao = db.prDao()
    private val templateDao = db.templateDao()
    private val prefs = app.getSharedPreferences("mentzer_prefs", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // Database-backed state
    // ------------------------------------------------------------------

    val exercises: StateFlow<List<Exercise>> = exerciseDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All logged sets, newest session first. */
    val history: StateFlow<List<SetWithExercise>> = workoutDao.getAllWithExercise()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * PRs computed live from history, so editing/deleting a set immediately fixes the PR.
     * (The old approach stored PRs separately and they went stale after deletions.)
     */
    val prs: StateFlow<Map<Long, PrInfo>> = history.map { computePrs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** exerciseId -> sets from the most recent session that included it. */
    val lastSessions: StateFlow<Map<Long, List<SetWithExercise>>> = history.map { computeLastSessions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val templates: StateFlow<List<WorkoutTemplate>> = templateDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** templateId -> ordered exerciseIds. Reactive, so edits show up everywhere instantly. */
    val templateExercises: StateFlow<Map<Long, List<Long>>> = templateDao.observeAllTemplateExercises()
        .map { rows -> rows.groupBy { it.templateId }.mapValues { (_, l) -> l.sortedBy { it.sortOrder }.map { it.exerciseId } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ------------------------------------------------------------------
    // Today-screen state (persisted so a crash / app kill mid-workout loses nothing)
    // ------------------------------------------------------------------

    private val _inputs = MutableStateFlow(loadDraft())
    val inputs: StateFlow<Map<Long, List<SetInput>>> = _inputs

    private val _heavyDutyMode = MutableStateFlow(prefs.getBoolean(PREF_HEAVY, false))
    val heavyDutyMode: StateFlow<Boolean> = _heavyDutyMode

    private val _selectedTemplateId = MutableStateFlow<Long?>(null)
    val selectedTemplateId: StateFlow<Long?> = _selectedTemplateId

    /** Ordered exercise ids of the selected template, or null for "All". */
    val templateExerciseIds: StateFlow<List<Long>?> =
        combine(_selectedTemplateId, templateExercises) { id, map -> id?.let { map[it].orEmpty() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _weightUnit = MutableStateFlow(
        runCatching { WeightUnit.valueOf(prefs.getString(PREF_UNIT, null) ?: "") }.getOrDefault(WeightUnit.LBS)
    )
    val weightUnit: StateFlow<WeightUnit> = _weightUnit

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(PREF_KEEP_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    // ------------------------------------------------------------------
    // Rest timer (lives here, not in the composable, so it survives tab switches
    // and scrolling; alerts even if you're on another tab)
    // ------------------------------------------------------------------

    private val _timerMinutes = MutableStateFlow(prefs.getInt(PREF_TIMER_MIN, 3))
    val timerMinutes: StateFlow<Int> = _timerMinutes

    /** SystemClock.elapsedRealtime() at which the timer ends, or null when idle. */
    private val _timerEndAt = MutableStateFlow<Long?>(null)
    val timerEndAt: StateFlow<Long?> = _timerEndAt
    private var timerJob: Job? = null

    // ------------------------------------------------------------------
    // Events: a Channel (not SharedFlow) so messages aren't lost when no screen
    // is collecting at the exact moment, and aren't shown twice.
    // ------------------------------------------------------------------

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private fun postEvent(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _events.trySend(UiEvent(message, actionLabel, action))
    }

    init {
        viewModelScope.launch {
            if (exerciseDao.count() == 0) seedDefaults()
            val saved = prefs.getLong(PREF_TEMPLATE, -1L)
            if (saved > 0 && templateDao.getAllTemplatesOnce().any { it.id == saved }) {
                _selectedTemplateId.value = saved
            } else {
                prefs.edit().remove(PREF_TEMPLATE).apply()
            }
        }
    }

    private suspend fun seedDefaults() {
        db.withTransaction {
            exerciseDao.insertAll(SeedData.exercises)
            val all = exerciseDao.getAllOnce()
            SeedData.templates.forEachIndexed { idx, (name, exIdx) ->
                val tId = templateDao.insertTemplate(WorkoutTemplate(name = name, sortOrder = idx))
                templateDao.insertTemplateExercises(
                    exIdx.mapIndexed { order, ei -> TemplateExercise(tId, all[ei].id, order) }
                )
            }
        }
    }

    // ==================================================================
    // Templates filter
    // ==================================================================

    /** Filters Today to a template. Does NOT wipe what you've already typed. */
    fun selectTemplate(templateId: Long) {
        _selectedTemplateId.value = templateId
        prefs.edit().putLong(PREF_TEMPLATE, templateId).apply()
    }

    fun clearTemplateFilter() {
        _selectedTemplateId.value = null
        prefs.edit().remove(PREF_TEMPLATE).apply()
    }

    fun toggleHeavyDuty() {
        val on = !_heavyDutyMode.value
        _heavyDutyMode.value = on
        prefs.edit().putBoolean(PREF_HEAVY, on).apply()
    }

    // ==================================================================
    // Set entry
    // ==================================================================

    private fun setInputs(new: Map<Long, List<SetInput>>) {
        _inputs.value = new
        saveDraft(new)
    }

    private fun currentSets(exerciseId: Long): List<SetInput> =
        _inputs.value[exerciseId] ?: List(DEFAULT_SETS) { SetInput() }

    fun updateSet(exerciseId: Long, setIndex: Int, weight: String? = null, reps: String? = null) {
        val list = currentSets(exerciseId).toMutableList()
        while (list.size <= setIndex) list.add(SetInput())
        val old = list[setIndex]
        val newWeight = weight?.let(::sanitizeWeightInput)
        val newReps = reps?.let(::sanitizeRepsInput)
        list[setIndex] = old.copy(weight = newWeight ?: old.weight, reps = newReps ?: old.reps)

        // Typing set 1's weight carries it to later sets that are blank OR still hold the
        // previously auto-filled value. (Old bug: typing "135" left the others stuck at "1".)
        if (newWeight != null && setIndex == 0) {
            for (i in 1 until list.size) {
                if (list[i].weight.isBlank() || list[i].weight == old.weight) {
                    list[i] = list[i].copy(weight = newWeight)
                }
            }
        }
        setInputs(_inputs.value + (exerciseId to list.toList()))
    }

    fun addSet(exerciseId: Long) {
        val list = currentSets(exerciseId)
        if (list.size >= MAX_SETS) return
        setInputs(_inputs.value + (exerciseId to list + SetInput(weight = list.lastOrNull()?.weight.orEmpty())))
    }

    fun removeSet(exerciseId: Long) {
        val list = currentSets(exerciseId)
        if (list.size <= 1) return
        setInputs(_inputs.value + (exerciseId to list.dropLast(1)))
    }

    /** Pre-fills an exercise with exactly what you did last time (in the current unit). */
    fun fillFromLast(exerciseId: Long) {
        val last = lastSessions.value[exerciseId] ?: return
        val unit = _weightUnit.value
        val sets = last.sortedBy { it.setNumber }
            .map { SetInput(formatWeight(lbsToDisplay(it.weight, unit)), it.reps.toString()) }
        if (sets.isNotEmpty()) setInputs(_inputs.value + (exerciseId to sets))
    }

    fun clearInputs() {
        val old = _inputs.value
        if (old.values.all { sets -> sets.all { it.isEmpty } }) return
        setInputs(emptyMap())
        postEvent("Cleared all entries", "Undo") { setInputs(old) }
    }

    // ==================================================================
    // Logging
    // ==================================================================

    private var logging = false

    fun logWorkout() {
        if (logging) return  // guards against a double-tap logging the session twice
        logging = true
        viewModelScope.launch {
            try {
                val heavy = _heavyDutyMode.value
                val unit = _weightUnit.value
                val sessionDate = System.currentTimeMillis()
                // Guards against a draft that references an exercise deleted since (FK crash).
                val exercisesById = exerciseDao.getAllOnce().associateBy { it.id }

                val toInsert = mutableListOf<WorkoutSet>()
                var incomplete = 0
                _inputs.value.forEach { (exId, sets) ->
                    if (exId !in exercisesById) return@forEach
                    var setNumber = 0
                    (if (heavy) sets.take(1) else sets).forEach { s ->
                        when {
                            s.isEmpty -> Unit
                            !s.isComplete -> incomplete++
                            else -> {
                                setNumber++
                                toInsert += WorkoutSet(
                                    exerciseId = exId, sessionDate = sessionDate, setNumber = setNumber,
                                    weight = displayToLbs(parseWeight(s.weight)!!, unit),
                                    reps = s.reps.toInt()
                                )
                            }
                        }
                    }
                }

                if (toInsert.isEmpty()) {
                    postEvent(if (incomplete > 0) "Each set needs both weight and reps" else "Enter at least one set")
                    return@launch
                }

                val touched = toInsert.map { it.exerciseId }.distinct()
                val previousBest = touched.associateWith { workoutDao.bestSetForExercise(it) }
                db.withTransaction { workoutDao.insertAll(toInsert) }

                val newPrs = touched.mapNotNull { exId ->
                    val prev = previousBest[exId] ?: return@mapNotNull null  // first time isn't a "PR"
                    val best = toInsert.filter { it.exerciseId == exId }
                        .maxWithOrNull(compareBy<WorkoutSet>({ it.weight }, { it.reps })) ?: return@mapNotNull null
                    val beat = best.weight > prev.weight || (best.weight == prev.weight && best.reps > prev.reps)
                    if (beat) exercisesById[exId]?.name else null
                }

                setInputs(emptyMap())
                postEvent(buildString {
                    append(if (heavy) "Heavy Duty session logged" else "Workout logged (${toInsert.size} sets)")
                    if (newPrs.isNotEmpty()) append(" \u00b7 New PR: ${newPrs.joinToString()}")
                    if (incomplete > 0) append(" \u00b7 skipped $incomplete incomplete")
                })
            } finally {
                logging = false
            }
        }
    }

    // ==================================================================
    // History editing (with Undo)
    // ==================================================================

    fun deleteSession(sessionDate: Long) {
        viewModelScope.launch {
            val sets = workoutDao.getSetsForSession(sessionDate)
            if (sets.isEmpty()) return@launch
            workoutDao.deleteSession(sessionDate)
            postEvent("Session deleted", "Undo") { restoreSets(sets) }
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            val set = workoutDao.getSetById(setId) ?: return@launch
            workoutDao.deleteSet(set)
            postEvent("Set deleted", "Undo") { restoreSets(listOf(set)) }
        }
    }

    private fun restoreSets(sets: List<WorkoutSet>) {
        viewModelScope.launch {
            try { workoutDao.insertAll(sets) } catch (e: Exception) { postEvent("Couldn't undo: ${e.message}") }
        }
    }

    fun updateLoggedSet(setId: Long, weightText: String, repsText: String) {
        viewModelScope.launch {
            val set = workoutDao.getSetById(setId) ?: return@launch
            val w = parseWeight(weightText)
            val r = repsText.toIntOrNull()
            if (w == null || w < 0f || r == null || r <= 0) { postEvent("Enter a valid weight and reps"); return@launch }
            workoutDao.updateSet(set.copy(weight = displayToLbs(w, _weightUnit.value), reps = r))
            postEvent("Set updated")
        }
    }

    // ==================================================================
    // Exercises
    // ==================================================================

    fun addExercise(name: String, cue: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) { postEvent("Exercise name can't be empty"); return@launch }
            if (exerciseDao.getAllOnce().any { it.name.equals(trimmed, ignoreCase = true) }) {
                postEvent("\"$trimmed\" already exists"); return@launch
            }
            val nextOrder = (exerciseDao.maxSortOrder() ?: -1) + 1
            exerciseDao.insertAll(listOf(Exercise(name = trimmed, cue = cue.trim(), sortOrder = nextOrder)))
            postEvent("Added \"$trimmed\"")
        }
    }

    fun updateExercise(id: Long, name: String, cue: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) { postEvent("Exercise name can't be empty"); return@launch }
            if (exerciseDao.getAllOnce().any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
                postEvent("\"$trimmed\" already exists"); return@launch
            }
            exerciseDao.update(id, trimmed, cue.trim())
            postEvent("Updated \"$trimmed\"")
        }
    }

    /** Moves an exercise up (-1) or down (+1) in the list. */
    fun moveExercise(id: Long, delta: Int) {
        viewModelScope.launch {
            val list = exerciseDao.getAllOnce().toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in list.indices) return@launch
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            db.withTransaction {
                list.forEachIndexed { idx, e -> if (e.sortOrder != idx) exerciseDao.updateSortOrder(e.id, idx) }
            }
        }
    }

    /** Deletes the exercise plus its sets and template slots (FK cascade). */
    fun deleteExercise(id: Long) {
        viewModelScope.launch {
            db.withTransaction {
                prDao.deleteForExercise(id)
                exerciseDao.deleteById(id)
            }
            if (id in _inputs.value) setInputs(_inputs.value - id)
            postEvent("Exercise deleted")
        }
    }

    // ==================================================================
    // Templates
    // ==================================================================

    fun addTemplate(name: String, exerciseIds: List<Long>) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) { postEvent("Template name can't be empty"); return@launch }
            if (exerciseIds.isEmpty()) { postEvent("Pick at least one exercise"); return@launch }
            if (templateDao.getAllTemplatesOnce().any { it.name.equals(trimmed, ignoreCase = true) }) {
                postEvent("A template called \"$trimmed\" already exists"); return@launch
            }
            db.withTransaction {
                val nextOrder = (templateDao.maxSortOrder() ?: -1) + 1
                val templateId = templateDao.insertTemplate(WorkoutTemplate(name = trimmed, sortOrder = nextOrder))
                templateDao.insertTemplateExercises(
                    exerciseIds.distinct().mapIndexed { idx, exId -> TemplateExercise(templateId, exId, idx) }
                )
            }
            postEvent("Created template \"$trimmed\"")
        }
    }

    fun updateTemplate(id: Long, name: String, exerciseIds: List<Long>) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) { postEvent("Template name can't be empty"); return@launch }
            if (exerciseIds.isEmpty()) { postEvent("Pick at least one exercise"); return@launch }
            if (templateDao.getAllTemplatesOnce().any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
                postEvent("A template called \"$trimmed\" already exists"); return@launch
            }
            // Transaction: an interruption can no longer leave a template with zero exercises.
            db.withTransaction {
                templateDao.renameTemplate(id, trimmed)
                templateDao.clearTemplateExercises(id)
                templateDao.insertTemplateExercises(
                    exerciseIds.distinct().mapIndexed { idx, exId -> TemplateExercise(id, exId, idx) }
                )
            }
            postEvent("Updated \"$trimmed\"")
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            templateDao.deleteTemplate(id)
            if (_selectedTemplateId.value == id) clearTemplateFilter()
            postEvent("Template deleted")
        }
    }

    // ==================================================================
    // Settings
    // ==================================================================

    fun setWeightUnit(unit: WeightUnit) {
        val oldUnit = _weightUnit.value
        if (oldUnit == unit) return
        setInputs(_inputs.value.mapValues { (_, sets) ->
            sets.map { s ->
                val w = parseWeight(s.weight)
                if (w == null) s else s.copy(weight = formatWeight(lbsToDisplay(displayToLbs(w, oldUnit), unit)))
            }
        })
        _weightUnit.value = unit
        prefs.edit().putString(PREF_UNIT, unit.name).apply()
    }

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        prefs.edit().putBoolean(PREF_KEEP_ON, on).apply()
    }

    // ==================================================================
    // Rest timer
    // ==================================================================

    fun setTimerMinutes(minutes: Int) {
        _timerMinutes.value = minutes
        prefs.edit().putInt(PREF_TIMER_MIN, minutes).apply()
    }

    fun startTimer() = scheduleTimerEnd(SystemClock.elapsedRealtime() + _timerMinutes.value * 60_000L)

    fun addTimerSeconds(seconds: Int) {
        val end = _timerEndAt.value ?: return
        scheduleTimerEnd(end + seconds * 1000L)
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerEndAt.value = null
    }

    private fun scheduleTimerEnd(endAt: Long) {
        timerJob?.cancel()
        _timerEndAt.value = endAt
        timerJob = viewModelScope.launch {
            delay((endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            _timerEndAt.value = null
            alertRestOver(ctx)
            postEvent("Rest is over \u2014 next set!")
        }
    }

    // ==================================================================
    // Export / backup / restore
    // ==================================================================

    /** Writes a CSV to cache and returns it for sharing, or null if there's nothing to export. */
    suspend fun exportCsvFile(): File? {
        val rows = workoutDao.getAllWithExerciseOnce()
        if (rows.isEmpty()) { postEvent("No workouts to export yet"); return null }
        val unit = _weightUnit.value
        return withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "mentzer_history_${formatDate(System.currentTimeMillis(), "yyyyMMdd")}.csv")
            file.bufferedWriter().use { w ->
                w.append("Date,Exercise,Set,Weight (${unit.label}),Reps,Est. 1RM (${unit.label}),Notes\n")
                rows.forEach { r ->
                    val weight = lbsToDisplay(r.weight, unit)
                    w.append(listOf(
                        formatDate(r.sessionDate, "yyyy-MM-dd HH:mm"),
                        r.exerciseName, r.setNumber.toString(), formatWeight(weight), r.reps.toString(),
                        formatWeight(estimated1RM(weight, r.reps)), r.notes
                    ).joinToString(",") { csvEscape(it) }).append('\n')
                }
            }
            file
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = BackupManager.exportJson(db)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Couldn't open the file for writing")
                }
                postEvent("Backup saved")
            } catch (e: Exception) {
                postEvent("Backup failed: ${e.message}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: throw IOException("Couldn't read that file")
                val s = BackupManager.importJson(db, text)
                setInputs(emptyMap())
                clearTemplateFilter()
                postEvent("Restored ${s.exercises} exercises, ${s.templates} templates, ${s.sessions} sessions")
            } catch (e: Exception) {
                postEvent("Restore failed: ${e.message}")
            }
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private fun saveDraft(map: Map<Long, List<SetInput>>) {
        val o = JSONObject()
        map.forEach { (id, sets) ->
            o.put(id.toString(), JSONArray().apply { sets.forEach { put(JSONArray().put(it.weight).put(it.reps)) } })
        }
        prefs.edit().putString(PREF_DRAFT, o.toString()).apply()
    }

    private fun loadDraft(): Map<Long, List<SetInput>> = try {
        val raw = prefs.getString(PREF_DRAFT, null)
        if (raw == null) emptyMap() else {
            val o = JSONObject(raw)
            buildMap {
                o.keys().forEach { k ->
                    val arr = o.getJSONArray(k)
                    put(k.toLong(), List(arr.length()) { i ->
                        val p = arr.getJSONArray(i)
                        SetInput(p.optString(0, ""), p.optString(1, ""))
                    })
                }
            }
        }
    } catch (e: Exception) { emptyMap() }

    companion object {
        const val DEFAULT_SETS = 3
        const val MAX_SETS = 10

        private const val PREF_UNIT = "weight_unit"
        private const val PREF_DRAFT = "draft_inputs"
        private const val PREF_HEAVY = "heavy_duty"
        private const val PREF_TEMPLATE = "selected_template"
        private const val PREF_KEEP_ON = "keep_screen_on"
        private const val PREF_TIMER_MIN = "timer_minutes"

        fun computePrs(sets: List<SetWithExercise>): Map<Long, PrInfo> =
            sets.groupBy { it.exerciseId }.mapNotNull { (exId, list) ->
                // Heaviest weight, then most reps, then the EARLIEST date it was achieved.
                list.maxWithOrNull(compareBy<SetWithExercise>({ it.weight }, { it.reps }, { -it.sessionDate }))
                    ?.let { exId to PrInfo(exId, it.weight, it.reps, it.sessionDate) }
            }.toMap()

        fun computeLastSessions(sets: List<SetWithExercise>): Map<Long, List<SetWithExercise>> =
            sets.groupBy { it.exerciseId }.mapValues { (_, list) ->
                val latest = list.maxOf { it.sessionDate }
                list.filter { it.sessionDate == latest }.sortedBy { it.setNumber }
            }

        private fun csvEscape(s: String): String =
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
