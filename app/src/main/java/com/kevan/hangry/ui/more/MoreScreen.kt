package com.kevan.hangry.ui.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.settings.SettingsActionRow
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

/** One destination on the More tab. */
data class MoreItem(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

/** Everything that isn't a tab of its own: progress tracking, health tools and app settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenBodyAge: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenPosture: () -> Unit,
    onOpenBodyFat: () -> Unit,
    onOpenBodyMetrics: () -> Unit,
    onOpenHealthRecords: () -> Unit,
    onOpenSupplements: () -> Unit,
    onOpenFasting: () -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenBreathing: () -> Unit,
    onOpenWidgets: () -> Unit,
    onCustomizeToday: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        stringResource(R.string.settings_more_section_progress) to listOf(
            MoreItem(Icons.Default.Cake, stringResource(R.string.settings_more_body_age), stringResource(R.string.settings_more_body_age_subtitle), onOpenBodyAge),
            MoreItem(Icons.AutoMirrored.Filled.TrendingUp, stringResource(R.string.settings_more_trends), stringResource(R.string.settings_more_trends_subtitle), onOpenTrends),
            MoreItem(Icons.Default.AccessibilityNew, stringResource(R.string.settings_more_posture), stringResource(R.string.settings_more_posture_subtitle), onOpenPosture),
            MoreItem(Icons.Default.PieChart, stringResource(R.string.settings_more_body_fat), stringResource(R.string.settings_more_body_fat_subtitle), onOpenBodyFat),
            MoreItem(Icons.Default.MonitorWeight, stringResource(R.string.settings_more_body_metrics), stringResource(R.string.settings_more_body_metrics_subtitle), onOpenBodyMetrics)
        ),
        stringResource(R.string.settings_more_section_health) to listOf(
            MoreItem(Icons.Default.MonitorHeart, stringResource(R.string.settings_more_health_records), stringResource(R.string.settings_more_health_records_subtitle), onOpenHealthRecords),
            MoreItem(Icons.Default.Medication, stringResource(R.string.settings_more_supplements), stringResource(R.string.settings_more_supplements_subtitle), onOpenSupplements),
            MoreItem(Icons.Default.Timer, stringResource(R.string.settings_more_fasting), stringResource(R.string.settings_more_fasting_subtitle), onOpenFasting),
            MoreItem(Icons.Default.DirectionsWalk, stringResource(R.string.settings_more_programs), stringResource(R.string.settings_more_programs_subtitle), onOpenPrograms),
            MoreItem(Icons.Default.Air, stringResource(R.string.settings_more_breathing), stringResource(R.string.settings_more_breathing_subtitle), onOpenBreathing)
        ),
        stringResource(R.string.settings_more_section_app) to listOf(
            MoreItem(Icons.Default.DashboardCustomize, stringResource(R.string.settings_more_customize_today), stringResource(R.string.settings_more_customize_today_subtitle), onCustomizeToday),
            MoreItem(Icons.Default.Widgets, stringResource(R.string.settings_more_widgets), stringResource(R.string.settings_more_widgets_subtitle), onOpenWidgets),
            MoreItem(Icons.Default.Settings, stringResource(R.string.settings_title), stringResource(R.string.settings_more_settings_subtitle), onOpenSettings)
        )
    )
    val tokens = LocalHangryTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_more_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            sections.forEach { (heading, items) ->
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(start = 4.dp, top = HangryTokens.Spacing.s)
                )
                HangryCard {
                    items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = tokens.cardBorder)
                        SettingsActionRow(icon = item.icon, title = item.title, subtitle = item.subtitle, onClick = item.onClick)
                    }
                }
            }
            // Clear of the floating tab bar.
            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m + LocalDockInset.current))
        }
    }
}
