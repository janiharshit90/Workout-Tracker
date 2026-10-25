@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.SetWithExercise
import com.example.mentzertracker.util.*
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Composable
fun HistoryScreen(vm: WorkoutViewModel, onOpenProgress: (Long) -> Unit) {
    val history by vm.history.collectAsState()
    val unit by vm.weightUnit.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<SetWithExercise?>(null) }
    editing?.let { s ->
        EditSetDialog(
            title = "${s.exerciseName} \u00b7 set ${s.setNumber}",
            initialWeight = formatWeight(lbsToDisplay(s.weight, unit)),
            initialReps = s.reps.toString(),
            initialToFailure = s.toFailure,
            initialForcedReps = s.forcedReps,
            initialWarmup = s.isWarmup,
            initialNotes = s.notes,
            unitLabel = unit.label,
            onDismiss = { editing = null },
            onDelete = { vm.deleteSet(s.id); editing = null },
            onConfirm = { w, r, f, fr, wu, n -> vm.updateLoggedSet(s.id, w, r, f, fr, wu, n); editing = null }
        )
    }

    // history is newest-first; groupBy keeps that order.
    val sessions = remember(history) { history.groupBy { it.sessionDate }.toList() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenTitle(
                title = "History",
                subtitle = if (sessions.isEmpty()) null else "${sessions.size} sessions \u00b7 tap a set to edit"
            ) {
                if (sessions.isNotEmpty()) {
                    IconButton(onClick = {
                        scope.launch {
                            vm.exportCsvFile()?.let { shareFile(ctx, it, "text/csv", "Share workout history") }
                        }
                    }) { Icon(Icons.Filled.Share, "Export CSV") }
                }
            }
        }

        if (sessions.isEmpty()) {
            item { EmptyMessage("No workouts logged yet.\nLog one from the Today tab.") }
        }

        sessions.forEach { (date, sets) ->
            item(key = "h_$date") {
                val volume = sets.sumOf { (lbsToDisplay(it.weight, unit) * it.reps).toDouble() }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(formatDate(date, "EEE, MMM d yyyy \u00b7 h:mm a"),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("${sets.map { it.exerciseId }.distinct().size} exercises \u00b7 ${sets.size} sets \u00b7 " +
                                "%,d ${unit.label} volume".format(volume.roundToLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Session length (0 = unknown, e.g. sessions logged before this existed).
                        val durationSec = sets.maxOf { it.durationSec }
                        if (durationSec > 0) {
                            val long = durationSec / 60 >= WorkoutViewModel.SESSION_TARGET_MIN
                            val tint = if (long) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Timer, null, Modifier.size(12.dp), tint = tint)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatDuration(durationSec) +
                                        if (long) " \u00b7 over the ${WorkoutViewModel.SESSION_TARGET_MIN}-min target" else "",
                                    style = MaterialTheme.typography.labelSmall, color = tint
                                )
                            }
                        }
                    }
                    IconButton(onClick = { vm.deleteSession(date) }) {
                        Icon(Icons.Filled.Delete, "Delete session")
                    }
                }
            }
            val byExercise = sets.groupBy { it.exerciseId }.values.toList()
            items(byExercise, key = { "e_${date}_${it.first().exerciseId}" }) { exSets ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProgress(exSets.first().exerciseId) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(exSets.first().exerciseName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, "Progress", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        exSets.firstOrNull { it.notes.isNotBlank() }?.let { n ->
                            Text(n.notes, style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                        }
                        exSets.sortedBy { it.setNumber }.forEach { s ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = s }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (s.isWarmup) "Warm-up" else "Set ${s.setNumber}", Modifier.width(64.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatWeight(lbsToDisplay(s.weight, unit))} ${unit.label} \u00d7 ${s.reps}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f))
                                if (s.toFailure) {
                                    Icon(Icons.Filled.LocalFireDepartment, "To failure",
                                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    if (s.forcedReps > 0) {
                                        Text(" +${s.forcedReps}", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error)
                                    }
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
