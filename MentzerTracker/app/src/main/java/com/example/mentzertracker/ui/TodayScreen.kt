package com.example.mentzertracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.mentzertracker.WorkoutViewModel

/**
 * Today is a guided flow with three states:
 *  - workout in progress -> [ActiveWorkoutScreen] (one exercise at a time, then review & finish)
 *  - a workout opened    -> [WorkoutPreviewScreen] (see what's in it, adjust, then tap Start)
 *  - otherwise           -> [StartWorkoutScreen] (pick a workout)
 *
 * Nothing starts, and no clock runs, until Start is tapped on the preview.
 */
@Composable
fun TodayScreen(vm: WorkoutViewModel, onOpenProgress: (Long) -> Unit, onOpenPrinciples: () -> Unit) {
    val active by vm.activeWorkout.collectAsState()
    val previewing by vm.preview.collectAsState()
    val workout = active
    val preview = previewing
    when {
        workout != null -> ActiveWorkoutScreen(vm, workout, onOpenProgress)
        preview != null -> WorkoutPreviewScreen(vm, preview)
        else -> StartWorkoutScreen(vm, onOpenPrinciples)
    }
}
