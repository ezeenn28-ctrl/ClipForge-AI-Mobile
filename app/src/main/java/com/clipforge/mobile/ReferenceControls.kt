package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun HookGeneratorControls(clip: TimelineClip, enabled: Boolean, initialTone: HookTone = HookTone.FUNNY, onTone: (HookTone) -> Unit = {}, onPreview: () -> Unit = {}, onApply: (String) -> Unit) {
    var cover by remember(clip.id,clip.hook) { mutableStateOf(clip.hook) }
    var description by remember(clip.id) { mutableStateOf("") }
    var tone by remember(initialTone) { mutableStateOf(initialTone) }
    var suggestions by remember(clip.id) { mutableStateOf(emptyList<String>()) }
    var message by remember(clip.id) { mutableStateOf("") }
    StudioCard("Hook ideas", "Offline text suggestions. Review the meaning before applying.") {
        OutlinedTextField(description, { description = it.take(1000); suggestions = emptyList() },
            enabled = enabled, label = { Text("What happens? (optional)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HookTone.entries.forEach { item ->
                FilterChip(selected = tone == item, onClick = { tone = item; onTone(item); suggestions = emptyList() }, enabled = enabled,
                    label = { Text(item.label) })
            }
        }
        Button(enabled = enabled, onClick = {
            val text = description.ifBlank {
                clip.subtitles.filter { it.endMs > clip.startMs && it.startMs < clip.endMs }.joinToString(" ") { it.text }
                    .ifBlank { clip.caption }
            }
            suggestions = HookGenerator.suggestions(text, tone)
            message = if (suggestions.isEmpty()) "Add captions or describe the scene first." else "Tap a suggestion to apply, then edit it in Hook text."
        }) { Text("Generate Hook") }
        if (message.isNotBlank()) Text(message)
        OutlinedTextField(cover,{cover=it.take(80)},enabled=enabled,label={Text("Editable opening / cover text")},modifier=Modifier.fillMaxWidth())
        Row { TextButton(enabled=enabled&&cover.isNotBlank(),onClick={onApply(cover)}){Text("Apply cover text")};TextButton(enabled=enabled,onClick=onPreview){Text("Preview first 3 seconds")} }
        Text("Cover text uses this clip's hook overlay. The three local variants are suggestions, not performance predictions.",style=MaterialTheme.typography.bodySmall)
        suggestions.forEach { hook -> OutlinedButton(enabled = enabled, onClick = { onApply(hook) }, modifier = Modifier.fillMaxWidth()) { Text(hook) } }
    }
}

@Composable
fun ReferencePresetControls(appearance: CaptionAppearance, enabled: Boolean, onChange: (CaptionAppearance) -> Unit) {
    Text("Reference frame")
    ReferenceLayout.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { layout ->
                val sample = remember(layout) {
                    val overlay = makeCaptionOverlay("", "", appearance = CaptionAppearance(layout = layout), bitmapScale = .25f)
                    val bitmap = android.graphics.Bitmap.createBitmap(180, 320, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(0xFF43637D.toInt())
                    canvas.drawBitmap(overlay, 0f, 0f, null)
                    overlay.recycle()
                    bitmap.asImageBitmap()
                }
                Surface(onClick = {
                    onChange(appearance.copy(layout = layout, positionY = layout.captionY))
                }, enabled = enabled, modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = if (appearance.layout == layout) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(if (appearance.layout == layout) 2.dp else 1.dp,
                        if (appearance.layout == layout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(sample, null, Modifier.height(54.dp).aspectRatio(9f/16f))
                        Text(layout.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
        }
    }
    Text("Selected frame: " + appearance.layout.label, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun SoundControls(clip: TimelineClip, localMs: Long, projectCount: Int, enabled: Boolean, onChange: (TimelineClip) -> Unit,
                  onPreview: (SoundKind?, SoundAsset?) -> Unit = { _, _ -> }) {
    var kind by remember { mutableStateOf(SoundKind.WHOOSH) }
    var asset by remember { mutableStateOf<SoundAsset?>(null) }
    var library by remember { mutableStateOf(false) }
    if (library) SoundLibraryDialog(onDismiss = { library = false }, onPreview = onPreview) { chosenKind, chosenAsset ->
        kind = chosenKind; asset = chosenAsset
    }
    val at = clip.startMs + clip.sourceOffsetMs(localMs.coerceIn(0, (clip.durationMs - 50).coerceAtLeast(0)))
    StudioCard("Sound effects", "Original offline effects. Timing follows this clip; preview before export.") {
        SoundKind.entries.take(6).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item -> FilterChip(selected = asset == null && kind == item, enabled = enabled, onClick = { kind = item; asset = null }, label = { Text(item.label) }) }
            }
        }
        Row {
            OutlinedButton(enabled = enabled, onClick = { library = true }) { Text("Sound library · 18+") }
            TextButton(enabled = enabled, onClick = { onPreview(kind, asset) }) { Text("Preview selected") }
        }
        Text("Selected: " + (asset?.name ?: kind.label))
        Button(enabled = enabled && clip.sounds.size < 12 && projectCount < 120, onClick = {
            onChange(clip.copy(sounds = clip.sounds.filterNot { it.sourceMs == at } + ClipSound(UUID.randomUUID().toString(), at, kind, asset = asset)))
        }) { Text("Add SFX at playhead") }
        OutlinedButton(enabled = enabled && projectCount - clip.sounds.size + 4 <= 120, onClick = {
            onChange(clip.copy(sounds = SoundTimeline.suggest(clip)))
        }) { Text("Suggest SFX from captions") }
        if (clip.sounds.isNotEmpty()) TextButton(enabled = enabled, onClick = { onChange(clip.copy(sounds = emptyList())) }) { Text("Clear clip sound effects") }
        clip.sounds.sortedBy { it.sourceMs }.forEach { sound ->
            key(sound.id) {
                var gain by remember(sound.volume) { mutableFloatStateOf(sound.volume) }
                val inside = sound.sourceMs in clip.startMs until clip.endMs
                Text(sound.label + " · " + Timecode.format(clip.outputOffsetMs(sound.sourceMs - clip.startMs)) + if (inside) "" else " · outside trim")
                Text("Effect volume: ${(gain * 100).toInt()}%")
                Slider(value = gain, onValueChange = { gain = it }, enabled = enabled,
                    onValueChangeFinished = { onChange(clip.copy(sounds = clip.sounds.map { if (it.id == sound.id) it.copy(volume = gain) else it })) })
                Row {
                    TextButton(enabled = enabled, onClick = { onChange(clip.copy(sounds = clip.sounds.map { if (it.id == sound.id) it.copy(sourceMs = at) else it })) }) { Text("Move to playhead") }
                    TextButton(enabled = enabled, onClick = { onChange(clip.copy(sounds = clip.sounds.filterNot { it.id == sound.id })) }) { Text("Remove effect") }
                }
            }
        }
    }
}


