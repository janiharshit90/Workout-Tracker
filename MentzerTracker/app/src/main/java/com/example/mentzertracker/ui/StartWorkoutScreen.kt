@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.WorkoutTemplate
import com.example.mentzertracker.util.daysAgo
import com.example.mentzertracker.util.relativeDay

/** What a template card needs to show, precomputed so the list stays cheap to recompose. */
private data class TemplateCardInfo(
    val template: WorkoutTemplate,
    val exerciseNames: List<String>,
    /** > 0 means at least one exercise in it is still inside the recovery window. */
    val daysLeft: Int,
    /** Most recent time any of its exercises was trained; 0 = never. */
    val lastTrained: Long
)

/** The screen you see when no workout is in progress: pick one and tap to start. */
@Composable
fun StartWorkoutScreen(vm: WorkoutViewModel, onOpenPrinciples: () -> Unit) {
    val templates by vm.templates.collectAsState()
    val templateExercises by vm.templateExercises.collectAsState()
    val allExercises by vm.exercises.collectAsState()
    val lastSessions by vm.lastSessions.collectAsState()
    val history by vm.history.collectAsState()
    val heavy by vm.heavyDutyMode.collectAsState()
    val recoveryDays by vm.recoveryDays.collectAsState()
    val introDismissed by vm.introDismissed.collectAsState()

    var showAddTemplate by remember { mutableStateOf(false) }
    if (showAddTemplate) {
        AddTemplateDialog(
            exercises = allExercises,
            onDismiss = { showAddTemplate = false },
            onConfirm = { name, ids -> vm.addTemplate(name, ids); showAddTemplate = false }
        )
    }

    val infos = remember(templates, templateExercises, allExercises, lastSessions, recoveryDays) {
        val names = allExercises.associate { it.id to it.name }
        templates.map { t ->
            val ids = templateExercises[t.id].orEmpty().filter { it in names }
            val lastDates = ids.mapNotNull { lastSessions[it]?.firstOrNull()?.sessionDate }
            TemplateCardInfo(
                template = t,
                exerciseNames = ids.mapNotNull { names[it] },
                daysLeft = lastDates.maxOfOrNull { recoveryDays - daysAgo(it) }?.coerceAtLeast(0) ?: 0,
                lastTrained = lastDates.maxOrNull() ?: 0L
            )
        }
    }
    // Suggest the ready workout you've gone longest without (never-trained counts as longest).
    val suggestedId = remember(infos) {
        infos.filter { it.exerciseNames.isNotEmpty() && it.daysLeft == 0 }
            .minByOrNull { it.lastTrained }?.template?.id
    }
    val lastWorkout = history.firstOrNull()?.sessionDate

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(
                title = "Ready to train?",
                subtitle = if (lastWorkout == null) "Pick a workout to begin"
                           else "Last workout ${relativeDay(lastWorkout)}"
            )
        }

        if (!introDismissed && history.isEmpty()) {
            item { IntroCard(onDismiss = vm::dismissIntro, onPrinciples = onOpenPrinciples) }
        }

        item {
            Column {
                SectionLabel("Style")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = heavy, onClick = { if (!heavy) vm.toggleHeavyDuty() },
                        label = { Text("Heavy Duty · 1 set") })
                    FilterChip(selected = !heavy, onClick = { if (heavy) vm.toggleHeavyDuty() },
                        label = { Text("Several sets") })
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (heavy) "One all-out set to failure per exercise"
                    else "Log as many sets per exercise as you do",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("Choose a workout") }
                TextButton(onClick = { showAddTemplate = true }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }

        items(infos, key = { it.template.id }) { info ->
            WorkoutCard(
                info = info,
                suggested = info.template.id == suggestedId,
                onStart = { vm.startWorkout(info.template.id) }
            )
        }

        item {
            Card(
                onClick = { vm.startWorkout(null) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pick exercises as you go", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WorkoutCard(info: TemplateCardInfo, suggested: Boolean, onStart: () -> Unit) {
    Card(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (suggested) {
                        Spacer(Modifier.width(8.dp))
                        Text("Suggested", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    if (info.exerciseNames.isEmpty()) "No exercises yet — edit it in Settings"
                    else info.exerciseNames.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (info.exerciseNames.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val ready = info.daysLeft == 0
                    val tint = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ready) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty,
                            null, Modifier.size(14.dp), tint = tint)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (ready) "Ready" else "Recovering · ${info.daysLeft} day${if (info.daysLeft == 1) "" else "s"} left",
                            style = MaterialTheme.typography.labelMedium, color = tint
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.PlayArrow, "Start ${info.template.name}",
                Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IntroCard(onDismiss: () -> Unit, onPrinciples: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Heavy Duty in 3 steps", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("1.  Pick a workout below.")
            Text("2.  For each exercise, warm up, then take ONE set to true failure.")
            Text("3.  Rest until fully recovered (5+ days) before repeating it.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onPrinciples) { Text("Read the principles") }
                Button(onClick = onDismiss) { Text("Got it") }
            }
        }
    }
}
