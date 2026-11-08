package com.clipforge.mobile

import android.content.Intent
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import android.text.format.Formatter
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.transformer.*
import androidx.media3.effect.Presentation
import androidx.media3.common.Effect
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Official multilingual Whisper weights; .en models cannot transcribe Tagalog. */
enum class OfflineModel(val filename: String, val approxMb: Int, val sha256: String) {
    TINY("ggml-tiny.bin", 78, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21")
}
/** Check local space before decoding long audio to PCM16 mono WAV. */
fun ensureAudioWorkspace(cache: File, durationMs: Long) {
    if (durationMs <= 0L) return
    val sampleRateBudget = 192_000L
    val estimatedBytes = (durationMs / 1000L + 1L) * sampleRateBudget * 2L + 64L * 1024L * 1024L
    val available = StatFs(cache.absolutePath).availableBytes
    require(available >= estimatedBytes) {
        "Not enough free storage for long-video audio. Estimated safe requirement: ${estimatedBytes / (1024L * 1024L)} MB; available: ${available / (1024L * 1024L)} MB. Free space or use shorter highlights."
    }
}

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
fun downloadOfflineModel(destination: File, model: OfflineModel, progress: (Int) -> Unit) {
    destination.parentFile?.mkdirs()
    val temp = File(destination.parentFile, destination.name + ".partial")
    val url = URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + model.filename)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 20000
        readTimeout = 45000
        instanceFollowRedirects = true
    }
    try {
        connection.connect()
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        val expected = connection.contentLengthLong
        var bytes = 0L
        connection.inputStream.use { input ->
            temp.outputStream().buffered().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes += count
                    if (bytes > 300_000_000L) error("Model exceeds download safety limit")
                    output.write(buffer, 0, count)
                    if (expected > 0) progress((bytes * 100L / expected).toInt().coerceIn(0, 99))
                }
            }
        }
        if (sha256Of(temp) != model.sha256) error("Model checksum mismatch")
        if (destination.exists()) destination.delete()
        if (!temp.renameTo(destination)) error("Could not finalize model")
        progress(100)
    } finally {
        connection.disconnect()
        if (temp.exists()) temp.delete()
    }
}

data class HighlightDraft(val startMs: Long, val endMs: Long, val approved: Boolean = false, val visualScore: Int? = null, val hook: String = "", val caption: String = "", val effect: String = "None", val captionStartMs: Long = 0L, val captionEndMs: Long = 0L)

/** Transparent baseline ranking. These scores describe segment duration and coverage,
 * not measured speech, visual action, or predicted engagement. */
data class DraftScore(val clarity: Int, val context: Int, val pacing: Int, val engagement: Int) {
    val balanced: Int get() = (clarity + context + pacing + engagement) / 4
}
/** Quality-first candidate coverage: overlapping 20s windows distributed across
 * the entire source. This does not inspect video/audio content. */
fun generateQualityCandidates(totalMs: Long): List<HighlightDraft> {
    if (totalMs <= 0L) return emptyList()
    val clipMs = minOf(20000L, totalMs)
    if (totalMs <= clipMs) return listOf(HighlightDraft(0L, totalMs))
    val lastStart = totalMs - clipMs
    val count = minOf(15, (lastStart / 10000L + 2L).toInt())
    return (0 until count).map { index ->
        val startMs = (lastStart * index / (count - 1)).coerceIn(0L, lastStart)
        HighlightDraft(startMs, startMs + clipMs)
    }.distinctBy { it.startMs }
}
fun baselineDraftScore(draft: HighlightDraft, totalMs: Long): DraftScore {
    val length = (draft.endMs - draft.startMs).coerceAtLeast(0L)
    val clarity = if (length >= 10000L) 75 else 35
    val context = if (draft.startMs >= 5000L && draft.endMs < totalMs - 3000L) 65 else 45
    val pacing = when (length) {
        in 12000L..30000L -> 80
        in 5000L..45000L -> 60
        else -> 30
    }
    return DraftScore(clarity, context, pacing, 50)
}

/** Static, bold, high-contrast caption card. Keywords prefixed with # are yellow.
 * This is a persistent overlay, not speech-synchronized transcription. */
fun makeCaptionOverlay(hook: String, caption: String, subtitleSize: Float = 39f, subtitleColor: Int = Color.WHITE): Bitmap {
    val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(5f, 2f, 3f, Color.BLACK)
    }
    val lines = listOf(hook.take(65), caption.take(100)).filter { it.isNotBlank() }
    lines.forEachIndexed { index, line ->
        val words = line.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val next = if (current.isEmpty()) word else "$current $word"
            if (next.length > 22 && current.isNotEmpty()) { chunks.add(current); current = word }
            else current = next
        }
        if (current.isNotBlank()) chunks.add(current)
        paint.textSize = if (index == 0) 45f else subtitleSize
        chunks.take(3).forEachIndexed { row, text ->
            val y = (if (index == 0) 210 else 970) + row * 55
            val clean = text.replace("#", "")
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 9f
            canvas.drawText(clean, 360f, y.toFloat(), paint)
            paint.style = Paint.Style.FILL
            paint.color = if ('#' in text) Color.YELLOW else if (index == 0) Color.WHITE else subtitleColor
            canvas.drawText(clean, 360f, y.toFloat(), paint)
        }
    }
    return bitmap
}

/** Video timestamps are relative to the exported trimmed clip.
 * Hook stays visible; subtitle appears only during the chosen time interval. */
fun timedCaptionOverlay(
    hook: String,
    caption: String,
    startMs: Long,
    endMs: Long,
    trimStartMs: Long
): BitmapOverlay {
    val withCaption = makeCaptionOverlay(hook, caption)
    val withoutCaption = makeCaptionOverlay(hook, "")
    val fromUs = (startMs - trimStartMs).coerceAtLeast(0L) * 1000L
    val untilUs = (endMs - trimStartMs).coerceAtLeast(0L) * 1000L
    return object : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap =
            if (untilUs <= fromUs || presentationTimeUs in fromUs until untilUs) withCaption else withoutCaption
    }
}

fun segmentSubtitleOverlay(hook: String, segments: List<WhisperNative.Segment>, trimStartMs: Long, subtitleSize: Float, subtitleColor: Int): BitmapOverlay {
    val empty = makeCaptionOverlay(hook, "")
    // Retain only the current subtitle bitmap, independent of transcript length.
    var cachedIndex = -1
    var cachedBitmap: Bitmap? = null
    return object : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val absoluteMs = SubtitleTimeline.sourcePositionMs(trimStartMs, presentationTimeUs)
            val index = SubtitleTimeline.activeSegmentIndex(segments, absoluteMs)
            if (index < 0) return empty
            if (cachedIndex != index) {
                cachedBitmap = makeCaptionOverlay(hook, segments[index].text, subtitleSize, subtitleColor)
                cachedIndex = index
            }
            return cachedBitmap ?: empty
        }
    }
}

/** Samples reduced-resolution frames locally. Frame differences are an activity
 * proxy only: they do not recognize objects, speech or audience engagement. */
