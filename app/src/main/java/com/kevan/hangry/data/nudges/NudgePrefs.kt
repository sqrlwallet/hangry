package com.kevan.hangry.data.nudges

import android.content.Context
import java.time.LocalDate

/** On/off switches for the daily nudges, and when the morning one was last sent. */
object NudgePrefs {
    private const val FILE = "hangry_nudges"
    private const val KEY_MORNING = "morning_brief_enabled"
    private const val KEY_BEDTIME = "bedtime_reminder_enabled"
    private const val KEY_MORNING_SENT = "morning_brief_sent_on"

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
}
