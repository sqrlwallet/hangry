package com.kevan.hangry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kevan.hangry.ui.navigation.BottomNavDestination
import com.kevan.hangry.ui.navigation.HangryBottomNavBar
import com.kevan.hangry.ui.navigation.HangryNavGraph
import com.kevan.hangry.ui.navigation.LocalDockInset
import com.kevan.hangry.ui.navigation.rememberDockInset
import com.kevan.hangry.ui.navigation.Screen
import com.kevan.hangry.ui.theme.HangryTheme

class MainActivity : ComponentActivity() {

    private var activeNavController: NavHostController? = null
    private var quickLogTrigger by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as HangryApplication).container

        if (intent?.action == "com.kevan.hangry.ACTION_QUICK_LOG_MEAL" || intent?.getStringExtra("action") == "quick_log_meal") {
            quickLogTrigger = true
        }

        // Detail screens opened from a notification/widget sit on top of Today, so Back lands
        // somewhere sensible instead of closing the app.
        val requested = getDestinationFromIntent(intent)
        val deepLinkOnTop = requested?.takeIf { route -> STACKED_DEEP_LINKS.any { route.substringBefore('?') == it } }
        val initialDestination = requested?.takeIf { deepLinkOnTop == null }
        val startDestination = initialDestination ?: runCatching {
            kotlinx.coroutines.runBlocking {
                if (appContainer.userProfileRepository.getProfileSync()?.onboardingCompleted == true) {
                    Screen.Dashboard.route
                } else {
                    Screen.Welcome.route
                }
            }
        }.getOrElse {
            android.util.Log.e("MainActivity", "Failed to determine initial destination, falling back to Welcome", it)
            Screen.Welcome.route
        }

        setContent {
            HangryTheme {
                val navController = rememberNavController()
                activeNavController = navController
                LaunchedEffect(Unit) {
                    if (deepLinkOnTop != null && startDestination == Screen.Dashboard.route) {
                        navController.navigate(deepLinkOnTop)
                    }
                }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // The dock floats over the screens instead of sitting on its own strip, so
                // content shows through behind it; tab screens pad by LocalDockInset so their
                // last item can still scroll clear of it.
                CompositionLocalProvider(LocalDockInset provides rememberDockInset(currentRoute)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HangryNavGraph(
                            navController = navController,
                            appContainer = appContainer,
                            modifier = Modifier.fillMaxSize(),
                            startDestination = startDestination,
                            quickLogTrigger = quickLogTrigger,
                            onQuickLogTriggerHandled = { quickLogTrigger = false }
                        )
                        HangryBottomNavBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app is when home screen widgets come into view, so bring them up to date
        // with anything logged in this visit (breathing, readings, weigh-ins, posture checks).
        com.kevan.hangry.ui.widget.HangryWidgetUpdater.updateAllWidgets(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == "com.kevan.hangry.ACTION_QUICK_LOG_MEAL" || intent?.getStringExtra("action") == "quick_log_meal") {
            quickLogTrigger = true
        }
        val destination = getDestinationFromIntent(intent)
        if (destination != null) {
            activeNavController?.navigate(destination)
        }
    }

    private companion object {
        /** Base routes (before any `?args`) that open on top of Today instead of replacing it. */
        val STACKED_DEEP_LINKS = setOf(
            Screen.Supplements.route,
            "breathing",
            Screen.HeartMetrics.route,
            Screen.HealthRecords.route,
            Screen.Trends.route,
            Screen.Posture.route,
            Screen.PostureCapture.route
        )
    }

    private fun getDestinationFromIntent(intent: Intent?): String? {
        val extraDest = intent?.getStringExtra("destination")
        if (!extraDest.isNullOrBlank()) return extraDest

        return when (intent?.action) {
            "android.intent.action.VIEW_PERMISSION_USAGE",
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" -> Screen.PrivacyPolicy.route
            else -> null
        }
    }
}