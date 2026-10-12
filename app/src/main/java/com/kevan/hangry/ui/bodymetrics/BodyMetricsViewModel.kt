package com.kevan.hangry.ui.bodymetrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.domain.model.BodyMetricGroup
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class BodyMetricsUiState(
    val isLoading: Boolean = true,
    val input: BodyMetricsInput = BodyMetricsInput(),
    val bodyFatScanDate: LocalDate? = null,
    val groups: List<BodyMetricGroup> = emptyList()
) {
    val allMetrics get() = groups.flatMap { it.metrics }
    val availableCount get() = allMetrics.count { it.isAvailable }
}

class BodyMetricsViewModel(private val repository: BodyMetricsRepository) : ViewModel() {

    fun logWeight(kg: Double, takenAt: Instant) {
        viewModelScope.launch { repository.logWeight(kg, takenAt) }
    }

    val uiState: StateFlow<BodyMetricsUiState> = repository.observe()
        .map { snapshot ->
            BodyMetricsUiState(
                isLoading = false,
                input = snapshot.input,
                bodyFatScanDate = snapshot.bodyFatScanDate,
                groups = snapshot.groups
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyMetricsUiState())

    companion object {
        fun provideFactory(repository: BodyMetricsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BodyMetricsViewModel(repository) as T
            }
    }
}
