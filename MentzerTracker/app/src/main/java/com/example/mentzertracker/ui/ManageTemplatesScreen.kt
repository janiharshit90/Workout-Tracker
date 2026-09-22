package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.WorkoutTemplate

@Composable
fun ManageTemplatesScreen(vm: WorkoutViewModel, onBack: () -> Unit) {
    val templates by vm.templates.collectAsState()
    val exercises by vm.exercises.collectAsState()
    // Reactive map, so the edit dialog opens pre-filled instantly (no async gap anymore).
    val templateExercises by vm.templateExercises.collectAsState()
    val names = remember(exercises) { exercises.associate { it.id to it.name } }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var deleting by remember { mutableStateOf<WorkoutTemplate?>(null) }

    if (showAdd) {
        AddTemplateDialog(
            exercises = exercises,
            onDismiss = { showAdd = false },
            onConfirm = { name, ids -> vm.addTemplate(name, ids); showAdd = false }
        )
    }
    editing?.let { t ->
        AddTemplateDialog(
            exercises = exercises,
            title = "Edit Template",
            initialName = t.name,
            initialSelectedIds = templateExercises[t.id].orEmpty(),
            confirmLabel = "Save",
            onDismiss = { editing = null },
            onConfirm = { name, ids -> vm.updateTemplate(t.id, name, ids); editing = null }
        )
    }
    deleting?.let { t ->
        ConfirmDialog(
            title = "Delete \"${t.name}\"?",
            message = "This only removes the template. Your logged history stays intact.",
            onDismiss = { deleting = null },
            onConfirm = { vm.deleteTemplate(t.id); deleting = null }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Manage Templates", onBack = onBack) }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Template")
            }
        }
        if (templates.isEmpty()) item { EmptyMessage("No templates yet.") }

        items(templates, key = { it.id }) { t ->
            val exNames = templateExercises[t.id].orEmpty().mapNotNull { names[it] }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (exNames.isEmpty()) "No exercises" else exNames.joinToString(" \u2192 "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editing = t }) { Icon(Icons.Filled.Edit, "Edit ${t.name}") }
                    IconButton(onClick = { deleting = t }) { Icon(Icons.Filled.Delete, "Delete ${t.name}") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
