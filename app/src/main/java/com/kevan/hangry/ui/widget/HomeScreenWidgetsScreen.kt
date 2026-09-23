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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
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
                title = {
                    Text(
                        text = "Home Screen Widgets",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            // Header Intro
            HangryCard {
                Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Glanceable Health on Your Phone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        HangryInfoTip(
                            title = "Home Screen Widgets",
                            body = "Add widgets to your Android home screen for activity, sleep, recovery, heart, health markers, goals, weight, posture, cycle, breathing, supplements and one-tap meal logging."
                        )
                    }
                }
            }

            // 1. Daily Activity Widget
            WidgetPreviewCard(
                title = "Daily Activity",
                sizeLabel = "3 × 2",
                description = "Today's steps and active calories.",
                onPinWidget = { pinWidget(context, ActivityWidgetProvider::class.java) }
            ) {
                ActivityWidgetMockup()
            }

            // 2. Quick Log Meal Widget
            WidgetPreviewCard(
                title = "Quick Log Meal",
                sizeLabel = "2 × 1",
                description = "One-tap camera shortcut to snap and log meals in seconds.",
                onPinWidget = { pinWidget(context, QuickLogWidgetProvider::class.java) }
            ) {
                QuickLogWidgetMockup()
            }

            // 3. Sleep Insights Widget
            WidgetPreviewCard(
                title = "Sleep Insights",
                sizeLabel = "2 × 2",
                description = "Last night's sleep, when you slept, and how steady your sleep schedule is.",
                onPinWidget = { pinWidget(context, SleepWidgetProvider::class.java) }
            ) {
                SleepWidgetMockup()
            }

            // 4. Recovery Score Widget
            WidgetPreviewCard(
                title = "Recovery Score",
                sizeLabel = "2 × 2",
                description = "Autonomic recovery percentage, readiness band, and advice.",
                onPinWidget = { pinWidget(context, RecoveryWidgetProvider::class.java) }
            ) {
                RecoveryWidgetMockup()
            }

            // Supplements Widget
            WidgetPreviewCard(
                title = "Supplements",
                sizeLabel = "2 × 2",
                description = "Your next supplement dose and how many you've taken today.",
                onPinWidget = { pinWidget(context, SupplementsWidgetProvider::class.java) }
            ) {
                SupplementsWidgetMockup()
            }

            // 5. Daily Overview Widget
            WidgetPreviewCard(
                title = "Daily Overview",
                sizeLabel = "4 × 2",
                description = "All-in-one glance: Recovery, Activity, Sleep, and Quick Log.",
                onPinWidget = { pinWidget(context, OverviewWidgetProvider::class.java) }
            ) {
                OverviewWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Breathe",
                sizeLabel = "2 × 2",
                description = "Minutes breathed today and this week. Tap to start your usual pattern with Dash, or pick another.",
                onPinWidget = { pinWidget(context, BreatheWidgetProvider::class.java) }
            ) {
                BreatheWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Heart",
                sizeLabel = "2 × 2",
                description = "Resting heart rate and HRV, each compared with your 4-week normal.",
                onPinWidget = { pinWidget(context, HeartWidgetProvider::class.java) }
            ) {
                HeartWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Health Markers",
                sizeLabel = "2 × 2",
                description = "Latest blood pressure and blood sugar with their status. The + opens Health Records to log a reading.",
                onPinWidget = { pinWidget(context, HealthMarkersWidgetProvider::class.java) }
            ) {
                MarkersWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Goals",
                sizeLabel = "4 × 2",
                description = "Progress toward your weight goal and any health marker goals, up to three at a time.",
                onPinWidget = { pinWidget(context, GoalsWidgetProvider::class.java) }
            ) {
                GoalsWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Weight Trend",
                sizeLabel = "2 × 2",
                description = "Latest weight, how much it changed in 30 days, and a trend line.",
                onPinWidget = { pinWidget(context, WeightWidgetProvider::class.java) }
            ) {
                WeightWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Posture Check",
                sizeLabel = "2 × 2",
                description = "Your last posture score and when you took it, with a button that opens a new check.",
                onPinWidget = { pinWidget(context, PostureWidgetProvider::class.java) }
            ) {
                PostureWidgetMockup()
            }

            WidgetPreviewCard(
                title = "Cycle",
                sizeLabel = "2 × 2",
                description = "Cycle day and when your next period is predicted. Available when your sex is set to female.",
                onPinWidget = { pinWidget(context, CycleWidgetProvider::class.java) }
            ) {
                CycleWidgetMockup()
            }

            HideValuesCard()

            // How to add manually guidance
            HangryCard {
                Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Add from your home screen",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        HangryInfoTip(
                            title = "How to add from your home screen",
                            body = "1. Press and hold any empty area on your phone's home screen.\n" +
                                "2. Tap 'Widgets' in the pop-up menu.\n" +
                                "3. Scroll down and locate 'Hangry'.\n" +
                                "4. Touch and drag your preferred widget onto your screen."
                        )
                    }
                }
            }
        }
    }
}

