package com.kevan.hangry.ui.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevan.hangry.R
import com.kevan.hangry.data.report.DoctorReportGenerator
import com.kevan.hangry.domain.report.ReportPeriod
import com.kevan.hangry.domain.report.ReportSection
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Build a PDF of the user's health data for a doctor: choose what goes in, preview it, then share, save or print. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorReportScreen(
    viewModel: DoctorReportViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    val snackbar = remember { SnackbarHostState() }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.saveTo(it) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                context.getString(
                    when (event) {
                        DoctorReportEvent.Saved -> R.string.report_saved
                        DoctorReportEvent.SaveFailed -> R.string.report_save_error
                        DoctorReportEvent.GenerateFailed -> R.string.report_error
                    }
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.body_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val result = state.result
        val preview = remember(result?.file, result?.file?.lastModified()) { result?.let { PagePreviewRenderer(it.file) } }
        DisposableEffect(preview) { onDispose { preview?.close() } }
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.section)
        ) {
            item(key = "intro") {
                HangryCard(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.report_intro_title), style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    Spacer(Modifier.height(HangryTokens.Spacing.xs))
                    Text(stringResource(R.string.report_intro_body), style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                }
            }

            item(key = "period") {
                HangryCard(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.report_period_label))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ReportPeriod.entries.forEachIndexed { index, period ->
                            SegmentedButton(
                                selected = state.options.period == period,
                                onClick = { viewModel.setPeriod(period) },
                                shape = SegmentedButtonDefaults.itemShape(index, ReportPeriod.entries.size),
                                label = { Text(stringResource(period.label()), maxLines = 1) }
                            )
                        }
                    }
                    Spacer(Modifier.height(HangryTokens.Spacing.m))
                    OutlinedTextField(
                        value = state.options.patientName.orEmpty(),
                        onValueChange = viewModel::setName,
                        label = { Text(stringResource(R.string.report_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(HangryTokens.Spacing.s))
                    OutlinedTextField(
                        value = state.options.visitNotes.orEmpty(),
                        onValueChange = viewModel::setNotes,
                        label = { Text(stringResource(R.string.report_notes_label)) },
                        placeholder = { Text(stringResource(R.string.report_notes_placeholder)) },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(key = "sections") {
                HangryCard(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.report_sections_label))
                    state.offeredSections.forEach { section ->
                        val included = section in state.options.sections
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = HangryTokens.Spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(section.label()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = included, onCheckedChange = { viewModel.setSection(section, it) })
                        }
                    }
                }
            }

            item(key = "create") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = viewModel::generate,
                        enabled = !state.isGenerating,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(HangryTokens.Spacing.s))
                            Text(stringResource(R.string.report_creating))
                        } else {
                            Text(stringResource(if (result == null) R.string.report_create else R.string.report_recreate))
                        }
                    }
                    if (state.isStale) {
                        Spacer(Modifier.height(HangryTokens.Spacing.xs))
                        Text(stringResource(R.string.report_stale), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                    }
                }
            }

            if (result != null) {
                item(key = "actions") {
                    HangryCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            pluralStringResource(R.plurals.report_ready, result.pageCount, result.pageCount),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                        Spacer(Modifier.height(HangryTokens.Spacing.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { shareReport(context, result.file) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(HangryTokens.Spacing.xs))
                                Text(stringResource(R.string.report_share), maxLines = 1)
                            }
                            FilledTonalButton(onClick = { saveLauncher.launch(DoctorReportGenerator.fileName(result.report.end)) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(HangryTokens.Spacing.xs))
                                Text(stringResource(R.string.report_save), maxLines = 1)
                            }
                            OutlinedButton(onClick = { printReport(context, result.file) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(HangryTokens.Spacing.xs))
                                Text(stringResource(R.string.report_print), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(HangryTokens.Spacing.s))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(HangryTokens.Spacing.xs))
                            Text(stringResource(R.string.report_privacy), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                        }
                    }
                }

                // One item per page, so pages scrolled away let go of their bitmaps.
                if (preview != null) {
                    items(result.pageCount, key = { "page-${result.file.lastModified()}-$it" }) { index ->
                        ReportPage(preview, index, result.pageCount)
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(HangryTokens.Spacing.xl)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalHangryTokens.current.textMuted,
        modifier = Modifier.padding(bottom = HangryTokens.Spacing.s)
    )
}

private fun ReportPeriod.label(): Int = when (this) {
    ReportPeriod.DAYS_30 -> R.string.report_period_30
    ReportPeriod.DAYS_90 -> R.string.report_period_90
    ReportPeriod.MONTHS_6 -> R.string.report_period_182
    ReportPeriod.YEAR -> R.string.report_period_365
}

private fun ReportSection.label(): Int = when (this) {
    ReportSection.VITALS -> R.string.report_section_vitals
    ReportSection.LABS -> R.string.report_section_labs
    ReportSection.CONDITIONS -> R.string.report_section_conditions
    ReportSection.SUPPLEMENTS -> R.string.report_section_supplements
    ReportSection.SLEEP -> R.string.report_section_sleep
    ReportSection.ACTIVITY -> R.string.report_section_activity
    ReportSection.NUTRITION -> R.string.report_section_nutrition
    ReportSection.CYCLE -> R.string.report_section_cycle
}

/** A page as the doctor will see it, rendered on demand with the platform's PDF renderer. */
@Composable
private fun ReportPage(renderer: PagePreviewRenderer, index: Int, pageCount: Int) {
    val description = stringResource(R.string.report_preview_page, index + 1, pageCount)
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(renderer.aspectRatio)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.White)
            .border(BorderStroke(0.5.dp, LocalHangryTokens.current.cardBorder), shape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val page by produceState<ImageBitmap?>(null, renderer, index, widthPx) {
            value = renderer.render(index, widthPx)
        }
        val bitmap = page
        if (bitmap == null) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        else Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** [PdfRenderer] opens one page at a time and isn't thread-safe, so renders queue on a lock. */
private class PagePreviewRenderer(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()
    private var closed = false

    val aspectRatio: Float = renderer.openPage(0).use { it.width.toFloat() / it.height }

    suspend fun render(index: Int, widthPx: Int): ImageBitmap? = lock.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val width = widthPx.coerceIn(1, MAX_PREVIEW_WIDTH)
                val height = (width * page.height.toFloat() / page.width).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }

    fun close() {
        // Wait for a render in progress; closing under it would crash the renderer.
        kotlinx.coroutines.runBlocking {
            lock.withLock {
                closed = true
                renderer.close()
                descriptor.close()
            }
        }
    }

    private companion object {
        const val MAX_PREVIEW_WIDTH = 1080
    }
}

private fun reportUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun shareReport(context: Context, file: File) {
    val uri = reportUri(context, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_share_subject))
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Hands the finished PDF to the system print dialog as-is. */
private fun printReport(context: Context, file: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            runCatching {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }
    printManager.print(file.nameWithoutExtension, adapter, null)
}
