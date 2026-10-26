package com.example.mentzertracker.util

import com.example.mentzertracker.data.SetWithExercise
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class StallAdvice {
    /** You've been training it before fully recovering: give it more time. */
    REST_MORE,
    /** Already resting plenty and still stuck: back off ~10% and rebuild. */
    DELOAD
}

/** An exercise that has stopped progressing. */
data class Stall(
    val exerciseId: Long,
    /** How many sessions in a row failed to beat your best. */
    val flatSessions: Int,
    val avgGapDays: Double,
    val advice: StallAdvice,
    /** Your current best top-set weight (lbs), for the "drop to ~x" suggestion. */
    val topWeightLbs: Float
) {
    /** A rest gap worth trying, in days. */
    fun suggestedRestDays(recoveryDays: Int): Int = maxOf(recoveryDays, ceil(avgGapDays).toInt()) + 2
}

private const val DAY_MS = 86_400_000.0

/**
 * Finds exercises where the last [window] sessions each failed to beat the best set before them
 * (more weight, or more reps at the same weight). Warm-ups are ignored. Heavy Duty's answer to a
 * stall is almost always more recovery rather than more work, so the advice leans that way: if
 * you've been training it more often than your recovery target allows, it says rest; if you
 * already rest enough, it says back off and rebuild.
 */
fun computeStalls(history: List<SetWithExercise>, recoveryDays: Int, window: Int = 2): Map<Long, Stall> =
    history.filter { !it.isWarmup }.groupBy { it.exerciseId }.mapNotNull { (exId, sets) ->
        val sessions = sets.groupBy { it.sessionDate }.toSortedMap().map { (date, s) ->
            date to s.map { TopSet(it.weight, it.reps) }.best()!!
        }
        if (sessions.size < window + 1) return@mapNotNull null

        var flat = 0
        for (i in sessions.lastIndex downTo 1) {
            val before = sessions.subList(0, i).map { it.second }.best()
            if (beatsBest(before, sessions[i].second)) break
            flat++
        }
        if (flat < window) return@mapNotNull null

        val recent = sessions.takeLast(flat + 1)
        val gaps = recent.zipWithNext { a, b -> (startOfDay(b.first) - startOfDay(a.first)) / DAY_MS }
        val avgGap = if (gaps.isEmpty()) 0.0 else gaps.average()
        val advice = if (avgGap < recoveryDays + 1) StallAdvice.REST_MORE else StallAdvice.DELOAD
        exId to Stall(exId, flat, avgGap, advice, sessions.map { it.second }.best()!!.weight)
    }.toMap()

/** Rounds a lbs weight down by ~10% to a multiple of [stepDisplay] in display units. */
fun deloadWeightLbs(topWeightLbs: Float, unit: WeightUnit, stepDisplay: Float): Float {
    val shown = lbsToDisplay(topWeightLbs, unit) * 0.9f
    val rounded = (shown / stepDisplay).roundToInt() * stepDisplay
    return displayToLbs(rounded.coerceAtLeast(stepDisplay), unit)
}
