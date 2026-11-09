package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A real sequence editor: clip, caption and original-audio rows share one clock. */
@Composable
fun TimelineEditor(
    context: Context,
    source: Uri?,
    sourceDurationMs: Long,
    history: TimelineHistory,
    selectedId: String,
    playheadMs: Long,
    enabled: Boolean,
    onHistory: (TimelineHistory) -> Unit,
    onSelect: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onAddSelection: () -> Unit,
    onMessage: (String) -> Unit
) {
    val seekRequests = remember { kotlinx.coroutines.channels.Channel<Long>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    val latestSeek by rememberUpdatedState(onSeek)
    LaunchedEffect(seekRequests) {
        for (position in seekRequests) { latestSeek(position); kotlinx.coroutines.delay(33) }
    }
    var snapping by remember { mutableStateOf(true) }
    val clips = history.clips
    val total = TimelineModel.duration(clips)
    val selectedIndex = clips.indexOfFirst { it.id == selectedId }.let { if (it < 0 && clips.isNotEmpty()) 0 else it }
    val selected = clips.getOrNull(selectedIndex)
    var zoom by remember { mutableFloatStateOf(12f) }
    val density = LocalDensity.current
    val scale = minOf(zoom, if (total > 0) 8_000_000f / total else zoom) / 1000f // dp per millisecond
    val trackWidth = (total * scale).coerceAtLeast(300f).dp
    val trackScroll = rememberScrollState()
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    fun scrub(time: Long) {
        seekRequests.trySend(TimelineTouch.seek(clips, time, snapping, minOf(150L, (8 / scale).toLong())))
    }
    fun edit(next: List<TimelineClip>) {
        try { TimelineModel.validate(next, sourceDurationMs); onHistory(history.edit(next)) }
        catch (error: Exception) { onMessage(error.message ?: "Timeline change failed") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Timeline · ${Timecode.precise(total)}", style = MaterialTheme.typography.titleMedium)
            Text("${clips.size} cuts", style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = snapping, onClick = { snapping = !snapping }, enabled = enabled, label = { Text("Snap") })
            OutlinedButton(enabled = enabled, onClick = onAddSelection) { Text("Add selection") }
            OutlinedButton(enabled = enabled && history.past.isNotEmpty(), onClick = { onHistory(history.undo()) }) { Text("Undo") }
            OutlinedButton(enabled = enabled && history.future.isNotEmpty(), onClick = { onHistory(history.redo()) }) { Text("Redo") }
        }
        if (clips.isEmpty()) {
            Text("Add the current source selection to start a sequence. Your originals remain unchanged.")
            return@Column
        }
        Box(Modifier.fillMaxWidth().background(surface, RoundedCornerShape(14.dp)).horizontalScroll(trackScroll)) {
            Column(Modifier.width(trackWidth).padding(vertical = 6.dp)) {
                Canvas(Modifier.fillMaxWidth().height(30.dp)
                    .pointerInput(total, scale, enabled, snapping, clips) {
                        detectTapGestures { if (enabled) scrub((it.x / density.density / scale).toLong().coerceIn(0, total)) }
                    }
                    .pointerInput(total, scale, enabled, snapping, clips) {
                        detectDragGestures(onDragStart = { if (enabled) scrub((it.x / density.density / scale).toLong().coerceIn(0, total)) }) { change, _ ->
                            if (enabled) { change.consume(); scrub((change.position.x / density.density / scale).toLong().coerceIn(0, total)) }
                        }
                    }) {
                    val tickSeconds = when { scale * 1000 < 1 -> 60; scale * 1000 < 4 -> 30; scale * 1000 < 12 -> 5; else -> 1 }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; textSize = 9.sp.toPx() }
                    var second = 0L
                    while (second * 1000 <= total) {
                        val x = second * 1000 * scale * density.density
                        drawLine(outline, Offset(x, size.height - 7.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                        if (second % (tickSeconds * 2) == 0L) drawContext.canvas.nativeCanvas.drawText(Timecode.format(second * 1000), x + 3.dp.toPx(), 12.dp.toPx(), paint)
                        second += tickSeconds
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    clips.forEachIndexed { index, clip ->
                        val thumbnail = rememberClipThumbnail(context, source, clip.startMs, enabled)
                        Surface(onClick = { onSelect(clip.id); onSeek(TimelineModel.offset(clips, index)) }, enabled = enabled,
                            modifier = Modifier.width((clip.durationMs * scale).dp).height(64.dp), shape = RoundedCornerShape(8.dp),
                            color = if (clip.id == selected?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(if (clip.id == selected?.id) 2.dp else 1.dp, if (clip.id == selected?.id) accent else outline)) {
                            Box {
                                if (thumbnail != null) Image(thumbnail.asImageBitmap(), "Timeline clip ${index + 1} source frame", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Text("${index + 1} · ${Timecode.format(clip.durationMs)}", Modifier.background(Color.Black.copy(alpha = .7f)).padding(5.dp), fontSize = 10.sp, color = Color.White, maxLines = 1)
                            }
                        }
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                    drawRect(outline.copy(alpha = .4f))
                    TimelineModel.subtitles(clips).forEach { cue ->
                        drawRoundRect(accent.copy(alpha = .7f), Offset(cue.startMs * scale * density.density, 7.dp.toPx()),
                            androidx.compose.ui.geometry.Size(((cue.endMs - cue.startMs) * scale * density.density).coerceAtLeast(1f), 18.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    }
                }
                Row {
                    clips.forEach { clip ->
                        Box(Modifier.width((clip.durationMs * scale).dp).height(28.dp).background(Color(0xFF243950)).border(1.dp, outline)) {
                            Text(if (clip.volume == 0f) "Muted" else "Original audio", Modifier.padding(5.dp), fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
            Canvas(Modifier.width(trackWidth).height(166.dp)) {
                val x = playheadMs.coerceIn(0, total) * scale * density.density
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        Text("Video / captions / original audio · Drag the ruler to scrub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = playheadMs.coerceIn(0, total).toFloat(), onValueChange = { scrub(it.toLong()) }, valueRange = 0f..total.coerceAtLeast(1).toFloat(), enabled = enabled)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Zoom", style = MaterialTheme.typography.bodySmall)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 4f..40f, modifier = Modifier.weight(1f), enabled = enabled)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = {
                try { edit(TimelineModel.split(clips, playheadMs)) }
                catch (error: Exception) { onMessage(error.message ?: "Move the playhead inside a clip") }
            }) { Text("Split") }
            OutlinedButton(enabled = enabled && selected != null, onClick = {
                selected?.let { edit(clips.take(selectedIndex + 1) + it.copy(id = java.util.UUID.randomUUID().toString()) + clips.drop(selectedIndex + 1)) }
            }) { Text("Duplicate") }
            OutlinedButton(enabled = enabled && selected != null, onClick = { edit(clips.filterIndexed { i, _ -> i != selectedIndex }) }) { Text("Remove") }
            OutlinedButton(enabled = enabled && selectedIndex > 0, onClick = { edit(TimelineModel.move(clips, selectedIndex, -1)) }) { Text("Earlier") }
            OutlinedButton(enabled = enabled && selectedIndex >= 0 && selectedIndex < clips.lastIndex, onClick = { edit(TimelineModel.move(clips, selectedIndex, 1)) }) { Text("Later") }
        }
        selected?.let { clip ->
            var from by remember(clip.id, clip.startMs) { mutableStateOf(Timecode.precise(clip.startMs)) }
            var until by remember(clip.id, clip.endMs) { mutableStateOf(Timecode.precise(clip.endMs)) }
            fun update(changed: TimelineClip) = edit(clips.map { if (it.id == clip.id) changed else it })
            StudioCard("Clip ${selectedIndex + 1} · source range") {
                SourceTrimHandles(clip, sourceDurationMs,
                    clips.flatMap { listOf(it.startMs, it.endMs) } + clip.subtitles.flatMap { listOf(it.startMs, it.endMs) } + listOf(0L, sourceDurationMs),
                    snapping, enabled, onCommit = { update(it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = from, onValueChange = { from = it.take(16) }, label = { Text("Source in") }, enabled = enabled, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = until, onValueChange = { until = it.take(16) }, label = { Text("Source out") }, enabled = enabled, modifier = Modifier.weight(1f))
                }
                OutlinedButton(enabled = enabled, onClick = {
                    val a = Timecode.parse(from); val b = Timecode.parse(until)
                    if (a == null || b == null || !Timecode.validTrim(a, b, sourceDurationMs)) onMessage("Enter a valid source range")
                    else update(clip.copy(startMs = a, endMs = b, fadeInMs = minOf(clip.fadeInMs, b - a), fadeOutMs = minOf(clip.fadeOutMs, b - a)))
                }) { Text("Apply clip trim") }
                OutlinedTextField(value = clip.hook, onValueChange = { update(clip.copy(hook = it.take(65))) }, label = { Text("Clip hook") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None", "Zoom", "Monochrome").forEach { effect -> FilterChip(selected = clip.effect == effect, onClick = { update(clip.copy(effect = effect)) }, enabled = enabled, label = { Text(effect) }) }
                }
                Text("Volume · ${(clip.volume * 100).toInt()}%")
                Slider(value = clip.volume, onValueChange = { update(clip.copy(volume = it)) }, valueRange = 0f..1f, enabled = enabled)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = clip.fadeInMs > 0, onClick = { update(clip.copy(fadeInMs = if (clip.fadeInMs > 0) 0 else minOf(1000, clip.durationMs))) }, enabled = enabled, label = { Text("Fade in · 1s") })
                    FilterChip(selected = clip.fadeOutMs > 0, onClick = { update(clip.copy(fadeOutMs = if (clip.fadeOutMs > 0) 0 else minOf(1000, clip.durationMs))) }, enabled = enabled, label = { Text("Fade out · 1s") })
                }
            }
        }
    }
}
