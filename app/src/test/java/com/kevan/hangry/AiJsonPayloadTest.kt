package com.kevan.hangry

import com.kevan.hangry.domain.ai.extractJsonPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class AiJsonPayloadTest {

    @Test
    fun plainJson_isReturnedUnchanged() {
        val raw = """{"foodName":"Apple","calories":95}"""
        assertEquals(raw, extractJsonPayload(raw))
    }

    @Test
    fun jsonFencedWithLanguageTag_isUnwrapped() {
        val raw = "```json\n{\"foodName\":\"Apple\",\"calories\":95}\n```"
        assertEquals("""{"foodName":"Apple","calories":95}""", extractJsonPayload(raw))
    }

    @Test
    fun bareFence_isUnwrapped() {
        val raw = "```\n{\"valid\":true,\"score\":80}\n```"
        assertEquals("""{"valid":true,"score":80}""", extractJsonPayload(raw))
    }

    @Test
    fun surroundingWhitespace_isTrimmed() {
        val raw = "  \n {\"valid\":false} \n  "
        assertEquals("""{"valid":false}""", extractJsonPayload(raw))
    }
}
