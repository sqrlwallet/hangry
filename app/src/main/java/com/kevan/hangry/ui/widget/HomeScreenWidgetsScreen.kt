package com.kevan.hangry.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWidgetsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = {
                    Text(
                        text = stringResource(R.string.dashboard_widget_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dashboard_widget_back),
                            tint = tokens.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            // Header Intro
            HangryCard {
                Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.dashboard_widget_intro_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        HangryInfoTip(
                            title = stringResource(R.string.dashboard_widget_screen_title),
                            body = stringResource(R.string.dashboard_widget_intro_body)
                        )
                    }
                }
            }

            WidgetSection(stringResource(R.string.dashboard_widget_section_food))

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_quick_log_title),
                sizeLabel = "2 × 1",
                description = stringResource(R.string.dashboard_widget_quick_log_desc),
                onPinWidget = { pinWidget(context, QuickLogWidgetProvider::class.java) }
            ) {
                QuickLogWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_calories_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_calories_desc),
                onPinWidget = { pinWidget(context, CaloriesWidgetProvider::class.java) }
            ) {
                CaloriesWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_nutrition_title),
                sizeLabel = "4 × 2",
                description = stringResource(R.string.dashboard_widget_nutrition_desc),
                onPinWidget = { pinWidget(context, NutritionWidgetProvider::class.java) }
            ) {
                NutritionWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_water_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_water_desc),
                onPinWidget = { pinWidget(context, WaterWidgetProvider::class.java) }
            ) {
                WaterWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_fasting_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_fasting_desc),
                onPinWidget = { pinWidget(context, FastingWidgetProvider::class.java) }
            ) {
                FastingWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_supplements_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_supplements_desc),
                onPinWidget = { pinWidget(context, SupplementsWidgetProvider::class.java) }
            ) {
                SupplementsWidgetMockup()
            }

            WidgetSection(stringResource(R.string.dashboard_widget_section_activity))

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_activity_title),
                sizeLabel = "3 × 2",
                description = stringResource(R.string.dashboard_widget_activity_desc),
                onPinWidget = { pinWidget(context, ActivityWidgetProvider::class.java) }
            ) {
                ActivityWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_strain_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_strain_desc),
                onPinWidget = { pinWidget(context, StrainWidgetProvider::class.java) }
            ) {
                StrainWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_weight_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_weight_desc),
                onPinWidget = { pinWidget(context, WeightWidgetProvider::class.java) }
            ) {
                WeightWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_posture_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_posture_desc),
                onPinWidget = { pinWidget(context, PostureWidgetProvider::class.java) }
            ) {
                PostureWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_breathe_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_breathe_desc),
                onPinWidget = { pinWidget(context, BreatheWidgetProvider::class.java) }
            ) {
                BreatheWidgetMockup()
            }

            WidgetSection(stringResource(R.string.dashboard_widget_section_sleep))

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_sleep_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_sleep_desc),
                onPinWidget = { pinWidget(context, SleepWidgetProvider::class.java) }
            ) {
                SleepWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_bedtime_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_bedtime_desc),
                onPinWidget = { pinWidget(context, BedtimeWidgetProvider::class.java) }
            ) {
                BedtimeWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_recovery_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_recovery_desc),
                onPinWidget = { pinWidget(context, RecoveryWidgetProvider::class.java) }
            ) {
                RecoveryWidgetMockup()
            }

            WidgetSection(stringResource(R.string.dashboard_widget_section_health))

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_heart_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_heart_desc),
                onPinWidget = { pinWidget(context, HeartWidgetProvider::class.java) }
            ) {
                HeartWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_markers_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_markers_desc),
                onPinWidget = { pinWidget(context, HealthMarkersWidgetProvider::class.java) }
            ) {
                MarkersWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_goals_title),
                sizeLabel = "4 × 2",
                description = stringResource(R.string.dashboard_widget_goals_desc),
                onPinWidget = { pinWidget(context, GoalsWidgetProvider::class.java) }
            ) {
                GoalsWidgetMockup()
            }

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_cycle_title),
                sizeLabel = "2 × 2",
                description = stringResource(R.string.dashboard_widget_cycle_desc),
                onPinWidget = { pinWidget(context, CycleWidgetProvider::class.java) }
            ) {
                CycleWidgetMockup()
            }

            WidgetSection(stringResource(R.string.dashboard_widget_section_all_in_one))

            WidgetPreviewCard(
                title = stringResource(R.string.dashboard_widget_overview_title),
                sizeLabel = "4 × 2",
                description = stringResource(R.string.dashboard_widget_overview_desc),
                onPinWidget = { pinWidget(context, OverviewWidgetProvider::class.java) }
            ) {
                OverviewWidgetMockup()
            }

            HideValuesCard()

            // How to add manually guidance
            HangryCard {
                Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.dashboard_widget_add_from_home),
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        HangryInfoTip(
                            title = stringResource(R.string.dashboard_widget_how_to_add_title),
                            body = stringResource(R.string.dashboard_widget_how_to_add_body)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LocalHangryTokens.current.textSecondary,
        modifier = Modifier.padding(top = HangryTokens.Spacing.s)
    )
}

