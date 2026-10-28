package com.kevan.hangry.data.prefs

import android.content.Context
import com.kevan.hangry.domain.model.RestModeState

/** Rest periods, kept with the other settings so backups carry them. */
class RestModePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hangry_rest_mode", Context.MODE_PRIVATE)

    fun read(): RestModeState = RestModeState.decode(prefs.getString(KEY_PERIODS, null))

    fun write(state: RestModeState) = prefs.edit().putString(KEY_PERIODS, state.encode()).apply()

    private companion object {
        const val KEY_PERIODS = "periods"
    }
}
