package com.clipforge.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import android.text.format.Formatter
import java.net.URL
import java.net.HttpURLConnection
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

data class HighlightDraft(val startMs: Long, val endMs: Long, val approved: Boolean = false)

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
        var vertical by remember { mutableStateOf(true) }
        var mute by remember { mutableStateOf(false) }
        var editingMode by remember { mutableStateOf("Manual Editor") }
        var cloudConsent by remember { mutableStateOf(false) }
        var cloudDialog by remember { mutableStateOf(false) }
        var approved by remember { mutableStateOf(false) }
        var drafts by remember { mutableStateOf<List<HighlightDraft>>(emptyList()) }
        var selectedDraft by remember { mutableIntStateOf(-1) }
        var status by remember { mutableStateOf("Select a video to begin") }
        var link by remember { mutableStateOf("") }
        var downloading by remember { mutableStateOf(false) }
        var exporting by remember { mutableStateOf(false) }
        val player = remember { ExoPlayer.Builder(this).build() }
        DisposableEffect(player) { onDispose { player.release() } }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {}
                video = uri
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
                    if (playbackState == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player, duration, start, end, video) {
            while (true) {
                if (duration > 0 && player.isPlaying && player.currentPosition >= (duration * end).toLong()) {
                    player.pause()
                    player.seekTo((duration * start).toLong())
                }
                delay(150)
            }
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ClipForge AI", style = MaterialTheme.typography.headlineMedium)
                Text("Mobile editor • Build #15 quality-first drafts")
                Text("Analysis privacy", style = MaterialTheme.typography.titleMedium)
                Text("Offline by default. No video is uploaded for analysis in this build.", style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(checked = cloudConsent, onCheckedChange = { enabled ->
                        if (enabled) cloudDialog = true else cloudConsent = false
                    })
                    Text("Allow optional cloud AI for this session")
                }
                if (cloudDialog) {
                    AlertDialog(
                        onDismissRequest = { cloudDialog = false },
                        title = { Text("Optional cloud AI permission") },
                        text = { Text("Future cloud analysis may transmit selected video/audio to an external processor. This prototype does not upload media or connect to an AI provider. Permission is off by default and is not saved between sessions.") },
                        confirmButton = { TextButton(onClick = { cloudConsent = true; cloudDialog = false }) { Text("Allow for this session") } },
                        dismissButton = { TextButton(onClick = { cloudDialog = false }) { Text("Keep offline") } }
                    )
                }
                Text(if (cloudConsent) "Cloud permission granted, but cloud processing is not available yet." else "Offline-only analysis mode active.", style = MaterialTheme.typography.bodySmall)
                Text("Editing mode", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = editingMode == "Manual Editor", onClick = { editingMode = "Manual Editor"; selectedDraft = -1; approved = false }, label = { Text("Manual Editor") })
                    FilterChip(selected = editingMode == "AI Draft", onClick = { editingMode = "AI Draft"; approved = false; selectedDraft = -1 }, label = { Text("AI Draft") })
                }
                if (editingMode == "AI Draft") {
                    Text("Quality-first prototype: creates up to 15 overlapping 20-second candidates covering the entire video. No speech, visual or audio AI detection yet. Review each segment individually.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = duration >= 1000L && !exporting, onClick = {
                        approved = false
                        drafts = generateQualityCandidates(duration)
                        selectedDraft = 0
                        start = 0f
                        end = drafts.first().endMs.toFloat() / duration.toFloat()
                        status = "${drafts.size} draft segments created. These are overlapping coverage candidates, NOT AI-ranked highlights."
                        player.seekTo(0)
                    }) { Text("Generate quality-first candidates") }
                }
                if (editingMode == "AI Draft" && drafts.isNotEmpty()) {
                    Text("Draft segments — tap to preview and edit", style = MaterialTheme.typography.titleMedium)
                    Text("Balanced baseline score = clarity + context + pacing + neutral engagement, each weighted 25%. This is a duration/position heuristic, not an AI prediction.", style = MaterialTheme.typography.bodySmall)
                    drafts.withIndex().sortedByDescending { baselineDraftScore(it.value, duration).balanced }.forEach { (index, draft) ->
                        val active = selectedDraft == index
                        OutlinedButton(
                            onClick = {
                                selectedDraft = index
                                start = draft.startMs.toFloat() / duration.toFloat()
                                end = draft.endMs.toFloat() / duration.toFloat()
                                approved = draft.approved
                                player.seekTo(draft.startMs)
                                player.play()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clip ${index + 1} • Baseline ${baselineDraftScore(draft, duration).balanced}/100 • ${draft.startMs / 1000}s–${draft.endMs / 1000}s • ${if (draft.approved) "Approved" else "Review"}${if (active) " (Selected)" else ""}")
                        }
                    }
                }
                Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Import downloaded video / gallery") }
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Direct HTTPS MP4 link (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = !downloading && link.startsWith("https://"), onClick = {
                    downloading = true
                    status = "Downloading direct video file…"
                    val address = link
                    Thread {
                        try {
                            val connection = URL(address).openConnection() as HttpURLConnection
                            connection.connectTimeout = 15000
                            connection.readTimeout = 30000
                            connection.instanceFollowRedirects = true
                            connection.connect()
                            val type = connection.contentType ?: ""
                            if (!type.startsWith("video/") && !type.startsWith("application/octet-stream")) error("This link is not a direct video file. Download the video first, then import it.")
                            if (connection.contentLengthLong > 1_000_000_000L) error("Video exceeds the 1 GB download limit")
                            val output = File(cacheDir, "source_video.mp4")
                            connection.inputStream.use { input ->
                                output.outputStream().use { stream ->
                                    val buffer = ByteArray(64 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > 1_000_000_000L) error("Video exceeds the 1 GB download limit")
                                        stream.write(buffer, 0, count)
                                    }
                                }
                            }
                            connection.disconnect()
                            runOnUiThread {
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
                AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().height(260.dp))
                Text("Duration: ${duration / 1000}s")
                Button(enabled = duration > 0, onClick = { player.seekTo((duration * start).toLong()); player.play() }) { Text("Preview from trim start") }
                Text("Trim start: ${(start * duration / 1000).toInt()}s")
                Slider(value = start, onValueChange = { approved = false; if (selectedDraft >= 0) drafts = drafts.toMutableList().also { list -> list[selectedDraft] = list[selectedDraft].copy(startMs = (duration * it.coerceAtMost(end - 0.01f)).toLong(), approved = false) }; start = it.coerceAtMost(end - 0.01f) }, valueRange = 0f..0.99f, enabled = duration > 0)
                Text("Trim end: ${(end * duration / 1000).toInt()}s")
                Slider(value = end, onValueChange = { approved = false; if (selectedDraft >= 0) drafts = drafts.toMutableList().also { list -> list[selectedDraft] = list[selectedDraft].copy(endMs = (duration * it.coerceAtLeast(start + 0.01f)).toLong(), approved = false) }; end = it.coerceAtLeast(start + 0.01f) }, valueRange = 0.01f..1f, enabled = duration > 0)
                Row { Checkbox(checked = vertical, onCheckedChange = { vertical = it; approved = false; drafts = drafts.map { draft -> draft.copy(approved = false) } }); Text("9:16 output canvas") }
                Row { Checkbox(checked = mute, onCheckedChange = { mute = it; approved = false; drafts = drafts.map { draft -> draft.copy(approved = false) } }); Text("Mute original audio") }
                HorizontalDivider()
                Text("Review and approve", style = MaterialTheme.typography.titleMedium)
                Text("Preview the selected segment and verify framing, audio and duration before exporting.", style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(checked = approved, enabled = video != null && duration > 0 && !exporting && (editingMode != "AI Draft" || selectedDraft >= 0), onCheckedChange = { value -> approved = value; if (selectedDraft >= 0) drafts = drafts.toMutableList().also { it[selectedDraft] = it[selectedDraft].copy(approved = value) } })
                    Text("I reviewed and approve this clip for export")
                }
                Button(enabled = video != null && duration > 0 && approved && !exporting && (editingMode != "AI Draft" || selectedDraft >= 0), onClick = {
                    val source = video ?: return@Button
                    val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
                    val output = File(outputDir, "clipforge_${System.currentTimeMillis()}.mp4")
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((duration * start).toLong())
                        .setEndPositionMs((duration * end).toLong())
                        .build()
                    val item = MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build()
                    val effects: List<Effect> = if (vertical) listOf(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT)) else emptyList()
                    val edited = EditedMediaItem.Builder(item).setRemoveAudio(mute).setEffects(Effects(emptyList(), effects)).build()
                    exporting = true
                    status = "Exporting…"
                    val transformer = Transformer.Builder(this@MainActivity)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                                runOnUiThread {
                                    exporting = false
                                    status = "Saved: ${output.absolutePath}"
                                    Toast.makeText(this@MainActivity, "Export complete", Toast.LENGTH_LONG).show()
                                }
                            }
                            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                                runOnUiThread { exporting = false; status = "Export failed: ${exportException.message}" }
                            }
                        }).build()
                    try { transformer.start(edited, output.absolutePath) }
                    catch (e: Exception) { exporting = false; status = "Export failed: ${e.message}" }
                }) { Text(if (exporting) "Exporting…" else "Export MP4") }
                Text(status, style = MaterialTheme.typography.bodySmall)
                Text("Exports are saved in the app-specific Movies directory.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}