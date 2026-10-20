package com.example.mentzertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import com.example.mentzertracker.util.parseWeight
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

/** Exercises are added to the template in the order you tick them (shown as 1, 2, 3...). */
@Composable
fun AddTemplateDialog(
    exercises: List<Exercise>,
    title: String = "New Template",
    initialName: String = "",
    initialSelectedIds: List<Long> = emptyList(),
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (name: String, exerciseIds: List<Long>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val selected = remember { mutableStateListOf(*initialSelectedIds.toTypedArray()) }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, selected.toList()) },
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
    initialForcedReps: Int,
    unitLabel: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (weight: String, reps: String, toFailure: Boolean, forcedReps: Int) -> Unit
) {
    var weight by remember { mutableStateOf(initialWeight) }
    var reps by remember { mutableStateOf(initialReps) }
    var toFailure by remember { mutableStateOf(initialToFailure) }
    var forced by remember { mutableStateOf(initialForcedReps) }
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
                    Modifier.fillMaxWidth().clickable { toFailure = !toFailure; if (!toFailure) forced = 0 },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment, null, Modifier.size(20.dp),
                        tint = if (toFailure) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Taken to failure", Modifier.weight(1f))
                    Switch(checked = toFailure, onCheckedChange = { toFailure = it; if (!it) forced = 0 })
                }
                if (toFailure) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Forced reps (with a spot)", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = { forced = (forced - 1).coerceAtLeast(0) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Remove, "Fewer forced reps", Modifier.size(18.dp))
                        }
                        Text("$forced", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(
                            onClick = { forced = (forced + 1).coerceAtMost(WorkoutViewModel.MAX_FORCED_REPS) },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Filled.Add, "More forced reps", Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weight, reps, toFailure, if (toFailure) forced else 0) }, enabled = valid) {
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
