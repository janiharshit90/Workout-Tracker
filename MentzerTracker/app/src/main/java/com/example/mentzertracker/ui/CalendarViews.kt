@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.*

private val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** What a session on a given day was, as a template letter (or "•" for a custom mix). */
private fun letterFor(session: SessionInfo, templates: TemplateIndex): String =
    templates.indexOf(session)?.let { templateLetter(it) } ?: "•"

/**
 * This week at a glance, Monday to Sunday: workouts you did (template letter), rest days, today,
 * and from your recovery setting which upcoming day you'd next be ready. Nothing is scheduled -
 * Heavy Duty is paced by recovery, so the strip follows you instead of the other way round.
 */
@Composable
fun WeekStrip(
    sessionsByDay: Map<Long, List<SessionInfo>>,
    templates: TemplateIndex,
    lastWorkoutDate: Long?,
    recoveryDays: Int,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val today = startOfDay(System.currentTimeMillis())
    val days = weekDays(today)
    val readyDay = lastWorkoutDate?.let { addDays(startOfDay(it), recoveryDays) } ?: today
    val doneThisWeek = days.sumOf { sessionsByDay[it]?.size ?: 0 }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("THIS WEEK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("$doneThisWeek workout${if (doneThisWeek == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, day ->
                val sessions = sessionsByDay[day].orEmpty()
                val isToday = day == today
                val done = sessions.isNotEmpty()
                val future = day > today
                val ready = !done && day >= readyDay && day >= today
                val caption = when {
                    done -> letterFor(sessions.first(), templates)
                    ready -> "READY"
                    else -> "REST"
                }
                val dayNumber = java.util.Calendar.getInstance().apply { timeInMillis = day }
                    .get(java.util.Calendar.DAY_OF_MONTH)

                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(WEEKDAYS[i], style = MaterialTheme.typography.labelSmall, letterSpacing = 0.5.sp,
                        color = if (isToday) scheme.primary else scheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier.fillMaxWidth().height(62.dp).clip(IronShape)
                            .background(if (done) scheme.primary else scheme.surface)
                            .border(1.dp, if (isToday) scheme.primary else scheme.outlineVariant, IronShape),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("$dayNumber", style = MaterialTheme.typography.titleMedium,
                            color = if (done) scheme.onPrimary else if (future && !ready) scheme.onSurfaceVariant else scheme.onSurface)
                        Text(
                            caption,
                            style = if (done) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, letterSpacing = if (done) 0.sp else 0.3.sp, maxLines = 1,
                            color = when {
                                done -> scheme.onPrimary
                                ready -> scheme.primary
                                else -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A month grid: days you trained are filled, today is outlined. Tap a day to see what you did
 * (or that it was a rest day). Month arrows page back through your history.
 */
@Composable
fun CalendarPanel(
    sessionsByDay: Map<Long, List<SessionInfo>>,
    templates: TemplateIndex,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val today = startOfDay(System.currentTimeMillis())
    var month by rememberSaveable { mutableLongStateOf(monthStart(today)) }
    var selected by rememberSaveable { mutableLongStateOf(today) }
    val grid = remember(month) { monthGrid(month) }
    val trainedThisMonth = grid.count { it != null && sessionsByDay.containsKey(it) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = addMonths(month, -1) }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDate(month, "MMMM yyyy").uppercase(), style = MaterialTheme.typography.titleMedium)
                Text("$trainedThisMonth training day${if (trainedThisMonth == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            IconButton(onClick = { month = addMonths(month, 1) }, enabled = month < monthStart(today)) {
                Icon(Icons.Filled.ChevronRight, "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach {
                Text(it.take(1), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    } else {
                        val trained = sessionsByDay.containsKey(day)
                        val isToday = day == today
                        val isSelected = day == selected
                        val number = java.util.Calendar.getInstance().apply { timeInMillis = day }
                            .get(java.util.Calendar.DAY_OF_MONTH)
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(IronShape)
                                .background(if (trained) scheme.primary else scheme.surface)
                                .border(
                                    if (isSelected || isToday) 2.dp else 1.dp,
                                    when { isSelected -> scheme.onSurface; isToday -> scheme.primary; else -> scheme.outlineVariant },
                                    IronShape
                                )
                                .clickable { selected = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$number", style = MaterialTheme.typography.titleSmall,
                                color = if (trained) scheme.onPrimary else if (day > today) scheme.onSurfaceVariant else scheme.onSurface)
                        }
                    }
                }
            }
        }

        // The tapped day.
        val sessions = sessionsByDay[selected].orEmpty()
        Text(formatDate(selected, "EEEE, MMM d").uppercase(), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
        if (sessions.isEmpty()) {
            Text(if (selected > today) "Upcoming" else "Rest day — nothing logged",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        sessions.forEach { s ->
            val idx = templates.indexOf(s)
            Column(
                Modifier.fillMaxWidth().clip(IronShape).background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, IronShape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text((idx?.let { "${templateLetter(it)} · ${templates.names[it]}" } ?: "Custom workout").uppercase(),
                    style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                Text(
                    formatDate(s.date, "h:mm a") + " · ${s.exerciseNames.size} exercises · ${s.sets} sets" +
                        if (s.durationSec > 0) " · ${formatDuration(s.durationSec)}" else "",
                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant
                )
                Text(s.exerciseNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
        }
    }
}
