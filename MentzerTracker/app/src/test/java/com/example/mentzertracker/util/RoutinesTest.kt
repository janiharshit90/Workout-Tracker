package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinesTest {
    @Test fun routinesAreWellFormed() {
        assertTrue(ROUTINES.isNotEmpty())
        assertEquals(ROUTINES.size, ROUTINES.map { it.key }.distinct().size)
        ROUTINES.forEach { r ->
            assertEquals("duplicate workout names in ${r.key}", r.workouts.size, r.workouts.map { it.name }.distinct().size)
            r.workouts.forEach { w ->
                assertTrue(w.slots.isNotEmpty())
                // A superset always has a partner after it.
                assertFalse("${w.name} ends on a superset", w.slots.last().supersetNext)
                assertEquals("repeated exercise in ${w.name}", w.slots.size, w.slots.map { it.exercise.lowercase() }.distinct().size)
            }
        }
    }

    @Test fun planReusesExercisesByNameAndCreatesTheRestOnce() {
        val ideal = ROUTINES.first { it.key == "ideal" }
        val plan = planRoutine(ideal, existingExerciseNames = listOf("deadlift", "Leg Curl"), existingTemplateNames = emptyList())
        val names = plan.newExercises.map { it.exercise.lowercase() }
        assertFalse("deadlift" in names)
        assertFalse("leg curl" in names)
        assertEquals(names.size, names.distinct().size)
        assertEquals(ideal.workouts.size, plan.newWorkouts.size)
        assertTrue(plan.skippedWorkouts.isEmpty())
    }

    @Test fun planSkipsWorkoutsWhoseNameIsTaken() {
        val ideal = ROUTINES.first { it.key == "ideal" }
        val plan = planRoutine(ideal, emptyList(), existingTemplateNames = listOf("legs"))
        assertEquals(listOf("Legs"), plan.skippedWorkouts)
        assertEquals(ideal.workouts.size - 1, plan.newWorkouts.size)
        // Exercises only used by the skipped workout aren't created.
        assertFalse(plan.newExercises.any { it.exercise == "Leg extension" })
    }
}
