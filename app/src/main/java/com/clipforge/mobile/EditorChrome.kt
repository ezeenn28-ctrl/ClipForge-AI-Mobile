package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class EditorTool(val label: String, val symbol: String) {
    EDIT("Edit", "✂"), AUDIO("Audio", "♪"), TEXT("Text", "T"), CAPTIONS("Captions", "CC"),
    EFFECTS("Effects", "◇"), FORMAT("Format", "▣"), MORE("More", "•••")
}

@Composable
fun EditorTopBar(enabled: Boolean, canUndo: Boolean, canRedo: Boolean, onBack: () -> Unit,
                 onUndo: () -> Unit, onRedo: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("Back") }
        TextButton(enabled = enabled && canUndo, onClick = onUndo) { Text("Undo", fontSize = 11.sp) }
        TextButton(enabled = enabled && canRedo, onClick = onRedo) { Text("Redo", fontSize = 11.sp) }
        Spacer(Modifier.weight(1f))
        Button(enabled = enabled, onClick = onExport, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Export") }
    }
}

@Composable
fun EditorToolBar(selected: EditorTool, enabled: Boolean, onSelect: (EditorTool) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            EditorTool.entries.forEach { tool ->
                TextButton(enabled = enabled, onClick = { onSelect(tool) }, modifier = Modifier.width(68.dp).height(64.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(tool.symbol, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            color = if (selected == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tool.label, fontSize = 10.sp, color = if (selected == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
