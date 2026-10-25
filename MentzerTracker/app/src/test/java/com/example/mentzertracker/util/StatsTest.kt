package com.example.mentzertracker.util

import com.example.mentzertracker.WorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    @Test fun firstTimeIsNeverAPr() {
        assertFalse(beatsBest(null, TopSet(100f, 5)))
    }

    @Test fun prNeedsMoreWeightOrMoreRepsAtSameWeight() {
        val prev = TopSet(100f, 8)
        assertTrue(beatsBest(prev, TopSet(105f, 3)))
        assertTrue(beatsBest(prev, TopSet(100f, 9)))
        assertFalse(beatsBest(prev, TopSet(100f, 8)))
        assertFalse(beatsBest(prev, TopSet(95f, 12)))
    }

    @Test fun bestPicksHeaviestThenMostReps() {
        val best = listOf(TopSet(100f, 5), TopSet(110f, 3), TopSet(110f, 4)).best()
        assertEquals(TopSet(110f, 4), best)
        assertNull(emptyList<TopSet>().best())
    }

    @Test fun compareToLastClassifiesChange() {
        val prev = TopSet(100f, 8)
        assertEquals(ChangeKind.FIRST, compareToLast(null, TopSet(100f, 8)).kind)
        assertEquals(ChangeKind.WEIGHT_UP, compareToLast(prev, TopSet(105f, 6)).kind)
        assertEquals(ChangeKind.REPS_UP, compareToLast(prev, TopSet(100f, 9)).kind)
        assertEquals(ChangeKind.SAME, compareToLast(prev, TopSet(100f, 8)).kind)
        assertEquals(ChangeKind.DOWN, compareToLast(prev, TopSet(95f, 10)).kind)
        assertEquals(ChangeKind.DOWN, compareToLast(prev, TopSet(100f, 7)).kind)
    }

    @Test fun kgRoundTripNoiseCountsAsSameWeight() {
        val stored = displayToLbs(60f, WeightUnit.KG)
        assertEquals(ChangeKind.SAME, compareToLast(TopSet(stored, 8), TopSet(stored + 0.001f, 8)).kind)
    }

    @Test fun changeDescriptionsReadNaturally() {
        assertEquals("+5 lbs", compareToLast(TopSet(100f, 8), TopSet(105f, 6)).describe(WeightUnit.LBS))
        assertEquals("+1 rep", compareToLast(TopSet(100f, 8), TopSet(100f, 9)).describe(WeightUnit.LBS))
        assertEquals("+2 reps", compareToLast(TopSet(100f, 8), TopSet(100f, 10)).describe(WeightUnit.LBS))
        assertEquals("First time", compareToLast(null, TopSet(100f, 8)).describe(WeightUnit.LBS))
    }

    @Test fun warmUpsNeverCountTowardPrs() {
        val sets = listOf(
            testSet(date = 1, number = 1, weight = 200f, reps = 3, warmup = true),
            testSet(date = 1, number = 2, weight = 100f, reps = 8)
        )
        val pr = WorkoutViewModel.computePrs(sets)[1L]!!
        assertEquals(100f, pr.weight, 0.001f)
    }

    @Test fun lastSessionIgnoresWarmUpsAndPicksNewestWorkingSession() {
        val sets = listOf(
            testSet(date = 1, weight = 90f, reps = 8),
            testSet(date = 2, number = 1, weight = 45f, reps = 12, warmup = true),
            testSet(date = 2, number = 2, weight = 95f, reps = 8)
        )
        val last = WorkoutViewModel.computeLastSessions(sets)[1L]!!
        assertEquals(1, last.size)
        assertEquals(95f, last.first().weight, 0.001f)
    }

    @Test fun warmUpOnlySessionDoesNotReplaceLastWorkingSession() {
        val sets = listOf(
            testSet(date = 1, weight = 90f, reps = 8),
            testSet(date = 2, weight = 45f, reps = 12, warmup = true)
        )
        assertEquals(90f, WorkoutViewModel.computeLastSessions(sets)[1L]!!.first().weight, 0.001f)
    }

    @Test fun lastNoteIsTheNewestNonBlankOne() {
        val sets = listOf(
            testSet(date = 1, weight = 90f, reps = 8, notes = "old"),
            testSet(date = 3, weight = 90f, reps = 8, notes = "new"),
            testSet(date = 5, weight = 90f, reps = 8, notes = "")
        )
        val n = WorkoutViewModel.computeLastNotes(sets)[1L]!!
        assertEquals("new", n.text)
        assertEquals(3L, n.date)
    }
}
