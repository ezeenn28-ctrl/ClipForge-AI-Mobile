package com.clipforge.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class EditorTool(val label: String, val glyph: StudioGlyph) {
    EDIT("Edit", StudioGlyph.EDIT), SPEED("Speed", StudioGlyph.SPEED), AUDIO("Audio", StudioGlyph.AUDIO), TEXT("Text", StudioGlyph.TEXT),
    CAPTIONS("Captions", StudioGlyph.CAPTIONS), EFFECTS("Effects", StudioGlyph.EFFECTS),
    FORMAT("Format", StudioGlyph.FORMAT), MORE("More", StudioGlyph.MORE)
}

@Composable
fun EditorTopBar(enabled: Boolean, canUndo: Boolean, canRedo: Boolean, onBack: () -> Unit,
                 onUndo: () -> Unit, onRedo: () -> Unit, onExport: () -> Unit, projectName: String = "Video editor") {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { StudioGlyph(StudioGlyph.BACK, description = "Back") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(projectName.ifBlank { "Video editor" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("1080p · EDITOR", fontSize = 9.sp, letterSpacing = .8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(enabled = enabled && canUndo, onClick = onUndo, modifier = Modifier.size(40.dp)) {
                StudioGlyph(StudioGlyph.UNDO, color = if (enabled && canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .3f), description = "Undo")
            }
            IconButton(enabled = enabled && canRedo, onClick = onRedo, modifier = Modifier.size(40.dp)) {
                StudioGlyph(StudioGlyph.REDO, color = if (enabled && canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .3f), description = "Redo")
            }
            Button(enabled = enabled, onClick = onExport, shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)) {
                StudioGlyph(StudioGlyph.EXPORT, Modifier.size(15.dp), MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp)); Text("Export", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EditorToolBar(selected: EditorTool, enabled: Boolean, onSelect: (EditorTool) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorTool.entries.forEach { tool ->
                Surface(onClick = { onSelect(tool) }, enabled = enabled, modifier = Modifier.width(68.dp).height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected == tool) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (selected == tool) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f)) else null) {
                    Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        StudioGlyph(tool.glyph, Modifier.size(22.dp),
                            if (selected == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tool.label, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                            color = if (selected == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewTransport(playing: Boolean, positionMs: Long, durationMs: Long, enabled: Boolean,
                     onToggle: () -> Unit, onRestart: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(50.dp)) {
        Text(Timecode.format(positionMs) + " / " + Timecode.format(durationMs),
            modifier = Modifier.align(Alignment.CenterStart), fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalIconButton(enabled = enabled, onClick = onToggle, modifier = Modifier.align(Alignment.Center)) {
            StudioGlyph(if (playing) StudioGlyph.PAUSE else StudioGlyph.PLAY,
                color = MaterialTheme.colorScheme.primary, description = if (playing) "Pause" else "Play")
        }
        IconButton(enabled = enabled, onClick = onRestart, modifier = Modifier.align(Alignment.CenterEnd)) {
            StudioGlyph(StudioGlyph.RESTART, description = "Restart")
        }
    }
}
