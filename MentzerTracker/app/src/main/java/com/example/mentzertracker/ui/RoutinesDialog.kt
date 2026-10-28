package com.example.mentzertracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.ROUTINES

/** Pick a ready-made Heavy Duty routine to add as templates (exercises are created as needed). */
@Composable
fun RoutinesDialog(onDismiss: () -> Unit, onInstall: (key: String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HEAVY DUTY ROUTINES") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Adds each workout as a template, with pre-exhaust supersets linked. Exercises you " +
                        "already have (same name) are reused, and you can edit everything afterwards.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                )
                ROUTINES.forEach { r ->
                    Column(
                        Modifier.fillMaxWidth().clip(IronShape)
                            .border(1.dp, scheme.outlineVariant, IronShape)
                            .clickable { onInstall(r.key) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(r.title.uppercase(), style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                        Text(r.summary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        r.workouts.forEach { w ->
                            Text(w.name.uppercase(), style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 4.dp))
                            Text(
                                buildString {
                                    w.slots.forEachIndexed { i, slot ->
                                        append(slot.exercise)
                                        if (i < w.slots.lastIndex) append(if (slot.supersetNext) " + " else " → ")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("TAP TO ADD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp, color = scheme.primary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}
