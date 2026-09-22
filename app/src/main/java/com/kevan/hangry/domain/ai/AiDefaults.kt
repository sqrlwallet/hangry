package com.kevan.hangry.domain.ai

object AiDefaults {
    const val DEFAULT_MODEL = "google/gemini-2.5-flash"
    const val COACH_MODEL = "openai/gpt-5.6-luna"
}

/** Strips a ```json ... ``` (or bare ```) fence a model commonly wraps its JSON answer in. */
fun extractJsonPayload(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    val withoutOpeningFence = trimmed.substringAfter('\n', trimmed)
    return withoutOpeningFence.substringBeforeLast("```").trim()
}
