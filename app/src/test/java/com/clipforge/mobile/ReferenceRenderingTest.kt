package com.clipforge.mobile

import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferenceRenderingTest {
    @Test fun whiteMatteKeepsTransparentVideoWindowInBothPresets() {
        for (layout in listOf(ReferenceLayout.WHITE_SQUARE, ReferenceLayout.WHITE_CLASSIC)) {
            val bitmap = makeCaptionOverlay("", "", appearance = CaptionAppearance(layout = layout))
            try {
                assertEquals(Color.WHITE, bitmap.getPixel(360, 40))
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(360, 640))
                assertEquals(Color.WHITE, bitmap.getPixel(360, 1250))
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(20, layout.videoTop.toInt() + 2))
            } finally { bitmap.recycle() }
        }
    }
    @Test fun whiteFrameAndHookRemainVisibleBetweenSubtitleCues() {
        val appearance = CaptionAppearance(layout = ReferenceLayout.WHITE_SQUARE)
        val overlay = segmentSubtitleOverlay("My hook", listOf(WhisperNative.Segment(1000, 2000, "HELLO")), 0, 39f, Color.WHITE, appearance = appearance)
        try {
            for (time in listOf(0L, 1_500_000L, 2_500_000L)) {
                val bitmap = overlay.getBitmap(time)
                assertEquals(Color.WHITE, bitmap.getPixel(360, 40))
                var dark = 0
                for (y in 130..250) for (x in 48..672) {
                    val c = bitmap.getPixel(x,y)
                    if (Color.alpha(c) > 230 && Color.red(c) < 80 && Color.green(c) < 80 && Color.blue(c) < 80) dark++
                }
                assertTrue("Readable hook missing between captions", dark > 100)
            }
        } finally { overlay.release() }
    }
}