private fun pinWidget(context: Context, providerClass: Class<*>) {
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
        val provider = ComponentName(context, providerClass)
        appWidgetManager.requestPinAppWidget(provider, null, null)
    } else {
        Toast.makeText(
            context,
            "Touch and hold your home screen, then choose Widgets -> Hangry to add.",
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
                    Text("Pin to Home", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Mockup Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF252329))
                    .border(1.dp, Color(0xFF3A363F), RoundedCornerShape(16.dp))
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF7E1D), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("DAILY ACTIVITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB5AEB8))
            }
            Text("Today", style = MaterialTheme.typography.labelSmall, color = Color(0xFF948D98))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricColumn(icon = Icons.Default.DirectionsWalk, tint = Color(0xFF6AA8FF), value = "6,420", label = "Steps")
            MetricColumn(icon = Icons.Default.LocalFireDepartment, tint = Color(0xFFFF7E1D), value = "450", label = "Active kcal")
        }
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
            Text("Log Meal", color = Color(0xFFF9F4F2), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
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
                Text("SLEEP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB5AEB8))
            }
            Surface(
                color = Color(0xFF2F2C33),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("88% steady", color = Color(0xFF00A4FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("7h 30m", color = Color(0xFFF9F4F2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("11:40 PM – 7:10 AM", color = Color(0xFFB5AEB8), style = MaterialTheme.typography.bodySmall)
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
                Text("SUPPLEMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB5AEB8))
            }
            Surface(color = Color(0xFF2F2C33), shape = RoundedCornerShape(10.dp)) {
                Text("2/4", color = Color(0xFFF7931E), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Magnesium Glycinate", color = Color(0xFFF9F4F2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Next at 9:00 PM · 2 left today", color = Color(0xFFB5AEB8), style = MaterialTheme.typography.bodySmall)
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
                Text("RECOVERY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB5AEB8))
            }
            Surface(
                color = Color(0xFF2F2C33),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("PRIMED", color = Color(0xFF01A652), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("82%", color = Color(0xFFF9F4F2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Physiological markers elevated above baseline", color = Color(0xFFB5AEB8), style = MaterialTheme.typography.bodySmall)
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
            Text("HANGRY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF9F4F2))
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
                    Text("Log Meal", color = Color(0xFFF9F4F2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RECOVERY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB5AEB8))
                Text("82%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFF9F4F2))
                Text("PRIMED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF01A652))
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0xFF3A363F))
            )

            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(start = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("6,420 steps", color = Color(0xFF6AA8FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("450 kcal", color = Color(0xFFFF7E1D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Sleep: 7h 30m", color = Color(0xFF00A4FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
        Text(value, color = Color(0xFFF9F4F2), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFB5AEB8), style = MaterialTheme.typography.labelSmall)
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
                Text("Hide values on widgets", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                Text(
                    "Heart, markers, goals, weight, posture and cycle show no numbers until you open the app.",
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

private val MockWhite = Color(0xFFF9F4F2)
private val MockMuted = Color(0xFFB5AEB8)
private val MockChip = Color(0xFF2F2C33)

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
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MockMuted)
        }
        if (chip != null) {
            Surface(color = MockChip, shape = RoundedCornerShape(10.dp)) {
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
        Text(text, color = MockWhite, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BreatheWidgetMockup() {
    Column {
        MockHeader(Icons.Default.Air, Color(0xFF6FC3DF), "BREATHE", "45 min / 7d")
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.dash_breathe_in),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("12 min", color = MockWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("today · 2 sessions", color = MockMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MockButton("4s Box", Modifier.weight(1f))
            MockButton("5 BPM", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MockValueRow(label: String, value: String, delta: String? = null, deltaColor: Color = MockMuted) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(label, color = MockMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = MockWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (delta != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(delta, color = deltaColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeartWidgetMockup() {
    Column {
        MockHeader(Icons.Default.MonitorHeart, Color(0xFFFF6B6B), "HEART")
        Spacer(modifier = Modifier.height(8.dp))
        MockValueRow("Resting HR", "58 bpm", "▼ 3", Color(0xFF01A652))
        Spacer(modifier = Modifier.height(4.dp))
        MockValueRow("HRV", "48 ms", "▲ 5", Color(0xFF01A652))
        Spacer(modifier = Modifier.height(4.dp))
        Text("vs your 4-week normal", color = MockMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MarkersWidgetMockup() {
    Column {
        MockHeader(Icons.Default.WaterDrop, Color(0xFFE57373), "HEALTH MARKERS", "+", MockWhite)
        Spacer(modifier = Modifier.height(8.dp))
        MockHeader(Icons.Default.Favorite, Color.Transparent, "Blood pressure", "Normal", Color(0xFF01A652))
        Text("118/76 mmHg", color = MockWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        MockHeader(Icons.Default.Favorite, Color.Transparent, "Blood sugar", "Normal", Color(0xFF01A652))
        Text("92 mg/dL", color = MockWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MockGoal(label: String, detail: String, progress: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = MockWhite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(detail, color = if (progress >= 1f) Color(0xFF01A652) else MockMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Color(0xFFF7931E),
            trackColor = MockChip,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun GoalsWidgetMockup() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MockHeader(Icons.Default.Flag, Color(0xFFF7931E), "GOALS", "1/3 reached")
        MockGoal("Weight", "74.2 → 72.0 kg", 0.6f)
        MockGoal("Blood pressure", "Reached", 1f)
        MockGoal("LDL cholesterol", "128 → 100 mg/dL", 0.35f)
    }
}

@Composable
private fun WeightWidgetMockup() {
    Column {
        MockHeader(Icons.Default.MonitorWeight, Color(0xFF6AA8FF), "WEIGHT", "-1.2 kg")
        Spacer(modifier = Modifier.height(4.dp))
        Text("74.2 kg", color = MockWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Last 30 days", color = MockMuted, style = MaterialTheme.typography.labelSmall)
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
        MockHeader(Icons.Default.Accessibility, Color(0xFF4ECDC4), "POSTURE", "GOOD")
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("78", color = MockWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("/100", color = MockMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
        Text("Checked 16 days ago · time for another", color = MockMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        MockButton("New check", Modifier.fillMaxWidth())
    }
}

@Composable
private fun CycleWidgetMockup() {
    Column {
        MockHeader(Icons.Default.Autorenew, Color(0xFFF48FB1), "CYCLE", "SOON")
        Spacer(modifier = Modifier.height(4.dp))
        Text("Day 26", color = MockWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Next period in ~3 days", color = MockMuted, style = MaterialTheme.typography.bodySmall)
    }
}

// endregion
