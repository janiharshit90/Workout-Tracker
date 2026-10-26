@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.data.WorkoutTemplate
import com.example.mentzertracker.util.*

/** Step 1 of logging a past workout: which day. Future days aren't allowed. */
@Composable
fun PastDatePickerDialog(onDismiss: () -> Unit, onPick: (dayStart: Long) -> Unit) {
    val today = startOfDay(System.currentTimeMillis())
    val state = rememberDatePickerState(initialSelectedDateMillis = localDayToPickerUtc(today))
    val picked = state.selectedDateMillis?.let(::pickerUtcToLocalDay)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null && picked <= today) { Text("NEXT") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    ) { DatePicker(state = state) }
}

/** Step 2: which workout it was (or a custom one). Leads into the normal guided flow, minus the clock. */
@Composable
fun PastWorkoutPickDialog(
    day: Long,
    templates: List<WorkoutTemplate>,
    onDismiss: () -> Unit,
    onPick: (templateId: Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WHICH WORKOUT?") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("For ${formatDate(day, "EEEE, MMM d")}. It's saved on that day with no timer.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                templates.forEachIndexed { i, t ->
                    Text("${templateLetter(i)}  ·  ${t.name}", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(t.id) }.padding(vertical = 12.dp))
                }
                Text("Custom workout", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}
