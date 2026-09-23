package com.kevan.hangry.domain.ai

import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.DailyHealthSummaryDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.dao.PostureScanDao
import com.kevan.hangry.data.local.dao.RecoveryScoreDao
import com.kevan.hangry.data.local.dao.SleepSessionDao
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.model.EnergyBalanceResult
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import java.util.Locale
import kotlinx.coroutines.flow.first
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
    private val coachJournalDao: CoachJournalDao,
    /** Body metrics + 7-day maintenance/goal calories. Optional so tests can omit it. */
    private val bodyMetricsRepository: BodyMetricsRepository? = null,
    /** Labs, vitals, goals, allergies, conditions, pregnancy and cycle. Optional for tests. */
    private val healthRecordsRepository: HealthRecordsRepository? = null,
    /** Daily supplements, schedule and adherence. Optional for tests. */
    private val supplementRepository: SupplementRepository? = null
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
        // Dash has no clock of its own: without this it can't tell "before bed" from "after lunch".
        val now = java.time.ZonedDateTime.now(zone)
        sb.appendLine("=== NOW ===")
        sb.appendLine(
            "Local time: " + now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a", java.util.Locale.US)) +
                " (${zone.id}). Use this for anything time-related (\"tonight\", \"today\", dose times, meal timing)."
        )
        sb.appendLine()

        sb.appendLine("=== USER PROFILE ===")
        if (profile != null) {
            val sex = profile.biologicalSex ?: "Not specified"
            val age = profile.age?.let { "$it yrs" } ?: "Not specified"
            val height = profile.heightCm?.let { "%.1f cm".format(java.util.Locale.US, it) } ?: "Not specified"
            val effectiveWeight = latestWeight?.weightKg ?: profile.currentWeightKg
            val curWeight = effectiveWeight?.let { "%.1f kg".format(java.util.Locale.US, it) } ?: "Not specified"
            val goalWeight = profile.weightGoalKg?.let { "%.1f kg".format(java.util.Locale.US, it) } ?: "None set"
            sb.appendLine("Sex: $sex | Age: $age | Height: $height | Current Weight: $curWeight | Goal Weight: $goalWeight")
            val circumferences = listOfNotNull(
                profile.neckCircumferenceCm?.let { "Neck: ${it}cm" },
                profile.chestCircumferenceCm?.let { "Chest: ${it}cm" },
                profile.waistCircumferenceCm?.let { "Waist: ${it}cm" },
                profile.hipCircumferenceCm?.let { "Hips: ${it}cm" }
            ).joinToString(", ")
            if (circumferences.isNotBlank()) {
                sb.appendLine("Circumferences: $circumferences")
            }
            sb.appendLine("Daily Targets: Steps: ${profile.dailyStepGoal} | Active Cal: ${profile.dailyActiveCaloriesGoal} kcal | Sleep Target: ${profile.sleepDurationTargetMinutes / 60}h ${profile.sleepDurationTargetMinutes % 60}m")
        } else {
            sb.appendLine("Profile defaults in use.")
        }
        sb.appendLine()

        bodyMetricsRepository?.current()?.let { snapshot ->
            appendEnergyBalance(sb, snapshot.input.energy)
            appendToday(sb, snapshot, foodLogs.filter { it.date == today })
            appendBodyMetrics(sb, snapshot)
        }

        healthRecordsRepository?.current()?.let { appendHealthRecords(sb, it, today) }
        supplementRepository?.current()?.let { appendSupplements(sb, it) }

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

        // Longer view so Dash can talk about direction, not just last week's raw numbers.
        val trendSummaries = dailyHealthSummaryDao.getSummariesBetweenList(today.minusDays(28), today.minusDays(1))
        val trendScores = recoveryScoreDao.getScoresBetweenList(today.minusDays(28), today.minusDays(1)).associate { it.date to it.score }
        val weights = runCatching { weightDao.getAllWeights().first() }.getOrDefault(emptyList())
        val trends = DashInsights.trendLines(trendSummaries, trendScores, weights, today, zone)
        sb.appendLine("=== TRENDS (last 7 full days vs the 3 weeks before) ===")
        if (trends.isEmpty()) sb.appendLine("Not enough history yet for trends.") else trends.forEach { sb.appendLine(it) }
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

    private fun appendEnergyBalance(sb: StringBuilder, energy: EnergyBalanceResult?) {
        sb.appendLine("=== ENERGY BALANCE (last 7 full days, excluding today) ===")
        val e = energy?.estimate
        if (e == null) {
            val missing = energy?.missing?.joinToString(", ") ?: "profile and step data"
            sb.appendLine("Not enough data to estimate maintenance calories yet (needs: $missing). Don't invent a number - suggest filling these in.")
            sb.appendLine()
            return
        }
        sb.appendLine("Maintenance calories: ${f(e.maintenanceKcal)} kcal/day (averaged over ${e.daysWithData} days with data, ${e.windowStart} to ${e.windowEnd})")
        sb.appendLine("• BMR (${e.bmrMethod}): ${f(e.bmrKcal)} kcal")
        sb.appendLine("• NEAT: avg ${f(e.avgTotalSteps)} steps/day ÷ 3 = ${f(e.avgNeatSteps)} counted steps × ${"%.3f".format(Locale.US, e.kcalPerStep)} kcal/step = ${f(e.neatKcal)} kcal (a third is counted as a rough allowance for steps already covered by workout calories - an estimate)")
        sb.appendLine("• Workouts: ${e.workoutsCounted} sessions, avg ${f(e.avgWorkoutKcal)} kcal/day" +
            if (e.workoutsWithoutCalories > 0) " (${e.workoutsWithoutCalories} had no calorie data)" else "")
        sb.appendLine("• Thermic effect of food (10%): ${f(e.tefKcal)} kcal")
        val goal = e.goal
        when {
            e.goalWeightKg == null || e.goalDate == null ->
                sb.appendLine("Goal: none set (no goal weight + target date). Eating ~${f(e.maintenanceKcal)} kcal/day maintains current weight.")
            goal?.dailyCalorieTarget == null ->
                sb.appendLine("Goal: ${"%.1f".format(Locale.US, e.goalWeightKg)} kg by ${e.goalDate} - ${goal?.guidance ?: "target date has passed"}")
            else -> {
                val adj = e.dailyAdjustmentKcal ?: 0.0
                val kind = if (adj < 0) "deficit" else "surplus"
                sb.appendLine("Goal: ${"%.1f".format(Locale.US, e.goalWeightKg)} kg by ${e.goalDate} → eat ${goal.dailyCalorieTarget} kcal/day (${f(kotlin.math.abs(adj))} kcal/day $kind, ~${"%.2f".format(Locale.US, kotlin.math.abs(goal.weeklyPaceKg))} kg/week). ${goal.guidance}")
            }
        }
        sb.appendLine()
    }

    /** Where today stands against the calorie target, so "what should I eat tonight?" has an answer. */
    private fun appendToday(sb: StringBuilder, snapshot: BodyMetricsSnapshot, todayFood: List<com.kevan.hangry.data.local.entity.FoodLogEntity>) {
        val e = snapshot.input.energy?.estimate
        val target = e?.goal?.dailyCalorieTarget ?: e?.maintenanceKcal?.toInt()
        val eaten = todayFood.sumOf { it.calories }
        val protein = todayFood.sumOf { it.proteinG }
        sb.appendLine("=== TODAY SO FAR ===")
        sb.appendLine(
            "Eaten: $eaten kcal, ${"%.0f".format(Locale.US, protein)} g protein across ${todayFood.size} logged item(s)" +
                (target?.let { " | target $it kcal | ${it - eaten} kcal left" } ?: " | no calorie target yet")
        )
        sb.appendLine()
    }

    private fun appendBodyMetrics(sb: StringBuilder, snapshot: BodyMetricsSnapshot) {
        val available = snapshot.groups.flatMap { it.metrics }.filter { it.isAvailable }
            .filterNot { it.id == "maintenance" || it.id == "goal_calories" } // covered above
        if (available.isEmpty()) return
        sb.appendLine("=== BODY METRICS (calculated in-app from profile, tape measurements & latest body-fat scan) ===")
        available.forEach { m ->
            val unit = if (m.unit.isNotEmpty()) " ${m.unit}" else ""
            val status = m.status?.let { " - $it" } ?: ""
            sb.appendLine("• ${m.name}: ${m.displayValue}$unit$status")
        }
        sb.appendLine()
    }

    private fun appendHealthRecords(sb: StringBuilder, records: HealthRecordsSnapshot, today: LocalDate) {
        sb.appendLine("=== HEALTH RECORDS (user-entered or imported; for tracking context only - never diagnose) ===")
        sb.appendLine("Allergies: " + records.allergies.joinToString(", ") { a -> a.name + (a.note?.let { " ($it)" } ?: "") }.ifEmpty { "none recorded" })
        sb.appendLine("Conditions: " + records.conditions.joinToString(", ") { it.name }.ifEmpty { "none recorded" })
        if (records.showsFemaleHealth) {
            sb.appendLine("Pregnant: " + if (records.isPregnant) "YES" + (records.pregnancyDueDate?.let { ", due $it" } ?: "") else "no")
            val cycle = HealthMarkerCalculator.cycleStats(records.periods.map { it.startDate to it.endDate }, today)
            if (cycle.lastPeriodStart != null) {
                val parts = listOfNotNull(
                    "last period started ${cycle.lastPeriodStart}",
                    cycle.currentCycleDay?.let { "cycle day $it" },
                    cycle.averageCycleDays?.let { "avg cycle %.0f days".format(Locale.US, it) },
                    cycle.predictedNextStart?.let { "next period predicted ~$it" },
                    if (cycle.inPeriodNow) "currently on period" else null
                )
                sb.appendLine("Menstrual cycle: " + parts.joinToString(", "))
            }
        }
        val tracked = records.visibleMarkers.filter { records.latest(it) != null || records.goal(it) != null }
        if (tracked.isEmpty()) {
            sb.appendLine("Lab/vital markers: none recorded yet.")
        } else {
            sb.appendLine("Lab/vital markers (latest first, up to 3 readings):")
            tracked.forEach { type ->
                val history = records.history(type).take(3)
                val status = HealthMarkerCalculator.describe(type, history.firstOrNull(), records.sex).status
                val readings = history.joinToString("; ") { r ->
                    val ctx = r.glucoseContext?.let { " ${it.label.lowercase()}" } ?: ""
                    "${HealthMarkerCalculator.format(type, r.value, r.secondaryValue)} ${type.canonicalUnit}$ctx on ${r.date}"
                }.ifEmpty { "no readings" }
                val goal = records.goal(type)?.let { g ->
                    val p = HealthMarkerCalculator.progress(g, history.firstOrNull())
                    val target = HealthMarkerCalculator.format(type, g.targetValue, g.targetSecondary)
                    val direction = if (g.direction == GoalDirection.LOWER) "at or below" else "at or above"
                    " | GOAL: $direction $target ${type.canonicalUnit}" + (g.targetDate?.let { " by $it" } ?: "") +
                        if (p.reached) " (reached)" else p.fraction?.let { " (%.0f%% of the way)".format(Locale.US, it * 100) } ?: ""
                } ?: ""
                sb.appendLine("• ${type.label}: $readings" + (status?.let { " [$it]" } ?: "") + goal)
            }
        }
        sb.appendLine()
    }

    private fun appendSupplements(sb: StringBuilder, snapshot: SupplementsSnapshot) {
        sb.appendLine("=== DAILY SUPPLEMENTS ===")
        if (snapshot.supplements.isEmpty()) {
            sb.appendLine("None recorded. The user can add one by sending you a photo of the bottle/label.")
            sb.appendLine()
            return
        }
        snapshot.supplements.forEach { s ->
            val dose = (if (s.doseAmount % 1.0 == 0.0) s.doseAmount.toInt().toString() else s.doseAmount.toString()) + " " + s.doseUnit
            val times = s.times.joinToString(", ").ifEmpty { "no set time" }
            val ingredients = s.ingredients.joinToString(", ") { i ->
                listOfNotNull(i.name, i.amount?.let { a -> (if (a % 1.0 == 0.0) a.toInt().toString() else a.toString()) + (i.unit?.let { " $it" } ?: "") }).joinToString(" ")
            }
            val adherence = snapshot.weekAdherence[s.id]?.takeIf { it.second > 0 }?.let { " | last 7 days: ${it.first}/${it.second} doses taken" } ?: ""
            val status = if (s.active) "" else " [PAUSED]"
            sb.appendLine("• ${s.name}${s.brand?.let { " ($it)" } ?: ""}$status: $dose at $times" +
                (if (ingredients.isNotEmpty()) " | per serving: $ingredients" else "") + adherence)
        }
        if (snapshot.todayDoses.isNotEmpty()) {
            sb.appendLine("Today: ${snapshot.takenToday} of ${snapshot.todayDoses.size} doses taken" +
                (snapshot.nextDose?.let { " | next: ${it.supplement.name} at ${it.time}" } ?: ""))
        }
        sb.appendLine()
    }

    private fun f(value: Double) = "%,.0f".format(Locale.US, value)
}
