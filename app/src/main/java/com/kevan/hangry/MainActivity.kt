package com.kevan.hangry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.kevan.hangry.ui.navigation.HangryNavGraph
import com.kevan.hangry.ui.navigation.Screen
import com.kevan.hangry.ui.theme.HangryTheme

class MainActivity : ComponentActivity() {

    private var activeNavController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as HangryApplication).container

        val initialDestination = getDestinationFromIntent(intent)
        val startDestination = initialDestination ?: kotlinx.coroutines.runBlocking {
            if (appContainer.userProfileRepository.getProfileSync()?.onboardingCompleted == true) {
                Screen.Dashboard.route
            } else {
                Screen.Welcome.route
            }
        }

        setContent {
            HangryTheme {
                val navController = rememberNavController()
                activeNavController = navController
                HangryNavGraph(
                    navController = navController,
                    appContainer = appContainer,
                    startDestination = startDestination
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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