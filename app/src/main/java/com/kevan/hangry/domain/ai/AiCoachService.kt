package com.kevan.hangry.domain.ai

import com.kevan.hangry.data.local.entity.CoachMessageEntity
import com.kevan.hangry.domain.model.CoachResponse

interface AiCoachService {
    /**
     * Sends the user's question or message along with recent conversation history
     * and 7-day health/journal context to the AI model.
     */
    suspend fun askCoach(
        userMessage: String,
        recentMessages: List<CoachMessageEntity> = emptyList(),
        /** Photos attached to this message, base64 JPEG. */
        imagesBase64: List<String> = emptyList(),
        /** When set, the reply is streamed and this gets the readable text so far. */
        onPartialReply: ((String) -> Unit)? = null
    ): Result<CoachResponse>
}
