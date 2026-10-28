package com.kevan.hangry.domain.repository

import com.kevan.hangry.domain.model.RestModeState
import com.kevan.hangry.domain.model.RestReason
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Rest mode: on while unwell, injured or just needing rest. Streaks pause and strain stays gentle. */
interface RestModeRepository {
    val state: StateFlow<RestModeState>

    /** Turns rest mode on from [day], or changes the reason while it's on. */
    suspend fun start(reason: RestReason, day: LocalDate = LocalDate.now())

    /** Turns rest mode off; [day] is the last rest day. */
    suspend fun end(day: LocalDate = LocalDate.now())
}
