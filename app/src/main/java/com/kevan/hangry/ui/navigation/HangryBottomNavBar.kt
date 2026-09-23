package com.kevan.hangry.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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
        label = "Ask Dash",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )

    data object Workouts : BottomNavDestination(
        route = Screen.Training.route,
        label = "Workouts",
        selectedIcon = Icons.Filled.FitnessCenter,
        unselectedIcon = Icons.Outlined.FitnessCenter
    )

    /** Trends, posture, body fat and everything else that isn't its own tab. */
    data object More : BottomNavDestination(
        route = Screen.More.route,
        label = "More",
        selectedIcon = Icons.Filled.GridView,
        unselectedIcon = Icons.Outlined.GridView
    )

    companion object {
        val entries = listOf(Today, Nutrition, AiCoach, Workouts, More)
        val routeSet = entries.map { it.route }.toSet()
    }
}

/** Height of the floating dock itself, plus its top and bottom margins. */
private val DOCK_HEIGHT = 68.dp
private val DOCK_MARGINS = 12.dp

/**
 * How much room the floating dock covers at the bottom of the screen right now: the dock plus
 * the system navigation bar on tab screens, zero where the dock is hidden (other screens, or
 * while the keyboard is up). Tab screens pad their scrolling content by this much so the last
 * item can scroll clear of the dock, while everything else shows through behind it.
 */
val LocalDockInset = compositionLocalOf { 0.dp }

@Composable
fun rememberDockInset(currentRoute: String?): Dp {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val navBar = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    return if (currentRoute in BottomNavDestination.routeSet && !imeVisible) DOCK_HEIGHT + DOCK_MARGINS + navBar else 0.dp
}

/**
 * Luxury Floating Dock Bottom Navigation Bar.
 *
 * Designed with a floating capsule geometry, frosted obsidian glass aesthetic,
 * luminous hairline chamfer gradient border, and tactile spring interactions.
 * Automatically clears system navigation bar insets and smoothly hides on keyboard entry.
 */
@Composable
fun HangryBottomNavBar(
    currentRoute: String?,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val isVisible = currentRoute in BottomNavDestination.routeSet && !isImeVisible

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
        ) + fadeIn(tween(220)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(180)
        ) + fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = LocalHangryTokens.current.glassSurface,
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            LocalHangryTokens.current.edgeHighlight.copy(alpha = LocalHangryTokens.current.edgeHighlight.alpha * 1.6f),
                            LocalHangryTokens.current.edgeHighlight.copy(alpha = LocalHangryTokens.current.edgeHighlight.alpha * 0.4f)
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DOCK_HEIGHT)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route

                        FloatingNavItem(
                            destination = destination,
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navController.navigateToTab(destination.route)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.FloatingNavItem(
    destination: BottomNavDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "nav_scale_${destination.label}"
    )

    val animatedPillAlpha by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "nav_pill_alpha_${destination.label}"
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else LocalHangryTokens.current.textMuted,
        animationSpec = tween(durationMillis = 200),
        label = "nav_icon_tint_${destination.label}"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else LocalHangryTokens.current.textMuted,
        animationSpec = tween(durationMillis = 200),
        label = "nav_text_tint_${destination.label}"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = destination.label
            },
        contentAlignment = Alignment.Center
    ) {
        // Active indicator: soft brand-blue capsule
        if (animatedPillAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.86f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f * animatedPillAlpha),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f * animatedPillAlpha)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f * animatedPillAlpha),
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .graphicsLayer(scaleX = animatedScale, scaleY = animatedScale)
                .padding(vertical = 2.dp)
        ) {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(23.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}
