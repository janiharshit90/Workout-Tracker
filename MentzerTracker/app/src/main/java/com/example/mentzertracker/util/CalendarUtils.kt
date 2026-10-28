package com.example.mentzertracker.util

import com.example.mentzertracker.data.SetWithExercise
import java.util.Calendar

/** Midnight [n] days after (or before, if negative) the given midnight. DST-safe. */
fun addDays(dayStart: Long, n: Int): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_YEAR, n)
}.timeInMillis

/** Monday 00:00 of the week containing [ts]. */
fun weekStart(ts: Long): Long {
    val day = startOfDay(ts)
    val dow = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK)
    return addDays(day, -((dow - Calendar.MONDAY + 7) % 7))
}

/** The seven midnights of the Monday-first week containing [ts]. */
fun weekDays(ts: Long): List<Long> = weekStart(ts).let { s -> List(7) { addDays(s, it) } }

/** First of the month, 00:00. */
fun monthStart(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ts)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

fun addMonths(monthStart: Long, n: Int): Long = Calendar.getInstance().apply {
    timeInMillis = monthStart
    add(Calendar.MONTH, n)
}.timeInMillis

/** Monday-first grid for a month: leading nulls to line up the first day, then every day of the month. */
fun monthGrid(monthStart: Long): List<Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val leading = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    return List(leading) { null } + List(days) { addDays(monthStart, it) }
}

/** One logged workout (all sets sharing a sessionDate). */
data class SessionInfo(
    val date: Long,
    val exerciseIds: Set<Long>,
    val exerciseNames: List<String>,
    val sets: Int,
    val durationSec: Int,
    /** Template the session was started from (0 = custom, or logged before this was recorded). */
    val templateId: Long = 0
)

/** Sessions grouped by the day they happened (key = midnight), each day's sessions oldest first. */
fun sessionsByDay(history: List<SetWithExercise>): Map<Long, List<SessionInfo>> =
    history.groupBy { it.sessionDate }.map { (date, sets) ->
        SessionInfo(
            date = date,
            exerciseIds = sets.map { it.exerciseId }.toSet(),
            exerciseNames = sets.distinctBy { it.exerciseId }.map { it.exerciseName },
            sets = sets.count { !it.isWarmup },
            durationSec = sets.maxOf { it.durationSec },
            templateId = sets.maxOf { it.templateId }
        )
    }.groupBy { startOfDay(it.date) }.mapValues { (_, l) -> l.sortedBy { it.date } }

/**
 * Which template a session was (the app doesn't store that), by exercise overlap: the best match
 * with at least 60% overlap wins. Returns its index in [templateExerciseIds], or null for a custom mix.
 */
fun matchTemplate(sessionExerciseIds: Set<Long>, templateExerciseIds: List<Set<Long>>): Int? {
    var best: Int? = null
    var bestScore = 0.6
    templateExerciseIds.forEachIndexed { i, ids ->
        val union = (ids + sessionExerciseIds).size
        if (union == 0) return@forEachIndexed
        val score = ids.intersect(sessionExerciseIds).size.toDouble() / union
        if (score >= bestScore && (best == null || score > bestScore)) { best = i; bestScore = score }
    }
    return best
}

/**
 * The templates in start-screen order, for naming past sessions. A session that recorded its
 * template is named by it; older ones (and ones whose template was deleted) fall back to
 * [matchTemplate].
 */
data class TemplateIndex(
    val ids: List<Long> = emptyList(),
    val names: List<String> = emptyList(),
    val exerciseIds: List<Set<Long>> = emptyList()
) {
    /** Position of the session's template, or null for a custom mix. */
    fun indexOf(session: SessionInfo): Int? =
        ids.indexOf(session.templateId).takeIf { session.templateId > 0 && it >= 0 }
            ?: matchTemplate(session.exerciseIds, exerciseIds)

    /** "Day A - Full Body", or "Custom workout". */
    fun nameOf(session: SessionInfo): String = indexOf(session)?.let { names.getOrNull(it) } ?: "Custom workout"
}

/** The letter shown for template number [index] (A, B, ...), matching the start screen. */
fun templateLetter(index: Int): String = if (index < 26) ('A' + index).toString() else (index + 1).toString()

/**
 * Material's date picker reports a day as UTC midnight; turns that into local midnight of the same
 * calendar day (otherwise people west of UTC get the day before).
 */
fun pickerUtcToLocalDay(utcMillis: Long): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

/** The reverse of [pickerUtcToLocalDay]: the UTC midnight the date picker expects for a local day. */
fun localDayToPickerUtc(localDay: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localDay }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}
