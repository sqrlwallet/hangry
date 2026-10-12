package com.kevan.hangry.ui.bodymetrics

import com.kevan.hangry.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.BodyMetricGroup
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.ui.coach.DashSpinner
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.components.HangryInfoTip
import com.kevan.hangry.ui.components.MetricBandTable
import com.kevan.hangry.ui.components.MetricInfoBlock
import com.kevan.hangry.ui.components.MetricRangeBar
import com.kevan.hangry.ui.components.MetricStatusChip
import com.kevan.hangry.ui.components.metricToneColor
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The top-bar dialog: general notes. Each group's own description sits on its card header. */
@Composable
private fun bodyMetricsInfo(): List<HangryInfoSection> = listOf(
    HangryInfoSection(
        stringResource(R.string.body_metrics_info_source_title),
        stringResource(R.string.body_metrics_info_source_body)
    ),
    HangryInfoSection(
        stringResource(R.string.body_metrics_info_tap_title),
        stringResource(R.string.body_metrics_info_tap_body)
    ),
    HangryInfoSection(
        stringResource(R.string.body_metrics_info_not_diagnosis_title),
        stringResource(R.string.body_metrics_info_not_diagnosis_body)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMetricsScreen(
    viewModel: BodyMetricsViewModel,
    onNavigateBack: () -> Unit,
    onUpdateMeasurements: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<BodyMetric?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.body_metrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back))
                    }
                },
                actions = {
                    HangryInfoIconButton(title = stringResource(R.string.body_metrics_about), sections = bodyMetricsInfo())
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                DashSpinner(size = 96.dp)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = HangryTokens.Spacing.m),
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = HangryTokens.Spacing.l),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            item(key = "summary") { SummaryCard(uiState, onUpdateMeasurements, onSelect = { selected = it }) }
            items(uiState.groups, key = { it.id }) { group ->
                GroupCard(group, onSelect = { selected = it })
            }
        }
    }

    selected?.let { metric ->
        MetricInfoSheet(metric = metric, onDismiss = { selected = null }, onUpdateMeasurements = {
            selected = null
            onUpdateMeasurements()
        })
    }
}

/** Calculated count, the inputs everything is based on, anything worth a look, and the edit button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(state: BodyMetricsUiState, onUpdateMeasurements: () -> Unit, onSelect: (BodyMetric) -> Unit) {
    val tokens = LocalHangryTokens.current
    val input = state.input
    // Waist and neck are left out: they already appear as their own rows under Shape.
    val chips = buildList {
        input.heightCm?.let { add(stringResource(R.string.body_metrics_chip_height, fmt(it, 0))) }
        input.weightKg?.let { add(stringResource(R.string.body_metrics_chip_weight, fmt(it, 1))) }
        input.age?.let { add(stringResource(R.string.body_metrics_chip_age, it.toString())) }
        input.bodyFatPercent?.let { bf ->
            val date = state.bodyFatScanDate?.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            add(
                if (date != null) stringResource(R.string.body_metrics_chip_body_fat_dated, fmt(bf, 1), date)
                else stringResource(R.string.body_metrics_chip_body_fat, fmt(bf, 1))
            )
        }
        input.hipCm?.let { add(stringResource(R.string.body_metrics_chip_hips, fmt(it, 1))) }
        input.chestCm?.let { add(stringResource(R.string.body_metrics_chip_chest, fmt(it, 1))) }
    }
    val attention = state.allMetrics.filter { it.isAvailable && (it.tone == MetricTone.CAUTION || it.tone == MetricTone.RISK) }
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${state.availableCount}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                stringResource(R.string.body_metrics_of_total_calculated, state.allMetrics.size),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                modifier = Modifier.alignByBaseline()
            )
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(HangryTokens.Spacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { InputChipLabel(it) }
            }
        }
        if (attention.isNotEmpty()) {
            Spacer(Modifier.height(HangryTokens.Spacing.s))
            HorizontalDivider(color = tokens.cardBorder)
            Spacer(Modifier.height(HangryTokens.Spacing.xs))
            Text(
                stringResource(R.string.body_metrics_worth_a_look),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
            attention.forEach { metric ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(metric) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToneDot(metric.tone)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        metric.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        metric.status.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = metricToneColor(metric.tone),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        Spacer(Modifier.height(HangryTokens.Spacing.s))
        FilledTonalButton(onClick = onUpdateMeasurements, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.body_metrics_update_measurements))
        }
    }
}

@Composable
private fun InputChipLabel(text: String) {
    val tokens = LocalHangryTokens.current
    Surface(color = tokens.cardBorder.copy(alpha = 0.5f), shape = RoundedCornerShape(HangryTokens.CornerRadii.pill)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun groupIcon(id: String): ImageVector = when (id) {
    "size" -> Icons.Default.MonitorWeight
    "composition" -> Icons.Default.PieChart
    "shape" -> Icons.Default.Straighten
    "energy" -> Icons.Default.LocalFireDepartment
    "reference" -> Icons.Default.MonitorHeart
    else -> Icons.Outlined.Info
}

/**
 * One card per group: header, then a row per calculated metric. Metrics we can't work out yet
 * collapse into a single "Add … to see N more" line that expands to list them.
 */
