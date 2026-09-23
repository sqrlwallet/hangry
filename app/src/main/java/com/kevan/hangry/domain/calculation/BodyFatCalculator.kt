package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyFatCategory
import com.kevan.hangry.domain.model.BodyFatCalculationResult
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface BodyFatCalculator {
    fun calculateNavyBodyFat(
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double?,
        weightKg: Double?,
        chestCm: Double?,
        biologicalSex: BiologicalSex
    ): BodyFatCalculationResult?

    fun classifyCategory(
        bodyFatPercentage: Double,
        biologicalSex: BiologicalSex
    ): BodyFatCategory
}

class HangryBodyFatCalculator : BodyFatCalculator {

    override fun calculateNavyBodyFat(
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double?,
        weightKg: Double?,
        chestCm: Double?,
        biologicalSex: BiologicalSex
    ): BodyFatCalculationResult? {
        if (heightCm <= 0.0 || neckCm <= 0.0 || waistCm <= 0.0) return null

        val bfPercentage = when (biologicalSex) {
            BiologicalSex.MALE -> calculateMaleFormula(heightCm, neckCm, waistCm)
            BiologicalSex.FEMALE -> {
                if (hipCm == null || hipCm <= 0.0) return null
                calculateFemaleFormula(heightCm, neckCm, waistCm, hipCm)
            }
            BiologicalSex.OTHER -> {
                // Average male and female formula results when hip is present, or fallback to male formula
                val maleVal = calculateMaleFormula(heightCm, neckCm, waistCm) ?: return null
                if (hipCm != null && hipCm > 0.0) {
                    val femaleVal = calculateFemaleFormula(heightCm, neckCm, waistCm, hipCm)
                    if (femaleVal != null) (maleVal + femaleVal) / 2.0 else maleVal
                } else {
                    maleVal
                }
            }
        } ?: return null

        val clampedBf = min(65.0, max(3.0, roundToOneDecimal(bfPercentage)))
        val category = classifyCategory(clampedBf, biologicalSex)

        val fatMassKg = weightKg?.let { roundToOneDecimal(it * (clampedBf / 100.0)) }
        val leanMassKg = if (weightKg != null && fatMassKg != null) {
            roundToOneDecimal(max(0.0, weightKg - fatMassKg))
        } else null

        val whr = if (hipCm != null && hipCm > 0.0) roundToOneDecimal(waistCm / hipCm * 10.0) / 10.0 else null
        val whtr = roundToOneDecimal(waistCm / heightCm * 10.0) / 10.0
        val ctwr = if (chestCm != null && chestCm > 0.0) roundToOneDecimal(chestCm / waistCm * 10.0) / 10.0 else null

        return BodyFatCalculationResult(
            bodyFatPercentage = clampedBf,
            category = category,
            fatMassKg = fatMassKg,
            leanMassKg = leanMassKg,
            waistToHipRatio = whr,
            waistToHeightRatio = whtr,
            chestToWaistRatio = ctwr,
            methodDescription = "U.S. Navy Circumference Standard"
        )
    }

    override fun classifyCategory(
        bodyFatPercentage: Double,
        biologicalSex: BiologicalSex
    ): BodyFatCategory {
        return when (biologicalSex) {
            BiologicalSex.MALE -> {
                when {
                    bodyFatPercentage < 6.0 -> BodyFatCategory.ESSENTIAL_FAT
                    bodyFatPercentage < 14.0 -> BodyFatCategory.ATHLETIC
                    bodyFatPercentage < 18.0 -> BodyFatCategory.FITNESS
                    bodyFatPercentage < 25.0 -> BodyFatCategory.AVERAGE
                    else -> BodyFatCategory.ABOVE_AVERAGE
                }
            }
            BiologicalSex.FEMALE -> {
                when {
                    bodyFatPercentage < 14.0 -> BodyFatCategory.ESSENTIAL_FAT
                    bodyFatPercentage < 21.0 -> BodyFatCategory.ATHLETIC
                    bodyFatPercentage < 25.0 -> BodyFatCategory.FITNESS
                    bodyFatPercentage < 32.0 -> BodyFatCategory.AVERAGE
                    else -> BodyFatCategory.ABOVE_AVERAGE
                }
            }
            BiologicalSex.OTHER -> {
                // Averaged classification bands
                when {
                    bodyFatPercentage < 10.0 -> BodyFatCategory.ESSENTIAL_FAT
                    bodyFatPercentage < 17.5 -> BodyFatCategory.ATHLETIC
                    bodyFatPercentage < 21.5 -> BodyFatCategory.FITNESS
                    bodyFatPercentage < 28.5 -> BodyFatCategory.AVERAGE
                    else -> BodyFatCategory.ABOVE_AVERAGE
                }
            }
        }
    }

    private fun calculateMaleFormula(heightCm: Double, neckCm: Double, waistCm: Double): Double? {
        val diffInches = (waistCm - neckCm) / 2.54
        val heightInches = heightCm / 2.54
        if (diffInches <= 0.0 || heightInches <= 0.0) return null
        // Official DoD / U.S. Navy standard: 86.010 * log10(waist - neck) - 70.041 * log10(height) + 36.76 (in inches)
        return 86.010 * log10(diffInches) - 70.041 * log10(heightInches) + 36.76
    }

    private fun calculateFemaleFormula(
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double
    ): Double? {
        val sumDiffInches = (waistCm + hipCm - neckCm) / 2.54
        val heightInches = heightCm / 2.54
        if (sumDiffInches <= 0.0 || heightInches <= 0.0) return null
        // Official DoD / U.S. Navy standard: 163.205 * log10(waist + hip - neck) - 97.684 * log10(height) - 78.387 (in inches)
        return 163.205 * log10(sumDiffInches) - 97.684 * log10(heightInches) - 78.387
    }

    private fun roundToOneDecimal(value: Double): Double {
        return (value * 10.0).roundToInt() / 10.0
    }
}
