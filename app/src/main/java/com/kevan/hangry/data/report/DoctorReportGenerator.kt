package com.kevan.hangry.data.report

import android.content.Context
import com.kevan.hangry.data.datasource.HealthConnectDataSource
import com.kevan.hangry.data.datasource.healthAppName
import com.kevan.hangry.domain.report.DoctorReport
import com.kevan.hangry.domain.report.DoctorReportBuilder
import com.kevan.hangry.domain.report.DoctorReportInputs
import com.kevan.hangry.domain.report.ReportOptions
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

/** A finished report on disk, ready to preview, share or save. */
data class GeneratedReport(val file: File, val pageCount: Int, val report: DoctorReport)

/**
 * Reads what a doctor report needs from local storage, builds it and writes the PDF into the
 * app's cache (`cache/reports/`, shared through the FileProvider). Only the latest report is
 * kept: making a new one deletes the old.
 */
class DoctorReportGenerator(
    private val context: Context,
    private val userProfileRepository: UserProfileRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val weightRepository: WeightRepository,
    private val healthRecordsRepository: HealthRecordsRepository,
    private val supplementRepository: SupplementRepository,
    private val workoutRepository: WorkoutRepository,
    private val foodLogRepository: FoodLogRepository,
    private val bodyFatRepository: BodyFatRepository,
    private val healthConnect: HealthConnectDataSource
) {

    suspend fun load(options: ReportOptions, now: ZonedDateTime = ZonedDateTime.now()): DoctorReport {
        val zone = now.zone
        val end = now.toLocalDate()
        val start = end.minusDays(options.period.days - 1L)
        val previousStart = start.minusDays(options.period.days.toLong())
        val periodStartInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = end.plusDays(1).atStartOfDay(zone).toInstant()

        val sources = runCatching { healthConnect.fetchDataSources(periodStartInstant) }.getOrDefault(emptyList())
            .flatMap { it.packages }
            .filter { it != context.packageName }
            .distinct()
            .map { healthAppName(context, it) }

        return DoctorReportBuilder.build(
            DoctorReportInputs(
                now = now,
                options = options,
                profile = userProfileRepository.getProfileSync(),
                summaries = dailySummaryRepository.getSummariesBetween(previousStart, end).first(),
                weights = weightRepository.getWeightsBetweenList(previousStart.atStartOfDay(zone).toInstant(), endInstant)
                    .ifEmpty { listOfNotNull(weightRepository.getLatestWeightSync()) },
                records = healthRecordsRepository.current(),
                supplements = supplementRepository.current(),
                workouts = workoutRepository.getSessionsBetweenList(periodStartInstant, endInstant),
                food = foodLogRepository.getBetween(start, end).first(),
                latestBodyFatPercent = bodyFatRepository.getLatestScanSync()?.bodyFatPercentage,
                dataSources = sources
            )
        )
    }

    /** Builds the report and writes it as a PDF; call off the main thread. */
    suspend fun generate(options: ReportOptions, units: ReportUnits): GeneratedReport {
        val report = load(options)
        val dir = reportsDir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(report.end))
        val pages = file.outputStream().use { DoctorReportPdf(context, units).write(report, it) }
        return GeneratedReport(file, pages, report)
    }

    companion object {
        fun reportsDir(context: Context) = File(context.cacheDir, "reports")

        /** e.g. "Health summary 2026-10-22.pdf" - sorts by date and reads well in a doctor's inbox. */
        fun fileName(date: LocalDate) = "Health summary $date.pdf"
    }
}
