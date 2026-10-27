package com.kevan.hangry.ui.share

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevan.hangry.R
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.components.rememberHangryHaptics
import com.kevan.hangry.ui.dashboard.DashboardUiState
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import com.kevan.hangry.ui.theme.SurfaceVariantDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every card there's data for - today, streaks, Body Age and the trend charts - previewed as
 * the image that will be posted, each with its own Share button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    dashboardState: DashboardUiState,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    val cards = remember(dashboardState, state.trends) { ShareCards.all(context, dashboardState, state.trends) }
    val hasTrends = cards.any { it.chart != null }

    val scope = rememberCoroutineScope()
    val haptic = rememberHangryHaptics()
    var sharing by remember { mutableStateOf<String?>(null) }
    val share: (ShareCard) -> Unit = { card ->
        if (sharing == null) {
            haptic.tick()
            sharing = card.kind
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { ShareCardFiles.write(context, card) } }
                    .onSuccess { ShareCardFiles.share(context, it, card) }
                    .onFailure { Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show() }
                sharing = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.share_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = HangryTokens.Spacing.m, end = HangryTokens.Spacing.m, bottom = HangryTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
        ) {
            item(key = "intro") {
                Text(stringResource(R.string.share_screen_intro), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                Spacer(Modifier.height(HangryTokens.Spacing.m))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SharePeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = { viewModel.setPeriod(period) },
                            shape = SegmentedButtonDefaults.itemShape(index, SharePeriod.entries.size),
                            label = { Text(stringResource(if (period == SharePeriod.WEEK) R.string.share_period_7 else R.string.share_period_30), maxLines = 1) }
                        )
                    }
                }
            }
            items(cards, key = { it.kind }) { card ->
                ShareCardItem(card = card, isSharing = sharing == card.kind, onShare = { share(card) })
            }
            if (state.trends != null && !hasTrends) item(key = "more") {
                Text(stringResource(R.string.share_more_trends), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
            }
        }
    }
}

/** The card's preview image, its name and a Share button. */
@Composable
private fun ShareCardItem(card: ShareCard, isSharing: Boolean, onShare: () -> Unit) {
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    // Drawn off the main thread at half size; only cards scrolled into view are drawn.
    val preview by produceState<ImageBitmap?>(null, card) {
        value = withContext(Dispatchers.Default) {
            runCatching { ShareCardRenderer(context).render(card, PREVIEW_SCALE).asImageBitmap() }.getOrNull()
        }
    }
    HangryCard(modifier = Modifier.fillMaxWidth(), contentPadding = HangryTokens.Spacing.s) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ShareCardRenderer.WIDTH / ShareCardRenderer.HEIGHT.toFloat())
                .clip(RoundedCornerShape(HangryTokens.CornerRadii.medium))
                .background(SurfaceVariantDark),
            contentAlignment = Alignment.Center
        ) {
            preview?.let {
                Image(bitmap = it, contentDescription = card.headline, modifier = Modifier.fillMaxSize())
            } ?: CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = HangryTokens.Spacing.s, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, modifier = Modifier.weight(1f))
            val description = stringResource(R.string.share_cd_card, card.title)
            Button(onClick = onShare, enabled = !isSharing, modifier = Modifier.semantics { contentDescription = description }) {
                if (isSharing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_action))
            }
        }
    }
}

private const val PREVIEW_SCALE = 0.5f
