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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.PrInfo
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
    val prs by vm.prs.collectAsState()
    val history by vm.history.collectAsState()

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(
                title = "Today",
                subtitle = if (lastWorkout == null) "No workouts logged yet"
                           else "Last workout ${relativeDay(lastWorkout)} \u00b7 recover fully between sessions"
            )
        }

        item { RestTimerCard(vm) }

        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Template", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAddTemplate = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
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
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Heavy Duty mode", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (heavy) "One all-out set to failure per exercise"
                            else "Multiple sets per exercise",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = heavy, onCheckedChange = { vm.toggleHeavyDuty() })
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exercises", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                pr = prs[ex.id],
                onWeight = { idx, v -> vm.updateSet(ex.id, idx, weight = v) },
                onReps = { idx, v -> vm.updateSet(ex.id, idx, reps = v) },
                onAddSet = { vm.addSet(ex.id) },
                onRemoveSet = { vm.removeSet(ex.id) },
                onFillLast = { vm.fillFromLast(ex.id) },
                onOpenProgress = { onOpenProgress(ex.id) }
            )
        }

        item {
            Button(
                onClick = { vm.logWorkout() },
                enabled = hasAnyInput,
                modifier = Modifier.fillMaxWidth().height(56.dp)
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
        item { Spacer(Modifier.height(24.dp)) }
    }
}

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
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rest timer", fontWeight = FontWeight.SemiBold)
                    Text("3\u20135 min between sets", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "%d:%02d".format(remainingSec / 60, remainingSec % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    Text("Vibrates when done, even on other tabs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.addTimerSeconds(30) }) { Text("+30s") }
                    Button(onClick = { vm.stopTimer() }) { Text("Stop") }
                } else {
                    listOf(3, 4, 5).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { vm.setTimerMinutes(m) },
                            label = { Text("${m}m") }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { vm.startTimer() }) { Text("Start") }
                }
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
    pr: PrInfo?,
    onWeight: (Int, String) -> Unit,
    onReps: (Int, String) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onFillLast: () -> Unit,
    onOpenProgress: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenProgress) {
                    Icon(Icons.Filled.ShowChart, "Progress for ${exercise.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (exercise.cue.isNotBlank()) {
                Text(exercise.cue, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp))
            }

            // What to beat: the core of progressive overload.
            if (last != null || pr != null) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(end = 8.dp)) {
                    if (last != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onFillLast)
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
                    if (pr != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("PR: ${formatWeight(lbsToDisplay(pr.weight, unit))} ${unit.label} \u00d7 ${pr.reps}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                Text("Set", Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Weight (${unit.label})", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Reps", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            sets.forEachIndexed { idx, s ->
                val lastSet = last?.getOrNull(idx)
                Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}", Modifier.width(32.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = s.weight,
                        onValueChange = { onWeight(idx, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text(lastSet?.let { formatWeight(lbsToDisplay(it.weight, unit)) } ?: unit.label)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = s.reps,
                        onValueChange = { onReps(idx, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(lastSet?.reps?.toString() ?: "reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
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