private fun pinWidget(context: Context, providerClass: Class<*>) {
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
        val provider = ComponentName(context, providerClass)
        appWidgetManager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.dashboard_widget_pin_fallback),
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun WidgetPreviewCard(
    title: String,
    sizeLabel: String,
    description: String,
    onPinWidget: () -> Unit,
    content: @Composable () -> Unit
) {
    val tokens = LocalHangryTokens.current
    HangryCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HangryTokens.Spacing.m)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = tokens.cardBorder,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = sizeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    HangryInfoTip(title = title, body = description)
                }

                Button(
                    onClick = onPinWidget,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.dashboard_widget_pin_to_home), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Mockup Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(mock.surface)
                    .border(1.dp, mock.outline, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ActivityWidgetMockup() {
    Column {
        MockHeader(Icons.Default.LocalFireDepartment, Color(0xFFFF7E1D), stringResource(R.string.dashboard_widget_mock_daily_activity), "80%", Color(0xFF6AA8FF))
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricColumn(icon = Icons.AutoMirrored.Filled.DirectionsWalk, tint = Color(0xFF6AA8FF), value = "6,420", label = stringResource(R.string.dashboard_widget_mock_steps_of_goal))
            MetricColumn(icon = Icons.Default.LocalFireDepartment, tint = Color(0xFFFF7E1D), value = "450", label = stringResource(R.string.dashboard_widget_mock_active_kcal))
            MetricColumn(icon = Icons.Default.Timer, tint = Color(0xFF01A652), value = "52", label = stringResource(R.string.dashboard_widget_mock_active_min))
        }
        Spacer(modifier = Modifier.height(8.dp))
        MockBar(0.8f, Color(0xFF6AA8FF))
    }
}

@Composable
private fun QuickLogWidgetMockup() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C6FF9))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhotoCamera, null, tint = Color(0xFFF9F4F2), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.dashboard_widget_mock_log_meal), color = Color(0xFFF9F4F2), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SleepWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, null, tint = Color(0xFF00A4FF), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_widget_mock_sleep), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
            }
            Surface(
                color = mock.track,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.dashboard_widget_mock_sleep_score), color = Color(0xFF00A4FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("82/100", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_sleep_subtitle), color = mock.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SupplementsWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Medication, null, tint = Color(0xFFF7931E), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_widget_mock_supplements), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
            }
            Surface(color = mock.track, shape = RoundedCornerShape(10.dp)) {
                Text("2/4", color = Color(0xFFF7931E), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.dashboard_widget_mock_supplement_name), color = mock.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_supplement_next), color = mock.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FastingWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = Color(0xFFB39DDB), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_widget_mock_fasting_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
            }
            Surface(color = mock.track, shape = RoundedCornerShape(10.dp)) {
                Text("🔥 5", color = Color(0xFFB39DDB), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("13:42:08", color = mock.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_fasting_left), color = mock.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(mock.track)) {
            Box(Modifier.fillMaxWidth(0.86f).fillMaxHeight().background(Color(0xFFB39DDB)))
        }
    }
}

