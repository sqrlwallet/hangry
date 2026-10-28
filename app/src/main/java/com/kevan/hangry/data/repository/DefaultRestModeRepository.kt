package com.kevan.hangry.data.repository

import com.kevan.hangry.data.prefs.RestModePrefs
import com.kevan.hangry.domain.model.RestModeState
import com.kevan.hangry.domain.model.RestReason
import com.kevan.hangry.domain.repository.RestModeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class DefaultRestModeRepository(
    private val prefs: RestModePrefs,
    /** Called after every change, e.g. to refresh widgets. */
    private val onChanged: () -> Unit = {}
) : RestModeRepository {

    // Rest periods only change through here, so a StateFlow mirrors the stored ones.
    private val _state = MutableStateFlow(prefs.read())
    override val state: StateFlow<RestModeState> = _state
    private val mutex = Mutex()

    override suspend fun start(reason: RestReason, day: LocalDate) = update { it.started(day, reason) }

    override suspend fun end(day: LocalDate) = update { it.ended(day) }

    private suspend fun update(change: (RestModeState) -> RestModeState) = mutex.withLock {
        val next = change(_state.value)
        if (next == _state.value) return@withLock
        prefs.write(next)
        _state.value = next
        onChanged()
    }
}
