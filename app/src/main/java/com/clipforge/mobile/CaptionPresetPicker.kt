package com.clipforge.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CaptionPresetPicker(selected: CaptionPreset, enabled: Boolean, onSelect: (CaptionPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      CaptionPreset.entries.chunked(3).forEach { row ->
       Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        row.forEach { preset ->
            val sample = remember(preset) {
                val appearance = CaptionThemes.apply(preset, CaptionAppearance(positionY = .55f))
                val full = makeCaptionOverlay("", "YOUR MOMENT", 42f, preset = preset, appearance = appearance, bitmapScale = .5f)
                val crop = android.graphics.Bitmap.createBitmap(full, 0, 305, full.width, 108)
                if (crop !== full) full.recycle()
                crop.asImageBitmap()
            }
            Surface(onClick = { onSelect(preset) }, enabled = enabled,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                color = if (selected == preset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(if (selected == preset) 2.dp else 1.dp,
                    if (selected == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Image(sample, null, Modifier.fillMaxWidth().height(42.dp), contentScale = ContentScale.Fit)
                    Text(preset.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
       }
      }
      Text("Selected style: " + selected.label, style = MaterialTheme.typography.bodySmall)
    }
}
