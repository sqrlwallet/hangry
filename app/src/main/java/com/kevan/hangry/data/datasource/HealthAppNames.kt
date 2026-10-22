package com.kevan.hangry.data.datasource

import android.content.Context
import android.content.pm.PackageManager

/**
 * Friendlier names for apps whose label isn't what people call them (the Fitbit app is now
 * Google Health); any other app shows its installed label - visible through the manifest's
 * Health Connect <queries> - else its package name.
 */
private val KNOWN_APPS = mapOf(
    "com.fitbit.FitbitMobile" to "Fitbit / Google Health",
    "com.google.android.apps.fitness" to "Google Fit",
    "com.sec.android.app.shealth" to "Samsung Health",
    "com.garmin.android.apps.connectmobile" to "Garmin Connect",
    "com.ouraring.oura" to "Oura",
    "com.whoop.android" to "WHOOP",
    "com.xiaomi.wearable" to "Mi Fitness",
    "com.huami.watch.hmwatchmanager" to "Zepp",
    "com.myfitnesspal.android" to "MyFitnessPal",
    "com.withings.wiscale2" to "Withings",
    "com.strava" to "Strava",
    "fi.polar.polarflow" to "Polar Flow"
)

internal fun healthAppName(context: Context, pkg: String): String = KNOWN_APPS[pkg] ?: try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    pkg
}
