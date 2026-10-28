@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.WorkoutPreview
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.*

/**
 * Look before you commit: what's in this workout, what to beat on each exercise, and the style
 * (one set / several). Nothing is started - and no clock runs - until you tap Start.
 */
@Composable
fun WorkoutPreviewScreen(vm: WorkoutViewModel, preview: WorkoutPreview) {
    val allExercises by vm.exercises.collectAsState()
    val heavy by vm.heavyDutyMode.collectAsState()
    val unit by vm.weightUnit.collectAsState()
    val lastSessions by vm.lastSessions.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val recoveryDays by vm.recoveryDays.collectAsState()
    val repRange by vm.repRange.collectAsState()
    val lastNotes by vm.lastNotes.collectAsState()
    val stalls by vm.stalls.collectAsState()

    BackHandler { vm.closePreview() }

    val exercises = remember(preview, allExercises) {
        val byId = allExercises.associateBy { it.id }
        preview.exerciseIds.mapNotNull { byId[it] }
    }

    var showPicker by remember { mutableStateOf(false) }
    var showNewExercise by remember { mutableStateOf(false) }
    if (showPicker) {
        PickExerciseDialog(
            available = allExercises.filter { it.id !in preview.exerciseIds },
            onPick = { ex -> vm.previewAdd(ex.id); showPicker = false },
            onNew = { showPicker = false; showNewExercise = true },
            onDismiss = { showPicker = false }
        )
    }
    if (showNewExercise) {
        AddExerciseDialog(
            onDismiss = { showNewExercise = false; showPicker = true },
            onConfirm = { name, cue -> vm.addExercise(name, cue); showNewExercise = false; showPicker = true }
        )
    }

    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closePreview() }) { Icon(Icons.Filled.ArrowBack, "Back to workouts") }
            Text("WORKOUT PREVIEW", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp, color = scheme.onSurfaceVariant)
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        preview.name.uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp, lineHeight = 50.sp)
                    )
                    Text(
                        when (exercises.size) {
                            0 -> "No exercises yet"
                            1 -> "1 exercise"
                            else -> "${exercises.size} exercises"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (preview.pastDate != null) {
                item {
                    Text(
                        "LOGGING FOR ${formatDate(preview.pastDate, "EEEE, MMM d").uppercase()} \u00b7 NO TIMER",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp, color = scheme.primary
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StyleSwitch(
                        heavy = heavy,
                        onHeavy = { if (!heavy) vm.toggleHeavyDuty() },
                        onSeveral = { if (heavy) vm.toggleHeavyDuty() }
                    )
                    Text(
                        if (heavy) "One all-out set to failure per exercise"
                        else "Log as many sets per exercise as you do",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                    )
                }
            }

            if (exercises.isEmpty()) {
                item {
                    Text(
                        "Add exercises now, or start and add them as you go.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(exercises, key = { _, ex -> ex.id }) { index, ex ->
                val last = lastSessions[ex.id]
                val suggestion = suggestions[ex.id]
                val daysSince = last?.firstOrNull()?.sessionDate?.let { daysAgo(it) }
                val recoveringLeft = daysSince?.let { (recoveryDays - it).coerceAtLeast(0) } ?: 0
                val linked = linksToNext(preview.exerciseIds, preview.supersets, ex.id)

                Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(IronShape)
                        .background(scheme.surface)
                        .border(1.dp, scheme.outlineVariant, IronShape)
                        .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            suggestion != null -> Text(
                                suggestion.label().uppercase() + "  " + suggestion.target(unit),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                color = scheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            last == null -> Text(
                                "FIRST TIME · find a ${repRange.first}–${repRange.last} rep weight",
                                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant
                            )
                        }
                        if (last != null) {
                            Text(
                                "Last (${relativeDay(last.first().sessionDate)}): " +
                                    last.joinToString(", ") { "${formatWeight(lbsToDisplay(it.weight, unit))}×${it.reps}" },
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        stalls[ex.id]?.let { StallNotice(it, unit, stepFor(ex.stepLbs, unit), recoveryDays, compact = true) }
                        lastNotes[ex.id]?.let { n ->
                            Text("Note: ${n.text}", style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (recoveringLeft > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.HourglassEmpty, null, Modifier.size(13.dp), tint = scheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "RECOVERING · $recoveringLeft DAY${if (recoveringLeft == 1) "" else "S"} LEFT",
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp, color = scheme.error
                                )
                            }
                        }
                    }
                    IconButton(onClick = { vm.previewRemove(ex.id) }) {
                        Icon(Icons.Filled.Close, "Remove ${ex.name} from this workout", tint = scheme.onSurfaceVariant)
                    }
                }
                if (linked) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, null, Modifier.size(14.dp), tint = scheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("SUPERSET · NO REST INTO THE NEXT ONE", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = scheme.primary)
                    }
                }
                }
            }

            item {
                val line = scheme.outline
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(IronShape)
                        .clickable { showPicker = true }
                        .drawBehind {
                            drawRoundRect(
                                color = line,
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                            )
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(12.dp))
                    Text("ADD EXERCISE", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Column(Modifier.background(scheme.background)) {
            Divider(color = scheme.outlineVariant)
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { vm.startWorkout() },
                    shape = IronShape,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (preview.pastDate != null) "START LOGGING" else "START WORKOUT", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
                }
                Text(if (preview.pastDate != null) "Saved on that day \u2014 no clock runs" else "The clock starts when you tap Start",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Two big segments instead of chips: this is the one setting that shapes the whole workout. */
@Composable
private fun StyleSwitch(heavy: Boolean, onHeavy: () -> Unit, onSeveral: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(IronShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, IronShape)
    ) {
        StyleSegment("One set to failure", selected = heavy, onClick = onHeavy, modifier = Modifier.weight(1f))
        StyleSegment("Several sets", selected = !heavy, onClick = onSeveral, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StyleSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
