package com.kevan.hangry.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.EmberAccent
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
                    Text(
                        text = "Glanceable Health on Your Phone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add widgets directly to your Android home screen. Stay on top of your steps, calories, sleep, recovery, and snap meal photos with one tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            // 1. Daily Activity Widget
            WidgetPreviewCard(
                title = "Daily Activity",
                sizeLabel = "3 × 2",
                description = "Live counts for Steps, Active Calories, and Activity Minutes.",
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
                description = "Last night's sleep duration, quality score, and debt.",
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

            // 5. Daily Overview Widget
            WidgetPreviewCard(
                title = "Daily Overview",
                sizeLabel = "4 × 2",
                description = "All-in-one glance: Recovery, Activity, Sleep, and Quick Log.",
                onPinWidget = { pinWidget(context, OverviewWidgetProvider::class.java) }
            ) {
                OverviewWidgetMockup()
            }

            // How to add manually guidance
            HangryCard {
                Column(modifier = Modifier.padding(HangryTokens.Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = EmberAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How to add from your home screen",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Press and hold any empty area on your phone's home screen.\n" +
                                "2. Tap 'Widgets' in the pop-up menu.\n" +
                                "3. Scroll down and locate 'Hangry'.\n" +
                                "4. Touch and drag your preferred widget onto your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        lineHeight = 20.sp
                    )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                }

                Button(
                    onClick = onPinWidget,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmberAccent,
                        contentColor = Color.White
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
                    Text("Pin to Home", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Mockup Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF14171C))
                    .border(1.dp, Color(0xFF262B34), RoundedCornerShape(16.dp))
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
                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF5722), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("DAILY ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
            }
            Text("Today", fontSize = 11.sp, color = Color(0xFF757575))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricColumn(icon = Icons.Default.DirectionsWalk, tint = Color(0xFF26A69A), value = "6,420", label = "Steps")
            MetricColumn(icon = Icons.Default.LocalFireDepartment, tint = Color(0xFFFF5722), value = "450", label = "Active kcal")
            MetricColumn(icon = Icons.Default.Timer, tint = Color(0xFFFFB74D), value = "45m", label = "Minutes")
        }
    }
}

@Composable
private fun QuickLogWidgetMockup() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFF5722))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Meal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                Icon(Icons.Default.Bedtime, null, tint = Color(0xFF9575CD), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SLEEP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
            }
            Surface(
                color = Color(0xFF20252D),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("96 Quality", color = Color(0xFF9575CD), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("7h 30m", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("94% of sleep need met", color = Color(0xFF9E9E9E), fontSize = 12.sp)
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
                Icon(Icons.Default.Favorite, null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("RECOVERY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
            }
            Surface(
                color = Color(0xFF20252D),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("PRIMED", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("82%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Physiological markers elevated above baseline", color = Color(0xFF9E9E9E), fontSize = 12.sp)
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
            Text("HANGRY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Surface(
                color = Color(0xFFFF5722),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Meal", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RECOVERY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
                Text("82%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("PRIMED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0xFF262B34))
            )

            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(start = 10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("6,420 steps", color = Color(0xFF26A69A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("450 kcal", color = Color(0xFFFF5722), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("45m activity", color = Color(0xFFFFB74D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Sleep: 7h 30m", color = Color(0xFF9575CD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFF9E9E9E), fontSize = 10.sp)
    }
}
