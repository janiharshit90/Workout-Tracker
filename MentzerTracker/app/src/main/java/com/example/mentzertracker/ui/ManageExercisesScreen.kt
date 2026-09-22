package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise

@Composable
fun ManageExercisesScreen(vm: WorkoutViewModel, onBack: () -> Unit) {
    val exercises by vm.exercises.collectAsState()
    val history by vm.history.collectAsState()
    val setCounts = remember(history) { history.groupingBy { it.exerciseId }.eachCount() }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }

    if (showAdd) {
        AddExerciseDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, cue -> vm.addExercise(name, cue); showAdd = false }
        )
    }
    editing?.let { ex ->
        AddExerciseDialog(
            title = "Edit Exercise", initialName = ex.name, initialCue = ex.cue, confirmLabel = "Save",
            onDismiss = { editing = null },
            onConfirm = { name, cue -> vm.updateExercise(ex.id, name, cue); editing = null }
        )
    }
    deleting?.let { ex ->
        val n = setCounts[ex.id] ?: 0
        ConfirmDialog(
            title = "Delete \"${ex.name}\"?",
            message = if (n > 0) "This permanently deletes $n logged set${if (n == 1) "" else "s"} for this exercise " +
                    "and removes it from all templates. Consider backing up first (Settings \u2192 Back up data)."
                else "It will also be removed from any templates.",
            onDismiss = { deleting = null },
            onConfirm = { vm.deleteExercise(ex.id); deleting = null }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Manage Exercises", "Arrows change the order everywhere in the app", onBack) }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Exercise")
            }
        }
        if (exercises.isEmpty()) item { EmptyMessage("No exercises yet.") }

        itemsIndexed(exercises, key = { _, ex -> ex.id }) { index, ex ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        IconButton(onClick = { vm.moveExercise(ex.id, -1) }, enabled = index > 0,
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Move ${ex.name} up")
                        }
                        IconButton(onClick = { vm.moveExercise(ex.id, +1) }, enabled = index < exercises.lastIndex,
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Move ${ex.name} down")
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(ex.name, fontWeight = FontWeight.SemiBold)
                        if (ex.cue.isNotBlank()) {
                            Text(ex.cue, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val n = setCounts[ex.id] ?: 0
                        Text("$n logged set${if (n == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = ex }) { Icon(Icons.Filled.Edit, "Edit ${ex.name}") }
                    IconButton(onClick = { deleting = ex }) { Icon(Icons.Filled.Delete, "Delete ${ex.name}") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
