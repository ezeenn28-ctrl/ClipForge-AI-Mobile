package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CaptionPresetPicker(selected: CaptionPreset, enabled: Boolean, onSelect: (CaptionPreset) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptionPreset.entries.forEach { preset ->
            FilterChip(selected = selected == preset, enabled = enabled, onClick = { onSelect(preset) }, label = { Text(preset.label) })
        }
    }
}
