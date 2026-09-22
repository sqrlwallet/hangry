package com.kevan.hangry

import com.kevan.hangry.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HangryNavigationTest {

    @Test
    fun screenRoutes_areUniqueAndCorrect() {
        val allScreens = listOf(
            Screen.Welcome,
            Screen.HealthConnectExplanation,
            Screen.PermissionSetup,
            Screen.HistoricalSyncSetup,
            Screen.SyncProgress,
            Screen.Dashboard,
            Screen.RecoveryDetails,
            Screen.Sleep,
            Screen.Training,
            Screen.HeartMetrics,
            Screen.Trends,
            Screen.DataSources,
            Screen.Settings,
            Screen.ThemePreview,
            Screen.PrivacyPolicy
        )

        val routeSet = allScreens.map { it.route }.toSet()
        // Ensure every screen route is unique
        assertEquals(allScreens.size, routeSet.size)
        assertEquals(15, allScreens.size)

        // Verify dynamic route builder
        val dynamicRoute = Screen.SyncProgress.createRoute(60)
        assertEquals("sync_progress/60", dynamicRoute)
        assertTrue(dynamicRoute.startsWith("sync_progress/"))
    }
}
