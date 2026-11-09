package com.clipforge.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

@Composable
fun MusicTrackEditor(track: MusicTrack?, sequenceMs: Long, enabled: Boolean,
                     onPick: () -> Unit, onChange: (MusicTrack?) -> Unit, onMessage: (String) -> Unit) {
    StudioCard("Music track", "Local file · Original sound keeps its per-clip volume") {
        OutlinedButton(enabled = enabled, onClick = onPick) { Text(if (track == null) "Add local music" else "Replace or relink music") }
        if (track != null) {
            Text(track.name, maxLines = 2)
            val accent = MaterialTheme.colorScheme.primary
            val background = MaterialTheme.colorScheme.surfaceVariant
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                drawRect(background)
                val scale = size.width / sequenceMs.coerceAtLeast(1)
                drawRect(accent.copy(alpha = .7f), Offset(track.timelineStartMs.coerceAtMost(sequenceMs) * scale, 4.dp.toPx()),
                    Size(track.activeDuration(sequenceMs) * scale, 16.dp.toPx()))
            }
            Text("${Timecode.precise(track.timelineStartMs)} — ${Timecode.precise(track.timelineStartMs + track.activeDuration(sequenceMs))} on timeline", style = MaterialTheme.typography.bodySmall)
            var from by remember(track.source, track.startMs) { mutableStateOf(Timecode.precise(track.startMs)) }
            var until by remember(track.source, track.endMs) { mutableStateOf(Timecode.precise(track.endMs)) }
            var offset by remember(track.source, track.timelineStartMs) { mutableStateOf(Timecode.precise(track.timelineStartMs)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it.take(16) }, Modifier.weight(1f), enabled = enabled, label = { Text("Music in") })
                OutlinedTextField(until, { until = it.take(16) }, Modifier.weight(1f), enabled = enabled, label = { Text("Music out") })
            }
            OutlinedTextField(offset, { offset = it.take(16) }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Start on timeline") })
            OutlinedButton(enabled = enabled, onClick = {
                try {
                    val changed = track.copy(startMs = Timecode.parse(from) ?: error("Invalid music in point"),
                        endMs = Timecode.parse(until) ?: error("Invalid music out point"), timelineStartMs = Timecode.parse(offset) ?: error("Invalid placement"))
                    changed.validate()
                    require(changed.activeDuration(sequenceMs) > 0) { "Place music before the sequence ends" }
                    onChange(changed)
                } catch (error: Exception) { onMessage(error.message ?: "Invalid music range") }
            }) { Text("Apply music timing") }
            Text("Music volume · ${(track.volume * 100).toInt()}%")
            Slider(value = track.volume, onValueChange = { onChange(track.copy(volume = it)) }, valueRange = 0f..1f, enabled = enabled)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = track.fadeInMs > 0, onClick = { onChange(track.copy(fadeInMs = if (track.fadeInMs > 0) 0 else 1000)) }, enabled = enabled, label = { Text("Music fade in") })
                FilterChip(selected = track.fadeOutMs > 0, onClick = { onChange(track.copy(fadeOutMs = if (track.fadeOutMs > 0) 0 else 1000)) }, enabled = enabled, label = { Text("Music fade out") })
            }
            Text("Music plays once and stops at its trim or the sequence end. It does not extend the video.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = enabled, onClick = { onChange(null) }) { Text("Remove music") }
        }
    }
}
