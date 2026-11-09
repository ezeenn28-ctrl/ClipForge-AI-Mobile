package com.clipforge.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class StudioGlyph { BACK, UNDO, REDO, PLAY, PAUSE, RESTART, EXPAND, EDIT, AUDIO, TEXT, CAPTIONS, EFFECTS, FORMAT, MORE, SPARKLES, FOLDER, EXPORT, SETTINGS }

@Composable
fun StudioGlyph(glyph: StudioGlyph, modifier: Modifier = Modifier.size(22.dp),
                color: Color = MaterialTheme.colorScheme.onSurface, description: String? = null) {
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(x, y), Offset(a, b), 1.7f, StrokeCap.Round)
            fun path(vararg points: Pair<Float, Float>) = drawPath(Path().apply {
                points.forEachIndexed { index, point -> if (index == 0) moveTo(point.first, point.second) else lineTo(point.first, point.second) }
            }, color, style = stroke)
            fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(color, Offset(x, y),
                androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(2f), style = stroke)
            when (glyph) {
                StudioGlyph.BACK -> { path(14f to 5f, 7f to 12f, 14f to 19f) }
                StudioGlyph.UNDO -> { path(8f to 4f, 3f to 9f, 8f to 14f); drawPath(Path().apply { moveTo(3f, 9f); lineTo(13f, 9f); cubicTo(21f, 9f, 22f, 19f, 14f, 20f) }, color, style = stroke) }
                StudioGlyph.REDO -> { path(16f to 4f, 21f to 9f, 16f to 14f); drawPath(Path().apply { moveTo(21f, 9f); lineTo(11f, 9f); cubicTo(3f, 9f, 2f, 19f, 10f, 20f) }, color, style = stroke) }
                StudioGlyph.PLAY -> drawPath(Path().apply { moveTo(8f, 4f); lineTo(20f, 12f); lineTo(8f, 20f); close() }, color)
                StudioGlyph.PAUSE -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
                StudioGlyph.RESTART -> { path(8f to 3f, 3f to 8f, 9f to 9f); drawPath(Path().apply { moveTo(4f, 8f); cubicTo(11f, -1f, 23f, 7f, 19f, 16f); cubicTo(15f, 24f, 5f, 22f, 3f, 15f) }, color, style = stroke) }
                StudioGlyph.EXPAND -> { path(3f to 9f, 3f to 3f, 9f to 3f); path(15f to 3f, 21f to 3f, 21f to 9f); path(21f to 15f, 21f to 21f, 15f to 21f); path(9f to 21f, 3f to 21f, 3f to 15f) }
                StudioGlyph.EDIT -> { drawCircle(color, 3f, Offset(6f, 6f), style = stroke); drawCircle(color, 3f, Offset(6f, 18f), style = stroke); line(8f, 8f, 21f, 20f); line(8f, 16f, 21f, 4f) }
                StudioGlyph.AUDIO -> listOf(4f to 5f, 8f to 9f, 12f to 3f, 16f to 7f, 20f to 5f).forEach { (x, y) -> line(x, y, x, 24f - y) }
                StudioGlyph.TEXT -> { line(4f, 5f, 20f, 5f); line(12f, 5f, 12f, 20f); line(8f, 20f, 16f, 20f) }
                StudioGlyph.CAPTIONS -> { box(2f, 4f, 20f, 16f); line(6f, 10f, 10f, 10f); line(14f, 10f, 18f, 10f); line(6f, 15f, 18f, 15f) }
                StudioGlyph.EFFECTS, StudioGlyph.SPARKLES -> { path(12f to 2f, 15f to 9f, 22f to 12f, 15f to 15f, 12f to 22f, 9f to 15f, 2f to 12f, 9f to 9f, 12f to 2f); if (glyph == StudioGlyph.SPARKLES) { line(20f, 2f, 20f, 6f); line(18f, 4f, 22f, 4f) } }
                StudioGlyph.FORMAT -> { box(5f, 2f, 14f, 20f); line(2f, 7f, 2f, 17f); line(22f, 7f, 22f, 17f) }
                StudioGlyph.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.7f, Offset(it, 12f)) }
                StudioGlyph.FOLDER -> drawPath(Path().apply { moveTo(3f, 7f); lineTo(3f, 20f); lineTo(21f, 20f); lineTo(21f, 6f); lineTo(12f, 6f); lineTo(9f, 3f); lineTo(3f, 3f); close() }, color, style = stroke)
                StudioGlyph.EXPORT -> { path(6f to 8f, 12f to 2f, 18f to 8f); line(12f, 2f, 12f, 16f); path(3f to 15f, 3f to 21f, 21f to 21f, 21f to 15f) }
                StudioGlyph.SETTINGS -> { box(4f, 4f, 16f, 16f); line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 12f, 16f) }
            }
        }
    }
}
