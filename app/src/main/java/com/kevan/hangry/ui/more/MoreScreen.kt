package com.kevan.hangry.ui.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessibilityNew
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
    onOpenBreathing: () -> Unit,
    onOpenWidgets: () -> Unit,
    onCustomizeToday: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = listOf(
        "Progress" to listOf(
            MoreItem(Icons.Default.Cake, "Body Age", "How old your body acts, from your habits and fitness", onOpenBodyAge),
            MoreItem(Icons.AutoMirrored.Filled.TrendingUp, "Trends", "Recovery, sleep, weight and more over time", onOpenTrends),
            MoreItem(Icons.Default.AccessibilityNew, "Posture", "Posture checks and your timeline", onOpenPosture),
            MoreItem(Icons.Default.PieChart, "Body Fat & Composition", "From photos or tape measurements", onOpenBodyFat),
            MoreItem(Icons.Default.MonitorWeight, "Body Metrics", "BMI, FFMI, maintenance calories and more", onOpenBodyMetrics)
        ),
        "Health" to listOf(
            MoreItem(Icons.Default.MonitorHeart, "Health Records", "Blood pressure, labs, goals and cycle", onOpenHealthRecords),
            MoreItem(Icons.Default.Medication, "Supplements", "Doses, reminders and checks", onOpenSupplements),
            MoreItem(Icons.Default.Air, "Breathing", "Guided breathing with Dash", onOpenBreathing)
        ),
        "App" to listOf(
            MoreItem(Icons.Default.DashboardCustomize, "Customize Today", "Choose and reorder the cards on Today", onCustomizeToday),
            MoreItem(Icons.Default.Widgets, "Home Screen Widgets", "Pin widgets to your home screen", onOpenWidgets),
            MoreItem(Icons.Default.Settings, "Settings & Privacy", "Goals, sync, AI and your data", onOpenSettings)
        )
    )
    val tokens = LocalHangryTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More") },
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
