package com.kevan.hangry.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.datasource.DataSourceInfo
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Duration
import java.time.Instant

/**
 * Which app wrote each kind of data lately - so a Fitbit, Pixel Watch or Garmin owner can see
 * their readings are arriving, and spot a data type their device's app isn't sharing.
 */
@Composable
internal fun DataSourcesByApp() {
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    var sources by remember { mutableStateOf<List<DataSourceInfo>?>(null) }
    // Read only when the section is opened: up to 13 Health Connect reads.
    LaunchedEffect(Unit) {
        sources = RealHealthConnectDataSource(context.applicationContext)
            .fetchDataSources(Instant.now().minus(Duration.ofDays(30)))
    }
    val list = sources
    when {
        list == null -> Text(stringResource(R.string.settings_data_sources_loading), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        list.all { it.packages.isEmpty() } -> Text(stringResource(R.string.settings_data_by_app_empty), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        else -> Column {
            list.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        stringResource(source.dataType.label()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(HangryTokens.Spacing.s))
                    Text(
                        if (source.packages.isEmpty()) stringResource(R.string.settings_data_sources_none)
                        else stringResource(
                            R.string.settings_data_sources_updated,
                            source.packages.joinToString(", ") { appName(context, it) },
                            source.latest?.let { relative(it) } ?: "—"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (source.packages.isEmpty()) tokens.textMuted else tokens.textSecondary,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
            Spacer(Modifier.height(HangryTokens.Spacing.s))
            Text(stringResource(R.string.settings_data_sources_hint), style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
        }
    }
}

private fun DataSourceInfo.DataType.label(): Int = when (this) {
    DataSourceInfo.DataType.SLEEP -> R.string.settings_data_type_sleep
    DataSourceInfo.DataType.STEPS -> R.string.settings_data_type_steps
    DataSourceInfo.DataType.HEART_RATE -> R.string.settings_data_type_heart_rate
    DataSourceInfo.DataType.RESTING_HEART_RATE -> R.string.settings_data_type_resting_hr
    DataSourceInfo.DataType.HRV -> R.string.settings_data_type_hrv
    DataSourceInfo.DataType.SPO2 -> R.string.settings_data_type_spo2
    DataSourceInfo.DataType.BREATHING -> R.string.settings_data_type_breathing
    DataSourceInfo.DataType.SKIN_TEMP -> R.string.settings_data_type_skin_temp
    DataSourceInfo.DataType.VO2_MAX -> R.string.settings_data_type_vo2
    DataSourceInfo.DataType.WORKOUTS -> R.string.settings_data_type_workouts
    DataSourceInfo.DataType.CALORIES -> R.string.settings_data_type_calories
    DataSourceInfo.DataType.WEIGHT -> R.string.settings_data_type_weight
    DataSourceInfo.DataType.NUTRITION -> R.string.settings_data_type_nutrition
}

/** Well-known health apps by package; others by their installed label, else the package name. */
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

private fun appName(context: Context, pkg: String): String = KNOWN_APPS[pkg] ?: try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    pkg
}

private fun relative(time: Instant): String =
    DateUtils.getRelativeTimeSpanString(time.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
