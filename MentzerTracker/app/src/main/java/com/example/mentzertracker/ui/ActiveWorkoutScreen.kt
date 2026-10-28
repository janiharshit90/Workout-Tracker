@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.ActiveWorkout
import com.example.mentzertracker.NoteInfo
import com.example.mentzertracker.PrInfo
import com.example.mentzertracker.SetInput
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import com.example.mentzertracker.data.SetWithExercise
import com.example.mentzertracker.ui.theme.DisplayFont
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.*
import kotlinx.coroutines.delay

/**
 * A workout in progress, one exercise at a time:
 *   header (name + clock) -> progress segments -> the current exercise -> rest timer + Back/Next.
 * The last exercise's "Next" becomes "Review & finish". The tab bar is hidden meanwhile.
 */
@Composable
fun ActiveWorkoutScreen(vm: WorkoutViewModel, workout: ActiveWorkout, onOpenProgress: (Long) -> Unit) {
    val allExercises by vm.exercises.collectAsState()
    val inputs by vm.inputs.collectAsState()
    val heavy by vm.heavyDutyMode.collectAsState()
    val unit by vm.weightUnit.collectAsState()
    val lastSessions by vm.lastSessions.collectAsState()
    val prs by vm.prs.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val trackFailure by vm.trackFailure.collectAsState()
    val recoveryDays by vm.recoveryDays.collectAsState()
    val repRange by vm.repRange.collectAsState()
    val startedAt by vm.sessionStartedAt.collectAsState()
    val notes by vm.notes.collectAsState()
    val lastNotes by vm.lastNotes.collectAsState()
    val stalls by vm.stalls.collectAsState()

    val exercises = remember(workout, allExercises) {
        val byId = allExercises.associateBy { it.id }
        workout.exerciseIds.mapNotNull { byId[it] }
    }

    /** The set rows shown for an exercise (Heavy Duty = one). */
    fun visibleSets(id: Long): List<SetInput> {
        val sets = inputs[id] ?: List(WorkoutViewModel.DEFAULT_SETS) { SetInput() }
        return if (heavy) sets.take(1) else sets
    }
    // "Done" means a real working set is in; a warm-up alone doesn't tick the exercise off.
    fun isDone(id: Long) = visibleSets(id).any { it.isComplete && !it.isWarmup }

    // On resume, land on the first exercise you haven't finished.
    var page by rememberSaveable {
        mutableStateOf(exercises.indexOfFirst { !isDone(it.id) }.coerceAtLeast(0))
    }
    val current = page.coerceIn(0, (exercises.size - 1).coerceAtLeast(0))
    val isLast = current >= exercises.size - 1

    var showReview by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showNewExercise by remember { mutableStateOf(false) }

    if (showCancel) {
        ConfirmDialog(
            title = "Discard this workout?",
            message = "Everything you've entered in this workout will be lost.",
            confirmLabel = "Discard",
            onDismiss = { showCancel = false },
            onConfirm = { showCancel = false; vm.cancelWorkout() }
        )
    }

    if (showPicker) {
        val available = allExercises.filter { it.id !in workout.exerciseIds }
        PickExerciseDialog(
            available = available,
            onPick = { ex ->
                page = exercises.size  // the new exercise lands at the end
                vm.addToWorkout(ex.id)
                showPicker = false
            },
            onNew = { showPicker = false; showNewExercise = true },
            onDismiss = { showPicker = false }
        )
    }
    if (showNewExercise) {
        AddExerciseDialog(
            onDismiss = { showNewExercise = false; showPicker = true },
            onConfirm = { name, cue -> vm.addExercise(name, cue); showNewExercise = false; showPicker = true }
        )
    }

    if (showReview) {
        val counts = exercises.map { ex -> ex to visibleSets(ex.id).count { it.isComplete && !it.isWarmup } }
        val warmups = exercises.sumOf { ex -> visibleSets(ex.id).count { it.isComplete && it.isWarmup } }
        val totalSets = counts.sumOf { it.second } + warmups
        val incomplete = exercises.sumOf { ex -> visibleSets(ex.id).count { !it.isEmpty && !it.isComplete } }
        AlertDialog(
            onDismissRequest = { showReview = false },
            title = { Text("FINISH WORKOUT?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    counts.forEach { (ex, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (n > 0) "$n set${if (n == 1) "" else "s"}" else "skipped",
                                color = if (n > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (incomplete > 0) {
                        Text(
                            "$incomplete set${if (incomplete == 1) "" else "s"} missing weight or reps will be skipped.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (warmups > 0) {
                        Text("$warmups warm-up set${if (warmups == 1) "" else "s"} logged (not counted toward PRs).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (totalSets == 0) {
                        Text("Nothing to log yet — enter weight and reps for at least one set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReview = false; vm.logWorkout() }, enabled = totalSets > 0) {
                    Text("LOG WORKOUT")
                }
            },
            dismissButton = { TextButton(onClick = { showReview = false }) { Text("KEEP GOING") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        WorkoutHeader(
            name = workout.name,
            startedAt = startedAt,
            pastDate = workout.pastDate,
            onFinish = { showReview = true },
            onClose = { showCancel = true }
        )

        if (exercises.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ProgressSegments(
                count = exercises.size,
                current = current,
                isDone = { isDone(exercises[it].id) },
                isLinked = { linksToNext(workout.exerciseIds, workout.supersets, exercises[it].id) },
                onSelect = { page = it }
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EXERCISE ${current + 1} OF ${exercises.size}",
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showPicker = true }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ADD")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (exercises.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("NO EXERCISES YET", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Add the first exercise for this workout.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showPicker = true }, shape = IronShape) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("ADD EXERCISE")
                    }
                }
            } else {
                val ex = exercises[current]
                val intoNext = exercises.getOrNull(current + 1)
                    ?.takeIf { linksToNext(workout.exerciseIds, workout.supersets, ex.id) }
                val fromPrev = exercises.getOrNull(current - 1)
                    ?.takeIf { linkedFrom(workout.exerciseIds, workout.supersets, ex.id) == it.id }
                key(ex.id) {
                    ExercisePage(
                        supersetInto = intoNext?.name,
                        supersetFrom = fromPrev?.name,
                        exercise = ex,
                        sets = visibleSets(ex.id),
                        heavy = heavy,
                        unit = unit,
                        last = lastSessions[ex.id],
                        pr = prs[ex.id],
                        suggestion = suggestions[ex.id],
                        trackFailure = trackFailure,
                        recoveryDays = recoveryDays,
                        repRange = repRange,
                        note = notes[ex.id].orEmpty(),
                        lastNote = lastNotes[ex.id],
                        stall = stalls[ex.id],
                        onNote = { vm.setNote(ex.id, it) },
                        onToggleWarmup = { idx -> vm.toggleWarmup(ex.id, idx) },
                        onWeight = { idx, v -> vm.updateSet(ex.id, idx, weight = v) },
                        onReps = { idx, v -> vm.updateSet(ex.id, idx, reps = v) },
                        onToggleFailure = { idx -> vm.toggleFailure(ex.id, idx) },
                        onTechnique = { idx, t, d -> vm.adjustTechnique(ex.id, idx, t, d) },
                        onAddSet = { vm.addSet(ex.id) },
                        onRemoveSet = { vm.removeSet(ex.id) },
                        onCopyLast = { vm.fillFromLast(ex.id) },
                        onApplySuggestion = { vm.applySuggestion(ex.id) },
                        onOpenProgress = { onOpenProgress(ex.id) },
                        onRemove = { vm.removeFromWorkout(ex.id) }
                    )
                }
            }
        }

        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val here = exercises.getOrNull(current)
                val upNext = exercises.getOrNull(current + 1)
                val noRest = here != null && upNext != null &&
                    linksToNext(workout.exerciseIds, workout.supersets, here.id)
                if (noRest) NoRestRow(upNext!!.name) else RestRow(
                    vm,
                    title = "Rest \u00b7 ${here?.name ?: workout.name}",
                    nextLine = when {
                        !heavy && here != null ->
                            suggestions[here.id]?.let { "Next set: ${it.target(unit)}" } ?: "Next set of ${here.name}"
                        upNext != null ->
                            "Up next: ${upNext.name}" + (suggestions[upNext.id]?.let { " \u00b7 ${it.target(unit)}" } ?: "")
                        else -> "Last exercise \u2014 finish strong"
                    }
                )
                if (exercises.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            onClick = { page = current - 1 },
                            enabled = current > 0,
                            modifier = Modifier.width(104.dp).height(56.dp)
                        ) { Text("BACK", style = MaterialTheme.typography.titleMedium) }
                        Button(
                            onClick = { if (isLast) showReview = true else page = current + 1 },
                            shape = IronShape,
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text(
                                when {
                                    isLast -> "REVIEW & FINISH"
                                    noRest -> "GO — NO REST"
                                    else -> "NEXT EXERCISE"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header, clock, progress
// ---------------------------------------------------------------------------------------------

@Composable
private fun WorkoutHeader(name: String, startedAt: Long?, pastDate: Long?, onFinish: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pastDate != null) {
                Text("LOGGING FOR ${formatDate(pastDate, "EEE, MMM d").uppercase()}",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                WorkoutClock(startedAt)
            }
        }
        GhostButton(onClick = onFinish) { Text("FINISH", style = MaterialTheme.typography.titleSmall) }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Discard workout") }
    }
}

/**
 * Big elapsed time with a thin bar toward the target. Heavy Duty sessions are short and brutal,
 * so past the target everything turns red as a nudge to wrap up - informational only.
 */
@Composable
private fun WorkoutClock(startedAt: Long?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val elapsedMs = startedAt?.let { now - it }
    if (elapsedMs == null || elapsedMs > WorkoutViewModel.SESSION_STALE_MS) {
        // Resumed long after starting: don't show a misleading multi-hour clock.
        Text("IN PROGRESS", style = MaterialTheme.typography.titleMedium, color = muted)
        return
    }
    val targetMs = WorkoutViewModel.SESSION_TARGET_MIN * 60_000L
    val over = elapsedMs >= targetMs
    val tint = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(formatClock(elapsedMs / 1000), style = MaterialTheme.typography.headlineMedium, color = tint)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.width(160.dp).height(3.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(
            Modifier.fillMaxWidth((elapsedMs.toFloat() / targetMs).coerceIn(0f, 1f))
                .height(3.dp).background(tint)
        )
    }
    if (over) {
        Text("PAST ${WorkoutViewModel.SESSION_TARGET_MIN} MIN — WRAP IT UP",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp, color = tint, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * One bar per exercise: lime = done, white = here, grey = still to do. Tap to jump.
 * Supersetted exercises sit almost touching, so the pairs read as one block.
 */
@Composable
private fun ProgressSegments(
    count: Int, current: Int, isDone: (Int) -> Boolean, isLinked: (Int) -> Boolean, onSelect: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).height(24.dp)
                    .padding(end = if (i == count - 1) 0.dp else if (isLinked(i)) 2.dp else 6.dp)
                    .clickable(onClickLabel = "Go to exercise ${i + 1}", role = Role.Button) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).background(
                        when {
                            i == current -> scheme.onSurface
                            isDone(i) -> scheme.primary
                            else -> scheme.outlineVariant
                        }
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The current exercise
// ---------------------------------------------------------------------------------------------

@Composable
private fun ExercisePage(
    exercise: Exercise,
    /** Next exercise's name when this one goes straight into it (pre-exhaust). */
    supersetInto: String?,
    /** Previous exercise's name when this one follows it with no rest. */
    supersetFrom: String?,
    sets: List<SetInput>,
    heavy: Boolean,
    unit: WeightUnit,
    last: List<SetWithExercise>?,
    pr: PrInfo?,
    suggestion: Suggestion?,
    trackFailure: Boolean,
    recoveryDays: Int,
    repRange: IntRange,
    note: String,
    lastNote: NoteInfo?,
    stall: Stall?,
    onNote: (String) -> Unit,
    onToggleWarmup: (Int) -> Unit,
    onWeight: (Int, String) -> Unit,
    onReps: (Int, String) -> Unit,
    onToggleFailure: (Int) -> Unit,
    onTechnique: (Int, Technique, Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onCopyLast: () -> Unit,
    onApplySuggestion: () -> Unit,
    onOpenProgress: () -> Unit,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val step = stepFor(exercise.stepLbs, unit)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (supersetInto != null || supersetFrom != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, Modifier.size(16.dp), tint = scheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (supersetFrom != null) "SUPERSET · STRAIGHT FROM ${supersetFrom.uppercase()}"
                    else "PRE-EXHAUST · THEN STRAIGHT INTO ${supersetInto!!.uppercase()}",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp, color = scheme.primary, maxLines = 2
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(
                exercise.name.uppercase(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp, lineHeight = 50.sp),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenProgress) {
                Icon(Icons.Filled.ShowChart, "Progress for ${exercise.name}", tint = scheme.onSurfaceVariant)
            }
        }
        if (exercise.cue.isNotBlank()) {
            Text(exercise.cue, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }

        // What to beat. The target is the hero; everything else is quiet supporting text.
        val daysSince = last?.firstOrNull()?.sessionDate?.let { daysAgo(it) }
        val recoveringDaysLeft = daysSince?.let { (recoveryDays - it).coerceAtLeast(0) } ?: 0
        Column(
            Modifier.fillMaxWidth().clip(IronShape).background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, IronShape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (suggestion != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(suggestion.label().uppercase(), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = scheme.onSurfaceVariant)
                        Text(suggestion.target(unit),
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 46.sp, lineHeight = 48.sp),
                            color = scheme.primary)
                    }
                    OutlinedButton(
                        onClick = onApplySuggestion, shape = IronShape,
                        border = BorderStroke(1.dp, scheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("USE\nWEIGHT", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall,
                            color = scheme.primary)
                    }
                }
            } else if (last == null) {
                Text("FIRST TIME", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp, color = scheme.onSurfaceVariant)
                Text("Find a weight you can take to failure in ${repRange.first}–${repRange.last} reps.",
                    style = MaterialTheme.typography.titleMedium, color = scheme.primary)
            }
            if (last != null) {
                Text(
                    "Last (${relativeDay(last.first().sessionDate)}): " +
                        last.joinToString(", ") { "${formatWeight(lbsToDisplay(it.weight, unit))}×${it.reps}" } +
                        (pr?.let { "  ·  PR ${formatWeight(lbsToDisplay(it.weight, unit))}×${it.reps}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                )
            }
            if (stall != null) StallNotice(stall, unit, step, recoveryDays)
            if (lastNote != null) {
                Text(
                    "NOTE \u00b7 ${relativeDay(lastNote.date).uppercase()}",
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp, color = scheme.onSurfaceVariant
                )
                Text(lastNote.text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
            // Only worth a line when it matters: training before you've recovered.
            if (recoveringDaysLeft > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HourglassEmpty, null, Modifier.size(14.dp), tint = scheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "STILL RECOVERING · $recoveringDaysLeft DAY${if (recoveringDaysLeft == 1) "" else "S"} LEFT",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp, color = scheme.error
                    )
                }
            }
        }

        // The sets: the thing you actually came here to fill in.
        Text(
            if (heavy) "YOUR SET · ONE TO FAILURE" else "YOUR SETS",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp, color = scheme.onSurfaceVariant
        )

        sets.forEachIndexed { idx, s ->
            val lastSet = last?.getOrNull(idx)
            val weightFallback = suggestion?.let { lbsToDisplay(it.weightLbs, unit) }
                ?: lastSet?.let { lbsToDisplay(it.weight, unit) }
            val repsFallback = suggestion?.targetReps ?: lastSet?.reps
            SetBlock(
                index = idx,
                showIndex = !heavy,
                set = s,
                big = heavy,
                unit = unit,
                step = step,
                weightHint = lastSet?.let { formatWeight(lbsToDisplay(it.weight, unit)) } ?: unit.label,
                repsHint = lastSet?.reps?.toString() ?: "reps",
                trackFailure = trackFailure,
                onWeight = { onWeight(idx, it) },
                onReps = { onReps(idx, it) },
                onNudgeWeight = { delta -> onWeight(idx, nudgeWeight(s.weight, delta, weightFallback)) },
                onNudgeReps = { delta -> onReps(idx, nudgeReps(s.reps, delta, repsFallback)) },
                onToggleFailure = { onToggleFailure(idx) },
                onToggleWarmup = { onToggleWarmup(idx) },
                onTechnique = { t, d -> onTechnique(idx, t, d) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!heavy) {
                TextButton(onClick = onAddSet, enabled = sets.size < WorkoutViewModel.MAX_SETS) { Text("+ SET") }
                TextButton(onClick = onRemoveSet, enabled = sets.size > 1) { Text("− SET") }
            }
            Spacer(Modifier.weight(1f))
            if (last != null) TextButton(onClick = onCopyLast) { Text("COPY LAST TIME") }
        }

        OutlinedTextField(
            value = note, onValueChange = onNote,
            modifier = Modifier.fillMaxWidth(), shape = IronShape,
            label = { Text("NOTE (OPTIONAL)", letterSpacing = 1.sp) },
            placeholder = { Text("Form, tempo, how it felt\u2026") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 1, maxLines = 4
        )

        TextButton(onClick = onRemove) {
            Text("REMOVE FROM THIS WORKOUT", color = scheme.error)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One set: big numeric fields with -/+ steppers under them (typing stays available), then the
 * Heavy Duty bits - to-failure and forced reps. Single-set (Heavy Duty) is large; multi-set is compact.
 */
@Composable
private fun SetBlock(
    index: Int,
    showIndex: Boolean,
    set: SetInput,
    big: Boolean,
    unit: WeightUnit,
    step: Float,
    weightHint: String,
    repsHint: String,
    trackFailure: Boolean,
    onWeight: (String) -> Unit,
    onReps: (String) -> Unit,
    onNudgeWeight: (Float) -> Unit,
    onNudgeReps: (Int) -> Unit,
    onToggleFailure: () -> Unit,
    onToggleWarmup: () -> Unit,
    onTechnique: (Technique, Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val numberStyle = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = if (big) 40.sp else 28.sp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showIndex) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SET ${index + 1}" + if (set.isWarmup) " \u00b7 WARM-UP" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (set.isWarmup) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // Warm-ups only make sense when you're doing several sets.
                FilterChip(
                    selected = set.isWarmup, onClick = onToggleWarmup, shape = IronShape,
                    label = { Text("WARM-UP", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                NumberField(set.weight, onWeight, "WEIGHT (${unit.label.uppercase()})", weightHint,
                    KeyboardType.Decimal, numberStyle)
                StepperRow("−${formatWeight(step)}", "+${formatWeight(step)}",
                    { onNudgeWeight(-step) }, { onNudgeWeight(step) })
            }
            Column(Modifier.weight(1f)) {
                NumberField(set.reps, onReps, "REPS", repsHint, KeyboardType.Number, numberStyle)
                StepperRow("−1", "+1", { onNudgeReps(-1) }, { onNudgeReps(1) })
            }
            if (!big && trackFailure) {
                IconButton(onClick = onToggleFailure, modifier = Modifier.padding(top = 4.dp).size(48.dp)) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = if (set.toFailure) "Marked to failure" else "Mark set as to failure",
                        tint = if (set.toFailure) scheme.error else scheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        if (big && trackFailure) {
            if (set.toFailure) {
                Button(
                    onClick = onToggleFailure, shape = IronShape,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null)
                    Spacer(Modifier.width(8.dp))
                    Text("TO FAILURE", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
                }
            } else {
                GhostButton(
                    onClick = onToggleFailure,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("MARK TO FAILURE", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
                }
            }
        }

        if (trackFailure && set.toFailure) {
            BeyondFailurePanel(set.beyond, onTechnique, compact = !big)
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboard: KeyboardType,
    style: TextStyle
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = style,
        shape = IronShape,
        label = { Text(label, letterSpacing = 1.sp) },
        placeholder = { Text(placeholder, style = style) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next)
    )
}

@Composable
private fun StepperRow(minus: String, plus: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton(onClick = onMinus, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.weight(1f).height(44.dp)) { Text(minus, style = MaterialTheme.typography.titleMedium) }
        GhostButton(onClick = onPlus, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.weight(1f).height(44.dp)) { Text(plus, style = MaterialTheme.typography.titleMedium) }
    }
}

/** Outlined button with plain white text: lime is kept for the things that matter. */
@Composable
private fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = IronShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = contentPadding, content = content
    )
}

/** Stepper maths. A blank field jumps to the sensible starting point rather than from zero. */
private fun nudgeWeight(current: String, delta: Float, fallback: Float?): String {
    val base = parseWeight(current)
    val next = when {
        base != null -> base + delta
        fallback != null -> fallback
        else -> delta
    }
    return formatWeight(next.coerceAtLeast(0f))
}

private fun nudgeReps(current: String, delta: Int, fallback: Int?): String {
    val base = current.toIntOrNull()
    val next = when {
        base != null -> base + delta
        fallback != null -> fallback
        else -> delta
    }.coerceIn(0, 999)
    return if (next == 0) "" else next.toString()
}

// ---------------------------------------------------------------------------------------------
// Rest timer (compact; the length is set in Settings)
// ---------------------------------------------------------------------------------------------

/** Shown instead of the rest timer when this exercise goes straight into the next one. */
@Composable
private fun NoRestRow(next: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(48.dp).border(1.dp, scheme.primary, IronShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Link, null, tint = scheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("NO REST — STRAIGHT INTO ${next.uppercase()}", style = MaterialTheme.typography.titleSmall,
            color = scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RestRow(vm: WorkoutViewModel, title: String, nextLine: String) {
    val endAt by vm.timerEndAt.collectAsState()
    val minutes by vm.timerMinutes.collectAsState()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Android 13+ needs permission to show the lock-screen countdown / rest-over alert.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.startTimer(title, nextLine)
    }

    LaunchedEffect(endAt) {
        if (endAt == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val running = endAt != null
    val remainingSec = endAt?.let { ((it - now).coerceAtLeast(0L) + 999) / 1000 } ?: (minutes * 60L)
    val tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Timer, null, tint = tint)
        Text("REST  %d:%02d".format(remainingSec / 60, remainingSec % 60),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp), color = tint,
            modifier = Modifier.weight(1f))
        if (running) {
            TextButton(onClick = { vm.addTimerSeconds(30) }) { Text("+30S") }
            GhostButton(onClick = { vm.stopTimer() }) { Text("STOP") }
        } else {
            GhostButton(onClick = { start() }) { Text("START") }
        }
    }
}
