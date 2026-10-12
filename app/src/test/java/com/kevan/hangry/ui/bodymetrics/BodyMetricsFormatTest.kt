package com.kevan.hangry.ui.bodymetrics

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMetricsFormatTest {
    @Test
    fun groupThousands_groupsWholeNumbersOnly() {
        assertEquals("2,850", groupThousands("2850"))
        assertEquals("1,850–2,100", groupThousands("1850–2100"))
        assertEquals("120–160", groupThousands("120–160"))
        assertEquals("24.3", groupThousands("24.3"))
        assertEquals("0.12345", groupThousands("0.12345"))
        assertEquals("< 88", groupThousands("< 88"))
        assertEquals("—", groupThousands("—"))
    }
}
