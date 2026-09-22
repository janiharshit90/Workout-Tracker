package com.example.mentzertracker.data

object SeedData {
    val exercises = listOf(
        Exercise(name = "Dumbbell Incline Chest Press",
            cue = "Arms 45 deg, shoulders down and retracted back at all times", sortOrder = 0),
        Exercise(name = "1 arm dumbbell rows",
            cue = "Elbows reaching for the ceiling", sortOrder = 1),
        Exercise(name = "Dumbbell overhead press",
            cue = "Elbows straight, no extreme bending", sortOrder = 2),
        Exercise(name = "Lateral raises",
            cue = "Slight bend in elbow, thumb side slightly down", sortOrder = 3),
        Exercise(name = "Bicep curls",
            cue = "Elbows locked in place, no elbow pushing up", sortOrder = 4),
        Exercise(name = "Skull crushers",
            cue = "Arm angled towards hairline", sortOrder = 5),
        Exercise(name = "Hammer curls",
            cue = "Elbows locked in place, no elbow pushing up", sortOrder = 6)
    )

    val templates = listOf(
        "Day A - Full Body"    to listOf(0,1,2,3,4,5,6),
        "Day B - Chest / Back" to listOf(0,1,3,4)
    )
}