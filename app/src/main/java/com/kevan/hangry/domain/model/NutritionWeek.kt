package com.kevan.hangry.domain.model

import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.domain.calculation.NutritionTargets
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** One day's logged totals. */
data class NutritionDay(
    val date: LocalDate,
    val meals: Int,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val sodiumMg: Double
)

/** A food the user logged more than once in the window. */
data class FrequentFood(val name: String, val times: Int)

/**
 * The last week of logging, boiled down for the weekly review. Averages are over the days that
 * have anything logged - an unlogged day is missing data, not a day of eating nothing.
 */
data class NutritionWeekSummary(
    val start: LocalDate,
    val end: LocalDate,
    /** Logged days only, oldest first. */
    val days: List<NutritionDay>,
    val frequentFoods: List<FrequentFood>,
    /** Meals logged at 9pm or later. */
    val lateMeals: Int,
    /** Distinct foods logged across the window. */
    val distinctFoods: Int
) {
    val daysLogged: Int get() = days.size
    val totalMeals: Int get() = days.sumOf { it.meals }
    private fun avg(f: (NutritionDay) -> Double) = if (days.isEmpty()) 0.0 else days.sumOf(f) / days.size
    val avgCalories: Int get() = avg { it.calories.toDouble() }.roundToInt()
    val avgProteinG: Double get() = avg { it.proteinG }
    val avgCarbsG: Double get() = avg { it.carbsG }
    val avgFatG: Double get() = avg { it.fatG }
    val avgFiberG: Double get() = avg { it.fiberG }
    val avgSugarG: Double get() = avg { it.sugarG }
    val avgSodiumMg: Double get() = avg { it.sodiumMg }
    val avgMealsPerDay: Double get() = avg { it.meals.toDouble() }
}

/** A plain-language, rule-based recommendation - available with or without AI. */
data class NutritionTip(val title: String, val body: String)

/** The AI's read of the week, on top of the rule-based [NutritionTip]s. */
data class NutritionReview(
    val headline: String,
    val overview: String,
    val wins: List<String> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val foodsToAdd: List<FoodToAdd> = emptyList(),
    val fiberPlan: String = ""
) {
    data class Recommendation(val title: String, val why: String, val how: String)
    data class FoodToAdd(val food: String, val why: String)
}

/** Practical fiber guidance, shown next to the fiber bar and in the weekly review. */
object FiberGuide {
    const val SUMMARY = "Fiber keeps you full, steadies blood sugar, feeds your gut bacteria and helps lower cholesterol. " +
        "Most adults need 25-38g a day - about 14g for every 1,000 kcal you eat."

    val HOW_TO = listOf(
        "Build up slowly - about 5g more a day each week - and drink more water as you go, or it can cause bloating.",
        "Spread it out: roughly 8-10g per meal works better than one huge bowl of bran.",
        "Swap refined for whole: brown rice, whole-wheat bread or pasta, oats instead of white versions.",
        "Add a scoop of beans, lentils or chickpeas to salads, soups and curries - about 7-8g per half cup.",
        "Keep the skin on potatoes, apples and pears, and pick berries, which are among the most fiber-dense fruit.",
        "Stir 1 tbsp chia or ground flax (~4-5g) into oats, yogurt or a smoothie.",
        "Snack on a handful of nuts, popcorn, hummus with veg or a piece of fruit instead of chips or sweets.",
        "Get both kinds: soluble (oats, beans, apples) helps blood sugar and cholesterol; insoluble (whole grains, veg skins) keeps you regular."
    )
}

object NutritionWeekAnalyzer {

    const val WINDOW_DAYS = 7L
    private const val LATE_MEAL_HOUR = 21

    /**
     * The 7 full days before [today]. Today is still in progress, so it only counts when nothing
     * was logged in those days - someone who just started logging still gets a review.
     */
    fun window(today: LocalDate, entries: List<FoodLogEntity>): Pair<LocalDate, LocalDate> {
        val fullStart = today.minusDays(WINDOW_DAYS)
        val fullEnd = today.minusDays(1)
        val hasFullDays = entries.any { it.date in fullStart..fullEnd }
        return if (hasFullDays) fullStart to fullEnd else today.minusDays(WINDOW_DAYS - 1) to today
    }

