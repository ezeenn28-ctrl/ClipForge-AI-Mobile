package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MotionControls(clip: TimelineClip, enabled: Boolean, onChange: (TimelineClip) -> Unit, onSeek: (Boolean) -> Unit) {
    var endPoint by remember(clip.id) { mutableStateOf(false) }
    val point = if (endPoint) clip.visuals.end else clip.visuals.start
    fun update(value: MotionPoint) = onChange(clip.copy(visuals = if (endPoint) clip.visuals.copy(end = value) else clip.visuals.copy(start = value)))
    StudioCard("Motion keyframes", "Set the first and last frame. Motion follows the clip's duration.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !endPoint, enabled = enabled, onClick = { endPoint = false; onSeek(false) }, label = { Text("Start frame") })
            FilterChip(selected = endPoint, enabled = enabled, onClick = { endPoint = true; onSeek(true) }, label = { Text("End frame") })
        }
        Text("Zoom · ${"%.0f".format(point.scale * 100)}%")
        Slider(value = point.scale, onValueChange = { update(point.copy(scale = it)) }, valueRange = 1f..1.6f, enabled = enabled)
        Text("Horizontal position")
        Slider(value = point.x, onValueChange = { update(point.copy(x = it)) }, valueRange = -0.2f..0.2f, enabled = enabled)
        Text("Vertical position")
        Slider(value = point.y, onValueChange = { update(point.copy(y = it)) }, valueRange = -0.2f..0.2f, enabled = enabled)
        FilterChip(selected = clip.visuals.ease, enabled = enabled, onClick = { onChange(clip.copy(visuals = clip.visuals.copy(ease = !clip.visuals.ease))) }, label = { Text(if (clip.visuals.ease) "Ease in/out" else "Linear motion") })
        TextButton(enabled = enabled, onClick = { onChange(clip.copy(visuals = clip.visuals.copy(start = MotionPoint(), end = MotionPoint()))) }) { Text("Reset motion") }
        Text("Position shifts can reveal black edges. Increase zoom when panning.", style = MaterialTheme.typography.bodySmall)
    }
    StudioCard("Fade through black", "These fades keep clip duration unchanged. They do not overlap clips.") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = clip.visuals.fadeInMs > 0, enabled = enabled, onClick = { onChange(clip.copy(visuals = clip.visuals.copy(fadeInMs = if (clip.visuals.fadeInMs > 0) 0 else 400))) }, label = { Text("Fade from black") })
            FilterChip(selected = clip.visuals.fadeOutMs > 0, enabled = enabled, onClick = { onChange(clip.copy(visuals = clip.visuals.copy(fadeOutMs = if (clip.visuals.fadeOutMs > 0) 0 else 400))) }, label = { Text("Fade to black") })
        }
    }
}
