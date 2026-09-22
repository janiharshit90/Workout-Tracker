@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.util.*

@Composable
fun PRsScreen(vm: WorkoutViewModel, onExerciseClick: (Long) -> Unit) {
    val prs by vm.prs.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val unit by vm.weightUnit.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle("Personal Records",
                "Heaviest set per exercise, updated live from your history. Tap for progress.")
        }
        if (exercises.isEmpty()) item { EmptyMessage("No exercises yet.") }

        items(exercises, key = { it.id }) { ex ->
            val pr = prs[ex.id]
            Card(onClick = { onExerciseClick(ex.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null,
                        tint = if (pr != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        if (pr == null) {
                            Text("No sets logged yet", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("${formatWeight(lbsToDisplay(pr.weight, unit))} ${unit.label} \u00d7 ${pr.reps}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("Est. 1RM ${formatWeight(lbsToDisplay(pr.e1rm, unit))} ${unit.label} \u00b7 " +
                                    formatDate(pr.date, "MMM d, yyyy"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
