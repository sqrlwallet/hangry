package com.kevan.hangry.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Age from a date of birth - always current, unlike an age typed in once. */
object AgeMath {

    /** Whole years, as on a birthday card. */
    fun years(dateOfBirth: LocalDate, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.YEARS.between(dateOfBirth, today).toInt().coerceAtLeast(0)

    /** Exact age in years, e.g. 34.7 - whole years plus the fraction of the way to the next birthday. */
    fun exact(dateOfBirth: LocalDate, today: LocalDate = LocalDate.now()): Double {
        val whole = years(dateOfBirth, today)
        val lastBirthday = dateOfBirth.plusYears(whole.toLong())
        val nextBirthday = dateOfBirth.plusYears(whole + 1L)
        val fraction = ChronoUnit.DAYS.between(lastBirthday, today).toDouble() /
            ChronoUnit.DAYS.between(lastBirthday, nextBirthday).toDouble()
        return whole + fraction
    }

    /** Plausible birthdays for an adult health app: 13 to 110 years ago. */
    fun isPlausible(dateOfBirth: LocalDate, today: LocalDate = LocalDate.now()) = years(dateOfBirth, today) in 13..110
}
