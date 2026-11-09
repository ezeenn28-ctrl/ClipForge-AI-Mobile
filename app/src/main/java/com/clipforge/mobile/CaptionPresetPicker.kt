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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CaptionPresetPicker(selected: CaptionPreset, enabled: Boolean, onSelect: (CaptionPreset) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptionPreset.entries.forEach { preset ->
            Surface(onClick = { onSelect(preset) }, enabled = enabled,
                modifier = Modifier.width(132.dp), shape = RoundedCornerShape(14.dp),
                color = if (selected == preset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(if (selected == preset) 2.dp else 1.dp,
                    if (selected == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("YOUR", fontSize = 17.sp, color = Color.White,
                        fontWeight = if (preset == CaptionPreset.CLEAN) FontWeight.Normal else FontWeight.ExtraBold)
                    Surface(color = if (preset == CaptionPreset.CARD) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)) {
                        Text("MOMENT", modifier = Modifier.padding(horizontal = 3.dp),
                            fontSize = 17.sp, color = if (preset == CaptionPreset.HIGHLIGHT) Color.Yellow else Color.White,
                            fontWeight = if (preset == CaptionPreset.CLEAN) FontWeight.Normal else FontWeight.ExtraBold)
                    }
                    Text(preset.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
