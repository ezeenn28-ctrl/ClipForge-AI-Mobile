package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun HookGeneratorControls(clip: TimelineClip, enabled: Boolean, onApply: (String) -> Unit) {
    var description by remember(clip.id) { mutableStateOf("") }
    var tone by remember { mutableStateOf(HookTone.FUNNY) }
    var suggestions by remember(clip.id) { mutableStateOf(emptyList<String>()) }
    var message by remember(clip.id) { mutableStateOf("") }
    StudioCard("Hook ideas", "Offline text suggestions. Review the meaning before applying.") {
        OutlinedTextField(description, { description = it.take(1000); suggestions = emptyList() },
            enabled = enabled, label = { Text("What happens? (optional)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HookTone.entries.forEach { item ->
                FilterChip(selected = tone == item, onClick = { tone = item; suggestions = emptyList() }, enabled = enabled,
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
        suggestions.forEach { hook -> OutlinedButton(enabled = enabled, onClick = { onApply(hook) }, modifier = Modifier.fillMaxWidth()) { Text(hook) } }
    }
}

@Composable
fun ReferencePresetControls(appearance: CaptionAppearance, enabled: Boolean, onChange: (CaptionAppearance) -> Unit) {
    Text("Reference frame")
    ReferenceLayout.entries.forEach { layout ->
        FilterChip(selected = appearance.layout == layout, enabled = enabled, onClick = {
            onChange(appearance.copy(layout = layout, font = CaptionFont.SANS, alignment = CaptionAlignment.CENTER,
                color = if (layout == ReferenceLayout.STANDARD) CaptionColor.INHERIT else CaptionColor.WHITE,
                highlight = if (layout == ReferenceLayout.WHITE_CLASSIC) CaptionColor.GREEN else CaptionColor.YELLOW,
                positionY = if (layout == ReferenceLayout.WHITE_CLASSIC) .79f else if (layout == ReferenceLayout.WHITE_SQUARE) .73f else 970f / 1280))
        }, label = { Text(layout.label) })
    }
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
            onChange(clip.copy(sounds = clip.sounds + ClipSound(UUID.randomUUID().toString(), at, kind, asset = asset)))
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

