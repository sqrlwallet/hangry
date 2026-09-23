package com.kevan.hangry

import com.kevan.hangry.domain.calculation.HangryBodyFatCalculator
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HangryBodyFatCalculatorTest {

    private lateinit var calculator: HangryBodyFatCalculator

    @Before
    fun setup() {
        calculator = HangryBodyFatCalculator()
    }

    @Test
    fun testNavyFormula_maleBenchmark() {
        // Height 180cm, Neck 38cm, Waist 84cm, Weight 80kg, Chest 102cm
        val result = calculator.calculateNavyBodyFat(
            heightCm = 180.0,
            neckCm = 38.0,
            waistCm = 84.0,
            hipCm = 98.0,
            weightKg = 80.0,
            chestCm = 102.0,
            biologicalSex = BiologicalSex.MALE
        )

        assertNotNull(result)
        // In inches: 86.010 * log10(18.11) - 70.041 * log10(70.87) + 36.76 = 15.35% -> ~15.4%
        assertEquals(15.4, result!!.bodyFatPercentage, 0.2)
        assertEquals(BodyFatCategory.FITNESS, result.category)
        assertNotNull(result.fatMassKg)
        assertNotNull(result.leanMassKg)
        assertEquals(80.0, result.fatMassKg!! + result.leanMassKg!!, 0.2)

        // WHR: 84 / 98 = 0.857 -> 0.86
        assertNotNull(result.waistToHipRatio)
        // WHtR: 84 / 180 = 0.466 -> 0.47
        assertEquals(0.47, result.waistToHeightRatio!!, 0.05)
        // Chest-to-Waist (V-taper): 102 / 84 = 1.21
        assertEquals(1.21, result.chestToWaistRatio!!, 0.05)
    }

    @Test
    fun testNavyFormula_femaleRequiresHip() {
        // Female without hip measurement cannot be evaluated via Navy formula
        val resultWithoutHip = calculator.calculateNavyBodyFat(
            heightCm = 165.0,
            neckCm = 33.0,
            waistCm = 70.0,
            hipCm = null,
            weightKg = 60.0,
            chestCm = 88.0,
            biologicalSex = BiologicalSex.FEMALE
        )
        assertNull("Female formula must require hip measurement", resultWithoutHip)

        val resultWithHip = calculator.calculateNavyBodyFat(
            heightCm = 165.0,
            neckCm = 33.0,
            waistCm = 70.0,
            hipCm = 95.0,
            weightKg = 60.0,
            chestCm = 88.0,
            biologicalSex = BiologicalSex.FEMALE
        )
        assertNotNull("Female formula with hip measurement must succeed", resultWithHip)
        assertTrue(resultWithHip!!.bodyFatPercentage in 10.0..45.0)
    }

    @Test
    fun testNavyFormula_invalidGeometryReturnsNull() {
        // If neck is larger than waist for a male, log10 is undefined (diff <= 0)
        val impossibleGeometry = calculator.calculateNavyBodyFat(
            heightCm = 180.0,
            neckCm = 85.0,
            waistCm = 80.0,
            hipCm = null,
            weightKg = 75.0,
            chestCm = null,
            biologicalSex = BiologicalSex.MALE
        )
        assertNull("Negative or zero waist-neck difference must return null safely", impossibleGeometry)

        val zeroHeight = calculator.calculateNavyBodyFat(
            heightCm = 0.0,
            neckCm = 38.0,
            waistCm = 80.0,
            hipCm = null,
            weightKg = 75.0,
            chestCm = null,
            biologicalSex = BiologicalSex.MALE
        )
        assertNull("Zero height must return null safely", zeroHeight)
    }

    @Test
    fun testCategoryClassification_maleBands() {
        assertEquals(BodyFatCategory.ESSENTIAL_FAT, calculator.classifyCategory(5.0, BiologicalSex.MALE))
        assertEquals(BodyFatCategory.ATHLETIC, calculator.classifyCategory(10.0, BiologicalSex.MALE))
        assertEquals(BodyFatCategory.FITNESS, calculator.classifyCategory(15.0, BiologicalSex.MALE))
        assertEquals(BodyFatCategory.AVERAGE, calculator.classifyCategory(20.0, BiologicalSex.MALE))
        assertEquals(BodyFatCategory.ABOVE_AVERAGE, calculator.classifyCategory(26.0, BiologicalSex.MALE))
    }

    @Test
    fun testCategoryClassification_femaleBands() {
        assertEquals(BodyFatCategory.ESSENTIAL_FAT, calculator.classifyCategory(12.0, BiologicalSex.FEMALE))
        assertEquals(BodyFatCategory.ATHLETIC, calculator.classifyCategory(18.0, BiologicalSex.FEMALE))
        assertEquals(BodyFatCategory.FITNESS, calculator.classifyCategory(23.0, BiologicalSex.FEMALE))
        assertEquals(BodyFatCategory.AVERAGE, calculator.classifyCategory(28.0, BiologicalSex.FEMALE))
        assertEquals(BodyFatCategory.ABOVE_AVERAGE, calculator.classifyCategory(34.0, BiologicalSex.FEMALE))
    }
}
