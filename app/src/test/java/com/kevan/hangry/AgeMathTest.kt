package com.kevan.hangry

import com.kevan.hangry.domain.model.AgeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgeMathTest {

    @Test
    fun wholeYearsChangeOnTheBirthday() {
        val dob = LocalDate.of(1990, 9, 25)
        assertEquals(35, AgeMath.years(dob, LocalDate.of(2026, 9, 24)))
        assertEquals(36, AgeMath.years(dob, LocalDate.of(2026, 9, 25)))
    }

    @Test
    fun exactAgeIsTheFractionOfTheWayToTheNextBirthday() {
        val dob = LocalDate.of(1990, 3, 24)
        // Six months (184 of 365 days) after the 36th birthday.
        assertEquals(36.0 + 184.0 / 365.0, AgeMath.exact(dob, LocalDate.of(2026, 9, 24)), 1e-9)
        assertEquals(36.0, AgeMath.exact(dob, LocalDate.of(2026, 3, 24)), 1e-9)
    }

    @Test
    fun leapDayBirthdaysWork() {
        val dob = LocalDate.of(2000, 2, 29)
        assertEquals(25, AgeMath.years(dob, LocalDate.of(2026, 2, 28)))
        assertEquals(26, AgeMath.years(dob, LocalDate.of(2026, 3, 1)))
        assertTrue(AgeMath.exact(dob, LocalDate.of(2026, 2, 28)) in 25.0..26.0)
    }

    @Test
    fun onlyPlausibleAdultBirthdaysAreAccepted() {
        val today = LocalDate.of(2026, 9, 24)
        assertTrue(AgeMath.isPlausible(LocalDate.of(1990, 1, 1), today))
        assertFalse(AgeMath.isPlausible(LocalDate.of(2020, 1, 1), today))
        assertFalse(AgeMath.isPlausible(LocalDate.of(1900, 1, 1), today))
    }
}
