package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.util.*
import kotlin.math.roundToInt

/** Heavy Duty's answer to a plateau: recover more, or back off and rebuild - not "do more". */
@Composable
fun StallNotice(stall: Stall, unit: WeightUnit, step: Float, recoveryDays: Int, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val advice = when (stall.advice) {
        StallAdvice.REST_MORE ->
            "You've been back at this about every ${stall.avgGapDays.roundToInt()} days. " +
                "Try ${stall.suggestedRestDays(recoveryDays)}+ days of rest before the next one."
        StallAdvice.DELOAD ->
            "You're resting enough, so back off: try about " +
                "${formatWeight(lbsToDisplay(deloadWeightLbs(stall.topWeightLbs, unit, step), unit))} ${unit.label} " +
                "and rebuild toward the top of your rep range."
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "STALLED · ${stall.flatSessions} SESSIONS WITHOUT PROGRESS",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp, color = scheme.error
        )
        Text(
            advice,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = if (compact) 2 else Int.MAX_VALUE
        )
    }
}
