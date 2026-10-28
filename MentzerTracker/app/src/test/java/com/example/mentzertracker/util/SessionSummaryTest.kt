package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {
    private val history = listOf(
        testSet(exerciseId = 1, date = 1_000, weight = 100f, reps = 8),
        testSet(exerciseId = 1, date = 2_000, weight = 105f, reps = 6),
        testSet(exerciseId = 2, date = 2_000, weight = 40f, reps = 10),
        testSet(exerciseId = 1, date = 3_000, weight = 100f, reps = 9)
    )

    @Test fun pastSessionIsJudgedAgainstWhatCameBeforeIt() {
        val s = summarizeSession(2_000, "Day A", history)!!
        assertEquals("Day A", s.name)
        assertEquals(2_000L, s.date)
        val ex1 = s.exercises.first { it.exerciseId == 1L }
        assertTrue(ex1.isPr)                                     // beat 100x8 at the time
        assertEquals(ChangeKind.WEIGHT_UP, ex1.change!!.kind)
        val ex2 = s.exercises.first { it.exerciseId == 2L }
        assertFalse(ex2.isPr)                                    // first time is never a PR
        assertEquals(ChangeKind.FIRST, ex2.change!!.kind)
    }

    @Test fun laterSessionComparesWithTheOneJustBefore() {
        val ex1 = summarizeSession(3_000, "Day A", history)!!.exercises.single()
        assertFalse(ex1.isPr)                                    // 100x9 < 105x6 best
        assertEquals(ChangeKind.DOWN, ex1.change!!.kind)
    }

    @Test fun unknownSessionIsNull() {
        assertNull(summarizeSession(9_999, "x", history))
    }
}
