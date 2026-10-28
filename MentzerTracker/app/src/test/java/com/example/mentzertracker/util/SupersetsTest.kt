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
}
