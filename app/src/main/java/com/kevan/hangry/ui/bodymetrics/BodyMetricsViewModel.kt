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
import java.time.LocalDate

data class BodyMetricsUiState(
    val isLoading: Boolean = true,
    val input: BodyMetricsInput = BodyMetricsInput(),
    val bodyFatScanDate: LocalDate? = null,
    val groups: List<BodyMetricGroup> = emptyList()
) {
    val allMetrics get() = groups.flatMap { it.metrics }
    val availableCount get() = allMetrics.count { it.isAvailable }

    /** Tape/profile inputs that would unlock the most still-missing metrics, most useful first. */
    val missingInputs: List<String>
        get() = allMetrics.filterNot { it.isAvailable }
            .flatMap { it.missingInputs }
            .filter { it in ENTERABLE_INPUTS }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
}

/** Inputs the user can fill in on the Body Fat calculator (vs. synced data or scans). */
private val ENTERABLE_INPUTS = setOf("Height", "Weight", "Age", "Sex", "Waist", "Hips", "Chest", "Neck")

class BodyMetricsViewModel(repository: BodyMetricsRepository) : ViewModel() {

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
