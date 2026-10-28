package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Principle(val icon: ImageVector, val title: String, val body: String)

// Paraphrased, in our own words, from the general body of Heavy Duty / HIT training
// literature - not quoted from any single source.
private val PRINCIPLES = listOf(
    Principle(
        Icons.Filled.LocalFireDepartment, "Train to true failure",
        "A work set only counts once you genuinely cannot complete another rep with good " +
        "form. Stopping short of that point - out of habit, caution, or just counting to a " +
        "number - is the single biggest reason people plateau despite \"working hard.\""
    ),
    Principle(
        Icons.Filled.HourglassEmpty, "Growth happens during recovery, not during the set",
        "The workout is the stimulus, not the result. Muscle is built in the days after, " +
        "while you sleep and eat, not under the bar. Training again before that process " +
        "finishes interrupts it - which is why this app tracks days since you last hit an " +
        "exercise instead of just how much volume you've piled on."
    ),
    Principle(
        Icons.Filled.Warning, "More is not better - enough is better",
        "Extra sets past the first one taken to failure mostly add fatigue, not extra growth " +
        "signal. A handful of hard sets per exercise, done properly, outperforms a long " +
        "routine done half-heartedly."
    ),
    Principle(
        Icons.Filled.Bolt, "Brief and infrequent, not long and constant",
        "Sessions built around a small number of maximally hard sets should be short. If " +
        "you're regularly training for over an hour, that's usually a sign the effort per " +
        "set could be higher, or there's more volume than needed."
    ),
    Principle(
        Icons.Filled.Whatshot, "Going beyond failure - sparingly",
        "Once a set hits failure, a few techniques can push it further: forced reps (a partner " +
        "helps just enough), negatives (a partner lifts, you lower slowly), rest-pause (rack it, " +
        "breathe 10-20 seconds, squeeze out a couple more) and static holds. They're powerful and " +
        "very taxing, so use one at a time and not every workout. The app logs them separately, " +
        "so they never inflate your reps, PRs or targets."
    ),
    Principle(
        Icons.Filled.Link, "Pre-exhaust: isolation, then straight into a compound",
        "In a compound lift the smaller muscles often give out before the big one is worked hard. " +
        "Doing an isolation move first (say, pec deck) and going straight into a compound (incline " +
        "press) with no rest lets the target muscle reach failure. Link two exercises as a superset " +
        "in a template and the workout will tell you to skip the rest between them."
    ),
    Principle(
        Icons.Filled.TrendingUp, "Progress is the only proof it's working",
        "The scoreboard is simple: are you moving more weight, or getting more reps at the " +
        "same weight, over time? If a lift has stalled for several sessions in a row, that's " +
        "the signal to reassess - usually more rest, not more sets."
    )
)

@Composable
fun PrinciplesScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SubScreenHeader(
                "Heavy Duty Principles",
                subtitle = "The ideas this app is built around",
                onBack = onBack
            )
        }
        items(PRINCIPLES) { p ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp)) {
                    Icon(p.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.title, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(p.body, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text(
                "These are general principles distilled from the wider Heavy Duty / HIT " +
                "training tradition, not medical advice. Listen to your own body and adjust.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
