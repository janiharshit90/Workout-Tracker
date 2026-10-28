package com.example.mentzertracker.util

/**
 * Ready-made Heavy Duty programs that can be added in one tap. Installing one creates any exercises
 * that don't exist yet (matched by name, case-insensitive) and a template per workout, with the
 * pre-exhaust pairs already linked as supersets.
 *
 * These follow the shape of the routines Mike Mentzer described in his books, as they're commonly
 * summarised; the cues are written in our own words. People are free to edit them afterwards.
 */
data class RoutineSlot(
    val exercise: String,
    val cue: String,
    /** Go straight into the next slot with no rest (pre-exhaust). */
    val supersetNext: Boolean = false,
    /** Default weight jump for this exercise, in lbs; 0 = the app default. */
    val stepLbs: Float = 0f
)

data class RoutineWorkout(val name: String, val slots: List<RoutineSlot>)

data class Routine(
    val key: String,
    val title: String,
    val summary: String,
    val workouts: List<RoutineWorkout>
)

private val PEC_DECK = RoutineSlot("Pec deck", "Elbows slightly bent and fixed; squeeze, don't press", supersetNext = true)
private val INCLINE_PRESS = RoutineSlot("Incline press", "Straight from the pec deck - no rest. Shoulders down and back")
private val PULLOVER = RoutineSlot("Pullover", "Long arc from overhead to the hips; feel the lats, not the arms", supersetNext = true)
private val PULLDOWN = RoutineSlot("Close-grip palms-up pulldown", "Straight from the pullover - no rest. Pull elbows to the ribs")
private val DEADLIFT = RoutineSlot("Deadlift", "Flat back, bar close; stand up, don't yank", stepLbs = 10f)
private val LEG_EXTENSION = RoutineSlot("Leg extension", "Pause at the top; lower under control", supersetNext = true)
private val LEG_PRESS = RoutineSlot("Leg press", "Straight from extensions - no rest. Full depth, no lockout rest", stepLbs = 10f)
private val SQUAT = RoutineSlot("Squat", "Brace hard; hips and knees together; full depth you can control", stepLbs = 10f)
private val LEG_CURL = RoutineSlot("Leg curl", "Hips pinned down; squeeze hard at the top")
private val CALF_RAISE = RoutineSlot("Standing calf raise", "Full stretch at the bottom, one-second hold at the top")
private val LATERAL_RAISE = RoutineSlot("Lateral raise", "Lead with the elbows; stop at shoulder height")
private val REAR_DELT = RoutineSlot("Bent-over rear-delt raise", "Chest down, little thumbs up; no swinging")
private val BARBELL_CURL = RoutineSlot("Barbell curl", "Elbows pinned to your sides; no hip drive")
private val PUSHDOWN = RoutineSlot("Triceps pushdown", "Elbows fixed; lock out hard", supersetNext = true)
private val DIPS = RoutineSlot("Dips", "Straight from pushdowns - no rest. Upright torso for triceps")

val ROUTINES: List<Routine> = listOf(
    Routine(
        key = "ideal",
        title = "Ideal Routine",
        summary = "Mentzer's classic split, run as a cycle: Chest & Back → Legs → Shoulders & Arms → Legs. " +
            "One set to failure each, pre-exhaust supersets, 4-7 days between workouts.",
        workouts = listOf(
            RoutineWorkout("Chest & Back", listOf(PEC_DECK, INCLINE_PRESS, PULLOVER, PULLDOWN, DEADLIFT)),
            RoutineWorkout("Legs", listOf(LEG_EXTENSION, LEG_PRESS, LEG_CURL, CALF_RAISE)),
            RoutineWorkout("Shoulders & Arms", listOf(LATERAL_RAISE, REAR_DELT, BARBELL_CURL, PUSHDOWN, DIPS))
        )
    ),
    Routine(
        key = "consolidated",
        title = "Consolidation",
        summary = "In the spirit of Mentzer's later, briefer programs: two short workouts of big compound lifts, " +
            "alternated with a week or more of rest in between.",
        workouts = listOf(
            RoutineWorkout("Consolidation A", listOf(SQUAT, PULLDOWN.copy(cue = "Pull elbows to the ribs; full stretch at the top"), DIPS.copy(cue = "Upright torso; full range you can control"))),
            RoutineWorkout("Consolidation B", listOf(DEADLIFT, CALF_RAISE))
        )
    )
)

/** What installing a routine would change, worked out before touching the database. */
data class RoutinePlan(
    /** Exercises to create (names that don't exist yet), in first-use order. */
    val newExercises: List<RoutineSlot>,
    /** Workouts to create as templates (those whose name isn't taken yet). */
    val newWorkouts: List<RoutineWorkout>,
    /** Workouts skipped because a template with that name already exists. */
    val skippedWorkouts: List<String>
)

fun planRoutine(routine: Routine, existingExerciseNames: Collection<String>, existingTemplateNames: Collection<String>): RoutinePlan {
    val haveEx = existingExerciseNames.map { it.trim().lowercase() }.toMutableSet()
    val haveT = existingTemplateNames.map { it.trim().lowercase() }.toSet()
    val (skip, create) = routine.workouts.partition { it.name.lowercase() in haveT }
    val newEx = mutableListOf<RoutineSlot>()
    create.flatMap { it.slots }.forEach { slot ->
        if (haveEx.add(slot.exercise.lowercase())) newEx += slot
    }
    return RoutinePlan(newEx, create, skip.map { it.name })
}
