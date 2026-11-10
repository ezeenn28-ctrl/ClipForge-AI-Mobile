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
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.sync.withLock

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

/** Video timestamps are relative to the exported trimmed clip.
 * Hook stays visible; subtitle appears only during the chosen time interval. */
fun timedCaptionOverlay(
    hook: String,
    caption: String,
    startMs: Long,
    endMs: Long,
    trimStartMs: Long,
    calibrateClock: Boolean = false,
    subtitleSize: Float = 39f,
    subtitleColor: Int = Color.WHITE,
    preset: CaptionPreset = CaptionPreset.BOLD,
    appearance: CaptionAppearance = CaptionAppearance()
): BitmapOverlay {
    var withCaption: Bitmap? = null
    var withoutCaption: Bitmap? = null
    val fromUs = (startMs - trimStartMs).coerceAtLeast(0L) * 1000L
    val untilUs = (endMs - trimStartMs).coerceAtLeast(0L) * 1000L
    val clock = ClipPresentationClock()
    return object : CaptionBitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val timeUs = if (calibrateClock) clock.relativeUs(presentationTimeUs) else presentationTimeUs
            return if (untilUs <= fromUs || timeUs in fromUs until untilUs) {
                withCaption ?: makeCaptionOverlay(hook, caption, subtitleSize, subtitleColor, preset, appearance).also { withCaption = it }
            } else withoutCaption ?: makeCaptionOverlay(hook, "", subtitleSize, subtitleColor, preset, appearance).also { withoutCaption = it }
        }
        override fun release() {
            super.release()
            withCaption?.recycle(); withoutCaption?.recycle()
            withCaption = null; withoutCaption = null
        }
    }
}

