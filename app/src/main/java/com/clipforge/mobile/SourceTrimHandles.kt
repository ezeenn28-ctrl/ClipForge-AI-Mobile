package com.clipforge.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Drag previews are local; release commits exactly one history entry. Cancel discards. */
@Composable
fun SourceTrimHandles(clip: TimelineClip, sourceDurationMs: Long, targets: List<Long>, snapping: Boolean,
                      enabled: Boolean, onCommit: (TimelineClip) -> Unit) {
    var draft by remember(clip) { mutableStateOf(clip) }
    val latestCommit by rememberUpdatedState(onCommit)
    val latestTargets by rememberUpdatedState(targets)
    val latestSnapping by rememberUpdatedState(snapping)
    val from = (clip.startMs - 5000).coerceAtLeast(0)
    val until = (clip.endMs + 5000).coerceAtMost(sourceDurationMs)
    val span = (until - from).coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Drag edges · ${Timecode.precise(draft.startMs)} — ${Timecode.precise(draft.endMs)}", style = MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
            val railWidth = (maxWidth - 44.dp).coerceAtLeast(1.dp)
            val railPx = with(density) { railWidth.toPx() }
            fun x(time: Long) = 22.dp + railWidth * ((time - from).toFloat() / span)
            Canvas(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
                drawRect(trackColor, Offset(0f, 10.dp.toPx()), Size(size.width, 32.dp.toPx()))
                val left = (draft.startMs - from).toFloat() / span * size.width
                val right = (draft.endMs - from).toFloat() / span * size.width
                drawRect(accent.copy(alpha = .4f), Offset(left, 10.dp.toPx()), Size((right - left).coerceAtLeast(1f), 32.dp.toPx()))
            }
            listOf(true, false).forEach { startEdge ->
                Box(Modifier.offset(x = x(if (startEdge) draft.startMs else draft.endMs) - 22.dp)
                    .size(44.dp, 52.dp)
                    .testTag(if (startEdge) "trim-start-handle" else "trim-end-handle")
                    .semantics { contentDescription = if (startEdge) "Drag source in point" else "Drag source out point" }
                    .pointerInput(clip, railPx, enabled, from, until) {
                        var requested = 0.0
                        detectDragGestures(
                            onDragStart = { draft = clip; requested = (if (startEdge) clip.startMs else clip.endMs).toDouble() },
                            onDragCancel = { draft = clip },
                            onDragEnd = { if (enabled && draft != clip) latestCommit(draft) },
                            onDrag = { change, amount ->
                                if (enabled && clip.durationMs >= TimelineTouch.MIN_CUT_MS) {
                                    change.consume()
                                    requested += amount.x / railPx * span
                                    draft = TimelineTouch.trim(clip, startEdge, requested.toLong().coerceIn(from, until), sourceDurationMs,
                                        latestTargets, latestSnapping, minOf(150L, (span * 8 / railPx).toLong()))
                                }
                            })
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.width(12.dp).height(40.dp).background(if (enabled) accent else trackColor, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Text("│", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Text("Release to apply · One drag = one undo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
