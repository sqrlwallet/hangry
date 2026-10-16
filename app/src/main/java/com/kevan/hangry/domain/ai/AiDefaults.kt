package com.kevan.hangry.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object AiDefaults {
    /** Food, posture, body-fat and supplement photos, plus the weekly nutrition review. */
    const val DEFAULT_MODEL = "google/gemini-3.8-flash"
    const val COACH_MODEL = "openai/gpt-5.6-luna"

    /**
     * Lean enough to stay grounded in the user's numbers, warm enough to sound like a person.
     * [COACH_MAX_TOKENS] caps runaway replies (and cost) well above a normal coaching answer.
     */
    const val COACH_TEMPERATURE = 0.7
    const val COACH_MAX_TOKENS = 1600

    /** Earlier defaults: a profile still holding one of these moves up to [DEFAULT_MODEL]. */
    private val RETIRED_DEFAULT_MODELS = setOf("google/gemini-2.5-flash")

    /** The analysis model to use for a saved preference - the default when unset or a retired default. */
    fun analysisModel(preferred: String?): String =
        preferred?.trim()?.takeIf { it.isNotBlank() && it !in RETIRED_DEFAULT_MODELS } ?: DEFAULT_MODEL
}

/**
 * The one Json config for reading model output: unknown keys are ignored, a null or mistyped
 * value for a field with a default falls back to that default, and lenient parsing tolerates
 * quoted numbers/booleans and unquoted strings. Models drift from the schema in small ways and a
 * single stray field shouldn't throw away the whole answer.
 */
val AiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

/**
 * Pulls the JSON object out of a model's answer: strips a ```json ... ``` (or bare ```) fence
 * wherever it sits, then cuts away chatter before and after the object ("Here's the estimate:
 * {...} Let me know if..."). Prefers the span from the first `{` to the last `}`; if that isn't
 * valid JSON (e.g. a stray brace in trailing text), the first balanced object that parses wins.
 * An answer with no `{` at all comes back trimmed so the caller's own error handling applies.
 */
fun extractJsonPayload(raw: String): String {
    val text = unfence(raw.trim())
    val start = text.indexOf('{')
    if (start < 0) return text
    val end = text.lastIndexOf('}')
    val widest = if (end > start) text.substring(start, end + 1) else text.substring(start)
    if (isJsonObject(widest)) return widest
    var from = start
    repeat(MAX_OBJECT_CANDIDATES) {
        if (from < 0) return@repeat
        balancedObjectAt(text, from)?.takeIf { isJsonObject(it) }?.let { return it }
        from = text.indexOf('{', from + 1)
    }
    return widest
}

private const val MAX_OBJECT_CANDIDATES = 20

/** The contents of the first fenced block that holds an object, else the text unchanged. */
private fun unfence(text: String): String {
    val open = text.indexOf("```")
    if (open < 0) return text
    val bodyStart = text.indexOf('\n', open).takeIf { it >= 0 }?.plus(1) ?: (open + 3)
    val close = text.indexOf("```", bodyStart)
    val body = (if (close >= 0) text.substring(bodyStart, close) else text.substring(bodyStart)).trim()
    return if ('{' in body) body else text
}

/** The `{...}` starting at [start] with braces balanced (ignoring braces inside strings), or null if it never closes. */
private fun balancedObjectAt(text: String, start: Int): String? {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '{' -> depth++
            c == '}' -> if (--depth == 0) return text.substring(start, i + 1)
        }
    }
    return null
}

private fun isJsonObject(candidate: String): Boolean =
    runCatching { AiJson.parseToJsonElement(candidate) is JsonObject }.getOrDefault(false)
