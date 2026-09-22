package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.update.UpdateState
import com.example.mentzertracker.update.UpdateViewModel

/** Single dialog that walks through check -> download -> permission -> install. */
@Composable
fun UpdateDialog(updateVm: UpdateViewModel) {
    val visible by updateVm.dialogVisible.collectAsState()
    val state by updateVm.state.collectAsState()
    if (!visible || state == UpdateState.Idle) return

    when (val s = state) {
        UpdateState.Idle -> Unit

        UpdateState.Checking -> AlertDialog(
            onDismissRequest = updateVm::dismiss,
            title = { Text("Checking for updates") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Contacting GitHub\u2026")
                }
            },
            confirmButton = { TextButton(onClick = updateVm::dismiss) { Text("Hide") } }
        )

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updateVm::dismiss,
            title = { Text("You're up to date") },
            text = { Text("v${updateVm.currentVersion} is the latest version.") },
            confirmButton = { TextButton(onClick = updateVm::dismiss) { Text("OK") } }
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updateVm::dismiss,
            title = { Text("Update available") },
            text = {
                Column {
                    Text("v${s.release.versionName}  (you have v${updateVm.currentVersion})",
                        fontWeight = FontWeight.SemiBold)
                    if (s.release.apkSize > 0) {
                        Text("%.1f MB download".format(s.release.apkSize / 1_048_576.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            Text(s.release.notes.take(3000), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { updateVm.download(s.release) }) { Text("Update") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { updateVm.skip(s.release) }) { Text("Skip version") }
                    TextButton(onClick = updateVm::dismiss) { Text("Later") }
                }
            }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { /* use Cancel */ },
            title = { Text("Downloading v${s.release.versionName}") },
            text = {
                Column {
                    if (s.progress >= 0f) {
                        @Suppress("DEPRECATION")
                        LinearProgressIndicator(progress = s.progress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = updateVm::cancelDownload) { Text("Cancel") } }
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = updateVm::dismiss,
            title = { Text("One-time permission") },
            text = {
                Text("Android needs you to allow Mentzer Tracker to install updates.\n\n" +
                     "Tap \"Open settings\", switch on \"Allow from this source\", come back, then tap \"Install\".")
            },
            confirmButton = { TextButton(onClick = updateVm::retryInstall) { Text("Install") } },
            dismissButton = {
                Row {
                    TextButton(onClick = updateVm::openPermissionSettings) { Text("Open settings") }
                    TextButton(onClick = updateVm::dismiss) { Text("Cancel") }
                }
            }
        )

        is UpdateState.InstallerLaunched -> AlertDialog(
            onDismissRequest = updateVm::dismiss,
            title = { Text("Installing v${s.release.versionName}") },
            text = { Text("Confirm in the system installer. Your data is kept. If nothing appeared, tap \"Install again\".") },
            confirmButton = { TextButton(onClick = updateVm::retryInstall) { Text("Install again") } },
            dismissButton = { TextButton(onClick = updateVm::dismiss) { Text("Close") } }
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updateVm::dismiss,
            title = { Text(if (s.signatureMismatch) "Can't update in place" else "Update problem") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(s.message)
                    if (s.signatureMismatch) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This happens once when switching from a debug build to the signed release. To move over:\n" +
                            "1. Settings \u2192 Back up data\n" +
                            "2. Uninstall this app\n" +
                            "3. Install the release APK from the release page\n" +
                            "4. Settings \u2192 Restore from backup\n\n" +
                            "After that, updates install normally.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = updateVm::dismiss) { Text("Close") } },
            dismissButton = {
                if (s.release != null || s.signatureMismatch) {
                    TextButton(onClick = { updateVm.openReleasePage(s.release) }) { Text("Release page") }
                }
            }
        )
    }
}
