package com.clipforge.mobile

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint

/** Color is a view layer, avoiding video-renderer flushes when the user changes tools. */
fun Modifier.previewMonochrome(enabled: Boolean): Modifier = if (!enabled) this else drawWithCache {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    onDrawWithContent {
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
        drawContent()
        drawContext.canvas.restore()
    }
}
