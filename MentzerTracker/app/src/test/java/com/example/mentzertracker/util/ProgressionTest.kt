package com.example.mentzertracker.util

import com.example.mentzertracker.data.SetWithExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal fun testSet(
    exerciseId: Long = 1, date: Long = 1_000, number: Int = 1,
    weight: Float, reps: Int, warmup: Boolean = false, notes: String = ""
) = SetWithExercise(
    id = 0, exerciseId = exerciseId, exerciseName = "Ex$exerciseId", sessionDate = date,
    setNumber = number, weight = weight, reps = reps, notes = notes, isWarmup = warmup
)

class ProgressionTest {
    private val range = 6..10

    @Test fun belowRangeBuildsReps() {
        val s = suggestNext(listOf(testSet(weight = 95f, reps = 4)), WeightUnit.LBS, range)!!
        assertEquals(SuggestionKind.BUILD_REPS, s.kind)
        assertEquals(95f, s.weightLbs, 0.001f)
        assertEquals(6, s.targetReps)
    }

    @Test fun insideRangeAddsARep() {
        val s = suggestNext(listOf(testSet(weight = 95f, reps = 8)), WeightUnit.LBS, range)!!
        assertEquals(SuggestionKind.ADD_REP, s.kind)
        assertEquals(9, s.targetReps)
    }

    @Test fun topOfRangeAddsWeightAndResetsReps() {
        val s = suggestNext(listOf(testSet(weight = 95f, reps = 10)), WeightUnit.LBS, range)!!
        assertEquals(SuggestionKind.INCREASE_WEIGHT, s.kind)
        assertEquals(100f, s.weightLbs, 0.001f)
        assertEquals(6, s.targetReps)
    }

    @Test fun customStepIsUsed() {
        val s = suggestNext(listOf(testSet(weight = 95f, reps = 10)), WeightUnit.LBS, range, step = 2.5f)!!
        assertEquals(97.5f, s.weightLbs, 0.001f)
    }

    @Test fun warmUpsDoNotDriveTheTarget() {
        val s = suggestNext(
            listOf(testSet(number = 1, weight = 135f, reps = 12, warmup = true), testSet(number = 2, weight = 95f, reps = 8)),
            WeightUnit.LBS, range
        )!!
        assertEquals(95f, s.weightLbs, 0.001f)
    }

    @Test fun onlyWarmUpsMeansNoSuggestion() {
        assertNull(suggestNext(listOf(testSet(weight = 45f, reps = 10, warmup = true)), WeightUnit.LBS, range))
    }

    @Test fun kgSuggestionIsCleanDespiteConversionNoise() {
        val stored = displayToLbs(100f, WeightUnit.KG)
        val s = suggestNext(listOf(testSet(weight = stored, reps = 10)), WeightUnit.KG, range)!!
        assertEquals(102.5f, lbsToDisplay(s.weightLbs, WeightUnit.KG), 0.01f)
    }

    @Test fun stepForFallsBackToDefaultAndConvertsStoredLbs() {
        assertEquals(5f, stepFor(0f, WeightUnit.LBS), 0.001f)
        assertEquals(2.5f, stepFor(0f, WeightUnit.KG), 0.001f)
        assertEquals(2.5f, stepFor(displayToLbs(2.5f, WeightUnit.KG), WeightUnit.KG), 0.001f)
        assertEquals(1f, stepFor(1f, WeightUnit.LBS), 0.001f)
    }
}
