package com.example.mentzertracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatesTest {
    @Test fun exactLbsLoads() {
        val p = platesFor(225f, 45f, WeightUnit.LBS)
        assertEquals(listOf(45f, 45f), p.perSide)
        assertEquals(225f, p.total)
        assertEquals("2 × 45", p.describe())
        assertEquals(listOf(45f, 10f, 5f), platesFor(165f, 45f, WeightUnit.LBS).perSide)
    }

    @Test fun kgAndFractionalPlates() {
        assertEquals(listOf(25f, 15f), platesFor(100f, 20f, WeightUnit.KG).perSide)
        assertEquals(listOf(20f, 1.25f), platesFor(62.5f, 20f, WeightUnit.KG).perSide)
    }

    @Test fun emptyBarAndUnreachableWeights() {
        assertTrue(platesFor(40f, 45f, WeightUnit.LBS).perSide.isEmpty())
        assertEquals("", platesFor(45f, 45f, WeightUnit.LBS).describe())
        // 47 lbs can't be made: closest below is the bar plus nothing (2.5 per side would be 50).
        assertEquals(45f, platesFor(47f, 45f, WeightUnit.LBS).total)
    }
}
