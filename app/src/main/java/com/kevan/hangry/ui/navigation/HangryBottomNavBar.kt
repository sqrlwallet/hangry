package com.kevan.hangry.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.kevan.hangry.ui.theme.EmberAccent
import com.kevan.hangry.ui.theme.LocalHangryTokens

sealed class BottomNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Today : BottomNavDestination(
        route = Screen.Dashboard.route,
        label = "Today",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Nutrition : BottomNavDestination(
        route = Screen.Nutrition.route,
        label = "Nutrition",
        selectedIcon = Icons.Filled.Restaurant,
        unselectedIcon = Icons.Outlined.Restaurant
    )

    data object AiCoach : BottomNavDestination(
        route = Screen.AiCoach.route,
        label = "Coach",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )

    data object Posture : BottomNavDestination(
        route = Screen.Posture.route,
        label = "Posture",
        selectedIcon = Icons.Filled.AccessibilityNew,
        unselectedIcon = Icons.Outlined.AccessibilityNew
    )

    data object Trends : BottomNavDestination(
        route = Screen.Trends.route,
        label = "Trends",
        selectedIcon = Icons.AutoMirrored.Filled.TrendingUp,
        unselectedIcon = Icons.AutoMirrored.Outlined.TrendingUp
    )

    companion object {
        val entries = listOf(Today, Nutrition, AiCoach, Posture, Trends)
        val routeSet = entries.map { it.route }.toSet()
    }
}

@Composable
fun HangryBottomNavBar(
    currentRoute: String?,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val isVisible = currentRoute in BottomNavDestination.routeSet

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        NavigationBar(
            modifier = modifier.height(64.dp),
            containerColor = tokens.cardBackground,
            tonalElevation = 4.dp
        ) {
            BottomNavDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmberAccent,
                        selectedTextColor = EmberAccent,
                        indicatorColor = EmberAccent.copy(alpha = 0.15f),
                        unselectedIconColor = tokens.textMuted,
                        unselectedTextColor = tokens.textMuted
                    )
                )
            }
        }
    }
}
