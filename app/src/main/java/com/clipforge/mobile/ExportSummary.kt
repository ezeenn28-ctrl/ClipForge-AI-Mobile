package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ExportSummary(name: String, durationMs: Long, vertical: Boolean, cuts: Int, captions: Int,
                  mute: Boolean, music: String?, approved: Boolean,quality:ExportQuality=ExportQuality()) {
    StudioCard("Delivery details") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioGlyph(StudioGlyph.EXPORT, color = MaterialTheme.colorScheme.primary)
            Text(name.ifBlank { "Untitled project" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportStat("DURATION", Timecode.format(durationMs), Modifier.weight(1f))
            ExportStat("CANVAS", if (vertical) "9:16" else "16:9", Modifier.weight(1f))
            ExportStat("CAPTIONS", captions.toString(), Modifier.weight(1f))
        }
        Text((if (vertical) "${quality.height} × ${quality.height*16/9}" else "${quality.height*16/9} × ${quality.height}") + " · MP4 · " + cuts + (if (cuts == 1) " cut" else " cuts"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Original audio: " + (if (mute) "muted" else "included") +
            (music?.let { " · Music: " + it } ?: ""), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        StudioBadge(if (approved) "APPROVED FOR EXPORT" else "REVIEW REQUIRED", emphasized = approved)
    }
}

@Composable
private fun ExportStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

