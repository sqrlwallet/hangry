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

    @Test
    fun textBeforeAndAfter_isCutAway() {
        val raw = "Here's the estimate:\n{\"foodName\":\"Apple\",\"calories\":95}\nHope that helps!"
        assertEquals("""{"foodName":"Apple","calories":95}""", extractJsonPayload(raw))
    }

    @Test
    fun fenceNotAtTheStart_isUnwrapped() {
        val raw = "Sure, here you go:\n```json\n{\"valid\":true}\n```\nAnything else?"
        assertEquals("""{"valid":true}""", extractJsonPayload(raw))
    }

    @Test
    fun strayBraceInTrailingText_fallsBackToTheBalancedObject() {
        val raw = "{\"reply\":\"Use {curly} braces\",\"n\":1} and a stray } here"
        assertEquals("{\"reply\":\"Use {curly} braces\",\"n\":1}", extractJsonPayload(raw))
    }

    @Test
    fun braceInIntroText_isSkipped() {
        val raw = "Format {name}: {\"foodName\":\"Egg\"}"
        assertEquals("{\"foodName\":\"Egg\"}", extractJsonPayload(raw))
    }

    @Test
    fun unclosedFence_stillYieldsTheObject() {
        val raw = "```json\n{\"valid\":false}"
        assertEquals("""{"valid":false}""", extractJsonPayload(raw))
    }

    @Test
    fun noJsonAtAll_returnsTheTrimmedText() {
        assertEquals("Sorry, I can't help.", extractJsonPayload("  Sorry, I can't help. "))
    }
}
