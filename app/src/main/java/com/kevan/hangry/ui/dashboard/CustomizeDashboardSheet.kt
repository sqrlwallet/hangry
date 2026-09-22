package com.kevan.hangry.ui.dashboard

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
    onNavigateToHomeScreenWidgets: (() -> Unit)? = null,
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
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customize Home",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary
                )
                Row {
                    TextButton(onClick = onResetDefaults) {
                        Text("Reset Defaults", color = tokens.scoreColors.rebuild)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }

            Text(
                text = "Show, hide, or reorder cards on your home screen. You can also create custom widgets with specific metrics.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                modifier = Modifier.padding(bottom = HangryTokens.Spacing.m)
            )

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Custom Widget")
            }

            if (onNavigateToHomeScreenWidgets != null) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onNavigateToHomeScreenWidgets()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = HangryTokens.Spacing.s)
                ) {
                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Phone Home Screen Widgets")
                }
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
                                        text = "Custom: ${widget.metricType?.name?.replace('_', ' ') ?: ""} (${widget.displayStyle.name})",
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
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
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
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                }

                                // Delete custom widget
                                if (widget.type == WidgetType.CUSTOM_METRIC) {
                                    IconButton(
                                        onClick = { onRemoveWidget(widget.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Widget",
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
        title = { Text("Create Dashboard Widget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Widget Title") },
                    placeholder = { Text("e.g. My Steps Goal") },
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
                        label = { Text("Select Health Metric") },
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
                    label = { Text("Target Goal (Optional)") },
                    placeholder = { Text("e.g. ${defaultGoalSuggestions[selectedMetric]?.first ?: "100"}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Display Style Selector
                Text("Display Style", style = MaterialTheme.typography.labelMedium)
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
                Text("Add to Home")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
