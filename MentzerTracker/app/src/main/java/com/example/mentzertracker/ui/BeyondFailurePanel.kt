package com.example.mentzertracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.BeyondFailure
import com.example.mentzertracker.util.Technique

/**
 * What you did after hitting failure: forced reps, negatives, rest-pause, static hold. Techniques
 * you've used get a -/+ row; the rest wait as small "+" chips so the set stays uncluttered.
 * Taking one back to zero tucks it away again.
 */
@Composable
fun BeyondFailurePanel(
    beyond: BeyondFailure,
    onNudge: (Technique, Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val used = beyond.used()
    val unused = Technique.values().filter { it !in used }
    val button = if (compact) 36.dp else 44.dp

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        Text(
            "BEYOND FAILURE",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp, color = scheme.error
        )
        used.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant)
                StepButton("−", "Less ${t.label.lowercase()}", button) { onNudge(t, -1) }
                Text(
                    if (t == Technique.HOLD) "${beyond[t]}s" else "${beyond[t]}",
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.widthIn(min = 48.dp).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                StepButton("+", "More ${t.label.lowercase()}", button) { onNudge(t, 1) }
            }
        }
        if (unused.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                unused.forEach { t ->
                    OutlinedButton(
                        onClick = { onNudge(t, 1) }, shape = IronShape,
                        border = BorderStroke(1.dp, scheme.outline),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(if (compact) 32.dp else 38.dp)
                    ) {
                        Text("+ ${t.chip}", style = MaterialTheme.typography.labelLarge, letterSpacing = 0.8.sp,
                            color = scheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, shape = IronShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.size(size).semantics { contentDescription = description }
    ) { Text(symbol, style = MaterialTheme.typography.titleMedium) }
}
