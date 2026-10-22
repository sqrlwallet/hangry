package com.kevan.hangry.ui.report

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.prefs.UnitPrefs
import com.kevan.hangry.data.report.DoctorReportGenerator
import com.kevan.hangry.data.report.GeneratedReport
import com.kevan.hangry.data.report.ReportUnits
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.report.ReportOptions
import com.kevan.hangry.domain.report.ReportPeriod
import com.kevan.hangry.domain.report.ReportSection
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.util.AppDispatchers
import com.kevan.hangry.util.DefaultAppDispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DoctorReportUiState(
    val options: ReportOptions = ReportOptions(),
    /** The cycle section is only offered when sex is set to female. */
    val offersCycle: Boolean = false,
    val isGenerating: Boolean = false,
    val result: GeneratedReport? = null,
    /** The options changed since [result] was made. */
    val isStale: Boolean = false
) {
    /** Sections listed on screen, in report order. */
    val offeredSections: List<ReportSection>
        get() = ReportSection.entries.filter { it != ReportSection.CYCLE || offersCycle }
}

sealed interface DoctorReportEvent {
    data object Saved : DoctorReportEvent
    data object SaveFailed : DoctorReportEvent
    data object GenerateFailed : DoctorReportEvent
}

class DoctorReportViewModel(
    private val appContext: Context,
    private val generator: DoctorReportGenerator,
    private val healthRecordsRepository: HealthRecordsRepository,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers()
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(DoctorReportUiState(options = restoreOptions()))
    val state: StateFlow<DoctorReportUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DoctorReportEvent>()
    val events: SharedFlow<DoctorReportEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch(dispatchers.io) {
            val female = runCatching { healthRecordsRepository.current().sex == BiologicalSex.FEMALE }.getOrDefault(false)
            _state.update { it.copy(offersCycle = female) }
        }
    }

    fun setPeriod(period: ReportPeriod) = updateOptions { it.copy(period = period) }

    fun setSection(section: ReportSection, included: Boolean) =
        updateOptions { it.copy(sections = if (included) it.sections + section else it.sections - section) }

    fun setName(name: String) = updateOptions { it.copy(patientName = name.take(MAX_NAME)) }

    fun setNotes(notes: String) = updateOptions { it.copy(visitNotes = notes.take(MAX_NOTES)) }

    private fun updateOptions(change: (ReportOptions) -> ReportOptions) {
        _state.update { state ->
            val options = change(state.options)
            state.copy(options = options, isStale = state.result != null && options != state.result.report.options)
        }
    }

    fun generate() {
        if (_state.value.isGenerating) return
        val options = _state.value.options.let { o ->
            // A cycle section the screen didn't offer stays out of the report.
            if (_state.value.offersCycle) o else o.copy(sections = o.sections - ReportSection.CYCLE)
        }
        saveOptions(options)
        // Unit prefs are snapshot state - read them here on the main thread.
        val units = ReportUnits(UnitPrefs.weightUnit(appContext), UnitPrefs.lengthUnit(appContext))
        _state.update { it.copy(isGenerating = true) }
        viewModelScope.launch(dispatchers.io) {
            val result = runCatching { generator.generate(options, units) }
            _state.update { it.copy(isGenerating = false, result = result.getOrNull() ?: it.result, isStale = result.isFailure && it.isStale) }
            if (result.isFailure) _events.emit(DoctorReportEvent.GenerateFailed)
        }
    }

    /** Copies the finished PDF to a file the user picked. */
    fun saveTo(uri: Uri) {
        val file = _state.value.result?.file ?: return
        viewModelScope.launch {
            val ok = withContext(dispatchers.io) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
            }
            _events.emit(if (ok) DoctorReportEvent.Saved else DoctorReportEvent.SaveFailed)
        }
    }

    // The period, sections and name are remembered for next time; visit notes are one-off.

    private fun restoreOptions(): ReportOptions {
        val period = prefs.getString(KEY_PERIOD, null)?.let { runCatching { ReportPeriod.valueOf(it) }.getOrNull() } ?: ReportPeriod.DAYS_90
        val sections = prefs.getStringSet(KEY_SECTIONS, null)
            ?.mapNotNull { runCatching { ReportSection.valueOf(it) }.getOrNull() }?.toSet()
            ?: ReportSection.entries.toSet()
        return ReportOptions(period = period, sections = sections, patientName = prefs.getString(KEY_NAME, null))
    }

    private fun saveOptions(options: ReportOptions) {
        prefs.edit()
            .putString(KEY_PERIOD, options.period.name)
            .putStringSet(KEY_SECTIONS, options.sections.map { it.name }.toSet())
            .putString(KEY_NAME, options.patientName?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
    }

    companion object {
        private const val PREFS = "hangry_doctor_report"
        private const val KEY_PERIOD = "period"
        private const val KEY_SECTIONS = "sections"
        private const val KEY_NAME = "name"
        private const val MAX_NAME = 80
        private const val MAX_NOTES = 1200

        fun provideFactory(context: Context, generator: DoctorReportGenerator, healthRecordsRepository: HealthRecordsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DoctorReportViewModel(context.applicationContext, generator, healthRecordsRepository) as T
            }
    }
}
