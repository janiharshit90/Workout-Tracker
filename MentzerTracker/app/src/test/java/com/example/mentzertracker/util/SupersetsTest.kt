package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetsTest {
    private val ids = listOf(1L, 2L, 3L, 4L)

    @Test fun linksPointForward() {
        val ss = setOf(1L, 3L)
        assertTrue(linksToNext(ids, ss, 1L))
        assertFalse(linksToNext(ids, ss, 2L))
        assertEquals(1L, linkedFrom(ids, ss, 2L))
        assertNull(linkedFrom(ids, ss, 1L))
        assertEquals(3L, linkedFrom(ids, ss, 4L))
    }

    @Test fun lastExerciseCanNeverLinkForward() {
        assertFalse(linksToNext(ids, setOf(4L), 4L))
        assertEquals(setOf(1L), cleanSupersets(ids, setOf(1L, 4L, 99L)))
    }

    @Test fun removingAnExerciseBreaksLinksIntoAndOutOfIt() {
        // 1+2, 3+4. Remove 2: 1 must not silently pair with 3.
        assertEquals(setOf(3L), supersetsWithout(ids, setOf(1L, 3L), 2L))
        // Remove 3 (which linked out to 4): its link goes; 2 wasn't linked so nothing else changes.
        assertEquals(setOf(1L), supersetsWithout(ids, setOf(1L, 3L), 3L))
        // Removing the first one is fine.
        assertEquals(setOf(3L), supersetsWithout(ids, setOf(1L, 3L), 1L))
    }

    @Test fun movingSwapsNeighboursAndDropsTouchedLinks() {
        // 1+2, 3+4. Move 3 up: order 1,3,2,4; links of 2, 3 and the one before them (1) go.
        val (order, ss) = moveExercise(ids, setOf(1L, 3L), 2, -1)
        assertEquals(listOf(1L, 3L, 2L, 4L), order)
        assertEquals(emptySet<Long>(), ss)
        // Moving the last one down does nothing.
        assertEquals(ids to setOf(1L), moveExercise(ids, setOf(1L), 3, 1))
        // Unrelated links survive: swap 3 and 4 keeps 1+2.
        assertEquals(setOf(1L), moveExercise(ids, setOf(1L, 3L), 3, -1).second)
    }
}
