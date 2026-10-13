package com.kevan.hangry

import com.kevan.hangry.ui.widget.DayWidgetText
import com.kevan.hangry.ui.widget.DayWidgetText.StrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayWidgetsTest {

    @Test
    fun `percent of a goal is whole and can pass 100`() {
        assertEquals(80, DayWidgetText.percent(6400.0, 8000.0))
        assertEquals(125, DayWidgetText.percent(10000.0, 8000.0))
        assertEquals(0, DayWidgetText.percent(0.0, 2500.0))
    }

    @Test
    fun `no goal means no percent`() {
        assertNull(DayWidgetText.percent(5000.0, null))
        assertNull(DayWidgetText.percent(5000.0, 0.0))
        assertEquals(0, DayWidgetText.progress(5000.0, null))
    }

    @Test
    fun `progress bars stop at full`() {
        assertEquals(48, DayWidgetText.progress(1200.0, 2500.0))
        assertEquals(100, DayWidgetText.progress(3000.0, 2500.0))
    }

    @Test
    fun `strain against the recommended range`() {
        assertEquals(StrainStatus.BELOW, DayWidgetText.strainStatus(6.0, 10.2, 13.5))
        assertEquals(StrainStatus.ON_TARGET, DayWidgetText.strainStatus(10.2, 10.2, 13.5))
        assertEquals(StrainStatus.ON_TARGET, DayWidgetText.strainStatus(13.5, 10.2, 13.5))
        assertEquals(StrainStatus.ABOVE, DayWidgetText.strainStatus(14.0, 10.2, 13.5))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals(7 to 32, DayWidgetText.hoursMinutes(452))
        assertEquals(0 to 45, DayWidgetText.hoursMinutes(45))
        assertEquals(0 to 0, DayWidgetText.hoursMinutes(-5))
    }
}
