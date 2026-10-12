package com.kevan.hangry

import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.BodyUnits
import com.kevan.hangry.domain.model.LengthUnit
import com.kevan.hangry.domain.model.MetricBand
import com.kevan.hangry.domain.model.MetricInfo
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyUnitsTest {

    @Test
    fun feetAndInchesConvertToCentimetres() {
        assertEquals(177.8, BodyUnits.feetInchesToCm(5, 10.0), 0.01)
        assertEquals(5 to 10, BodyUnits.cmToFeetInches(177.8))
        assertEquals(6 to 0, BodyUnits.cmToFeetInches(182.6)) // 71.9 in rounds up to a whole 6 ft
    }

    @Test
    fun poundsConvertToKilograms() {
        assertEquals(72.57, BodyUnits.lbToKg(160.0), 0.01)
        assertEquals(160.0, BodyUnits.kgToLb(BodyUnits.lbToKg(160.0)), 1e-9)
    }

    @Test
    fun implausibleValuesAreRejected() {
        assertNull(BodyUnits.plausibleHeight(17.0)) // typed 17 instead of 170
        assertNull(BodyUnits.plausibleWeight(700.0))
        assertNull(BodyUnits.plausibleWeight(null))
        assertEquals(70.0, BodyUnits.plausibleWeight(70.0)!!, 0.0)
    }

    @Test
    fun stonesAndPoundsRoundTrip() {
        assertEquals(11 to 6.0, BodyUnits.kgToStonesPounds(BodyUnits.lbToKg(160.0)))
        // 13.96 lb rounds to a whole stone rather than showing "14.0 lb".
        assertEquals(12 to 0.0, BodyUnits.kgToStonesPounds(BodyUnits.lbToKg(167.96)))
        assertEquals(72.57, BodyUnits.stonesPoundsToKg(11, 6.0), 0.01)
    }

    @Test
    fun chartsAndRatesUsePoundsForStones() {
        assertEquals(160.0, BodyUnits.weightIn(BodyUnits.lbToKg(160.0), WeightUnit.ST_LB), 1e-9)
        assertEquals(72.0, BodyUnits.weightIn(72.0, WeightUnit.KG), 0.0)
        assertEquals(72.0, BodyUnits.weightToKg(BodyUnits.weightIn(72.0, WeightUnit.LB), WeightUnit.LB), 1e-9)
        assertEquals(32.0, BodyUnits.lengthIn(81.28, LengthUnit.IN), 1e-9)
    }

    @Test
    fun decimalCommasAreAccepted() {
        assertEquals(72.5, BodyUnits.parseNumber(" 72,5 ")!!, 0.0)
        assertNull(BodyUnits.parseNumber(""))
        assertNull(BodyUnits.parseNumber("abc"))
    }

    private val info = MetricInfo("", "18.5 × (1.80 m)² = 59.9 kg", "", "")

    @Test
    fun weightMetricsConvertToPounds() {
        val metric = BodyMetric(
            id = "weight_range", name = "Healthy weight range", value = 90.0, displayValue = "60–81", unit = "kg",
            bands = listOf(MetricBand("In range", 81.0, MetricTone.GOOD), MetricBand("Above range", null, MetricTone.CAUTION)),
            scaleMin = 48.0, scaleMax = 101.0, statusOverride = "9.0 kg above range", info = info
        )
        val lb = BodyUnits.convertMetric(metric, WeightUnit.LB, LengthUnit.CM)
        assertEquals("lb", lb.unit)
        assertEquals("132–179", lb.displayValue)
        assertEquals("19.8 lb above range", lb.status)
        assertEquals(198.4, lb.value!!, 0.1)
        assertEquals(178.6, lb.bands[0].upTo!!, 0.1)
        assertEquals("Above range", lb.band!!.label)
        // The formula explanation stays metric.
        assertEquals(info, lb.info)
        // Metric users and other units are untouched.
        assertEquals(metric, BodyUnits.convertMetric(metric, WeightUnit.KG, LengthUnit.IN))
    }

    @Test
    fun lengthMetricsConvertToInches() {
        val metric = BodyMetric(id = "waist_target", name = "Waist target", value = 85.0, displayValue = "< 90", unit = "cm", statusOverride = "5.0 cm under", info = info)
        val inches = BodyUnits.convertMetric(metric, WeightUnit.KG, LengthUnit.IN)
        assertEquals("< 35", inches.displayValue)
        assertEquals("in", inches.unit)
        assertEquals("2.0 in under", inches.status)
        assertEquals(metric, BodyUnits.convertMetric(metric, WeightUnit.LB, LengthUnit.CM))
    }
}
