package com.example.mentzertracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.util.formatDate
import com.example.mentzertracker.util.formatWeight

/**
 * A trend line with min/max labels and the first/last date underneath. Needs 2+ values.
 * With [dates], points are spaced by real time (a long break looks like one), and tapping or
 * dragging across the chart picks the nearest point and shows [labelFor] it.
 */
@Composable
fun LineChart(
    values: List<Float>,
    firstDate: Long,
    lastDate: Long,
    unitLabel: String,
    height: Int = 180,
    dates: List<Long>? = null,
    labelFor: ((Int) -> String)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).let { if (it < 1f) 1f else it }
    val interactive = labelFor != null

    // Horizontal position of each point, 0..1.
    val xs = remember(values, dates) {
        val span = dates?.let { it.last() - it.first() } ?: 0L
        if (dates != null && dates.size == values.size && span > 0) dates.map { (it - dates.first()).toFloat() / span }
        else List(values.size) { it.toFloat() / (values.size - 1).coerceAtLeast(1) }
    }
    var selected by remember(values) { mutableIntStateOf(values.lastIndex) }
    fun nearest(x: Float, width: Float): Int {
        val f = if (width <= 0f) 1f else (x / width).coerceIn(0f, 1f)
        return xs.indices.minByOrNull { kotlin.math.abs(xs[it] - f) } ?: values.lastIndex
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (interactive) {
                Text(labelFor!!(selected), style = MaterialTheme.typography.titleSmall, color = primary)
                Spacer(Modifier.height(4.dp))
            }
            Text("${formatWeight(maxV)} $unitLabel", style = MaterialTheme.typography.labelSmall, color = muted)
            Canvas(
                Modifier.fillMaxWidth().height(height.dp).padding(vertical = 8.dp, horizontal = 6.dp)
                    .then(if (!interactive) Modifier else Modifier
                        .pointerInput(xs) { detectTapGestures { selected = nearest(it.x, size.width.toFloat()) } }
                        .pointerInput(xs) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                selected = nearest(change.position.x, size.width.toFloat())
                            }
                        })
            ) {
                val w = size.width
                val h = size.height
                val points = values.mapIndexed { i, v -> Offset(xs[i] * w, h - ((v - minV) / range) * h) }
                for (i in 0..3) {
                    val y = h * i / 3
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }
                if (interactive) points.getOrNull(selected)?.let { p ->
                    drawLine(onSurface.copy(alpha = 0.4f), Offset(p.x, 0f), Offset(p.x, h), strokeWidth = 1.dp.toPx())
                }
                for (i in 0 until points.size - 1) {
                    drawLine(primary, points[i], points[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
                points.forEachIndexed { i, p ->
                    if (interactive && i == selected) {
                        drawCircle(onSurface, radius = 7.dp.toPx(), center = p)
                        drawCircle(surface, radius = 3.dp.toPx(), center = p)
                    } else {
                        drawCircle(primary, radius = if (i == points.lastIndex) 6.dp.toPx() else 4.dp.toPx(), center = p)
                    }
                }
            }
            Text("${formatWeight(minV)} $unitLabel", style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(formatDate(firstDate, "MMM d"), style = MaterialTheme.typography.labelSmall, color = muted)
                Spacer(Modifier.weight(1f))
                if (interactive) Text("TAP OR DRAG", style = MaterialTheme.typography.labelSmall, color = muted)
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
