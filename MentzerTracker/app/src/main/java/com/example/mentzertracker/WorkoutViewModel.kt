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

data class SetInput(
    val weight: String = "",
    val reps: String = "",
    /** Heavy Duty logging: did this set go to true momentary muscular failure? */
    val toFailure: Boolean = false,
    /** Forced reps taken past failure with a spotter's help. Only meaningful when toFailure. */
    val forcedReps: Int = 0
) {
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

/** A workout in progress: its display name and the exercises in it, in order. */
data class ActiveWorkout(val name: String, val exerciseIds: List<Long>)

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

    /**
     * The workout in progress (guided flow: pick a template, then go exercise by exercise).
     * Null = nothing in progress, so Today shows the start screen. Persisted, so an app
     * kill mid-workout resumes exactly where you were.
     */
    private val _activeWorkout = MutableStateFlow(loadActiveWorkout())
    val activeWorkout: StateFlow<ActiveWorkout?> = _activeWorkout

    private val _introDismissed = MutableStateFlow(prefs.getBoolean(PREF_INTRO_DISMISSED, false))
    val introDismissed: StateFlow<Boolean> = _introDismissed

    private val _weightUnit = MutableStateFlow(
        runCatching { WeightUnit.valueOf(prefs.getString(PREF_UNIT, null) ?: "") }.getOrDefault(WeightUnit.LBS)
    )
    val weightUnit: StateFlow<WeightUnit> = _weightUnit

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(PREF_KEEP_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    /** Whether Today shows the per-set "to failure" toggle and forced-reps stepper. */
    private val _trackFailure = MutableStateFlow(prefs.getBoolean(PREF_TRACK_FAILURE, true))
    val trackFailure: StateFlow<Boolean> = _trackFailure

    /**
     * Days of full recovery Mentzer's HIT philosophy calls for before training the same
     * exercise again. Purely informational (a badge on Today) - never blocks logging,
     * since only you know how you actually feel.
     */
    private val _recoveryDays = MutableStateFlow(prefs.getInt(PREF_RECOVERY_DAYS, DEFAULT_RECOVERY_DAYS))
    val recoveryDays: StateFlow<Int> = _recoveryDays

    /** Rep range that drives progression suggestions (double progression). */
    private val _repRange = MutableStateFlow(loadRepRange())
    val repRange: StateFlow<IntRange> = _repRange

    /** exerciseId -> what to aim for next time, derived from the last session of each exercise. */
    val suggestions: StateFlow<Map<Long, Suggestion>> =
        combine(lastSessions, _weightUnit, _repRange) { last, unit, range ->
            last.mapNotNull { (id, sets) -> suggestNext(sets, unit, range)?.let { id to it } }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Wall-clock time the current workout started, or null when nothing is in progress.
     * Starts on the first weight/reps/failure entry, persists across app kills (with the draft),
     * and is cleared when the workout is logged or the entries are cleared.
     */
    private val _sessionStartedAt = MutableStateFlow(
        prefs.getLong(PREF_SESSION_START, 0L).takeIf { it > 0L }
    )
    val sessionStartedAt: StateFlow<Long?> = _sessionStartedAt

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
            prefs.edit().remove("selected_template").apply()  // old Today template filter, now unused
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
    // Guided workout
    // ==================================================================

    /** Starts a workout from a template, or an empty "custom" one when [templateId] is null. */
    fun startWorkout(templateId: Long?) {
        viewModelScope.launch {
            val existing = exerciseDao.getAllOnce().map { it.id }.toSet()
            val name: String
            val ids: List<Long>
            if (templateId == null) {
                name = "Custom workout"; ids = emptyList()
            } else {
                val t = templateDao.getAllTemplatesOnce().firstOrNull { it.id == templateId } ?: return@launch
                name = t.name
                ids = templateDao.getExerciseIdsForTemplate(templateId).filter { it in existing }
                if (ids.isEmpty()) {
                    postEvent("\"$name\" has no exercises. Edit it in Settings \u2192 Manage templates.")
                    return@launch
                }
            }
            setInputs(emptyMap())
            setSessionStart(System.currentTimeMillis())
            setActiveWorkout(ActiveWorkout(name, ids))
        }
    }

    fun addToWorkout(exerciseId: Long) {
        val w = _activeWorkout.value ?: return
        if (exerciseId !in w.exerciseIds) setActiveWorkout(w.copy(exerciseIds = w.exerciseIds + exerciseId))
    }

    fun removeFromWorkout(exerciseId: Long) {
        val w = _activeWorkout.value ?: return
        if (exerciseId in _inputs.value) setInputs(_inputs.value - exerciseId)
        setActiveWorkout(w.copy(exerciseIds = w.exerciseIds - exerciseId))
    }

    /** Throws away the workout in progress (the UI confirms first). */
    fun cancelWorkout() {
        setInputs(emptyMap())
        setSessionStart(null)
        setActiveWorkout(null)
    }

    fun dismissIntro() {
        _introDismissed.value = true
        prefs.edit().putBoolean(PREF_INTRO_DISMISSED, true).apply()
    }

    private fun setActiveWorkout(w: ActiveWorkout?) {
        _activeWorkout.value = w
        prefs.edit().apply {
            if (w == null) remove(PREF_ACTIVE) else putString(PREF_ACTIVE,
                JSONObject().put("name", w.name).put("ids", JSONArray(w.exerciseIds)).toString())
        }.apply()
    }

    private fun loadActiveWorkout(): ActiveWorkout? {
        try {
            prefs.getString(PREF_ACTIVE, null)?.let { raw ->
                val o = JSONObject(raw)
                val arr = o.getJSONArray("ids")
                return ActiveWorkout(o.optString("name", "Workout"), List(arr.length()) { arr.getLong(it) })
            }
        } catch (e: Exception) { /* fall through to draft adoption */ }
        // A draft saved before guided workouts existed: adopt it as a workout in progress.
        val withInput = _inputs.value.filter { (_, sets) -> sets.any { !it.isEmpty } }.keys.sorted()
        return if (withInput.isEmpty()) null else ActiveWorkout("Workout", withInput)
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
        if (!newWeight.isNullOrEmpty() || !newReps.isNullOrEmpty()) touchSession()

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

    /** Marks/unmarks a set as taken to true muscular failure. Clears forced reps when unmarked. */
    fun toggleFailure(exerciseId: Long, setIndex: Int) {
        val list = currentSets(exerciseId).toMutableList()
        if (setIndex !in list.indices) return
        val cur = list[setIndex]
        list[setIndex] = cur.copy(toFailure = !cur.toFailure, forcedReps = if (cur.toFailure) 0 else cur.forcedReps)
        touchSession()
        setInputs(_inputs.value + (exerciseId to list.toList()))
    }

    /** +/- stepper for forced reps; only meaningful once a set is marked to failure. */
    fun adjustForcedReps(exerciseId: Long, setIndex: Int, delta: Int) {
        val list = currentSets(exerciseId).toMutableList()
        if (setIndex !in list.indices) return
        val cur = list[setIndex]
        if (!cur.toFailure) return
        list[setIndex] = cur.copy(forcedReps = (cur.forcedReps + delta).coerceIn(0, MAX_FORCED_REPS))
        touchSession()
        setInputs(_inputs.value + (exerciseId to list.toList()))
    }

    /** Pre-fills an exercise with exactly what you did last time (in the current unit). */
    fun fillFromLast(exerciseId: Long) {
        val last = lastSessions.value[exerciseId] ?: return
        val unit = _weightUnit.value
        val sets = last.sortedBy { it.setNumber }
            .map { SetInput(formatWeight(lbsToDisplay(it.weight, unit)), it.reps.toString()) }
        if (sets.isNotEmpty()) setInputs(_inputs.value + (exerciseId to sets))
    }

    /**
     * Puts the suggested weight into every set row of this exercise. Reps are left blank on
     * purpose - they're what you go and earn. Does not start the session clock.
     */
    fun applySuggestion(exerciseId: Long) {
        val sug = suggestions.value[exerciseId] ?: return
        val shown = formatWeight(lbsToDisplay(sug.weightLbs, _weightUnit.value))
        setInputs(_inputs.value + (exerciseId to currentSets(exerciseId).map { it.copy(weight = shown) }))
    }

    fun clearInputs() {
        val old = _inputs.value
        if (old.values.all { sets -> sets.all { it.isEmpty } }) return
        val oldStart = _sessionStartedAt.value
        setInputs(emptyMap())
        setSessionStart(null)
        postEvent("Cleared all entries", "Undo") { setInputs(old); setSessionStart(oldStart) }
    }

    // ==================================================================
    // Session clock
    // ==================================================================

    /** Starts the clock on first activity; restarts it if the previous start is clearly stale. */
    private fun touchSession() {
        val now = System.currentTimeMillis()
        val start = _sessionStartedAt.value
        if (start == null || now - start > SESSION_STALE_MS) setSessionStart(now)
    }

    private fun setSessionStart(t: Long?) {
        _sessionStartedAt.value = t
        prefs.edit().apply { if (t == null) remove(PREF_SESSION_START) else putLong(PREF_SESSION_START, t) }.apply()
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
                // Whole-session length; 0 (unknown) if there was no clock or it looks abandoned.
                val elapsedSec = _sessionStartedAt.value?.let { (sessionDate - it) / 1000L } ?: 0L
                val durationSec = if (elapsedSec in 1..(SESSION_STALE_MS / 1000L)) elapsedSec.toInt() else 0
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
                                    reps = s.reps.toInt(),
                                    toFailure = s.toFailure, forcedReps = if (s.toFailure) s.forcedReps else 0,
                                    durationSec = durationSec
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
                setSessionStart(null)
                setActiveWorkout(null)
                postEvent(buildString {
                    append(if (heavy) "Heavy Duty session logged" else "Workout logged (${toInsert.size} sets)")
                    if (durationSec > 0) append(" in ${formatDuration(durationSec)}")
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

    fun updateLoggedSet(setId: Long, weightText: String, repsText: String, toFailure: Boolean, forcedReps: Int) {
        viewModelScope.launch {
            val set = workoutDao.getSetById(setId) ?: return@launch
            val w = parseWeight(weightText)
            val r = repsText.toIntOrNull()
            if (w == null || w < 0f || r == null || r <= 0) { postEvent("Enter a valid weight and reps"); return@launch }
            workoutDao.updateSet(set.copy(
                weight = displayToLbs(w, _weightUnit.value), reps = r,
                toFailure = toFailure,
                forcedReps = if (toFailure) forcedReps.coerceIn(0, MAX_FORCED_REPS) else 0
            ))
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
            _activeWorkout.value?.let { w ->
                if (id in w.exerciseIds) setActiveWorkout(w.copy(exerciseIds = w.exerciseIds - id))
            }
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

    fun setTrackFailure(on: Boolean) {
        _trackFailure.value = on
        prefs.edit().putBoolean(PREF_TRACK_FAILURE, on).apply()
    }

    fun setRepRange(min: Int, max: Int) {
        if (min < 1 || max < min) return
        _repRange.value = min..max
        prefs.edit().putInt(PREF_REP_MIN, min).putInt(PREF_REP_MAX, max).apply()
    }

    fun setRecoveryDays(days: Int) {
        _recoveryDays.value = days
        prefs.edit().putInt(PREF_RECOVERY_DAYS, days).apply()
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
                w.append("Date,Exercise,Set,Weight (${unit.label}),Reps,To failure,Forced reps,Est. 1RM (${unit.label}),Session (min),Notes\n")
                rows.forEach { r ->
                    val weight = lbsToDisplay(r.weight, unit)
                    w.append(listOf(
                        formatDate(r.sessionDate, "yyyy-MM-dd HH:mm"),
                        r.exerciseName, r.setNumber.toString(), formatWeight(weight), r.reps.toString(),
                        if (r.toFailure) "yes" else "no", r.forcedReps.toString(),
                        formatWeight(estimated1RM(weight, r.reps)),
                        if (r.durationSec > 0) (r.durationSec / 60).toString() else "", r.notes
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
                setSessionStart(null)
                setActiveWorkout(null)
                postEvent("Restored ${s.exercises} exercises, ${s.templates} templates, ${s.sessions} sessions")
            } catch (e: Exception) {
                postEvent("Restore failed: ${e.message}")
            }
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private fun loadRepRange(): IntRange {
        val lo = prefs.getInt(PREF_REP_MIN, DEFAULT_REP_MIN)
        val hi = prefs.getInt(PREF_REP_MAX, DEFAULT_REP_MAX)
        return if (lo >= 1 && hi >= lo) lo..hi else DEFAULT_REP_MIN..DEFAULT_REP_MAX
    }

    private fun saveDraft(map: Map<Long, List<SetInput>>) {
        val o = JSONObject()
        map.forEach { (id, sets) ->
            o.put(id.toString(), JSONArray().apply {
                sets.forEach { put(JSONArray().put(it.weight).put(it.reps).put(it.toFailure).put(it.forcedReps)) }
            })
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
                        // optBoolean/optInt default gracefully for drafts saved before v4.
                        SetInput(p.optString(0, ""), p.optString(1, ""), p.optBoolean(2, false), p.optInt(3, 0))
                    })
                }
            }
        }
    } catch (e: Exception) { emptyMap() }

    companion object {
        const val DEFAULT_SETS = 3
        const val MAX_SETS = 10
        const val MAX_FORCED_REPS = 20
        const val DEFAULT_RECOVERY_DAYS = 5
        const val DEFAULT_REP_MIN = 6
        const val DEFAULT_REP_MAX = 10

        /** Heavy Duty workouts are short and brutal; past this the Today clock and History nudge you. */
        const val SESSION_TARGET_MIN = 45
        /** A session clock older than this is assumed abandoned (draft left overnight, etc.). */
        const val SESSION_STALE_MS = 3 * 60 * 60 * 1000L

        private const val PREF_UNIT = "weight_unit"
        private const val PREF_DRAFT = "draft_inputs"
        private const val PREF_HEAVY = "heavy_duty"
        private const val PREF_ACTIVE = "active_workout"
        private const val PREF_INTRO_DISMISSED = "intro_dismissed"
        private const val PREF_KEEP_ON = "keep_screen_on"
        private const val PREF_TIMER_MIN = "timer_minutes"
        private const val PREF_TRACK_FAILURE = "track_failure"
        private const val PREF_RECOVERY_DAYS = "recovery_days"
        private const val PREF_REP_MIN = "rep_min"
        private const val PREF_REP_MAX = "rep_max"
        private const val PREF_SESSION_START = "session_started_at"

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