fun measureVisualActivity(retriever: MediaMetadataRetriever, startMs: Long, endMs: Long): Int? {
    var previous: IntArray? = null
    var differences = 0.0
    var comparisons = 0
    val sampleCount = 5
    for (i in 0 until sampleCount) {
        val timeMs = startMs + (endMs - startMs) * i / sampleCount
        val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
        val small = Bitmap.createScaledBitmap(frame, 16, 16, false)
        val pixels = IntArray(256)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (small !== frame) small.recycle()
        frame.recycle()
        val old = previous
        if (old != null) {
            var diff = 0L
            for (j in pixels.indices) {
                val a = pixels[j]
                val b = old[j]
                diff += kotlin.math.abs((a shr 16 and 255) - (b shr 16 and 255))
                diff += kotlin.math.abs((a shr 8 and 255) - (b shr 8 and 255))
                diff += kotlin.math.abs((a and 255) - (b and 255))
            }
            differences += diff.toDouble() / (256.0 * 3.0 * 255.0)
            comparisons++
        }
        previous = pixels
    }
    return if (comparisons == 0) null else (differences / comparisons * 250.0).toInt().coerceIn(0, 100)
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorScreen() }
    }

    @Composable
    private fun EditorScreen() {
        var video by remember { mutableStateOf<Uri?>(null) }
        var duration by remember { mutableLongStateOf(0L) }
        var start by remember { mutableFloatStateOf(0f) }
        var end by remember { mutableFloatStateOf(1f) }
        var selectedTab by remember { mutableStateOf(StudioTab.STUDIO) }
        var studioEditing by remember { mutableStateOf(false) }
        var speechAdvanced by remember { mutableStateOf(false) }
        var showTranscriptNotes by remember { mutableStateOf(false) }
        var advancedImport by remember { mutableStateOf(false) }
        var previewPlaying by remember { mutableStateOf(false) }
        var trimInText by remember { mutableStateOf("00:00.000") }
        var trimOutText by remember { mutableStateOf("00:00.000") }
        var fillFrame by remember { mutableStateOf(false) }
        var vertical by remember { mutableStateOf(true) }
        var mute by remember { mutableStateOf(false) }
        var editingMode by remember { mutableStateOf("Manual Editor") }
        var approved by remember { mutableStateOf(false) }
        var drafts by remember { mutableStateOf<List<HighlightDraft>>(emptyList()) }
        var selectedDraft by remember { mutableIntStateOf(-1) }
        var hookText by remember { mutableStateOf("") }
        var captionText by remember { mutableStateOf("") }
        var effectChoice by remember { mutableStateOf("None") }
        var captionFrom by remember { mutableStateOf("0") }
        var captionTo by remember { mutableStateOf("5") }
        var previewPosition by remember { mutableLongStateOf(0L) }
        var status by remember { mutableStateOf("Select a video to begin") }
        var link by remember { mutableStateOf("") }
        var downloading by remember { mutableStateOf(false) }
        var exporting by remember { mutableStateOf(false) }
        var lastExportUri by remember { mutableStateOf<Uri?>(null) }
        val exportScope = rememberCoroutineScope()
        var activeExportToken by remember { mutableStateOf<Any?>(null) }
        var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
        var exportFile by remember { mutableStateOf<File?>(null) }
        var exportPercent by remember { mutableIntStateOf(0) }
        var analyzing by remember { mutableStateOf(false) }
        var audioPreparing by remember { mutableStateOf(false) }
        var transcribing by remember { mutableStateOf(false) }
        var speechPhase by remember { mutableStateOf("") }
        var speechPercent by remember { mutableIntStateOf(-1) }
        var speechElapsedSeconds by remember { mutableLongStateOf(0L) }

        var transcript by remember { mutableStateOf("") }
        var sourceTranscriptSegments by remember { mutableStateOf<List<WhisperNative.Segment>>(emptyList()) }
        var transcriptSegments by remember { mutableStateOf<List<WhisperNative.Segment>>(emptyList()) }
        var subtitleSegments by remember { mutableStateOf<List<WhisperNative.Segment>>(emptyList()) }
        var subtitleDraftIndex by remember { mutableIntStateOf(-1) }
        var subtitlePage by remember { mutableIntStateOf(0) }
        var subtitleSize by remember { mutableFloatStateOf(39f) }
        var subtitleYellow by remember { mutableStateOf(false) }
        var batchRunning by remember { mutableStateOf(false) }
        var batchProgress by remember { mutableIntStateOf(0) }
        var batchTotal by remember { mutableIntStateOf(0) }
        LaunchedEffect(transcribing, batchRunning) {
            speechElapsedSeconds = 0
            val began = android.os.SystemClock.elapsedRealtime()
            while (transcribing || batchRunning) {
                speechElapsedSeconds = (android.os.SystemClock.elapsedRealtime() - began) / 1000
                delay(1000)
            }
        }
        val reportSpeechProgress: (String, Int) -> Unit = { phase, percent ->
            runOnUiThread { speechPhase = phase; speechPercent = percent }
        }
        val batchCancel = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        var batchResults by remember { mutableStateOf<Map<Int, List<WhisperNative.Segment>>>(emptyMap()) }
        var modelDownloading by remember { mutableStateOf(false) }
        var modelProgress by remember { mutableIntStateOf(0) }
        var modelReady by remember { mutableStateOf(File(filesDir, "models/ggml-tiny.bin").exists()) }
        val campaignPrefs = remember { getSharedPreferences("campaign", MODE_PRIVATE) }
        var showTracker by remember { mutableStateOf(false) }
        var campaignEnabled by remember { mutableStateOf(campaignPrefs.getBoolean("enabled", false)) }
        var campaignTags by remember { mutableStateOf(campaignPrefs.getString("tags", "") ?: "") }
        var campaignCaption by remember { mutableStateOf(campaignPrefs.getString("caption", "") ?: "") }
        var campaignMin by remember { mutableStateOf(campaignPrefs.getString("min", "15") ?: "15") }
        var campaignMax by remember { mutableStateOf(campaignPrefs.getString("max", "30") ?: "30") }
        var campaignSource by remember { mutableStateOf(campaignPrefs.getString("source", "0") ?: "0") }
        var sourceVerified by remember { mutableStateOf(false) }
        var rightsVerified by remember { mutableStateOf(false) }
        var projectLoading by remember { mutableStateOf(true) }
        var projectMessage by remember { mutableStateOf("Checking saved project…") }
        var expectedRestoredDuration by remember { mutableLongStateOf(0L) }
        val busy = projectLoading || exporting || analyzing || transcribing || batchRunning || audioPreparing || downloading
        val campaignIssues = if (campaignEnabled) CampaignRules(campaignTags,
            campaignMin.toLongOrNull() ?: -1, campaignMax.toLongOrNull() ?: -1,
            campaignSource.toLongOrNull() ?: -1).issues(campaignCaption,
            (duration * end).toLong() - (duration * start).toLong(), sourceVerified, rightsVerified) else emptyList()
        LaunchedEffect(campaignEnabled, campaignTags, campaignCaption, campaignMin, campaignMax, campaignSource) {
            campaignPrefs.edit().putBoolean("enabled", campaignEnabled).putString("tags", campaignTags)
                .putString("caption", campaignCaption).putString("min", campaignMin)
                .putString("max", campaignMax).putString("source", campaignSource).apply()
            approved = false
        }
        val player = remember { ExoPlayer.Builder(this).build() }
        LaunchedEffect(effectChoice, vertical, fillFrame) {
            val previewEffects = creativeEffects(effectChoice).toMutableList()
            if (vertical) previewEffects.add(Presentation.createForAspectRatio(9f / 16f,
                if (fillFrame) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
            player.setVideoEffects(previewEffects)
        }
        LaunchedEffect(start, end, duration) {
            trimInText = Timecode.precise((duration * start).toLong())
            trimOutText = Timecode.precise((duration * end).toLong())
        }
        DisposableEffect(player) { onDispose { player.release() } }
        DisposableEffect(Unit) {
            onDispose { batchCancel.set(true); activeExportToken = null; if (activeTransformer != null) { activeTransformer?.cancel(); exportFile?.delete() } }
        }
        LaunchedEffect(exporting) {
            val holder = ProgressHolder()
            while (exporting) {
                if (activeTransformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    exportPercent = holder.progress
                }
                delay(300)
            }
        }
        DisposableEffect(busy) {
            if (busy) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        var pendingSrt by remember { mutableStateOf("") }
        val subtitleSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { destination ->
            if (destination != null) {
                try {
                    val stream = contentResolver.openOutputStream(destination) ?: error("Unable to open subtitle destination")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(pendingSrt) }
                    status = "Timed SRT saved"
                } catch (e: Exception) { status = "Subtitle save failed: ${e.message}" }
            }
            pendingSrt = ""
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {}
                if (busy) return@rememberLauncherForActivityResult
                batchResults = emptyMap()
                sourceVerified = false
                rightsVerified = false
                effectChoice = "None"
                hookText = ""
                captionText = ""
                expectedRestoredDuration = 0
                video = uri
                transcript = ""
                sourceTranscriptSegments = emptyList()
                transcriptSegments = emptyList()
                subtitleSegments = emptyList()
                subtitleDraftIndex = -1
                drafts = emptyList()
                selectedDraft = -1
                approved = false
                start = 0f
                end = 1f
                duration = 0L
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
                status = "Video loaded"
            }
        }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val actual = player.duration.coerceAtLeast(0)
                        if (expectedRestoredDuration > 0 && kotlin.math.abs(expectedRestoredDuration - actual) > 1_000) {
                            drafts = emptyList()
                            selectedDraft = -1
                            subtitleDraftIndex = -1
                            subtitleSegments = emptyList()
                            transcriptSegments = emptyList()
                            sourceTranscriptSegments = emptyList()
                            batchResults = emptyMap()
                            approved = false
                            start = 0f
                            end = 1f
                            status = "Source duration changed. Saved drafts and subtitles cleared; review the video again."
                        }
                        expectedRestoredDuration = 0
                        duration = actual
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    approved = false
                    status = "Unable to read video. Reimport it if it was moved or access permission expired: ${error.message}"
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        val projectFile = remember { File(filesDir, "projects/last-project.properties") }
        LaunchedEffect(Unit) {
            try {
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ProjectStore.read(projectFile) }
                if (saved != null) {
                    val source = Uri.parse(saved.source)
                    // Check access before installing a saved editor state.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        contentResolver.openAssetFileDescriptor(source, "r")?.use { } ?: error("Saved source is unavailable")
                    }
                    video = source
                    duration = saved.durationMs
                    expectedRestoredDuration = saved.durationMs
                    start = saved.startMs.toFloat() / saved.durationMs
                    end = saved.endMs.toFloat() / saved.durationMs
                    editingMode = saved.mode
                    fillFrame = saved.fillFrame
                    vertical = saved.vertical
                    mute = saved.mute
                    drafts = saved.drafts.map { it.copy(approved = false) }
                    selectedDraft = saved.selectedDraft
                    hookText = saved.hook
                    captionText = saved.caption
                    effectChoice = saved.effect
                    subtitleSize = saved.subtitleSize
                    subtitleYellow = saved.subtitleYellow
                    subtitleDraftIndex = saved.subtitleDraftIndex
                    transcriptSegments = saved.currentSegments
                    sourceTranscriptSegments = saved.sourceSegments
                    subtitleSegments = if (saved.subtitleDraftIndex >= 0) saved.currentSegments else emptyList()
                    batchResults = saved.draftSegments
                    transcript = saved.currentSegments.joinToString("\n") { it.text }
                    val chosen = saved.drafts.getOrNull(saved.selectedDraft)
                    captionFrom = ((chosen?.captionStartMs ?: 0L) / 1000L).toString()
                    captionTo = ((chosen?.captionEndMs ?: 0L) / 1000L).toString()
                    approved = false
                    sourceVerified = false
                    rightsVerified = false
                    player.setVideoEffects(creativeEffects(saved.effect))
                    player.setMediaItem(MediaItem.fromUri(source))
                    player.seekTo(saved.startMs)
                    player.playWhenReady = false
                    player.prepare()
                    projectMessage = "Local project restored. Review again before export."
                    status = "Restored project; export approval and campaign confirmations were cleared"
                } else projectMessage = "Edits save locally after a short pause."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                projectMessage = "Saved project could not be restored: ${error.message}. Reimport the original video."
            } finally { projectLoading = false }
        }
        val projectSnapshot = if (video != null && duration > 0) EditorProject(
            source = video.toString(), durationMs = duration,
            startMs = (duration * start).toLong(), endMs = (duration * end).toLong(),
            mode = editingMode, vertical = vertical, mute = mute,
            drafts = drafts.map { it.copy(approved = false) }, selectedDraft = selectedDraft,
            hook = hookText, caption = captionText, effect = effectChoice,
            subtitleSize = subtitleSize, subtitleYellow = subtitleYellow, subtitleDraftIndex = subtitleDraftIndex,
            currentSegments = transcriptSegments, sourceSegments = sourceTranscriptSegments,
            draftSegments = batchResults, fillFrame = fillFrame
        ) else null
        LaunchedEffect(projectSnapshot, projectLoading) {
            if (projectSnapshot != null && !projectLoading) {
                delay(1000)
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ProjectStore.write(projectFile, projectSnapshot)
                    }
                    projectMessage = "Project saved on this phone. Review approval is never restored."
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    projectMessage = "Project save failed: ${error.message}"
                }
            }
        }
        LaunchedEffect(player, duration, start, end, video) {
            while (true) {
                if (duration > 0 && player.isPlaying && player.currentPosition >= (duration * end).toLong()) {
                    player.pause()
                    player.seekTo((duration * start).toLong())
                }
                previewPlaying = player.isPlaying
                previewPosition = player.currentPosition.coerceAtLeast(0L)
                delay(150)
            }
        }
        val previewHeight = if (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 760) 170.dp else 230.dp
        val previewPanel: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (video == null) {
                    Box(Modifier.fillMaxWidth()) {
                        StudioEmptyPreview()
                        FilledTonalButton(enabled = !busy, onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { Text("Choose a video") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selectedDraft >= 0) "CLIP ${(selectedDraft + 1).toString().padStart(2, '0')}" else "SOURCE PREVIEW", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StudioBadge(if (vertical) if (fillFrame) "9:16 · CROP" else "9:16 · FIT" else "SOURCE", false)
                    }
                Box(modifier = Modifier.fillMaxWidth().height(previewHeight).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxHeight().aspectRatio(if (vertical) 9f / 16f else 16f / 9f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (editingMode == "AI Draft" && selectedDraft in drafts.indices) {
                        val activeSegment = if (subtitleDraftIndex == selectedDraft) {
                            subtitleSegments.getOrNull(SubtitleTimeline.activeSegmentIndex(subtitleSegments, previewPosition))
                        } else null
                        val fallbackCaption = if (subtitleDraftIndex == selectedDraft && subtitleSegments.isNotEmpty()) "" else {
                            val draft = drafts[selectedDraft]
                            if (draft.captionEndMs <= draft.captionStartMs ||
                                previewPosition in draft.captionStartMs until draft.captionEndMs) captionText else ""
                        }
                        val displayedCaption = activeSegment?.text ?: fallbackCaption
                        if (hookText.isNotBlank() || displayedCaption.isNotBlank()) {
                            val previewBitmap = remember(hookText, displayedCaption, subtitleSize, subtitleYellow) {
                                makeCaptionOverlay(hookText, displayedCaption, subtitleSize,
                                    if (subtitleYellow) Color.YELLOW else Color.WHITE).asImageBitmap()
                            }
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Preview of time-synchronized subtitles",
                                modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                }
                }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(enabled = !busy, onClick = { if (player.isPlaying) player.pause() else player.play() }) { Text(if (previewPlaying) "Pause" else "Play") }
                        TextButton(enabled = !busy, onClick = { player.seekTo((duration * start).toLong()); player.play() }) { Text("Restart clip") }
                        Spacer(Modifier.weight(1f))
                        Text("${Timecode.format(previewPosition)} / ${Timecode.format(duration)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        ClipForgeTheme {
            Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = { StudioHeader(BuildConfig.VERSION_NAME) },
                    bottomBar = {
                        Column {
                            StudioActivity(
                                message = if (modelDownloading) "Downloading speech model · $modelProgress%" else if (transcribing || batchRunning) "$speechPhase · ${speechElapsedSeconds}s elapsed" else status,
                                active = busy || modelDownloading,
                                percent = if (modelDownloading) modelProgress else if (exporting) exportPercent else if (transcribing || batchRunning) speechPercent else -1)
                            StudioNavigation(selectedTab) { selectedTab = it }
                        }
                    }
                ) { innerPadding ->
                    Column(Modifier.fillMaxSize().padding(innerPadding)) {
                        if (selectedTab != StudioTab.CAMPAIGN) previewPanel()
                        key(selectedTab, studioEditing) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                StudioIntro(selectedTab)
                                when (selectedTab) {
                                    StudioTab.STUDIO -> {
                                        StudioCard("Source footage", if (video == null) "Choose a video to start your project." else "${Timecode.format(duration)} source · saved locally") {
                Button(enabled = !busy, onClick = { picker.launch(arrayOf("video/*")) }) { Text(if (video == null) "Import video" else "Replace source video") }
                TextButton(enabled = !busy, onClick = { advancedImport = !advancedImport }) { Text(if (advancedImport) "Hide direct-link import" else "Import from a direct link") }
                if (advancedImport) {
                OutlinedTextField(enabled = !busy, value = link, onValueChange = { link = it }, label = { Text("Direct HTTPS MP4 link (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = !busy && link.startsWith("https://"), onClick = {
                    downloading = true
                    status = "Downloading direct video file…"
                    val address = link
                    Thread {
                        try {
                            val output = LocalVideoDownload.fetch(address, File(filesDir, "imports"))
                            runOnUiThread {
                                transcript = ""
                                sourceTranscriptSegments = emptyList()
                                transcriptSegments = emptyList()
                                subtitleSegments = emptyList()
                                subtitleDraftIndex = -1
                                batchResults = emptyMap()
                                sourceVerified = false
                                rightsVerified = false
                                effectChoice = "None"
                                hookText = ""
                                captionText = ""
                                expectedRestoredDuration = 0
                                video = Uri.fromFile(output)
                                drafts = emptyList()
                                selectedDraft = -1
                                approved = false
                                start = 0f
                                end = 1f
                                duration = 0L
                                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(output)))
                                player.prepare()
                                status = "Downloaded video ready"
                                downloading = false
                            }
                        } catch (e: Exception) {
                            runOnUiThread { downloading = false; status = "Download failed: ${e.message}" }
                        }
                    }.start()
                }) { Text(if (downloading) "Downloading…" else "Import direct link") }
                Text("Links to webpages (TikTok/YouTube posts) are not direct video files; use authorized downloads.", style = MaterialTheme.typography.bodySmall)
                }

                                        }
                                        if (video != null) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(selected = !studioEditing, onClick = { studioEditing = false }, label = { Text("Highlights") })
                                                FilterChip(selected = studioEditing, onClick = { studioEditing = true }, label = { Text("Edit & style") })
                                            }
                                            if (!studioEditing) {
                Text("Editing mode", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(enabled = !busy, selected = editingMode == "Manual Editor", onClick = { editingMode = "Manual Editor"; selectedDraft = -1; effectChoice = "None"; approved = false }, label = { Text("Manual Editor") })
                    FilterChip(enabled = !busy, selected = editingMode == "AI Draft", onClick = { editingMode = "AI Draft"; approved = false; selectedDraft = -1 }, label = { Text("AI Draft") })
                }
                if (editingMode == "AI Draft") {
                    Text("Start with up to 15 coverage suggestions, or use speech highlights after transcription. Each clip stays in review until you approve it.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = duration >= 1000L && !busy, onClick = {
                        approved = false
                        batchResults = emptyMap()
                        subtitleSegments = emptyList()
                        subtitleDraftIndex = -1
                        drafts = generateQualityCandidates(duration)
                        selectedDraft = 0
                        hookText = ""
                        captionText = ""
                        effectChoice = "None"
                        start = 0f
                        end = drafts.first().endMs.toFloat() / duration.toFloat()
                        status = "${drafts.size} draft segments created. These are overlapping coverage candidates, NOT AI-ranked highlights."
                        player.seekTo(0)
                    }) { Text("Suggest coverage clips") }
                }
                if (editingMode == "AI Draft" && drafts.isNotEmpty()) {
                    Button(enabled = !busy && video != null, onClick = {
                        val source = video ?: return@Button
                        analyzing = true
                        status = "Sampling video frames locally…"
                        val snapshot = drafts
                        Thread {
                            try {
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(this@MainActivity, source)
                                    val measured = snapshot.map { draft ->
                                        draft.copy(visualScore = measureVisualActivity(retriever, draft.startMs, draft.endMs))
                                    }
                                    runOnUiThread {
                                        drafts = measured
                                        analyzing = false
                                        status = "Offline visual activity measured. No media uploaded."
                                    }
                                } finally { retriever.release() }
                            } catch (e: Exception) {
                                runOnUiThread { analyzing = false; status = "Visual analysis failed: ${e.message}" }
                            }
                        }.start()
                    }) { Text(if (analyzing) "Analyzing…" else "Measure frame activity") }
                    Text("Visual activity measures frame changes, not semantic scene quality. Speech ranking is available after transcription.", style = MaterialTheme.typography.bodySmall)
                    Text("Your clip shortlist", style = MaterialTheme.typography.titleMedium)
                    Text("Coverage scores reflect duration and position. They are not predictions of views or engagement.", style = MaterialTheme.typography.bodySmall)
                    drafts.withIndex().sortedByDescending { (baselineDraftScore(it.value, duration).balanced * 3 + (it.value.visualScore ?: 50)) / 4 }.forEach { (index, draft) ->
                        val active = selectedDraft == index
                        ClipReviewCard(index = index, draft = draft, active = active,
                            score = (baselineDraftScore(draft, duration).balanced * 3 + (draft.visualScore ?: 50)) / 4,
                            enabled = !busy,
                            onClick = {
                                selectedDraft = index
                                start = draft.startMs.toFloat() / duration.toFloat()
                                end = draft.endMs.toFloat() / duration.toFloat()
                                val matching = batchResults[index] ?: (sourceTranscriptSegments.ifEmpty { transcriptSegments }).filter { it.startMs >= draft.startMs && it.startMs < draft.endMs }
                                transcriptSegments = matching
                                subtitleSegments = matching
                                subtitleDraftIndex = if (matching.isNotEmpty()) index else -1
                                approved = false
                                hookText = draft.hook
                                captionText = draft.caption
                                effectChoice = draft.effect
                                captionFrom = (draft.captionStartMs / 1000L).toString()
                                captionTo = (draft.captionEndMs / 1000L).toString()
                                player.seekTo(draft.startMs)
                                player.play()
                            }
                        )
                    }
                }

                                                if (selectedDraft in drafts.indices) Button(enabled = !busy, onClick = { studioEditing = true }) { Text("Edit selected clip") }
                                                OutlinedButton(enabled = !busy, onClick = { selectedTab = StudioTab.CAPTIONS }) { Text("Open speech tools") }
                                            } else {
                                                StudioCard("Trim & framing", "Set an exact range, then choose how the frame fills the canvas.") {
                                                    Text("IN · ${Timecode.precise((duration * start).toLong())}", style = MaterialTheme.typography.bodySmall)
                                                    Slider(value = start, onValueChange = { value ->
                                                        val minimum = minOf(1000L, duration).toFloat() / duration.coerceAtLeast(1)
                                                        val changed = value.coerceIn(0f, (end - minimum).coerceAtLeast(0f))
                                                        start = changed; approved = false
                                                        if (selectedDraft in drafts.indices) drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(startMs = (duration * changed).toLong(), approved = false) }
                                                    }, valueRange = 0f..1f, enabled = duration > 0 && !busy)
                                                    Text("OUT · ${Timecode.precise((duration * end).toLong())}", style = MaterialTheme.typography.bodySmall)
                                                    Slider(value = end, onValueChange = { value ->
                                                        val minimum = minOf(1000L, duration).toFloat() / duration.coerceAtLeast(1)
                                                        val changed = value.coerceIn((start + minimum).coerceAtMost(1f), 1f)
                                                        end = changed; approved = false
                                                        if (selectedDraft in drafts.indices) drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(endMs = (duration * changed).toLong(), approved = false) }
                                                    }, valueRange = 0f..1f, enabled = duration > 0 && !busy)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedTextField(enabled = !busy, value = trimInText, onValueChange = { trimInText = it.take(16); approved = false }, label = { Text("In · mm:ss.mmm") }, singleLine = true, modifier = Modifier.weight(1f))
                                                        OutlinedTextField(enabled = !busy, value = trimOutText, onValueChange = { trimOutText = it.take(16); approved = false }, label = { Text("Out · mm:ss.mmm") }, singleLine = true, modifier = Modifier.weight(1f))
                                                    }
                                                    OutlinedButton(enabled = !busy, onClick = {
                                                        val from = Timecode.parse(trimInText)
                                                        val until = Timecode.parse(trimOutText)
                                                        if (from == null || until == null || !Timecode.validTrim(from, until, duration)) status = "Enter a valid range within the source video"
                                                        else {
                                                            start = from.toFloat() / duration; end = until.toFloat() / duration; approved = false
                                                            if (selectedDraft in drafts.indices) drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(startMs = from, endMs = until, approved = false) }
                                                            player.seekTo(from); status = "Trim updated. Review before export."
                                                        }
                                                    }) { Text("Apply exact range") }
                                                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled = !busy, checked = vertical, onCheckedChange = { vertical = it; approved = false; drafts = drafts.map { d -> d.copy(approved = false) } }); Spacer(Modifier.width(10.dp)); Text("Vertical 9:16 canvas") }
                                                    if (vertical) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        FilterChip(enabled = !busy, selected = !fillFrame, onClick = { fillFrame = false; approved = false; drafts = drafts.map { it.copy(approved = false) } }, label = { Text("Fit full image") })
                                                        FilterChip(enabled = !busy, selected = fillFrame, onClick = { fillFrame = true; approved = false; drafts = drafts.map { it.copy(approved = false) } }, label = { Text("Fill & crop") })
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled = !busy, checked = mute, onCheckedChange = { mute = it; approved = false; drafts = drafts.map { d -> d.copy(approved = false) } }); Spacer(Modifier.width(10.dp)); Text("Mute source audio") }
                                                }
                                                StudioCard("Hook & visual style") {
                if (editingMode == "AI Draft" && selectedDraft in drafts.indices) {
                    Text("Creative direction", style = MaterialTheme.typography.titleMedium)
                    Text("Give the opening a clear hook. Timed speech captions are edited in the Captions workspace.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(enabled = !busy, value = hookText, onValueChange = { value ->
                        hookText = value
                        approved = false
                        drafts = drafts.toMutableList().also { list -> list[selectedDraft] = list[selectedDraft].copy(hook = value, approved = false) }
                    }, label = { Text("Opening hook text") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(enabled = !busy, value = captionText, onValueChange = { value ->
                        captionText = value
                        approved = false
                        drafts = drafts.toMutableList().also { list -> list[selectedDraft] = list[selectedDraft].copy(caption = value, approved = false) }
                    }, label = { Text("Manual caption") }, modifier = Modifier.fillMaxWidth())
                    Text("Caption timing (seconds within source video)", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(enabled = !busy, value = captionFrom, onValueChange = { value ->
                            captionFrom = value.filter { it.isDigit() }.take(7)
                            val ms = (captionFrom.toLongOrNull() ?: 0L) * 1000L
                            drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(captionStartMs = ms, approved = false) }
                            approved = false
                        }, label = { Text("From (s)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(enabled = !busy, value = captionTo, onValueChange = { value ->
                            captionTo = value.filter { it.isDigit() }.take(7)
                            val ms = (captionTo.toLongOrNull() ?: 0L) * 1000L
                            drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(captionEndMs = ms, approved = false) }
                            approved = false
                        }, label = { Text("To (s)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Text("Timed speech captions take priority over a manual caption when available.", style = MaterialTheme.typography.bodySmall)
                    Text("Rendered effect", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("None", "Zoom", "Monochrome").forEach { option ->
                            FilterChip(enabled = !busy, selected = effectChoice == option, onClick = {
                                effectChoice = option
                                approved = false
                                drafts = drafts.toMutableList().also { list -> list[selectedDraft] = list[selectedDraft].copy(effect = option, approved = false) }
                            }, label = { Text(option) })
                        }
                    }
                }

                                                    if (selectedDraft !in drafts.indices || editingMode != "AI Draft") Text("Select a highlight to add a hook and manual caption.", style = MaterialTheme.typography.bodySmall)
                                                }
                                                Button(enabled = !busy, onClick = { selectedTab = StudioTab.EXPORT }) { Text("Review this edit") }
                                            }
                                        }
                                        Text(projectMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StudioTab.CAPTIONS -> {
                                        StudioCard("Private by design", "Your footage stays on this phone. The speech model is downloaded once; transcription runs locally.") { StudioBadge(if (modelReady) "MODEL READY" else "MODEL NEEDED") }
                                        TextButton(enabled = !batchRunning, onClick = { speechAdvanced = !speechAdvanced }) { Text(if (speechAdvanced) "Hide batch & long video tools" else "Batch & long video tools") }
                                        StudioCard("Speech & subtitles") {
                if (!modelReady || modelDownloading) {
                Text("Speech tools", style = MaterialTheme.typography.titleMedium)
                Text("English, Tagalog and Taglish, processed on your phone. Review recognition before publishing.", style = MaterialTheme.typography.bodySmall)
                Text("Transcription prerequisites: ${if (video != null) "video selected" else "select video"} • ${if (modelReady) "model installed" else "download model"}", style = MaterialTheme.typography.bodySmall)
                Text("Multilingual Whisper tiny (~78 MB) runs locally; no video is uploaded for transcription.", style = MaterialTheme.typography.bodySmall)
                Text(if (modelReady) "Multilingual model downloaded" else if (modelDownloading) "Downloading model: $modelProgress%" else "Model not installed")
                Button(enabled = !busy && !modelDownloading, onClick = {
                    if (modelReady) {
                        status = "The verified speech model is ready for local transcription."
                    } else {
                        modelDownloading = true
                        modelProgress = 0
                        status = "Downloading multilingual model…"
                        Thread {
                            try {
                                downloadOfflineModel(File(filesDir, "models/ggml-tiny.bin"), OfflineModel.TINY) { value ->
                                    runOnUiThread { modelProgress = value }
                                }
                                runOnUiThread {
                                    modelReady = true
                                    modelDownloading = false
                                    status = "Multilingual model verified and saved locally."
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    modelDownloading = false
                                    status = "Model download failed: ${e.message}"
                                }
                            }
                        }.start()
                    }
                }) { Text(if (modelReady) "Model installed" else if (modelDownloading) "Downloading…" else "Download English + Tagalog model") }
                }
                if (speechAdvanced) {
                Button(enabled = !busy && video != null && !audioPreparing, onClick = {
                    val selectedVideo = video ?: return@Button
                    audioPreparing = true
                    status = "Extracting audio locally…"
                    Thread {
                        try {
                            ensureAudioWorkspace(cacheDir, minOf(duration, 30_000L))
                            val wav = OfflineAudioPreparer.extractWav(this@MainActivity, selectedVideo, File(cacheDir, "clipforge_audio.wav"), 0, 30_000)
                            runOnUiThread {
                                audioPreparing = false
                                status = "Audio prepared locally: ${wav.length() / 1024} KB. Ready for offline Whisper transcription."
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                audioPreparing = false
                                status = "Audio extraction failed: ${e.message}"
                            }
                        }
                    }.start()
                }) { Text(if (audioPreparing) "Preparing audio…" else "Check source audio") }
                }
                Button(enabled = !busy && video != null && modelReady && !transcribing && !batchRunning && !audioPreparing, onClick = {
                    val selectedVideo = video ?: return@Button
                    approved = false
                    speechPhase = "Starting offline transcription"
                    speechPercent = -1
                    transcribing = true
                    transcript = ""
                    subtitleDraftIndex = -1
                    subtitleSegments = emptyList()
                    status = "Preparing first 30 seconds for offline Whisper…"
                    Thread {
                        try {
                            WhisperNative.withSession {
                            ensureAudioWorkspace(cacheDir, minOf(duration, 30_000L))
                            val result = OfflineTranscriber.range(this@MainActivity, selectedVideo, File(filesDir, "models/ggml-tiny.bin"), 0L, minOf(duration, 30_000L), progress = reportSpeechProgress)
                            runOnUiThread {
                                sourceTranscriptSegments = result
                                transcriptSegments = result
                                transcript = result.joinToString(" ") { it.text }
                                transcribing = false
                                status = "Offline Whisper processed first 30 seconds. Review accuracy below."
                            }
                            }
                        } catch (e: Throwable) {
                            runOnUiThread {
                                transcribing = false
                                status = "Whisper failed: ${e.message}"
                            }
                        }
                    }.start()
                }) { Text(if (transcribing) "Transcribing offline…" else "Transcribe first 30 seconds") }
                if (transcript.isNotBlank()) {
                    TextButton(onClick = { showTranscriptNotes = !showTranscriptNotes }) { Text(if (showTranscriptNotes) "Hide transcript notes" else "Show transcript notes") }
                    if (showTranscriptNotes) {
                    Text("Transcript notes", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(enabled = !busy, value = transcript, onValueChange = { transcript = it }, label = { Text("Transcript notes (edit timed cues below)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                }
                Button(enabled = !busy && video != null && modelReady && !transcribing && !batchRunning && !audioPreparing && selectedDraft in drafts.indices, onClick = {
                    val selectedVideo = video ?: return@Button
                    val chosenIndex = selectedDraft
                    val chosen = drafts.getOrNull(chosenIndex) ?: return@Button
                    approved = false
                    speechPhase = "Starting offline transcription"
                    speechPercent = -1
                    transcribing = true
                    transcript = ""
                    transcriptSegments = emptyList()
                    subtitleSegments = emptyList()
                    subtitleDraftIndex = -1
                    status = "Transcribing selected highlight offline…"
                    Thread {
                        try {
                            WhisperNative.withSession {
                            ensureAudioWorkspace(cacheDir, minOf(duration, 30_000L))
                            val segments = OfflineTranscriber.range(this@MainActivity, selectedVideo, File(filesDir, "models/ggml-tiny.bin"), chosen.startMs, chosen.endMs, progress = reportSpeechProgress)
                            runOnUiThread {
                                transcriptSegments = segments
                                subtitleSegments = segments
                                batchResults = batchResults + (chosenIndex to segments)
                                subtitleDraftIndex = chosenIndex
                                approved = false
                                transcript = segments.joinToString("\n") { it.text }
                                transcribing = false
                                status = "Transcribed selected highlight: ${segments.size} timed segments."
                            }
                            }
                        } catch (e: Throwable) {
                            runOnUiThread {
                                transcribing = false
                                status = "Highlight transcription failed: ${e.message}"
                            }
                        }
                    }.start()
                }) { Text(if (transcribing) "Transcribing offline…" else "Transcribe selected clip") }
                if (transcriptSegments.isNotEmpty()) {
                    Text("Timed subtitle editor", style = MaterialTheme.typography.titleMedium)
                    Text("Subtitle size: ${subtitleSize.toInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(enabled = !busy, value = subtitleSize, onValueChange = { subtitleSize = it; approved = false; drafts = drafts.map { d -> d.copy(approved = false) } }, valueRange = 28f..55f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(enabled = !busy, checked = subtitleYellow, onCheckedChange = { subtitleYellow = it; approved = false; drafts = drafts.map { d -> d.copy(approved = false) } })
                        Text("Yellow subtitles (otherwise white)")
                    }

                    val pageCount = (transcriptSegments.size + 24) / 25
                    val currentPage = subtitlePage.coerceIn(0, pageCount - 1)
                    Row {
                        OutlinedButton(enabled = !busy && currentPage > 0, onClick = { subtitlePage = currentPage - 1 }) { Text("Previous") }
                        Text("Page ${currentPage + 1}/$pageCount")
                        OutlinedButton(enabled = !busy && currentPage + 1 < pageCount, onClick = { subtitlePage = currentPage + 1 }) { Text("Next") }
                    }
                    transcriptSegments.drop(currentPage * 25).take(25).forEachIndexed { pageIndex, segment ->
                        val segmentIndex = currentPage * 25 + pageIndex
                        Text("${segment.startMs / 1000.0}s – ${segment.endMs / 1000.0}s", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(enabled = !busy, value = segment.text, onValueChange = { edited ->
                            val changed = transcriptSegments.toMutableList()
                            changed[segmentIndex] = segment.copy(text = edited)
                            transcriptSegments = changed
                            subtitleSegments = changed
                            if (selectedDraft >= 0 && subtitleDraftIndex == selectedDraft) batchResults = batchResults + (selectedDraft to changed)
                            transcript = changed.joinToString("\n") { it.text }
                            approved = false
                        }, label = { Text("Subtitle ${segmentIndex + 1}") }, modifier = Modifier.fillMaxWidth())
                    }
                    Button(enabled = !busy, onClick = {
                        captionText = transcript
                        captionFrom = (transcriptSegments.first().startMs / 1000.0).toString()
                        captionTo = (transcriptSegments.last().endMs / 1000.0).toString()
                        approved = false
                        status = "Transcript copied to caption draft. Review timing and approve before export."
                    }) { Text("Use transcript as caption draft") }
                }
                if (transcribing) {
                    Text(speechPhase)
                    if (speechPercent < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { speechPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Elapsed: ${speechElapsedSeconds / 60}m ${speechElapsedSeconds % 60}s • ${if (speechPercent < 0) "Preparing" else "$speechPercent% of current audio window"}", style = MaterialTheme.typography.bodySmall)
                }
                if (speechAdvanced) {
                Text("Processing plan", style = MaterialTheme.typography.titleMedium)
                val highlightPlan = HybridTranscriptionPlan.highlights(drafts)
                val fullPlan = HybridTranscriptionPlan.fullVideo(duration)
                Text("Highlight-first: ${highlightPlan.size} × up-to-30-second audio chunks from current drafts.", style = MaterialTheme.typography.bodySmall)
                Text("Optional full video: ${fullPlan.size} × up-to-30-second chunks.", style = MaterialTheme.typography.bodySmall)
                Text("Chunk counts are estimates. Timed subtitle rendering and chunked transcription are experimental until device-tested.", style = MaterialTheme.typography.bodySmall)
                Text("Batch highlight transcription", style = MaterialTheme.typography.titleMedium)
                Text("Process each candidate offline, up to 15. This can take substantial time and battery.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy && video != null && modelReady && drafts.isNotEmpty() && !batchRunning && !transcribing && !audioPreparing, onClick = {
                    val source = video ?: return@Button
                    val snapshot = drafts.toList()
                    approved = false
                    speechPhase = "Starting offline batch"
                    speechPercent = -1
                    batchRunning = true
                    batchCancel.set(false)
                    batchProgress = 0
                    batchTotal = snapshot.size
                    batchResults = emptyMap()
                    status = "Preparing audio for highlight batch…"
                    Thread {
                        try {
                            WhisperNative.withSession {
                            ensureAudioWorkspace(cacheDir, minOf(duration, 30_000L))
                            val model = File(filesDir, "models/ggml-tiny.bin")
                            val completed = mutableMapOf<Int, List<WhisperNative.Segment>>()
                            for ((index, draft) in snapshot.withIndex()) {
                                if (batchCancel.get()) break
                                runOnUiThread { status = "Processing highlight ${index + 1}/${snapshot.size}"; speechPhase = "Decoding highlight ${index + 1}"; speechPercent = -1 }
                                val segments = OfflineTranscriber.range(this@MainActivity, source, model, draft.startMs, minOf(draft.endMs, draft.startMs + 30_000L), progress = reportSpeechProgress)
                                completed[index] = segments
                                val completedSnapshot = completed.toMap()
                                runOnUiThread {
                                    batchResults = completedSnapshot
                                    batchProgress = index + 1
                                    status = "Transcribed ${batchProgress}/${batchTotal} highlights"
                                }
                            }
                            runOnUiThread {
                                batchRunning = false
                                status = if (batchCancel.get()) "Batch stopped after ${completed.size} highlights" else "Batch complete: ${completed.size} highlights"
                            }
                            }
                        } catch (e: Throwable) {
                            runOnUiThread {
                                batchRunning = false
                                status = "Batch failed: ${e.message}"
                            }
                        }
                    }.start()
                }) { Text(if (batchRunning) "Transcribing highlights…" else "Transcribe all highlight candidates") }
                if (batchRunning) {
                    LinearProgressIndicator(progress = { if (batchTotal > 0) batchProgress.toFloat() / batchTotal else 0f }, modifier = Modifier.fillMaxWidth())
                    Text(status)
                    Text(speechPhase)
                    if (speechPercent < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { speechPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Elapsed: ${speechElapsedSeconds / 60}m ${speechElapsedSeconds % 60}s • ${if (speechPercent < 0) "Preparing current window" else "$speechPercent% of current window"}", style = MaterialTheme.typography.bodySmall)
                    Text("${batchProgress}/${batchTotal} audio windows completed", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { batchCancel.set(true) }) { Text("Stop after current audio window") }
                }
                if (batchResults.isNotEmpty()) {
                    Text("${batchResults.size} transcribed highlights ready to review", style = MaterialTheme.typography.bodySmall)
                    batchResults.toSortedMap().forEach { (index, segments) ->
                        OutlinedButton(enabled = !busy, onClick = {
                            selectedDraft = index
                            val chosen = drafts[index]
                            editingMode = "AI Draft"
                            hookText = chosen.hook
                            effectChoice = chosen.effect
                            captionFrom = (chosen.captionStartMs / 1000L).toString()
                            captionTo = (chosen.captionEndMs / 1000L).toString()
                            start = chosen.startMs.toFloat() / duration
                            end = chosen.endMs.toFloat() / duration
                            subtitleDraftIndex = index
                            transcriptSegments = segments
                            subtitleSegments = segments
                            transcript = segments.joinToString(" ") { it.text }
                            captionText = transcript
                            approved = false
                            player.seekTo(chosen.startMs)
                            status = "Review subtitles for clip ${index + 1}, then approve before export"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Review clip ${index + 1} • ${segments.size} subtitles") }
                    }
                }
                Text("Full-video transcription (optional)", style = MaterialTheme.typography.titleMedium)
                Text("Runs in 30-second chunks. Long videos may take hours; keep the app open. Stopping finishes the current chunk first.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy && video != null && modelReady && duration > 0 && !batchRunning && !transcribing && !audioPreparing, onClick = {
                    val source = video ?: return@Button
                    val chunks = HybridTranscriptionPlan.fullVideo(duration)
                    approved = false
                    speechPhase = "Starting offline batch"
                    speechPercent = -1
                    batchRunning = true
                    batchCancel.set(false)
                    batchProgress = 0
                    batchTotal = chunks.size
                    batchResults = emptyMap()
                    subtitleDraftIndex = -1
                    subtitleSegments = emptyList()
                    status = "Preparing full-video audio…"
                    Thread {
                        try {
                            WhisperNative.withSession {
                            ensureAudioWorkspace(cacheDir, minOf(duration, 30_000L))
                            val model = File(filesDir, "models/ggml-tiny.bin")
                            val sourceKey = MessageDigest.getInstance("SHA-256")
                                .digest((source.toString() + ":chunk-v2:" + duration).toByteArray())
                                .take(10).joinToString("") { "%02x".format(it) }
                            val transcriptFile = File(filesDir, "transcript_${sourceKey}.tsv")
                            val previous = TranscriptStore.read(transcriptFile)
                            val completedThrough = previous.throughMs
                            val collected = previous.segments.toMutableList()
                            var done = 0
                            for (chunk in chunks) {
                                if (batchCancel.get()) break
                                if (chunk.endMs <= completedThrough) {
                                    done++
                                    val skipped = done
                                    runOnUiThread { batchProgress = skipped }
                                    continue
                                }
                                runOnUiThread { status = "Processing chunk ${chunk.index + 1}/${chunks.size}"; speechPhase = "Decoding chunk ${chunk.index + 1}"; speechPercent = -1 }
                                val segments = OfflineTranscriber.range(this@MainActivity, source, model, chunk.startMs, chunk.endMs, progress = reportSpeechProgress)
                                collected.addAll(segments)
                                TranscriptStore.write(transcriptFile, TranscriptStore.Snapshot(chunk.endMs, collected))
                                done++
                                val progressValue = done
                                runOnUiThread {
                                    batchProgress = progressValue
                                    status = "Full video: ${progressValue}/${chunks.size} chunks; transcript saved locally"
                                }
                            }
                            runOnUiThread {
                                sourceTranscriptSegments = collected.toList()
                                transcriptSegments = collected.toList()
                                transcript = collected.joinToString("\n") { it.text }
                                batchRunning = false
                                status = if (batchCancel.get()) "Full-video transcription stopped; partial transcript saved" else "Full-video transcript saved locally"
                            }
                            }
                        } catch (e: Throwable) {
                            runOnUiThread {
                                batchRunning = false
                                status = "Full-video transcription failed: ${e.message}"
                            }
                        }
                    }.start()
                }) { Text(if (batchRunning) "Processing offline…" else "Transcribe full video (optional)") }
                }
                Button(enabled = !busy && (sourceTranscriptSegments.isNotEmpty() || transcriptSegments.isNotEmpty()) && duration > 0, onClick = {
                    val speech = sourceTranscriptSegments.ifEmpty { transcriptSegments }.toList()
                    val sourceDuration = duration
                    approved = false
                    analyzing = true
                    status = "Ranking speech locally…"
                    Thread {
                        try {
                            val detected = TranscriptHighlights.detect(speech, sourceDuration)
                            runOnUiThread {
                                drafts = detected
                                batchResults = emptyMap()
                                selectedDraft = -1
                                subtitleDraftIndex = -1
                                subtitleSegments = emptyList()
                                editingMode = "AI Draft"
                                selectedTab = StudioTab.STUDIO
                                studioEditing = false
                                analyzing = false
                                status = "${detected.size} speech-ranked candidates. Review context and subtitles before approving."
                            }
                        } catch (e: Exception) {
                            runOnUiThread { analyzing = false; status = "Speech ranking failed: ${e.message}" }
                        }
                    }.start()
                }) { Text("Find speech highlights") }
                Text("Speech ranking uses density, vocabulary and hook words. It does not predict engagement.", style = MaterialTheme.typography.bodySmall)

                                        }
                                    }
                                    StudioTab.CAMPAIGN -> {
                Text("Campaign Mode", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(enabled = !busy, onClick = { showTracker = !showTracker }) { Text(if (showTracker) "Hide submission tracker" else "Open submission and payout tracker") }
                if (showTracker) StudioCard("", "") { CampaignTracker(filesDir, enabled = !busy) }
                Row {
                    Checkbox(enabled = !busy, checked = campaignEnabled, onCheckedChange = { campaignEnabled = it; approved = false })
                    Text("Check campaign rules before export")
                }
                if (campaignEnabled) {
                    Text("Enter the current brief. Checks use your inputs; platform standing, geography, engagement and payout eligibility require manual verification.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(enabled = !busy, value = campaignTags, onValueChange = { campaignTags = it; approved = false }, label = { Text("Required @mentions and #hashtags") })
                    OutlinedTextField(enabled = !busy, value = campaignCaption, onValueChange = { campaignCaption = it; approved = false }, label = { Text("Posting caption (not burned into video)") })
                    OutlinedTextField(enabled = !busy, value = campaignMin, onValueChange = { campaignMin = it.filter(Char::isDigit).take(4); approved = false }, label = { Text("Minimum clip seconds") })
                    OutlinedTextField(enabled = !busy, value = campaignMax, onValueChange = { campaignMax = it.filter(Char::isDigit).take(4); approved = false }, label = { Text("Maximum clip seconds") })
                    OutlinedTextField(enabled = !busy, value = campaignSource, onValueChange = { campaignSource = it.filter(Char::isDigit).take(4); approved = false }, label = { Text("Required source footage seconds") })
                    Row { Checkbox(enabled = !busy, checked = sourceVerified, onCheckedChange = { sourceVerified = it; approved = false }); Text("I verified the permitted source and footage requirement") }
                    Row { Checkbox(enabled = !busy, checked = rightsVerified, onCheckedChange = { rightsVerified = it; approved = false }); Text("I verified rights and duplicate-submission restrictions") }
                    campaignIssues.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (campaignIssues.isEmpty()) Text("Entered rules checked. Review the clip before approving export.")
                }

                                    }
                                    StudioTab.EXPORT -> {
                                        StudioCard("Delivery details") {
                                            Text("Range · ${Timecode.precise((duration * start).toLong())} — ${Timecode.precise((duration * end).toLong())}")
                                            Text("Frame · ${if (vertical) if (fillFrame) "9:16 crop" else "9:16 fit" else "Source aspect"}   /   Audio · ${if (mute) "Muted" else "Original"}")
                                            Text("Captions · ${if (subtitleDraftIndex == selectedDraft) subtitleSegments.size else 0} timed cues   /   Style · $effectChoice")
                                        }
                                        StudioCard("Ready when you are") {
                Text("Export review", style = MaterialTheme.typography.titleMedium)
                Text("Check framing, audio and subtitle timing in the preview. Approval applies to this edit and is cleared when you change it.", style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(checked = approved, enabled = !busy && campaignIssues.isEmpty() && video != null && duration > 0 && (editingMode != "AI Draft" || selectedDraft >= 0), onCheckedChange = { value -> approved = value; if (selectedDraft >= 0) drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(approved = value) } })
                    Text("I reviewed this edit and approve its export", modifier = Modifier.weight(1f))
                }
                Button(enabled = !busy && campaignIssues.isEmpty() && video != null && duration > 0 && approved && (editingMode != "AI Draft" || selectedDraft >= 0), onClick = {
                    if (!approved || busy || campaignIssues.isNotEmpty()) return@Button
                    val source = video ?: return@Button
                    val outputDir = (getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(filesDir, "exports")).apply { mkdirs() }
                    val output = File(outputDir, "clipforge_${System.currentTimeMillis()}.mp4")
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((duration * start).toLong())
                        .setEndPositionMs((duration * end).toLong())
                        .build()
                    val item = MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build()
                    val effects = mutableListOf<Effect>()
                    effects.addAll(creativeEffects(effectChoice))
                    if (vertical) effects.add(Presentation.createForAspectRatio(9f / 16f, if (fillFrame) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
                    if (editingMode == "AI Draft" && (hookText.isNotBlank() || captionText.isNotBlank() || (subtitleDraftIndex == selectedDraft && subtitleSegments.isNotEmpty()))) {
                        val draft = drafts.getOrNull(selectedDraft)
                        val trimStartMs = (duration * start).toLong()
                        val overlay = if (draft != null && subtitleDraftIndex == selectedDraft && subtitleSegments.isNotEmpty()) {
                            segmentSubtitleOverlay(hookText, subtitleSegments, trimStartMs, subtitleSize, if (subtitleYellow) Color.YELLOW else Color.WHITE)
                        } else if (draft != null) {
                            timedCaptionOverlay(hookText, captionText, draft.captionStartMs, draft.captionEndMs, trimStartMs)
                        } else BitmapOverlay.createStaticBitmapOverlay(makeCaptionOverlay(hookText, captionText))
                        effects.add(OverlayEffect(listOf(overlay)))
                    }
                    val edited = EditedMediaItem.Builder(item).setRemoveAudio(mute).setEffects(Effects(emptyList(), effects)).build()
                    exportPercent = 0
                    exporting = true
                    status = "Exporting…"
                    val requestToken = Any()
                    activeExportToken = requestToken
                    val transformer = Transformer.Builder(this@MainActivity)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                                runOnUiThread {
                                    if (activeExportToken !== requestToken) return@runOnUiThread
                                    activeTransformer = null
                                    exportPercent = 100
                                    status = "Rendering complete. Saving approved MP4…"
                                    exportScope.launch {
                                        try {
                                            val published = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                ExportPublisher.publish(applicationContext, output)
                                            }
                                            lastExportUri = published.uri
                                            if (published.inGallery) output.delete()
                                            status = if (published.inGallery) "Saved to Gallery: Movies/ClipForge/${output.name}" else "Saved privately. Use Open or Share below."
                                            Toast.makeText(this@MainActivity, "Export complete", Toast.LENGTH_LONG).show()
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            lastExportUri = runCatching { ExportPublisher.privateUri(this@MainActivity, output) }.getOrNull()
                                            status = "MP4 rendered and retained privately. Gallery save failed: ${error.message}. Use Share if available."
                                        } finally { activeExportToken = null; exporting = false; exportFile = null }
                                    }
                                }
                            }
                            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                                runOnUiThread { if (activeExportToken !== requestToken) return@runOnUiThread; activeExportToken = null; output.delete(); activeTransformer = null; exporting = false; status = "Export failed: ${exportException.message}" }
                            }
                        }).build()
                    exportFile = output
                    activeTransformer = transformer
                    try { transformer.start(edited, output.absolutePath) }
                    catch (e: Exception) { activeExportToken = null; output.delete(); activeTransformer = null; exporting = false; status = "Export failed: ${e.message}" }
                }) { Text(if (exporting) "Exporting approved clip…" else "Export approved MP4") }
                OutlinedButton(enabled = !busy && approved && campaignIssues.isEmpty() && subtitleDraftIndex == selectedDraft && selectedDraft >= 0 && subtitleSegments.isNotEmpty(), onClick = {
                    pendingSrt = SubtitleSrt.render(subtitleSegments, (duration * start).toLong(), (duration * end).toLong())
                    subtitleSaver.launch("clipforge_subtitles.srt")
                }) { Text("Save approved clip subtitles (.srt)") }
                if (exporting) {
                    LinearProgressIndicator(progress = { exportPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Export progress: $exportPercent%")
                    if (activeTransformer != null) OutlinedButton(onClick = {
                        activeExportToken = null
                        activeTransformer?.cancel()
                        exportFile?.delete()
                        exportFile = null
                        activeTransformer = null
                        exporting = false
                        approved = false
                        status = "Export cancelled. Review before starting again."
                    }) { Text("Cancel export") }
                }
                lastExportUri?.let { uri ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) { status = "Could not open MP4: ${error.message}" }
                        }) { Text("Open last MP4") }
                        OutlinedButton(enabled = !busy, onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND).setType("video/mp4")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.clipData = android.content.ClipData.newRawUri("Approved ClipForge export", uri)
                                startActivity(Intent.createChooser(intent, "Share approved clip"))
                            } catch (error: Exception) { status = "Could not share MP4: ${error.message}" }
                        }) { Text("Share last MP4") }
                    }
                }

                                        }
                                        Text("Completed clips save to Gallery on Android 10+. Open or share them after export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
