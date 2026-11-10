package com.clipforge.mobile

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.TextureView
import androidx.media3.ui.PlayerView

/** Grade the video texture only: letterbox bars and sibling caption layers stay unchanged. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerView.applyPreviewColor(color: ColorAdjustments, monochrome: Boolean = false) {
    val texture = videoSurfaceView as? TextureView ?: return
    texture.setLayerPaint(Paint().apply {
        if (color != ColorAdjustments() || monochrome) colorFilter = ColorMatrixColorFilter(color.previewMatrix(monochrome))
    })
}
