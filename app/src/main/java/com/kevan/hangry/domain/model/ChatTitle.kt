package com.kevan.hangry.domain.model

/** A Dash chat's title, made from its first message so the history reads like a list of questions. */
object ChatTitle {
    const val MAX_LENGTH = 60

    /** First line of [message], cut at a word near 60 characters. A photo with no text is a "Photo chat". */
    fun from(message: String): String {
        val line = message.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isEmpty()) return "Photo chat"
        if (line.length <= MAX_LENGTH) return line
        val cut = line.take(MAX_LENGTH).substringBeforeLast(' ').takeIf { it.length >= 20 } ?: line.take(MAX_LENGTH)
        return cut.trimEnd(',', '.', ' ') + "…"
    }
}
