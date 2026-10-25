package com.example.mentzertracker.util

/** The set that represents an exercise in a session: the heaviest, ties broken by more reps. */
data class TopSet(val weight: Float, val reps: Int)

fun List<TopSet>.best(): TopSet? = maxWithOrNull(compareBy<TopSet>({ it.weight }, { it.reps }))

/** A new PR beats the previous best on weight, or ties it on weight with more reps. First time isn't a PR. */
fun beatsBest(previous: TopSet?, now: TopSet): Boolean {
    if (previous == null) return false
    return now.weight > previous.weight || (now.weight == previous.weight && now.reps > previous.reps)
}

enum class ChangeKind { FIRST, WEIGHT_UP, REPS_UP, SAME, DOWN }

/** How today's top set compares with the top set of the previous session (weights in lbs). */
data class Change(val kind: ChangeKind, val weightDeltaLbs: Float = 0f, val repsDelta: Int = 0)

private const val WEIGHT_EPS = 0.01f  // float noise from kg <-> lbs conversion

fun compareToLast(previous: TopSet?, now: TopSet): Change {
    if (previous == null) return Change(ChangeKind.FIRST)
    val dw = now.weight - previous.weight
    val dr = now.reps - previous.reps
    return when {
        dw > WEIGHT_EPS -> Change(ChangeKind.WEIGHT_UP, dw, dr)
        dw < -WEIGHT_EPS -> Change(ChangeKind.DOWN, dw, dr)
        dr > 0 -> Change(ChangeKind.REPS_UP, 0f, dr)
        dr < 0 -> Change(ChangeKind.DOWN, 0f, dr)
        else -> Change(ChangeKind.SAME)
    }
}

/** "+5 lbs", "+1 rep", "same as last time", ... for the summary screen. */
fun Change.describe(unit: WeightUnit): String = when (kind) {
    ChangeKind.FIRST -> "First time"
    ChangeKind.WEIGHT_UP -> "+${formatWeight(lbsToDisplay(weightDeltaLbs, unit))} ${unit.label}"
    ChangeKind.REPS_UP -> "+$repsDelta rep${if (repsDelta == 1) "" else "s"}"
    ChangeKind.SAME -> "Same as last time"
    ChangeKind.DOWN ->
        if (weightDeltaLbs < -WEIGHT_EPS) "${formatWeight(lbsToDisplay(weightDeltaLbs, unit))} ${unit.label}"
        else "$repsDelta rep${if (repsDelta == -1) "" else "s"}"
}

// --- Post-workout summary ---------------------------------------------------------------------

data class SummarySet(
    val weightLbs: Float, val reps: Int,
    val toFailure: Boolean, val forcedReps: Int, val isWarmup: Boolean
)

data class ExerciseSummary(
    val exerciseId: Long,
    val name: String,
    val sets: List<SummarySet>,
    /** Heaviest working set today; null if only warm-ups were logged. */
    val top: TopSet?,
    val isPr: Boolean,
    /** Versus the previous session; null if only warm-ups were logged. */
    val change: Change?,
    val note: String
)

data class WorkoutSummary(
    val name: String,
    /** 0 = unknown. */
    val durationSec: Int,
    val exercises: List<ExerciseSummary>,
    val skippedIncomplete: Int
) {
    val workingSets: Int get() = exercises.sumOf { ex -> ex.sets.count { !it.isWarmup } }
    val prCount: Int get() = exercises.count { it.isPr }
}
