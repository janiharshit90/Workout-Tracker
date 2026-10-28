package com.example.mentzertracker.util

/**
 * Heavy Duty's "beyond failure" techniques: ways to push a set past the point where you can't do
 * another full rep on your own. They're logged separately from reps so they never inflate PRs or
 * the progression target - the reps you did unassisted stay the honest scoreboard.
 */
enum class Technique(
    /** Full label for the stepper row. */
    val label: String,
    /** Short label for chips and compact summaries. */
    val chip: String,
    /** How much one tap of +/- changes it. */
    val step: Int,
    val max: Int,
    /** One line on what it is, for the Principles screen and hints. */
    val explainer: String
) {
    FORCED("Forced reps (with a spot)", "FORCED", 1, 20,
        "A partner helps just enough to finish 1-3 more reps after you fail."),
    NEGATIVES("Negatives", "NEGATIVES", 1, 20,
        "After failure, a partner lifts the weight and you lower it slowly (4-8 s) on your own."),
    REST_PAUSE("Rest-pause reps", "REST-PAUSE", 1, 20,
        "Rack it at failure, breathe 10-20 s, then grind out 1-3 more reps. Repeat once or twice."),
    HOLD("Static hold (seconds)", "HOLD", 5, 120,
        "At failure, hold the weight in the strongest position as long as you can.");

    /** "+2 forced", "3 neg", "RP +2", "15s hold". */
    fun short(value: Int): String = when (this) {
        FORCED -> "+$value forced"
        NEGATIVES -> "$value neg"
        REST_PAUSE -> "RP +$value"
        HOLD -> "${value}s hold"
    }
}

/** All the beyond-failure work on one set. */
data class BeyondFailure(
    val forcedReps: Int = 0,
    val negatives: Int = 0,
    val restPauseReps: Int = 0,
    val holdSec: Int = 0
) {
    val isEmpty: Boolean get() = forcedReps == 0 && negatives == 0 && restPauseReps == 0 && holdSec == 0

    operator fun get(t: Technique): Int = when (t) {
        Technique.FORCED -> forcedReps
        Technique.NEGATIVES -> negatives
        Technique.REST_PAUSE -> restPauseReps
        Technique.HOLD -> holdSec
    }

    /** A copy with [t] set to [value], clamped to the technique's range. */
    fun with(t: Technique, value: Int): BeyondFailure {
        val v = value.coerceIn(0, t.max)
        return when (t) {
            Technique.FORCED -> copy(forcedReps = v)
            Technique.NEGATIVES -> copy(negatives = v)
            Technique.REST_PAUSE -> copy(restPauseReps = v)
            Technique.HOLD -> copy(holdSec = v)
        }
    }

    /** One tap of +/- on [t]. Starting from zero, a hold jumps straight to its first step. */
    fun nudge(t: Technique, direction: Int): BeyondFailure = with(t, this[t] + direction * t.step)

    /** The techniques actually used, in a stable order. */
    fun used(): List<Technique> = Technique.values().filter { this[it] > 0 }

    /** "+2 forced · 3 neg" - empty when nothing beyond failure was done. */
    fun describe(): String = used().joinToString(" · ") { it.short(this[it]) }
}
