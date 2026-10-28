package com.example.mentzertracker.util

/** What to load on EACH side of a barbell. Weights are in the display unit. */
data class PlateLoad(
    val bar: Float,
    /** Heaviest first, e.g. [45, 45, 10]. */
    val perSide: List<Float>,
    /** What the bar actually weighs loaded like this. */
    val total: Float
) {
    /** "2 × 45 + 10", or "" for an empty bar. */
    fun describe(): String = perSide.groupingBy { it }.eachCount().entries
        .joinToString(" + ") { (plate, n) -> if (n > 1) "$n × ${formatWeight(plate)}" else formatWeight(plate) }
}

fun barChoices(unit: WeightUnit): List<Float> = if (unit == WeightUnit.KG) listOf(20f, 15f, 10f) else listOf(45f, 35f, 15f)

fun plateSizes(unit: WeightUnit): List<Float> =
    if (unit == WeightUnit.KG) listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f) else listOf(45f, 35f, 25f, 10f, 5f, 2.5f)

/**
 * Greedy plate breakdown for [target] on a [bar]. Standard plate sets make greedy exact whenever
 * an exact answer exists; otherwise it loads the closest weight not above the target.
 */
fun platesFor(target: Float, bar: Float, unit: WeightUnit): PlateLoad {
    if (target <= bar) return PlateLoad(bar, emptyList(), bar)
    var left = (target - bar) / 2f
    val plates = mutableListOf<Float>()
    for (p in plateSizes(unit)) {
        while (left + 0.001f >= p) { plates += p; left -= p }
    }
    return PlateLoad(bar, plates, bar + plates.sum() * 2f)
}
