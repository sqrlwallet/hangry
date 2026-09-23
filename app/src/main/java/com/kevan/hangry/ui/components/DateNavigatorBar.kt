package com.kevan.hangry.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevan.hangry.ui.theme.EmberAccent
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reusable obsidian-styled Date Navigator Bar.
 *
 * Allows users to step backwards and forwards through daily records, open a calendar
 * date picker dialog to inspect any past date, and quickly snap back to "Today".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNavigatorBar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val haptic = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }
    val isToday = selectedDate == today
    val isYesterday = selectedDate == today.minusDays(1)
    val canGoForward = selectedDate < today

    var showDatePicker by remember { mutableStateOf(false) }

    val formattedDateText = remember(selectedDate) {
        when {
            isToday -> "Today, ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            isYesterday -> "Yesterday, ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
            else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.cardBackground)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous day button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDateSelected(selectedDate.minusDays(1))
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Day",
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Date picker trigger button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDatePicker = true
                },
                shape = RoundedCornerShape(12.dp),
                color = if (!isToday) EmberAccent.copy(alpha = 0.12f) else Color.Transparent,
                border = if (!isToday) BorderStroke(1.dp, EmberAccent.copy(alpha = 0.35f)) else null,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Date",
                        tint = if (!isToday) EmberAccent else tokens.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formattedDateText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (!isToday) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.5.sp
                        ),
                        color = if (!isToday) EmberAccent else tokens.textPrimary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Today snap button (when on past date)
                AnimatedVisibility(
                    visible = !isToday,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDateSelected(today)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.cardBorder.copy(alpha = 0.6f),
                        modifier = Modifier
                            .height(28.dp)
                            .padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Jump to Today",
                                tint = tokens.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.textSecondary
                            )
                        }
                    }
                }

                // Next day button
                IconButton(
                    onClick = {
                        if (canGoForward) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDateSelected(selectedDate.plusDays(1))
                        }
                    },
                    enabled = canGoForward,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Day",
                        tint = if (canGoForward) tokens.textSecondary else tokens.textMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Informative Past Date pill banner
        AnimatedVisibility(
            visible = !isToday,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = tokens.scoreColors.balanced.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, tokens.scoreColors.balanced.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Viewing past records for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = tokens.scoreColors.balanced
                    )
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = tokens.scoreColors.balanced,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDateSelected(today)
                        }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    // Prevent picking future dates
                    val selectedLocalDate = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                    return !selectedLocalDate.isAfter(today)
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val chosenDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            onDateSelected(chosenDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = EmberAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = tokens.textSecondary)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = tokens.cardBackground,
                    titleContentColor = tokens.textPrimary,
                    headlineContentColor = tokens.textPrimary,
                    weekdayContentColor = tokens.textSecondary,
                    selectedDayContainerColor = EmberAccent,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = EmberAccent
                )
            )
        }
    }
}