@Composable
private fun MockRing(fraction: Float, size: androidx.compose.ui.unit.Dp, value: String, valueStyle: androidx.compose.ui.text.TextStyle) {
    val track = mock.track
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color(0xFFFF7E1D), -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = mock.text, style = valueStyle, fontWeight = FontWeight.Bold)
            Text("kcal", color = mock.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CaloriesWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF7E1D), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_widget_mock_eaten_today), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
            }
            Surface(color = mock.track, shape = RoundedCornerShape(10.dp)) {
                Text("62%", color = Color(0xFFFF7E1D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MockRing(0.62f, 84.dp, "1,240", MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.dashboard_widget_mock_calories_left),
            color = mock.muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun NutritionWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, null, tint = Color(0xFFFF7E1D), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_widget_mock_nutrition_today), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
            }
            Surface(color = mock.track, shape = RoundedCornerShape(10.dp)) {
                Text("62%", color = Color(0xFFFF7E1D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MockRing(0.62f, 72.dp, "1,240", MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.dashboard_widget_mock_calories_left), color = mock.text, style = MaterialTheme.typography.bodySmall)
                listOf(
                    Triple(stringResource(R.string.dashboard_widget_mock_protein), "82 / 125 g", 0.66f) to Color(0xFF6AA8FF),
                    Triple(stringResource(R.string.dashboard_widget_mock_carbs), "140 / 250 g", 0.56f) to Color(0xFF01A652),
                    Triple(stringResource(R.string.dashboard_widget_mock_fat), "40 / 56 g", 0.71f) to Color(0xFFFFCE00)
                ).forEach { (row, color) ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.first, color = mock.muted, style = MaterialTheme.typography.labelSmall)
                            Text(row.second, color = mock.text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { row.third },
                            color = color,
                            trackColor = mock.track,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(stringResource(R.string.dashboard_widget_mock_quick_oats), stringResource(R.string.dashboard_widget_mock_quick_banana), stringResource(R.string.dashboard_widget_mock_quick_latte)).forEach { label ->
                Surface(color = mock.track, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        color = mock.text,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Surface(color = Color(0xFF0C6FF9), shape = RoundedCornerShape(15.dp), modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.PhotoCamera, null, tint = Color(0xFFF9F4F2), modifier = Modifier.padding(6.dp))
            }
        }
    }
}

@Composable
private fun RecoveryWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFF01A652), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_widget_mock_recovery), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
            }
            Surface(
                color = mock.track,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.dashboard_widget_mock_primed), color = Color(0xFF01A652), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("82%", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_recovery_advice), color = mock.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OverviewWidgetMockup() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("HANGRY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.text)
            Surface(
                color = Color(0xFF0C6FF9),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoCamera, null, tint = Color(0xFFF9F4F2), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.dashboard_widget_mock_log_meal), color = Color(0xFFF9F4F2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_widget_mock_recovery), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
                Text("82%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = mock.text)
                Text(stringResource(R.string.dashboard_widget_mock_primed), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF01A652))
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(mock.outline)
            )

            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(start = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.dashboard_widget_mock_overview_steps), color = Color(0xFF6AA8FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("450 kcal", color = Color(0xFFFF7E1D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_widget_mock_overview_sleep), color = Color(0xFF00A4FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = mock.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, color = mock.muted, style = MaterialTheme.typography.labelSmall)
    }
}

// region New health widgets

@Composable
private fun HideValuesCard() {
    val tokens = LocalHangryTokens.current
    val context = LocalContext.current
    var hide by remember { mutableStateOf(WidgetPrefs.hideValues(context)) }
    HangryCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HangryTokens.Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_widget_hide_values_title), style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    stringResource(R.string.dashboard_widget_hide_values_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(HangryTokens.Spacing.s))
            Switch(
                checked = hide,
                onCheckedChange = {
                    hide = it
                    WidgetPrefs.setHideValues(context, it)
                }
            )
        }
    }
}

