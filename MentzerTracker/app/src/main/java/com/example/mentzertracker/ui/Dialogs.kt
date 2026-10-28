package com.example.mentzertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import com.example.mentzertracker.util.BeyondFailure
import com.example.mentzertracker.util.WeightUnit
import com.example.mentzertracker.util.formatWeight
import com.example.mentzertracker.util.parseWeight
import com.example.mentzertracker.util.stepChoices
import com.example.mentzertracker.util.weightIncrement
import com.example.mentzertracker.util.sanitizeRepsInput
import com.example.mentzertracker.util.sanitizeWeightInput

@Composable
fun AddExerciseDialog(
    title: String = "New Exercise",
    initialName: String = "",
    initialCue: String = "",
    confirmLabel: String = "Add",
    onDismiss: () -> Unit,
    onConfirm: (name: String, cue: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var cue by remember { mutableStateOf(initialCue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text("Exercise name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cue, onValueChange = { cue = it.take(200) },
                    label = { Text("Form cue (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, cue) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Exercises are added to the template in the order you tick them (shown as 1, 2, 3...). Once two or
 * more are picked, each can be linked to the next as a superset (no rest between them).
 */
@Composable
fun AddTemplateDialog(
    exercises: List<Exercise>,
    title: String = "New Template",
    initialName: String = "",
    initialSelectedIds: List<Long> = emptyList(),
    initialSupersets: Set<Long> = emptySet(),
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (name: String, exerciseIds: List<Long>, supersets: Set<Long>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val selected = remember { mutableStateListOf(*initialSelectedIds.toTypedArray()) }
    val linked = remember { mutableStateListOf(*initialSupersets.toTypedArray()) }
    val names = remember(exercises) { exercises.associate { it.id to it.name } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text("Template name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Exercises (tick in the order you'll do them)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    exercises.forEach { ex ->
                        val position = selected.indexOf(ex.id)
                        val isSelected = position >= 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (isSelected) selected.remove(ex.id) else selected.add(ex.id) }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { if (it) selected.add(ex.id) else selected.remove(ex.id) }
                            )
                            Text(ex.name, Modifier.weight(1f))
                            if (isSelected) {
                                Text("${position + 1}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }
                if (selected.size >= 2) {
                    Spacer(Modifier.height(10.dp))
                    Text("Order & supersets (arrows to reorder; tap a link for no rest into the next one)",
                        style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        selected.toList().forEachIndexed { i, id ->
                            fun move(delta: Int) {
                                val (ids, ss) = com.example.mentzertracker.util.moveExercise(selected.toList(), linked.toSet(), i, delta)
                                selected.clear(); selected.addAll(ids)
                                linked.clear(); linked.addAll(ss)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}. ${names[id].orEmpty()}", style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f).padding(vertical = 2.dp))
                                IconButton(onClick = { move(-1) }, enabled = i > 0, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Move ${names[id].orEmpty()} up", Modifier.size(18.dp))
                                }
                                IconButton(onClick = { move(1) }, enabled = i < selected.lastIndex, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Move ${names[id].orEmpty()} down", Modifier.size(18.dp))
                                }
                            }
                            if (i < selected.lastIndex) {
                                val on = id in linked
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { if (on) linked.remove(id) else linked.add(id) }
                                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (on) Icons.Filled.Link else Icons.Filled.LinkOff, null, Modifier.size(16.dp),
                                        tint = if (on) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (on) "SUPERSET · NO REST" else "rest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (on) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ids = selected.toList()
                    onConfirm(name, ids, linked.filter { it in ids && it != ids.last() }.toSet())
                },
                enabled = name.isNotBlank() && selected.isNotEmpty()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditSetDialog(
    title: String,
    initialWeight: String,
    initialReps: String,
    initialToFailure: Boolean,
    initialBeyond: BeyondFailure,
    initialWarmup: Boolean,
    initialNotes: String,
    unitLabel: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (weight: String, reps: String, toFailure: Boolean, beyond: BeyondFailure, isWarmup: Boolean, notes: String) -> Unit
) {
    var weight by remember { mutableStateOf(initialWeight) }
    var reps by remember { mutableStateOf(initialReps) }
    var toFailure by remember { mutableStateOf(initialToFailure) }
    var beyond by remember { mutableStateOf(initialBeyond) }
    var warmup by remember { mutableStateOf(initialWarmup) }
    var notes by remember { mutableStateOf(initialNotes) }
    val valid = parseWeight(weight) != null && (reps.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = sanitizeWeightInput(it) },
                        label = { Text("Weight ($unitLabel)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = sanitizeRepsInput(it) },
                        label = { Text("Reps") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { warmup = !warmup; if (warmup) { toFailure = false; beyond = BeyondFailure() } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Warm-up set")
                        Text("Not counted toward PRs or progression", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = warmup, onCheckedChange = { warmup = it; if (it) { toFailure = false; beyond = BeyondFailure() } })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { toFailure = !toFailure; if (!toFailure) beyond = BeyondFailure(); if (toFailure) warmup = false },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment, null, Modifier.size(20.dp),
                        tint = if (toFailure) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Taken to failure", Modifier.weight(1f))
                    Switch(checked = toFailure, onCheckedChange = { toFailure = it; if (!it) beyond = BeyondFailure(); if (it) warmup = false })
                }
                if (toFailure) {
                    BeyondFailurePanel(
                        beyond, onNudge = { t, d -> beyond = beyond.nudge(t, d) },
                        modifier = Modifier.padding(top = 8.dp), compact = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it.take(WorkoutViewModel.MAX_NOTE_LENGTH) },
                    label = { Text("Note (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 1, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weight, reps, toFailure, if (toFailure) beyond else BeyondFailure(), warmup, notes) }, enabled = valid) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    destructive: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Pick an exercise to add to a workout (preview or in progress), or create a new one. */
@Composable
fun PickExerciseDialog(
    available: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADD EXERCISE") },
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
        confirmButton = { TextButton(onClick = onNew) { Text("NEW EXERCISE…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}


/**
 * Pick how much weight to add when this exercise progresses (and what the +/- buttons step by).
 * Light isolation moves want small jumps; big compound lifts take bigger ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepDialog(
    exerciseName: String,
    unit: WeightUnit,
    currentStep: Float,
    onDismiss: () -> Unit,
    onPick: (Float?) -> Unit
) {
    val choices = stepChoices(unit)
    val default = weightIncrement(unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weight jump") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "How much to add to $exerciseName when you hit the top of your rep range. " +
                        "It's also what the \u2212/+ buttons step by.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { c ->
                        FilterChip(
                            selected = kotlin.math.abs(currentStep - c) < 0.001f,
                            onClick = { onPick(if (c == default) null else c) },
                            label = { Text("${formatWeight(c)} ${unit.label}") }
                        )
                    }
                }
                Text("Default: ${formatWeight(default)} ${unit.label}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
