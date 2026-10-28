package com.kevan.hangry.data.nudges

import android.content.Context
import com.kevan.hangry.domain.calculation.CardioFitness
import java.time.LocalDate

/** On/off switches for the daily nudges and fitness alerts, and when each was last sent. */
object NudgePrefs {
    private const val FILE = "hangry_nudges"
    private const val KEY_MORNING = "morning_brief_enabled"
    private const val KEY_BEDTIME = "bedtime_reminder_enabled"
    private const val KEY_MORNING_SENT = "morning_brief_sent_on"
    private const val KEY_CARDIO = "cardio_fitness_alerts_enabled"
    private const val KEY_CARDIO_CHECKED = "cardio_fitness_checked_on"
    private const val KEY_CARDIO_SENT_PREFIX = "cardio_fitness_sent_"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun morningEnabled(context: Context) = prefs(context).getBoolean(KEY_MORNING, true)
    fun setMorningEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_MORNING, on).apply()

    fun bedtimeEnabled(context: Context) = prefs(context).getBoolean(KEY_BEDTIME, true)
    fun setBedtimeEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_BEDTIME, on).apply()
        BedtimeReminder.reschedule(context)
    }

    fun morningSentOn(context: Context): LocalDate? =
        prefs(context).getString(KEY_MORNING_SENT, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun markMorningSent(context: Context, date: LocalDate) =
        prefs(context).edit().putString(KEY_MORNING_SENT, date.toString()).apply()

    fun cardioAlertsEnabled(context: Context) = prefs(context).getBoolean(KEY_CARDIO, true)
    fun setCardioAlertsEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_CARDIO, on).apply()

    fun cardioCheckedOn(context: Context): LocalDate? = date(context, KEY_CARDIO_CHECKED)
    fun markCardioChecked(context: Context, date: LocalDate) = prefs(context).edit().putString(KEY_CARDIO_CHECKED, date.toString()).apply()

    /** When each kind of cardio fitness alert was last sent. */
    fun cardioAlertsSent(context: Context): Map<CardioFitness.Alert, LocalDate> =
        CardioFitness.Alert.entries.mapNotNull { a -> date(context, KEY_CARDIO_SENT_PREFIX + a.name)?.let { a to it } }.toMap()

    fun markCardioAlertSent(context: Context, alert: CardioFitness.Alert, date: LocalDate) =
        prefs(context).edit().putString(KEY_CARDIO_SENT_PREFIX + alert.name, date.toString()).apply()

    private fun date(context: Context, key: String): LocalDate? =
        prefs(context).getString(key, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
