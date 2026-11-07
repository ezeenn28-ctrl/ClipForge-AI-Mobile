package com.clipforge.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
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
        var status by remember { mutableStateOf("Select a video to begin") }
        var exporting by remember { mutableStateOf(false) }
        val player = remember { ExoPlayer.Builder(this).build() }
        DisposableEffect(player) { onDispose { player.release() } }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {}
                video = uri
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
        MaterialTheme(colorScheme = darkColorScheme()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ClipForge AI", style = MaterialTheme.typography.headlineMedium)
                Text("Mobile editor • Build #1")
                Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Import video") }
                AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().height(260.dp))
                Text("Duration: ${duration / 1000}s")
                Text("Trim start: ${(start * duration / 1000).toInt()}s")
                Slider(value = start, onValueChange = { start = it.coerceAtMost(end - 0.01f) }, valueRange = 0f..0.99f, enabled = duration > 0)
                Text("Trim end: ${(end * duration / 1000).toInt()}s")
                Slider(value = end, onValueChange = { end = it.coerceAtLeast(start + 0.01f) }, valueRange = 0.01f..1f, enabled = duration > 0)
                Row { Checkbox(checked = vertical, onCheckedChange = { vertical = it }); Text("9:16 output canvas") }
                Row { Checkbox(checked = mute, onCheckedChange = { mute = it }); Text("Mute original audio") }
                Button(enabled = video != null && duration > 0 && !exporting, onClick = {
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
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                runOnUiThread {
                                    exporting = false
                                    status = "Saved: ${output.absolutePath}"
                                    Toast.makeText(this@MainActivity, "Export complete", Toast.LENGTH_LONG).show()
                                }
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
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