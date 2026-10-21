@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.SetInput
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import com.example.mentzertracker.data.SetWithExercise
import com.example.mentzertracker.util.*
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(vm: WorkoutViewModel, onOpenProgress: (Long) -> Unit) {
    val allExercises by vm.exercises.collectAsState()
    val inputs by vm.inputs.collectAsState()
    val templates by vm.templates.collectAsState()
    val heavy by vm.heavyDutyMode.collectAsState()
    val selectedTemplateId by vm.selectedTemplateId.collectAsState()
    val templateIds by vm.templateExerciseIds.collectAsState()
    val unit by vm.weightUnit.collectAsState()
    val lastSessions by vm.lastSessions.collectAsState()
    val history by vm.history.collectAsState()
    val trackFailure by vm.trackFailure.collectAsState()
    val recoveryDays by vm.recoveryDays.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val sessionStart by vm.sessionStartedAt.collectAsState()

    var showAddExercise by remember { mutableStateOf(false) }
    var showAddTemplate by remember { mutableStateOf(false) }

    // Template order is respected (previously the global exercise order was used).
    val visibleExercises = remember(allExercises, templateIds) {
        val ids = templateIds
        if (ids == null) allExercises
        else { val byId = allExercises.associateBy { it.id }; ids.mapNotNull { byId[it] } }
    }
    val readySets = remember(inputs, heavy) {
        inputs.values.sumOf { sets -> (if (heavy) sets.take(1) else sets).count { it.isComplete } }
    }
    val hasAnyInput = remember(inputs) { inputs.values.any { sets -> sets.any { !it.isEmpty } } }
    val lastWorkout = history.firstOrNull()?.sessionDate

    if (showAddExercise) {
        AddExerciseDialog(
            onDismiss = { showAddExercise = false },
            onConfirm = { name, cue -> vm.addExercise(name, cue); showAddExercise = false }
        )
    }
    if (showAddTemplate) {
        AddTemplateDialog(
            exercises = allExercises,
            onDismiss = { showAddTemplate = false },
            onConfirm = { name, ids -> vm.addTemplate(name, ids); showAddTemplate = false }
        )
    }

    // Avoids flashing the first-run guide for a split second at startup, before history loads.
    var historyLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(500); historyLoaded = true }
    val showGuide = historyLoaded && history.isEmpty() && !hasAnyInput

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ScreenTitle(
                    title = "Today",
                    subtitle = if (lastWorkout == null) null
                               else "Last workout ${relativeDay(lastWorkout)}"
                )
            }

            if (showGuide) item(key = "guide") { FirstRunGuide() }

            // 1) Pick the workout. Chips first: it's the first decision of every session.
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTemplateId == null,
                        onClick = { vm.clearTemplateFilter() },
                        label = { Text("All") }
                    )
                    templates.forEach { t ->
                        FilterChip(
                            selected = selectedTemplateId == t.id,
                            onClick = { vm.selectTemplate(t.id) },
                            label = { Text(t.name) }
                        )
                    }
                    AssistChip(
                        onClick = { showAddTemplate = true },
                        label = { Text("New") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }
                    )
                }
            }

            // 2) One quiet line for the mode, instead of a whole card.
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggleHeavyDuty() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Heavy Duty mode", modifier = Modifier.weight(1f))
                    Text(
                        if (heavy) "1 all-out set each" else "multiple sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Switch(checked = heavy, onCheckedChange = { vm.toggleHeavyDuty() })
                }
            }

            sessionStart?.let { start ->
                item(key = "session_clock") { SessionClockCard(start, onReset = { vm.resetSession() }) }
            }

            item { RestTimerCard(vm) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Exercises", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (hasAnyInput) TextButton(onClick = { vm.clearInputs() }) { Text("Clear") }
                    TextButton(onClick = { showAddExercise = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Exercise")
                    }
                }
            }

            if (visibleExercises.isEmpty()) {
                item {
                    EmptyMessage(
                        if (selectedTemplateId != null) "This template has no exercises.\nEdit it in Settings \u2192 Manage Templates."
                        else "No exercises yet. Tap \"+ Exercise\" to add one."
                    )
                }
            }

            items(visibleExercises, key = { it.id }) { ex ->
                val sets = inputs[ex.id] ?: List(WorkoutViewModel.DEFAULT_SETS) { SetInput() }
                ExerciseCard(
                    exercise = ex,
                    sets = if (heavy) sets.take(1) else sets,
                    heavy = heavy,
                    unit = unit,
                    last = lastSessions[ex.id],
                    trackFailure = trackFailure,
                    recoveryDays = recoveryDays,
                    suggestion = suggestions[ex.id],
                    onApplySuggestion = { vm.applySuggestion(ex.id) },
                    onWeight = { idx, v -> vm.updateSet(ex.id, idx, weight = v) },
                    onReps = { idx, v -> vm.updateSet(ex.id, idx, reps = v) },
                    onToggleFailure = { idx -> vm.toggleFailure(ex.id, idx) },
                    onForcedReps = { idx, delta -> vm.adjustForcedReps(ex.id, idx, delta) },
                    onAddSet = { vm.addSet(ex.id) },
                    onRemoveSet = { vm.removeSet(ex.id) },
                    onFillLast = { vm.fillFromLast(ex.id) },
                    onOpenProgress = { onOpenProgress(ex.id) }
                )
            }
        }

        // Pinned so you never have to scroll back down to finish: appears once you've typed something.
        if (hasAnyInput) {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Button(
                    onClick = { vm.logWorkout() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (readySets > 0) "LOG WORKOUT \u00b7 $readySets SET${if (readySets == 1) "" else "S"}"
                        else "LOG WORKOUT",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Shown only until the very first workout is logged. */
@Composable
private fun FirstRunGuide() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Getting started", fontWeight = FontWeight.Bold)
            listOf(
                "Pick a template below (or stay on All)",
                "Enter the weight and reps you did",
                "Tap LOG WORKOUT at the bottom"
            ).forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Heavy Duty = one all-out set to failure per exercise. Switch it on below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Live workout stopwatch, one slim row. Heavy Duty sessions are short and brutal, so past the
 * target it turns red as a nudge to wrap up - informational only, never blocks anything.
 */
@Composable
private fun SessionClockCard(startedAt: Long, onReset: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedMs = now - startedAt
    if (elapsedMs > WorkoutViewModel.SESSION_STALE_MS) return  // abandoned; the next entry restarts it
    val over = elapsedMs / 60_000L >= WorkoutViewModel.SESSION_TARGET_MIN
    val accent = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, null, Modifier.size(20.dp), tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("Workout ${formatClock(elapsedMs / 1000)}", fontWeight = FontWeight.SemiBold, color = accent)
            if (over) {
                Text("  \u00b7 wrap it up", style = MaterialTheme.typography.bodySmall, color = accent)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("Reset") }
        }
    }
}

/** Rest timer in one row. Tap the minutes to cycle 3 -> 4 -> 5 min. */
@Composable
private fun RestTimerCard(vm: WorkoutViewModel) {
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

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null,
                tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Rest timer", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "%d:%02d".format(remainingSec / 60, remainingSec % 60),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            if (running) {
                OutlinedButton(onClick = { vm.addTimerSeconds(30) }) { Text("+30s") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.stopTimer() }) { Text("Stop") }
            } else {
                TextButton(onClick = { vm.setTimerMinutes(if (minutes >= 5) 3 else minutes + 1) }) {
                    Text("$minutes min")
                }
                Button(onClick = { vm.startTimer() }) { Text("Start") }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    sets: List<SetInput>,
    heavy: Boolean,
    unit: WeightUnit,
    last: List<SetWithExercise>?,
    trackFailure: Boolean,
    recoveryDays: Int,
    suggestion: Suggestion?,
    onApplySuggestion: () -> Unit,
    onWeight: (Int, String) -> Unit,
    onReps: (Int, String) -> Unit,
    onToggleFailure: (Int) -> Unit,
    onForcedReps: (Int, Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onFillLast: () -> Unit,
    onOpenProgress: () -> Unit
) {
    // The form cue is one tap away instead of always taking up a line.
    var showCue by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (exercise.cue.isNotBlank()) {
                    IconButton(onClick = { showCue = !showCue }) {
                        Icon(Icons.Filled.Info, if (showCue) "Hide form cue" else "Show form cue",
                            tint = if (showCue) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onOpenProgress) {
                    Icon(Icons.Filled.ShowChart, "Progress for ${exercise.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showCue && exercise.cue.isNotBlank()) {
                Text(exercise.cue, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
            }

            // Recovery only speaks up when you're still recovering (informational, never blocks
            // logging). "Recovered" is the default state, so it no longer needs a line of its own.
            val daysSinceTrained = last?.firstOrNull()?.sessionDate?.let { daysAgo(it) }
            if (daysSinceTrained != null && daysSinceTrained < recoveryDays) {
                val left = recoveryDays - daysSinceTrained
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Filled.HourglassEmpty, null, Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Recovering \u00b7 $left day${if (left == 1) "" else "s"} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // What to beat: one muted line for last time, one tappable chip for the target.
            if (last != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                ) {
                    Icon(Icons.Filled.History, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Last (${relativeDay(last.first().sessionDate)}): " +
                            last.joinToString(", ") { "${formatWeight(lbsToDisplay(it.weight, unit))}\u00d7${it.reps}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (suggestion != null) {
                AssistChip(
                    onClick = onApplySuggestion,
                    label = { Text(suggestion.describe(unit), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.TrendingUp, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.secondary,
                        leadingIconContentColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                Text("Set", Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Weight (${unit.label})", Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Reps", Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Labels the flame column so it's no longer a mystery icon.
                if (trackFailure) Text("Failure", Modifier.width(36.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            sets.forEachIndexed { idx, s ->
                val lastSet = last?.getOrNull(idx)
                Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}", Modifier.width(28.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = s.weight,
                        onValueChange = { onWeight(idx, it) },
                        modifier = Modifier.weight(1.1f),
                        singleLine = true,
                        placeholder = {
                            Text(lastSet?.let { formatWeight(lbsToDisplay(it.weight, unit)) } ?: unit.label)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = s.reps,
                        onValueChange = { onReps(idx, it) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        placeholder = { Text(lastSet?.reps?.toString() ?: "reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                    if (trackFailure) {
                        IconButton(onClick = { onToggleFailure(idx) }, modifier = Modifier.size(36.dp)) {
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
                        Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Forced reps (spotted)", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onForcedReps(idx, -1) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Remove, null, Modifier.size(16.dp))
                        }
                        Text("${s.forcedReps}", style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp))
                        IconButton(onClick = { onForcedReps(idx, 1) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
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
                if (last != null) TextButton(onClick = onFillLast) { Text("Use last") }
            }
        }
    }
}
