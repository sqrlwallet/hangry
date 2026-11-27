package com.kevan.hangry.ui.breathing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.kevan.hangry.R
import com.kevan.hangry.domain.model.BreathingVideo
import com.kevan.hangry.domain.model.BreathingVideos
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens

/** Guided breathing as embedded YouTube videos: a player on top, the list of sessions below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingVideosScreen(
    initialKey: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalHangryTokens.current
    val accent = tokens.chartColors.sleep
    var selectedKey by rememberSaveable {
        mutableStateOf((BreathingVideos.fromKey(initialKey) ?: BreathingVideos.all.first()).key)
    }
    val selected = BreathingVideos.fromKey(selectedKey) ?: BreathingVideos.all.first()

    // These are long, watch-along sessions; don't let the display time out mid-video.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.coach_breathing_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.coach_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            YouTubePlayer(
                video = selected,
                modifier = Modifier
                    .padding(horizontal = HangryTokens.Spacing.m)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            )
            Spacer(Modifier.height(HangryTokens.Spacing.s))
            Text(
                text = selected.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.padding(horizontal = HangryTokens.Spacing.m)
            )
            Text(
                text = stringResource(R.string.coach_breathing_video_minutes, selected.minutes) + " · " + selected.description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(horizontal = HangryTokens.Spacing.m)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(HangryTokens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)
            ) {
                items(BreathingVideos.all, key = { it.key }) { video ->
                    VideoRow(
                        video = video,
                        isSelected = video.key == selected.key,
                        accent = accent,
                        onClick = { selectedKey = video.key }
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.coach_breathing_videos_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(top = HangryTokens.Spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: BreathingVideo, isSelected: Boolean, accent: Color, onClick: () -> Unit) {
    val tokens = LocalHangryTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accent.copy(alpha = 0.12f) else tokens.cardBackground,
        border = BorderStroke(1.dp, if (isSelected) accent.copy(alpha = 0.5f) else tokens.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(4.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (isSelected) {
                    Text(
                        text = stringResource(R.string.coach_breathing_now_playing),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.coach_breathing_video_minutes, video.minutes) + " · " + video.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * YouTube's embedded player in a WebView. The page is loaded with an https base URL so the
 * iframe sends a Referer; without one YouTube refuses to play embeds (error 153).
 * The player's full-screen button is supported by laying its view over the whole window.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(video: BreathingVideo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var fullscreen by remember { mutableStateOf<FullscreenView?>(null) }

    fun exitFullscreen() {
        val current = fullscreen ?: return
        fullscreen = null
        activity?.let { act ->
            (act.window.decorView as? ViewGroup)?.removeView(current.container)
            WindowCompat.getInsetsController(act.window, act.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        current.callback.onCustomViewHidden()
    }

    BackHandler(enabled = fullscreen != null) { exitFullscreen() }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    val act = activity ?: return callback.onCustomViewHidden()
                    if (fullscreen != null) exitFullscreen()
                    val container = FrameLayout(act).apply {
                        setBackgroundColor(AndroidColor.BLACK)
                        addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                    }
                    (act.window.decorView as? ViewGroup)?.addView(container, FrameLayout.LayoutParams(MATCH, MATCH))
                    WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                    fullscreen = FullscreenView(container, callback)
                }

                override fun onHideCustomView() = exitFullscreen()
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            exitFullscreen()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        update = { wv ->
            if (wv.tag != video.youtubeId) {
                wv.tag = video.youtubeId
                wv.loadDataWithBaseURL(
                    "https://${wv.context.packageName}",
                    embedHtml(video.youtubeId),
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        modifier = modifier
    )
}

private class FullscreenView(val container: View, val callback: WebChromeClient.CustomViewCallback)

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

private fun embedHtml(youtubeId: String): String = """
    <!DOCTYPE html>
    <html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}
    iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
    </head><body>
    <iframe src="https://www.youtube.com/embed/$youtubeId?playsinline=1&rel=0&modestbranding=1"
      referrerpolicy="strict-origin-when-cross-origin"
      allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
      allowfullscreen></iframe>
    </body></html>
""".trimIndent()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
