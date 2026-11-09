package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class CaptionPanel(val label: String) { STYLE("Style"), FONT("Font"), COLOR("Color"), POSITION("Position") }

@Composable
fun CaptionStyleControls(preset: CaptionPreset, size: Float, appearance: CaptionAppearance,
                         legacyColor: Int, enabled: Boolean, onPreset: (CaptionPreset) -> Unit,
                         onSize: (Float) -> Unit, onAppearance: (CaptionAppearance) -> Unit) {
    var panel by remember { mutableStateOf(CaptionPanel.STYLE) }
    Text("Applies to all captions in this project.", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptionPanel.entries.forEach { item ->
            FilterChip(selected = panel == item, enabled = enabled, onClick = { panel = item }, label = { Text(item.label) })
        }
    }
    when (panel) {
        CaptionPanel.STYLE -> {
            CaptionPresetPicker(preset, enabled, onPreset)
            Text("Bold highlight emphasizes each line’s last word. Prefix a word with # to choose it.", style = MaterialTheme.typography.bodySmall)
        }
        CaptionPanel.FONT -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionFont.entries.forEach { font ->
                    FilterChip(selected = appearance.font == font, enabled = enabled,
                        onClick = { onAppearance(appearance.copy(font = font)) }, label = { Text(font.label) })
                }
            }
            Text("Text size · " + size.toInt())
            Slider(value = size, enabled = enabled, valueRange = 28f..55f, onValueChange = onSize)
        }
        CaptionPanel.COLOR -> {
            Text("Text color")
            CaptionColorChoices(appearance.textColor(preset, legacyColor), enabled) { onAppearance(appearance.copy(color = it)) }
            Text("Highlight color")
            CaptionColorChoices(appearance.highlightColor(), enabled) { onAppearance(appearance.copy(highlight = it)) }
        }
        CaptionPanel.POSITION -> {
            Text("Vertical position")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Upper" to .35f, "Middle" to .55f, "Lower" to (970f / 1280f)).forEach { (label, value) ->
                    FilterChip(selected = kotlin.math.abs(appearance.positionY - value) < .001f, enabled = enabled,
                        onClick = { onAppearance(appearance.copy(positionY = value)) }, label = { Text(label) })
                }
            }
            Slider(value = appearance.positionY, enabled = enabled, valueRange = .35f.. .85f,
                onValueChange = { onAppearance(appearance.copy(positionY = it)) })
            Text("Alignment")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptionAlignment.entries.forEach { alignment ->
                    FilterChip(selected = appearance.alignment == alignment, enabled = enabled,
                        onClick = { onAppearance(appearance.copy(alignment = alignment)) }, label = { Text(alignment.label) })
                }
            }
        }
    }
    TextButton(enabled = enabled, onClick = { onAppearance(CaptionAppearance()) }) { Text("Reset font, color & position") }
}

@Composable
private fun CaptionColorChoices(selectedArgb: Int, enabled: Boolean, onSelect: (CaptionColor) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptionColor.entries.filter { it != CaptionColor.INHERIT }.forEach { color ->
            FilterChip(selected = selectedArgb == color.argb, enabled = enabled,
                onClick = { onSelect(color) }, label = { Text(color.label) })
        }
    }
}
