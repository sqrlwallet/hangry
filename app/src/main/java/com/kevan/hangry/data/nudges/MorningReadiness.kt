package com.kevan.hangry.data.nudges

import android.content.Context
import androidx.core.app.NotificationCompat
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.NudgeText
import com.kevan.hangry.domain.model.RecoveryState
import com.kevan.hangry.ui.coach.DashMood
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The morning readiness notification: once a day, as soon as last night's sleep and a real
 * recovery score are in, tell the user how recovered they are and how hard to push.
 */
object MorningReadiness {

    /** Only mornings - a "good morning" brief at 9pm would be odd. */
    val WINDOW_START: LocalTime = LocalTime.of(5, 0)
    val WINDOW_END: LocalTime = LocalTime.of(12, 0)

    fun inWindow(now: LocalTime = LocalTime.now()) = !now.isBefore(WINDOW_START) && now.isBefore(WINDOW_END)

    fun alreadySentToday(context: Context, today: LocalDate = LocalDate.now()) = NudgePrefs.morningSentOn(context) == today

    /** Sends today's brief if it's due and there's a real score to report. Returns true if sent. */
    suspend fun maybeNotify(context: Context, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = LocalDate.now(zone)
        if (!NudgePrefs.morningEnabled(context) || !NudgeNotifications.canNotify(context)) return false
        if (!inWindow(LocalTime.now(zone)) || alreadySentToday(context, today)) return false

        val container = (context.applicationContext as? HangryApplication)?.container ?: return false
        val summary = container.dailySummaryRepository.getSummaryForDateSync(today) ?: return false
        val recovery = container.dailySummaryRepository.getRecoveryScoreForDateSync(today) ?: return false
        // No sleep yet, or no score (not enough data): wait - never send a made-up brief.
        val score = recovery.score ?: return false
        if ((summary.sleepDurationMinutes ?: 0) <= 0) return false
        val state = runCatching { RecoveryState.valueOf(recovery.state) }.getOrNull() ?: return false

        val recentStrain = container.dailySummaryRepository
            .getSummariesBetween(today.minusDays(7), today.minusDays(1))
            .firstOrNull()
            .orEmpty()
            .mapNotNull { it.dayStrain }
        val target = recentStrain.takeIf { it.isNotEmpty() }?.let { container.strainCalculator.recommendStrainTarget(state, it) }
        val message = NudgeText.morning(score, state, summary.sleepDurationMinutes, target?.targetLow, target?.targetHigh)

        val mood = when (state) {
            RecoveryState.PRIMED -> DashMood.HAPPY
            RecoveryState.BALANCED -> DashMood.CHEER
            RecoveryState.REBUILD -> DashMood.CONCERNED
            RecoveryState.BUILDING_BASELINE -> DashMood.THINKING
        }
        NudgeNotifications.notify(
            context, NudgeNotifications.MORNING_ID,
            NotificationCompat.Builder(context, NudgeNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_morning)
                .setLargeIcon(NudgeNotifications.dashPicture(context, mood.imageRes))
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setAutoCancel(true)
                .setContentIntent(NudgeNotifications.openApp(context, NudgeNotifications.MORNING_ID))
        )
        NudgePrefs.markMorningSent(context, today)
        return true
    }
}
