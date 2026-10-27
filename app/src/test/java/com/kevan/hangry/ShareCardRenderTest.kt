package com.kevan.hangry

import android.content.Context
import android.graphics.Bitmap
import com.kevan.hangry.data.local.entity.DailyHealthSummaryEntity
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.data.local.entity.RecoveryScoreEntity
import com.kevan.hangry.data.repository.BodyAgeSnapshot
import com.kevan.hangry.domain.calculation.BodyAgeResult
import com.kevan.hangry.domain.calculation.Streak
import com.kevan.hangry.domain.calculation.StreakType
import com.kevan.hangry.ui.dashboard.DashboardUiState
import com.kevan.hangry.ui.share.ChartType
import com.kevan.hangry.ui.share.ShareCardRenderer
import com.kevan.hangry.ui.share.ShareCards
import com.kevan.hangry.ui.share.ShareTrends
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.Random

/**
 * Builds every share card from realistic data and paints it with real Android graphics. Catches
 * layout crashes, and leaves PNGs in `app/build/share-card-preview/` to look at when changing
 * the design.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShareCardRenderTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val today = LocalDate.now()

    @Test
    fun rendersEveryCardForAWeek() {
        val cards = ShareCards.all(context, dashboard(), trends(7))
        assertEquals(
            listOf("today", "streaks", "body-age", "recovery", "sleep", "steps", "strain", "calories", "macros"),
            cards.map { it.kind }
        )
        cards.forEach { render(it.kind + "-7", it) }
    }

    @Test
    fun rendersTrendsForAMonth() {
        val cards = ShareCards.all(context, dashboard(), trends(30)).filter { it.chart != null }
        assertEquals(6, cards.size)
        assertTrue(cards.all { it.chart!!.type == ChartType.DONUT || it.chart.points.size == 30 })
        cards.forEach { render(it.kind + "-30", it) }
    }

    @Test
    fun aNewUserStillGetsATodayCard() {
        val cards = ShareCards.all(context, DashboardUiState(isLoading = false), ShareTrends(today.minusDays(6), today))
        assertEquals(listOf("today"), cards.map { it.kind })
        assertTrue(cards.single().stats.isEmpty())
        render("today-empty", cards.single())
    }

    @Test
    fun pendingSleepHidesTheRecoveryScore() {
        val card = ShareCards.today(context, dashboard().copy(isPendingSleepData = true))
        assertTrue(card.stats.none { it.label == context.getString(R.string.share_stat_recovery) })
        assertEquals(context.getString(R.string.share_today_headline), card.headline)
    }

    @Test
    fun oneDayIsNotATrend() {
        val trends = ShareTrends(today.minusDays(6), today, summaries = listOf(DailyHealthSummaryEntity(date = today, steps = 9000, sleepDurationMinutes = 420)))
        assertNull(ShareCards.stepsTrend(context, trends, 8000))
        assertNull(ShareCards.sleepTrend(context, trends, 480))
    }

    @Test
    fun captionsLinkToTheApp() {
        ShareCards.all(context, dashboard(), trends(7)).forEach {
            assertTrue(it.shareText, it.shareText.endsWith(ShareCards.APP_URL))
        }
    }

    @Test
    fun macroSplitAddsUpToAboutAHundred() {
        val macros = ShareCards.macros(context, trends(7))!!
        val total = macros.stats.sumOf { it.value.removeSuffix("%").toInt() }
        assertTrue("got $total", total in 99..101)
    }

    @Test
    fun longHeadlinesAndAllFourStatsStillFit() {
        val card = ShareCards.today(context, dashboard()).copy(
            headline = "A really very long headline that has to wrap onto a second line and then some more",
            message = "And a message under it that is also fairly long so it wraps"
        )
        render("today-long", card)
    }

    private fun render(name: String, card: com.kevan.hangry.ui.share.ShareCard) {
        val dir = File("build/share-card-preview").apply { mkdirs() }
        val bitmap = ShareCardRenderer(context).render(card)
        assertEquals(ShareCardRenderer.WIDTH, bitmap.width)
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun dashboard() = DashboardUiState(
        isLoading = false,
        selectedDate = today,
        dailySummary = DailyHealthSummaryEntity(date = today, sleepDurationMinutes = 462, steps = 9812, dayStrain = 12.4),
        recoveryScore = RecoveryScoreEntity(date = today, score = 78, confidence = "HIGH", state = "PRIMED", supportiveAdvice = ""),
        isPendingSleepData = false,
        todayActiveCalories = 512.0,
        todayActiveMinutes = 74,
        streaks = listOf(
            Streak(StreakType.STEPS, 30, true, today.minusDays(29), 45),
            Streak(StreakType.SLEEP, 6, false, today.minusDays(6), 12),
            Streak(StreakType.MEALS, 14, true, today.minusDays(13), 14),
            Streak(StreakType.FASTING, 0, false, null, 3)
        ),
        bodyAge = BodyAgeSnapshot(
            current = BodyAgeResult(chronologicalAge = 34.6, bodyAge = 30.4, factors = emptyList(), missing = emptyList()),
            previous = BodyAgeResult(chronologicalAge = 34.5, bodyAge = 31.1, factors = emptyList(), missing = emptyList()),
            needs = emptyList()
        )
    )

    private fun trends(days: Int): ShareTrends {
        val random = Random(days.toLong())
        val start = today.minusDays(days - 1L)
        val dates = generateSequence(start) { it.plusDays(1) }.take(days).toList()
        // A missed day here and there, like real data.
        val logged = dates.filterIndexed { i, _ -> i % 5 != 3 }
        return ShareTrends(
            start = start,
            end = today,
            summaries = logged.map {
                DailyHealthSummaryEntity(
                    date = it,
                    sleepDurationMinutes = 380 + random.nextInt(140),
                    steps = 4000L + random.nextInt(9000),
                    dayStrain = 6 + random.nextDouble() * 10,
                    exerciseCount = random.nextInt(2)
                )
            },
            scores = logged.map {
                val score = 30 + random.nextInt(65)
                RecoveryScoreEntity(
                    date = it, score = score, confidence = "HIGH", supportiveAdvice = "",
                    state = when { score >= 70 -> "PRIMED"; score >= 40 -> "BALANCED"; else -> "REBUILD" }
                )
            },
            food = logged.flatMap { day ->
                (1..3).map { FoodLogEntity(date = day, source = "MANUAL", foodName = "Meal $it", calories = 450 + random.nextInt(400), proteinG = 30.0 + random.nextInt(20), carbsG = 50.0 + random.nextInt(40), fatG = 15.0 + random.nextInt(15)) }
            }
        )
    }
}
