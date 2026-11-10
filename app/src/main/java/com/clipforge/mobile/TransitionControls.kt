package com.clipforge.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TransitionControls(clip: TimelineClip, hasNext: Boolean, enabled: Boolean, onChange: (TimelineClip) -> Unit) {
    StudioCard("Transition to next clip", "Keeps clip lengths and music timing unchanged.") {
        if (!hasNext) Text("Add another clip after this one to use a transition.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CutTransitionKind.entries.forEach { kind ->
                FilterChip(selected = clip.transition.kind == kind, enabled = enabled && hasNext,
                    onClick = { onChange(clip.copy(transition = clip.transition.copy(kind = kind))) }, label = { Text(kind.label) })
            }
        }
        if (clip.transition.kind != CutTransitionKind.CUT && hasNext) {
            Text("Transition length · ${Timecode.precise(clip.transition.durationMs)}")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(200L, 400L, 600L, 1000L).forEach { duration ->
                    FilterChip(selected = clip.transition.durationMs == duration, enabled = enabled,
                        onClick = { onChange(clip.copy(transition = clip.transition.copy(durationMs = duration))) }, label = { Text("${duration}ms") })
                }
            }
        }
    }
}
