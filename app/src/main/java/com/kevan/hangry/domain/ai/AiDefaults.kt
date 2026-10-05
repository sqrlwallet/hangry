package com.kevan.hangry.domain.ai

object AiDefaults {
    /** Food, posture, body-fat and supplement photos, plus the weekly nutrition review. */
    const val DEFAULT_MODEL = "google/gemini-3.8-flash"
    const val COACH_MODEL = "openai/gpt-5.6-luna"

    /** Earlier defaults: a profile still holding one of these moves up to [DEFAULT_MODEL]. */
    private val RETIRED_DEFAULT_MODELS = setOf("google/gemini-2.5-flash")

    /** The analysis model to use for a saved preference - the default when unset or a retired default. */
    fun analysisModel(preferred: String?): String =
        preferred?.trim()?.takeIf { it.isNotBlank() && it !in RETIRED_DEFAULT_MODELS } ?: DEFAULT_MODEL
}

/** Strips a ```json ... ``` (or bare ```) fence a model commonly wraps its JSON answer in. */
fun extractJsonPayload(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    val withoutOpeningFence = trimmed.substringAfter('\n', trimmed)
    return withoutOpeningFence.substringBeforeLast("```").trim()
}
