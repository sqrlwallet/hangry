package com.kevan.hangry.ui.share

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.repository.BodyAgeSnapshot
import com.kevan.hangry.domain.calculation.HangryStrainCalculator
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.coach.dashMood
import com.kevan.hangry.ui.dashboard.DashboardUiState
import com.kevan.hangry.ui.fasting.FastingColor
import com.kevan.hangry.ui.theme.AzureRadiance
import com.kevan.hangry.ui.theme.BlueRibbonLight
import com.kevan.hangry.ui.theme.GreenHaze
import com.kevan.hangry.ui.theme.Pumpkin
import com.kevan.hangry.ui.theme.Supernova
import com.kevan.hangry.ui.theme.TextMutedDark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One number on a share card, e.g. "7h 42m" / "Sleep". */
data class ShareStat(val value: String, val label: String, @ColorInt val color: Int)

/** One bar (or donut slice); a null [value] is a day with no data. */
data class ChartPoint(val label: String, val value: Double?, @ColorInt val color: Int)

enum class ChartType { BARS, DONUT }

/** A small chart for a trend card; [goal] draws a dashed line across the bars. */
data class ShareChart(val type: ChartType, val points: List<ChartPoint>, val goal: Double? = null)

/**
 * An image to post: Dash in [mood], a [headline], an optional [chart], up to four [stats] and
 * the app's name and link. Only what's on the card leaves the phone - [ShareCardRenderer] draws
 * it, the user picks where it goes from the share sheet.
 */
data class ShareCard(
    /** Short name for the file, e.g. "sleep". */
    val kind: String,
    /** What the Share screen calls it, e.g. "Sleep trend". */
    val title: String,
    /** Top right of the card: a day or a range of days. */
    val dateLabel: String,
    val mood: DashMood,
    @ColorInt val accent: Int,
    val headline: String,
    val message: String? = null,
    val chart: ShareChart? = null,
    val stats: List<ShareStat> = emptyList(),
    /** Caption for apps that take one (WhatsApp, X, Messages); ends with the app link. */
    val shareText: String
)

/** Stored days for the trend cards, [start] to [end] inclusive. */
data class ShareTrends(
    val start: LocalDate,
    val end: LocalDate,
    val summaries: List<DailyHealthSummaryEntity> = emptyList(),
    val scores: List<RecoveryScoreEntity> = emptyList(),
    val food: List<FoodLogEntity> = emptyList()
) {
    val days: List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
}

/** Builds the cards the Share screen offers. Each returns null when there's nothing to show. */
object ShareCards {

    /** Where people get the app; on every card and caption. */
    const val APP_URL = "https://github.com/sqrlwallet/hangry"
    val APP_URL_SHORT = APP_URL.removePrefix("https://")

    const val MAX_STATS = 4

    /** Trend cards need at least this many days with data, so one stray reading isn't a "trend". */
    const val MIN_TREND_DAYS = 2

    /** Every card there's data for, in the order the Share screen lists them. */
    fun all(context: Context, state: DashboardUiState, trends: ShareTrends?): List<ShareCard> = buildList {
        add(today(context, state))
        streaks(context, state.streaks, state.selectedDate)?.let(::add)
        state.bodyAge?.let { bodyAge(context, it, LocalDate.now()) }?.let(::add)
        if (trends != null) {
            recoveryTrend(context, trends)?.let(::add)
            sleepTrend(context, trends, state.sleepGoalMinutes)?.let(::add)
            stepsTrend(context, trends, state.dailyStepGoal)?.let(::add)
            strainTrend(context, trends)?.let(::add)
            caloriesTrend(context, trends, state.calorieGoal?.dailyCalorieTarget)?.let(::add)
            macros(context, trends)?.let(::add)
        }
    }

