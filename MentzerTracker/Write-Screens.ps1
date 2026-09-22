# Write-Screens.ps1 — creates the three UI screens

$base = ".\app\src\main\java\com\example\mentzertracker\ui"
New-Item -ItemType Directory -Force -Path $base | Out-Null

function W($path, $content) {
    [System.IO.File]::WriteAllText((Join-Path $PWD $path), $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  wrote $path" -ForegroundColor Green
}

Write-Host "Writing UI screens..." -ForegroundColor Cyan

# ---------- TodayScreen.kt ----------
W "$base\TodayScreen.kt" @'
package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.SetInput
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(vm: WorkoutViewModel) {
    val exercises by vm.exercises.collectAsState()
    val inputs by vm.inputs.collectAsState()
    val templates by vm.templates.collectAsState()
    val heavy by vm.heavyDutyMode.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { RestReminder() }
            item { RestTimer() }

            item {
                Column {
                    Text("Templates", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { t ->
                            AssistChip(
                                onClick = { vm.loadTemplate(t.id) },
                                label = { Text(t.name) }
                            )
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Heavy Duty Mode (1 set to failure)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = heavy, onCheckedChange = { vm.toggleHeavyDuty() })
                }
            }

            items(exercises, key = { it.id }) { ex ->
                ExerciseCard(
                    exercise = ex,
                    sets = inputs[ex.id] ?: List(if (heavy) 1 else 3) { SetInput() },
                    onSetChange = { idx, w, r -> vm.updateSet(ex.id, idx, w, r) }
                )
            }

            item {
                Button(
                    onClick = { vm.logWorkout() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("LOG WORKOUT", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RestReminder() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "REST 3-5 MIN BETWEEN SETS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Optimized for hypertrophy. Train to failure - one all-out set is the Mentzer ideal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RestTimer() {
    var selectedMin by remember { mutableIntStateOf(3) }
    var remaining by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000L)
            remaining -= 1
        }
        if (remaining == 0) running = false
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rest Timer", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "%d:%02d".format(remaining / 60, remaining % 60),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { m ->
                    FilterChip(
                        selected = selectedMin == m,
                        onClick = { selectedMin = m; if (!running) remaining = m * 60 },
                        label = { Text("${m}m") }
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (running) running = false
                    else {
                        if (remaining <= 0) remaining = selectedMin * 60
                        running = true
                    }
                }) { Text(if (running) "Stop" else "Start") }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    sets: List<SetInput>,
    onSetChange: (Int, String?, String?) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                exercise.cue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Set", Modifier.width(36.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Weight (lbs)", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Reps", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))

            sets.forEachIndexed { idx, s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${idx + 1}", Modifier.width(36.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = s.weight,
                        onValueChange = { onSetChange(idx, it, null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("lbs") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = s.reps,
                        onValueChange = { onSetChange(idx, null, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }
}
'@

# ---------- HistoryScreen.kt ----------
W "$base\HistoryScreen.kt" @'
package com.example.mentzertracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: WorkoutViewModel, context: Context) {
    val history by vm.history.collectAsState()
    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy - h:mm a", Locale.getDefault()) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No workouts logged yet.\nLog one from the Today tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val grouped = history.groupBy { it.sessionDate }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(onClick = {
                        val path = vm.exportCsv(context)
                        if (path != null) Toast.makeText(
                            context, "CSV saved: $path", Toast.LENGTH_LONG
                        ).show()
                    }) { Text("Export CSV") }
                    Spacer(Modifier.height(8.dp))
                }

                grouped.forEach { (date, sets) ->
                    item(key = "h_$date") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                fmt.format(Date(date)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.deleteSession(date) }) {
                                Icon(Icons.Filled.Delete, "Delete session")
                            }
                        }
                    }
                    val byExercise = sets.groupBy { it.exerciseId }
                    items(
                        byExercise.values.toList(),
                        key = { "e_${date}_${it.first().exerciseId}" }
                    ) { exSets ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(exSets.first().exerciseName, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                exSets.sortedBy { it.setNumber }.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Set ${s.setNumber}:  ${s.reps} reps @ ${formatWeight(s.weight)} lbs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { vm.deleteSet(s.id) }) {
                                            Icon(
                                                Icons.Filled.Close, "Delete set",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun formatWeight(w: Float): String =
    if (w % 1f == 0f) w.toInt().toString() else w.toString()
'@

# ---------- PRsScreen.kt ----------
W "$base\PRsScreen.kt" @'
package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PRsScreen(vm: WorkoutViewModel) {
    val prs by vm.prs.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Personal Records",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Auto-tracked when you log a heavier set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        val prMap = prs.associateBy { it.exerciseId }
        items(exercises, key = { it.id }) { ex ->
            val pr = prMap[ex.id]
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pr != null) {
                        Icon(
                            Icons.Filled.EmojiEvents, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        if (pr == null) {
                            Text(
                                "No PR yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "${formatW(pr.maxWeight)} lbs x ${pr.repsAtMaxWeight} reps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Achieved ${fmt.format(Date(pr.dateAchieved))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatW(w: Float): String =
    if (w % 1f == 0f) w.toInt().toString() else w.toString()
'@

Write-Host ""
Write-Host "Done! 3 UI screens written." -ForegroundColor Cyan