package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SpeechLanguageControls(language: String, enabled: Boolean, onChange: (String) -> Unit) {
    Text("Speech language", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("auto" to "Auto / Taglish", "en" to "English", "tl" to "Filipino").forEach { (code, label) ->
            FilterChip(selected = language == code, enabled = enabled, onClick = { onChange(code) }, label = { Text(label) })
        }
    }
    Text("Use Auto for mixed speech. Forced language can help detection but does not guarantee accurate spelling. Review captions.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun CampaignExportChecklist(active: Boolean, tags: String, caption: String, durationMs: Long, minimum: String, maximum: String,
                            issues: List<String>, source: Boolean, rights: Boolean, enabled: Boolean,
                            onSource: (Boolean) -> Unit, onRights: (Boolean) -> Unit, onEdit: () -> Unit) {
    StudioCard("Campaign export checklist") {
        if (!active) {
            Text("Campaign checks are off for this edit.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Output: ${Timecode.precise(durationMs)} · required $minimum–$maximum seconds")
            Text("Required tags: ${tags.ifBlank { "None entered" }}", style = MaterialTheme.typography.bodySmall)
            Text("Posting caption: ${caption.ifBlank { "Not entered" }}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(source, onSource, enabled = enabled)
                Text("Permitted source and required footage checked", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rights, onRights, enabled = enabled)
                Text("Rights, duplicate submissions and campaign rules checked", Modifier.weight(1f))
            }
            issues.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (issues.isEmpty()) Text("Entered checklist passed. This does not verify eligibility with the platform.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(enabled = enabled, onClick = onEdit) { Text("Edit campaign checklist") }
    }
}

@Composable
fun CaptionTimingEditor(cues: List<WhisperNative.Segment>, durationMs: Long, enabled: Boolean,
                        onChange: (List<WhisperNative.Segment>) -> Unit, onSeek: (Long) -> Unit,
                        onMessage: (String) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var find by remember { mutableStateOf("") }; var replacement by remember { mutableStateOf("") }
    if (cues.isEmpty()) return
    val current = selected.coerceIn(0, cues.lastIndex)
    val cue = cues[current]
    var from by remember(current, cue.startMs) { mutableStateOf(Timecode.precise(cue.startMs)) }
    var until by remember(current, cue.endMs) { mutableStateOf(Timecode.precise(cue.endMs)) }
    Text("Caption timing", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(enabled = enabled && current > 0, onClick = { selected = current - 1; onSeek(cues[current - 1].startMs) }) { Text("Previous line") }
        Text("${current + 1}/${cues.size}", Modifier.align(Alignment.CenterVertically))
        TextButton(enabled = enabled && current < cues.lastIndex, onClick = { selected = current + 1; onSeek(cues[current + 1].startMs) }) { Text("Next line") }
    }
    OutlinedTextField(cue.text, { text -> onChange(cues.mapIndexed { i, item -> if (i == current) item.copy(text = text.take(2000)) else item }) },
        enabled = enabled, label = { Text("Timing editor text") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(from, { from = it.take(16) }, enabled = enabled, label = { Text("Caption in") }, modifier = Modifier.weight(1f))
        OutlinedTextField(until, { until = it.take(16) }, enabled = enabled, label = { Text("Caption out") }, modifier = Modifier.weight(1f))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled, onClick = {
            runCatching {
                val a = Timecode.parse(from) ?: error("Invalid caption in time")
                val b = Timecode.parse(until) ?: error("Invalid caption out time")
                onChange(CaptionTiming.replace(cues, current, cue.copy(startMs = a, endMs = b), durationMs))
            }.onFailure { onMessage(it.message ?: "Could not update timing") }
        }) { Text("Apply caption timing") }
        TextButton(enabled = enabled, onClick = { onSeek(cue.startMs) }) { Text("Preview line") }
    }
    OutlinedButton(enabled = enabled && cue.text.trim().contains(Regex("\\s")) && cues.size < 19_900, onClick = {
        val words = CaptionTiming.words(cue)
        if (words.isNotEmpty()) onChange(cues.take(current) + words + cues.drop(current + 1))
    }) { Text("Split line into editable words") }
    Text("Split word timings are estimates. Listen and adjust them; Filipino/Taglish spelling is preserved.", style = MaterialTheme.typography.bodySmall)
    var corrections by remember { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { corrections = !corrections }) { Text("Correct repeated names or Taglish words") }
    if (corrections) {
        OutlinedTextField(find, { find = it.take(80) }, label = { Text("Find whole word") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(replacement, { replacement = it.take(80) }, label = { Text("Replace with") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        Button(enabled = enabled && find.isNotBlank(), onClick = {
            runCatching { onChange(CaptionTiming.correct(cues, find, replacement)) }.onFailure { onMessage(it.message ?: "Correction failed") }
        }) { Text("Apply correction to this transcript") }
    }
}

@Composable
fun PresetControls(presets: List<EditingPreset>, enabled: Boolean, onSave: (String) -> Unit,
                   onApply: (EditingPreset) -> Unit, onDelete: (EditingPreset) -> Unit) {
    var name by remember { mutableStateOf("") }
    StudioCard("My editing presets", "Save this cut's look, framing, captions, motion and SFX settings. Applying replaces those settings on the selected cut.") {
        OutlinedTextField(name, { name = it.take(60) }, enabled = enabled, label = { Text("Preset name") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = enabled && name.isNotBlank(), onClick = { onSave(name); name = "" }) { Text("Save editing preset") }
        presets.forEach { preset ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, Modifier.weight(1f))
                TextButton(enabled = enabled, onClick = { onApply(preset) }) { Text("Apply") }
                TextButton(enabled = enabled, onClick = { onDelete(preset) }) { Text("Delete") }
            }
        }
        if (presets.isEmpty()) Text("No saved presets yet.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ExportQueueControls(items: List<QueuedExport>, enabled: Boolean, canAdd: Boolean, running: Boolean, progress: Int,
                        onAdd: () -> Unit, onStart: () -> Unit, onStop: () -> Unit,
                        onReview: (QueuedExport) -> Unit, onRemove: (QueuedExport) -> Unit, onOpen: (QueuedExport) -> Unit) {
    StudioCard("Export queue", "Add each reviewed edit as a frozen snapshot. Start processes approved snapshots one at a time. Restored or interrupted items need a fresh review.") {
        Button(enabled = enabled && canAdd && items.size < ExportQueue.LIMIT, onClick = onAdd) { Text("Add approved edit to queue") }
        Button(enabled = enabled && items.any { it.status == QueueStatus.READY }, onClick = onStart) { Text("Start approved queue") }
        if (running) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onStop) { Text("Stop queue") }
        }
        items.forEach { item ->
            Text(item.project.projectName.ifBlank { "Untitled edit" } + " · " + item.status.name.lowercase())
            Text(item.message, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                if (item.status != QueueStatus.COMPLETE) TextButton(enabled = enabled, onClick = { onReview(item) }) { Text("Load for review") }
                if (item.uri.isNotBlank()) TextButton(enabled = enabled, onClick = { onOpen(item) }) { Text("Open MP4") }
                TextButton(enabled = enabled, onClick = { onRemove(item) }) { Text("Remove") }
            }
        }
        if (items.isEmpty()) Text("Queue is empty.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ExportApproval(approved: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = approved, enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = approved, onCheckedChange = null, enabled = enabled)
        Text("I reviewed this edit and approve its export", Modifier.weight(1f))
    }
}
