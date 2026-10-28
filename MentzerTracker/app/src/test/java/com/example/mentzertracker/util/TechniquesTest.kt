package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniquesTest {
    @Test fun emptyByDefault() {
        assertTrue(BeyondFailure().isEmpty)
        assertEquals("", BeyondFailure().describe())
        assertTrue(BeyondFailure().used().isEmpty())
    }

    @Test fun nudgeStepsAndClamps() {
        var b = BeyondFailure()
        b = b.nudge(Technique.NEGATIVES, 1).nudge(Technique.NEGATIVES, 1)
        assertEquals(2, b.negatives)
        b = b.nudge(Technique.HOLD, 1)
        assertEquals(5, b.holdSec)  // holds move in 5-second steps
        b = b.nudge(Technique.FORCED, -1)
        assertEquals(0, b.forcedReps)  // never below zero
        repeat(50) { b = b.nudge(Technique.REST_PAUSE, 1) }
        assertEquals(Technique.REST_PAUSE.max, b.restPauseReps)
    }

    @Test fun describeListsOnlyWhatWasUsedInAStableOrder() {
        val b = BeyondFailure(forcedReps = 2, holdSec = 15, negatives = 3)
        assertEquals(listOf(Technique.FORCED, Technique.NEGATIVES, Technique.HOLD), b.used())
        assertEquals("+2 forced · 3 neg · 15s hold", b.describe())
    }

    @Test fun getAndWithRoundTrip() {
        Technique.values().forEach { t ->
            assertEquals(3, BeyondFailure().with(t, 3)[t])
        }
    }
}
