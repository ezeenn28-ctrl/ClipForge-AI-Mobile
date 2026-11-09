package com.clipforge.mobile

import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings

/** Match the preview's centered 9:16 text canvas to the video's full height.
 * Media3 otherwise retains the bitmap's native pixel dimensions. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
abstract class CaptionBitmapOverlay : BitmapOverlay() {
    private var settings = OverlaySettings.Builder().build()
    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        val scale = videoSize.height / 1280f
        settings = OverlaySettings.Builder().setScale(scale, scale).build()
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
}
