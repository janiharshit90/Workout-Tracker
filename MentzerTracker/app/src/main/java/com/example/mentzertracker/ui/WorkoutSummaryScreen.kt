@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.*

/**
 * Shown right after a workout is logged: how long it took, what you did, any PRs, and how each
 * exercise compared with last time. Replaces the old one-line snackbar.
 */
@Composable
fun WorkoutSummaryScreen(vm: WorkoutViewModel, summary: WorkoutSummary) {
    val unit by vm.weightUnit.collectAsState()
    val recoveryDays by vm.recoveryDays.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPro by com.example.mentzertracker.billing.ProAccess.isPro.collectAsState()
    var showPro by remember { mutableStateOf(false) }
    if (showPro) ProDialog(onDismiss = { showPro = false })

    BackHandler { vm.dismissSummary() }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("WORKOUT COMPLETE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp, color = scheme.primary)
                    Text(
                        summary.name.uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp, lineHeight = 50.sp),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBlock("TIME", if (summary.durationSec > 0) formatDuration(summary.durationSec) else "—", Modifier.weight(1.3f))
                    StatBlock("EXERCISES", "${summary.exercises.size}", Modifier.weight(1f))
                    StatBlock("SETS", "${summary.workingSets}", Modifier.weight(0.8f))
                    StatBlock("PRS", "${summary.prCount}", Modifier.weight(0.8f), highlight = summary.prCount > 0)
                }
            }

            items(summary.exercises, key = { it.exerciseId }) { ex ->
                Column(
                    Modifier.fillMaxWidth().clip(IronShape).background(scheme.surface)
                        .border(1.dp, if (ex.isPr) scheme.primary else scheme.outlineVariant, IronShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (ex.isPr) {
                            Icon(Icons.Filled.EmojiEvents, null, Modifier.size(16.dp), tint = scheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("NEW PR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp, color = scheme.primary)
                        }
                    }
                    if (ex.top != null) {
                        Text(
                            "${formatWeight(lbsToDisplay(ex.top.weight, unit))} ${unit.label} × ${ex.top.reps}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (ex.isPr) scheme.primary else scheme.onSurface
                        )
                        ex.change?.let { c ->
                            val up = c.kind == ChangeKind.WEIGHT_UP || c.kind == ChangeKind.REPS_UP
                            Text(
                                (if (c.kind == ChangeKind.FIRST) "" else "VS LAST TIME  ") + c.describe(unit).uppercase(),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                                color = if (up) scheme.primary else scheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("Warm-up only", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    if (ex.sets.size > 1 || ex.sets.any { it.isWarmup || !it.beyond.isEmpty }) {
                        Text(
                            ex.sets.joinToString("  ·  ") { s ->
                                (if (s.isWarmup) "W " else "") +
                                    "${formatWeight(lbsToDisplay(s.weightLbs, unit))}×${s.reps}" +
                                    (if (s.toFailure && !s.beyond.isEmpty) " (${s.beyond.describe()})" else "")
                            },
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                        )
                    }
                    if (ex.note.isNotBlank()) {
                        Text(ex.note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                            color = scheme.onSurfaceVariant)
                    }
                }
            }

            if (summary.skippedIncomplete > 0) {
                item {
                    Text(
                        "${summary.skippedIncomplete} set${if (summary.skippedIncomplete == 1) "" else "s"} " +
                            "missing weight or reps ${if (summary.skippedIncomplete == 1) "was" else "were"} skipped.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.error
                    )
                }
            }
            item {
                Text(
                    "Now rest. Growth happens during recovery — give these muscles $recoveryDays+ days before hitting them again.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Column(Modifier.background(scheme.background)) {
            Divider(color = scheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isPro) showPro = true
                        else scope.launch { vm.summaryImage(summary)?.let { shareFile(ctx, it, "image/png", "Share workout") } }
                    },
                    shape = IronShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Filled.IosShare, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SHARE", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp)
                }
                Button(
                    onClick = { vm.dismissSummary() }, shape = IronShape,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("DONE", style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp) }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(IronShape).background(scheme.surface).border(1.dp, scheme.outlineVariant, IronShape)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1,
            color = if (highlight) scheme.primary else scheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp,
            color = scheme.onSurfaceVariant, maxLines = 1)
    }
}
