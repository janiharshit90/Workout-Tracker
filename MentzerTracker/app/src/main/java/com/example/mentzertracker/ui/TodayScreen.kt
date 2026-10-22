package com.example.mentzertracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.mentzertracker.WorkoutViewModel

/**
 * Today is a guided flow with two states:
 *  - no workout in progress -> [StartWorkoutScreen] (pick a template and go)
 *  - workout in progress    -> [ActiveWorkoutScreen] (one exercise at a time, then review & finish)
 */
@Composable
fun TodayScreen(vm: WorkoutViewModel, onOpenProgress: (Long) -> Unit, onOpenPrinciples: () -> Unit) {
    val active by vm.activeWorkout.collectAsState()
    val workout = active
    if (workout == null) StartWorkoutScreen(vm, onOpenPrinciples)
    else ActiveWorkoutScreen(vm, workout, onOpenProgress)
}
