package com.kevan.hangry.ui.healthrecords

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.R
import com.kevan.hangry.data.ai.OpenRouterException
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.domain.ai.LabReportAnalyzer
import com.kevan.hangry.domain.ai.ScannedLabValue
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.RecordSource
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.util.clearCapturedImageCache
import com.kevan.hangry.util.readImageAsBase64Jpeg
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

data class HealthConnectImportState(
    val available: Boolean = false,
    val medicalRecordsSupported: Boolean = false,
    /** Not yet granted; empty when everything we'd ask for is already granted. */
    val permissionsToRequest: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val message: String? = null
)

/** One scanned value under review; [valueText]/[secondaryText] are in the marker's canonical unit. */
data class ReportScanRow(
    val scanned: ScannedLabValue,
    val checked: Boolean,
    val valueText: String,
    val secondaryText: String
) {
    val value: Double? get() = valueText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val secondary: Double? get() = secondaryText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val isComplete: Boolean
        get() = scanned.type != null && value != null && (!scanned.type.hasSecondaryValue || secondary != null)

    companion object {
        fun from(scanned: ScannedLabValue) = ReportScanRow(
            scanned = scanned,
            checked = scanned.selectedByDefault,
            valueText = scanned.value?.let { format(scanned.type, it) }.orEmpty(),
            secondaryText = scanned.secondaryValue?.let { format(scanned.type, it) }.orEmpty()
        )

        private fun format(type: MarkerType?, value: Double): String =
            String.format(Locale.US, "%.${type?.decimals ?: 1}f", value)
    }
}

/** Scan-a-report flow: pages to read, then (once read) rows to review. Nothing is saved until [HealthRecordsViewModel.saveScan]. */
data class ReportScanState(
    val pages: List<Uri> = emptyList(),
    val isReading: Boolean = false,
    val error: String? = null,
    /** Non-null once the report has been read. */
    val rows: List<ReportScanRow>? = null,
    val date: LocalDate = LocalDate.now()
) {
    val toAdd: List<ReportScanRow> get() = rows.orEmpty().filter { it.checked && it.isComplete }
}

