package com.kevan.hangry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kevan.hangry.ui.navigation.BottomNavDestination
import com.kevan.hangry.ui.navigation.HangryBottomNavBar
import com.kevan.hangry.ui.navigation.HangryNavGraph
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
        val deepLinkOnTop = requested?.takeIf { it in STACKED_DEEP_LINKS }
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

                Scaffold(
                    // Every screen hosts its own Scaffold/TopAppBar (or, on Dashboard, a
                    // statusBarsPadding()'d header) which already applies the status/nav bar
                    // insets itself. Leaving the default here would apply them a second time,
                    // producing a large dead gap above each screen's content.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        HangryBottomNavBar(
                            currentRoute = currentRoute,
                            navController = navController
                        )
                    }
                ) { innerPadding ->
                    HangryNavGraph(
                        navController = navController,
                        appContainer = appContainer,
                        modifier = Modifier.padding(innerPadding),
                        startDestination = startDestination,
                        quickLogTrigger = quickLogTrigger,
                        onQuickLogTriggerHandled = { quickLogTrigger = false }
                    )
                }
            }
        }
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
        val STACKED_DEEP_LINKS = setOf(Screen.Supplements.route)
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