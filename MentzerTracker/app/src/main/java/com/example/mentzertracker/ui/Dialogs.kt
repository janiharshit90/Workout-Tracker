package com.example.mentzertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    unitLabel: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (weight: String, reps: String) -> Unit
) {
    var weight by remember { mutableStateOf(initialWeight) }
    var reps by remember { mutableStateOf(initialReps) }
    val valid = parseWeight(weight) != null && (reps.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(weight, reps) }, enabled = valid) { Text("Save") }
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