/** Neutral colours of the previews. Widgets follow the phone's dark mode, not the in-app theme, so the previews do too. */
private class MockPalette(val surface: Color, val outline: Color, val track: Color, val text: Color, val muted: Color, val faint: Color)

private val DarkMock = MockPalette(Color(0xFF252329), Color(0xFF3A363F), Color(0xFF2F2C33), Color(0xFFF9F4F2), Color(0xFFB5AEB8), Color(0xFF948D98))
private val LightMock = MockPalette(Color(0xFFFFFDFC), Color(0xFFE7DFDA), Color(0xFFF2ECE8), Color(0xFF413D45), Color(0xFF6E6873), Color(0xFF7D7681))

private val mock: MockPalette
    @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) DarkMock else LightMock

/** Text on the blue buttons, the same in both modes. */
private val MockOnBlue = Color(0xFFF9F4F2)

@Composable
private fun MockHeader(icon: ImageVector, tint: Color, label: String, chip: String? = null, chipColor: Color = tint) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = mock.muted)
        }
        if (chip != null) {
            Surface(color = mock.track, shape = RoundedCornerShape(10.dp)) {
                Text(chip, color = chipColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun MockButton(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C6FF9))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MockOnBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BreatheWidgetMockup() {
    Column {
        MockHeader(Icons.Default.Air, Color(0xFF6FC3DF), stringResource(R.string.dashboard_widget_mock_breathe), "45 min / 7d")
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.dash_breathe_in),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("12 min", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.dashboard_widget_mock_breathe_today), color = mock.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MockButton(stringResource(R.string.dashboard_widget_mock_breathe_box), Modifier.weight(1f))
            MockButton("5 BPM", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MockValueRow(label: String, value: String, delta: String? = null, deltaColor: Color = mock.muted) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, color = mock.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = mock.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (delta != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(delta, color = deltaColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeartWidgetMockup() {
    Column {
        MockHeader(Icons.Default.MonitorHeart, Color(0xFFFF6B6B), stringResource(R.string.dashboard_widget_mock_heart))
        Spacer(modifier = Modifier.height(8.dp))
        MockValueRow(stringResource(R.string.resting_hr_label), "58 bpm", "▼ 3", Color(0xFF01A652))
        Spacer(modifier = Modifier.height(4.dp))
        MockValueRow("HRV", "48 ms", "▲ 5", Color(0xFF01A652))
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.dashboard_widget_mock_heart_vs_normal), color = mock.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MarkersWidgetMockup() {
    Column {
        MockHeader(Icons.Default.WaterDrop, Color(0xFFE57373), stringResource(R.string.dashboard_widget_mock_markers), "+", mock.text)
        Spacer(modifier = Modifier.height(8.dp))
        MockHeader(Icons.Default.Favorite, Color.Transparent, stringResource(R.string.dashboard_widget_mock_blood_pressure), stringResource(R.string.dashboard_widget_mock_normal), Color(0xFF01A652))
        Text("118/76 mmHg", color = mock.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        MockHeader(Icons.Default.Favorite, Color.Transparent, stringResource(R.string.dashboard_widget_mock_blood_sugar), stringResource(R.string.dashboard_widget_mock_normal), Color(0xFF01A652))
        Text("92 mg/dL", color = mock.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MockGoal(label: String, detail: String, progress: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = mock.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(detail, color = if (progress >= 1f) Color(0xFF01A652) else mock.muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Color(0xFFF7931E),
            trackColor = mock.track,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun GoalsWidgetMockup() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MockHeader(Icons.Default.Flag, Color(0xFFF7931E), stringResource(R.string.dashboard_widget_mock_goals), stringResource(R.string.dashboard_widget_mock_goals_reached))
        MockGoal(stringResource(R.string.dashboard_widget_mock_weight), "74.2 → 72.0 kg", 0.6f)
        MockGoal(stringResource(R.string.dashboard_widget_mock_blood_pressure), stringResource(R.string.dashboard_widget_mock_reached), 1f)
        MockGoal(stringResource(R.string.dashboard_widget_mock_ldl), "128 → 100 mg/dL", 0.35f)
    }
}

@Composable
private fun WeightWidgetMockup() {
    Column {
        MockHeader(Icons.Default.MonitorWeight, Color(0xFF6AA8FF), stringResource(R.string.dashboard_widget_mock_weight_caps), "-1.2 kg")
        Spacer(modifier = Modifier.height(4.dp))
        Text("74.2 kg", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_last_30_days), color = mock.muted, style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(6.dp))
        val points = listOf(75.4f, 75.6f, 75.1f, 75.3f, 74.9f, 74.8f, 75.0f, 74.6f, 74.4f, 74.5f, 74.2f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val min = points.min()
            val range = points.max() - min
            val path = Path()
            points.forEachIndexed { i, v ->
                val x = size.width * i / (points.size - 1)
                val y = size.height * (1 - (v - min) / range)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF6AA8FF), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun PostureWidgetMockup() {
    Column {
        MockHeader(Icons.Default.Accessibility, Color(0xFF4ECDC4), stringResource(R.string.dashboard_widget_mock_posture), stringResource(R.string.dashboard_widget_mock_good))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("78", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("/100", color = mock.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        Text(stringResource(R.string.dashboard_widget_mock_posture_checked), color = mock.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        MockButton(stringResource(R.string.dashboard_widget_mock_new_check), Modifier.fillMaxWidth())
    }
}

@Composable
private fun CycleWidgetMockup() {
    Column {
        MockHeader(Icons.Default.Autorenew, Color(0xFFF48FB1), stringResource(R.string.dashboard_widget_mock_cycle), stringResource(R.string.dashboard_widget_mock_soon))
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.dashboard_widget_mock_cycle_day), color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_next_period), color = mock.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MockBar(progress: Float, color: Color) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp),
        color = color,
        trackColor = mock.track,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {}
    )
}

@Composable
private fun MockChipButton(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = mock.track, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}

@Composable
private fun WaterWidgetMockup() {
    val water = Color(0xFF7DBBFF)
    Column {
        MockHeader(Icons.Default.WaterDrop, Color(0xFF4FA3FF), stringResource(R.string.dashboard_widget_mock_water), "48%", water)
        Spacer(modifier = Modifier.height(4.dp))
        Text("1.2 L", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_water_of), color = mock.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        MockBar(0.48f, Color(0xFF4FA3FF))
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MockChipButton("+250 ml", water, Modifier.weight(1f))
            MockChipButton("+500 ml", water, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StrainWidgetMockup() {
    val strain = Color(0xFF00A4FF)
    val track = mock.track
    Column {
        MockHeader(Icons.Default.Bolt, strain, stringResource(R.string.dashboard_widget_mock_strain), stringResource(R.string.dashboard_widget_strain_on_target), Color(0xFF01A652))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("11.4", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("/ 21", color = mock.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = radius)
            drawRoundRect(
                strain.copy(alpha = 0.33f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 10.2f / 21f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width * 3.3f / 21f, size.height),
                cornerRadius = radius
            )
            drawRoundRect(strain, size = androidx.compose.ui.geometry.Size(size.width * 11.4f / 21f, size.height), cornerRadius = radius)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.dashboard_widget_mock_strain_target), color = mock.muted, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.dashboard_widget_mock_strain_workouts), color = mock.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BedtimeWidgetMockup() {
    Column {
        MockHeader(Icons.Default.Hotel, Color(0xFFB39DDB), stringResource(R.string.dashboard_widget_mock_bedtime), "40m debt")
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.dashboard_widget_mock_tonight), color = mock.muted, style = MaterialTheme.typography.labelSmall)
        Text("10:45 PM", color = mock.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.dashboard_widget_mock_bedtime_wind_down), color = mock.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.dashboard_widget_mock_bedtime_last_night), color = mock.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

// endregion
