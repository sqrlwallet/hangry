package com.kevan.hangry.data.coach

import com.kevan.hangry.data.healthrecords.FhirHealthRecordParser
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.FoodLogSource
import com.kevan.hangry.domain.model.CoachAction
import com.kevan.hangry.domain.model.GlucoseContext
import com.kevan.hangry.domain.model.HealthProfileKind
import com.kevan.hangry.domain.model.MarkerType
import com.kevan.hangry.domain.model.SupplementTimes
import com.kevan.hangry.domain.repository.FoodLogRepository
import com.kevan.hangry.domain.repository.HealthRecordsRepository
import com.kevan.hangry.domain.repository.SupplementDraft
import com.kevan.hangry.domain.repository.SupplementRepository
import com.kevan.hangry.domain.repository.UserProfileRepository
import com.kevan.hangry.data.local.dao.WeightDao
import com.kevan.hangry.domain.repository.BodyFatRepository
import com.kevan.hangry.domain.repository.HealthSyncManager
import com.kevan.hangry.domain.repository.MealPlanRepository
import com.kevan.hangry.domain.repository.SleepRepository
import android.content.Context
import com.kevan.hangry.data.local.dao.CoachJournalDao
import com.kevan.hangry.data.local.dao.ExerciseSessionDao
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.data.nudges.NudgePrefs
import com.kevan.hangry.domain.model.WorkoutType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

/**
 * Runs an action Dash proposed, once the user taps it, through the same repositories the rest
 * of the app uses. Validates everything the model supplied; a bad value fails with a message
 * rather than saving something wrong.
 */
