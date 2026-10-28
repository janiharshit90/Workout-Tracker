package com.example.mentzertracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.util.formatDate
import com.example.mentzertracker.util.formatWeight

/** A simple trend line with min/max labels and the first/last date underneath. Needs 2+ values. */
@Composable
fun LineChart(values: List<Float>, firstDate: Long, lastDate: Long, unitLabel: String, height: Int = 180) {
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).let { if (it < 1f) 1f else it }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("${formatWeight(maxV)} $unitLabel", style = MaterialTheme.typography.labelSmall, color = muted)
            Canvas(Modifier.fillMaxWidth().height(height.dp).padding(vertical = 8.dp, horizontal = 6.dp)) {
                val w = size.width
                val h = size.height
                val stepX = w / (values.size - 1).coerceAtLeast(1)
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
            Text("${formatWeight(minV)} $unitLabel", style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(formatDate(firstDate, "MMM d"), style = MaterialTheme.typography.labelSmall, color = muted)
                Spacer(Modifier.weight(1f))
                Text(formatDate(lastDate, "MMM d"), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/** Workouts per week as bars, oldest on the left, this week on the right; empty weeks show a thin line. */
@Composable
fun WeekBars(counts: List<Int>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val top = (counts.maxOrNull() ?: 0).coerceAtLeast(2)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val n = counts.size.coerceAtLeast(1)
            val gap = 6.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            counts.forEachIndexed { i, c ->
                val x = i * (bw + gap)
                if (c == 0) {
                    drawRect(empty, Offset(x, size.height - 3.dp.toPx()), Size(bw, 3.dp.toPx()))
                } else {
                    val h = size.height * c / top
                    drawRect(primary, Offset(x, size.height - h), Size(bw, h))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${counts.size} WEEKS AGO", style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.weight(1f))
            Text("THIS WEEK", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
