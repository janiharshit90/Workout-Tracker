package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StallsTest {
    private val day = 86_400_000L
    private val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 5, 12, 0, 0) }.timeInMillis

    /** One working set per session; [gapDays] apart. */
    private fun sessions(vararg tops: Pair<Float, Int>, gapDays: Int = 7, exerciseId: Long = 1) =
        tops.mapIndexed { i, (w, r) ->
            testSet(exerciseId = exerciseId, date = start + i * gapDays * day, weight = w, reps = r)
        }

    @Test fun steadyProgressIsNotAStall() {
        assertTrue(computeStalls(sessions(100f to 6, 100f to 7, 100f to 8, 105f to 6), 5).isEmpty())
    }

    @Test fun twoFlatSessionsInARowIsAStall() {
        val s = computeStalls(sessions(100f to 8, 105f to 6, 105f to 6, 105f to 6), 5)[1L]!!
        assertEquals(2, s.flatSessions)
    }

    @Test fun oneFlatSessionIsNotYetAStall() {
        assertTrue(computeStalls(sessions(100f to 8, 105f to 6, 105f to 6), 5).isEmpty())
    }

    @Test fun tooFewSessionsNeverStall() {
        assertTrue(computeStalls(sessions(100f to 8, 100f to 8), 5).isEmpty())
    }

    @Test fun trainingTooOftenSuggestsMoreRest() {
        val s = computeStalls(sessions(100f to 8, 100f to 8, 100f to 8, gapDays = 4), 5)[1L]!!
        assertEquals(StallAdvice.REST_MORE, s.advice)
        assertEquals(4.0, s.avgGapDays, 0.01)
        assertEquals(7, s.suggestedRestDays(5))
    }

    @Test fun alreadyRestingEnoughSuggestsDeload() {
        val s = computeStalls(sessions(100f to 8, 100f to 8, 100f to 8, gapDays = 9), 5)[1L]!!
        assertEquals(StallAdvice.DELOAD, s.advice)
    }

    @Test fun regressionCountsAsFlat() {
        val s = computeStalls(sessions(100f to 8, 95f to 8, 95f to 6), 5)[1L]!!
        assertEquals(2, s.flatSessions)
    }

    @Test fun warmUpsAreIgnored() {
        val h = sessions(100f to 8, 105f to 6, 110f to 6) +
            testSet(date = start + 3 * 7 * day, weight = 300f, reps = 10, warmup = true)
        assertTrue(computeStalls(h, 5).isEmpty())
    }

    @Test fun deloadIsAboutTenPercentRoundedToTheStep() {
        assertEquals(90f, deloadWeightLbs(100f, WeightUnit.LBS, 5f), 0.001f)
        assertEquals(135f, deloadWeightLbs(150f, WeightUnit.LBS, 5f), 0.001f)
    }

    @Test fun pickerDayRoundTrips() {
        val local = startOfDay(start)
        assertEquals(local, pickerUtcToLocalDay(localDayToPickerUtc(local)))
    }
}
