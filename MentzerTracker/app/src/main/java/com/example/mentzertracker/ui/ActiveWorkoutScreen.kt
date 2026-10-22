@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.ActiveWorkout
import com.example.mentzertracker.PrInfo
import com.example.mentzertracker.SetInput
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import com.example.mentzertracker.data.SetWithExercise
import com.example.mentzertracker.util.*
import kotlinx.coroutines.delay

/**
 * A workout in progress, one exercise at a time:
 *   header (name + clock) -> exercise chips -> the current exercise -> rest timer + Back/Next.
 * The last exercise's "Next" becomes "Review & finish".
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
    val startedAt by vm.sessionStartedAt.collectAsState()

    val exercises = remember(workout, allExercises) {
        val byId = allExercises.associateBy { it.id }
        workout.exerciseIds.mapNotNull { byId[it] }
    }

    /** The set rows shown for an exercise (Heavy Duty = one). */
    fun visibleSets(id: Long): List<SetInput> {
        val sets = inputs[id] ?: List(WorkoutViewModel.DEFAULT_SETS) { SetInput() }
        return if (heavy) sets.take(1) else sets
    }
    fun isDone(id: Long) = visibleSets(id).any { it.isComplete }

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
        val counts = exercises.map { ex -> ex to visibleSets(ex.id).count { it.isComplete } }
        val totalSets = counts.sumOf { it.second }
        val incomplete = exercises.sumOf { ex -> visibleSets(ex.id).count { !it.isEmpty && !it.isComplete } }
        AlertDialog(
            onDismissRequest = { showReview = false },
            title = { Text("Finish workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    counts.forEach { (ex, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (n > 0) "$n set${if (n == 1) "" else "s"}" else "skipped",
                                color = if (n > 0) MaterialTheme.colorScheme.secondary
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
                    if (totalSets == 0) {
                        Text("Nothing to log yet — enter weight and reps for at least one set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReview = false; vm.logWorkout() }, enabled = totalSets > 0) {
                    Text("Log workout")
                }
            },
            dismissButton = { TextButton(onClick = { showReview = false }) { Text("Keep going") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        WorkoutHeader(
            name = workout.name,
            startedAt = startedAt,
            onFinish = { showReview = true },
            onClose = { showCancel = true }
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            exercises.forEachIndexed { i, ex ->
                FilterChip(
                    selected = i == current,
                    onClick = { page = i },
                    label = { Text("${i + 1}. ${ex.name}", maxLines = 1) },
                    leadingIcon = if (isDone(ex.id)) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }
            AssistChip(
                onClick = { showPicker = true },
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (exercises.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No exercises yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Add the first exercise for this workout.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showPicker = true }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise")
                    }
                }
            } else {
                val ex = exercises[current]
                key(ex.id) {
                    ExercisePage(
                        exercise = ex,
                        sets = visibleSets(ex.id),
                        heavy = heavy,
                        unit = unit,
                        last = lastSessions[ex.id],
                        pr = prs[ex.id],
                        suggestion = suggestions[ex.id],
                        trackFailure = trackFailure,
                        recoveryDays = recoveryDays,
                        onWeight = { idx, v -> vm.updateSet(ex.id, idx, weight = v) },
                        onReps = { idx, v -> vm.updateSet(ex.id, idx, reps = v) },
                        onToggleFailure = { idx -> vm.toggleFailure(ex.id, idx) },
                        onForcedReps = { idx, d -> vm.adjustForcedReps(ex.id, idx, d) },
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

        Surface(tonalElevation = 3.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RestRow(vm)
                if (exercises.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { page = current - 1 },
                            enabled = current > 0,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Back") }
                        Button(
                            onClick = { if (isLast) showReview = true else page = current + 1 },
                            modifier = Modifier.weight(2f).height(48.dp)
                        ) { Text(if (isLast) "Review & finish" else "Next exercise", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header + clock
// ---------------------------------------------------------------------------------------------

@Composable
private fun WorkoutHeader(name: String, startedAt: Long?, onFinish: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            WorkoutClock(startedAt)
        }
        TextButton(onClick = onFinish) { Text("Finish") }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Discard workout") }
    }
}

/**
 * Elapsed time. Heavy Duty sessions are short and brutal, so it turns red past the target as a
 * nudge to wrap up - informational only, never blocks anything.
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
        Text("In progress", style = MaterialTheme.typography.labelMedium, color = muted)
        return
    }
    val over = elapsedMs / 60_000L >= WorkoutViewModel.SESSION_TARGET_MIN
    val tint = if (over) MaterialTheme.colorScheme.error else muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(
            formatClock(elapsedMs / 1000) +
                if (over) " · past ${WorkoutViewModel.SESSION_TARGET_MIN} min — wrap it up" else "",
            style = MaterialTheme.typography.labelMedium, color = tint
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The current exercise
// ---------------------------------------------------------------------------------------------

@Composable
private fun ExercisePage(
    exercise: Exercise,
    sets: List<SetInput>,
    heavy: Boolean,
    unit: WeightUnit,
    last: List<SetWithExercise>?,
    pr: PrInfo?,
    suggestion: Suggestion?,
    trackFailure: Boolean,
    recoveryDays: Int,
    onWeight: (Int, String) -> Unit,
    onReps: (Int, String) -> Unit,
    onToggleFailure: (Int) -> Unit,
    onForcedReps: (Int, Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onCopyLast: () -> Unit,
    onApplySuggestion: () -> Unit,
    onOpenProgress: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenProgress) {
                Icon(Icons.Filled.ShowChart, "Progress for ${exercise.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (exercise.cue.isNotBlank()) {
            Text(exercise.cue, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // What to beat. The suggestion is the hero; everything else is quiet supporting text.
        val daysSince = last?.firstOrNull()?.sessionDate?.let { daysAgo(it) }
        val recoveringDaysLeft = daysSince?.let { (recoveryDays - it).coerceAtLeast(0) } ?: 0
        if (suggestion != null || last != null || pr != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (suggestion != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Target", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(suggestion.describe(unit), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            FilledTonalButton(onClick = onApplySuggestion) { Text("Use weight") }
                        }
                    }
                    if (last != null) {
                        Text(
                            "Last time (${relativeDay(last.first().sessionDate)}): " +
                                last.joinToString(", ") { "${formatWeight(lbsToDisplay(it.weight, unit))}×${it.reps}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pr != null) {
                        Text(
                            "PR: ${formatWeight(lbsToDisplay(pr.weight, unit))} ${unit.label} × ${pr.reps}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Only worth a line when it matters: training before you've recovered.
                    if (recoveringDaysLeft > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.HourglassEmpty, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Still recovering · $recoveringDaysLeft day${if (recoveringDaysLeft == 1) "" else "s"} left",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // The sets: the thing you actually came here to fill in.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Set", Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Weight (${unit.label})", Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Reps", Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (trackFailure) {
                        Text("Failure", Modifier.width(48.dp), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                sets.forEachIndexed { idx, s ->
                    val lastSet = last?.getOrNull(idx)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${idx + 1}", Modifier.width(32.dp), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = s.weight,
                            onValueChange = { onWeight(idx, it) },
                            modifier = Modifier.weight(1.1f),
                            singleLine = true,
                            placeholder = { Text(lastSet?.let { formatWeight(lbsToDisplay(it.weight, unit)) } ?: unit.label) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = s.reps,
                            onValueChange = { onReps(idx, it) },
                            modifier = Modifier.weight(0.9f),
                            singleLine = true,
                            placeholder = { Text(lastSet?.reps?.toString() ?: "reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                        if (trackFailure) {
                            IconButton(onClick = { onToggleFailure(idx) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Filled.LocalFireDepartment,
                                    contentDescription = if (s.toFailure) "Marked to failure" else "Mark set as to failure",
                                    tint = if (s.toFailure) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    if (trackFailure && s.toFailure) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Forced reps (with a spot)", Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { onForcedReps(idx, -1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Remove, "Fewer forced reps", Modifier.size(18.dp))
                            }
                            Text("${s.forcedReps}", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 6.dp))
                            IconButton(onClick = { onForcedReps(idx, 1) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Add, "More forced reps", Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!heavy) {
                        TextButton(onClick = onAddSet, enabled = sets.size < WorkoutViewModel.MAX_SETS) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("Set")
                        }
                        TextButton(onClick = onRemoveSet, enabled = sets.size > 1) {
                            Icon(Icons.Filled.Remove, null, Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("Set")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (last != null) TextButton(onClick = onCopyLast) { Text("Copy last time") }
                }
            }
        }

        TextButton(onClick = onRemove) {
            Text("Remove from this workout", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Rest timer (compact; the length is set in Settings)
// ---------------------------------------------------------------------------------------------

@Composable
private fun RestRow(vm: WorkoutViewModel) {
    val endAt by vm.timerEndAt.collectAsState()
    val minutes by vm.timerMinutes.collectAsState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(endAt) {
        if (endAt == null) return@LaunchedEffect
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }

    val running = endAt != null
    val remainingSec = endAt?.let { ((it - now).coerceAtLeast(0L) + 999) / 1000 } ?: (minutes * 60L)
    val tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Timer, null, tint = tint)
        Text("Rest  %d:%02d".format(remainingSec / 60, remainingSec % 60),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint,
            modifier = Modifier.weight(1f))
        if (running) {
            TextButton(onClick = { vm.addTimerSeconds(30) }) { Text("+30s") }
            OutlinedButton(onClick = { vm.stopTimer() }) { Text("Stop") }
        } else {
            FilledTonalButton(onClick = { vm.startTimer() }) { Text("Start rest") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Picker for adding an exercise mid-workout
// ---------------------------------------------------------------------------------------------

@Composable
private fun PickExerciseDialog(
    available: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (available.isEmpty()) {
                    Text("Every exercise is already in this workout.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                available.forEach { ex ->
                    TextButton(onClick = { onPick(ex) }, modifier = Modifier.fillMaxWidth()) {
                        Text(ex.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("New exercise…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