    /** The selected day on Today: recovery, sleep, strain and steps (whichever there are). */
    fun today(context: Context, state: DashboardUiState): ShareCard {
        val pending = state.isPendingSleepData
        val recovery = state.recoveryScore?.state
            ?.let { runCatching { RecoveryState.valueOf(it) }.getOrNull() }
            ?: RecoveryState.BUILDING_BASELINE
        val score = state.recoveryScore?.score?.takeUnless { pending }
        val summary = state.dailySummary
        val sleepMin = summary?.sleepDurationMinutes?.takeIf { it > 0 }
        val strain = summary?.dayStrain?.coerceIn(0.0, HangryStrainCalculator.MAX_STRAIN)
        val steps = summary?.steps?.takeIf { it > 0 }

        val stats = buildList {
            if (score != null) add(ShareStat("$score%", context.getString(R.string.share_stat_recovery), recovery.color()))
            if (sleepMin != null) add(ShareStat(hoursMinutes(context, sleepMin), context.getString(R.string.share_stat_sleep), AzureRadiance.toArgb()))
            if (strain != null && strain > 0) add(
                ShareStat(
                    oneDecimal(strain),
                    context.getString(if (state.isViewingToday) R.string.share_stat_strain_so_far else R.string.share_stat_strain),
                    Pumpkin.toArgb()
                )
            )
            if (steps != null) add(ShareStat(thousands(steps), context.getString(R.string.share_stat_steps), BlueRibbonLight.toArgb()))
            if (state.todayActiveCalories >= 1) add(ShareStat(thousands(state.todayActiveCalories.toLong()), context.getString(R.string.share_stat_active_kcal), Supernova.toArgb()))
            if (state.todayActiveMinutes > 0) add(ShareStat(hoursMinutes(context, state.todayActiveMinutes), context.getString(R.string.share_stat_active_time), GreenHaze.toArgb()))
        }.take(MAX_STATS)

        val headline = when {
            score == null -> context.getString(if (state.isViewingToday) R.string.share_today_headline else R.string.share_day_headline)
            recovery == RecoveryState.PRIMED -> context.getString(R.string.dashboard_briefing_headline_primed)
            recovery == RecoveryState.BALANCED -> context.getString(R.string.dashboard_briefing_headline_balanced)
            else -> context.getString(R.string.dashboard_briefing_headline_rebuild)
        }
        return ShareCard(
            kind = "today",
            title = context.getString(if (state.isViewingToday) R.string.share_title_today else R.string.share_title_day),
            dateLabel = dayLabel(state.selectedDate),
            mood = if (score == null) DashMood.CHEER else recovery.dashMood(),
            accent = if (score == null) Pumpkin.toArgb() else recovery.color(),
            headline = headline,
            stats = stats,
            shareText = caption(context, if (score != null) context.getString(R.string.share_caption_recovery, score) else headline)
        )
    }

    /** Running streaks, longest first. Null when no streak is running. */
    fun streaks(context: Context, streaks: List<Streak>, date: LocalDate): ShareCard? {
        val running = streaks.filter { it.current > 0 }.sortedByDescending { it.current }
        val lead = running.firstOrNull() ?: return null
        val headline = context.getString(R.string.share_streak_headline, lead.current, lead.type.label.lowercase())
        return ShareCard(
            kind = "streaks",
            title = context.getString(R.string.share_title_streaks),
            dateLabel = dayLabel(date),
            mood = DashMood.STREAK,
            accent = Pumpkin.toArgb(),
            headline = headline,
            message = context.getString(R.string.share_streak_best, lead.best).takeIf { lead.best > lead.current },
            stats = running.take(MAX_STATS).map {
                ShareStat(
                    context.resources.getQuantityString(R.plurals.share_streak_days, it.current, it.current),
                    context.getString(it.type.shortLabel()),
                    it.type.color()
                )
            },
            shareText = caption(context, headline)
        )
    }