    fun summarize(
        entries: List<FoodLogEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): NutritionWeekSummary {
        val (start, end) = window(today, entries)
        val inWindow = entries.filter { it.date in start..end }
        val days = inWindow.groupBy { it.date }.toSortedMap().map { (date, meals) ->
            NutritionDay(
                date = date,
                meals = meals.size,
                calories = meals.sumOf { it.calories },
                proteinG = meals.sumOf { it.proteinG },
                carbsG = meals.sumOf { it.carbsG },
                fatG = meals.sumOf { it.fatG },
                fiberG = meals.sumOf { it.fiberG },
                sugarG = meals.sumOf { it.sugarG },
                sodiumMg = meals.sumOf { it.sodiumMg }
            )
        }
        val byName = inWindow.groupBy { it.foodName.trim().lowercase() }
        val frequent = byName.values
            .filter { it.size > 1 }
            .sortedByDescending { it.size }
            .take(8)
            .map { FrequentFood(it.first().foodName.trim(), it.size) }
        return NutritionWeekSummary(
            start = start,
            end = end,
            days = days,
            frequentFoods = frequent,
            lateMeals = inWindow.count { it.timestamp.atZone(zone).hour >= LATE_MEAL_HOUR },
            distinctFoods = byName.size
        )
    }

    /** Rule-based recommendations, most important first. */
    fun tips(summary: NutritionWeekSummary, calorieTarget: Int?): List<NutritionTip> {
        if (summary.daysLogged == 0) return emptyList()
        val tips = mutableListOf<NutritionTip>()
        val macros = NutritionTargets.macros(calorieTarget)
        val fiberTarget = NutritionTargets.fiberG(calorieTarget)

        if (summary.daysLogged < 4) {
            tips += NutritionTip(
                "Log a few more days",
                "Only ${summary.daysLogged} of 7 days have meals logged, so these averages are rough. Snap every meal for a week for a sharper picture."
            )
        }
        if (summary.avgFiberG < fiberTarget * 0.8) {
            val gap = (fiberTarget - summary.avgFiberG).roundToInt()
            tips += NutritionTip(
                "Add ~${gap}g of fiber a day",
                "You averaged ${summary.avgFiberG.roundToInt()}g of fiber against ~${fiberTarget.roundToInt()}g. " +
                    "Easy wins: a half cup of beans or lentils (~7g), oats with chia (~8g), or swapping white rice or bread for whole grain. " +
                    "Build up over a couple of weeks and drink more water."
            )
        }
        macros?.let { goals ->
            if (summary.avgProteinG < goals.proteinG * 0.8) {
                tips += NutritionTip(
                    "Bring protein up",
                    "You averaged ${summary.avgProteinG.roundToInt()}g against ~${goals.proteinG.roundToInt()}g. " +
                        "Aim for a palm-sized portion (25-30g) at every meal - eggs or Greek yogurt at breakfast, chicken, fish, tofu or lentils at lunch and dinner."
                )
            }
        }
        calorieTarget?.takeIf { it > 0 }?.let { target ->
            val ratio = summary.avgCalories.toDouble() / target
            if (ratio > 1.1) {
                tips += NutritionTip(
                    "Eating above your target",
                    "You averaged ${summary.avgCalories} kcal against $target. Trimming cooking oil, sauces and sugary drinks is the easiest place to find 200-300 kcal."
                )
            } else if (ratio < 0.8 && summary.daysLogged >= 4) {
                tips += NutritionTip(
                    "Possibly under-eating",
                    "You averaged ${summary.avgCalories} kcal against $target. If that's not a missed log, a protein-rich snack can help energy and recovery."
                )
            }
        }
        if (summary.avgSodiumMg > 2300) {
            tips += NutritionTip(
                "Watch the salt",
                "You averaged ${summary.avgSodiumMg.roundToInt()}mg of sodium (aim under 2,300mg). Processed meats, instant noodles, sauces and takeaway are usually the source - herbs, citrus and spices add flavour instead."
            )
        }
        if (summary.avgSugarG > 50) {
            tips += NutritionTip(
                "Cut back on sugar",
                "You averaged ${summary.avgSugarG.roundToInt()}g of sugar a day. Swapping one sweet drink or dessert for fruit or yogurt makes a big dent."
            )
        }
        if (summary.lateMeals >= 3) {
            tips += NutritionTip(
                "Late eating",
                "${summary.lateMeals} meals were logged after 9pm. Finishing eating 2-3 hours before bed tends to help sleep quality and morning hunger."
            )
        }
        if (summary.daysLogged >= 4 && summary.distinctFoods <= 6) {
            tips += NutritionTip(
                "Mix it up",
                "Only ${summary.distinctFoods} different foods this week. Rotating in a new vegetable, grain or protein each week widens the vitamins and minerals you get."
            )
        }
        if (tips.isEmpty()) {
            tips += NutritionTip(
                "Solid week",
                "Calories, protein and fiber all look on track. Keep the same rhythm and keep logging."
            )
        }
        return tips
    }

