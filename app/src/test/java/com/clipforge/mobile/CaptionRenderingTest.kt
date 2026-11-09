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
class CaptionRenderingTest {
    @Test fun subtitleWithoutHookStaysInSubtitlePositionAndUsesItsColor() {
        val bitmap = makeCaptionOverlay("", "Readable subtitle", 39f, Color.YELLOW)
        try {
            var topPixels = 0; var bottomPixels = 0; var yellowPixels = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 0) {
                    if (y < 500) topPixels++ else bottomPixels++
                    if (Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) < 80) yellowPixels++
                }
            }
            assertEquals(0, topPixels); assertTrue(bottomPixels > 100); assertTrue(yellowPixels > 100)
        } finally { bitmap.recycle() }
    }
    @Test fun manualCaptionOverlayPreservesSubtitleStyle() {
        val overlay = timedCaptionOverlay("", "Manual subtitle", 1000, 2000, 0, subtitleSize = 50f, subtitleColor = Color.YELLOW)
        try {
            val before = overlay.getBitmap(0)
            assertEquals(Color.TRANSPARENT, before.getPixel(360, 950))
            val visible = overlay.getBitmap(1_500_000)
            var yellow = 0
            for (y in 900 until 1100) for (x in 0 until visible.width) {
                val c = visible.getPixel(x, y)
                if (Color.red(c) > 200 && Color.green(c) > 200 && Color.blue(c) < 80 && Color.alpha(c) > 0) yellow++
            }
            assertTrue(yellow > 100)
        } finally { overlay.release() }
    }

    private fun coloredBounds(bitmap: android.graphics.Bitmap, yellow: Boolean): IntRange {
        var left = bitmap.width
        var right = -1
        for (y in 900 until 1120) for (x in 0 until bitmap.width) {
            val c = bitmap.getPixel(x, y)
            if (Color.alpha(c) > 230 && Color.red(c) > 230 && Color.green(c) > 230 &&
                (if (yellow) Color.blue(c) < 40 else Color.blue(c) > 230)) {
                left = minOf(left, x); right = maxOf(right, x)
            }
        }
        assertTrue("Expected visible " + (if (yellow) "yellow" else "white") + " words", right >= left)
        return left..right
    }

    @Test fun explicitHighlightColorsOnlyTheChosenWord() {
        val bitmap = makeCaptionOverlay("", "FIRST #MIDDLE LAST")
        try {
            val white = coloredBounds(bitmap, false)
            val yellow = coloredBounds(bitmap, true)
            assertTrue("First word lost its white fill", white.first < yellow.first)
            assertTrue("Last word lost its white fill", white.last > yellow.last)
        } finally { bitmap.recycle() }
    }

    @Test fun boldHighlightKeepsPrefixWhiteAndEmphasizesLastWord() {
        val overlay = timedCaptionOverlay("", "YOUR MOMENT", 1000, 2000, 0, preset = CaptionPreset.HIGHLIGHT)
        try {
            val bitmap = overlay.getBitmap(1_500_000)
            val white = coloredBounds(bitmap, false)
            val yellow = coloredBounds(bitmap, true)
            assertTrue(white.last < yellow.first)
            val file = java.io.File("build/outputs/studio-previews/bold-highlight-caption.png").apply { parentFile.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { overlay.release() }
    }

    @Test fun wideWordsStayInsideTheCaptionSafeArea() {
        val bitmap = makeCaptionOverlay("", "WWWWWWWWWWWWWWWWWWWW", 55f)
        try {
            val white = coloredBounds(bitmap, false)
            assertTrue("Caption overflows left", white.first >= 40)
            assertTrue("Caption overflows right", white.last <= 680)
        } finally { bitmap.recycle() }
    }
}
