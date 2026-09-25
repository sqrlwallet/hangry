package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.FastingPlan
import com.kevan.hangry.domain.model.FastingSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Opt-in intermittent fasting: a plan, the running fast and history. Off until the user turns it on. */
interface FastingRepository {
    fun observe(): Flow<FastingSnapshot>
    suspend fun current(): FastingSnapshot

    /** Turning off ends (and keeps) a running fast and cancels its reminder; history stays. */
    suspend fun setEnabled(enabled: Boolean)

    /** [customHours] is used for [FastingPlan.CUSTOM]. A running fast takes the new target. */
    suspend fun setPlan(plan: FastingPlan, customHours: Int? = null)
    suspend fun setGoalReminder(enabled: Boolean)

    suspend fun startFast(at: Instant = Instant.now())

    /** Ends the running fast. Returns false when there was none. */
    suspend fun endFast(at: Instant = Instant.now()): Boolean

    /** Moves the running fast's start, e.g. when the user forgot to tap Start. */
    suspend fun editStart(at: Instant)
    suspend fun deleteFast(id: Long)

    /** Re-arms the goal alarm (after boot, time changes, app updates). */
    suspend fun rescheduleReminder()
}
