package com.example.mentzertracker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mentzertracker.billing.ProAccess
import com.example.mentzertracker.ui.theme.IronShape

/** What Pro adds, shown on the upgrade dialog. Keep in step with the gates in the screens. */
private val PRO_FEATURES = listOf(
    "Stats: consistency, strength trend for every lift, body-weight log",
    "Ready-made routines, with pre-exhaust supersets linked",
    "Share any workout as an image",
    "Everything added to Pro in future updates"
)

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** The one-time upgrade. Free features stay free forever; this just adds more. */
@Composable
fun ProDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val price by ProAccess.price.collectAsState()
    val isPro by ProAccess.isPro.collectAsState()
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RARELIFT PRO") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isPro) "Pro is unlocked on this account. Thank you for supporting the app."
                    else "One payment, yours for good. No subscription, no ads.",
                    style = MaterialTheme.typography.bodyMedium
                )
                PRO_FEATURES.forEach { f ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = scheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(f, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!isPro) {
                    TextButton(onClick = { ProAccess.refresh() }) { Text("RESTORE PURCHASE") }
                }
            }
        },
        confirmButton = {
            if (isPro) {
                TextButton(onClick = onDismiss) { Text("DONE") }
            } else {
                Button(
                    onClick = { ctx.findActivity()?.let { ProAccess.purchase(it) } },
                    shape = IronShape
                ) { Text(price?.let { "UNLOCK · $it" } ?: "UNLOCK") }
            }
        },
        dismissButton = { if (!isPro) TextButton(onClick = onDismiss) { Text("NOT NOW") } }
    )
}

/** Stands in for a Pro-only section: what it is, and a tap to unlock. */
@Composable
fun ProLockedCard(title: String, description: String, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(IronShape).background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, IronShape)
            .clickable(onClick = onUnlock).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, null, tint = scheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp, color = scheme.onSurfaceVariant)
            Text(description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text("PRO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.primary)
    }
}
