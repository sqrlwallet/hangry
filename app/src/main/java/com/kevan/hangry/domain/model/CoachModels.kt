package com.kevan.hangry.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExtractedJournalEntry(
    val category: String = "NOTE", // e.g. "PROBLEM", "DIET", "INJURY", "HABIT", "GOAL", "HEALTH", "NOTE"
    val summary: String,
    val content: String
)

@Serializable
data class CoachResponse(
    val reply: String,
    val journalEntry: ExtractedJournalEntry? = null
)
