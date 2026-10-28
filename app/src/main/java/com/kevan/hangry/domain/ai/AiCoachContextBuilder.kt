package com.kevan.hangry.domain.ai

import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.DailyHealthSummaryDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.dao.FoodLogDao
import com.kevan.hangry.data.local.dao.PostureScanDao
import com.kevan.hangry.data.local.dao.RecoveryScoreDao
import com.kevan.hangry.data.local.dao.SleepSessionDao
import com.kevan.hangry.domain.repository.WeightRepository
import com.kevan.hangry.domain.calculation.BodySignals
import com.kevan.hangry.domain.calculation.ActiveActivityCalculator
import com.kevan.hangry.domain.calculation.HealthMarkerCalculator
import com.kevan.hangry.domain.model.BodyMetricsSnapshot
import com.kevan.hangry.domain.model.GoalDirection
import com.kevan.hangry.domain.model.HealthRecordsSnapshot
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.WorkoutText
import com.kevan.hangry.domain.model.WorkoutType
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.data.repository.BodyAgeLoader
import com.kevan.hangry.data.repository.BodyAgeSnapshot
import com.kevan.hangry.data.repository.StreaksLoader
import com.kevan.hangry.domain.calculation.StrainCalculator
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.domain.model.SupplementsSnapshot
import com.kevan.hangry.domain.model.EnergyBalanceResult
import com.kevan.hangry.domain.repository.BodyMetricsRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import java.util.Locale
import kotlin.math.roundToInt
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
    private val weightRepository: WeightRepository,
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
    private val supplementRepository: SupplementRepository? = null,
    /** Intermittent fasting, only when the user has turned it on. Optional for tests. */
    private val fastingRepository: com.kevan.hangry.domain.repository.FastingRepository? = null,
    /** Guided programs the user has turned on (stress, focus, knees, back...). Optional for tests. */
    private val programsRepository: com.kevan.hangry.data.repository.ProgramsRepository? = null,
    private val bodyAgeLoader: BodyAgeLoader? = null,
    private val streaksLoader: StreaksLoader? = null,
    private val strainCalculator: StrainCalculator? = null,
    /** Rest mode, so Dash doesn't push training while the user is unwell. Optional for tests. */
    private val restModeRepository: com.kevan.hangry.domain.repository.RestModeRepository? = null
) {

    suspend fun build7DayContext(): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDate = today.minusDays(6) // 7 days inclusive

        val startInstant = startDate.atStartOfDay(zone).toInstant()
        val endInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val profile = userProfileRepository.getProfileSync()
        val latestWeight = weightRepository.getLatestWeightSync()
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
            "Local time: " + now.format(LOCAL_TIME_FORMAT) +
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

        bodyAgeLoader?.let { loader ->
            runCatching { loader.load(today, zone) }.getOrNull()?.let { snapshot ->
                appendBodyAge(sb, snapshot)
            }
        }

        restModeRepository?.state?.value?.active?.let { rest ->
            sb.appendLine("=== REST MODE ON ===")
            sb.appendLine(
                "Since ${rest.start} (day ${rest.dayNumber(today)}), reason: ${rest.reason.label}. Streaks are paused and the strain " +
                    "target is gentle. Don't suggest hard training; focus on rest, fluids, sleep and gentle movement. If they say " +
                    "they feel better, they can end rest mode on Today."
            )
            sb.appendLine()
        }

        healthRecordsRepository?.current()?.let { appendHealthRecords(sb, it, today) }
        supplementRepository?.current()?.let { appendSupplements(sb, it) }
        fastingRepository?.current()?.takeIf { it.enabled }?.let { appendFasting(sb, it) }
        programsRepository?.current()?.filter { it.enabled }?.takeIf { it.isNotEmpty() }?.let { programs ->
            sb.appendLine("=== GUIDED PROGRAMS (the user turned these on) ===")
            programs.forEach { p ->
                val recent = p.sessions.take(3).joinToString(", ") { s -> "${s.date} ${s.feel.label(p.program.kind)}" }.ifEmpty { "none yet" }
                sb.appendLine("• ${p.program.title}: level ${p.level}/${p.program.levels.size} (${p.currentLevel.title}), ${p.sessionsThisWeek}/${p.program.sessionsPerWeek} this week, recent: $recent")
            }
            sb.appendLine("Keep advice simple and gentle. If they mention pain or feeling worse, suggest the easier versions and checking with a professional.")
            sb.appendLine()
        }
        streaksLoader?.let { loader ->
            val stepGoal = profile?.dailyStepGoal ?: 10000L
            val sleepTarget = profile?.sleepDurationTargetMinutes ?: 480
            runCatching { loader.load(today, stepGoal, sleepTarget, zone) }.getOrNull()?.let { streaks ->
                appendStreaks(sb, streaks)
            }
        }

        sb.appendLine("=== USER'S PERSONAL JOURNAL & KNOWN PROBLEMS / MEMORIES ===")
        if (journalMemories.isEmpty()) {
            sb.appendLine("No past journal entries recorded yet.")
        } else {
            journalMemories.take(20).forEach { entry ->
                sb.appendLine("- [${entry.date}][${entry.category}] ${entry.summary}: ${entry.content}")
            }
        }
        sb.appendLine()

        val latestScore = recoveryScores[today] ?: recoveryScores.values.maxByOrNull { it.date }
        val recentStrains = dailySummaries.mapNotNull { it.dayStrain }
        appendRecoveryGuidance(sb, latestScore, recentStrains, strainCalculator, restModeRepository?.state?.value?.isRestDay(today) == true)

        appendSleepArchitecture(sb, sleepSessions, zone)

        sb.appendLine("=== 7-DAY DAILY HEALTH SUMMARIES ($startDate to $today) ===")
        if (dailySummaries.isEmpty()) {
            sb.appendLine("No synced daily summaries recorded in this 7-day period.")
        } else {
            dailySummaries.forEach { s ->
                val dateStr = s.date.toString()
                val rec = recoveryScores[s.date]?.score?.let { "$it%" } ?: "N/A"
                val strain = s.dayStrain?.let { "%.1f/21".format(java.util.Locale.US, it) } ?: "N/A"
                val sleepDur = s.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m asleep" + (s.sleepNeedMinutes?.let { n -> " of ${n / 60}h ${n % 60}m needed" } ?: "") + (s.sleepScore?.let { sc -> ", sleep score $sc/100" } ?: "") } ?: "N/A"
                val steps = s.steps?.let { "$it steps" } ?: "0 steps"
                val actCal = s.activeCalories?.let { "%.0f active kcal".format(java.util.Locale.US, it) } ?: "0 active kcal"
                val actMin = s.activeMinutes?.let { " | Active: $it min" } ?: ""
                val rhr = s.restingHeartRate?.let { "overnight RHR %.0f bpm".format(java.util.Locale.US, it) } ?: "RHR N/A"
                val hrv = s.hrvRmssd?.let { "HRV %.1f ms".format(java.util.Locale.US, it) } ?: "HRV N/A"
                // Only what the wearable recorded, so trackers without these sensors don't add noise.
                val vitals = listOfNotNull(
                    s.respiratoryRate?.let { "breathing %.1f/min".format(java.util.Locale.US, it) },
                    s.spo2Percentage?.let { "SpO2 %.0f%%".format(java.util.Locale.US, it) },
                    s.skinTempDeltaCelsius?.let { "skin temp %+.1f°C vs baseline".format(java.util.Locale.US, it) },
                    s.vo2Max?.let { "VO2 max %.1f".format(java.util.Locale.US, it) },
                    s.sleepingHeartRate?.let { "sleeping HR %.0f bpm".format(java.util.Locale.US, it) },
                    BodySignals.decode(s.bodySignals).takeIf { it.size >= BodySignals.MIN_SIGNALS }?.let { signals ->
                        "BODY SIGNALS OFF NORMAL (possible illness/strain): " + signals.joinToString(", ") { BodySignals.describe(it) }
                    }
                ).joinToString("") { " | $it" }

                sb.appendLine("• $dateStr: Recovery: $rec | Sleep: $sleepDur | Strain: $strain | Steps: $steps | Burn: $actCal$actMin | $rhr | $hrv$vitals")
            }
        }
        sb.appendLine()

        // Longer view so Dash can talk about direction, not just last week's raw numbers.
        val trendSummaries = dailyHealthSummaryDao.getSummariesBetweenList(today.minusDays(28), today.minusDays(1))
        val trendScores = recoveryScoreDao.getScoresBetweenList(today.minusDays(28), today.minusDays(1)).associate { it.date to it.score }
        val weights = runCatching { weightRepository.getAllWeights().first() }.getOrDefault(emptyList())
        val trends = DashInsights.trendLines(trendSummaries, trendScores, weights, today, zone)
        sb.appendLine("=== TRENDS (last 7 full days vs the 3 weeks before) ===")
        if (trends.isEmpty()) sb.appendLine("Not enough history yet for trends.") else trends.forEach { sb.appendLine(it) }
        sb.appendLine()

        sb.appendLine("=== 7-DAY WORKOUTS & EXERCISE SESSIONS ===")
        if (workouts.isEmpty()) {
            sb.appendLine("No exercise sessions recorded in the last 7 days.")
        } else {
            val formatter = DATE_TIME_FORMAT.withZone(zone)
            workouts.forEach { w ->
                val timeStr = formatter.format(w.startTime)
                val titleStr = w.title?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val dur = "${w.durationMinutes} mins"
                val cal = ActiveActivityCalculator.workoutCalories(w, bmr = null)?.let { "%.0f kcal".format(java.util.Locale.US, it) } ?: "N/A kcal"
                val extra = (WorkoutText.details(w) + listOfNotNull(w.segmentSummary, w.notes?.let { "notes: $it" }))
                    .joinToString("") { " · $it" }
                sb.appendLine("- $timeStr: ${WorkoutType.fromId(w.exerciseType).label}$titleStr · $dur · $cal$extra".trim())
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
                // Meal names go in after formatting: a name like "2% milk" would otherwise be read as a format slot.
                val macros = "P: %.0fg, C: %.0fg, F: %.0fg".format(java.util.Locale.US, totalProtein, totalCarbs, totalFat)
                sb.appendLine("• $date: Total $totalCal kcal ($macros) | Meals: $mealNames")
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

        val built = sb.toString().trim()
        if (built.length <= MAX_CONTEXT_CHARS) return built
        // Safety net: a very long history mustn't crowd out the model's own reasoning. Cut on a
        // line boundary so nothing is half-stated, and say so rather than pretend it's complete.
        val trimmed = built.take(MAX_CONTEXT_CHARS).substringBeforeLast('\n').ifBlank { built.take(MAX_CONTEXT_CHARS) }
        return trimmed + "\n\n[... older detail trimmed to keep replies fast; the sections above are complete ...]"
    }

    private fun appendEnergyBalance(sb: StringBuilder, energy: EnergyBalanceResult?) {
        sb.appendLine("=== ENERGY BALANCE (typical day: last 7 full days; logs: last 28; today's target below) ===")
        val e = energy?.estimate
        if (e == null) {
            val missing = energy?.missing?.joinToString(", ") ?: "profile and step data"
            sb.appendLine("Not enough data to estimate maintenance calories yet (needs: $missing). Don't invent a number - suggest filling these in.")
            sb.appendLine()
            return
        }
        sb.appendLine("Maintenance calories: ${f(e.maintenanceKcal)} kcal/day (activity averaged over ${e.daysWithData} days with data, ${e.windowStart} to ${e.windowEnd})")
        sb.appendLine("• Resting: BMR (${e.bmrMethod}) ${f(e.bmrKcal)} kcal, counted for the ${f(1440 - e.avgActiveMinutes)} inactive minutes = ${f(e.restingKcal)} kcal")
        sb.appendLine("• Steps: avg ${f(e.avgTotalSteps)} steps/day, ${f(e.avgCountedSteps)} outside workouts × ${"%.3f".format(Locale.US, e.kcalPerStep)} kcal/step (walking + resting burn while walking) = ${f(e.stepKcal)} kcal")
        sb.appendLine("• Workouts (counted in full): ${e.workoutsCounted} sessions, avg ${f(e.avgWorkoutKcal)} kcal/day" +
            if (e.workoutsEstimated > 0) " (${e.workoutsEstimated} estimated, no calorie data)" else "")
        sb.appendLine("• Thermic effect of food (10% of intake): ${f(e.tefKcal)} kcal")
        sb.appendLine("• Activity estimate: ${f(e.formulaMaintenanceKcal)} kcal/day")
        val adaptive = e.adaptive
        if (adaptive != null) {
            sb.appendLine("• Learnt from logs (${adaptive.windowStart} to ${adaptive.windowEnd}): ate ${f(adaptive.avgIntakeKcal)} kcal/day on ${adaptive.loggedDays} fully-logged days, weight trend ${"%+.2f".format(Locale.US, adaptive.weeklyWeightChangeKg)} kg/week over ${adaptive.weighIns} weigh-ins → burnt ~${f(adaptive.maintenanceKcal)} kcal/day")
            sb.appendLine("• Maintenance blends ${f(adaptive.weight * 100)}% logs + ${f((1 - adaptive.weight) * 100)}% activity estimate")
        } else {
            sb.appendLine("• Not yet learning from logs (needs ${e.adaptiveMissing}) - maintenance is the activity estimate")
        }
        val day = e.today
        if (day != null) {
            sb.appendLine("TODAY'S CALORIE TARGET: ${day.targetKcal} kcal (worked out for today, not averaged - this is the number the app shows)")
            sb.appendLine("• Resting ${f(day.restingKcal)} + NEAT ${f(day.neatKcal)} (${if (day.neatFromDay) "today's ${f(day.neatSteps)} steps" else "usual ${f(day.neatSteps)} steps/day until today's pass it"}) + today's workouts ${f(day.workoutKcal)} + digestion ${f(day.tefKcal)}" +
                (if (day.adaptiveFactor != 1.0) ", × ${"%.2f".format(Locale.US, day.adaptiveFactor)} learnt from logs" else "") +
                " = ${f(day.burnKcal)} kcal burnt today")
            val goal = day.goal
            when {
                e.goalWeightKg == null ->
                    sb.appendLine("• Goal: no goal weight set, so the target is today's burn (maintains current weight).")
                goal?.dailyCalorieTarget == null ->
                    sb.appendLine("• Goal: ${"%.1f".format(Locale.US, e.goalWeightKg)} kg${e.goalDate?.let { " by $it" } ?: ""} - ${goal?.guidance ?: "target date has passed"}")
                else -> {
                    val adj = day.adjustmentKcal
                    val kind = if (adj < 0) "deficit" else "surplus"
                    sb.appendLine("• Goal: ${"%.1f".format(Locale.US, e.goalWeightKg)} kg${e.goalDate?.let { " by $it" } ?: " (no date: default pace)"} → ${f(kotlin.math.abs(adj))} kcal $kind, ~${"%.2f".format(Locale.US, kotlin.math.abs(goal.weeklyPaceKg))} kg/week. ${goal.guidance}")
                }
            }
            sb.appendLine("• The target rises when a workout syncs or today's steps pass the usual; a workout later today will add to it.")
        }
        sb.appendLine()
    }

    /** Where today stands against the calorie target, so "what should I eat tonight?" has an answer. */
    private fun appendToday(sb: StringBuilder, snapshot: BodyMetricsSnapshot, todayFood: List<com.kevan.hangry.data.local.entity.FoodLogEntity>) {
        val e = snapshot.input.energy?.estimate
        val target = e?.today?.targetKcal
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
            // Adding a supplement isn't a request to be tracked: untracked ones count as taken.
            val tracking = if (s.tracked) " [TRACKED${if (s.remindersEnabled) ", reminders on" else ""}]" else " [not tracked - assume taken as usual]"
            val ingredients = s.ingredients.joinToString(", ") { i ->
                listOfNotNull(i.name, i.amount?.let { a -> (if (a % 1.0 == 0.0) a.toInt().toString() else a.toString()) + (i.unit?.let { " $it" } ?: "") }).joinToString(" ")
            }
            val adherence = snapshot.weekAdherence[s.id]?.takeIf { it.second > 0 }?.let { " | last 7 days: ${it.first}/${it.second} doses taken" } ?: ""
            val status = if (s.active) "" else " [PAUSED]"
            sb.appendLine("• ${s.name}${s.brand?.let { " ($it)" } ?: ""}$status$tracking: $dose at $times" +
                (if (ingredients.isNotEmpty()) " | per serving: $ingredients" else "") + adherence)
        }
        if (snapshot.todayDoses.isNotEmpty()) {
            sb.appendLine("Today (tracked only): ${snapshot.takenToday} of ${snapshot.todayDoses.size} doses taken" +
                (snapshot.nextDose?.let { " | next: ${it.supplement.name} at ${it.time}" } ?: ""))
        }
        sb.appendLine()
    }

    private fun appendFasting(sb: StringBuilder, snapshot: com.kevan.hangry.domain.model.FastingSnapshot) {
        sb.appendLine("=== INTERMITTENT FASTING (the user turned this on) ===")
        sb.appendLine("Plan: ${snapshot.plan.label} (${snapshot.targetHours}h fast) | streak: ${snapshot.streak} days (best ${snapshot.bestStreak})")
        val fmt = com.kevan.hangry.domain.model.FastingMath::formatDuration
        snapshot.active?.let { f ->
            val stage = com.kevan.hangry.domain.model.FastingStage.at(f.elapsed())
            sb.appendLine("Fasting now: ${fmt(f.elapsed())} of ${f.targetMinutes / 60}h, started ${f.startAt.atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0)} | stage: ${stage.title}")
        } ?: sb.appendLine("Not fasting right now (eating window).")
        val week = com.kevan.hangry.domain.model.FastingMath.lastWeek(snapshot.history)
        if (week.isNotEmpty()) {
            sb.appendLine("Last 7 days: ${week.size} fasts, ${week.count { it.reachedGoal() }} reached goal, lengths: " + week.joinToString(", ") { fmt(it.elapsed()) })
        }
        sb.appendLine()
    }

    private fun appendBodyAge(sb: StringBuilder, snapshot: BodyAgeSnapshot) {
        sb.appendLine("=== BODY AGE (Biological vs Chronological Age over 30 days) ===")
        val cur = snapshot.current
        if (cur == null) {
            val needs = snapshot.needs.joinToString("; ").ifBlank { "More health data needed" }
            sb.appendLine("Not enough data to calculate Body Age yet ($needs).")
        } else {
            val diffYears = cur.difference
            val diffStr = when {
                diffYears < -0.2 -> "%.1f years younger than chronological age".format(Locale.US, kotlin.math.abs(diffYears))
                diffYears > 0.2 -> "%.1f years older than chronological age".format(Locale.US, diffYears)
                else -> "matches chronological age"
            }
            sb.appendLine("Body Age: %.1f yrs (Chronological: %.1f yrs) - %s".format(Locale.US, cur.bodyAge, cur.chronologicalAge, diffStr))
            snapshot.previous?.let { prev ->
                val change = cur.bodyAge - prev.bodyAge
                val trendStr = if (kotlin.math.abs(change) < 0.2) "stable" else if (change < 0) "trending %.1f years younger over the past month".format(Locale.US, kotlin.math.abs(change)) else "trending %.1f years older over the past month".format(Locale.US, change)
                sb.appendLine("30-Day Trend: $trendStr (was ${"%.1f".format(Locale.US, prev.bodyAge)} yrs 30 days ago)")
            }
            if (cur.factors.isNotEmpty()) {
                sb.appendLine("Key Drivers:")
                cur.factors.forEach { f ->
                    val effect = if (f.years < 0) "%.1f yrs younger".format(Locale.US, kotlin.math.abs(f.years))
                    else if (f.years > 0) "+%.1f yrs older".format(Locale.US, f.years)
                    else "neutral"
                    sb.appendLine("• ${f.name}: ${f.value} ($effect) - ${f.tip}")
                }
            }
        }
        sb.appendLine()
    }

    private fun appendStreaks(sb: StringBuilder, streaks: List<Streak>) {
        sb.appendLine("=== HABIT STREAKS ===")
        if (streaks.isEmpty()) {
            sb.appendLine("No active streaks recorded yet.")
        } else {
            streaks.forEach { s ->
                val status = when {
                    s.doneToday -> "done today"
                    s.paused -> "paused for rest mode"
                    else -> "pending today"
                }
                sb.appendLine("• ${s.type.label}: ${s.current} day(s) ($status | all-time best: ${s.best} days)")
            }
        }
        sb.appendLine()
    }

    private fun appendRecoveryGuidance(
        sb: StringBuilder,
        latestScore: RecoveryScoreEntity?,
        recentStrains: List<Double>,
        strainCalculator: StrainCalculator?,
        resting: Boolean
    ) {
        sb.appendLine("=== TODAY'S RECOVERY & RECOMMENDED STRAIN ===")
        if (latestScore == null) {
            sb.appendLine("No recovery score computed yet for today.")
        } else {
            val recState = runCatching { RecoveryState.valueOf(latestScore.state) }.getOrNull()
            val scoreStr = latestScore.score?.let { "$it%" } ?: "Building baseline"
            sb.appendLine("Recovery Score: $scoreStr | State: ${latestScore.state} | Confidence: ${latestScore.confidence}")
            if (latestScore.supportiveAdvice.isNotBlank()) {
                sb.appendLine("Advice: ${latestScore.supportiveAdvice}")
            }
            val components = listOfNotNull(
                latestScore.hrvComponentScore?.let { "HRV: ${it.roundToInt()}%" },
                latestScore.rhrComponentScore?.let { "RHR: ${it.roundToInt()}%" },
                latestScore.sleepComponentScore?.let { "Sleep: ${it.roundToInt()}%" },
                latestScore.trainingLoadComponentScore?.let { "Load: ${it.roundToInt()}%" }
            )
            if (components.isNotEmpty()) {
                sb.appendLine("Score Drivers: " + components.joinToString(" | "))
            }
            if (recState != null && strainCalculator != null) {
                val recommendation = strainCalculator.recommendStrainTarget(recState, recentStrains, resting)
                sb.appendLine("Recommended Day Strain Target: %.1f - %.1f / 21".format(Locale.US, recommendation.targetLow, recommendation.targetHigh))
                sb.appendLine("Strain Guidance: ${recommendation.guidance}")
            }
        }
        sb.appendLine()
    }

    private fun appendSleepArchitecture(
        sb: StringBuilder,
        sessions: List<com.kevan.hangry.data.local.entity.SleepSessionEntity>,
        zone: ZoneId
    ) {
        sb.appendLine("=== SLEEP ARCHITECTURE (last 7 days) ===")
        val validSessions = sessions.filter { it.durationMinutes > 0 }
        if (validSessions.isEmpty()) {
            sb.appendLine("No detailed sleep stage sessions recorded in the last 7 days.")
        } else {
            val formatter = DATE_FORMAT.withZone(zone)
            validSessions.sortedByDescending { it.startTime }.take(7).forEach { s ->
                val dateStr = formatter.format(s.startTime)
                val durH = s.durationMinutes / 60
                val durM = s.durationMinutes % 60
                val totalMin = s.durationMinutes.toDouble()
                val stages = mutableListOf<String>()
                s.deepSleepMinutes?.let {
                    val pct = ((it / totalMin) * 100).roundToInt()
                    stages += "Deep: ${it}m ($pct%)"
                }
                s.remSleepMinutes?.let {
                    val pct = ((it / totalMin) * 100).roundToInt()
                    stages += "REM: ${it}m ($pct%)"
                }
                s.lightSleepMinutes?.let {
                    val pct = ((it / totalMin) * 100).roundToInt()
                    stages += "Light: ${it}m ($pct%)"
                }
                s.awakeMinutes?.let {
                    stages += "Awake: ${it}m"
                }
                val scoreStr = s.sleepQualityScore?.let { " | Quality: $it/100" } ?: ""
                val stagesStr = if (stages.isNotEmpty()) " | Stages: ${stages.joinToString(", ")}" else ""
                sb.appendLine("• $dateStr: ${durH}h ${durM}m$stagesStr$scoreStr")
            }
        }
        sb.appendLine()
    }

    private fun f(value: Double) = "%,.0f".format(Locale.US, value)
}

/** Upper bound on the assembled context. A safety net, well above a typical week's worth. */
private const val MAX_CONTEXT_CHARS = 20000

private val LOCAL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a", Locale.US)
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
