package com.kevan.hangry.ui.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.domain.model.FastingPlan
import com.kevan.hangry.domain.model.FastingSnapshot
import com.kevan.hangry.domain.repository.FastingRepository
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FastingViewModel(
    private val repository: FastingRepository,
    healthRecordsRepository: HealthRecordsRepository
) : ViewModel() {

    val snapshot: StateFlow<FastingSnapshot?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Fasting during pregnancy isn't advised; the screen says so before it's turned on. */
    val isPregnant: StateFlow<Boolean> = healthRecordsRepository.observe()
        .map { it.isPregnant }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEnabled(enabled: Boolean) = launch { repository.setEnabled(enabled) }
    fun setPlan(plan: FastingPlan, customHours: Int? = null) = launch { repository.setPlan(plan, customHours) }
    fun setGoalReminder(enabled: Boolean) = launch { repository.setGoalReminder(enabled) }
    fun start() = launch { repository.startFast() }
    fun end() = launch { repository.endFast() }
    fun delete(id: Long) = launch { repository.deleteFast(id) }

    /** "I started at 8:00 PM" - the latest such time that isn't in the future. */
    fun startAt(time: LocalTime) = launch { repository.startFast(mostRecent(time)) }
    fun editStart(time: LocalTime) = launch { repository.editStart(mostRecent(time)) }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    companion object {
        fun mostRecent(time: LocalTime, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant {
            val today = LocalDate.ofInstant(now, zone).atTime(time).atZone(zone).toInstant()
            return if (today.isAfter(now)) today.minusSeconds(24 * 3600) else today
        }

        fun provideFactory(repository: FastingRepository, healthRecordsRepository: HealthRecordsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FastingViewModel(repository, healthRecordsRepository) as T
            }
    }
}
