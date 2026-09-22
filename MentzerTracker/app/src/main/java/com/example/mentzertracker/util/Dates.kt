package com.example.mentzertracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
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
