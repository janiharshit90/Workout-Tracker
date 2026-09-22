@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mentzertracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.update.UpdateState
import com.example.mentzertracker.update.UpdateViewModel
import com.example.mentzertracker.util.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: WorkoutViewModel,
    updateVm: UpdateViewModel,
    onManageExercises: () -> Unit,
    onManageTemplates: () -> Unit
) {
    val unit by vm.weightUnit.collectAsState()
    val keepOn by vm.keepScreenOn.collectAsState()
    val autoCheck by updateVm.autoCheck.collectAsState()
    val lastChecked by updateVm.lastChecked.collectAsState()
    val updateState by updateVm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var confirmRestore by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportBackup) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importBackup) }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore from backup?",
            message = "This REPLACES all current exercises, templates and history with the backup's contents.",
            confirmLabel = "Choose file",
            onDismiss = { confirmRestore = false },
            onConfirm = {
                confirmRestore = false
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenTitle("Settings") }

        item {
            SettingsSection("Units") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = unit == WeightUnit.LBS, onClick = { vm.setWeightUnit(WeightUnit.LBS) },
                        label = { Text("lbs") })
                    FilterChip(selected = unit == WeightUnit.KG, onClick = { vm.setWeightUnit(WeightUnit.KG) },
                        label = { Text("kg") })
                }
                Text("History is stored precisely; switching only changes how it's shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsSection("Workout") {
                SwitchRow("Keep screen on", "While on Today or while the rest timer runs", keepOn, vm::setKeepScreenOn)
            }
        }

        item {
            SettingsSection("Workout data") {
                SettingsRow(Icons.Filled.FitnessCenter, "Manage exercises", null, onManageExercises)
                SettingsRow(Icons.Filled.ListAlt, "Manage templates", null, onManageTemplates)
            }
        }

        item {
            SettingsSection("Backup & export") {
                SettingsRow(Icons.Filled.Backup, "Back up data",
                    "Save everything to a file (do this before reinstalling)") {
                    backupLauncher.launch("mentzer-backup-${formatDate(System.currentTimeMillis(), "yyyy-MM-dd")}.json")
                }
                SettingsRow(Icons.Filled.Restore, "Restore from backup", "Replace current data with a backup file") {
                    confirmRestore = true
                }
                SettingsRow(Icons.Filled.Share, "Export history as CSV", "Open in Sheets/Excel") {
                    scope.launch {
                        vm.exportCsvFile()?.let { shareFile(ctx, it, "text/csv", "Share workout history") }
                    }
                }
            }
        }

        item {
            SettingsSection("Updates") {
                SwitchRow("Check automatically", "On app start, at most every 6 hours", autoCheck, updateVm::setAutoCheck)
                Spacer(Modifier.height(4.dp))
                val checking = updateState is UpdateState.Checking
                Button(onClick = { updateVm.checkNow() }, enabled = !checking) {
                    Icon(Icons.Filled.SystemUpdate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (checking) "Checking\u2026" else "Check for updates")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("Installed: v${updateVm.currentVersion}")
                        if (lastChecked > 0) append(" \u00b7 last checked ${relativeDay(lastChecked)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (updateVm.isConfigured) "Source: github.com/${updateVm.repo}"
                    else "No update source configured in this build (see README).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateVm.isConfigured) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Text("Mentzer Tracker v${updateVm.currentVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Normal)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
