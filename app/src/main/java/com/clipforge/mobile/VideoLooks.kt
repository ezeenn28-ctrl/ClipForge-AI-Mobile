package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One bounded SDR color transform shared by the video texture and MP4 renderer. */
object VideoLooks {
    val names = listOf("None", "Zoom", "Monochrome", "Warm film", "Cool film", "Vivid", "Vintage", "Noir", "Soft fade")
    fun color(name: String, manual: ColorAdjustments = ColorAdjustments()): ColorAdjustments {
        require(name in names) { "Unknown video look" }
        manual.validate()
        val base = when (name) {
            "Warm film" -> ColorAdjustments(.015f, 1.08f, .9f, .4f)
            "Cool film" -> ColorAdjustments(0f, 1.08f, .9f, -.4f)
            "Vivid" -> ColorAdjustments(.01f, 1.12f, 1.35f, 0f)
            "Vintage" -> ColorAdjustments(.035f, .88f, .65f, .28f)
            "Noir" -> ColorAdjustments(-.025f, 1.3f, 0f, 0f)
            "Soft fade" -> ColorAdjustments(.035f, .8f, .85f, 0f)
            else -> ColorAdjustments()
        }
        return ColorAdjustments((base.brightness + manual.brightness).coerceIn(-.4f,.4f),
            (base.contrast * manual.contrast).coerceIn(.5f,1.5f),
            (base.saturation * manual.saturation).coerceIn(0f,2f),
            (base.warmth + manual.warmth).coerceIn(-1f,1f))
    }
}

@Composable
fun VideoLookPicker(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Text("Video looks", style = MaterialTheme.typography.titleSmall)
    VideoLooks.names.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { look -> FilterChip(selected = selected == look, enabled = enabled,
                onClick = { onSelect(look) }, label = { Text(look) }) }
        }
    }
    Text("Selected look: " + selected, style = MaterialTheme.typography.bodySmall)
    Text("Color controls refine the selected look. Choose None to remove it.", style = MaterialTheme.typography.bodySmall)
}
