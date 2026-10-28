package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class CalendarUtilsTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(y, m, d, h, 0, 0) }.timeInMillis

    private fun dow(ts: Long) = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)

    @Test fun weekStartIsMonday() {
        // Wed 28 Oct 2026 -> Mon 26 Oct 2026
        val start = weekStart(at(2026, Calendar.OCTOBER, 28))
        assertEquals(Calendar.MONDAY, dow(start))
        assertEquals(startOfDay(at(2026, Calendar.OCTOBER, 26)), start)
        // A Sunday belongs to the week that started the Monday before.
        assertEquals(startOfDay(at(2026, Calendar.OCTOBER, 26)), weekStart(at(2026, Calendar.NOVEMBER, 1)))
        // A Monday is its own week start.
        assertEquals(startOfDay(at(2026, Calendar.OCTOBER, 26)), weekStart(at(2026, Calendar.OCTOBER, 26, 0)))
    }

    @Test fun weekHasSevenDaysMondayFirst() {
        val days = weekDays(at(2026, Calendar.OCTOBER, 28))
        assertEquals(7, days.size)
        assertEquals(Calendar.MONDAY, dow(days.first()))
        assertEquals(Calendar.SUNDAY, dow(days.last()))
    }

    @Test fun monthGridAlignsFirstDay() {
        // 1 Oct 2026 is a Thursday -> three leading blanks (Mon, Tue, Wed), then 31 days.
        val grid = monthGrid(monthStart(at(2026, Calendar.OCTOBER, 15)))
        assertEquals(3, grid.takeWhile { it == null }.size)
        assertEquals(31, grid.filterNotNull().size)
    }

    @Test fun addMonthsMovesToNextFirst() {
        val next = addMonths(monthStart(at(2026, Calendar.DECEMBER, 20)), 1)
        assertEquals(startOfDay(at(2027, Calendar.JANUARY, 1)), next)
    }

    @Test fun sessionsGroupByDay() {
        val h = listOf(
            testSet(exerciseId = 1, date = at(2026, Calendar.OCTOBER, 26, 9), number = 1, weight = 50f, reps = 8),
            testSet(exerciseId = 2, date = at(2026, Calendar.OCTOBER, 26, 9), number = 1, weight = 50f, reps = 8),
            testSet(exerciseId = 1, date = at(2026, Calendar.OCTOBER, 28, 9), number = 1, weight = 50f, reps = 8)
        )
        val days = sessionsByDay(h)
        assertEquals(2, days.size)
        assertEquals(2, days[startOfDay(at(2026, Calendar.OCTOBER, 26))]!!.single().exerciseIds.size)
    }

    @Test fun templateMatchedByOverlap() {
        val templates = listOf(setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), setOf(1L, 2L, 4L, 5L))
        assertEquals(0, matchTemplate(setOf(1L, 2L, 3L, 4L, 5L, 6L), templates))
        assertEquals(1, matchTemplate(setOf(1L, 2L, 4L, 5L), templates))
        assertNull(matchTemplate(setOf(9L, 10L), templates))
    }

    @Test fun lettersFollowTemplateOrder() {
        assertEquals("A", templateLetter(0))
        assertEquals("C", templateLetter(2))
    }

    @Test fun templateIndexPrefersTheStoredTemplateThenFallsBackToMatching() {
        val idx = TemplateIndex(ids = listOf(10L, 20L), names = listOf("A", "B"),
            exerciseIds = listOf(setOf(1L, 2L, 3L), setOf(4L, 5L)))
        // Stored template wins even when the exercises look like the other one.
        assertEquals(1, idx.indexOf(SessionInfo(0, setOf(1L, 2L, 3L), emptyList(), 3, 0, templateId = 20L)))
        // Nothing stored: matched by overlap.
        assertEquals(0, idx.indexOf(SessionInfo(0, setOf(1L, 2L, 3L), emptyList(), 3, 0)))
        // Stored template since deleted: falls back too.
        assertEquals(1, idx.indexOf(SessionInfo(0, setOf(4L, 5L), emptyList(), 2, 0, templateId = 99L)))
        assertEquals("Custom workout", idx.nameOf(SessionInfo(0, setOf(42L), emptyList(), 1, 0)))
    }
}