    /** Body Age against real age, and how it moved over the month. Null until there's a result. */
    fun bodyAge(context: Context, snapshot: BodyAgeSnapshot, today: LocalDate): ShareCard? {
        val current = snapshot.current ?: return null
        val difference = current.difference
        val younger = difference <= -0.5
        val headline = when {
            younger -> context.getString(R.string.share_body_age_younger, oneDecimal(-difference))
            difference >= 0.5 -> context.getString(R.string.share_body_age_headline, oneDecimal(current.bodyAge))
            else -> context.getString(R.string.share_body_age_even)
        }
        val change = snapshot.previous?.let { current.bodyAge - it.bodyAge }?.takeIf { abs(it) >= 0.1 }
        return ShareCard(
            kind = "body-age",
            title = context.getString(R.string.share_title_body_age),
            dateLabel = dayLabel(today),
            mood = if (younger) DashMood.LEVEL_UP else DashMood.HAPPY,
            accent = if (younger) GreenHaze.toArgb() else Supernova.toArgb(),
            headline = headline,
            stats = buildList {
                add(ShareStat(oneDecimal(current.bodyAge), context.getString(R.string.share_stat_body_age), if (younger) GreenHaze.toArgb() else Supernova.toArgb()))
                change?.let {
                    add(
                        ShareStat(
                            context.getString(R.string.share_years, (if (it < 0) "−" else "+") + oneDecimal(abs(it))),
                            context.getString(R.string.share_stat_vs_last_month),
                            if (it < 0) GreenHaze.toArgb() else Supernova.toArgb()
                        )
                    )
                }
            },
            shareText = caption(context, headline)
        )
    }

    fun recoveryTrend(context: Context, trends: ShareTrends): ShareCard? {
        val byDay = trends.scores.filter { it.score != null }.associateBy { it.date }
        if (byDay.size < MIN_TREND_DAYS) return null
        val scores = byDay.values.mapNotNull { it.score }
        val average = scores.average().roundToInt()
        val primed = byDay.values.count { it.state == RecoveryState.PRIMED.name }
        val headline = context.getString(R.string.share_recovery_headline, average)
        return trendCard(
            context, trends, kind = "recovery", title = R.string.share_title_recovery,
            mood = DashMood.PUSH, accent = GreenHaze.toArgb(), headline = headline,
            chart = ShareChart(ChartType.BARS, trends.days.mapIndexed { i, day ->
                val score = byDay[day]
                val state = score?.state?.let { runCatching { RecoveryState.valueOf(it) }.getOrNull() } ?: RecoveryState.BUILDING_BASELINE
                ChartPoint(barLabel(trends, i, day), score?.score?.toDouble(), state.color())
            }),
            stats = listOf(
                ShareStat("$average%", context.getString(R.string.share_stat_average), GreenHaze.toArgb()),
                ShareStat("${scores.max()}%", context.getString(R.string.share_stat_best_day), GreenHaze.toArgb()),
                ShareStat("$primed", context.getString(R.string.share_stat_primed_days), GreenHaze.toArgb())
            )
        )
    }

    fun sleepTrend(context: Context, trends: ShareTrends, goalMinutes: Int): ShareCard? {
        val byDay = trends.summaries.filter { (it.sleepDurationMinutes ?: 0) > 0 }.associate { it.date to it.sleepDurationMinutes!! }
        if (byDay.size < MIN_TREND_DAYS) return null
        val average = byDay.values.average().roundToInt()
        val atGoal = byDay.values.count { it >= goalMinutes }
        val headline = context.getString(R.string.share_sleep_headline, hoursMinutes(context, average))
        return trendCard(
            context, trends, kind = "sleep", title = R.string.share_title_sleep,
            mood = DashMood.SLEEP_GREAT, accent = AzureRadiance.toArgb(), headline = headline,
            chart = ShareChart(
                ChartType.BARS,
                trends.days.mapIndexed { i, day -> ChartPoint(barLabel(trends, i, day), byDay[day]?.div(60.0), AzureRadiance.toArgb()) },
                goal = goalMinutes / 60.0
            ),
            stats = listOf(
                ShareStat(hoursMinutes(context, average), context.getString(R.string.share_stat_average), AzureRadiance.toArgb()),
                ShareStat(hoursMinutes(context, byDay.values.max()), context.getString(R.string.share_stat_best_night), AzureRadiance.toArgb()),
                ShareStat("$atGoal/${byDay.size}", context.getString(R.string.share_stat_nights_at_goal), AzureRadiance.toArgb())
            )
        )
    }

