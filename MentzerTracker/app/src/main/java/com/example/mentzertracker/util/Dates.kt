package com.example.mentzertracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Whole calendar days between [ts] and now (DST-safe via rounding). */
fun daysAgo(ts: Long): Int =
    ((startOfDay(System.currentTimeMillis()) - startOfDay(ts)) / 86_400_000.0).roundToInt()

fun relativeDay(ts: Long): String = when (val d = daysAgo(ts)) {
    0 -> "today"
    1 -> "yesterday"
    in 2..13 -> "$d days ago"
    else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
}

fun formatDate(ts: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts))

/** Stored session length for display: "42s", "38 min", "1h 05m". */
fun formatDuration(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${(sec + 30) / 60} min"
    else -> "${sec / 3600}h %02dm".format((sec % 3600) / 60)
}

/** Live stopwatch text: "7:05" or "1:02:03". */
fun formatClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0L)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
