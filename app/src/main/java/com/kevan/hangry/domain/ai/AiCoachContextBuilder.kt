package com.kevan.hangry.domain.ai

import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.DailyHealthSummaryDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.dao.PostureScanDao
import com.kevan.hangry.data.local.dao.RecoveryScoreDao
import com.kevan.hangry.data.local.dao.SleepSessionDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.domain.repository.UserProfileRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Aggregates user data from the past 7 days (profile, vitals, sleep, strain, workouts, nutrition,
 * posture, and personal journal memories) into a clean structured string for the AI Coach.
 */
class AiCoachContextBuilder(
    private val userProfileRepository: UserProfileRepository,
    private val weightDao: WeightDao,
    private val dailyHealthSummaryDao: DailyHealthSummaryDao,
    private val recoveryScoreDao: RecoveryScoreDao,
    private val exerciseSessionDao: ExerciseSessionDao,
    private val sleepSessionDao: SleepSessionDao,
    private val foodLogDao: FoodLogDao,
    private val postureScanDao: PostureScanDao,
    private val coachJournalDao: CoachJournalDao
) {

    suspend fun build7DayContext(): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDate = today.minusDays(6) // 7 days inclusive

        val startInstant = startDate.atStartOfDay(zone).toInstant()
        val endInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val profile = userProfileRepository.getProfileSync()
        val latestWeight = weightDao.getLatestWeightSync()
        val dailySummaries = dailyHealthSummaryDao.getSummariesBetweenList(startDate, today)
        val recoveryScores = recoveryScoreDao.getScoresBetweenList(startDate, today).associateBy { it.date }
        val workouts = exerciseSessionDao.getSessionsBetweenList(startInstant, endInstant)
        val sleepSessions = sleepSessionDao.getSessionsBetweenList(startInstant, endInstant)
        val foodLogs = foodLogDao.getBetweenList(startDate, today)
        val latestPosture = postureScanDao.getLatestSync()
        val journalMemories = coachJournalDao.getAllSync()

        val sb = StringBuilder()
        sb.appendLine("=== USER PROFILE ===")
        if (profile != null) {
            val sex = profile.biologicalSex ?: "Not specified"
            val age = profile.age?.let { "$it yrs" } ?: "Not specified"
            val height = profile.heightCm?.let { "%.1f cm".format(java.util.Locale.US, it) } ?: "Not specified"
            val curWeight = latestWeight?.weightKg?.let { "%.1f kg".format(java.util.Locale.US, it) } ?: "Not specified"
            val goalWeight = profile.weightGoalKg?.let { "%.1f kg".format(java.util.Locale.US, it) } ?: "None set"
            sb.appendLine("Sex: $sex | Age: $age | Height: $height | Current Weight: $curWeight | Goal Weight: $goalWeight")
            sb.appendLine("Daily Targets: Steps: ${profile.dailyStepGoal} | Active Cal: ${profile.dailyActiveCaloriesGoal} kcal | Sleep Target: ${profile.sleepDurationTargetMinutes / 60}h ${profile.sleepDurationTargetMinutes % 60}m")
        } else {
            sb.appendLine("Profile defaults in use.")
        }
        sb.appendLine()

        sb.appendLine("=== USER'S PERSONAL JOURNAL & KNOWN PROBLEMS / MEMORIES ===")
        if (journalMemories.isEmpty()) {
            sb.appendLine("No past journal entries recorded yet.")
        } else {
            journalMemories.take(20).forEach { entry ->
                sb.appendLine("- [${entry.date}][${entry.category}] ${entry.summary}: ${entry.content}")
            }
        }
        sb.appendLine()

        sb.appendLine("=== 7-DAY DAILY HEALTH SUMMARIES ($startDate to $today) ===")
        if (dailySummaries.isEmpty()) {
            sb.appendLine("No synced daily summaries recorded in this 7-day period.")
        } else {
            dailySummaries.forEach { s ->
                val dateStr = s.date.toString()
                val rec = recoveryScores[s.date]?.score?.let { "$it%" } ?: "N/A"
                val strain = s.dayStrain?.let { "%.1f/21".format(java.util.Locale.US, it) } ?: "N/A"
                val sleepDur = s.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "N/A"
                val steps = s.steps?.let { "$it steps" } ?: "0 steps"
                val actCal = s.activeCalories?.let { "%.0f active kcal".format(java.util.Locale.US, it) } ?: "0 active kcal"
                val rhr = s.restingHeartRate?.let { "RHR %.0f bpm".format(java.util.Locale.US, it) } ?: "RHR N/A"
                val hrv = s.hrvRmssd?.let { "HRV %.1f ms".format(java.util.Locale.US, it) } ?: "HRV N/A"

                sb.appendLine("• $dateStr: Recovery: $rec | Sleep: $sleepDur | Strain: $strain | Steps: $steps | Burn: $actCal | $rhr | $hrv")
            }
        }
        sb.appendLine()

        sb.appendLine("=== 7-DAY WORKOUTS & EXERCISE SESSIONS ===")
        if (workouts.isEmpty()) {
            sb.appendLine("No exercise sessions recorded in the last 7 days.")
        } else {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
            workouts.forEach { w ->
                val timeStr = formatter.format(w.startTime)
                val titleStr = w.title?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val dur = "${w.durationMinutes} mins"
                val cal = w.activeCalories?.let { "%.0f kcal".format(java.util.Locale.US, it) } ?: "N/A kcal"
                sb.appendLine("- $timeStr: ${w.exerciseType}$titleStr · $dur · $cal".trim())
            }
        }
        sb.appendLine()

        sb.appendLine("=== 7-DAY NUTRITION & FOOD LOGS ===")
        if (foodLogs.isEmpty()) {
            sb.appendLine("No meals logged in the last 7 days.")
        } else {
            val logsByDate = foodLogs.groupBy { it.date }
            logsByDate.forEach { (date, entries) ->
                val totalCal = entries.sumOf { it.calories }
                val totalProtein = entries.sumOf { it.proteinG }
                val totalCarbs = entries.sumOf { it.carbsG }
                val totalFat = entries.sumOf { it.fatG }
                val mealNames = entries.joinToString(", ") { "${it.foodName} (${it.calories} kcal)" }
                sb.appendLine("• $date: Total $totalCal kcal (P: %.0fg, C: %.0fg, F: %.0fg) | Meals: $mealNames".format(java.util.Locale.US, totalProtein, totalCarbs, totalFat))
            }
        }
        sb.appendLine()

        if (latestPosture != null) {
            sb.appendLine("=== LATEST POSTURE SCAN (${latestPosture.date}) ===")
            sb.appendLine("Score: ${latestPosture.score}/100")
            if (latestPosture.findingsJson.isNotBlank()) {
                sb.appendLine("Findings: ${latestPosture.findingsJson}")
            }
            sb.appendLine()
        }

        return sb.toString().trim()
    }
}
