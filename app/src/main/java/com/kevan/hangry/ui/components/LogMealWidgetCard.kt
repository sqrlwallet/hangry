package com.kevan.hangry.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.data.local.entity.FoodLogEntity
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

@Composable
fun LogMealWidgetCard(
    totalCaloriesToday: Int,
    calorieGoal: Int?,
    recentEntries: List<FoodLogEntity>,
    isAnalyzing: Boolean = false,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onOpenNutrition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current

    HangryCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenNutrition() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Nutrition & Meals",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary
                    )
                    HangryInfoTip(
                        title = "Nutrition & Meals",
                        body = "Snap your food and AI logs the calories and macros for you. You can edit any entry afterwards."
                    )
                }

                Surface(
                    color = tokens.cardBackground,
                    shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(1.dp, tokens.cardBorder)
                ) {
                    Text(
                        text = if (totalCaloriesToday > 0) {
                            if (calorieGoal != null && calorieGoal > 0) {
                                "$totalCaloriesToday / $calorieGoal kcal"
                            } else {
                                "$totalCaloriesToday kcal"
                            }
                        } else {
                            "0 kcal today"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (totalCaloriesToday > 0) MaterialTheme.colorScheme.primary else tokens.textMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Photo is the only primary action; gallery is a secondary icon. Manual entry lives
            // in the Nutrition screen's overflow menu.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTakePhoto,
                    enabled = !isAnalyzing,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(HangryTokens.CornerRadii.medium),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAnalyzing) "Reading your meal…" else "Snap Meal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedIconButton(
                    onClick = onPickFromGallery,
                    enabled = !isAnalyzing,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(HangryTokens.CornerRadii.medium)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Pick meal photo from gallery",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Recent entries chips (if any logged today)
            if (recentEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentEntries.take(4).forEach { entry ->
                        Surface(
                            color = tokens.cardBackground,
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.cardBorder)
                        ) {
                            Text(
                                text = "${entry.foodName} (${entry.calories} kcal)",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (recentEntries.size > 4) {
                        Text(
                            text = "+${recentEntries.size - 4} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
