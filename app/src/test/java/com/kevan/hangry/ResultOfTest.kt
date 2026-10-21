package com.kevan.hangry

import com.kevan.hangry.util.resultOf
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultOfTest {

    @Test
    fun returnsTheValueOnSuccess() {
        assertEquals(42, resultOf { 42 }.getOrThrow())
    }

    @Test
    fun turnsAnExceptionIntoAFailure() {
        val result = resultOf { error("boom") }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun letsCancellationThrough() {
        assertThrows(CancellationException::class.java) { resultOf { throw CancellationException("stop") } }
    }
}
