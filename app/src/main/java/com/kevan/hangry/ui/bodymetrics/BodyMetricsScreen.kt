package com.kevan.hangry.ui.bodymetrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.MetricBand
import com.kevan.hangry.domain.model.MetricTone
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.MetricBandTable
import com.kevan.hangry.ui.components.MetricInfoBlock
import com.kevan.hangry.ui.components.MetricRangeBar
import com.kevan.hangry.ui.components.MetricStatusChip
import com.kevan.hangry.ui.components.metricToneColor
import com.kevan.hangry.ui.components.HangryInfoIconButton
import com.kevan.hangry.ui.components.HangryInfoSection
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val BODY_METRICS_INFO = listOf(
    HangryInfoSection(
        "Where these numbers come from",
        "Everything here is calculated on your phone from your height, weight, age, sex, tape measurements and your latest body-fat scan. Update any of those in the Body Fat calculator and every metric refreshes."
    ),
    HangryInfoSection(
        "Tap any metric",
        "Each one opens a short explainer: what it measures, the exact sum we did with your numbers, why it matters, and the research its ranges come from."
    ),
    HangryInfoSection(
        "Not a diagnosis",
        "These are screening numbers used by doctors and researchers. They're great for tracking trends, but talk to a health professional before acting on a single reading."
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
                title = { Text("Body Metrics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HangryInfoIconButton(title = "About Body Metrics", sections = BODY_METRICS_INFO)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = HangryTokens.Spacing.m),
            contentPadding = PaddingValues(top = HangryTokens.Spacing.s, bottom = HangryTokens.Spacing.l),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
        ) {
            item { SummaryCard(uiState, onUpdateMeasurements) }

            val attention = uiState.allMetrics.filter { it.isAvailable && (it.tone == MetricTone.CAUTION || it.tone == MetricTone.RISK) }
            if (attention.isNotEmpty()) {
                item { AttentionCard(attention, onSelect = { selected = it }) }
            }

            uiState.groups.forEach { group ->
                item(key = "header_${group.id}") {
                    Column(modifier = Modifier.padding(top = HangryTokens.Spacing.m, bottom = 2.dp)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium, color = LocalHangryTokens.current.textPrimary)
                        Text(group.description, style = MaterialTheme.typography.bodySmall, color = LocalHangryTokens.current.textSecondary)
                    }
                }
                items(group.metrics, key = { it.id }) { metric ->
                    MetricCard(metric = metric, onClick = { selected = metric })
                }
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

@Composable
private fun SummaryCard(state: BodyMetricsUiState, onUpdateMeasurements: () -> Unit) {
    val tokens = LocalHangryTokens.current
    val input = state.input
    val chips = buildList {
        input.heightCm?.let { add("Height ${fmt(it, 0)} cm") }
        input.weightKg?.let { add("Weight ${fmt(it, 1)} kg") }
        input.age?.let { add("Age $it") }
        input.bodyFatPercent?.let { bf ->
            val date = state.bodyFatScanDate?.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            add("Body fat ${fmt(bf, 1)}%" + (date?.let { " · $it" } ?: ""))
        }
        input.waistCm?.let { add("Waist ${fmt(it, 1)} cm") }
        input.hipCm?.let { add("Hips ${fmt(it, 1)} cm") }
        input.chestCm?.let { add("Chest ${fmt(it, 1)} cm") }
        input.neckCm?.let { add("Neck ${fmt(it, 1)} cm") }
    }
    val total = state.allMetrics.size
    HangryCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.availableCount}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )
            Text(
                " of $total metrics calculated",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(chips.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        }
        val missing = state.missingInputs
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Add ${missing.take(3).joinToString(", ").lowercase()} to unlock the rest.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onUpdateMeasurements, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Update measurements")
        }
    }
}

@Composable
private fun AttentionCard(metrics: List<BodyMetric>, onSelect: (BodyMetric) -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text("Worth a look", style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        Spacer(Modifier.height(6.dp))
        metrics.forEach { metric ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(metric) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(metricToneColor(metric.tone), RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Text(metric.name, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                Text(metric.status.orEmpty(), style = MaterialTheme.typography.labelMedium, color = metricToneColor(metric.tone))
            }
        }
    }
}

@Composable
private fun MetricCard(metric: BodyMetric, onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    HangryCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metric.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (metric.isAvailable) tokens.textPrimary else tokens.textSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.Info, contentDescription = "About ${metric.name}", tint = tokens.textMuted, modifier = Modifier.size(14.dp))
                }
                if (metric.isAvailable) {
                    metric.status?.let { MetricStatusChip(it, metric.tone, modifier = Modifier.padding(top = 4.dp)) }
                } else {
                    Text(
                        text = if (metric.missingInputs.isEmpty()) "Not available" else "Add ${metric.missingInputs.joinToString(" & ").lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    metric.displayValue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (metric.isAvailable) tokens.textPrimary else tokens.textMuted
                )
                if (metric.unit.isNotEmpty() && metric.displayValue != "—") {
                    Text(
                        " ${metric.unit}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
        if (metric.isAvailable && metric.bands.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MetricRangeBar(metric)
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
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            Column {
                Text(metric.name, style = MaterialTheme.typography.titleLarge, color = tokens.textPrimary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        metric.displayValue,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (metric.isAvailable) tokens.textPrimary else tokens.textMuted
                    )
                    if (metric.unit.isNotEmpty() && metric.displayValue != "—") {
                        Text(" ${metric.unit}", style = MaterialTheme.typography.titleMedium, color = tokens.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                metric.status?.let { MetricStatusChip(it, metric.tone) }
            }

            if (!metric.isAvailable && metric.missingInputs.isNotEmpty()) {
                Surface(color = tokens.brandAccentContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Add ${metric.missingInputs.joinToString(", ").lowercase()} to see yours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textPrimary
                        )
                        TextButton(onClick = onUpdateMeasurements, contentPadding = PaddingValues(0.dp)) { Text("Update measurements") }
                    }
                }
            }

            if (metric.bands.isNotEmpty()) {
                if (metric.isAvailable) MetricRangeBar(metric)
                MetricBandTable(metric)
            }

            MetricInfoBlock("What it is", metric.info.whatItIs)
            MetricInfoBlock(if (metric.isAvailable) "How we calculated yours" else "How it's calculated", metric.info.howCalculated)
            MetricInfoBlock("Why it matters", metric.info.whyItMatters)
            MetricInfoBlock("Source", metric.info.source)
            Text(
                "A screening number, not a diagnosis. Talk to a health professional about any concern.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted
            )
        }
    }
}

private fun fmt(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
