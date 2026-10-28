package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.util.*

/** How to load a barbell for a weight: plates per side, with a choice of bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorDialog(initialWeight: Float?, unit: WeightUnit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialWeight?.let { formatWeight(it) }.orEmpty()) }
    var bar by remember { mutableFloatStateOf(barChoices(unit).first()) }
    val target = parseWeight(text)
    val load = target?.let { platesFor(it, bar, unit) }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PLATES") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = sanitizeWeightInput(it) },
                    label = { Text("Total weight (${unit.label})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Bar", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    barChoices(unit).forEach { b ->
                        FilterChip(selected = bar == b, onClick = { bar = b },
                            label = { Text("${formatWeight(b)} ${unit.label}") })
                    }
                }
                when {
                    load == null -> Text("Enter a weight.", color = scheme.onSurfaceVariant)
                    load.perSide.isEmpty() -> Text("Just the bar (${formatWeight(bar)} ${unit.label}).",
                        style = MaterialTheme.typography.titleMedium)
                    else -> {
                        Text("EACH SIDE", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        Text(load.describe(), style = MaterialTheme.typography.headlineSmall, color = scheme.primary)
                        if (target != null && kotlin.math.abs(load.total - target) > 0.01f) {
                            Text("Closest you can load: ${formatWeight(load.total)} ${unit.label}",
                                style = MaterialTheme.typography.bodySmall, color = scheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }
    )
}
