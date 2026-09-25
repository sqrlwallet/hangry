package com.kevan.hangry.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class SupplementIngredient(
    val name: String,
    val amount: Double? = null,
    val unit: String? = null,
    /** % of the daily value per serving, when the label lists it. */
    val dailyValuePercent: Double? = null
)

/** What the AI reads off a supplement photo; every field is a suggestion the user can edit. */
@Serializable
data class SupplementAnalysisResult(
    val name: String,
    val brand: String? = null,
    val form: String? = null,
    /** Units in one serving per the label, e.g. 2 (capsules). */
    val servingAmount: Double? = null,
    val servingUnit: String? = null,
    val ingredients: List<SupplementIngredient> = emptyList(),
    /** e.g. "Morning, with a meal that has some fat". */
    val suggestedTiming: String? = null,
    /** Label directions and any cautions relevant to the user's profile. */
    val notes: String? = null,
    val cautions: List<String> = emptyList()
)

data class Supplement(
    val id: Long,
    val name: String,
    val brand: String?,
    val form: String?,
    /** How many units per dose, e.g. 2. */
    val doseAmount: Double,
    /** e.g. "capsules", "scoop", "ml". */
    val doseUnit: String,
    /** When each daily dose is taken, sorted. */
    val times: List<LocalTime>,
    val ingredients: List<SupplementIngredient>,
    val remindersEnabled: Boolean,
    /** Hangry helps the user take it (check-offs, adherence, reminders); otherwise assumed taken. */
    val tracked: Boolean,
    val notes: String?,
    val photoPath: String?,
    val active: Boolean
)

/** One scheduled dose today and whether it's been taken. */
data class SupplementDose(
    val supplement: Supplement,
    val time: LocalTime,
    val taken: Boolean
)

data class SupplementsSnapshot(
    val supplements: List<Supplement> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Today's doses of tracked supplements, in time order. */
    val todayDoses: List<SupplementDose> = emptyList(),
    /** Doses taken / scheduled over the last 7 full days, per tracked supplement id. */
    val weekAdherence: Map<Long, Pair<Int, Int>> = emptyMap()
) {
    val active: List<Supplement> get() = supplements.filter { it.active }
    val takenToday: Int get() = todayDoses.count { it.taken }
    val nextDose: SupplementDose? get() {
        val now = LocalTime.now()
        return todayDoses.firstOrNull { !it.taken && !it.time.isBefore(now) } ?: todayDoses.firstOrNull { !it.taken }
    }
}

/** Parses/format "HH:mm" times as stored. */
object SupplementTimes {
    fun parse(raw: String?): List<LocalTime> =
        raw.orEmpty().split(',').mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted()

    fun format(times: List<LocalTime>): String =
        times.distinct().sorted().joinToString(",") { String.format(java.util.Locale.US, "%02d:%02d", it.hour, it.minute) }

    fun key(time: LocalTime): String = String.format(java.util.Locale.US, "%02d:%02d", time.hour, time.minute)
}
