package com.example.mentzertracker

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.mentzertracker.data.*
import com.example.mentzertracker.timer.RestTimer
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
    val forcedReps: Int = 0,
    /** Warm-up sets are logged but excluded from PRs and from the progression target. */
    val isWarmup: Boolean = false,
    /** Beyond-failure work (see [Technique]); like forced reps, only meaningful when toFailure. */
    val negatives: Int = 0,
    val restPauseReps: Int = 0,
    val holdSec: Int = 0
) {
    val beyond: BeyondFailure get() = BeyondFailure(forcedReps, negatives, restPauseReps, holdSec)
    fun withBeyond(b: BeyondFailure) =
        copy(forcedReps = b.forcedReps, negatives = b.negatives, restPauseReps = b.restPauseReps, holdSec = b.holdSec)

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
data class ActiveWorkout(
    val name: String,
    val exerciseIds: List<Long>,
    /** Midnight of the day being backfilled, or null for a normal workout happening now. */
    val pastDate: Long? = null,
    /** The template it was started from (null = custom); saved on the logged sets. */
    val templateId: Long? = null,
    /** Exercises done straight into the next one, no rest (pre-exhaust supersets). */
    val supersets: Set<Long> = emptySet()
)

/**
 * The overview shown before a workout starts: what's in it, still editable. Lives only in memory
 * (survives rotation, not an app kill) - a workout becomes real, and persisted, when you tap Start.
 */
data class WorkoutPreview(
    val templateId: Long?,
    val name: String,
    val exerciseIds: List<Long>,
    /** Midnight of the day being backfilled, or null for a normal workout happening now. */
    val pastDate: Long? = null,
    /** Exercises done straight into the next one, no rest (pre-exhaust supersets). */
    val supersets: Set<Long> = emptySet()
)

/** The most recent note written for an exercise. */
data class NoteInfo(val text: String, val date: Long)

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
    private val bodyWeightDao = db.bodyWeightDao()
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

    /**
     * Midnight of the day being backfilled (a past workout being previewed or logged), else null.
     * While backfilling, "last time", notes, stalls and targets only look at sessions BEFORE that
     * day, so you see what you were up against back then. Kept in sync in the init block.
     */
    private val backfillDay = MutableStateFlow<Long?>(null)

    /** History as of now, or as of the backfilled day. */
    private val historyForTargets: Flow<List<SetWithExercise>> = combine(history, backfillDay) { h, day ->
        if (day == null) h else h.filter { it.sessionDate < day }
    }

    /** exerciseId -> sets from the most recent session that included it. */
    val lastSessions: StateFlow<Map<Long, List<SetWithExercise>>> = historyForTargets.map { computeLastSessions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** exerciseId -> the latest non-blank note, shown as a reminder the next time you train it. */
    val lastNotes: StateFlow<Map<Long, NoteInfo>> = historyForTargets.map { computeLastNotes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val templates: StateFlow<List<WorkoutTemplate>> = templateDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** templateId -> ordered exerciseIds. Reactive, so edits show up everywhere instantly. */
    val templateExercises: StateFlow<Map<Long, List<Long>>> = templateDao.observeAllTemplateExercises()
        .map { rows -> rows.groupBy { it.templateId }.mapValues { (_, l) -> l.sortedBy { it.sortOrder }.map { it.exerciseId } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** templateId -> exercises in it that go straight into the next one (supersets). */
    val templateSupersets: StateFlow<Map<Long, Set<Long>>> = templateDao.observeAllTemplateExercises()
        .map { rows -> rows.filter { it.supersetNext }.groupBy { it.templateId }.mapValues { (_, l) -> l.map { it.exerciseId }.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Templates in start-screen order, for naming past sessions (letters, calendar, sharing). */
    val templateIndex: StateFlow<TemplateIndex> = combine(templates, templateExercises) { ts, ex ->
        TemplateIndex(ts.map { it.id }, ts.map { it.name }, ts.map { ex[it.id].orEmpty().toSet() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplateIndex())

    /** Body-weight log, oldest first (lbs). */
    val bodyWeights: StateFlow<List<BodyWeight>> = bodyWeightDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _preview = MutableStateFlow<WorkoutPreview?>(null)
    val preview: StateFlow<WorkoutPreview?> = _preview

    /** Per-exercise notes for the workout in progress (persisted with the draft). */
    private val _notes = MutableStateFlow(loadNotes())
    val notes: StateFlow<Map<Long, String>> = _notes

    /** Shown right after a workout is logged; in memory only (the data itself is in History). */
    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary

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

    /** exerciseId -> a stall warning when the last sessions stopped beating your best. */
    val stalls: StateFlow<Map<Long, Stall>> = combine(historyForTargets, _recoveryDays) { h, days -> computeStalls(h, days) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Rep range that drives progression suggestions (double progression). */
    private val _repRange = MutableStateFlow(loadRepRange())
    val repRange: StateFlow<IntRange> = _repRange

    /** exerciseId -> what to aim for next time, derived from the last session of each exercise. */
    val suggestions: StateFlow<Map<Long, Suggestion>> =
        combine(lastSessions, _weightUnit, _repRange, exercises) { last, unit, range, exs ->
            val steps = exs.associate { it.id to it.stepLbs }
            last.mapNotNull { (id, sets) ->
                suggestNext(sets, unit, range, stepFor(steps[id] ?: 0f, unit))?.let { id to it }
            }.toMap()
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

    /**
     * Wall-clock time (System.currentTimeMillis) at which the rest ends, or null when idle.
     * The timer itself lives in [RestTimer] so it keeps running (and alerts) with the app closed.
     */
    val timerEndAt: StateFlow<Long?> = RestTimer.endAt

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
            combine(_activeWorkout, _preview) { w, p -> (w?.pastDate ?: p?.pastDate)?.let(::startOfDay) }
                .collect { backfillDay.value = it }
        }
        viewModelScope.launch { RestTimer.finished.collect { postEvent("Rest is over \u2014 next set!") } }
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

    /** Opens the pre-start overview for a template (or an empty custom workout). Starts nothing. */
    fun openPreview(templateId: Long?, pastDate: Long? = null) {
        if (pastDate != null && _activeWorkout.value != null) {
            postEvent("Finish or discard your current workout first")
            return
        }
        viewModelScope.launch {
            if (templateId == null) {
                _preview.value = WorkoutPreview(null, "Custom workout", emptyList(), pastDate)
                return@launch
            }
            val existing = exerciseDao.getAllOnce().map { it.id }.toSet()
            val t = templateDao.getAllTemplatesOnce().firstOrNull { it.id == templateId } ?: return@launch
            val ids = templateDao.getExerciseIdsForTemplate(templateId).filter { it in existing }
            val linked = templateDao.getAllTemplateExercisesOnce()
                .filter { it.templateId == templateId && it.supersetNext }.map { it.exerciseId }.toSet()
            _preview.value = WorkoutPreview(t.id, t.name, ids, pastDate, cleanSupersets(ids, linked))
        }
    }

    fun closePreview() { _preview.value = null }

    fun previewAdd(exerciseId: Long) {
        val p = _preview.value ?: return
        if (exerciseId !in p.exerciseIds) _preview.value = p.copy(exerciseIds = p.exerciseIds + exerciseId)
    }

    fun previewRemove(exerciseId: Long) {
        val p = _preview.value ?: return
        _preview.value = p.copy(
            exerciseIds = p.exerciseIds - exerciseId,
            supersets = supersetsWithout(p.exerciseIds, p.supersets, exerciseId)
        )
    }

    /** Moves an exercise up (-1) or down (+1) in the preview, keeping superset links sensible. */
    fun previewMove(exerciseId: Long, delta: Int) {
        val p = _preview.value ?: return
        val (ids, ss) = com.example.mentzertracker.util.moveExercise(
            p.exerciseIds, p.supersets, p.exerciseIds.indexOf(exerciseId), delta
        )
        _preview.value = p.copy(exerciseIds = ids, supersets = ss)
    }

    /** Starts the previewed workout. This is the moment the clock starts. */
    fun startWorkout() {
        val p = _preview.value ?: return  // also makes a double-tap harmless
        setInputs(emptyMap())
        setNotes(emptyMap())
        // A backfilled workout has no live clock: its date is the day you picked.
        setSessionStart(if (p.pastDate == null) System.currentTimeMillis() else null)
        setActiveWorkout(ActiveWorkout(p.name, p.exerciseIds, p.pastDate, p.templateId, cleanSupersets(p.exerciseIds, p.supersets)))
        _preview.value = null
    }

    fun addToWorkout(exerciseId: Long) {
        val w = _activeWorkout.value ?: return
        if (exerciseId !in w.exerciseIds) setActiveWorkout(w.copy(exerciseIds = w.exerciseIds + exerciseId))
    }

    fun removeFromWorkout(exerciseId: Long) {
        val w = _activeWorkout.value ?: return
        if (exerciseId in _inputs.value) setInputs(_inputs.value - exerciseId)
        if (exerciseId in _notes.value) setNotes(_notes.value - exerciseId)
        setActiveWorkout(w.copy(
            exerciseIds = w.exerciseIds - exerciseId,
            supersets = supersetsWithout(w.exerciseIds, w.supersets, exerciseId)
        ))
    }

    /** Throws away the workout in progress (the UI confirms first). */
    fun cancelWorkout() {
        RestTimer.stop(ctx)
        setInputs(emptyMap())
        setNotes(emptyMap())
        setSessionStart(null)
        setActiveWorkout(null)
    }

    fun dismissSummary() { _summary.value = null }

    fun dismissIntro() {
        _introDismissed.value = true
        prefs.edit().putBoolean(PREF_INTRO_DISMISSED, true).apply()
    }

    private fun setActiveWorkout(w: ActiveWorkout?) {
        _activeWorkout.value = w
        prefs.edit().apply {
            if (w == null) remove(PREF_ACTIVE) else putString(PREF_ACTIVE,
                JSONObject().put("name", w.name).put("ids", JSONArray(w.exerciseIds))
                    .put("ss", JSONArray(w.supersets.toList()))
                    .apply {
                        if (w.pastDate != null) put("past", w.pastDate)
                        if (w.templateId != null) put("tid", w.templateId)
                    }.toString())
        }.apply()
    }

    private fun loadActiveWorkout(): ActiveWorkout? {
        try {
            prefs.getString(PREF_ACTIVE, null)?.let { raw ->
                val o = JSONObject(raw)
                val arr = o.getJSONArray("ids")
                val ss = o.optJSONArray("ss")
                return ActiveWorkout(
                    o.optString("name", "Workout"), List(arr.length()) { arr.getLong(it) },
                    o.optLong("past", 0L).takeIf { it > 0L },
                    o.optLong("tid", 0L).takeIf { it > 0L },
                    if (ss == null) emptySet() else List(ss.length()) { ss.getLong(it) }.toSet()
                )
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
        // A set can't be both a warm-up and an all-out set.
        list[setIndex] = cur.copy(
            toFailure = !cur.toFailure,
            isWarmup = if (cur.toFailure) cur.isWarmup else false
        ).let { if (cur.toFailure) it.withBeyond(BeyondFailure()) else it }
        touchSession()
        setInputs(_inputs.value + (exerciseId to list.toList()))
    }

    /** Tags/untags a set as a warm-up. Warm-ups never count as failure sets. */
    fun toggleWarmup(exerciseId: Long, setIndex: Int) {
        val list = currentSets(exerciseId).toMutableList()
        if (setIndex !in list.indices) return
        val cur = list[setIndex]
        list[setIndex] = cur.copy(isWarmup = !cur.isWarmup, toFailure = false).withBeyond(BeyondFailure())
        touchSession()
        setInputs(_inputs.value + (exerciseId to list.toList()))
    }

    fun setNote(exerciseId: Long, text: String) {
        val clean = text.take(MAX_NOTE_LENGTH)
        setNotes(if (clean.isEmpty()) _notes.value - exerciseId else _notes.value + (exerciseId to clean))
    }

    private fun setNotes(new: Map<Long, String>) {
        _notes.value = new
        val o = JSONObject()
        new.forEach { (id, text) -> o.put(id.toString(), text) }
        prefs.edit().putString(PREF_NOTES, o.toString()).apply()
    }

    private fun loadNotes(): Map<Long, String> = try {
        val raw = prefs.getString(PREF_NOTES, null)
        if (raw == null) emptyMap() else {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k.toLong(), o.getString(k)) } }
        }
    } catch (e: Exception) { emptyMap() }

    /**
     * One tap of +/- on a beyond-failure technique ([direction] is +1 or -1; holds move in 5 s steps).
     * Only meaningful once a set is marked to failure.
     */
    fun adjustTechnique(exerciseId: Long, setIndex: Int, technique: Technique, direction: Int) {
        val list = currentSets(exerciseId).toMutableList()
        if (setIndex !in list.indices) return
        val cur = list[setIndex]
        if (!cur.toFailure) return
        list[setIndex] = cur.withBeyond(cur.beyond.nudge(technique, direction))
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
        val oldNotes = _notes.value
        setInputs(emptyMap())
        setNotes(emptyMap())
        setSessionStart(null)
        postEvent("Cleared all entries", "Undo") { setInputs(old); setNotes(oldNotes); setSessionStart(oldStart) }
    }

    // ==================================================================
    // Session clock
    // ==================================================================

    /** Starts the clock on first activity; restarts it if the previous start is clearly stale. */
    private fun touchSession() {
        if (_activeWorkout.value?.pastDate != null) return  // backfilling: no live clock
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
                val now = System.currentTimeMillis()
                val past = _activeWorkout.value?.pastDate
                val templateId = _activeWorkout.value?.templateId ?: 0L
                // A backfilled workout lands on the chosen day, at the current time of day so two
                // backfilled sessions never share a timestamp (sets are grouped by it).
                val sessionDate = if (past != null) past + (now - startOfDay(now)) else now
                // Whole-session length; 0 (unknown) if there was no clock or it looks abandoned.
                val elapsedSec = if (past != null) 0L else _sessionStartedAt.value?.let { (now - it) / 1000L } ?: 0L
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
                                    durationSec = durationSec,
                                    // One-set Heavy Duty has no warm-up rows, so ignore a stale flag.
                                    isWarmup = !heavy && s.isWarmup,
                                    negatives = if (s.toFailure) s.negatives else 0,
                                    restPauseReps = if (s.toFailure) s.restPauseReps else 0,
                                    holdSec = if (s.toFailure) s.holdSec else 0,
                                    templateId = templateId
                                )
                            }
                        }
                    }
                }

                if (toInsert.isEmpty()) {
                    postEvent(if (incomplete > 0) "Each set needs both weight and reps" else "Enter at least one set")
                    return@launch
                }

                // Each exercise's note rides on its first working set (or first set if all warm-ups).
                for (exId in toInsert.map { it.exerciseId }.distinct()) {
                    val note = _notes.value[exId]?.trim().orEmpty()
                    if (note.isEmpty()) continue
                    val idx = toInsert.indexOfFirst { it.exerciseId == exId && !it.isWarmup }
                        .takeIf { it >= 0 } ?: toInsert.indexOfFirst { it.exerciseId == exId }
                    if (idx >= 0) toInsert[idx] = toInsert[idx].copy(notes = note)
                }

                val touched = toInsert.map { it.exerciseId }.distinct()
                // Taken BEFORE inserting: what you were up against going in (working sets only).
                val previousBest = touched.associateWith { workoutDao.bestSetForExercise(it) }
                val previousTop = touched.associateWith { id ->
                    lastSessions.value[id]?.map { TopSet(it.weight, it.reps) }?.best()
                }
                db.withTransaction { workoutDao.insertAll(toInsert) }

                val order = _activeWorkout.value?.exerciseIds.orEmpty()
                val summaryExercises = touched
                    .sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
                    .mapNotNull { exId ->
                        val ex = exercisesById[exId] ?: return@mapNotNull null
                        val mine = toInsert.filter { it.exerciseId == exId }
                        val top = mine.filter { !it.isWarmup }.map { TopSet(it.weight, it.reps) }.best()
                        ExerciseSummary(
                            exerciseId = exId, name = ex.name,
                            sets = mine.map {
                                SummarySet(it.weight, it.reps, it.toFailure,
                                    BeyondFailure(it.forcedReps, it.negatives, it.restPauseReps, it.holdSec), it.isWarmup)
                            },
                            top = top,
                            // PR / "vs last time" only make sense for a workout happening now.
                            isPr = past == null && top != null && beatsBest(previousBest[exId]?.let { TopSet(it.weight, it.reps) }, top),
                            change = if (past != null) null else top?.let { compareToLast(previousTop[exId], it) },
                            note = mine.firstOrNull { it.notes.isNotBlank() }?.notes.orEmpty()
                        )
                    }

                val summary = WorkoutSummary(
                    name = _activeWorkout.value?.name ?: "Workout",
                    durationSec = durationSec,
                    exercises = summaryExercises,
                    skippedIncomplete = incomplete,
                    date = sessionDate
                )

                // A backfilled workout is judged against what came before THAT day (PRs, vs last time).
                val finalSummary = if (past == null) summary else
                    summarizeSession(sessionDate, summary.name, workoutDao.getAllWithExerciseOnce())
                        ?.copy(skippedIncomplete = incomplete, durationSec = 0) ?: summary

                RestTimer.stop(ctx)
                setInputs(emptyMap())
                setNotes(emptyMap())
                setSessionStart(null)
                setActiveWorkout(null)
                _summary.value = finalSummary
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

    fun updateLoggedSet(
        setId: Long, weightText: String, repsText: String,
        toFailure: Boolean, beyond: BeyondFailure, isWarmup: Boolean, notes: String
    ) {
        viewModelScope.launch {
            val set = workoutDao.getSetById(setId) ?: return@launch
            val w = parseWeight(weightText)
            val r = repsText.toIntOrNull()
            if (w == null || w < 0f || r == null || r <= 0) { postEvent("Enter a valid weight and reps"); return@launch }
            val failure = toFailure && !isWarmup
            val b = if (failure) beyond else BeyondFailure()
            workoutDao.updateSet(set.copy(
                weight = displayToLbs(w, _weightUnit.value), reps = r,
                toFailure = failure,
                forcedReps = b.forcedReps.coerceIn(0, MAX_FORCED_REPS),
                negatives = b.negatives, restPauseReps = b.restPauseReps, holdSec = b.holdSec,
                isWarmup = isWarmup,
                notes = notes.trim().take(MAX_NOTE_LENGTH)
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

    /** Sets an exercise's weight jump from a display-unit value; null/<=0 goes back to the default. */
    fun setExerciseStep(id: Long, stepDisplay: Float?) {
        viewModelScope.launch {
            val lbs = if (stepDisplay == null || stepDisplay <= 0f) 0f else displayToLbs(stepDisplay, _weightUnit.value)
            exerciseDao.updateStep(id, lbs)
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
            if (id in _notes.value) setNotes(_notes.value - id)
            _activeWorkout.value?.let { w ->
                if (id in w.exerciseIds) setActiveWorkout(w.copy(
                    exerciseIds = w.exerciseIds - id, supersets = supersetsWithout(w.exerciseIds, w.supersets, id)
                ))
            }
            _preview.value?.let { p ->
                if (id in p.exerciseIds) _preview.value = p.copy(
                    exerciseIds = p.exerciseIds - id, supersets = supersetsWithout(p.exerciseIds, p.supersets, id)
                )
            }
            postEvent("Exercise deleted")
        }
    }

    // ==================================================================
    // Templates
    // ==================================================================

    fun addTemplate(name: String, exerciseIds: List<Long>, supersets: Set<Long> = emptySet()) {
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
                templateDao.insertTemplateExercises(templateRows(templateId, exerciseIds, supersets))
            }
            postEvent("Created template \"$trimmed\"")
        }
    }

    fun updateTemplate(id: Long, name: String, exerciseIds: List<Long>, supersets: Set<Long> = emptySet()) {
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
                templateDao.insertTemplateExercises(templateRows(id, exerciseIds, supersets))
            }
            postEvent("Updated \"$trimmed\"")
        }
    }

    private fun templateRows(templateId: Long, exerciseIds: List<Long>, supersets: Set<Long>): List<TemplateExercise> {
        val ids = exerciseIds.distinct()
        val linked = cleanSupersets(ids, supersets)
        return ids.mapIndexed { idx, exId -> TemplateExercise(templateId, exId, idx, supersetNext = exId in linked) }
    }

    /**
     * Adds a ready-made routine: creates the exercises it needs (reusing ones with the same name)
     * and one template per workout, with its supersets linked. Templates whose name is taken are skipped.
     */
    fun installRoutine(key: String) {
        val routine = ROUTINES.firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            var created = 0
            var skipped: List<String> = emptyList()
            db.withTransaction {
                val existing = exerciseDao.getAllOnce()
                val plan = planRoutine(routine, existing.map { it.name }, templateDao.getAllTemplatesOnce().map { it.name })
                skipped = plan.skippedWorkouts
                var nextEx = (exerciseDao.maxSortOrder() ?: -1) + 1
                if (plan.newExercises.isNotEmpty()) {
                    exerciseDao.insertAll(plan.newExercises.map { slot ->
                        Exercise(name = slot.exercise, cue = slot.cue, sortOrder = nextEx++, stepLbs = slot.stepLbs)
                    })
                }
                val byName = exerciseDao.getAllOnce().associateBy { it.name.trim().lowercase() }
                var nextT = (templateDao.maxSortOrder() ?: -1) + 1
                plan.newWorkouts.forEach { w ->
                    val ids = w.slots.mapNotNull { byName[it.exercise.lowercase()]?.id }
                    val linked = w.slots.filter { it.supersetNext }.mapNotNull { byName[it.exercise.lowercase()]?.id }.toSet()
                    val tId = templateDao.insertTemplate(WorkoutTemplate(name = w.name, sortOrder = nextT++))
                    templateDao.insertTemplateExercises(templateRows(tId, ids, linked))
                    created++
                }
            }
            postEvent(when {
                created == 0 -> "You already have the ${routine.title} workouts"
                skipped.isEmpty() -> "Added ${routine.title}: $created workouts"
                else -> "Added $created workouts (kept your existing ${skipped.joinToString()})"
            })
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            templateDao.deleteTemplate(id)
            if (_preview.value?.templateId == id) _preview.value = null
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

    /** [title] and [nextLine] are what the lock-screen notification shows. */
    fun startTimer(title: String, nextLine: String) =
        RestTimer.start(ctx, _timerMinutes.value * 60, title, nextLine)

    fun addTimerSeconds(seconds: Int) = RestTimer.addSeconds(ctx, seconds)

    fun stopTimer() = RestTimer.stop(ctx)

    // ==================================================================
    // Body weight
    // ==================================================================

    /** Logs a body-weight reading (in the display unit) for now. */
    fun logBodyWeight(text: String) {
        val w = parseWeight(text)
        if (w == null || w <= 0f || w > 1500f) { postEvent("Enter a valid body weight"); return }
        viewModelScope.launch {
            bodyWeightDao.insert(BodyWeight(date = System.currentTimeMillis(), weightLbs = displayToLbs(w, _weightUnit.value)))
            postEvent("Body weight logged")
        }
    }

    fun deleteBodyWeight(id: Long) {
        viewModelScope.launch {
            val entry = bodyWeightDao.getById(id) ?: return@launch
            bodyWeightDao.deleteById(id)
            postEvent("Entry deleted", "Undo") { viewModelScope.launch { bodyWeightDao.insertAll(listOf(entry)) } }
        }
    }

    // ==================================================================
    // Sharing
    // ==================================================================

    /** A logged session rebuilt as a summary (PRs judged against what came before it). */
    fun sessionSummary(sessionDate: Long): WorkoutSummary? {
        val all = history.value
        val sets = all.filter { it.sessionDate == sessionDate }
        if (sets.isEmpty()) return null
        val info = sessionsByDay(sets).values.flatten().firstOrNull() ?: return null
        val name = templateIndex.value.indexOf(info)?.let { templateIndex.value.names.getOrNull(it) } ?: "Workout"
        return summarizeSession(sessionDate, name, all)
    }

    /** Draws [summary] as an image in the share cache; null (with a message) if that fails. */
    suspend fun summaryImage(summary: WorkoutSummary): File? = try {
        withContext(Dispatchers.Default) { ShareCard.writePng(ctx, summary, _weightUnit.value) }
    } catch (e: Exception) {
        postEvent("Couldn't create the image: ${e.message}")
        null
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
                w.append("Date,Exercise,Set,Weight (${unit.label}),Reps,To failure,Forced reps,Negatives,Rest-pause reps,Static hold (s),Warm-up,Est. 1RM (${unit.label}),Session (min),Notes\n")
                rows.forEach { r ->
                    val weight = lbsToDisplay(r.weight, unit)
                    w.append(listOf(
                        formatDate(r.sessionDate, "yyyy-MM-dd HH:mm"),
                        r.exerciseName, r.setNumber.toString(), formatWeight(weight), r.reps.toString(),
                        if (r.toFailure) "yes" else "no", r.forcedReps.toString(),
                        r.negatives.toString(), r.restPauseReps.toString(), r.holdSec.toString(),
                        if (r.isWarmup) "yes" else "no",
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
                setNotes(emptyMap())
                _summary.value = null
                setSessionStart(null)
                setActiveWorkout(null)
                _preview.value = null
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
                sets.forEach {
                    put(JSONArray().put(it.weight).put(it.reps).put(it.toFailure).put(it.forcedReps).put(it.isWarmup)
                        .put(it.negatives).put(it.restPauseReps).put(it.holdSec))
                }
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
                        SetInput(p.optString(0, ""), p.optString(1, ""), p.optBoolean(2, false), p.optInt(3, 0), p.optBoolean(4, false),
                            p.optInt(5, 0), p.optInt(6, 0), p.optInt(7, 0))
                    })
                }
            }
        }
    } catch (e: Exception) { emptyMap() }

    companion object {
        const val DEFAULT_SETS = 3
        const val MAX_SETS = 10
        const val MAX_FORCED_REPS = 20
        const val MAX_NOTE_LENGTH = 300
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
        private const val PREF_NOTES = "draft_notes"
        private const val PREF_INTRO_DISMISSED = "intro_dismissed"
        private const val PREF_KEEP_ON = "keep_screen_on"
        private const val PREF_TIMER_MIN = "timer_minutes"
        private const val PREF_TRACK_FAILURE = "track_failure"
        private const val PREF_RECOVERY_DAYS = "recovery_days"
        private const val PREF_REP_MIN = "rep_min"
        private const val PREF_REP_MAX = "rep_max"
        private const val PREF_SESSION_START = "session_started_at"

        /** Warm-ups never count toward PRs. */
        fun computePrs(sets: List<SetWithExercise>): Map<Long, PrInfo> =
            sets.filter { !it.isWarmup }.groupBy { it.exerciseId }.mapNotNull { (exId, list) ->
                // Heaviest weight, then most reps, then the EARLIEST date it was achieved.
                list.maxWithOrNull(compareBy<SetWithExercise>({ it.weight }, { it.reps }, { -it.sessionDate }))
                    ?.let { exId to PrInfo(exId, it.weight, it.reps, it.sessionDate) }
            }.toMap()

        /** The latest session's WORKING sets per exercise (warm-ups excluded). */
        fun computeLastSessions(sets: List<SetWithExercise>): Map<Long, List<SetWithExercise>> =
            sets.filter { !it.isWarmup }.groupBy { it.exerciseId }.mapValues { (_, list) ->
                val latest = list.maxOf { it.sessionDate }
                list.filter { it.sessionDate == latest }.sortedBy { it.setNumber }
            }

        /** [sets] may be in any order; the newest non-blank note per exercise wins. */
        fun computeLastNotes(sets: List<SetWithExercise>): Map<Long, NoteInfo> =
            sets.filter { it.notes.isNotBlank() }.groupBy { it.exerciseId }.mapValues { (_, list) ->
                list.maxByOrNull { it.sessionDate }!!.let { NoteInfo(it.notes, it.sessionDate) }
            }

        private fun csvEscape(s: String): String =
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
