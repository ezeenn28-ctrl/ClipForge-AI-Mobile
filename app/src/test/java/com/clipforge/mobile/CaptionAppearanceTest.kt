package com.clipforge.mobile

import android.graphics.Color
import androidx.media3.common.util.Size
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CaptionAppearanceTest {
    private val custom = CaptionAppearance(CaptionFont.MONO, .45f, CaptionColor.PINK, CaptionColor.CYAN, CaptionAlignment.LEFT)

    @Test fun customAppearanceMovesAndColorsOnlyCaptions() {
        val bitmap = makeCaptionOverlay("", "HELLO #WORLD", appearance = custom)
        try {
            var cyan = 0; var pink = 0; var minX = bitmap.width; var maxY = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) > 230) {
                    if (Color.green(c) > 200 && Color.blue(c) > 200 && Color.red(c) < 40) cyan++
                    if (Color.red(c) > 240 && Color.blue(c) in 140..210 && Color.green(c) in 60..150) pink++
                    minX = minOf(minX, x); maxY = maxOf(maxY, y)
                }
            }
            assertTrue(cyan > 100); assertTrue(pink > 100)
            assertTrue("Alignment was not applied", minX in 40..70)
            assertTrue("Position was not applied", maxY in 540..610)
        } finally { bitmap.recycle() }
    }

    @Test fun exportCanvasUsesTheSameHeightAsPreviewInBothOrientations() {
        val overlay = timedCaptionOverlay("", "Caption", 0, 1000, 0)
        try {
            overlay.configure(Size(1080, 1920))
            assertEquals(1.5f, overlay.getOverlaySettings(0).scale.first, .0001f)
            assertEquals(1.5f, overlay.getOverlaySettings(0).scale.second, .0001f)
            overlay.configure(Size(1920, 1080))
            assertEquals(1080f / 1280f, overlay.getOverlaySettings(0).scale.first, .0001f)
        } finally { overlay.release() }
    }

    @Test fun projectRoundTripPreservesAppearanceAndOlderFilesKeepNeutralDefaults() {
        val directory = Files.createTempDirectory("caption-appearance").toFile()
        try {
            val file = File(directory, "project.properties")
            val project = EditorProject("content://videos/1", 30_000, 0, 20_000,
                drafts = listOf(HighlightDraft(0, 20_000, approved = true)), captionAppearance = custom)
            ProjectStore.write(file, project)
            val restored = ProjectStore.read(file)!!
            assertEquals(custom, restored.captionAppearance)
            assertFalse(restored.drafts.single().approved)
            file.writeText(file.readText().lineSequence().filterNot { it.startsWith("caption.") }.joinToString("\n"))
            assertEquals(CaptionAppearance(), ProjectStore.read(file)!!.captionAppearance)
            file.appendText("\ncaption.y=NaN\n")
            try { ProjectStore.read(file); fail("Invalid caption position accepted") }
            catch (_: IllegalArgumentException) {}
        } finally { directory.deleteRecursively() }
    }

    @Test fun phonePreviewUsesABoundedBitmapWithTheSameCanvasAspectRatio() {
        val bitmap = makeCaptionOverlay("", "Preview", appearance = custom, bitmapScale = .5f)
        try {
            assertEquals(360, bitmap.width); assertEquals(640, bitmap.height)
            assertTrue(bitmap.allocationByteCount <= 360 * 640 * 4)
        } finally { bitmap.recycle() }
    }

    @Test fun systemFontsProduceDifferentReadableGlyphs() {
        val sans = makeCaptionOverlay("", "AgimW", appearance = CaptionAppearance(font = CaptionFont.SANS))
        val mono = makeCaptionOverlay("", "AgimW", appearance = CaptionAppearance(font = CaptionFont.MONO))
        try {
            val first = IntArray(sans.width * sans.height); val second = IntArray(first.size)
            sans.getPixels(first, 0, sans.width, 0, 0, sans.width, sans.height)
            mono.getPixels(second, 0, mono.width, 0, 0, mono.width, mono.height)
            assertFalse("Font control has no effect on rendering", first.contentEquals(second))
        } finally { sans.recycle(); mono.recycle() }
    }
}
