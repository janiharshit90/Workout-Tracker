@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.billing.ProAccess
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.*
import kotlin.math.roundToInt

private const val TREND_WEEKS = 8
private const val CONSISTENCY_WEEKS = 12

/**
 * The big picture: how often you train, how long you rest, whether you're getting stronger across
 * the board, body weight, and your personal records (tap any exercise for its own chart).
 */
@Composable
fun StatsScreen(vm: WorkoutViewModel, onExerciseClick: (Long) -> Unit) {
    val history by vm.history.collectAsState()
    val prs by vm.prs.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val unit by vm.weightUnit.collectAsState()
    val bodyWeights by vm.bodyWeights.collectAsState()
    val scheme = MaterialTheme.colorScheme

    val isPro by ProAccess.isPro.collectAsState()
    var showPro by remember { mutableStateOf(false) }
    if (showPro) ProDialog(onDismiss = { showPro = false })
    var loggingWeight by remember { mutableStateOf(false) }
    if (loggingWeight) {
        BodyWeightDialog(
            unitLabel = unit.label,
            initial = bodyWeights.lastOrNull()?.let { formatWeight(lbsToDisplay(it.weightLbs, unit)) }.orEmpty(),
            onDismiss = { loggingWeight = false },
            onSave = { vm.logBodyWeight(it); loggingWeight = false }
        )
    }

    val now = remember(history) { System.currentTimeMillis() }
    val monthAgo = now - 30 * DAY_MILLIS
    val dates = remember(history) { sessionDates(history) }
    val perWeek = remember(dates) { workoutsPerWeek(dates, CONSISTENCY_WEEKS, now) }
    val gap = remember(dates) { averageGapDays(dates) }
    val avgSession = remember(history) { averageDurationSec(history, monthAgo) }
    val prsThisMonth = remember(history) { prEvents(history).count { it.date >= monthAgo } }
    val trends = remember(history) { strengthTrends(history, now - TREND_WEEKS * 7 * DAY_MILLIS) }
    val overall = remember(trends) { overallStrengthChange(trends) }
    val bw = remember(bodyWeights) { bodyWeightSummary(bodyWeights) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Stats", "Your training at a glance · tap an exercise for its chart") }

        if (dates.isEmpty()) {
            item { EmptyMessage("Log your first workout and your stats will show up here.") }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("WORKOUTS · 30 DAYS", "${dates.count { it >= monthAgo }}", Modifier.weight(1f))
                        StatTile("PRS · 30 DAYS", "$prsThisMonth", Modifier.weight(1f), highlight = prsThisMonth > 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("AVG REST BETWEEN", gap?.let { "${formatOneDecimal(it)} days" } ?: "—", Modifier.weight(1f))
                        StatTile("AVG SESSION", avgSession?.let { formatDuration(it) } ?: "—", Modifier.weight(1f))
                    }
                }
            }

            if (!isPro) item {
                ProLockedCard(
                    "Consistency & strength trends",
                    "Weekly consistency and how every lift is trending",
                    onUnlock = { showPro = true }
                )
            }
            if (isPro) item {
                Panel {
                    SectionHeading("Consistency · $CONSISTENCY_WEEKS weeks")
                    Spacer(Modifier.height(10.dp))
                    WeekBars(perWeek)
                }
            }

            if (isPro) item {
                Panel {
                    SectionHeading("Strength · last $TREND_WEEKS weeks")
                    if (overall == null) {
                        Text(
                            "Train the same exercise twice within $TREND_WEEKS weeks to see whether it's going up.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                            Text(formatPercent(overall), style = MaterialTheme.typography.headlineLarge,
                                color = if (overall > 0) scheme.primary else scheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                            Text("average change in estimated 1RM", style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        trends.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onExerciseClick(t.exerciseId) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${formatWeight(lbsToDisplay(t.firstE1rm, unit))} → " +
                                            "${formatWeight(lbsToDisplay(t.lastE1rm, unit))} ${unit.label} est. 1RM · ${t.sessions} sessions",
                                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    formatPercent(t.percentChange), style = MaterialTheme.typography.titleMedium,
                                    color = when {
                                        t.percentChange > 0.05 -> scheme.primary
                                        t.percentChange < -0.05 -> scheme.error
                                        else -> scheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isPro) item {
            ProLockedCard("Body weight", "Log your weight and see it next to your strength", onUnlock = { showPro = true })
        }
        if (isPro) item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeading("Body weight") }
                    TextButton(onClick = { loggingWeight = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("LOG")
                    }
                }
                if (bw == null) {
                    Text("Log your body weight now and then to see it next to your strength.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${formatWeight(lbsToDisplay(bw.latest.weightLbs, unit))} ${unit.label}",
                            style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(10.dp))
                        bw.changeLbs?.let { ch ->
                            val shown = lbsToDisplay(ch, unit)
                            Text(
                                (if (shown > 0) "+" else "") + formatWeight(shown) + " ${unit.label} since " +
                                    formatDate(bw.sinceDate!!, "MMM d"),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                    Text("Last logged ${relativeDay(bw.latest.date)}", style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                    if (bodyWeights.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            bodyWeights.map { lbsToDisplay(it.weightLbs, unit) },
                            bodyWeights.first().date, bodyWeights.last().date, unit.label, height = 120
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    bodyWeights.takeLast(5).reversed().forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDate(e.date, "EEE, MMM d"), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            Text("${formatWeight(lbsToDisplay(e.weightLbs, unit))} ${unit.label}",
                                style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { vm.deleteBodyWeight(e.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, "Delete this entry", Modifier.size(16.dp),
                                    tint = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Box(Modifier.padding(top = 8.dp)) { SectionLabel("Personal records") } }
        if (exercises.isEmpty()) item { EmptyMessage("No exercises yet.") }
        items(exercises, key = { it.id }) { ex ->
            val pr = prs[ex.id]
            Card(onClick = { onExerciseClick(ex.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null,
                        tint = if (pr != null) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        if (pr == null) {
                            Text("No sets logged yet", style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant)
                        } else {
                            Text("${formatWeight(lbsToDisplay(pr.weight, unit))} ${unit.label} × ${pr.reps}",
                                style = MaterialTheme.typography.titleMedium, color = scheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text("Est. 1RM ${formatWeight(lbsToDisplay(pr.e1rm, unit))} ${unit.label} · " +
                                    formatDate(pr.date, "MMM d, yyyy"),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatOneDecimal(d: Double): String {
    val r = (d * 10).roundToInt() / 10.0
    return if (r == Math.floor(r)) r.toInt().toString() else r.toString()
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(IronShape).background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, IronShape).padding(14.dp),
        content = content
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(IronShape).background(scheme.surface).border(1.dp, scheme.outlineVariant, IronShape)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1,
            color = if (highlight) scheme.primary else scheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp,
            color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun BodyWeightDialog(unitLabel: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LOG BODY WEIGHT") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = sanitizeWeightInput(it) },
                label = { Text("Weight ($unitLabel)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = (parseWeight(text) ?: 0f) > 0f) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}
