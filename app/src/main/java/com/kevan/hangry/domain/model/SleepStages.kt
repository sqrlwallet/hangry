package com.kevan.hangry.domain.model

import java.time.Instant

/** A sleep stage as recorded by the device. [ASLEEP] is "sleeping" with no stage detail. */
enum class SleepStage(val code: Char) {
    AWAKE('A'), REM('R'), LIGHT('L'), DEEP('D'), ASLEEP('S');

    companion object {
        fun fromCode(code: Char): SleepStage? = entries.firstOrNull { it.code == code }
    }
}

data class SleepStageSegment(val stage: SleepStage, val start: Instant, val end: Instant)

/**
 * A night's stage timeline, stored on the sleep session as compact text:
 * "L0,1800;D1800,2400;..." - stage code, then start and end in seconds from the session start.
 */
object SleepStageCodec {
    fun encode(sessionStart: Instant, segments: List<SleepStageSegment>): String? =
        segments.sortedBy { it.start }
            .filter { it.end.isAfter(it.start) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(";") {
                "${it.stage.code}${it.start.epochSecond - sessionStart.epochSecond},${it.end.epochSecond - sessionStart.epochSecond}"
            }

    fun decode(sessionStart: Instant, text: String?): List<SleepStageSegment> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { part ->
            val stage = part.firstOrNull()?.let { SleepStage.fromCode(it) } ?: return@mapNotNull null
            val (from, to) = part.substring(1).split(',').takeIf { it.size == 2 }
                ?.map { it.toLongOrNull() ?: return@mapNotNull null } ?: return@mapNotNull null
            SleepStageSegment(stage, sessionStart.plusSeconds(from), sessionStart.plusSeconds(to))
        }
    }
}