    fun stepsTrend(context: Context, trends: ShareTrends, goal: Long): ShareCard? {
        val byDay = trends.summaries.filter { (it.steps ?: 0L) > 0 }.associate { it.date to it.steps!! }
        if (byDay.size < MIN_TREND_DAYS) return null
        val average = byDay.values.average().roundToInt().toLong()
        val headline = context.getString(R.string.share_steps_headline, thousands(average))
        return trendCard(
            context, trends, kind = "steps", title = R.string.share_title_steps,
            mood = DashMood.WORKOUT, accent = BlueRibbonLight.toArgb(), headline = headline,
            chart = ShareChart(
                ChartType.BARS,
                trends.days.mapIndexed { i, day -> ChartPoint(barLabel(trends, i, day), byDay[day]?.toDouble(), BlueRibbonLight.toArgb()) },
                goal = goal.toDouble()
            ),
            stats = listOf(
                ShareStat(thousands(average), context.getString(R.string.share_stat_daily_average), BlueRibbonLight.toArgb()),
                ShareStat(thousands(byDay.values.sum()), context.getString(R.string.share_stat_total), BlueRibbonLight.toArgb()),
                ShareStat(thousands(byDay.values.max()), context.getString(R.string.share_stat_best_day), BlueRibbonLight.toArgb())
            )
        )
    }

    fun strainTrend(context: Context, trends: ShareTrends): ShareCard? {
        val days = trends.summaries.filter { (it.dayStrain ?: 0.0) > 0 }
        if (days.size < MIN_TREND_DAYS) return null
        val byDay = days.associate { it.date to it.dayStrain!!.coerceIn(0.0, HangryStrainCalculator.MAX_STRAIN) }
        val average = byDay.values.average()
        val workouts = trends.summaries.sumOf { it.exerciseCount }
        val headline = context.getString(R.string.share_strain_headline, oneDecimal(average))
        return trendCard(
            context, trends, kind = "strain", title = R.string.share_title_strain,
            mood = DashMood.STRENGTH, accent = Pumpkin.toArgb(), headline = headline,
            chart = ShareChart(ChartType.BARS, trends.days.mapIndexed { i, day -> ChartPoint(barLabel(trends, i, day), byDay[day], Pumpkin.toArgb()) }),
            stats = listOf(
                ShareStat(oneDecimal(average), context.getString(R.string.share_stat_average), Pumpkin.toArgb()),
                ShareStat(oneDecimal(byDay.values.max()), context.getString(R.string.share_stat_peak), Pumpkin.toArgb()),
                ShareStat("$workouts", context.getString(R.string.share_stat_workouts), Pumpkin.toArgb())
            )
        )
    }

    fun caloriesTrend(context: Context, trends: ShareTrends, target: Int?): ShareCard? {
        val byDay = trends.food.groupBy { it.date }.mapValues { (_, entries) -> entries.sumOf { it.calories } }.filterValues { it > 0 }
        if (byDay.size < MIN_TREND_DAYS) return null
        val average = byDay.values.average().roundToInt().toLong()
        val loggedDays = trends.food.map { it.date }.toSet()
        val protein = trends.food.sumOf { it.proteinG } / loggedDays.size
        val headline = context.getString(R.string.share_calories_headline, thousands(average))
        return trendCard(
            context, trends, kind = "calories", title = R.string.share_title_calories,
            mood = DashMood.NUTRITION, accent = Pumpkin.toArgb(), headline = headline,
            chart = ShareChart(
                ChartType.BARS,
                trends.days.mapIndexed { i, day -> ChartPoint(barLabel(trends, i, day), byDay[day]?.toDouble(), Pumpkin.toArgb()) },
                goal = target?.toDouble()
            ),
            stats = listOf(
                ShareStat(thousands(average), context.getString(R.string.share_stat_avg_kcal), Pumpkin.toArgb()),
                ShareStat(context.getString(R.string.share_grams, protein.roundToInt()), context.getString(R.string.share_stat_avg_protein), BlueRibbonLight.toArgb()),
                ShareStat("${loggedDays.size}/${trends.days.size}", context.getString(R.string.share_stat_days_logged), GreenHaze.toArgb())
            )
        )
    }

