package com.kevan.hangry.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.util.AppDispatchers
import com.kevan.hangry.util.DefaultAppDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How far back the trend cards look. */
enum class SharePeriod(val days: Int) { WEEK(7), MONTH(30) }

data class ShareUiState(
    val period: SharePeriod = SharePeriod.WEEK,
    /** Null while loading. */
    val trends: ShareTrends? = null
)

/** Loads the stored days the trend cards on the Share screen are drawn from. */
class ShareViewModel(
    private val dailySummaryRepository: DailySummaryRepository,
    private val foodLogRepository: FoodLogRepository,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers(),
    private val today: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    init {
        load(SharePeriod.WEEK)
    }

    fun setPeriod(period: SharePeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load(period)
    }

    private fun load(period: SharePeriod) {
        viewModelScope.launch(dispatchers.io) {
            val end = today()
            val start = end.minusDays(period.days - 1L)
            val trends = runCatching {
                ShareTrends(
                    start = start,
                    end = end,
                    summaries = dailySummaryRepository.getSummariesBetween(start, end).first(),
                    scores = dailySummaryRepository.getScoresBetween(start, end).first(),
                    food = foodLogRepository.getBetween(start, end).first()
                )
            }.getOrDefault(ShareTrends(start, end))
            // A slower load for the other period mustn't land on top of this one.
            _state.update { if (it.period == period) it.copy(trends = trends) else it }
        }
    }

    companion object {
        fun provideFactory(dailySummaryRepository: DailySummaryRepository, foodLogRepository: FoodLogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ShareViewModel(dailySummaryRepository, foodLogRepository) as T
            }
    }
}