class CoachActionExecutor(
    private val supplementRepository: SupplementRepository,
    private val healthRecordsRepository: HealthRecordsRepository,
    private val foodLogRepository: FoodLogRepository,
    /** Health Connect nutrition write (HealthConnectDataSource::writeNutritionRecord). */
    private val writeNutritionRecord: suspend (FoodLogEntity) -> Boolean,
    /** Optional so tests can leave them out; the matching actions then fail cleanly. */
    private val userProfileRepository: UserProfileRepository? = null,
    private val weightDao: WeightDao? = null,
    private val mealPlanRepository: MealPlanRepository? = null,
    private val sleepRepository: SleepRepository? = null,
    private val bodyFatRepository: BodyFatRepository? = null,
    private val healthSyncManager: HealthSyncManager? = null,
    private val exerciseSessionDao: ExerciseSessionDao? = null,
    private val coachJournalDao: CoachJournalDao? = null,
    private val context: Context? = null,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    suspend fun execute(action: CoachAction): Result<String> = runCatching {
        when (action.type) {
            CoachAction.ADD_SUPPLEMENT -> addSupplement(action)
            CoachAction.MARK_SUPPLEMENT_TAKEN -> markTaken(action)
            CoachAction.LOG_MEAL -> logMeal(action)
            CoachAction.ADD_READING -> addReading(action)
            CoachAction.SET_GOAL -> setGoal(action)
            CoachAction.ADD_ALLERGY -> addProfileItem(action, HealthProfileKind.ALLERGY)
            CoachAction.ADD_CONDITION -> addProfileItem(action, HealthProfileKind.CONDITION)
            CoachAction.UPDATE_GOALS -> updateGoals(action)
            CoachAction.UPDATE_MEAL -> updateMeal(action)
            CoachAction.ADD_MEAL_PLAN -> addMealPlan(action)
            CoachAction.UPDATE_SUPPLEMENT -> updateSupplement(action)
            CoachAction.SET_PREGNANCY -> setPregnancy(action)
            CoachAction.LOG_PERIOD -> logPeriod(action)
            CoachAction.UPDATE_PROFILE -> updateProfile(action)
            CoachAction.LOG_SLEEP -> logSleep(action)
            CoachAction.SET_HRV_FEELING -> setHrvFeeling(action)
            CoachAction.LOG_BODY_FAT -> logBodyFat(action)
            CoachAction.LOG_WEIGHT -> logWeight(action)
            CoachAction.LOG_WORKOUT -> logWorkout(action)
            CoachAction.RESOLVE_JOURNAL_ENTRY -> resolveJournalEntry(action)
            CoachAction.UPDATE_REMINDERS -> updateReminders(action)
            // Navigation itself is done by the chat screen; this just validates the target.
            CoachAction.OPEN_SCREEN -> {
                require(action.screen in CoachAction.Screens.ALL) { "Hangry doesn't have that screen." }
                "Opened ${action.screen!!.replace('_', ' ')}"
            }
            else -> error("Hangry can't do that yet.")
        }
    }

    private suspend fun addSupplement(action: CoachAction): String {
        val p = action.supplement ?: error("Missing supplement details.")
        require(p.name.isNotBlank()) { "Missing supplement name." }
        val times = SupplementTimes.parse(p.times.joinToString(","))
        supplementRepository.save(
            SupplementDraft(
                name = p.name,
                brand = p.brand,
                form = p.form,
                doseAmount = p.doseAmount?.takeIf { it > 0 } ?: 1.0,
                doseUnit = p.doseUnit?.takeIf { it.isNotBlank() } ?: p.form ?: "serving",
                times = times,
                ingredients = p.ingredients,
                remindersEnabled = p.reminders && times.isNotEmpty(),
                notes = p.notes
            )
        )
        return "Added ${p.name}" + if (times.isNotEmpty()) " · ${times.joinToString { it.toString() }}" else ""
    }

    private suspend fun markTaken(action: CoachAction): String {
        val name = action.supplementName?.trim()?.lowercase() ?: error("Which supplement?")
        val dose = supplementRepository.current().todayDoses
            .filter { !it.taken && (it.supplement.name.lowercase().contains(name) || name.contains(it.supplement.name.lowercase())) }
            .minByOrNull { it.time } ?: error("No untaken dose of that supplement today.")
        supplementRepository.setTaken(dose.supplement.id, dose.time, taken = true)
        return "Marked ${dose.supplement.name} taken"
    }

    private suspend fun logMeal(action: CoachAction): String {
        val m = action.meal ?: error("Missing meal details.")
        require(m.foodName.isNotBlank() && m.calories in 0..10_000) { "That meal estimate doesn't look right." }
        val entry = FoodLogEntity(
            date = LocalDate.now(zone),
            timestamp = Instant.now(),
            source = FoodLogSource.PHOTO,
            foodName = m.foodName,
            calories = m.calories,
            proteinG = m.proteinG,
            carbsG = m.carbsG,
            fatG = m.fatG,
            fiberG = m.fiberG,
            sugarG = m.sugarG,
            sodiumMg = m.sodiumMg
        )
        val id = foodLogRepository.insert(entry)
        if (writeNutritionRecord(entry)) foodLogRepository.markSyncedToHealthConnect(id)
        return "Logged ${m.foodName} · ${m.calories} kcal"
    }

    private suspend fun addReading(action: CoachAction): String {
        val r = action.reading ?: error("Missing reading details.")
        val type = MarkerType.fromId(r.marker) ?: error("Unknown marker \"${r.marker}\".")
        val value = FhirHealthRecordParser.toCanonical(type, r.value, r.unit) ?: error("Unsupported unit \"${r.unit}\".")
        val secondary = if (type.hasSecondaryValue) {
            r.secondaryValue ?: error("Blood pressure needs both numbers.")
        } else null
        require(value > 0) { "That value doesn't look right." }
        val date = r.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isAfter(LocalDate.now(zone)) }
            ?: LocalDate.now(zone)
        val measuredAt = if (date == LocalDate.now(zone)) Instant.now() else date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        healthRecordsRepository.addReading(
            type = type,
            value = value,
            secondaryValue = secondary,
            measuredAt = measuredAt,
            glucoseContext = GlucoseContext.fromName(r.context) ?: GlucoseContext.RANDOM.takeIf { type == MarkerType.BLOOD_GLUCOSE },
            note = "Added via Ask Dash"
        )
        return "Saved ${type.label.lowercase()}"
    }

    private suspend fun setGoal(action: CoachAction): String {
        val g = action.goal ?: error("Missing goal details.")
        val type = MarkerType.fromId(g.marker) ?: error("Unknown marker \"${g.marker}\".")
        val target = FhirHealthRecordParser.toCanonical(type, g.targetValue, g.unit) ?: error("Unsupported unit \"${g.unit}\".")
        val date = g.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.isAfter(LocalDate.now(zone)) }
        healthRecordsRepository.setGoal(type, target, g.targetSecondary.takeIf { type.hasSecondaryValue }, date)
        return "Goal set for ${type.label.lowercase()}"
    }

    private suspend fun updateGoals(action: CoachAction): String {
        val repo = userProfileRepository ?: error("Goals can't be changed here.")
        val g = action.goals ?: error("Missing goal details.")
        val profile = repo.getProfileSync() ?: com.kevan.hangry.data.local.entity.UserProfileEntity()
        val changes = mutableListOf<String>()
        var updated = profile
        g.weightGoalKg?.let {
            require(it in 30.0..300.0) { "That goal weight doesn't look right." }
            updated = updated.copy(weightGoalKg = it); changes += "goal weight ${"%.1f".format(java.util.Locale.US, it)} kg"
        }
        g.goalDate?.let { raw ->
            val date = runCatching { LocalDate.parse(raw) }.getOrNull()?.takeIf { it.isAfter(LocalDate.now(zone)) }
                ?: error("The target date needs to be in the future.")
            updated = updated.copy(goalTargetDate = date); changes += "by $date"
        }
        g.dailySteps?.let {
            require(it in 1_000..50_000) { "That step goal doesn't look right." }
            updated = updated.copy(dailyStepGoal = it); changes += "$it steps/day"
        }
        g.dailyActiveCalories?.let {
            require(it in 50..3_000) { "That active-calorie goal doesn't look right." }
            updated = updated.copy(dailyActiveCaloriesGoal = it); changes += "$it active kcal/day"
        }
        g.sleepHours?.let {
            require(it in 4.0..12.0) { "That sleep target doesn't look right." }
            updated = updated.copy(sleepDurationTargetMinutes = (it * 60).toInt()); changes += "${"%.1f".format(java.util.Locale.US, it)} h sleep"
        }
        require(changes.isNotEmpty()) { "Nothing to change." }
        repo.saveProfile(updated)
        return "Updated " + changes.joinToString(", ")
    }

    private suspend fun logWeight(action: CoachAction): String {
        val dao = weightDao ?: error("Weight can't be logged here.")
        val w = action.weight ?: error("Missing weight.")
        val kg = when (w.unit?.trim()?.lowercase()) {
            null, "", "kg", "kgs", "kilograms" -> w.value
            "lb", "lbs", "pounds" -> w.value * 0.45359237
            else -> error("Unsupported unit \"${w.unit}\".")
        }
        require(kg in 25.0..350.0) { "That weight doesn't look right." }
        val now = Instant.now()
        dao.insertOrIgnore(
            listOf(
                com.kevan.hangry.data.local.entity.WeightMeasurementEntity(
                    recordFingerprint = "manual:ask-dash:${now.toEpochMilli()}",
                    sourcePackageName = "manual",
                    timestamp = now,
                    weightKg = kg
                )
            )
        )
        userProfileRepository?.let { repo ->
            repo.getProfileSync()?.let { repo.saveProfile(it.copy(currentWeightKg = kg)) }
        }
        return "Logged ${"%.1f".format(java.util.Locale.US, kg)} kg"
    }

    private suspend fun updateMeal(action: CoachAction): String {
        val name = action.mealName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: error("Which meal?")
        val m = action.meal ?: error("Missing the new meal details.")
        require(m.calories in 0..10_000) { "That meal estimate doesn't look right." }
        val today = LocalDate.now(zone)
        val candidates = foodLogRepository.getBetween(today.minusDays(2), today).first()
        val entry = candidates
            .filter { it.foodName.lowercase().contains(name) || name.contains(it.foodName.lowercase()) }
            .maxByOrNull { it.timestamp } ?: error("Couldn't find a recent meal called \"${action.mealName}\".")
        foodLogRepository.update(
            entry.copy(
                foodName = m.foodName.ifBlank { entry.foodName },
                calories = m.calories,
                proteinG = m.proteinG,
                carbsG = m.carbsG,
                fatG = m.fatG,
                fiberG = m.fiberG,
                sugarG = m.sugarG,
                sodiumMg = m.sodiumMg
            )
        )
        return "Updated ${entry.foodName} · ${m.calories} kcal"
    }

    private suspend fun addMealPlan(action: CoachAction): String {
        val repo = mealPlanRepository ?: error("Meal plans can't be changed here.")
        val p = action.mealPlan ?: error("Missing meal plan details.")
        require(p.name.isNotBlank() && p.calories in 0..5_000) { "That meal plan entry doesn't look right." }
        val type = p.mealType?.uppercase()?.takeIf { it in setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK", "OTHER") } ?: "OTHER"
        repo.upsert(
            com.kevan.hangry.data.local.entity.MealPlanEntity(
                name = p.name, mealType = type, calories = p.calories,
                proteinG = p.proteinG, carbsG = p.carbsG, fatG = p.fatG
            )
        )
        return "Added ${p.name} to your meal plan"
    }

    private suspend fun updateSupplement(action: CoachAction): String {
        val name = action.supplementName?.trim()?.lowercase() ?: error("Which supplement?")
        val u = action.supplementUpdate ?: error("Missing what to change.")
        val s = supplementRepository.current().supplements.firstOrNull {
            it.name.lowercase().contains(name) || name.contains(it.name.lowercase())
        } ?: error("You don't have a supplement called \"${action.supplementName}\".")
        val times = u.times?.let { SupplementTimes.parse(it.joinToString(",")) } ?: s.times
        u.doseAmount?.let { require(it > 0) { "That dose doesn't look right." } }
        supplementRepository.save(
            SupplementDraft(
                id = s.id, name = s.name, brand = s.brand, form = s.form,
                doseAmount = u.doseAmount ?: s.doseAmount,
                doseUnit = u.doseUnit?.takeIf { it.isNotBlank() } ?: s.doseUnit,
                times = times,
                ingredients = s.ingredients,
                remindersEnabled = (u.reminders ?: s.remindersEnabled) && times.isNotEmpty(),
                notes = u.notes ?: s.notes,
                photoPath = s.photoPath,
                active = u.active ?: s.active
            )
        )
        return if (u.active == false) "Paused ${s.name}" else "Updated ${s.name}"
    }

    private suspend fun requireFemale() {
        val sex = userProfileRepository?.getProfileSync()?.biologicalSex
        require(sex == com.kevan.hangry.domain.model.BiologicalSex.FEMALE.name) {
            "Pregnancy and cycle tracking are only available when your sex is set to female."
        }
    }

    private suspend fun setPregnancy(action: CoachAction): String {
        requireFemale()
        val p = action.pregnancy ?: error("Missing pregnancy details.")
        val due = p.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: error("That due date isn't valid.") }
        healthRecordsRepository.setPregnancy(p.pregnant, due)
        return if (p.pregnant) "Pregnancy saved" + (due?.let { " · due $it" } ?: "") else "Pregnancy turned off"
    }

    private suspend fun logPeriod(action: CoachAction): String {
        requireFemale()
        val p = action.period ?: error("Missing period dates.")
        val start = runCatching { LocalDate.parse(p.startDate) }.getOrNull() ?: error("That start date isn't valid.")
        require(!start.isAfter(LocalDate.now(zone))) { "The start date can't be in the future." }
        val end = p.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isBefore(start) }
        healthRecordsRepository.logPeriod(start, end)
        return "Logged period from $start" + (end?.let { " to $it" } ?: "")
    }

    private suspend fun updateProfile(action: CoachAction): String {
        val repo = userProfileRepository ?: error("Your profile can't be changed here.")
        val p = action.profile ?: error("Missing profile details.")
        val toCm = when (p.lengthUnit?.trim()?.lowercase()) {
            null, "", "cm" -> 1.0
            "in", "inch", "inches" -> 2.54
            else -> error("Unsupported unit \"${p.lengthUnit}\".")
        }
        fun cm(value: Double?, range: ClosedFloatingPointRange<Double>, label: String): Double? = value?.let {
            val v = it * toCm
            require(v in range) { "That $label doesn't look right." }
            v
        }
        var profile = repo.getProfileSync() ?: com.kevan.hangry.data.local.entity.UserProfileEntity()
        val changes = mutableListOf<String>()
        p.age?.let { require(it in 13..110) { "That age doesn't look right." }; profile = profile.copy(age = it); changes += "age $it" }
        p.sex?.let { raw ->
            val sex = runCatching { com.kevan.hangry.domain.model.BiologicalSex.valueOf(raw.uppercase()) }.getOrNull() ?: error("Sex must be male, female or other.")
            profile = profile.copy(biologicalSex = sex.name); changes += "sex ${sex.name.lowercase()}"
        }
        cm(p.height, 100.0..250.0, "height")?.let { profile = profile.copy(heightCm = it); changes += "height ${"%.0f".format(java.util.Locale.US, it)} cm" }
        cm(p.neck, 20.0..70.0, "neck measurement")?.let { profile = profile.copy(neckCircumferenceCm = it); changes += "neck" }
        cm(p.chest, 50.0..200.0, "chest measurement")?.let { profile = profile.copy(chestCircumferenceCm = it); changes += "chest" }
        cm(p.waist, 40.0..200.0, "waist measurement")?.let { profile = profile.copy(waistCircumferenceCm = it); changes += "waist" }
        cm(p.hip, 50.0..200.0, "hip measurement")?.let { profile = profile.copy(hipCircumferenceCm = it); changes += "hips" }
        require(changes.isNotEmpty()) { "Nothing to change." }
        repo.saveProfile(profile)
        return "Updated " + changes.joinToString(", ")
    }

    private suspend fun logSleep(action: CoachAction): String {
        val repo = sleepRepository ?: error("Sleep can't be logged here.")
        val p = action.sleep ?: error("Missing sleep details.")
        require(p.durationMinutes in 30..16 * 60) { "That sleep length doesn't look right." }
        val end = p.endTime?.let { raw ->
            runCatching { java.time.LocalDateTime.parse(raw).atZone(zone).toInstant() }.getOrNull() ?: error("That wake time isn't valid.")
        } ?: Instant.now()
        require(!end.isAfter(Instant.now().plusSeconds(600))) { "The wake time can't be in the future." }
        val start = end.minusSeconds(p.durationMinutes * 60L)
        repo.insertSessions(
            listOf(
                com.kevan.hangry.data.local.entity.SleepSessionEntity(
                    recordFingerprint = "manual-sleep-${end.toEpochMilli()}",
                    sourceRecordId = "manual-${end.toEpochMilli()}",
                    sourcePackageName = "com.kevan.hangry.manual",
                    startTime = start,
                    endTime = end,
                    durationMinutes = p.durationMinutes,
                    timeInBedMinutes = p.durationMinutes,
                    isManualEntry = true
                )
            )
        )
        // Recompute so sleep score, recovery and strain pick the new session up.
        healthSyncManager?.syncRecent()?.collect { }
        return "Logged ${p.durationMinutes / 60}h ${p.durationMinutes % 60}m of sleep"
    }

    private suspend fun setHrvFeeling(action: CoachAction): String {
        val manager = healthSyncManager ?: error("Recovery can't be changed here.")
        val feeling = com.kevan.hangry.domain.model.HrvFeeling.fromName(action.feeling?.uppercase()) ?: error("Pick excellent, good, okay, tired or drained.")
        // Only used by recovery on days with no HRV reading; real HRV always wins.
        manager.setHrvFeeling(LocalDate.now(zone), feeling)
        return "Recovery updated - feeling ${feeling.label.lowercase()}"
    }

    private suspend fun logBodyFat(action: CoachAction): String {
        val repo = bodyFatRepository ?: error("Body fat can't be logged here.")
        val p = action.bodyFat ?: error("Missing body fat.")
        require(p.percentage in 3.0..65.0) { "That body fat % doesn't look right." }
        val profile = userProfileRepository?.getProfileSync()
        val sex = profile?.biologicalSex?.let { runCatching { com.kevan.hangry.domain.model.BiologicalSex.valueOf(it) }.getOrNull() }
            ?: com.kevan.hangry.domain.model.BiologicalSex.OTHER
        val weight = weightDao?.getLatestWeightSync()?.weightKg ?: profile?.currentWeightKg
        val fat = weight?.let { it * p.percentage / 100.0 }
        repo.saveScan(
            com.kevan.hangry.data.local.entity.BodyFatScanEntity(
                date = LocalDate.now(zone),
                bodyFatPercentage = p.percentage,
                category = com.kevan.hangry.domain.calculation.HangryBodyFatCalculator().classifyCategory(p.percentage, sex).displayName,
                method = "REPORTED",
                weightKg = weight,
                leanMassKg = fat?.let { weight - it },
                fatMassKg = fat,
                consistencyNote = p.source
            )
        )
        return "Saved body fat ${"%.1f".format(java.util.Locale.US, p.percentage)}%"
    }

    private suspend fun addProfileItem(action: CoachAction, kind: HealthProfileKind): String {
        val item = action.item ?: error("Missing details.")
        require(item.name.isNotBlank()) { "Missing name." }
        healthRecordsRepository.addProfileItem(kind, item.name, item.note)
        return "Added ${item.name} to your ${if (kind == HealthProfileKind.ALLERGY) "allergies" else "conditions"}"
    }

    private suspend fun logWorkout(action: CoachAction): String {
        val payload = action.workout ?: error("Missing workout details.")
        require(payload.durationMinutes > 0) { "Duration must be greater than zero." }
        val dao = exerciseSessionDao ?: error("Workout storage unavailable.")
        val now = Instant.now()
        val start = payload.startTime?.let { runCatching { LocalDateTime.parse(it).atZone(zone).toInstant() }.getOrNull() }
            ?: now.minusSeconds(payload.durationMinutes * 60L)
        val end = start.plusSeconds(payload.durationMinutes * 60L)
        val rawType = payload.exerciseType.trim().uppercase().replace(" ", "_")
        val workoutType = WorkoutType.fromId(rawType)
        val fingerprint = "manual_${start.toEpochMilli()}_${workoutType.name}"
        val entity = ExerciseSessionEntity(
            recordFingerprint = fingerprint,
            exerciseType = workoutType.name,
            title = payload.title?.trim()?.ifBlank { null },
            startTime = start,
            endTime = end,
            durationMinutes = payload.durationMinutes,
            totalCalories = payload.calories,
            activeCalories = payload.calories,
            distanceMeters = payload.distanceKm?.let { it * 1000.0 },
            notes = payload.notes?.trim()?.ifBlank { null },
            dataQualityState = "VALID"
        )
        dao.insertOrIgnore(listOf(entity))
        healthSyncManager?.syncRecent()?.collect { }
        return "Logged ${workoutType.label} (${payload.durationMinutes} min" + (payload.calories?.let { ", ${it.toInt()} kcal" } ?: "") + ")."
    }

    private suspend fun resolveJournalEntry(action: CoachAction): String {
        val payload = action.resolveJournal ?: error("Missing journal entry details.")
        val dao = coachJournalDao ?: error("Journal storage unavailable.")
        val entries = dao.getAllSync()
        val target = if (payload.entryId != null) {
            entries.firstOrNull { it.id == payload.entryId }
        } else if (!payload.summary.isNullOrBlank()) {
            entries.firstOrNull { it.summary.contains(payload.summary, ignoreCase = true) || it.content.contains(payload.summary, ignoreCase = true) }
        } else null
        requireNotNull(target) { "Could not find matching journal memory to resolve." }
        dao.delete(target.id)
        return "Resolved and removed memory: \"${target.summary}\"."
    }

    private suspend fun updateReminders(action: CoachAction): String {
        val payload = action.reminders ?: error("Missing reminder preferences.")
        val ctx = context ?: error("App context unavailable.")
        val changes = mutableListOf<String>()
        payload.morningReadinessEnabled?.let {
            NudgePrefs.setMorningEnabled(ctx, it)
            changes += if (it) "morning brief on" else "morning brief off"
        }
        payload.bedtimeReminderEnabled?.let {
            NudgePrefs.setBedtimeEnabled(ctx, it)
            changes += if (it) "bedtime reminder on" else "bedtime reminder off"
        }
        require(changes.isNotEmpty()) { "No reminder changes specified." }
        return "Updated reminders: ${changes.joinToString(", ")}."
    }
}
