package com.example.mentzertracker.util

import com.example.mentzertracker.data.BodyWeight
import com.example.mentzertracker.data.SetWithExercise

/**
 * The numbers behind the Stats screen. Everything here is derived from logged sets (warm-ups
 * excluded) so it's always in step with History, and it's plain Kotlin so it can be unit-tested.
 */

const val DAY_MILLIS = 86_400_000L

/** Distinct session timestamps, oldest first. */
fun sessionDates(history: List<SetWithExercise>): List<Long> = history.map { it.sessionDate }.distinct().sorted()

/** Workouts in each of the last [weeks] Monday-first weeks, oldest first; the last entry is this week. */
fun workoutsPerWeek(dates: List<Long>, weeks: Int, now: Long = System.currentTimeMillis()): List<Int> {
    val thisWeek = weekStart(now)
    val starts = List(weeks) { addDays(thisWeek, -7 * (weeks - 1 - it)) }
    val counts = IntArray(weeks)
    dates.forEach { d ->
        val w = weekStart(d)
        val i = starts.indexOf(w)
        if (i >= 0) counts[i]++
    }
    return counts.toList()
}

/** Average days between your last [lastN] workouts (by calendar day), or null with fewer than two. */
fun averageGapDays(dates: List<Long>, lastN: Int = 6): Double? {
    val days = dates.map { startOfDay(it) }.distinct().sorted().takeLast(lastN + 1)
    if (days.size < 2) return null
    return days.zipWithNext { a, b -> (b - a).toDouble() / DAY_MILLIS }.average()
}

/** Average recorded session length since [since], or null if none were timed. */
fun averageDurationSec(history: List<SetWithExercise>, since: Long): Int? {
    val durations = history.filter { it.sessionDate >= since }
        .groupBy { it.sessionDate }.values.map { s -> s.maxOf { it.durationSec } }.filter { it > 0 }
    return if (durations.isEmpty()) null else durations.average().toInt()
}

/** One new personal record: the session (date) where an exercise's top set beat everything before it. */
data class PrEvent(val exerciseId: Long, val date: Long, val top: TopSet)

/** Every PR ever set, oldest first. The first session of an exercise is never a PR. */
fun prEvents(history: List<SetWithExercise>): List<PrEvent> =
    history.filter { !it.isWarmup }.groupBy { it.exerciseId }.flatMap { (exId, sets) ->
        var best: TopSet? = null
        sets.groupBy { it.sessionDate }.toSortedMap().mapNotNull { (date, s) ->
            val top = s.map { TopSet(it.weight, it.reps) }.best()!!
            val isPr = beatsBest(best, top)
            if (best == null || isPr) best = top
            if (isPr) PrEvent(exId, date, top) else null
        }
    }.sortedBy { it.date }

/** How one exercise's estimated 1RM moved over a window. */
data class StrengthTrend(
    val exerciseId: Long,
    val name: String,
    val firstE1rm: Float,
    val lastE1rm: Float,
    val sessions: Int
) {
    val percentChange: Double get() = if (firstE1rm <= 0f) 0.0 else (lastE1rm - firstE1rm) * 100.0 / firstE1rm
}

/**
 * Per exercise, the best estimated 1RM of the first and the latest session since [since]
 * (needs at least [minSessions] sessions). Sorted from most improved to least.
 */
fun strengthTrends(history: List<SetWithExercise>, since: Long, minSessions: Int = 2): List<StrengthTrend> =
    history.filter { !it.isWarmup && it.sessionDate >= since }.groupBy { it.exerciseId }.mapNotNull { (exId, sets) ->
        val perSession = sets.groupBy { it.sessionDate }.toSortedMap().values.map { s ->
            s.maxOf { estimated1RM(it.weight, it.reps) }
        }
        if (perSession.size < minSessions) return@mapNotNull null
        StrengthTrend(exId, sets.first().exerciseName, perSession.first(), perSession.last(), perSession.size)
    }.sortedByDescending { it.percentChange }

/** The average change across [trends], or null if there are none. */
fun overallStrengthChange(trends: List<StrengthTrend>): Double? =
    if (trends.isEmpty()) null else trends.map { it.percentChange }.average()

/** Latest body weight and how it moved over the last [days] days (lbs). */
data class BodyWeightSummary(val latest: BodyWeight, val changeLbs: Float?, val sinceDate: Long?)

fun bodyWeightSummary(entries: List<BodyWeight>, days: Int = 30): BodyWeightSummary? {
    val sorted = entries.sortedBy { it.date }
    val latest = sorted.lastOrNull() ?: return null
    if (sorted.size < 2) return BodyWeightSummary(latest, null, null)
    val cutoff = latest.date - days * DAY_MILLIS
    // Compare with the reading closest to [days] ago (at or before it), else the oldest one there is.
    val base = sorted.lastOrNull { it.date <= cutoff } ?: sorted.first()
    return BodyWeightSummary(latest, latest.weightLbs - base.weightLbs, base.date)
}

/** "+3.2%", "-1%", "0%". */
fun formatPercent(p: Double): String {
    val r = Math.round(p * 10) / 10.0
    val text = if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    return (if (r > 0) "+" else "") + text + "%"
}
