package com.kevan.hangry

import com.kevan.hangry.domain.ai.StreamingReply
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingReplyTest {

    @Test
    fun `shows the reply received so far`() {
        assertEquals("Hi there, your", StreamingReply.visibleText("""{"reply": "Hi there, your"""))
    }

    @Test
    fun `nothing until the reply field starts`() {
        assertEquals("", StreamingReply.visibleText("""{"repl"""))
        assertEquals("", StreamingReply.visibleText("```js"))
    }

    @Test
    fun `stops at the closing quote and decodes escapes`() {
        val raw = """{"reply": "Line one\nSay \"hi\" • ok", "actions": [{"type": "LOG_MEAL"}]}"""
        assertEquals("Line one\nSay \"hi\" • ok", StreamingReply.visibleText(raw))
    }

    @Test
    fun `a half-received escape is held back`() {
        assertEquals("Almost", StreamingReply.visibleText("""{"reply": "Almost\u20"""))
        assertEquals("Almost", StreamingReply.visibleText("""{"reply": "Almost\"""))
    }

    @Test
    fun `fenced json and plain text both work`() {
        assertEquals("Hello", StreamingReply.visibleText("```json\n{\"reply\": \"Hello"))
        assertEquals("Plain answer", StreamingReply.visibleText("Plain answer"))
    }

    @Test
    fun `asterisks are cleaned like the final reply`() {
        assertEquals("• Drink **water**".replace("*", ""), StreamingReply.visibleText("""{"reply": "* Drink **water**"""))
    }
}
