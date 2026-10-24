@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.WorkoutTemplate
import com.example.mentzertracker.ui.theme.Iron
import com.example.mentzertracker.ui.theme.IronShape
import com.example.mentzertracker.util.daysAgo
import com.example.mentzertracker.util.relativeDay

/** What a template card needs to show, precomputed so the list stays cheap to recompose. */
private data class TemplateCardInfo(
    val template: WorkoutTemplate,
    val exerciseNames: List<String>,
    /** > 0 means at least one exercise in it is still inside the recovery window. */
    val daysLeft: Int,
    /** Most recent time any of its exercises was trained; 0 = never. */
    val lastTrained: Long
)

/** The screen you see when no workout is in progress: pick one and tap to start. */
@Composable
fun StartWorkoutScreen(vm: WorkoutViewModel, onOpenPrinciples: () -> Unit) {
    val templates by vm.templates.collectAsState()
    val templateExercises by vm.templateExercises.collectAsState()
    val allExercises by vm.exercises.collectAsState()
    val lastSessions by vm.lastSessions.collectAsState()
    val history by vm.history.collectAsState()
    val recoveryDays by vm.recoveryDays.collectAsState()
    val introDismissed by vm.introDismissed.collectAsState()

    var showAddTemplate by remember { mutableStateOf(false) }
    if (showAddTemplate) {
        AddTemplateDialog(
            exercises = allExercises,
            onDismiss = { showAddTemplate = false },
            onConfirm = { name, ids -> vm.addTemplate(name, ids); showAddTemplate = false }
        )
    }

    val infos = remember(templates, templateExercises, allExercises, lastSessions, recoveryDays) {
        val names = allExercises.associate { it.id to it.name }
        templates.map { t ->
            val ids = templateExercises[t.id].orEmpty().filter { it in names }
            val lastDates = ids.mapNotNull { lastSessions[it]?.firstOrNull()?.sessionDate }
            TemplateCardInfo(
                template = t,
                exerciseNames = ids.mapNotNull { names[it] },
                daysLeft = lastDates.maxOfOrNull { recoveryDays - daysAgo(it) }?.coerceAtLeast(0) ?: 0,
                lastTrained = lastDates.maxOrNull() ?: 0L
            )
        }
    }
    // Suggest the ready workout you've gone longest without (never-trained counts as longest).
    val suggestedId = remember(infos) {
        infos.filter { it.exerciseNames.isNotEmpty() && it.daysLeft == 0 }
            .minByOrNull { it.lastTrained }?.template?.id
    }
    val lastWorkout = history.firstOrNull()?.sessionDate

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("HEAVY DUTY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                    "READY\nTO TRAIN?",
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 66.sp, lineHeight = 60.sp),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                Text(
                    if (lastWorkout == null) "Pick a workout to begin" else "Last session ${relativeDay(lastWorkout)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!introDismissed && history.isEmpty()) {
            item { IntroCard(onDismiss = vm::dismissIntro, onPrinciples = onOpenPrinciples) }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("Choose a workout") }
                TextButton(onClick = { showAddTemplate = true }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("NEW")
                }
            }
        }

        itemsIndexed(infos, key = { _, info -> info.template.id }) { index, info ->
            WorkoutCard(
                letter = if (index < 26) ('A' + index).toString() else (index + 1).toString(),
                info = info,
                suggested = info.template.id == suggestedId,
                onStart = { vm.openPreview(info.template.id) }
            )
        }

        item {
            val line = MaterialTheme.colorScheme.outline
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(IronShape)
                    .clickable { vm.openPreview(null) }
                    .drawBehind {
                        drawRoundRect(
                            color = line,
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("CUSTOM WORKOUT", style = MaterialTheme.typography.titleMedium)
                    Text("Pick exercises as you go", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(letter: String, info: TemplateCardInfo, suggested: Boolean, onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (suggested) scheme.onPrimary else scheme.onSurface
    // Dark olive on lime keeps secondary text readable; plain grey on the dark cards.
    val muted = if (suggested) Iron.LimeDeep else scheme.onSurfaceVariant
    val ready = info.daysLeft == 0

    Row(
        Modifier
            .fillMaxWidth()
            .clip(IronShape)
            .background(if (suggested) scheme.primary else scheme.surface)
            .then(if (suggested) Modifier else Modifier.border(1.dp, scheme.outlineVariant, IronShape))
            .clickable(onClick = onStart)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 72.sp, lineHeight = 64.sp),
            color = if (suggested) fg else Color(0xFF6B6B6B),
            modifier = Modifier.width(56.dp)
        )
        Column(Modifier.weight(1f)) {
            if (suggested) {
                Box(
                    Modifier.padding(bottom = 6.dp).clip(IronShape).background(scheme.onPrimary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("SUGGESTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, color = scheme.primary)
                }
            }
            Text(info.template.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = fg)
            Text(
                if (info.exerciseNames.isEmpty()) "No exercises yet — edit it in Settings"
                else info.exerciseNames.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (info.exerciseNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val statusColor = if (suggested) fg else if (ready) scheme.primary else muted
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ready) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty,
                        null, Modifier.size(14.dp), tint = statusColor)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            !ready -> "RECOVERING · ${info.daysLeft} DAY${if (info.daysLeft == 1) "" else "S"} LEFT"
                            info.lastTrained > 0L -> {
                                val d = daysAgo(info.lastTrained)
                                "READY · $d DAY${if (d == 1) "" else "S"} RESTED"
                            }
                            else -> "READY · NEW"
                        },
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp, color = statusColor
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (suggested) scheme.onPrimary else Color.Transparent)
                .then(if (suggested) Modifier else Modifier.border(1.dp, scheme.outline, CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, "Start ${info.template.name}", Modifier.size(26.dp),
                tint = if (suggested) scheme.primary else scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntroCard(onDismiss: () -> Unit, onPrinciples: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(IronShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("HEAVY DUTY IN 3 STEPS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("1.  Pick a workout, check it over, then tap Start.")
        Text("2.  For each exercise, warm up, then take ONE set to true failure.")
        Text("3.  Rest until fully recovered (5+ days) before repeating it.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onPrinciples) { Text("READ THE PRINCIPLES") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDismiss, shape = IronShape) { Text("GOT IT") }
        }
    }
}