fun segmentSubtitleOverlay(hook: String, segments: List<WhisperNative.Segment>, trimStartMs: Long, subtitleSize: Float, subtitleColor: Int, calibrateClock: Boolean = false, preset: CaptionPreset = CaptionPreset.BOLD, appearance: CaptionAppearance = CaptionAppearance()): BitmapOverlay {
    var empty: Bitmap? = null
    // Retain only the current subtitle bitmap, independent of transcript length.
    val clock = ClipPresentationClock()
    var cachedIndex = -1
    var cachedBitmap: Bitmap? = null
    return object : CaptionBitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val absoluteMs = SubtitleTimeline.sourcePositionMs(trimStartMs, if (calibrateClock) clock.relativeUs(presentationTimeUs) else presentationTimeUs)
            val index = SubtitleTimeline.activeSegmentIndex(segments, absoluteMs)
            if (index < 0) return empty ?: makeCaptionOverlay(hook, "", subtitleSize, subtitleColor, preset, appearance).also { empty = it }
            if (cachedIndex != index) {
                cachedBitmap?.recycle()
                cachedBitmap = makeCaptionOverlay(hook, segments[index].text, subtitleSize, subtitleColor, preset, appearance)
                cachedIndex = index
            }
            return cachedBitmap ?: (empty ?: makeCaptionOverlay(hook, "", subtitleSize, subtitleColor, preset, appearance).also { empty = it })
        }
        override fun release() {
            super.release()
            cachedBitmap?.recycle(); empty?.recycle()
            cachedBitmap = null; empty = null
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
        var fullScreenPreview by remember { mutableStateOf(false) }
        var sourceName by remember { mutableStateOf("Imported video") }
        var projectId by remember { mutableStateOf(ProjectCatalog.newId()) }
        var projectName by remember { mutableStateOf("") }
        var recentProjects by remember { mutableStateOf(ProjectCatalog.Listing()) }
        var catalogRevision by remember { mutableIntStateOf(0) }
        var studioEditing by remember { mutableStateOf(false) }
        var showDiscovery by remember { mutableStateOf(false) }
        var homeSettings by remember { mutableStateOf(false) }
        var pendingHomeAction by remember { mutableStateOf("") }
        var autoFindRequested by remember { mutableStateOf(false) }
        var advancedEditor by remember { mutableStateOf(false) }
        var editorTool by remember { mutableStateOf(EditorTool.EDIT) }
        var showProjectTools by remember { mutableStateOf(false) }
        var speechAdvanced by remember { mutableStateOf(false) }
        var showTranscriptNotes by remember { mutableStateOf(false) }
        var advancedImport by remember { mutableStateOf(false) }
        var previewPlaying by remember { mutableStateOf(false) }
        var trimInText by remember { mutableStateOf("00:00.000") }
        var trimOutText by remember { mutableStateOf("00:00.000") }
        var fillFrame by remember { mutableStateOf(false) }
        var vertical by remember { mutableStateOf(true) }
        var mute by remember { mutableStateOf(false) }
        var editingMode by remember { mutableStateOf("AI Draft") }
        var approved by remember { mutableStateOf(false) }
        var drafts by remember { mutableStateOf<List<HighlightDraft>>(emptyList()) }
        var selectedDraft by remember { mutableIntStateOf(-1) }
        var hookText by remember { mutableStateOf("") }
        var captionText by remember { mutableStateOf("") }
        var effectChoice by remember { mutableStateOf("None") }
        var captionFrom by remember { mutableStateOf("0") }
        var captionTo by remember { mutableStateOf("5") }
        var timeline by remember { mutableStateOf(TimelineHistory()) }
        var music by remember { mutableStateOf<MusicTrack?>(null) }
        var assetImporting by remember { mutableStateOf(false) }
        var relinkTimelineId by remember { mutableStateOf("") }
        var timelineMode by remember { mutableStateOf(false) }
        var selectedTimelineId by remember { mutableStateOf("") }
        var timelinePlaybackIndex by remember { mutableIntStateOf(0) }
        var timelinePosition by remember { mutableLongStateOf(0) }
        var timelineSubtitleTarget by remember { mutableStateOf("") }
        val timelineActive = timelineMode && timeline.clips.isNotEmpty()
        val playingTimelineClip = if (timelineActive) timeline.clips.getOrNull(timelinePlaybackIndex) else null
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
        val speechState by SpeechJobs.state.collectAsState()
        val speechJob = speechState.job
        var speechStarting by remember { mutableStateOf(false) }
        val batchRunning = speechJob?.active == true && speechJob.mode in listOf(SpeechJobMode.HIGHLIGHTS, SpeechJobMode.FULL_VIDEO)
        val transcribing = speechJob?.active == true && !batchRunning
        val speechPhase = speechState.phase
        val speechPercent = speechState.percent
        var speechElapsedSeconds by remember { mutableLongStateOf(0) }
        var transcript by remember { mutableStateOf("") }
        var sourceTranscriptSegments by remember { mutableStateOf<List<WhisperNative.Segment>>(emptyList()) }
        var transcriptSegments by remember { mutableStateOf<List<WhisperNative.Segment>>(emptyList()) }
        var subtitleSegments by remember { mutableStateOf<List<WhisperNative.Segment>>(emptyList()) }
        var subtitleDraftIndex by remember { mutableIntStateOf(-1) }
        var subtitlePage by remember { mutableIntStateOf(0) }
        var subtitleSize by remember { mutableFloatStateOf(39f) }
        var captionPreset by remember { mutableStateOf(CaptionPreset.BOLD) }
        var captionAppearance by remember { mutableStateOf(CaptionAppearance()) }
        var subtitleYellow by remember { mutableStateOf(false) }
        val batchProgress = speechJob?.results?.size ?: 0
        val batchTotal = speechJob?.units()?.size ?: 0
        LaunchedEffect(speechJob?.id, speechJob?.active) {
            do {
                speechElapsedSeconds = SpeechJobs.elapsedMs() / 1000
                delay(1000)
            } while (speechJob?.active == true)
        }
        LaunchedEffect(Unit) {
            try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SpeechJobs.initialize(applicationContext) } }
            catch (error: Exception) { status = "Saved speech task could not be read: ${error.message}" }
        }
        var batchResults by remember { mutableStateOf<Map<Int, List<WhisperNative.Segment>>>(emptyMap()) }
        var modelDownloading by remember { mutableStateOf(false) }
        var modelProgress by remember { mutableIntStateOf(0) }
        var modelReady by remember { mutableStateOf(File(filesDir, "models/ggml-tiny.bin").exists()) }
        val campaignPrefs = remember { getSharedPreferences("campaign", MODE_PRIVATE) }
        var showTracker by remember { mutableStateOf(true) }
        var campaignEnabled by remember { mutableStateOf(campaignPrefs.getBoolean("enabled", false)) }
        var campaignTags by remember { mutableStateOf(campaignPrefs.getString("tags", "") ?: "") }
        var campaignCaption by remember { mutableStateOf(campaignPrefs.getString("caption", "") ?: "") }
        var campaignMin by remember { mutableStateOf(campaignPrefs.getString("min", "15") ?: "15") }
        var campaignMax by remember { mutableStateOf(campaignPrefs.getString("max", "30") ?: "30") }
        var campaignSource by remember { mutableStateOf(campaignPrefs.getString("source", "0") ?: "0") }
        var sourceVerified by remember { mutableStateOf(false) }
        var rightsVerified by remember { mutableStateOf(false) }
        var appliedSpeechId by remember { mutableStateOf("") }
        var appliedSpeechUnits by remember { mutableIntStateOf(0) }
        val latestBackupProject = remember { java.util.concurrent.atomic.AtomicReference<EditorProject?>(null) }
        var pendingRelink by remember { mutableStateOf<EditorProject?>(null) }
        var metadataBusy by remember { mutableStateOf(false) }
        var pendingBackup by remember { mutableStateOf<MetadataBackup.Staged?>(null) }
        var projectLoading by remember { mutableStateOf(true) }
        var projectMessage by remember { mutableStateOf("Checking saved project…") }
        var expectedRestoredDuration by remember { mutableLongStateOf(0L) }
        val busy = assetImporting || projectLoading || metadataBusy || speechStarting || exporting || analyzing || transcribing || batchRunning || audioPreparing || downloading
        val deliveryDuration = if (timelineMode) TimelineModel.duration(timeline.clips) else (duration * end).toLong() - (duration * start).toLong()
        val exportReady = video != null && duration > 0 && if (timelineMode) timeline.clips.isNotEmpty() else (editingMode != "AI Draft" || selectedDraft >= 0)
        val campaignIssues = if (campaignEnabled) CampaignRules(campaignTags,
            campaignMin.toLongOrNull() ?: -1, campaignMax.toLongOrNull() ?: -1,
            campaignSource.toLongOrNull() ?: -1).issues(campaignCaption,
            deliveryDuration, sourceVerified, rightsVerified) else emptyList()
        LaunchedEffect(campaignEnabled, campaignTags, campaignCaption, campaignMin, campaignMax, campaignSource) {
            campaignPrefs.edit().putBoolean("enabled", campaignEnabled).putString("tags", campaignTags)
                .putString("caption", campaignCaption).putString("min", campaignMin)
                .putString("max", campaignMax).putString("source", campaignSource).apply()
            approved = false
        }
        val speechScope = rememberCoroutineScope()
        var pendingSpeechRequest by remember { mutableStateOf<SpeechJob?>(null) }
        val enqueueSpeech: (SpeechJob) -> Unit = { request ->
            approved = false
            drafts = drafts.map { it.copy(approved = false) }
            speechStarting = true
            speechScope.launch {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        SpeechJobs.submit(applicationContext, request)
                        SpeechJobs.startService(applicationContext)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { status = "Speech job could not start: ${error.message}" }
                finally { speechStarting = false }
            }
        }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            pendingSpeechRequest?.let(enqueueSpeech)
            pendingSpeechRequest = null
        }
        val launchSpeech: (SpeechJobMode, List<SpeechRange>, String) -> Unit = { mode, ranges, target ->
            val selected = if (mode == SpeechJobMode.TIMELINE) timeline.clips.firstOrNull { it.id == target } else null
            val source = selected?.resolvedSource(video?.toString().orEmpty())?.let(Uri::parse) ?: video
            val speechDuration = selected?.resolvedDuration(duration) ?: duration
            if (source != null && speechDuration > 0) {
                val request = SpeechJob(java.util.UUID.randomUUID().toString(), source.toString(), speechDuration, mode, ranges, target)
                val preferences = getSharedPreferences("processing", MODE_PRIVATE)
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    !preferences.getBoolean("notifications-asked", false)) {
                    preferences.edit().putBoolean("notifications-asked", true).apply()
                    pendingSpeechRequest = request
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else enqueueSpeech(request)
            }
        }
        val playbackPrefs = remember { getSharedPreferences("playback", MODE_PRIVATE) }
        var compatiblePreview by remember { mutableStateOf(playbackPrefs.getBoolean("software-preview", false)) }
        val player = remember(compatiblePreview) { PreviewPlayerFactory.create(this, compatiblePreview) }
        val previewPlayback = remember(player) { PreviewPlayback(player) }
        val previewBinding = remember(player) { PreviewSurfaceBinding(player) }
        var playbackError by remember { mutableStateOf("") }
        val soundPlayer = remember { ExoPlayer.Builder(this).build() }
        val soundPreview = remember(soundPlayer) { SoundPreview(soundPlayer) }
        var auditionUntil by remember { mutableLongStateOf(0) }
        var auditionRequest by remember { mutableIntStateOf(0) }
        val auditionScope = rememberCoroutineScope()
        val audition: (SoundKind?, SoundAsset?) -> Unit = { kind, asset ->
            val request = ++auditionRequest
            soundPlayer.pause(); auditionUntil = 0; soundPreview.reset()
            if (kind != null || asset != null) {
                player.pause()
                auditionScope.launch {
                    try {
                        val uri = asset?.source ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SoundAssets.ensure(filesDir)[requireNotNull(kind)] }
                            ?: error("Sound file missing")
                        if (request != auditionRequest) return@launch
                        val length = asset?.durationMs ?: kind!!.durationMs
                        soundPlayer.setMediaItem(MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder().setEndPositionMs(length).build()).build())
                        auditionUntil = android.os.SystemClock.elapsedRealtime() + length + 2000
                        soundPlayer.volume = .25f; soundPlayer.prepare(); soundPlayer.play()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { auditionUntil = 0; status = "Sound preview failed: ${error.message}" }
                }
            }
        }
        var soundAssets by remember { mutableStateOf<Map<SoundKind, String>>(emptyMap()) }
        LaunchedEffect(timeline.clips.any { it.sounds.isNotEmpty() }) {
            if (timeline.clips.any { it.sounds.isNotEmpty() } && soundAssets.isEmpty()) {
                try { soundAssets = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SoundAssets.ensure(filesDir) } }
                catch (error: Exception) { status = "Could not prepare sound effects: ${error.message}"; approved = false }
            }
        }
        DisposableEffect(soundPlayer) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    status = "Sound effect preview failed: ${error.message}"; approved = false
                }
            }
            soundPlayer.addListener(listener)
            onDispose { soundPlayer.release() }
        }
        val musicPlayer = remember { ExoPlayer.Builder(this).build() }
        DisposableEffect(musicPlayer) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    approved = false
                    status = "Music unavailable: replace or relink the local music file. ${error.message}"
                }
            }
            musicPlayer.addListener(listener)
            onDispose { musicPlayer.release() }
        }
        LaunchedEffect(music?.source, music?.startMs, music?.endMs, timelineActive, TimelineModel.duration(timeline.clips)) {
            musicPlayer.pause()
            val track = music
            val length = track?.activeDuration(TimelineModel.duration(timeline.clips)) ?: 0
            if (track != null && timelineActive && length > 0) {
                musicPlayer.setMediaItem(MediaItem.Builder().setUri(Uri.parse(track.source)).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(track.startMs).setEndPositionMs(track.startMs + length).build()).build())
                musicPlayer.prepare()
            } else musicPlayer.clearMediaItems()
        }
        DisposableEffect(player, musicPlayer, soundPlayer) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++ }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(video) {
            val source = video
            if (source == null) sourceName = "Imported video"
            else {
                val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val name = runCatching {
                        if (source.scheme == "file") File(source.path.orEmpty()).name
                        else contentResolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }.orEmpty()
                    }.getOrDefault("").ifBlank { "Imported video" }.replace('\n', ' ').take(70)
                    val retriever = MediaMetadataRetriever()
                    val length = try {
                        retriever.setDataSource(this@MainActivity, source)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    } catch (_: Exception) { null } finally { retriever.release() }
                    name to length
                }
                sourceName = info.first
                info.second?.takeIf { it > 0 }?.let { actual ->
                    if (expectedRestoredDuration > 0 && kotlin.math.abs(expectedRestoredDuration - actual) > 1000) {
                        timeline = TimelineHistory(); timelineMode = false; selectedTimelineId = ""
                        drafts = emptyList(); selectedDraft = -1; batchResults = emptyMap()
                        transcriptSegments = emptyList(); subtitleSegments = emptyList(); sourceTranscriptSegments = emptyList(); subtitleDraftIndex = -1
                        hookText = ""; captionText = ""; approved = false; start = 0f; end = 1f
                        status = "Source duration changed. Incompatible edits were cleared; review the video again."
                    }
                    expectedRestoredDuration = 0
                    duration = actual
                }
            }
        }
        val seekTimeline: (Long) -> Unit = { time ->
            val seekClips = timeline.clips
            val seekSource = video
            TimelineModel.position(seekClips, time)?.let { position ->
                timelinePosition = time.coerceIn(0, TimelineModel.duration(seekClips))
                timelinePlaybackIndex = position.index
                previewPosition = position.sourceMs
                if (timelineMode && seekSource != null) {
                    val sourceLocal = position.sourceMs - seekClips[position.index].startMs
                    previewPlayback.load(seekClips.map { PreviewPlayback.Source(it.resolvedSource(seekSource.toString()), it.startMs, it.endMs, it.speed) }, position.index, sourceLocal)
                    player.seekTo(position.index, sourceLocal)
                }
            }
        }
        val updateTimeline: (TimelineHistory) -> Unit = { changed ->
            try {
                TimelineModel.validate(changed.clips, duration)
                timeline = changed
                if (timelineSubtitleTarget.isNotBlank() && changed.clips.none { it.id == timelineSubtitleTarget }) {
                    timelineSubtitleTarget = ""; transcriptSegments = emptyList(); transcript = ""
                }
                if (changed.clips.none { it.id == selectedTimelineId }) selectedTimelineId = changed.clips.firstOrNull()?.id.orEmpty()
                timelinePosition = timelinePosition.coerceIn(0, TimelineModel.duration(changed.clips))
                approved = false
                status = "Timeline updated. Review before export."
            } catch (error: Exception) { status = error.message ?: "Timeline change failed" }
        }
        val addTimelineSelection: () -> Unit = {
            val from = (duration * start).toLong()
            val until = (duration * end).toLong()
            if (Timecode.validTrim(from, until, duration)) {
                val cues = if (subtitleDraftIndex == selectedDraft && subtitleSegments.isNotEmpty()) subtitleSegments else sourceTranscriptSegments.filter { it.endMs > from && it.startMs < until }
                val draft = drafts.getOrNull(selectedDraft)
                val clip = TimelineClip(java.util.UUID.randomUUID().toString(), from, until, hookText, captionText,
                    draft?.captionStartMs ?: from, draft?.captionEndMs ?: until, effectChoice, subtitles = cues, source = video.toString(), sourceDurationMs = duration, sourceName = sourceName)
                updateTimeline(timeline.edit(timeline.clips + clip))
                if (timeline.clips.any { it.id == clip.id }) {
                    selectedTimelineId = clip.id; timelineMode = true
                    timelinePosition = TimelineModel.offset(timeline.clips, timeline.clips.lastIndex)
                }
            }
        }
        LaunchedEffect(player, video, timelineActive, timeline.clips.map { listOf(it.id, it.startMs, it.endMs, it.source, it.speed) }) {
            val source = video ?: return@LaunchedEffect
            val playbackClips = timeline.clips
            val position = if (timelineMode) TimelineModel.position(playbackClips, timelinePosition) else null
            if (position != null) {
                previewPlayback.load(playbackClips.map { PreviewPlayback.Source(it.resolvedSource(source.toString()), it.startMs, it.endMs, it.speed) }, position.index, position.sourceMs - playbackClips[position.index].startMs)
            } else previewPlayback.load(listOf(PreviewPlayback.Source(source.toString())), positionMs = (duration * start).toLong())
        }
        LaunchedEffect(start, end, duration) {
            trimInText = Timecode.precise((duration * start).toLong())
            trimOutText = Timecode.precise((duration * end).toLong())
        }
        DisposableEffect(player) { onDispose { player.release() } }
        DisposableEffect(Unit) {
            onDispose { activeExportToken = null; if (activeTransformer != null) { activeTransformer?.cancel(); exportFile?.delete() } }
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
        val applySavedProject: (EditorProject, Uri) -> Unit = { saved, source ->
                    pendingRelink = null
                    projectId = saved.projectId.ifEmpty { ProjectCatalog.newId() }
                    projectName = saved.projectName
                    appliedSpeechId = saved.lastSpeechJobId
                    appliedSpeechUnits = saved.lastSpeechUnits
                    timeline = TimelineHistory(saved.timelineClips)
                    music = saved.music
                    timelineSubtitleTarget = saved.currentTimelineId
                    timelineMode = saved.timelineMode
                    selectedTimelineId = saved.selectedTimelineId.ifEmpty { saved.timelineClips.firstOrNull()?.id.orEmpty() }
                    timelinePosition = 0; timelinePlaybackIndex = 0
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
                    captionPreset = CaptionPreset.valueOf(saved.captionPreset)
                    captionAppearance = saved.captionAppearance
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
                    previewPlayback.load(listOf(PreviewPlayback.Source(source.toString())), positionMs = saved.startMs)
                    player.seekTo(saved.startMs)
                    player.pause()
                    playbackError = ""
                    projectMessage = "Local project restored. Review again before export."
                    status = "Restored project; export approval and campaign confirmations were cleared"
        }
        val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destination ->
            if (destination != null) {
                metadataBusy = true
                exportScope.launch {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            MetadataGate.lock.withLock {
                                latestBackupProject.get()?.let { ProjectCatalog.save(filesDir, it) }
                                val stream = contentResolver.openOutputStream(destination) ?: error("Unable to open backup destination")
                                MetadataBackup.create(filesDir, stream)
                            }
                        }
                        status = "Backup saved. Video files and the speech model are not included."
                    } catch (error: Exception) { status = "Backup failed: ${error.message}" }
                    finally { metadataBusy = false }
                }
            }
        }
        val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
            if (source != null) {
                metadataBusy = true
                exportScope.launch {
                    try {
                        pendingBackup = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val stream = contentResolver.openInputStream(source) ?: error("Unable to open backup")
                            MetadataBackup.stage(filesDir, stream)
                        }
                    } catch (error: Exception) { status = "Backup validation failed: ${error.message}" }
                    finally { metadataBusy = false }
                }
            }
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
        val timelinePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty() && !busy && video != null) {
                assetImporting = true; approved = false
                exportScope.launch {
                    try {
                        require(uris.size <= 12 && timeline.clips.size + uris.size <= 60) { "Import up to 12 videos at once, with at most 60 cuts" }
                        val infos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            uris.map { uri ->
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                MediaAssets.inspect(this@MainActivity, uri, true)
                            }
                        }
                        val added = infos.map { info -> TimelineClip(java.util.UUID.randomUUID().toString(), 0, info.durationMs,
                            source = info.uri, sourceDurationMs = info.durationMs, sourceName = info.name) }
                        val next = timeline.edit(timeline.clips + added)
                        TimelineModel.validate(next.clips, duration)
                        updateTimeline(next)
                        selectedTimelineId = added.first().id; timelineMode = true
                        timelineSubtitleTarget = selectedTimelineId; transcriptSegments = emptyList(); transcript = ""
                        subtitleDraftIndex = -1; subtitleSegments = emptyList()
                        timelinePosition = TimelineModel.offset(next.clips, next.clips.size - added.size)
                        status = "Added ${added.size} local videos. Trim and review each source before export."
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { status = "Video import failed: ${error.message}" }
                    finally { assetImporting = false }
                }
            }
        }
        val timelineRelinker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val selected = timeline.clips.firstOrNull { it.id == relinkTimelineId }
            relinkTimelineId = ""
            if (uri != null && selected != null && !busy) {
                assetImporting = true; approved = false
                exportScope.launch {
                    try {
                        val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            MediaAssets.inspect(this@MainActivity, uri, true)
                        }
                        require(kotlin.math.abs(info.durationMs - selected.resolvedDuration(duration)) <= 1000) { "Choose the same original video duration" }
                        val previousSource = selected.resolvedSource(video.toString())
                        val next = timeline.clips.map { clip -> if (clip.resolvedSource(video.toString()) == previousSource)
                            clip.copy(source = info.uri, sourceDurationMs = info.durationMs, sourceName = info.name) else clip }
                        TimelineModel.validate(next, duration)
                        updateTimeline(timeline.edit(next))
                        status = "Source relinked. Verify this is the original footage and review subtitles again."
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { status = "Relink failed: ${error.message}" }
                    finally { assetImporting = false }
                }
            }
        }
        val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !busy) {
                assetImporting = true; approved = false
                exportScope.launch {
                    try {
                        val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            MediaAssets.inspect(this@MainActivity, uri, false)
                        }
                        val previous = music
                        music = if (previous != null && previous.durationMs == info.durationMs) previous.copy(source = info.uri, name = info.name)
                            else MusicTrack(info.uri, info.name, info.durationMs)
                        status = "Local music added. Review the balance with original audio."
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { status = "Music import failed: ${error.message}" }
                    finally { assetImporting = false }
                }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) pendingHomeAction = ""
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {}
                if (busy) return@rememberLauncherForActivityResult
                val current = latestBackupProject.get()
                projectLoading = true
                exportScope.launch {
                    try {
                        val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val media = MediaAssets.inspect(this@MainActivity, uri, true)
                            MetadataGate.lock.withLock { current?.let { ProjectCatalog.save(filesDir, it) } }
                            media
                        }
                        val saved = pendingRelink
                        if (saved != null) {
                            applySavedProject(saved.copy(source = uri.toString()), uri)
                            speechScope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SpeechJobs.relink(applicationContext, saved.source, uri.toString(), saved.durationMs) } }
                            status = "Original video relinked. Review framing and subtitles again."
                            return@launch
                        }
                        projectId = ProjectCatalog.newId(); projectName = info.name.filterNot(Char::isISOControl).take(80)
                        appliedSpeechId = ""; appliedSpeechUnits = 0
                        timeline = TimelineHistory(); timelineMode = false; selectedTimelineId = ""; timelinePosition = 0; music = null; timelineSubtitleTarget = ""
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
                        duration = info.durationMs
                        sourceName = info.name
                        previewPlayback.load(listOf(PreviewPlayback.Source(uri.toString())), play = true)
                        previewPlayback.play(restart = true)
                        playbackError = ""
                        status = "Video loaded"
                        when (pendingHomeAction) {
                            "edit" -> { editingMode = "Manual Editor"; advancedEditor = false; studioEditing = true; showDiscovery = false; editorTool = EditorTool.EDIT }
                            "discover" -> { studioEditing = false; showDiscovery = true; autoFindRequested = true }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { status = "Import failed: ${error.message}" }
                    finally { projectLoading = false; catalogRevision++; pendingHomeAction = "" }
                }
            }
        }
        DisposableEffect(player, timelineActive) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && !timelineActive) {
                        val actual = player.duration.coerceAtLeast(0)
                        if (expectedRestoredDuration > 0 && kotlin.math.abs(expectedRestoredDuration - actual) > 1_000) {
                            timeline = TimelineHistory(); timelineMode = false; selectedTimelineId = ""
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
                    playbackError = "${error.errorCodeName}: ${error.message}"
                    if (PreviewFailurePolicy.useCompatibleDecoder(compatiblePreview, error.errorCode)) {
                        compatiblePreview = true
                        playbackPrefs.edit().putBoolean("software-preview", true).apply()
                        status = "Decoder recovery: compatible preview enabled. Press Play to retry."
                    } else status = "Preview error. Use Reload preview, or relink the source if access expired."
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
                    pendingRelink = saved
                    val source = Uri.parse(saved.source)
                    // Check access before installing a saved editor state.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        contentResolver.openAssetFileDescriptor(source, "r")?.use { } ?: error("Saved source is unavailable")
                    }
                    applySavedProject(saved, source)
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
            draftSegments = batchResults, fillFrame = fillFrame, lastSpeechJobId = appliedSpeechId, lastSpeechUnits = appliedSpeechUnits, timelineClips = timeline.clips, timelineMode = timelineMode, selectedTimelineId = selectedTimelineId,
            currentTimelineId = timelineSubtitleTarget.takeIf { id -> timeline.clips.any { it.id == id } }.orEmpty(), music = music, captionPreset = captionPreset.name,
            projectId = projectId, projectName = projectName.ifBlank { sourceName }.filterNot(Char::isISOControl).take(80),
            captionAppearance = captionAppearance
        ) else null
        SideEffect { latestBackupProject.set(projectSnapshot) }
        LaunchedEffect(projectSnapshot, projectLoading, metadataBusy) {
            if (projectSnapshot != null && !projectLoading && !metadataBusy) {
                delay(1000)
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        MetadataGate.lock.withLock { ProjectCatalog.save(filesDir, projectSnapshot) }
                    }
                    catalogRevision++
                    projectMessage = "Project saved on this phone. Review approval is never restored."
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    projectMessage = "Project save failed: ${error.message}"
                }
            }
        }
        LaunchedEffect(studioEditing, showDiscovery, projectLoading, catalogRevision) {
            if (!projectLoading && !studioEditing && !showDiscovery) {
                recentProjects = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    MetadataGate.lock.withLock { ProjectCatalog.list(filesDir) }
                }
            }
        }
        val openSavedProject: (String) -> Unit = { id ->
            if (!busy) {
                val current = latestBackupProject.get()
                projectLoading = true
                exportScope.launch {
                    try {
                        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            MetadataGate.lock.withLock {
                                current?.let { ProjectCatalog.save(filesDir, it) }
                                ProjectCatalog.read(filesDir, id).also {
                                    contentResolver.openAssetFileDescriptor(Uri.parse(it.source), "r")?.use { }
                                        ?: error("Original video unavailable. Keep it in its original location or restore its access.")
                                }
                            }
                        }
                        applySavedProject(saved, Uri.parse(saved.source))
                        selectedTab = StudioTab.STUDIO
                        advancedEditor = false; studioEditing = true; showDiscovery = false; editorTool = EditorTool.EDIT
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { status = "Could not open project: ${error.message}" }
                    finally { projectLoading = false; catalogRevision++ }
                }
            }
        }
        LaunchedEffect(player, duration, start, end, video, timelineActive, timeline.clips, mute) {
            while (true) {
                // Import/relink can clear the timeline before this effect is cancelled.
                // Read one current immutable snapshot for the entire video/music tick.
                val clockClips = timeline.clips
                val clockTimelineActive = timelineMode && clockClips.isNotEmpty()
                val clock = if (clockTimelineActive) TimelineModel.playerPosition(
                    clockClips, player.currentMediaItemIndex, player.currentPosition) else null
                if (clock != null) {
                    val clip = clockClips[clock.index]
                    val local = clock.localMs
                    timelinePlaybackIndex = clock.index
                    timelinePosition = TimelineModel.offset(clockClips, clock.index) + local
                    previewPosition = clock.sourceMs
                    player.volume = if (mute) 0f else TimelineModel.audioGain(clip, local).coerceIn(0f, 1f)
                } else {
                    if (duration > 0 && player.isPlaying && player.currentPosition >= (duration * end).toLong()) {
                        player.pause(); player.seekTo((duration * start).toLong())
                    }
                    previewPosition = player.currentPosition.coerceAtLeast(0L)
                    player.volume = if (mute) 0f else 1f
                }
                val track = music
                val localMusic = timelinePosition - (track?.timelineStartMs ?: 0)
                val musicLength = track?.activeDuration(TimelineModel.duration(clockClips)) ?: 0
                if (clockTimelineActive && track != null && localMusic >= 0 && localMusic < musicLength && musicPlayer.mediaItemCount > 0) {
                    if (kotlin.math.abs(musicPlayer.currentPosition - localMusic) > 120) musicPlayer.seekTo(localMusic)
                    musicPlayer.volume = track.gain(timelinePosition, TimelineModel.duration(clockClips))
                    musicPlayer.playWhenReady = player.isPlaying
                } else musicPlayer.pause()
                if (auditionUntil <= android.os.SystemClock.elapsedRealtime() || player.isPlaying) {
                    if (auditionUntil > 0) { auditionUntil = 0; soundPlayer.pause(); soundPreview.reset() }
                    soundPreview.tick(if (clockTimelineActive) SoundTimeline.events(clockClips) else emptyList(),
                        timelinePosition, player.isPlaying && clockTimelineActive, soundAssets)
                }
                previewPlaying = player.isPlaying
                delay(50)
            }
        }
        val screen = androidx.compose.ui.platform.LocalConfiguration.current
        val wideWorkspace = screen.screenWidthDp >= 700
        val workspaceState = rememberSaveableStateHolder()
        val highlightsScroll = rememberScrollState()
        val homeScroll = rememberScrollState()
        val styleScroll = rememberScrollState()
        val captionsScroll = rememberScrollState()
        val campaignScroll = rememberScrollState()
        val exportScroll = rememberScrollState()
        val activeWorkspaceScroll = when (selectedTab) {
            StudioTab.STUDIO -> if (studioEditing) styleScroll else if (showDiscovery) highlightsScroll else homeScroll
            StudioTab.CAPTIONS -> captionsScroll
            StudioTab.CAMPAIGN -> campaignScroll
            StudioTab.EXPORT -> exportScroll
        }
        LaunchedEffect(speechJob?.id, speechJob?.results?.size, speechJob?.status, video, duration) {
            val job = speechJob
            val target = timeline.clips.firstOrNull { it.id == job?.targetId }
            val jobMatches = job != null && if (job.mode == SpeechJobMode.TIMELINE)
                target != null && target.resolvedSource(video?.toString().orEmpty()) == job.source && target.resolvedDuration(duration) == job.durationMs
                else job.source == video?.toString() && job.durationMs == duration
            if (job != null && jobMatches) {
                if (!job.active) status = job.message
                if (job.targetId == "quick-find" && job.status != SpeechJobStatus.COMPLETED) return@LaunchedEffect
                if (job.results.isNotEmpty() && (appliedSpeechId != job.id || appliedSpeechUnits != job.results.size)) {
                    val sameJob = appliedSpeechId == job.id
                    val grouped = job.groupedResults(job.mode != SpeechJobMode.FULL_VIDEO)
                    fun matches(index: Int): Boolean {
                        val range = job.ranges.firstOrNull { it.index == index } ?: return false
                        val draft = drafts.getOrNull(index) ?: return false
                        return draft.startMs == range.startMs && draft.endMs == range.endMs
                    }
                    when (job.mode) {
                        SpeechJobMode.FIRST_30, SpeechJobMode.FULL_VIDEO -> {
                            grouped[0]?.let { segments ->
                                val existing = if (sameJob) sourceTranscriptSegments.associateBy { it.startMs to it.endMs } else emptyMap()
                                val merged = segments.map { existing[it.startMs to it.endMs] ?: it }
                                sourceTranscriptSegments = merged
                                timelineSubtitleTarget = ""
                                timeline = timeline.copy(clips = timeline.clips.map { clip -> if (clip.resolvedSource(video.toString()) == video.toString() && clip.subtitles.isEmpty()) clip.copy(subtitles = merged.filter { it.endMs > clip.startMs && it.startMs < clip.endMs }) else clip })
                                transcriptSegments = merged
                                transcript = merged.joinToString("\n") { it.text }
                                subtitleDraftIndex = -1
                                subtitleSegments = emptyList()
                                approved = false
                            }
                        }
                        SpeechJobMode.SELECTED -> {
                            val index = job.ranges.first().index
                            grouped[index]?.takeIf { matches(index) }?.let { segments ->
                                batchResults = batchResults + (index to segments)
                                if (selectedDraft == index) {
                                    timelineSubtitleTarget = ""
                                    transcriptSegments = segments; subtitleSegments = segments; subtitleDraftIndex = index
                                    transcript = segments.joinToString("\n") { it.text }; approved = false
                                }
                            }
                        }
                        SpeechJobMode.HIGHLIGHTS -> {
                            if (job.targetId == "quick-find") {
                                val speech = grouped.values.flatten().distinctBy { it.startMs to it.endMs }.sortedBy { it.startMs }
                                drafts = TranscriptHighlights.detect(speech, duration)
                                sourceTranscriptSegments = speech
                                batchResults = drafts.mapIndexed { index, clip -> index to speech.filter { it.endMs > clip.startMs && it.startMs < clip.endMs } }.toMap()
                                selectedDraft = -1; subtitleDraftIndex = -1; subtitleSegments = emptyList()
                                timelineSubtitleTarget = ""; transcriptSegments = speech; approved = false
                                studioEditing = false; showDiscovery = true; selectedTab = StudioTab.STUDIO
                                status = "${drafts.size} speech-ranked clips from sampled moments. Tap a clip to review it."
                            } else {
                            batchResults = grouped.filterKeys { matches(it) }.mapValues { (index, segments) ->
                                val existing = if (sameJob) batchResults[index].orEmpty().associateBy { it.startMs to it.endMs } else emptyMap()
                                segments.map { existing[it.startMs to it.endMs] ?: it }
                            }
                            drafts = drafts.map { it.copy(approved = false) }; approved = false
                            }
                        }
                        SpeechJobMode.TIMELINE -> {
                            val clip = timeline.clips.firstOrNull { it.id == job.targetId }
                            val range = job.ranges.first()
                            grouped[0]?.takeIf { clip != null && clip.startMs == range.startMs && clip.endMs == range.endMs }?.let { segments ->
                                val existing = if (sameJob) clip!!.subtitles.associateBy { it.startMs to it.endMs } else emptyMap()
                                val cues = segments.map { existing[it.startMs to it.endMs] ?: it }
                                timeline = timeline.copy(clips = timeline.clips.map { if (it.id == job.targetId) it.copy(subtitles = cues) else it })
                                if (selectedTimelineId == job.targetId) {
                                    timelineSubtitleTarget = job.targetId; transcriptSegments = cues; transcript = cues.joinToString("\n") { it.text }
                                    subtitleDraftIndex = -1; subtitleSegments = emptyList()
                                }
                                approved = false
                            }
                        }
                    }
                    appliedSpeechId = job.id; appliedSpeechUnits = job.results.size
                }
            }
        }
        val previewHeight = (screen.screenHeightDp * .38f).coerceIn(200f, 380f).dp
        val previewPanel: @Composable (Boolean) -> Unit = { expanded ->
            val canvasHeight = if (expanded) (screen.screenHeightDp - 160).coerceAtLeast(170).dp else previewHeight
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (video == null) {
                    Box(Modifier.fillMaxWidth()) {
                        StudioEmptyPreview()
                        Button(enabled = !busy, onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { Text(if (pendingRelink != null) "Relink original video" else "Choose a video") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (timelineActive) "CLIP ${(timelinePlaybackIndex + 1).toString().padStart(2, '0')}" else if (selectedDraft >= 0) "CLIP ${(selectedDraft + 1).toString().padStart(2, '0')}" else "SOURCE PREVIEW", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StudioBadge(if (vertical) if (fillFrame) "9:16 · CROP" else "9:16 · FIT" else if (fillFrame) "16:9 · CROP" else "16:9 · FIT", false)
                        if (!expanded) IconButton(onClick = { fullScreenPreview = true }) { StudioGlyph(StudioGlyph.EXPAND, description = "Full screen") }
                    }
                Box(modifier = Modifier.fillMaxWidth().height(canvasHeight).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxHeight().aspectRatio(if (vertical) 9f / 16f else 16f / 9f, matchHeightConstraintsFirst = true).graphicsLayer { clip = true }, contentAlignment = Alignment.Center) {
                    val localVisualMs = if (timelineActive) playingTimelineClip?.outputOffsetMs(previewPosition - playingTimelineClip.startMs) ?: 0L else 0L
                    val visuals = playingTimelineClip?.visuals ?: ClipVisuals()
                    val point = visuals.point(localVisualMs, playingTimelineClip?.durationMs ?: 1)
                    val previewEffect = if (timelineActive) playingTimelineClip?.effect ?: "None" else effectChoice
                    val boundary = if (timelineActive) TimelineTransitions.frame(timeline.clips, timelinePlaybackIndex, localVisualMs) else TimelineTransitions.Frame()
                    val staticZoom = if (previewEffect == "Zoom") 1.12f else 1f
                    val visualOpacity = visuals.opacity(localVisualMs, playingTimelineClip?.durationMs ?: 1) * boundary.opacity
                    val renderAppearance = if (vertical) captionAppearance else captionAppearance.copy(layout = ReferenceLayout.STANDARD)
                    val reference = renderAppearance.layout
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleX = point.scale * staticZoom * boundary.scale; scaleY = point.scale * staticZoom * boundary.scale
                        translationX = point.x * size.width; translationY = point.y * size.height; alpha = visualOpacity
                    }, contentAlignment = Alignment.Center) {
                        PreviewVideo(previewBinding,
                            if (reference.videoAspect != null) Modifier.fillMaxWidth().aspectRatio(reference.videoAspect) else Modifier.fillMaxSize(),
                            crop = fillFrame || reference != ReferenceLayout.STANDARD,
                            color = if (timelineActive) playingTimelineClip?.color ?: ColorAdjustments() else ColorAdjustments(), monochrome = previewEffect == "Monochrome")
                    }
                    if (reference != ReferenceLayout.STANDARD || timelineActive || selectedDraft in drafts.indices || hookText.isNotBlank() || captionText.isNotBlank()) {
                        val playing = if (timelineActive) timeline.clips.getOrNull(timelinePlaybackIndex) else null
                        val hook = playing?.hook ?: hookText
                        val cues = playing?.subtitles ?: if (subtitleDraftIndex == selectedDraft) subtitleSegments else emptyList()
                        val activeSegment = cues.getOrNull(SubtitleTimeline.activeSegmentIndex(cues, previewPosition))
                        val caption = playing?.caption ?: captionText
                        val from = playing?.captionStartMs ?: drafts.getOrNull(selectedDraft)?.captionStartMs ?: 0
                        val until = playing?.captionEndMs ?: drafts.getOrNull(selectedDraft)?.captionEndMs ?: 0
                        val fallback = if (cues.isNotEmpty()) "" else if (until <= from || previewPosition in from until until) caption else ""
                        val displayedCaption = activeSegment?.text ?: fallback
                        if (reference != ReferenceLayout.STANDARD || hook.isNotBlank() || displayedCaption.isNotBlank()) {
                            val previewBitmap = remember(hook, displayedCaption, subtitleSize, subtitleYellow, captionPreset, renderAppearance) {
                                makeCaptionOverlay(hook, displayedCaption, subtitleSize,
                                    if (subtitleYellow) Color.YELLOW else Color.WHITE, captionPreset, renderAppearance, bitmapScale = .5f).asImageBitmap()
                            }
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Preview of time-synchronized subtitles",
                                modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f).graphicsLayer { alpha = visualOpacity },
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                }
                }

                    PreviewTransport(previewPlaying, if (timelineActive) timelinePosition else previewPosition,
                        if (timelineActive) TimelineModel.duration(timeline.clips) else duration, !busy,
                        onToggle = {
                            if (player.isPlaying) player.pause()
                            else { playbackError = ""; previewPlayback.play((duration * start).toLong(), (duration * end).toLong()) }
                        },
                        onRestart = { playbackError = ""; previewPlayback.play((duration * start).toLong(), (duration * end).toLong(), restart = true) })
                    if (playbackError.isNotBlank()) Text(playbackError, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (playbackError.isNotBlank() || !studioEditing) TextButton(enabled = !busy, onClick = {
                        musicPlayer.pause(); previewPlayback.recover(); playbackError = ""
                        status = "Preview reloaded and paused. Press Play to retry."
                    }) { Text("Reload preview") }
                }
            }
        }
        val beginFindClips: (Boolean) -> Unit = { duplicateProject ->
            if (!busy && video != null && duration > 0) {
                player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++
                showDiscovery = true; studioEditing = false; advancedEditor = false
                if (!modelReady) {
                    selectedTab = StudioTab.CAPTIONS
                    status = "Set up the speech model, then return to Find clips."
                } else {
                    val snapshot = latestBackupProject.get()
                    val needsCopy = duplicateProject || timeline.clips.isNotEmpty()
                    projectLoading = true
                    exportScope.launch {
                        try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                MetadataGate.lock.withLock { snapshot?.let { ProjectCatalog.save(filesDir, it) } }
                            }
                            val previous = snapshot ?: error("Source is still loading")
                            val next = previous.forHighlights(if (needsCopy) ProjectCatalog.newId() else previous.projectId, sourceName.take(60) + " highlights")
                            applySavedProject(next, Uri.parse(next.source))
                            val candidates = generateQualityCandidates(duration)
                            drafts = candidates; selectedDraft = -1
                            launchSpeech(SpeechJobMode.HIGHLIGHTS, candidates.mapIndexed { index, clip -> SpeechRange(index, clip.startMs, clip.endMs) }, "quick-find")
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) { status = "Could not start highlights: ${error.message}" }
                        finally { projectLoading = false; catalogRevision++ }
                    }
                }
            }
        }
        LaunchedEffect(autoFindRequested, projectLoading) {
            if (autoFindRequested && !projectLoading) {
                autoFindRequested = false
                beginFindClips(false)
            }
        }
        val showProjectHome = selectedTab == StudioTab.STUDIO && !studioEditing && !showDiscovery
        LaunchedEffect(showProjectHome) {
            if (showProjectHome) { player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++ }
        }
        BackHandler(enabled = !fullScreenPreview && (selectedTab != StudioTab.STUDIO || studioEditing || showDiscovery)) {
            if (selectedTab != StudioTab.STUDIO) selectedTab = StudioTab.STUDIO
            else { studioEditing = false; showDiscovery = false; player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++ }
        }
        val revokeCaptionApproval: () -> Unit = {
            approved = false
            drafts = drafts.map { it.copy(approved = false) }
        }
        val simpleEditor = selectedTab == StudioTab.STUDIO && studioEditing && video != null && !advancedEditor
        LaunchedEffect(simpleEditor) {
            if (simpleEditor) {
                if (timeline.clips.isEmpty()) addTimelineSelection() else timelineMode = true
            }
        }
        ClipForgeTheme {
            pendingBackup?.let { staged ->
                AlertDialog(onDismissRequest = { if (!metadataBusy) { staged.discard(); pendingBackup = null } },
                    title = { Text("Restore this backup?") },
                    text = { Text("Saved drafts: ${staged.projectCount}. Last project: ${if (staged.hasProject) "included" else "not included"}. Submissions: ${staged.submissions}. Speech checkpoint: ${if (staged.hasSpeech) "included" else "not included"}. Included local records will be replaced. Original videos and model files are not part of this backup.") },
                    confirmButton = { TextButton(enabled = !busy, onClick = {
                        metadataBusy = true
                        exportScope.launch {
                            try {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    MetadataGate.lock.withLock {
                                        MetadataBackup.restore(filesDir, staged)
                                        SpeechJobs.reloadAfterRestore(applicationContext)
                                    }
                                }
                                pendingBackup = null
                                recreate()
                            } catch (error: Exception) { pendingBackup = null; metadataBusy = false; status = "Restore failed: ${error.message}" }
                        }
                    }) { Text("Restore included records") } },
                    dismissButton = { TextButton(enabled = !busy, onClick = { staged.discard(); pendingBackup = null }) { Text("Cancel") } })
            }
            if (fullScreenPreview && video != null) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreenPreview = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
                            TextButton(onClick = { fullScreenPreview = false }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Back to editor") }
                            previewPanel(true)
                        }
                    }
                }
            }
            Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (simpleEditor) EditorTopBar(!busy, timeline.past.isNotEmpty(), timeline.future.isNotEmpty(),
                            onBack = { studioEditing = false; showDiscovery = false; player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++ }, onUndo = { updateTimeline(timeline.undo()) }, onRedo = { updateTimeline(timeline.redo()) },
                            onExport = { selectedTab = StudioTab.EXPORT }, projectName = projectName.ifBlank { sourceName })
                        else StudioHeader(BuildConfig.VERSION_NAME)
                    },
                    bottomBar = {
                        Column {
                            if (!simpleEditor || busy || playbackError.isNotBlank() || projectMessage.startsWith("Project save failed:")) StudioActivity(
                                message = if (modelDownloading) "Downloading speech model · $modelProgress%" else if (transcribing || batchRunning) "$speechPhase · ${speechElapsedSeconds}s elapsed" else if (projectMessage.startsWith("Project save failed:")) projectMessage else status,
                                active = busy || modelDownloading,
                                percent = if (modelDownloading) modelProgress else if (exporting) exportPercent else if (transcribing || batchRunning) speechPercent else -1)
                            if (simpleEditor) EditorToolBar(editorTool, !busy) { editorTool = it }
                            else StudioNavigation(selectedTab) { selectedTab = it }
                        }
                    }
                ) { innerPadding ->
                    if (simpleEditor) {
                        StudioWorkspaceLayout(modifier = Modifier.fillMaxSize().padding(innerPadding), showPreview = !fullScreenPreview,
                            wide = wideWorkspace, preview = { previewPanel(false) }) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimelineEditor(this@MainActivity, video, duration, timeline, selectedTimelineId, timelinePosition, !busy,
                                    onHistory = updateTimeline, onSelect = { id ->
                                        selectedTimelineId = id; editorTool = EditorTool.EDIT
                                        timeline.clips.firstOrNull { it.id == id }?.let { clip ->
                                            timelineSubtitleTarget = id; transcriptSegments = clip.subtitles
                                            transcript = clip.subtitles.joinToString("\n") { it.text }; subtitleDraftIndex = -1; subtitleSegments = emptyList()
                                        }
                                    }, onSeek = seekTimeline, onAddSelection = addTimelineSelection, onMessage = { status = it }, compact = true, tool = editorTool, music = music)
                                when (editorTool) {
                                    EditorTool.TEXT -> {
                                        timeline.clips.firstOrNull { it.id == selectedTimelineId }?.let { clip ->
                                            HookGeneratorControls(clip, !busy) { hook ->
                                                updateTimeline(timeline.edit(timeline.clips.map { if (it.id == clip.id) it.copy(hook = hook) else it }))
                                            }
                                        }
                                    }
                                    EditorTool.SPEED -> Unit
                                    EditorTool.EDIT -> {
                                        OutlinedButton(enabled = !busy, onClick = { timelinePicker.launch(arrayOf("video/*")) }) { Text("Add video") }
                                    }
                                    EditorTool.AUDIO -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(checked = mute, enabled = !busy, onCheckedChange = { mute = it; approved = false })
                                            Spacer(Modifier.width(8.dp)); Text("Mute original audio")
                                        }
                                        MusicTrackEditor(music, TimelineModel.duration(timeline.clips), !busy && timeline.clips.isNotEmpty(),
                                            onPick = { musicPicker.launch(arrayOf("audio/*")) }, onChange = { music = it; approved = false }, onMessage = { status = it })
                                        timeline.clips.firstOrNull { it.id == selectedTimelineId }?.let { clip ->
                                            val index = timeline.clips.indexOfFirst { it.id == clip.id }
                                            SoundControls(clip, timelinePosition - TimelineModel.offset(timeline.clips, index),
                                                timeline.clips.sumOf { it.sounds.size }, !busy, onChange = { changed ->
                                                updateTimeline(timeline.edit(timeline.clips.map { if (it.id == clip.id) changed else it }))
                                            }, onPreview = audition)
                                        }
                                    }
                                    EditorTool.CAPTIONS -> {
                                        val clip = timeline.clips.firstOrNull { it.id == selectedTimelineId }
                                        StudioCard("Auto captions", "Generate on this phone, then tap a line to correct it.") {
                                            Button(enabled = !busy && clip != null, onClick = {
                                                if (!modelReady) { selectedTab = StudioTab.CAPTIONS; status = "Download the speech model once, then return to the editor." }
                                                else if (clip != null) launchSpeech(SpeechJobMode.TIMELINE, listOf(SpeechRange(0, clip.startMs, clip.endMs)), clip.id)
                                            }) { Text(if (modelReady) "Generate captions" else "Set up auto captions") }
                                            CaptionStyleControls(captionPreset, subtitleSize, captionAppearance,
                                                if (subtitleYellow) Color.YELLOW else Color.WHITE, !busy,
                                                onPreset = { captionPreset = it; revokeCaptionApproval() },
                                                onSize = { subtitleSize = it; revokeCaptionApproval() },
                                                onAppearance = { captionAppearance = it; revokeCaptionApproval() })
                                            clip?.subtitles?.take(10)?.forEachIndexed { index, cue ->
                                                OutlinedTextField(cue.text, { text ->
                                                    val changed = clip.copy(subtitles = clip.subtitles.mapIndexed { i, item -> if (i == index) item.copy(text = text) else item })
                                                    updateTimeline(timeline.edit(timeline.clips.map { if (it.id == clip.id) changed else it }))
                                                    if (timelineSubtitleTarget == clip.id) transcriptSegments = changed.subtitles
                                                }, enabled = !busy, label = { Text("${Timecode.format(clip.outputOffsetMs(cue.startMs - clip.startMs))} · caption ${index + 1}") }, modifier = Modifier.fillMaxWidth())
                                            }
                                            if ((clip?.subtitles?.size ?: 0) > 10) TextButton(onClick = { selectedTab = StudioTab.CAPTIONS }) { Text("Edit all captions") }
                                        }
                                    }
                                    EditorTool.FORMAT -> StudioCard("Canvas") {
                                        ReferencePresetControls(captionAppearance, !busy) {
                                            captionAppearance = it; vertical = true; captionPreset = CaptionPreset.HIGHLIGHT; revokeCaptionApproval()
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = vertical, enabled = !busy, onClick = { vertical = true; approved = false }, label = { Text("9:16 vertical") })
                                            FilterChip(selected = !vertical, enabled = !busy, onClick = { vertical = false; approved = false }, label = { Text("16:9 landscape") })
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = !fillFrame, enabled = !busy, onClick = { fillFrame = false; approved = false }, label = { Text("Fit") })
                                            FilterChip(selected = fillFrame, enabled = !busy, onClick = { fillFrame = true; approved = false }, label = { Text("Fill & crop") })
                                        }
                                    }
                                    EditorTool.EFFECTS -> {
                                        timeline.clips.firstOrNull { it.id == selectedTimelineId }?.let { clip ->
                                            ColorControls(clip.color, !busy) { color ->
                                                updateTimeline(timeline.edit(timeline.clips.map { if (it.id == clip.id) it.copy(color = color) else it }))
                                            }
                                            TransitionControls(clip, timeline.clips.indexOfFirst { it.id == clip.id } < timeline.clips.lastIndex, !busy) { changed ->
                                                updateTimeline(timeline.edit(timeline.clips.map { if (it.id == clip.id) changed else it }))
                                            }
                                            MotionControls(clip, !busy, onChange = { changed ->
                                                updateTimeline(timeline.edit(timeline.clips.map { if (it.id == clip.id) changed else it }))
                                            }, onSeek = { endFrame ->
                                                player.pause()
                                                val index = timeline.clips.indexOfFirst { it.id == clip.id }
                                                seekTimeline(TimelineModel.offset(timeline.clips, index) + if (endFrame) clip.durationMs - 1 else 0)
                                            })
                                        }
                                    }
                                    EditorTool.MORE -> StudioCard("Project tools") {
                                        OutlinedTextField(value = projectName, onValueChange = { projectName = it.filterNot(Char::isISOControl).take(80) },
                                            enabled = !busy, singleLine = true, label = { Text("Project name") },
                                            placeholder = { Text(sourceName) }, modifier = Modifier.fillMaxWidth())

                                        TextButton(enabled = !busy, onClick = { relinkTimelineId = selectedTimelineId; timelineRelinker.launch(arrayOf("video/*")) }) { Text("Relink selected video") }
                                        TextButton(enabled = !busy, onClick = { backupSaver.launch("ClipForge-backup.zip") }) { Text("Save metadata backup") }
                                        TextButton(enabled = !busy, onClick = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Restore metadata backup") }
                                        TextButton(onClick = { selectedTab = StudioTab.CAMPAIGN }) { Text("Campaign tools") }
                                        TextButton(enabled = !busy, onClick = { musicPlayer.pause(); previewPlayback.recover(); playbackError = "" }) { Text("Reload preview") }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(checked = compatiblePreview, enabled = !busy, onCheckedChange = {
                                                player.pause(); compatiblePreview = it; playbackPrefs.edit().putBoolean("software-preview", it).apply(); playbackError = ""
                                            }); Text("Compatible preview decoder")
                                        }
                                        TextButton(onClick = { advancedEditor = true }) { Text("Advanced source tools") }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    } else {
                    StudioWorkspaceLayout(modifier = Modifier.fillMaxSize().padding(innerPadding),
                        showPreview = selectedTab != StudioTab.CAMPAIGN && !fullScreenPreview && !showProjectHome && video != null,
                        wide = wideWorkspace, preview = { previewPanel(false) }) {
                        workspaceState.SaveableStateProvider(selectedTab.name + if (selectedTab == StudioTab.STUDIO) if (showProjectHome) "home" else if (showDiscovery) "discovery" else "source" else "") {
                            Column(Modifier.fillMaxSize().verticalScroll(activeWorkspaceScroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (selectedTab != StudioTab.STUDIO) StudioIntro(selectedTab)
                                when (selectedTab) {
                                    StudioTab.STUDIO -> {
                                        if (showProjectHome) {
                                            ProjectHome(!busy, modelReady, video != null && duration > 0, pendingRelink != null,
                                                projectName.ifBlank { sourceName }, if (timeline.clips.isNotEmpty()) TimelineModel.duration(timeline.clips) else deliveryDuration,
                                                recentProjects, projectId,
                                                onNewProject = { pendingHomeAction = if (pendingRelink == null) "edit" else ""; picker.launch(arrayOf("video/*")) },
                                                onFindClips = { if (video == null) { pendingHomeAction = "discover"; picker.launch(arrayOf("video/*")) } else beginFindClips(true) },
                                                onContinue = { advancedEditor = false; studioEditing = true; showDiscovery = false; editorTool = EditorTool.EDIT },
                                                onOpen = openSavedProject,
                                                onImportLink = { showDiscovery = true; advancedImport = true },
                                                onProjectTools = { homeSettings = !homeSettings })
                                            if (homeSettings) StudioCard("Project tools", projectMessage) {
                                                TextButton(enabled = !busy, onClick = { backupSaver.launch("ClipForge-backup.zip") }) { Text("Save metadata backup") }
                                                TextButton(enabled = !busy, onClick = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Restore metadata backup") }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Switch(compatiblePreview, onCheckedChange = { player.pause(); compatiblePreview = it; playbackPrefs.edit().putBoolean("software-preview", it).apply() }, enabled = !busy)
                                                    Text("Compatible preview decoder", modifier = Modifier.weight(1f))
                                                }
                                                Text("ClipForge AI · " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall)
                                            }
                                        } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { showDiscovery = false; studioEditing = false; player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++ }) { StudioGlyph(StudioGlyph.BACK, description = "Back to projects") }
                                            Text(if (studioEditing) "Source tools" else "AI Highlights", style = MaterialTheme.typography.titleLarge)
                                        }

                                        if (!studioEditing || video == null) StudioCard(if (video == null) "Your workflow" else "Source footage", if (video == null) "" else "$sourceName · ${Timecode.format(duration)}") {
                if (video == null) OutlinedButton(enabled = !busy, onClick = { pendingHomeAction = "discover"; picker.launch(arrayOf("video/*")) }) { Text("Choose a video") }
                if (video != null) Button(enabled = !busy, onClick = { picker.launch(arrayOf("video/*")) }) { Text("New project") }
                TextButton(enabled = !busy, onClick = { advancedImport = !advancedImport }) { Text(if (advancedImport) "Hide direct-link import" else "Import from a direct link") }
                if (advancedImport) {
                OutlinedTextField(enabled = !busy, value = link, onValueChange = { link = it }, label = { Text("Direct HTTPS MP4 link (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = !busy && link.startsWith("https://"), onClick = {
                    downloading = true
                    status = "Downloading direct video file…"
                    val address = link
                    Thread {
                        try {
                            val current = latestBackupProject.get()
                            if (current != null) kotlinx.coroutines.runBlocking {
                                MetadataGate.lock.withLock { ProjectCatalog.save(filesDir, current) }
                            }
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
                                timeline = TimelineHistory(); timelineMode = false; selectedTimelineId = ""; timelinePosition = 0; music = null; timelineSubtitleTarget = ""
                                projectId = ProjectCatalog.newId(); projectName = output.name.take(80)
                                video = Uri.fromFile(output)
                                drafts = emptyList()
                                selectedDraft = -1
                                approved = false
                                start = 0f
                                end = 1f
                                duration = 0L
                                previewPlayback.load(listOf(PreviewPlayback.Source(Uri.fromFile(output).toString())), play = true)
                                playbackError = ""
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
                                                FilterChip(selected = studioEditing, onClick = { studioEditing = true }, label = { Text("Editor") })
                                            }
                                            if (!studioEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && duration > 0, onClick = { beginFindClips(false) }) { Text("Find clips") }
                    OutlinedButton(enabled = !busy && duration > 0, onClick = { advancedEditor = false; studioEditing = true; editorTool = EditorTool.EDIT }) { Text("Edit video") }
                }
                Text("Find clips samples moments across your video using local speech analysis. Review every result before exporting.", style = MaterialTheme.typography.bodySmall)
                if (timeline.clips.isNotEmpty()) OutlinedButton(enabled = !busy, onClick = { advancedEditor = false; studioEditing = true }) { Text("Continue editing") }
                TextButton(onClick = { showProjectTools = !showProjectTools }) { Text(if (showProjectTools) "Hide advanced tools" else "More tools") }
                if (showProjectTools) {
                if (editingMode == "AI Draft") {
                    Text("Start with up to 15 coverage suggestions, or use speech highlights after transcription. Each clip stays in review until you approve it.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = duration >= 1000L && !busy, onClick = {
                        approved = false
                        batchResults = emptyMap()
                        subtitleSegments = emptyList()
                        subtitleDraftIndex = -1
                        timelineMode = false; timelineSubtitleTarget = ""
                        video?.let { previewPlayback.load(listOf(PreviewPlayback.Source(it.toString()))) }
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
                }
                if (editingMode == "AI Draft" && drafts.isNotEmpty()) {
                    if (showProjectTools) {
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
                    }
                    Text("Your clip shortlist", style = MaterialTheme.typography.titleMedium)
                    Text("Coverage scores reflect duration and position. They are not predictions of views or engagement.", style = MaterialTheme.typography.bodySmall)
                    drafts.withIndex().sortedByDescending { (baselineDraftScore(it.value, duration).balanced * 3 + (it.value.visualScore ?: 50)) / 4 }.forEach { (index, draft) ->
                        val active = selectedDraft == index
                        val sourceFrame = rememberClipThumbnail(this@MainActivity, video, draft.startMs, enabled = !busy)
                        ClipReviewCard(index = index, draft = draft, active = active, thumbnail = sourceFrame,
                            score = (baselineDraftScore(draft, duration).balanced * 3 + (draft.visualScore ?: 50)) / 4,
                            enabled = !busy,
                            onClick = {
                                selectedDraft = index
                                studioEditing = false
                                timelineMode = false
                                start = draft.startMs.toFloat() / duration.toFloat()
                                end = draft.endMs.toFloat() / duration.toFloat()
                                timelineSubtitleTarget = ""
                                val matching = batchResults[index] ?: sourceTranscriptSegments.filter { it.startMs >= draft.startMs && it.startMs < draft.endMs }
                                transcriptSegments = matching
                                subtitleSegments = matching
                                subtitleDraftIndex = if (matching.isNotEmpty()) index else -1
                                approved = false
                                hookText = draft.hook
                                captionText = draft.caption
                                effectChoice = draft.effect
                                captionFrom = (draft.captionStartMs / 1000L).toString()
                                captionTo = (draft.captionEndMs / 1000L).toString()
                                video?.let { previewPlayback.load(listOf(PreviewPlayback.Source(it.toString())), positionMs = draft.startMs) }
                                player.seekTo(draft.startMs)
                                playbackError = ""
                                previewPlayback.play(draft.startMs, draft.endMs)
                            }
                        )
                    }
                }

                                                if (selectedDraft in drafts.indices) Button(enabled = !busy, onClick = { addTimelineSelection(); advancedEditor = false; studioEditing = true }) { Text("Edit selected clip") }
                                                OutlinedButton(enabled = !busy, onClick = { selectedTab = StudioTab.CAPTIONS }) { Text("Open speech tools") }
                                            } else {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    FilterChip(selected = !timelineMode, onClick = { timelineMode = false; approved = false }, label = { Text("Source selection") }, enabled = !busy)
                                                    FilterChip(selected = timelineMode, onClick = { timelineMode = true; approved = false }, label = { Text("Timeline editor") }, enabled = !busy)
                                                }
                                                if (timelineMode) {
                                                    OutlinedButton(enabled = !busy, onClick = { timelinePicker.launch(arrayOf("video/*")) }) { Text("Add videos to timeline") }
                                                    OutlinedButton(enabled = !busy && timeline.clips.any { it.id == selectedTimelineId }, onClick = {
                                                        relinkTimelineId = selectedTimelineId; timelineRelinker.launch(arrayOf("video/*"))
                                                    }) { Text("Relink selected source") }
                                                    TimelineEditor(this@MainActivity, video, duration, timeline, selectedTimelineId, timelinePosition, !busy,
                                                        onHistory = updateTimeline,
                                                        onSelect = { id ->
                                                            selectedTimelineId = id
                                                            timeline.clips.firstOrNull { it.id == id }?.let { clip ->
                                                                timelineSubtitleTarget = id; transcriptSegments = clip.subtitles; transcript = clip.subtitles.joinToString("\n") { it.text }
                                                                subtitleDraftIndex = -1; subtitleSegments = emptyList()
                                                            }
                                                        }, onSeek = seekTimeline, onAddSelection = addTimelineSelection, onMessage = { status = it })
                                                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled = !busy, checked = mute, onCheckedChange = { mute = it; approved = false }); Spacer(Modifier.width(8.dp)); Text("Mute all source audio") }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        FilterChip(selected = vertical, enabled = !busy, onClick = { vertical = !vertical; approved = false }, label = { Text(if (vertical) "9:16 canvas" else "16:9 canvas") })
                                                        FilterChip(selected = fillFrame, enabled = !busy, onClick = { fillFrame = !fillFrame; approved = false }, label = { Text(if (fillFrame) "Crop to fill" else "Fit in frame") })
                                                    }
                                                    MusicTrackEditor(music, TimelineModel.duration(timeline.clips), !busy && timeline.clips.isNotEmpty(),
                                                        onPick = { musicPicker.launch(arrayOf("audio/*")) },
                                                        onChange = { music = it; approved = false }, onMessage = { status = it })
                                                    Button(enabled = !busy && timeline.clips.isNotEmpty(), onClick = { selectedTab = StudioTab.EXPORT }) { Text("Review sequence export") }
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
                                                Button(enabled = !busy, onClick = addTimelineSelection) { Text("Add selection to timeline") }
                                                Button(enabled = !busy, onClick = { selectedTab = StudioTab.EXPORT }) { Text("Review this edit") }
                                                }
                                            }
                                        }
                                        if (pendingRelink != null) StudioCard("Original video needed", "Re-select the original video to restore its saved edits. A different source duration clears incompatible drafts.") {
                                            Button(enabled = !busy, onClick = { picker.launch(arrayOf("video/*")) }) { Text("Relink original source") }
                                            TextButton(enabled = !busy, onClick = { pendingRelink = null; status = "Choose a new source to start a fresh project" }) { Text("Start a new project") }
                                        }
                                        TextButton(onClick = { showProjectTools = !showProjectTools }) { Text("Project settings") }
                                        if (showProjectTools || pendingRelink != null) {
                                        Text(projectMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        StudioCard("Project backup", "Save your edits, submissions and last speech checkpoint. Media and model files stay separate.") {
                                            OutlinedButton(enabled = !busy, onClick = { backupSaver.launch("ClipForge-backup.zip") }) { Text("Save metadata backup") }
                                            TextButton(enabled = !busy, onClick = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Restore metadata backup") }
                                        }
                                        }
                                        }
                                    }
                                    StudioTab.CAPTIONS -> {
                                        StudioCard("Private by design", "Your footage stays on this phone. The speech model is downloaded once; transcription runs locally.") { StudioBadge(if (modelReady) "MODEL READY" else "MODEL NEEDED", emphasized = modelReady) }
                                        TextButton(enabled = !batchRunning, onClick = { speechAdvanced = !speechAdvanced }) { Text(if (speechAdvanced) "Hide batch & long video tools" else "Batch & long video tools") }
                                        if (speechJob != null && !speechJob.active && speechJob.status != SpeechJobStatus.COMPLETED) {
                                            StudioCard("Saved processing task", speechJob.message) {
                                                Text("${speechJob.results.size}/${speechJob.units().size} audio windows saved")
                                                Button(enabled = !busy && modelReady && ((speechJob.source == video?.toString() && speechJob.durationMs == duration) ||
                                                    (speechJob.mode == SpeechJobMode.TIMELINE && timeline.clips.any { it.id == speechJob.targetId && it.resolvedSource(video.toString()) == speechJob.source && it.resolvedDuration(duration) == speechJob.durationMs })),
                                                    onClick = { enqueueSpeech(speechJob.copy(status = SpeechJobStatus.QUEUED)) }) { Text("Resume saved transcription") }
                                            }
                                        }
                                        StudioCard("Speech & subtitles") {
                if (!modelReady || modelDownloading) {
                Text("English, Tagalog and Taglish, processed on your phone. Review recognition before publishing.", style = MaterialTheme.typography.bodySmall)
                Text("One-time model download · about 78 MB", style = MaterialTheme.typography.bodySmall)
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
                    launchSpeech(SpeechJobMode.FIRST_30, listOf(SpeechRange(0, 0, minOf(duration, 30_000))), "")
                }) { Text(if (transcribing) "Transcribing offline…" else "Transcribe first 30 seconds") }
                if (transcript.isNotBlank()) {
                    TextButton(onClick = { showTranscriptNotes = !showTranscriptNotes }) { Text(if (showTranscriptNotes) "Hide transcript notes" else "Show transcript notes") }
                    if (showTranscriptNotes) {
                    Text("Transcript notes", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(enabled = !busy, value = transcript, onValueChange = { transcript = it }, label = { Text("Transcript notes (edit timed cues below)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                }
                Button(enabled = !busy && video != null && modelReady && !transcribing && !batchRunning && !audioPreparing && (selectedDraft in drafts.indices || (timelineMode && timeline.clips.any { it.id == selectedTimelineId })), onClick = {
                    val clip = timeline.clips.firstOrNull { it.id == selectedTimelineId }
                    if (timelineMode && clip != null) launchSpeech(SpeechJobMode.TIMELINE, listOf(SpeechRange(0, clip.startMs, clip.endMs)), clip.id)
                    else drafts.getOrNull(selectedDraft)?.let { chosen ->
                        launchSpeech(SpeechJobMode.SELECTED, listOf(SpeechRange(selectedDraft, chosen.startMs, chosen.endMs)), "")
                    }
                }) { Text(if (transcribing) "Transcribing offline…" else "Transcribe selected clip") }
                if (transcriptSegments.isNotEmpty()) {
                    Text("Timed subtitle editor", style = MaterialTheme.typography.titleMedium)
                    CaptionStyleControls(captionPreset, subtitleSize, captionAppearance,
                        if (subtitleYellow) Color.YELLOW else Color.WHITE, !busy,
                        onPreset = { captionPreset = it; revokeCaptionApproval() },
                        onSize = { subtitleSize = it; revokeCaptionApproval() },
                        onAppearance = { captionAppearance = it; revokeCaptionApproval() })

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
                            val corrected = changed[segmentIndex]
                            val cueSource = timeline.clips.firstOrNull { it.id == timelineSubtitleTarget }?.resolvedSource(video.toString()) ?: video.toString()
                            timeline = timeline.copy(clips = timeline.clips.map { clip ->
                                if (clip.resolvedSource(video.toString()) != cueSource) clip
                                else if (timelineSubtitleTarget == clip.id) clip.copy(subtitles = changed)
                                else clip.copy(subtitles = clip.subtitles.map { cue -> if (cue.startMs == corrected.startMs && cue.endMs == corrected.endMs) corrected else cue })
                            })
                            if (cueSource == video.toString()) sourceTranscriptSegments = sourceTranscriptSegments.map { original ->
                                if (original.startMs == corrected.startMs && original.endMs == corrected.endMs) corrected else original
                            }
                            if (cueSource == video.toString() && selectedDraft >= 0 && subtitleDraftIndex == selectedDraft) batchResults = batchResults + (selectedDraft to changed)
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
                    launchSpeech(SpeechJobMode.HIGHLIGHTS, drafts.mapIndexed { index, draft -> SpeechRange(index, draft.startMs, draft.endMs) }, "")
                }) { Text(if (batchRunning) "Transcribing highlights…" else "Transcribe all highlight candidates") }
                if (batchRunning) {
                    LinearProgressIndicator(progress = { if (batchTotal > 0) batchProgress.toFloat() / batchTotal else 0f }, modifier = Modifier.fillMaxWidth())
                    Text(status)
                    Text(speechPhase)
                    if (speechPercent < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { speechPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Elapsed: ${speechElapsedSeconds / 60}m ${speechElapsedSeconds % 60}s • ${if (speechPercent < 0) "Preparing current window" else "$speechPercent% of current window"}", style = MaterialTheme.typography.bodySmall)
                    Text("${batchProgress}/${batchTotal} audio windows completed", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { SpeechJobs.pause() }) { Text("Stop after current audio window") }
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
                            timelineSubtitleTarget = ""; timelineMode = false
                            transcriptSegments = segments
                            subtitleSegments = segments
                            transcript = segments.joinToString(" ") { it.text }
                            captionText = transcript
                            approved = false
                            video?.let { previewPlayback.load(listOf(PreviewPlayback.Source(it.toString())), positionMs = chosen.startMs) }
                            player.seekTo(chosen.startMs)
                            status = "Review subtitles for clip ${index + 1}, then approve before export"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Review clip ${index + 1} • ${segments.size} subtitles") }
                    }
                }
                Text("Full-video transcription (optional)", style = MaterialTheme.typography.titleMedium)
                Text("Runs in 30-second chunks. You can switch apps while processing. Pause finishes the current window; Resume continues from saved progress.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy && video != null && modelReady && duration > 0 && !batchRunning && !transcribing && !audioPreparing, onClick = {
                    launchSpeech(SpeechJobMode.FULL_VIDEO, listOf(SpeechRange(0, 0, duration)), "")
                }) { Text(if (batchRunning) "Processing offline…" else "Transcribe full video (optional)") }
                }
                val primarySpeech = sourceTranscriptSegments.ifEmpty {
                    batchResults.values.flatten().distinctBy { it.startMs to it.endMs }.sortedBy { it.startMs }
                }.ifEmpty {
                    val targetSource = timeline.clips.firstOrNull { it.id == timelineSubtitleTarget }?.resolvedSource(video.toString())
                    if (timelineSubtitleTarget.isBlank() || targetSource == video.toString()) transcriptSegments else emptyList()
                }
                Button(enabled = !busy && primarySpeech.isNotEmpty() && duration > 0, onClick = {
                    val speech = primarySpeech.toList()
                    val sourceDuration = duration
                    approved = false
                    analyzing = true
                    status = "Ranking speech locally…"
                    Thread {
                        try {
                            val detected = TranscriptHighlights.detect(speech, sourceDuration)
                            runOnUiThread {
                                drafts = detected
                                sourceTranscriptSegments = speech
                                batchResults = emptyMap()
                                selectedDraft = -1
                                subtitleDraftIndex = -1
                                subtitleSegments = emptyList()
                                editingMode = "AI Draft"
                                timelineMode = false; timelineSubtitleTarget = ""
                                transcriptSegments = sourceTranscriptSegments
                                video?.let { previewPlayback.load(listOf(PreviewPlayback.Source(it.toString())), positionMs = (duration * start).toLong()) }
                                player.pause()
                                selectedTab = StudioTab.STUDIO
                                studioEditing = false; showDiscovery = true
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
                                        if (video == null) {
                                            StudioCard("Start with a moment", "Import footage and shape a clip before reviewing its export.") {
                                                Button(onClick = { selectedTab = StudioTab.STUDIO }) { Text("Go to Studio") }
                                                OutlinedButton(enabled = false, onClick = {}) { Text("Export approved MP4") }
                                            }
                                        } else {
                                        ExportSummary(projectName.ifBlank { sourceName }, deliveryDuration, vertical,
                                            if (timelineMode) timeline.clips.size else 1,
                                            if (timelineMode) TimelineModel.subtitles(timeline.clips).size else if (subtitleDraftIndex == selectedDraft) subtitleSegments.size else 0,
                                            mute, if (timelineMode) music?.name else null, approved)
                                        StudioCard("Ready when you are") {
                Text("Export review", style = MaterialTheme.typography.titleMedium)
                Text("Check framing, audio and subtitle timing in the preview. Approval applies to this edit and is cleared when you change it.", style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(checked = approved, enabled = !busy && campaignIssues.isEmpty() && exportReady, onCheckedChange = { value -> approved = value; if (selectedDraft in drafts.indices) drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(approved = value) } })
                    Text("I reviewed this edit and approve its export", modifier = Modifier.weight(1f))
                }
                Button(enabled = !busy && campaignIssues.isEmpty() && exportReady && approved, onClick = {
                    if (!approved || !exportReady || busy || campaignIssues.isNotEmpty()) return@Button
                    val source = video ?: return@Button
                    val outputDir = (getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(filesDir, "exports")).apply { mkdirs() }
                    val output = File(outputDir, "clipforge_${System.currentTimeMillis()}.mp4")
                    val selectedDraftData = drafts.getOrNull(selectedDraft)
                    val exportClips = if (timelineMode) timeline.clips else listOf(TimelineClip(
                        "selection", (duration * start).toLong(), (duration * end).toLong(),
                        hook = hookText, caption = captionText,
                        captionStartMs = selectedDraftData?.captionStartMs ?: (duration * start).toLong(),
                        captionEndMs = selectedDraftData?.captionEndMs ?: (duration * end).toLong(),
                        effect = effectChoice,
                        subtitles = if (selectedDraft >= 0 && subtitleDraftIndex == selectedDraft) subtitleSegments else emptyList(),
                        source = source.toString(), sourceDurationMs = duration))
                    val exportMusic = if (timelineMode) music else null
                    exportPercent = 0
                    exporting = true
                    status = "Exporting…"
                    val requestToken = Any()
                    activeExportToken = requestToken
                    val transformer = Transformer.Builder(this@MainActivity).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory())
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
                    player.pause(); musicPlayer.pause(); soundPlayer.pause(); auditionUntil = 0; auditionRequest++
                    exportScope.launch {
                    try {
                        val exportSounds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            MediaAssets.checkTimeline(this@MainActivity, source.toString(), duration, exportClips, exportMusic)
                            if (SoundTimeline.events(exportClips).isNotEmpty()) SoundAssets.ensure(filesDir) else emptyMap()
                        }
                        if (activeExportToken !== requestToken) return@launch
                        transformer.start(TimelineExport.composition(source, exportClips, vertical, fillFrame, mute,
                            subtitleSize, if (subtitleYellow) Color.YELLOW else Color.WHITE, exportMusic, captionPreset, captionAppearance, exportSounds), output.absolutePath)
                    }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (e: Exception) { activeExportToken = null; output.delete(); activeTransformer = null; exporting = false; approved = false; status = "Export failed: ${e.message}" }
                    }
                }) { Text(if (exporting) "Exporting approved clip…" else "Export approved MP4") }
                OutlinedButton(enabled = !busy && approved && campaignIssues.isEmpty() && (if (timelineMode) TimelineModel.subtitles(timeline.clips).isNotEmpty() else subtitleDraftIndex == selectedDraft && selectedDraft >= 0 && subtitleSegments.isNotEmpty()), onClick = {
                    pendingSrt = if (timelineMode) SubtitleSrt.render(TimelineModel.subtitles(timeline.clips), 0, TimelineModel.duration(timeline.clips))
                        else SubtitleSrt.render(subtitleSegments, (duration * start).toLong(), (duration * end).toLong())
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
}