@Composable
private fun GroupCard(group: BodyMetricGroup, onSelect: (BodyMetric) -> Unit) {
    val tokens = LocalHangryTokens.current
    val available = group.metrics.filter { it.isAvailable }
    val unavailable = group.metrics.filterNot { it.isAvailable }
    var showUnavailable by rememberSaveable(group.id) { mutableStateOf(false) }
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
            Icon(groupIcon(group.id), contentDescription = null, tint = tokens.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f, fill = false)
            )
            HangryInfoTip(title = group.title, body = group.description)
        }
        available.forEachIndexed { index, metric ->
            if (index > 0) RowDivider()
            MetricRow(metric, onClick = { onSelect(metric) })
        }
        if (unavailable.isNotEmpty()) {
            if (available.isNotEmpty()) RowDivider()
            val inputs = unavailable.flatMap { it.missingInputs }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .map { it.key.lowercase() }
            val hint = if (inputs.isEmpty()) {
                stringResource(R.string.body_metrics_more_unavailable, unavailable.size)
            } else {
                stringResource(R.string.body_metrics_add_to_see_more, inputs.take(3).joinToString(", "), unavailable.size)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showUnavailable = !showUnavailable }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (showUnavailable) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (showUnavailable) R.string.body_metrics_hide_unavailable else R.string.body_metrics_show_unavailable),
                    tint = tokens.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (showUnavailable) {
                unavailable.forEach { metric ->
                    RowDivider()
                    MetricRow(metric, onClick = { onSelect(metric) })
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = LocalHangryTokens.current.cardBorder.copy(alpha = 0.6f))
}

@Composable
private fun ToneDot(tone: MetricTone) {
    Box(Modifier.size(8.dp).background(metricToneColor(tone), CircleShape))
}

/**
 * Label (+ info icon) on the left with a one-line status underneath; value and unit right-aligned
 * on a shared baseline. The value is measured first so it never wraps; the label takes what's left.
 */
@Composable
private fun MetricRow(metric: BodyMetric, onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    metric.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (metric.isAvailable) tokens.textPrimary else tokens.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.body_metrics_about_metric, metric.name),
                    tint = tokens.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            val status = metric.status
            if (metric.isAvailable && status != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(6.dp).background(metricToneColor(metric.tone), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = metricToneColor(metric.tone),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (!metric.isAvailable) {
                Text(
                    text = if (metric.missingInputs.isEmpty()) stringResource(R.string.body_metrics_not_available)
                    else stringResource(R.string.body_metrics_add_inputs, metric.missingInputs.joinToString(", ").lowercase()),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (metric.isAvailable) {
            Spacer(Modifier.width(12.dp))
            ValueWithUnit(metric, valueStyle = MaterialTheme.typography.titleMedium, unitStyle = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ValueWithUnit(metric: BodyMetric, valueStyle: TextStyle, unitStyle: TextStyle, fontWeight: FontWeight = FontWeight.SemiBold) {
    val tokens = LocalHangryTokens.current
    Row {
        Text(
            groupThousands(metric.displayValue),
            style = valueStyle,
            fontWeight = fontWeight,
            color = if (metric.isAvailable) tokens.textPrimary else tokens.textMuted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alignByBaseline()
        )
        if (metric.unit.isNotEmpty()) {
            Text(
                " ${metric.unit}",
                style = unitStyle,
                color = tokens.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricInfoSheet(metric: BodyMetric, onDismiss: () -> Unit, onUpdateMeasurements: () -> Unit) {
    val tokens = LocalHangryTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HangryTokens.Spacing.m)
                .padding(bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            Column {
                Text(metric.name, style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                if (metric.displayValue != NO_VALUE) {
                    ValueWithUnit(
                        metric,
                        valueStyle = MaterialTheme.typography.displaySmall,
                        unitStyle = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                metric.status?.let { MetricStatusChip(it, metric.tone) }
            }

            if (!metric.isAvailable && metric.missingInputs.isNotEmpty()) {
                Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.body_metrics_add_to_see_yours, metric.missingInputs.joinToString(", ").lowercase()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textPrimary
                        )
                        TextButton(onClick = onUpdateMeasurements, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.body_metrics_update_measurements)) }
                    }
                }
            }

            if (metric.bands.isNotEmpty()) {
                if (metric.isAvailable) MetricRangeBar(metric)
                MetricBandTable(metric)
            }

            MetricInfoBlock(stringResource(R.string.body_metrics_what_it_is), metric.info.whatItIs)
            MetricInfoBlock(if (metric.isAvailable) stringResource(R.string.body_metrics_how_we_calculated_yours) else stringResource(R.string.body_metrics_how_its_calculated), metric.info.howCalculated)
            MetricInfoBlock(stringResource(R.string.body_metrics_why_it_matters), metric.info.whyItMatters)
            MetricInfoBlock(stringResource(R.string.body_metrics_source), metric.info.source)
            Text(
                stringResource(R.string.body_metrics_screening_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}

private fun fmt(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

/** Placeholder the calculator uses for a metric it couldn't work out. */
private const val NO_VALUE = "—"

/** Whole numbers of 4+ digits (not decimals) in a display value, e.g. the 2850 in "2850" or "1850–2100". */
private val BIG_INTEGER = Regex("""(?<![\d.])\d{4,}(?!\d)""")

/** "2850" → "2,850" so calorie values read like the rest of the app. Decimals stay as the calculator wrote them. */
internal fun groupThousands(value: String): String =
    BIG_INTEGER.replace(value) { String.format(Locale.US, "%,d", it.value.toLong()) }
