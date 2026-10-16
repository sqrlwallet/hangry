package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.local.entity.SleepSessionEntity

/**
 * Collapses a wake-up day's sleep sessions into one, so naps and split sleeps add up instead of
 * the day only counting its single longest session.
 *
 * Duration, time in bed and the stage minutes are summed, so a nap is added to the day's totals
 * and to the score, need and debt worked out from them. Bed and wake times stay the main
 * (longest) session's, so an afternoon nap never moves your regular bed/wake times.
 */
object SleepSessions {

    /** All of a day's sessions as one period, or null when there are none. */
    fun combine(sessions: List<SleepSessionEntity>): SleepSessionEntity? {
        if (sessions.isEmpty()) return null
        if (sessions.size == 1) return sessions.first()

        val main = sessions.maxBy { it.durationMinutes }
        // Sum each stage that any session recorded. A stage stays null only when nothing did.
        val deep = sumOrNull(sessions) { it.deepSleepMinutes }
        val rem = sumOrNull(sessions) { it.remSleepMinutes }
        val light = sumOrNull(sessions) { it.lightSleepMinutes }
        val awake = sumOrNull(sessions) { it.awakeMinutes }
        val duration = sessions.sumOf { it.durationMinutes }
        // Time in bed is summed too; a session that didn't record it falls back to its own
        // duration so the total never undercuts time asleep.
        val timeInBed = if (sessions.any { it.timeInBedMinutes != null }) {
            sessions.sumOf { it.timeInBedMinutes ?: it.durationMinutes }
        } else {
            null
        }

        return main.copy(
            id = 0,
            sourceRecordId = null,
            sourcePackageName = null,
            recordFingerprint = "combined:" + sessions.joinToString("+") { it.recordFingerprint },
            durationMinutes = duration,
            timeInBedMinutes = timeInBed,
            deepSleepMinutes = deep,
            remSleepMinutes = rem,
            lightSleepMinutes = light,
            awakeMinutes = awake,
            // The stage timeline belongs to one session; the main night's is kept for the hypnogram.
            stageSegments = main.stageSegments,
            sleepQualityScore = null
        )
    }

    private inline fun sumOrNull(
        sessions: List<SleepSessionEntity>,
        select: (SleepSessionEntity) -> Int?
    ): Int? = sessions.mapNotNull(select).takeIf { it.isNotEmpty() }?.sum()
}
