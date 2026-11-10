package com.clipforge.mobile

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun ColorControls(color: ColorAdjustments, enabled: Boolean, onChange: (ColorAdjustments) -> Unit) {
    StudioCard("Color adjustments", "Apply to this clip. Captions keep their own colors.") {
        ColorSlider("Brightness", color.brightness, -0.4f..0.4f, enabled) { onChange(color.copy(brightness = it)) }
        ColorSlider("Contrast", color.contrast, .5f..1.5f, enabled) { onChange(color.copy(contrast = it)) }
        ColorSlider("Saturation", color.saturation, 0f..2f, enabled) { onChange(color.copy(saturation = it)) }
        ColorSlider("Warmth", color.warmth, -1f..1f, enabled) { onChange(color.copy(warmth = it)) }
        TextButton(enabled = enabled && color != ColorAdjustments(), onClick = { onChange(ColorAdjustments()) }) { Text("Reset color") }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onCommit: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Text("$label · ${"%.2f".format(draft)}")
    // One undo step for the whole drag; a released slider updates the picture.
    Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { onCommit(draft) },
        valueRange = range, enabled = enabled, modifier = Modifier.testTag("color-$label"))
}
