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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

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
    onMessage: (String) -> Unit,
    compact: Boolean = false,
    tool: EditorTool = EditorTool.EDIT,
    music: MusicTrack? = null,
    showTracks: Boolean = true
) {
    val seekRequests = remember { kotlinx.coroutines.channels.Channel<Long>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    val latestSeek by rememberUpdatedState(onSeek)
    LaunchedEffect(seekRequests) {
        for (position in seekRequests) { latestSeek(position); kotlinx.coroutines.delay(33) }
    }
    var tracksExpanded by remember(tool) { mutableStateOf(false) }
    val tracksVisible = showTracks || tracksExpanded
    var snapping by remember { mutableStateOf(true) }
    val clips = history.clips
    val soundEvents = remember(clips) { SoundTimeline.events(clips) }
    val total = TimelineModel.duration(clips)
    val selectedIndex = clips.indexOfFirst { it.id == selectedId }.let { if (it < 0 && clips.isNotEmpty()) 0 else it }
    val selected = clips.getOrNull(selectedIndex)
    var zoom by remember { mutableFloatStateOf(12f) }
    val density = LocalDensity.current
    var viewportDp by remember { mutableFloatStateOf(300f) }
    val fitZoom = if (total > 0) viewportDp * 1000f / total else zoom
    val scale = minOf(maxOf(zoom, fitZoom), if (total > 0) 8_000_000f / total else zoom) / 1000f
    val trackWidth = (total * scale).coerceAtLeast(viewportDp).dp
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
        if (compact && !showTracks) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                clips.forEachIndexed { index, clip ->
                    FilterChip(selected = clip.id == selected?.id, enabled = enabled,
                        onClick = { onSelect(clip.id); onSeek(TimelineModel.offset(clips, index)) }, label = { Text("Clip ${index + 1}") })
                }
                TextButton(onClick = { tracksExpanded = !tracksExpanded }) { Text(if (tracksExpanded) "Hide timeline" else "Show timeline") }
            }
        }
        if (tracksVisible) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Timeline", style = MaterialTheme.typography.titleMedium)
            Text("${Timecode.precise(total)} · ${clips.size} cuts", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = snapping, onClick = { snapping = !snapping }, enabled = enabled, label = { Text("Snap") })
            TextButton(enabled = enabled, onClick = { zoom = (zoom / 1.5f).coerceAtLeast(4f) },
                modifier = Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
            TextButton(enabled = enabled, onClick = { zoom = (zoom * 1.5f).coerceAtMost(40f) },
                modifier = Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
            if (!compact) OutlinedButton(enabled = enabled, onClick = onAddSelection) { Text("Add selection") }
            if (!compact) OutlinedButton(enabled = enabled && history.past.isNotEmpty(), onClick = { onHistory(history.undo()) }) { Text("Undo") }
            if (!compact) OutlinedButton(enabled = enabled && history.future.isNotEmpty(), onClick = { onHistory(history.redo()) }) { Text("Redo") }
        }
        if (clips.isEmpty()) {
            Text("Add the current source selection to start a sequence. Your originals remain unchanged.")
            if (compact) Button(enabled = enabled, onClick = onAddSelection) { Text("Add source clip") }
            return@Column
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(28.dp).padding(top = 36.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Box(Modifier.height(64.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { StudioGlyph(StudioGlyph.FORMAT, Modifier.size(16.dp), accent, "Video track") }
                Box(Modifier.height(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { StudioGlyph(StudioGlyph.CAPTIONS, Modifier.size(16.dp), Color(0xFFFFD76A), "Captions track") }
                Box(Modifier.height(28.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { StudioGlyph(StudioGlyph.AUDIO, Modifier.size(16.dp), accent, "Original audio track") }
                if (music != null) Box(Modifier.height(28.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { StudioGlyph(StudioGlyph.AUDIO, Modifier.size(16.dp), MaterialTheme.colorScheme.secondary, "Music track") }
                if (soundEvents.isNotEmpty()) Box(Modifier.height(28.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("FX", fontSize = 10.sp, color = accent) }
            }
        Box(Modifier.weight(1f).onSizeChanged { viewportDp = it.width / density.density }
            .background(surface, RoundedCornerShape(14.dp)).horizontalScroll(trackScroll)) {
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
                    val intervals = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600)
                    val tickSeconds = intervals.firstOrNull { it * scale * 1000 >= 12 } ?: 3600
                    val labelSeconds = intervals.firstOrNull { it >= tickSeconds && it % tickSeconds == 0 && it * scale * 1000 >= 64 } ?: 3600
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; textSize = 9.sp.toPx() }
                    var second = 0L
                    while (second * 1000 <= total) {
                        val x = second * 1000 * scale * density.density
                        drawLine(outline, Offset(x, size.height - 7.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                        if (second % labelSeconds == 0L) drawContext.canvas.nativeCanvas.drawText(Timecode.format(second * 1000), x + 3.dp.toPx(), 12.dp.toPx(), paint)
                        second += tickSeconds
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    clips.forEachIndexed { index, clip ->
                        val clipOffset = TimelineModel.offset(clips, index)
                        val leftDp = trackScroll.value / density.density
                        val visible = clipOffset * scale < leftDp + viewportDp && (clipOffset + clip.durationMs) * scale > leftDp
                        val tiles = if (clip.durationMs * scale > 140f) 3 else 1
                        val frames = (0 until tiles).map { tile ->
                            rememberClipThumbnail(context, clip.source.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: source,
                                clip.startMs + clip.sourceSpanMs * tile / tiles, enabled && visible)
                        }
                        Surface(onClick = { onSelect(clip.id); onSeek(TimelineModel.offset(clips, index)) }, enabled = enabled,
                            modifier = Modifier.width((clip.durationMs * scale).dp).height(64.dp), shape = RoundedCornerShape(8.dp),
                            color = if (clip.id == selected?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(if (clip.id == selected?.id) 2.dp else 1.dp, if (clip.id == selected?.id) accent else outline)) {
                            Box {
                                Row(Modifier.fillMaxSize()) {
                                    frames.forEach { thumbnail ->
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            if (thumbnail != null) Image(thumbnail.asImageBitmap(), "Timeline clip ${index + 1} source frame", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                }
                                Text("${index + 1} · ${Timecode.format(clip.durationMs)}", Modifier.background(Color.Black.copy(alpha = .7f)).padding(5.dp), fontSize = 10.sp, color = Color.White, maxLines = 1)
                            }
                        }
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                    drawRect(outline.copy(alpha = .4f))
                    TimelineModel.subtitles(clips).forEach { cue ->
                        drawRoundRect(Color(0xFFFFD76A).copy(alpha = .85f), Offset(cue.startMs * scale * density.density, 7.dp.toPx()),
                            androidx.compose.ui.geometry.Size(((cue.endMs - cue.startMs) * scale * density.density).coerceAtLeast(1f), 18.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    }
                }
                Row {
                    clips.forEach { clip ->
                        Box(Modifier.width((clip.durationMs * scale).dp).height(28.dp).background(Color(0xFF0D414A)).border(1.dp, outline)) {
                            Text(if (clip.volume == 0f) "Muted" else "Original audio", Modifier.padding(5.dp), fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
                music?.let { track ->
                    Canvas(Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Music track: " + track.name }) {
                        drawRect(outline.copy(alpha = .25f))
                        val active = track.activeDuration(total)
                        if (active > 0) {
                            val x = track.timelineStartMs * scale * density.density
                            val width = active * scale * density.density
                            drawRoundRect(Color(0xFF577B91), Offset(x, 3.dp.toPx()),
                                androidx.compose.ui.geometry.Size(width, 22.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 9.sp.toPx() }
                            drawContext.canvas.nativeCanvas.save()
                            drawContext.canvas.nativeCanvas.clipRect(x, 0f, x + width, size.height)
                            drawContext.canvas.nativeCanvas.drawText(track.name, x + 5.dp.toPx(), 18.dp.toPx(), label)
                            drawContext.canvas.nativeCanvas.restore()
                        }
                    }
                }
            }
            if (soundEvents.isNotEmpty()) Canvas(Modifier.width(trackWidth).height(28.dp).align(androidx.compose.ui.Alignment.BottomStart).semantics { contentDescription = "Sound effects track" }) {
                drawRect(surface)
                soundEvents.forEach { sound ->
                    drawRoundRect(accent.copy(alpha = .8f), Offset(sound.startMs * scale * density.density, 4.dp.toPx()),
                        androidx.compose.ui.geometry.Size((sound.durationMs * scale * density.density).coerceAtLeast(3.dp.toPx()), 20.dp.toPx()),
                        androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }
            }
            Canvas(Modifier.width(trackWidth).height(((if (music != null) 194 else 166) + if (soundEvents.isNotEmpty()) 28 else 0).dp)) {
                val x = playheadMs.coerceIn(0, total) * scale * density.density
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        }
        Text("Drag the ruler to scrub · Use + / − to zoom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!compact) Slider(value = playheadMs.coerceIn(0, total).toFloat(), onValueChange = { scrub(it.toLong()) }, valueRange = 0f..total.coerceAtLeast(1).toFloat(), enabled = enabled)
        if (!compact || tool == EditorTool.MORE) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Zoom", style = MaterialTheme.typography.bodySmall)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 4f..40f, modifier = Modifier.weight(1f), enabled = enabled)
        }
        }
        if (!compact || tool == EditorTool.EDIT) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        selected?.takeIf { !compact || tool in listOf(EditorTool.EDIT, EditorTool.AUDIO, EditorTool.TEXT, EditorTool.SPEED) }?.let { clip ->
            var from by remember(clip.id, clip.startMs) { mutableStateOf(Timecode.precise(clip.startMs)) }
            var until by remember(clip.id, clip.endMs) { mutableStateOf(Timecode.precise(clip.endMs)) }
            fun update(changed: TimelineClip) = edit(clips.map { if (it.id == clip.id) changed else it })
            val clipDuration = clip.resolvedDuration(sourceDurationMs)
            StudioCard(if (compact) "${tool.label} · clip ${selectedIndex + 1}" else "Clip ${selectedIndex + 1} · source range") {
                if (clip.sourceName.isNotBlank()) Text(clip.sourceName, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (!compact || tool == EditorTool.EDIT) {
                SourceTrimHandles(clip, clipDuration,
                    clips.filter { it.resolvedSource(source?.toString().orEmpty()) == clip.resolvedSource(source?.toString().orEmpty()) }.flatMap { listOf(it.startMs, it.endMs) } + clip.subtitles.flatMap { listOf(it.startMs, it.endMs) } + listOf(0L, clipDuration),
                    snapping, enabled, onCommit = { update(it) })
                var exact by remember { mutableStateOf(false) }
                if (compact) TextButton(onClick = { exact = !exact }) { Text(if (exact) "Hide exact trim" else "Exact trim") }
                if (!compact || exact) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = from, onValueChange = { from = it.take(16) }, label = { Text("Source in") }, enabled = enabled, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = until, onValueChange = { until = it.take(16) }, label = { Text("Source out") }, enabled = enabled, modifier = Modifier.weight(1f))
                }
                OutlinedButton(enabled = enabled, onClick = {
                    val a = Timecode.parse(from); val b = Timecode.parse(until)
                    if (a == null || b == null || !Timecode.validTrim(a, b, clipDuration)) onMessage("Enter a valid source range")
                    else update(clip.trimmed(a, b))
                }) { Text("Apply clip trim") }
                }
                }
                if (!compact || tool == EditorTool.SPEED) {
                    Text("Speed · ${clip.speed}x · ${Timecode.precise(clip.durationMs)} output")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            FilterChip(selected = clip.speed == speed, enabled = enabled,
                                onClick = { update(clip.withSpeed(speed)) }, label = { Text("${speed}x") })
                        }
                    }
                    Text("Voice pitch stays natural. Music keeps its own speed.", style = MaterialTheme.typography.bodySmall)
                }
                if (!compact || tool == EditorTool.TEXT) {
                OutlinedTextField(value = clip.hook, onValueChange = { update(clip.copy(hook = it.take(65))) }, label = { Text("Clip hook") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                if (compact) OutlinedTextField(value = clip.caption, onValueChange = { update(clip.copy(caption = it.take(100), captionStartMs = clip.startMs, captionEndMs = clip.endMs)) },
                    label = { Text("Manual caption") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                }
                if (!compact) VideoLookPicker(clip.effect, enabled) { effect -> update(clip.copy(effect = effect)) }
                if (!compact || tool == EditorTool.AUDIO) {
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
}



