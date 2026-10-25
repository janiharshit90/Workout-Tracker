package com.example.mentzertracker.util

import com.example.mentzertracker.data.SetWithExercise
import kotlin.math.roundToInt

/**
 * Double progression, the way Heavy Duty is usually run: stay at a weight until you can
 * hit the TOP of your rep range, then add weight and work back up from the bottom.
 *
 *  - reps at/above the top of the range -> add weight, aim for the bottom of the range
 *  - reps inside the range              -> same weight, beat last time by one rep
 *  - reps below the bottom of the range -> same weight, build up to the bottom first
 *
 * Reps here are unassisted reps; forced reps are logged separately and never counted.
 */
enum class SuggestionKind { INCREASE_WEIGHT, ADD_REP, BUILD_REPS }

data class Suggestion(
    val kind: SuggestionKind,
    /** Weight to use next time, in lbs (the storage unit). */
    val weightLbs: Float,
    /** Reps to aim for at that weight (at least). */
    val targetReps: Int
)

/** Typical plate jump: 5 lb, or 2.5 kg for metric users. */
fun weightIncrement(unit: WeightUnit): Float = if (unit == WeightUnit.KG) 2.5f else 5f

/**
 * The weight jump to use for one exercise, in display units. [stepLbs] is what's stored on the
 * exercise (lbs); 0 means "not set", which falls back to the default for the unit.
 */
fun stepFor(stepLbs: Float, unit: WeightUnit): Float =
    if (stepLbs <= 0f) weightIncrement(unit)
    else (((lbsToDisplay(stepLbs, unit) * 100f).roundToInt() / 100f)).coerceAtLeast(0.25f)

/** Jumps offered when setting an exercise's increment, in display units. */
fun stepChoices(unit: WeightUnit): List<Float> =
    if (unit == WeightUnit.KG) listOf(1f, 1.25f, 2.5f, 5f) else listOf(1f, 2.5f, 5f, 10f)

/**
 * @param last sets from the most recent session of one exercise (see computeLastSessions).
 * @return null when there's nothing to base a suggestion on.
 */
fun suggestNext(
    last: List<SetWithExercise>,
    unit: WeightUnit,
    repRange: IntRange,
    /** Weight jump in display units; see [stepFor]. */
    step: Float = weightIncrement(unit)
): Suggestion? {
    // The working set is the heaviest one (ties: most reps). Warm-ups don't drive progression.
    val top = last.filter { !it.isWarmup }.maxWithOrNull(compareBy<SetWithExercise>({ it.weight }, { it.reps })) ?: return null
    if (top.reps <= 0) return null

    return when {
        top.reps >= repRange.last -> {
            // Round the current weight to 2dp in display units first so kg<->lbs float noise
            // (100 kg stored as 220.46225 lbs) can't leak into the suggested number.
            val shown = (lbsToDisplay(top.weight, unit) * 100f).roundToInt() / 100f
            Suggestion(SuggestionKind.INCREASE_WEIGHT, displayToLbs(shown + step, unit), repRange.first)
        }
        top.reps < repRange.first ->
            Suggestion(SuggestionKind.BUILD_REPS, top.weight, repRange.first)
        else ->
            Suggestion(SuggestionKind.ADD_REP, top.weight, top.reps + 1)
    }
}

/** Short heading for the target block, by what the suggestion is asking of you. */
fun Suggestion.label(): String = when (kind) {
    SuggestionKind.INCREASE_WEIGHT -> "Target \u00b7 add weight"
    SuggestionKind.ADD_REP -> "Target \u00b7 beat it"
    SuggestionKind.BUILD_REPS -> "Target \u00b7 build reps"
}

/** "95 lbs × 9+" */
fun Suggestion.target(unit: WeightUnit): String =
    "${formatWeight(lbsToDisplay(weightLbs, unit))} ${unit.label} \u00d7 $targetReps+"

/** One-line description (kept for compact spots). */
fun Suggestion.describe(unit: WeightUnit): String {
    val w = "${formatWeight(lbsToDisplay(weightLbs, unit))} ${unit.label}"
    return when (kind) {
        SuggestionKind.INCREASE_WEIGHT -> "Add weight: $w × $targetReps+"
        SuggestionKind.ADD_REP -> "Beat it: $w × $targetReps+"
        SuggestionKind.BUILD_REPS -> "Build reps: $w × $targetReps+"
    }
}
