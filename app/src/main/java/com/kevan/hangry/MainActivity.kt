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
import com.kevan.hangry.util.OnboardingFlag

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

        // Start on Today (or Welcome before onboarding) from a cached flag - no waiting on the
        // database. The database is only consulted once, the first launch after the flag existed.
        val onboarded = OnboardingFlag.get(this) ?: runCatching {
            kotlinx.coroutines.runBlocking { appContainer.userProfileRepository.getProfileSync()?.onboardingCompleted == true }
        }.getOrElse {
            android.util.Log.e("MainActivity", "Couldn't read onboarding state, falling back to Welcome", it)
            false
        }.also { OnboardingFlag.set(this, it) }
        val startDestination = if (onboarded) Screen.Dashboard.route else Screen.Welcome.route

        // A widget/notification destination always opens on top of the start screen, so Back
        // lands on Today instead of a blank screen. Before onboarding, only the privacy policy
        // (which Health Connect can ask to show) is allowed through.
        val requested = getDestinationFromIntent(intent)
        val deepLinkOnTop = requested?.takeIf { route ->
            route != Screen.Dashboard.route && (onboarded || route == Screen.PrivacyPolicy.route)
        }

        setContent {
            HangryTheme {
                val navController = rememberNavController()
                activeNavController = navController
                LaunchedEffect(Unit) {
                    if (deepLinkOnTop != null) {
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