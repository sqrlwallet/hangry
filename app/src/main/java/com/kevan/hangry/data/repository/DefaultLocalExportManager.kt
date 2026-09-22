package com.kevan.hangry.data.repository

import com.kevan.hangry.domain.repository.DailySummaryRepository
import com.kevan.hangry.domain.repository.LocalExportManager
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate

class DefaultLocalExportManager(
    private val dailySummaryRepository: DailySummaryRepository
) : LocalExportManager {

    override suspend fun exportDataAsJson(): String {
        val today = LocalDate.now()
        val summaries = dailySummaryRepository.getSummariesBetween(today.minusDays(90), today).firstOrNull() ?: emptyList()
        val scores = dailySummaryRepository.getScoresBetween(today.minusDays(90), today).firstOrNull() ?: emptyList()

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"appName\": \"Hangry\",\n")
        sb.append("  \"exportTimestamp\": \"${System.currentTimeMillis()}\",\n")
        sb.append("  \"dailySummaries\": [\n")
        summaries.forEachIndexed { index, s ->
            sb.append("    {\"date\": \"${s.date}\", \"sleepMinutes\": ${s.sleepDurationMinutes}, \"steps\": ${s.steps}, \"restingHeartRate\": ${s.restingHeartRate}, \"hrvRmssd\": ${s.hrvRmssd}}")
            if (index < summaries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"recoveryScores\": [\n")
        scores.forEachIndexed { index, r ->
            sb.append("    {\"date\": \"${r.date}\", \"score\": ${r.score}, \"confidence\": \"${r.confidence}\", \"state\": \"${r.state}\"}")
            if (index < scores.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    override suspend fun exportDataAsCsv(): String {
        val today = LocalDate.now()
        val summaries = dailySummaryRepository.getSummariesBetween(today.minusDays(90), today).firstOrNull() ?: emptyList()

        val sb = StringBuilder()
        sb.append("Date,SleepMinutes,Steps,DistanceMeters,RestingHeartRate,HrvRmssd,TrainingLoad\n")
        summaries.forEach { s ->
            sb.append("${s.date},${s.sleepDurationMinutes ?: ""},${s.steps ?: ""},${s.distanceMeters ?: ""},${s.restingHeartRate ?: ""},${s.hrvRmssd ?: ""},${s.dailyTrainingLoad ?: ""}\n")
        }
        return sb.toString()
    }
}
