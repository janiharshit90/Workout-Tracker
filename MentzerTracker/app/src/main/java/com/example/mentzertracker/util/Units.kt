package com.example.mentzertracker.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Weights are ALWAYS stored in the database as lbs. This only affects display/input. */
enum class WeightUnit(val label: String) { LBS("lbs"), KG("kg") }

private const val LBS_PER_KG = 2.20462262f

fun lbsToDisplay(lbs: Float, unit: WeightUnit): Float =
    if (unit == WeightUnit.KG) lbs / LBS_PER_KG else lbs

fun displayToLbs(value: Float, unit: WeightUnit): Float =
    if (unit == WeightUnit.KG) value * LBS_PER_KG else value

/** 45.0 -> "45", 22.5 -> "22.5", 20.41166 -> "20.41". */
fun formatWeight(w: Float): String {
    if (w.isNaN() || w.isInfinite()) return "0"
    val rounded = BigDecimal(w.toDouble()).setScale(2, RoundingMode.HALF_UP)
    if (rounded.signum() == 0) return "0"
    return rounded.stripTrailingZeros().toPlainString()
}

/** Accepts both "22.5" and "22,5" (comma-decimal locales). */
fun parseWeight(text: String): Float? =
    text.trim().replace(',', '.').toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }

/** Keeps digits and a single decimal separator; normalises ',' to '.'. */
fun sanitizeWeightInput(raw: String): String {
    val sb = StringBuilder()
    var seenDot = false
    for (c in raw) {
        when {
            c.isDigit() -> sb.append(c)
            (c == '.' || c == ',') && !seenDot -> { seenDot = true; sb.append('.') }
        }
    }
    return sb.toString().take(7)
}

fun sanitizeRepsInput(raw: String): String = raw.filter { it.isDigit() }.take(3)

/** Epley estimate. A single rep is its own 1RM. */
fun estimated1RM(weight: Float, reps: Int): Float =
    if (reps <= 1) weight else weight * (1f + reps / 30f)
