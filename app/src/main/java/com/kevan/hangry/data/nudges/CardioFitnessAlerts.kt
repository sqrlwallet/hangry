package com.kevan.hangry.data.nudges

import android.content.Context
import androidx.core.app.NotificationCompat
import com.kevan.hangry.HangryApplication
import com.kevan.hangry.R
import com.kevan.hangry.data.repository.CardioFitnessLoader
import com.kevan.hangry.domain.calculation.CardioFitness
import com.kevan.hangry.domain.model.NudgeText
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.navigation.Screen
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Like Apple's low cardio fitness notification: once a day, in the daytime, check whether VO2 max
 * has stayed low for your age and sex, or fallen by a meaningful amount, and say so - at most once
 * per kind every few months (see [CardioFitness.alertDue]).
 */
object CardioFitnessAlerts {

    /** Daytime only - this can wait, it's not worth waking anyone for. */
    val WINDOW_START: LocalTime = LocalTime.of(10, 0)
    val WINDOW_END: LocalTime = LocalTime.of(20, 0)

    fun inWindow(now: LocalTime = LocalTime.now()) = !now.isBefore(WINDOW_START) && now.isBefore(WINDOW_END)

    /** Checks once a day and sends an alert if one is due. Returns true if sent. */
    suspend fun maybeNotify(context: Context, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = LocalDate.now(zone)
        if (!NudgePrefs.cardioAlertsEnabled(context) || !NudgeNotifications.canNotify(context)) return false
        if (!inWindow(LocalTime.now(zone)) || NudgePrefs.cardioCheckedOn(context) == today) return false
        val container = (context.applicationContext as? HangryApplication)?.container ?: return false
        NudgePrefs.markCardioChecked(context, today)

        val loader = CardioFitnessLoader(container.database)
        val person = loader.person(today)
        val readings = loader.vo2Readings(today)
        val alert = CardioFitness.alertDue(readings, today, person.age, person.sex, NudgePrefs.cardioAlertsSent(context)) ?: return false
        val vo2 = CardioFitness.valueOn(readings, today) ?: return false
        val message = when (alert) {
            CardioFitness.Alert.LOW -> NudgeText.cardioLow(vo2, person.age?.let { CardioFitness.ranges(it, person.sex).lowBelow })
            CardioFitness.Alert.DECLINING -> NudgeText.cardioDeclining(vo2, -(CardioFitness.change(readings, today) ?: 0.0))
        }

        NudgeNotifications.notify(
            context, NudgeNotifications.CARDIO_FITNESS_ID,
            NotificationCompat.Builder(context, NudgeNotifications.FITNESS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_heart)
                .setLargeIcon(NudgeNotifications.dashPicture(context, DashMood.CONCERNED.imageRes))
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setAutoCancel(true)
                .setContentIntent(NudgeNotifications.openApp(context, NudgeNotifications.CARDIO_FITNESS_ID, Screen.HeartMetrics.route))
        )
        NudgePrefs.markCardioAlertSent(context, alert, today)
        return true
    }
}
