package com.kevan.hangry.ui.dashboard

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.DashboardWidget
import com.kevan.hangry.domain.model.MetricType
import com.kevan.hangry.domain.model.WidgetDisplayStyle
import com.kevan.hangry.domain.model.WidgetType
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardSheet(
    widgets: List<DashboardWidget>,
    onToggleVisibility: (widgetId: String, isVisible: Boolean) -> Unit,
    onReorderWidgets: (orderedIds: List<String>) -> Unit,
    onAddCustomWidget: (widget: DashboardWidget) -> Unit,
    onRemoveWidget: (widgetId: String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dashboard_customize_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary
                    )
                    HangryInfoTip(
                        title = stringResource(R.string.dashboard_customize_title),
                        body = stringResource(R.string.dashboard_customize_info)
                    )
                }
                Row {
                    TextButton(onClick = onResetDefaults) {
                        Text(stringResource(R.string.dashboard_reset_defaults), color = tokens.scoreColors.rebuild)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dashboard_done))
                    }
                }
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.s))

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_create_custom_widget))
            }

            Spacer(modifier = Modifier.height(HangryTokens.Spacing.m))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(widgets, key = { _, item -> item.id }) { index, widget ->
                    Surface(
                        color = tokens.cardBackground,
                        shape = MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = widget.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (widget.isVisible) tokens.textPrimary else tokens.textMuted
                                )
                                if (widget.type == WidgetType.CUSTOM_METRIC) {
                                    Text(
                                        text = stringResource(R.string.dashboard_custom_widget_subtitle, widget.metricType?.name?.replace('_', ' ') ?: "", widget.displayStyle.name),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.scoreColors.primed
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Reorder up
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = widgets.map { it.id }.toMutableList()
                                            val currentId = mutable.removeAt(index)
                                            mutable.add(index - 1, currentId)
                                            onReorderWidgets(mutable)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.dashboard_move_up))
                                }

                                // Reorder down
                                IconButton(
                                    onClick = {
                                        if (index < widgets.size - 1) {
                                            val mutable = widgets.map { it.id }.toMutableList()
                                            val currentId = mutable.removeAt(index)
                                            mutable.add(index + 1, currentId)
                                            onReorderWidgets(mutable)
                                        }
                                    },
                                    enabled = index < widgets.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.dashboard_move_down))
                                }

                                // Delete custom widget
                                if (widget.type == WidgetType.CUSTOM_METRIC) {
                                    IconButton(
                                        onClick = { onRemoveWidget(widget.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.dashboard_delete_widget),
                                            tint = tokens.scoreColors.rebuild,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Switch(
                                    checked = widget.isVisible,
                                    onCheckedChange = { onToggleVisibility(widget.id, it) },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCustomWidgetDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { newWidget ->
                onAddCustomWidget(newWidget)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCustomWidgetDialog(
    onDismiss: () -> Unit,
    onCreate: (DashboardWidget) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedMetric by remember { mutableStateOf(MetricType.STEPS) }
    var targetGoalText by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(WidgetDisplayStyle.RING) }
    var metricDropdownExpanded by remember { mutableStateOf(false) }

    val defaultGoalSuggestions = mapOf(
        MetricType.STEPS to ("10000" to "steps"),
        MetricType.ACTIVE_CALORIES to ("600" to "kcal"),
        MetricType.RHR to ("55" to "bpm"),
        MetricType.HRV to ("65" to "ms"),
        MetricType.VO2_MAX to ("45" to "mL/kg/min"),
        MetricType.SPO2 to ("98" to "%"),
        MetricType.SLEEP_DURATION to ("480" to "min"),
        MetricType.DAY_STRAIN to ("12" to "strain"),
        MetricType.STRESS to ("30" to "%"),
        MetricType.WEIGHT to ("70" to "kg")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_create_card_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dashboard_widget_title_label)) },
                    placeholder = { Text(stringResource(R.string.dashboard_widget_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Metric Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = metricDropdownExpanded,
                    onExpandedChange = { metricDropdownExpanded = !metricDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMetric.name.replace('_', ' '),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.dashboard_select_metric)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = metricDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = metricDropdownExpanded,
                        onDismissRequest = { metricDropdownExpanded = false }
                    ) {
                        MetricType.entries.forEach { metric ->
                            DropdownMenuItem(
                                text = { Text(metric.name.replace('_', ' ')) },
                                onClick = {
                                    selectedMetric = metric
                                    if (targetGoalText.isBlank()) {
                                        targetGoalText = defaultGoalSuggestions[metric]?.first ?: ""
                                    }
                                    metricDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Target / Goal input
                OutlinedTextField(
                    value = targetGoalText,
                    onValueChange = { input ->
                        val filtered = input.filter { ch -> ch.isDigit() || ch == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            targetGoalText = filtered
                        }
                    },
                    label = { Text(stringResource(R.string.dashboard_target_goal_label)) },
                    placeholder = { Text(stringResource(R.string.dashboard_target_goal_placeholder, defaultGoalSuggestions[selectedMetric]?.first ?: "100")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Display Style Selector
                Text(stringResource(R.string.dashboard_display_style), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetDisplayStyle.entries.forEach { style ->
                        val isSelected = selectedStyle == style
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStyle = style },
                            label = { Text(style.name.replace('_', ' ')) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalTitle = if (title.isNotBlank()) title else selectedMetric.name.replace('_', ' ')
                    val goal = targetGoalText.toDoubleOrNull()
                    val unit = defaultGoalSuggestions[selectedMetric]?.second

                    val newWidget = DashboardWidget(
                        id = "custom_${UUID.randomUUID()}",
                        type = WidgetType.CUSTOM_METRIC,
                        title = finalTitle,
                        isVisible = true,
                        metricType = selectedMetric,
                        targetGoal = goal,
                        unit = unit,
                        displayStyle = selectedStyle
                    )
                    onCreate(newWidget)
                }
            ) {
                Text(stringResource(R.string.dashboard_add_to_today))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
