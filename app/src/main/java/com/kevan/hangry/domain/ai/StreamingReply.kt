package com.kevan.hangry.domain.ai

/**
 * Dash replies with a JSON object ({"reply": "...", "actions": [...]}), so a half-streamed
 * response isn't readable as-is. This pulls out the part of "reply" received so far - decoding
 * escapes and stopping cleanly mid-escape - so it can be shown word by word. If the model is
 * answering in plain text instead of JSON, that text is shown directly.
 */
object StreamingReply {

    private val replyKey = Regex(""""reply"\s*:\s*"""")

    fun visibleText(raw: String): String {
        val start = raw.trimStart()
        // A code fence ("```json\n{...") - wait for its first line to end before reading on.
        val body = if (start.startsWith("```")) {
            val newline = start.indexOf('\n')
            if (newline < 0) return ""
            start.substring(newline + 1).trimStart()
        } else start
        if (body.isEmpty() || body.startsWith("`")) return ""
        if (!body.startsWith("{")) return tidy(raw.trim())
        val match = replyKey.find(body) ?: return ""
        val out = StringBuilder()
        var i = match.range.last + 1
        while (i < body.length) {
            val c = body[i]
            when {
                c == '"' -> break
                c == '\\' -> {
                    if (i + 1 >= body.length) break
                    when (val e = body[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> {}
                        'b', 'f' -> {}
                        'u' -> {
                            if (i + 6 > body.length) break
                            body.substring(i + 2, i + 6).toIntOrNull(16)?.let { out.append(it.toChar()) }
                            i += 4
                        }
                        else -> out.append(e) // \" \\ \/
                    }
                    i += 2
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return tidy(out.toString())
    }

    /** Same asterisk clean-up the final reply gets, so the text doesn't jump when it lands. */
    private fun tidy(text: String): String = text
        .replace(Regex("""(?m)^\s*\*\s+"""), "• ")
        .replace("*", "")
}
