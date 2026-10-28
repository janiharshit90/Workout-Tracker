package com.example.mentzertracker.util

import com.example.mentzertracker.data.BodyWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class InsightsTest {
    /** Noon on day [d] of a fixed month, so day maths never trips over midnight. */
    private fun day(d: Int): Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.MARCH, d, 12, 0, 0)
    }.timeInMillis

    @Test fun workoutsPerWeekBucketsMondayFirst() {
        // March 2026: Mon 2, Mon 9, Mon 16. "Now" is Wed 18th.
        val dates = listOf(day(2), day(4), day(10), day(16), day(18))
        assertEquals(listOf(2, 1, 2), workoutsPerWeek(dates, 3, now = day(18)))
    }

    @Test fun averageGapUsesCalendarDays() {
        assertNull(averageGapDays(listOf(day(1))))
        assertEquals(3.0, averageGapDays(listOf(day(1), day(4), day(7)))!!, 0.001)
        // Only the last N gaps count.
        assertEquals(2.0, averageGapDays(listOf(day(1), day(20), day(22), day(24)), lastN = 2)!!, 0.001)
    }

    @Test fun prEventsSkipFirstSessionAndIgnoreWarmups() {
        val h = listOf(
            testSet(date = day(1), weight = 100f, reps = 8),
            testSet(date = day(5), weight = 200f, reps = 8, warmup = true),  // a warm-up never counts
            testSet(date = day(5), weight = 100f, reps = 9),                  // more reps: PR
            testSet(date = day(9), weight = 100f, reps = 9),                  // tie: not a PR
            testSet(date = day(13), weight = 105f, reps = 6)                  // more weight: PR
        )
        assertEquals(listOf(day(5), day(13)), prEvents(h).map { it.date })
    }

    @Test fun strengthTrendComparesFirstAndLastSessionInWindow() {
        val h = listOf(
            testSet(exerciseId = 1, date = day(1), weight = 100f, reps = 1),
            testSet(exerciseId = 1, date = day(8), weight = 110f, reps = 1),
            testSet(exerciseId = 2, date = day(8), weight = 50f, reps = 1)    // one session: no trend
        )
        val t = strengthTrends(h, since = day(1) - 1)
        assertEquals(1, t.size)
        assertEquals(10.0, t.first().percentChange, 0.001)
        assertEquals(10.0, overallStrengthChange(t)!!, 0.001)
        // Window excludes the first session -> not enough data.
        assertEquals(0, strengthTrends(h, since = day(2)).size)
    }

    @Test fun bodyWeightChangeAgainstReadingAboutAMonthBack() {
        assertNull(bodyWeightSummary(emptyList()))
        val one = bodyWeightSummary(listOf(BodyWeight(1, day(1), 180f)))!!
        assertNull(one.changeLbs)
        val e = listOf(
            BodyWeight(1, day(1) - 40 * DAY_MILLIS, 190f),
            BodyWeight(2, day(1) - 31 * DAY_MILLIS, 185f),
            BodyWeight(3, day(1) - 10 * DAY_MILLIS, 183f),
            BodyWeight(4, day(1), 181f)
        )
        val s = bodyWeightSummary(e)!!
        assertEquals(181f, s.latest.weightLbs)
        assertEquals(-4f, s.changeLbs!!, 0.001f)
    }

    @Test fun percentFormatting() {
        assertEquals("+3.2%", formatPercent(3.24))
        assertEquals("-1%", formatPercent(-1.0))
        assertEquals("0%", formatPercent(0.0))
    }
}