    /** Protein, carbs and fat as a share of calories over the period. */
    fun macros(context: Context, trends: ShareTrends): ShareCard? {
        val protein = trends.food.sumOf { it.proteinG } * 4
        val carbs = trends.food.sumOf { it.carbsG } * 4
        val fat = trends.food.sumOf { it.fatG } * 9
        val total = protein + carbs + fat
        if (total <= 0 || trends.food.map { it.date }.toSet().size < MIN_TREND_DAYS) return null
        val slices = listOf(
            ChartPoint(context.getString(R.string.share_stat_protein), protein, BlueRibbonLight.toArgb()),
            ChartPoint(context.getString(R.string.share_stat_carbs), carbs, GreenHaze.toArgb()),
            ChartPoint(context.getString(R.string.share_stat_fat), fat, Supernova.toArgb())
        )
        val headline = context.getString(R.string.share_macros_headline)
        return trendCard(
            context, trends, kind = "macros", title = R.string.share_title_macros,
            mood = DashMood.NUTRITION, accent = BlueRibbonLight.toArgb(), headline = headline,
            chart = ShareChart(ChartType.DONUT, slices),
            stats = slices.map { ShareStat("${(it.value!! / total * 100).roundToInt()}%", it.label, it.color) }
        )
    }

    private fun trendCard(
        context: Context,
        trends: ShareTrends,
        kind: String,
        title: Int,
        mood: DashMood,
        @ColorInt accent: Int,
        headline: String,
        chart: ShareChart,
        stats: List<ShareStat>
    ) = ShareCard(
        kind = kind,
        title = context.getString(title),
        dateLabel = context.getString(R.string.share_range, shortDate(trends.start), shortDate(trends.end)),
        mood = mood,
        accent = accent,
        headline = headline,
        chart = chart,
        stats = stats,
        shareText = caption(context, context.getString(R.string.share_caption_period, headline, trends.days.size))
    )

    /** A week labels every bar with its weekday; longer runs label every 7th day with a date. */
    private fun barLabel(trends: ShareTrends, index: Int, day: LocalDate): String = when {
        trends.days.size <= 7 -> day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        (trends.days.size - 1 - index) % 7 == 0 -> shortDate(day)
        else -> ""
    }

    private fun caption(context: Context, lead: String) = context.getString(R.string.share_caption, lead, APP_URL)

    private fun dayLabel(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    private fun shortDate(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    private fun hoursMinutes(context: Context, minutes: Int) = context.getString(R.string.dashboard_hours_minutes, minutes / 60, minutes % 60)
    private fun oneDecimal(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun thousands(value: Long) = String.format(Locale.US, "%,d", value)

    @ColorInt
    private fun RecoveryState.color(): Int = when (this) {
        RecoveryState.PRIMED -> GreenHaze
        RecoveryState.BALANCED -> Supernova
        RecoveryState.REBUILD -> Pumpkin
        RecoveryState.BUILDING_BASELINE -> TextMutedDark
    }.toArgb()

    @ColorInt
    private fun StreakType.color(): Int = when (this) {
        StreakType.STEPS -> BlueRibbonLight
        StreakType.MEALS -> Supernova
        StreakType.SLEEP -> AzureRadiance
        StreakType.SUPPLEMENTS -> GreenHaze
        StreakType.FASTING -> FastingColor
    }.toArgb()

    private fun StreakType.shortLabel(): Int = when (this) {
        StreakType.STEPS -> R.string.dashboard_streak_short_steps
        StreakType.MEALS -> R.string.dashboard_streak_short_meals
        StreakType.SLEEP -> R.string.dashboard_streak_short_sleep
        StreakType.SUPPLEMENTS -> R.string.dashboard_streak_short_supplements
        StreakType.FASTING -> R.string.dashboard_streak_short_fasting
    }
}