    /** The week as plain text for the AI reviewer. */
    fun promptText(
        summary: NutritionWeekSummary,
        calorieTarget: Int?,
        profile: String?,
        allergies: List<String>
    ): String = buildString {
        val macros = NutritionTargets.macros(calorieTarget)
        appendLine("Window: ${summary.start} to ${summary.end} (${summary.daysLogged} of 7 days logged, ${summary.totalMeals} meals).")
        profile?.takeIf { it.isNotBlank() }?.let { appendLine("About the user: $it") }
        if (allergies.isNotEmpty()) appendLine("Allergies (never suggest these): ${allergies.joinToString(", ")}")
        appendLine(
            "Daily targets: " + (calorieTarget?.let { "$it kcal" } ?: "calorie target unknown") +
                (macros?.let { ", protein ${it.proteinG.roundToInt()}g, carbs ${it.carbsG.roundToInt()}g, fat ${it.fatG.roundToInt()}g" } ?: "") +
                ", fiber ${NutritionTargets.fiberG(calorieTarget).roundToInt()}g, sodium <2300mg, added sugar <50g."
        )
        appendLine(
            "Averages per logged day: ${summary.avgCalories} kcal, protein ${summary.avgProteinG.roundToInt()}g, " +
                "carbs ${summary.avgCarbsG.roundToInt()}g, fat ${summary.avgFatG.roundToInt()}g, fiber ${summary.avgFiberG.roundToInt()}g, " +
                "sugar ${summary.avgSugarG.roundToInt()}g, sodium ${summary.avgSodiumMg.roundToInt()}mg, " +
                "${"%.1f".format(summary.avgMealsPerDay)} meals/day, ${summary.lateMeals} meals after 9pm, ${summary.distinctFoods} distinct foods."
        )
        appendLine("Per day:")
        summary.days.forEach { d ->
            appendLine(
                "- ${d.date.dayOfWeek.name.take(3)} ${d.date}: ${d.meals} meals, ${d.calories} kcal, P${d.proteinG.roundToInt()} " +
                    "C${d.carbsG.roundToInt()} F${d.fatG.roundToInt()} fiber ${d.fiberG.roundToInt()}g sugar ${d.sugarG.roundToInt()}g sodium ${d.sodiumMg.roundToInt()}mg"
            )
        }
        if (summary.frequentFoods.isNotEmpty()) {
            appendLine("Most logged: " + summary.frequentFoods.joinToString(", ") { "${it.name} (x${it.times})" })
        }
    }

    /** Every meal in the window, oldest first, so the reviewer can see what was actually eaten. */
    fun mealList(entries: List<FoodLogEntity>, summary: NutritionWeekSummary, zone: ZoneId = ZoneId.systemDefault()): String =
        entries.filter { it.date in summary.start..summary.end }
            .sortedBy { it.timestamp }
            .take(MAX_MEALS_IN_PROMPT)
            .joinToString("\n") { e ->
                val t = e.timestamp.atZone(zone).toLocalTime()
                "- ${e.date} ${"%02d:%02d".format(t.hour, t.minute)} ${e.foodName}: ${e.calories} kcal, " +
                    "P${e.proteinG.roundToInt()} C${e.carbsG.roundToInt()} F${e.fatG.roundToInt()} fiber ${e.fiberG.roundToInt()}g"
            }

    private const val MAX_MEALS_IN_PROMPT = 80
}