class HealthRecordsViewModel(
    private val repository: HealthRecordsRepository,
    private val healthConnect: HealthConnectDataSource,
    summaries: DailySummaryRepository? = null,
    @field:SuppressLint("StaticFieldLeak") private val context: Context? = null,
    userProfileRepository: UserProfileRepository? = null,
    private val labReportAnalyzer: LabReportAnalyzer? = null
) : ViewModel() {

    val aiEnabled: StateFlow<Boolean> = (userProfileRepository?.getProfile()?.map { it?.aiFeaturesEnabled == true } ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _scan = MutableStateFlow<ReportScanState?>(null)
    val scan: StateFlow<ReportScanState?> = _scan.asStateFlow()

    /** How many readings the last scan added, for the snackbar. */
    private val _scanSaved = MutableStateFlow<Int?>(null)
    val scanSaved: StateFlow<Int?> = _scanSaved.asStateFlow()

    /** Recent nights' skin temperature change, for spotting the post-ovulation rise. */
    val skinTempByDate: StateFlow<Map<LocalDate, Double>> = (summaries?.getSummariesBetween(LocalDate.now().minusDays(365), LocalDate.now())
        ?.map { list -> list.mapNotNull { s -> s.skinTempDeltaCelsius?.let { s.date to it } }.toMap() }
        ?: flowOf(emptyMap()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Readings / periods deleted on screen but still undoable - hidden now, removed on commit. */
    private val hiddenReadings = MutableStateFlow<Set<Long>>(emptySet())
    private val hiddenPeriods = MutableStateFlow<Set<Long>>(emptySet())

    val records: StateFlow<HealthRecordsSnapshot> = combine(repository.observe(), hiddenReadings, hiddenPeriods) { snapshot, readings, periods ->
        if (readings.isEmpty() && periods.isEmpty()) snapshot
        else snapshot.copy(
            readings = snapshot.readings.filter { it.id !in readings },
            periods = snapshot.periods.filter { it.id !in periods }
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthRecordsSnapshot())

    private val _import = MutableStateFlow(HealthConnectImportState())
    val import: StateFlow<HealthConnectImportState> = _import.asStateFlow()

    init {
        viewModelScope.launch {
            refreshPermissions()
            // Quietly pull anything new if access was granted earlier.
            if (_import.value.available && _import.value.permissionsToRequest.isEmpty()) importNow(quiet = true)
        }
    }

    fun addReading(type: MarkerType, value: Double, secondary: Double?, measuredAt: Instant, context: GlucoseContext?, note: String?) =
        viewModelScope.launch { repository.addReading(type, value, secondary, measuredAt, context, note) }

    fun updateReading(id: Long, value: Double, secondary: Double?, measuredAt: Instant, context: GlucoseContext?, note: String?) =
        viewModelScope.launch { repository.updateReading(id, value, secondary, measuredAt, context, note) }

    /** Hides the reading at once; it (and its Health Connect copy) is only removed on [commitReadingDelete]. */
    fun hideReading(id: Long) = hiddenReadings.update { it + id }

    fun undoReadingDelete(id: Long) = hiddenReadings.update { it - id }

    // NonCancellable: the undo window can close as the screen is leaving.
    fun commitReadingDelete(id: Long) = viewModelScope.launch(NonCancellable) {
        repository.deleteReading(id)
        hiddenReadings.update { it - id }
    }

    fun setGoal(type: MarkerType, target: Double, secondary: Double?, date: LocalDate?) =
        viewModelScope.launch { repository.setGoal(type, target, secondary, date) }

    fun clearGoal(type: MarkerType) = viewModelScope.launch { repository.clearGoal(type) }

    fun addProfileItem(kind: HealthProfileKind, name: String, note: String?) =
        viewModelScope.launch { repository.addProfileItem(kind, name, note) }

    fun deleteProfileItem(id: Long) = viewModelScope.launch { repository.deleteProfileItem(id) }

    fun logPeriod(start: LocalDate, end: LocalDate?) = viewModelScope.launch { repository.logPeriod(start, end) }

    fun updatePeriod(id: Long, start: LocalDate, end: LocalDate?) = viewModelScope.launch { repository.updatePeriod(id, start, end) }

    fun hidePeriod(id: Long) = hiddenPeriods.update { it + id }

    fun undoPeriodDelete(id: Long) = hiddenPeriods.update { it - id }

    fun commitPeriodDelete(id: Long) = viewModelScope.launch(NonCancellable) {
        repository.deletePeriod(id)
        hiddenPeriods.update { it - id }
    }

    fun setPregnancy(isPregnant: Boolean, dueDate: LocalDate?) =
        viewModelScope.launch { repository.setPregnancy(isPregnant, dueDate) }

    fun openScan() {
        _scan.value = ReportScanState()
    }

    fun onScanPages(uris: List<Uri>) = _scan.update { s ->
        (s ?: ReportScanState()).copy(pages = ((s?.pages ?: emptyList()) + uris).take(MAX_SCAN_PAGES), error = null)
    }

    fun restartScan() {
        context?.clearCapturedImageCache()
        _scan.value = ReportScanState()
    }

    fun readScan() {
        val state = _scan.value ?: return
        val ctx = context ?: return
        val analyzer = labReportAnalyzer ?: return
        if (state.pages.isEmpty() || state.isReading) return
        _scan.update { it?.copy(isReading = true, error = null) }
        viewModelScope.launch {
            // Report text is small - a larger long edge than food photos keeps it legible.
            val images = state.pages.mapNotNull { ctx.readImageAsBase64Jpeg(it, maxDimension = 1600, quality = 85) }
            if (images.isEmpty()) {
                _scan.update { it?.copy(isReading = false, error = ctx.getString(R.string.body_records_scan_error_photo)) }
                return@launch
            }
            analyzer.readReport(images, repository.current().visibleMarkers).fold(
                onSuccess = { report ->
                    _scan.update {
                        if (report.values.isEmpty()) it?.copy(isReading = false, error = ctx.getString(R.string.body_records_scan_error_empty))
                        else it?.copy(
                            isReading = false,
                            // Tracked markers first, in the report's order.
                            rows = (report.tracked + report.untracked).map(ReportScanRow::from),
                            date = report.reportDate ?: LocalDate.now()
                        )
                    }
                },
                onFailure = { err ->
                    _scan.update {
                        it?.copy(isReading = false, error = (err as? OpenRouterException)?.message ?: ctx.getString(R.string.body_records_scan_error_generic))
                    }
                }
            )
        }
    }

    fun updateScanRow(index: Int, transform: (ReportScanRow) -> ReportScanRow) = _scan.update { s ->
        s?.copy(rows = s.rows?.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    fun setScanDate(date: LocalDate) = _scan.update { it?.copy(date = date) }

    fun saveScan() {
        val state = _scan.value ?: return
        val rows = state.toAdd
        if (rows.isEmpty()) return
        dismissScan()
        val at = if (state.date == LocalDate.now()) Instant.now()
        else state.date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant()
        viewModelScope.launch {
            val added = rows.count { row ->
                val type = row.scanned.type ?: return@count false
                runCatching {
                    repository.addReading(type, row.value!!, row.secondary, at, row.scanned.glucoseContext, null, RecordSource.AI_SCAN)
                }.isSuccess
            }
            _scanSaved.value = added
        }
    }

    fun dismissScan() {
        _scan.value = null
        context?.clearCapturedImageCache()
    }

    fun clearScanSaved() {
        _scanSaved.value = null
    }

    /** After the Health Connect permission dialog closes, whatever was granted is imported. */
    fun onPermissionResult() = viewModelScope.launch {
        refreshPermissions()
        importNow(quiet = false)
    }

    fun importFromHealthConnect() = viewModelScope.launch { importNow(quiet = false) }

    fun clearImportMessage() = _import.update { it.copy(message = null) }

    private suspend fun refreshPermissions() {
        val available = healthConnect.isAvailable()
        if (!available) {
            _import.update { it.copy(available = false) }
            return
        }
        val includeCycle = repository.current().showsFemaleHealth
        val wanted = healthConnect.healthRecordPermissions(includeCycle)
        val granted = healthConnect.grantedPermissions()
        _import.update {
            it.copy(
                available = true,
                medicalRecordsSupported = healthConnect.supportsMedicalRecords(),
                permissionsToRequest = wanted - granted
            )
        }
    }

    private suspend fun importNow(quiet: Boolean) {
        _import.update { it.copy(isImporting = true) }
        val result = runCatching { repository.importFromHealthConnect() }.getOrNull()
        _import.update {
            it.copy(
                isImporting = false,
                message = when {
                    result == null -> if (quiet) null else "Couldn't read from Health Connect."
                    result.total > 0 -> "Imported ${result.total} new record${if (result.total == 1) "" else "s"} from Health Connect."
                    quiet -> null
                    else -> "Up to date - nothing new in Health Connect."
                }
            )
        }
    }

    companion object {
        const val MAX_SCAN_PAGES = 4

        fun provideFactory(
            repository: HealthRecordsRepository,
            healthConnect: HealthConnectDataSource,
            summaries: DailySummaryRepository,
            context: Context,
            userProfileRepository: UserProfileRepository,
            labReportAnalyzer: LabReportAnalyzer
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthRecordsViewModel(
                    repository, healthConnect, summaries, context.applicationContext, userProfileRepository, labReportAnalyzer
                ) as T
            }
    }
}
