package com.example.mentzertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.mentzertracker.ui.*
import com.example.mentzertracker.ui.theme.MentzerTrackerTheme
import com.example.mentzertracker.update.UpdateViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val vm: WorkoutViewModel by viewModels()
    private val updateVm: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh launch, not on rotation / process restore.
        if (savedInstanceState == null) updateVm.checkOnLaunch()
        setContent { MentzerTrackerTheme { AppRoot(vm, updateVm) } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("today", "Today", Icons.Filled.FitnessCenter),
    Tab("history", "History", Icons.Filled.History),
    Tab("prs", "PRs", Icons.Filled.EmojiEvents),
    Tab("settings", "Settings", Icons.Filled.Settings)
)
private val MAIN_ROUTES = TABS.map { it.route }.toSet()

@Composable
fun AppRoot(vm: WorkoutViewModel, updateVm: UpdateViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // One snackbar host for the whole app (previously every screen had its own
    // collector, which could drop or duplicate messages).
    LaunchedEffect(Unit) {
        vm.events.collectLatest { e ->
            val result = snackbar.showSnackbar(
                message = e.message,
                actionLabel = e.actionLabel,
                duration = if (e.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) e.action?.invoke()
        }
    }

    UpdateDialog(updateVm)

    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route

    // Keep screen awake at the gym: on Today, or whenever the rest timer is running.
    val keepOnSetting by vm.keepScreenOn.collectAsState()
    val timerEnd by vm.timerEndAt.collectAsState()
    val keepOn = keepOnSetting && (current == "today" || timerEnd != null)
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val openProgress: (Long) -> Unit = { id -> nav.navigate("progress/$id") }

    // A workout in progress (or being previewed) is a focus mode: the tab bar steps out of the way.
    val activeWorkout by vm.activeWorkout.collectAsState()
    val previewing by vm.preview.collectAsState()
    val finished by vm.summary.collectAsState()
    val inWorkout = current == "today" && (activeWorkout != null || previewing != null || finished != null)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if ((current == null || current in MAIN_ROUTES) && !inWorkout) {
                Column {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        TABS.forEach { t ->
                            NavigationBarItem(
                                selected = current == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label.uppercase(), fontSize = 11.sp, letterSpacing = 0.8.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(pad)) {
            composable("today") {
                TodayScreen(vm, onOpenProgress = openProgress, onOpenPrinciples = { nav.navigate("principles") })
            }
            composable("history") {
                HistoryScreen(vm, onOpenProgress = openProgress, onGoToToday = {
                    nav.navigate("today") {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable("prs") { PRsScreen(vm, onExerciseClick = openProgress) }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    updateVm = updateVm,
                    onManageExercises = { nav.navigate("manage_exercises") },
                    onManageTemplates = { nav.navigate("manage_templates") },
                    onOpenPrinciples = { nav.navigate("principles") }
                )
            }
            composable("manage_exercises") { ManageExercisesScreen(vm, onBack = { nav.popBackStack() }) }
            composable("manage_templates") { ManageTemplatesScreen(vm, onBack = { nav.popBackStack() }) }
            composable("principles") { PrinciplesScreen(onBack = { nav.popBackStack() }) }
            composable(
                "progress/{exerciseId}",
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                ProgressScreen(vm, exerciseId = entry.arguments?.getLong("exerciseId") ?: 0L,
                    onBack = { nav.popBackStack() })
            }
        }
    }
}
