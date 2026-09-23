package com.kevan.hangry

import com.kevan.hangry.domain.model.BodyUnits
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
}
