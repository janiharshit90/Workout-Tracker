@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.SetWithExercise
import com.example.mentzertracker.util.*

private enum class Metric(val label: String, val description: String) {
    TOP("Top set", "Heaviest set per session"),
    E1RM("Est. 1RM", "Estimated one-rep max (Epley) \u2014 fairer when reps vary"),
    VOLUME("Volume", "Weight \u00d7 reps, summed per session")
}

private data class SessionPoint(
    val date: Long, val topWeight: Float, val topReps: Int,
    val e1rm: Float, val volume: Float, val sets: Int
)

@Composable
fun ProgressScreen(vm: WorkoutViewModel, exerciseId: Long, onBack: () -> Unit) {
    val history by vm.history.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val unit by vm.weightUnit.collectAsState()
    val exercise = exercises.find { it.id == exerciseId }
    var metric by rememberSaveable { mutableStateOf(Metric.TOP) }

    val points = remember(history, exerciseId) {
        history.filter { it.exerciseId == exerciseId }
            .groupBy { it.sessionDate }
            .map { (date, sets) ->
                val top = sets.maxWith(compareBy<SetWithExercise>({ it.weight }, { it.reps }))
                SessionPoint(
                    date = date, topWeight = top.weight, topReps = top.reps,
                    e1rm = sets.maxOf { estimated1RM(it.weight, it.reps) },
                    volume = sets.sumOf { (it.weight * it.reps).toDouble() }.toFloat(),
                    sets = sets.size
                )
            }
            .sortedBy { it.date }
    }
    val values = points.map { p ->
        lbsToDisplay(when (metric) { Metric.TOP -> p.topWeight; Metric.E1RM -> p.e1rm; Metric.VOLUME -> p.volume }, unit)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader(exercise?.name ?: "Progress", metric.description, onBack) }

        if (points.isEmpty()) {
            item { EmptyMessage("No sets logged for this exercise yet.") }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric.values().forEach { m ->
                    FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(m.label) })
                }
            }
        }
        item { StatsRow(values, unit) }
        item {
            if (points.size >= 2) ProgressChart(values, points.first().date, points.last().date, unit)
            else Text("Log one more session to see a trend line.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        item { Text("Sessions", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
        items(points.reversed(), key = { it.date }) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(p.date, "MMM d, yyyy"), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                Text("${formatWeight(lbsToDisplay(p.topWeight, unit))} \u00d7 ${p.topReps}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("${p.sets} set${if (p.sets == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatsRow(values: List<Float>, unit: WeightUnit) {
    val best = values.max()
    val latest = values.last()
    val change = latest - values.first()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCell("Best", formatWeight(best), unit.label)
        StatCell("Latest", formatWeight(latest), unit.label)
        StatCell("Sessions", "${values.size}", null)
        StatCell("Change", (if (change >= 0) "+" else "") + formatWeight(change), unit.label)
    }
}

@Composable
private fun StatCell(label: String, value: String, suffix: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (suffix != null) "$value $suffix" else value,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProgressChart(values: List<Float>, firstDate: Long, lastDate: Long, unit: WeightUnit) {
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).let { if (it < 1f) 1f else it }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("${formatWeight(maxV)} ${unit.label}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp, horizontal = 6.dp)) {
                val w = size.width
                val h = size.height
                val stepX = w / (values.size - 1)
                val points = values.mapIndexed { i, v -> Offset(i * stepX, h - ((v - minV) / range) * h) }

                for (i in 0..3) {
                    val y = h * i / 3
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }
                for (i in 0 until points.size - 1) {
                    drawLine(primary, points[i], points[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
                points.forEachIndexed { i, p ->
                    drawCircle(primary, radius = if (i == points.lastIndex) 6.dp.toPx() else 4.dp.toPx(), center = p)
                }
            }
            Text("${formatWeight(minV)} ${unit.label}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(formatDate(firstDate, "MMM d"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatDate(lastDate, "MMM d"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
