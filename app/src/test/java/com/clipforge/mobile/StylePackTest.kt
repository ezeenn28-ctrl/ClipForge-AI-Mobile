package com.clipforge.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
class StylePackTest {
    @Test fun allReferenceThemesKeepTheirDeclaredMatteAndTransparentPictureWindow() {
        assertEquals(9, ReferenceLayout.entries.size)
        for (layout in ReferenceLayout.entries) {
            val bitmap = makeCaptionOverlay("", "", appearance = CaptionAppearance(layout = layout))
            try {
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(360,640))
                if (layout != ReferenceLayout.STANDARD) {
                    assertTrue(layout.videoTop >= 160f && layout.videoBottom <= 1120f)
                    assertEquals(layout.matteColor, bitmap.getPixel(360,20))
                    assertEquals(layout.matteColor, bitmap.getPixel(360,1260))
                    assertEquals(Color.TRANSPARENT, bitmap.getPixel(360,layout.videoTop.toInt()+10))
                    assertTrue(layout.captionY in .35f.. .85f)
                }
                if (layout.cornerRadius > 0) assertEquals(layout.matteColor, bitmap.getPixel(3,layout.videoTop.toInt()+3))
            } finally { bitmap.recycle() }
        }
    }
    @Test fun everyCaptionPresetHasADistinctRenderedDefault() {
        val hashes = CaptionPreset.entries.map { preset ->
            val appearance = CaptionThemes.apply(preset, CaptionAppearance(positionY = .55f))
            val bitmap = makeCaptionOverlay("", "Hello world", 39f, preset = preset, appearance = appearance)
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                assertTrue(pixels.count { Color.alpha(it)>0 } > 100)
                pixels.contentHashCode()
            } finally { bitmap.recycle() }
        }
        assertEquals(10, hashes.size); assertEquals(hashes.size, hashes.toSet().size)
    }
    @Test fun newStylesKeepLongLargeCaptionsInsideTheCanvas() {
        for (preset in CaptionPreset.entries.drop(4)) {
            val appearance = CaptionThemes.apply(preset, CaptionAppearance(positionY = .85f))
            val bitmap = makeCaptionOverlay("", "THIS IS A LONG CAPTION WITH SEVERAL WORDS AND A #HIGHLIGHT TO WRAP OVER THREE LINES", 55f, preset = preset, appearance = appearance)
            try {
                for (y in 0 until bitmap.height) {
                    assertEquals(Color.TRANSPARENT, bitmap.getPixel(0,y))
                    assertEquals(Color.TRANSPARENT, bitmap.getPixel(719,y))
                }
                for (x in 0 until bitmap.width) assertEquals("Clipped caption bottom: " + preset, Color.TRANSPARENT, bitmap.getPixel(x,1279))
            } finally { bitmap.recycle() }
        }
    }
    @Test fun lookGradesUseEquivalentTextureAndExportMatrices() {
        val manual = ColorAdjustments(.03f,1.05f,.9f,.1f)
        for (name in VideoLooks.names) {
            val grade = VideoLooks.color(name,manual)
            val preview = grade.previewMatrix(name == "Monochrome")
            val export = grade.exportMatrix(name == "Monochrome")
            val rgb = floatArrayOf(40f,120f,190f)
            for (row in 0..2) {
                val first = (0..2).sumOf { (preview[row*5+it] * rgb[it]).toDouble() } + preview[row*5+4]
                val second = (0..2).sumOf { (export[it*4+row] * rgb[it] / 255f).toDouble() } + export[12+row]
                assertEquals(first/255.0,second, .00001)
            }
        }
    }
    @Test fun looksPreserveNeutralDefaultsAndClampCombinedManualAdjustments() {
        assertEquals(ColorAdjustments(),VideoLooks.color("None"))
        assertEquals(6, VideoLooks.names.drop(3).map { VideoLooks.color(it) }.toSet().size)
        for (name in VideoLooks.names) for (manual in listOf(ColorAdjustments(.4f,1.5f,2f,1f),ColorAdjustments(-.4f,.5f,0f,-1f))) VideoLooks.color(name,manual).validate()
        try { VideoLooks.color("unknown"); fail("Unknown look accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun stylesFramesLooksAndNewFontsPersistWithoutRestoringApproval() {
        val directory = Files.createTempDirectory("style-pack").toFile()
        try {
            for (i in CaptionPreset.entries.indices) {
                val preset = CaptionPreset.entries[i]; val layout = ReferenceLayout.entries[i % ReferenceLayout.entries.size]
                val look = VideoLooks.names[i % VideoLooks.names.size]
                val appearance = CaptionThemes.apply(preset,CaptionAppearance(layout = layout, positionY = layout.captionY))
                val clip = TimelineClip("clip",0,3000,effect = look)
                val project = EditorProject("content://videos/1",5000,0,3000, effect = look, timelineClips = listOf(clip), timelineMode = true,
                    captionPreset = preset.name, captionAppearance = appearance, drafts = listOf(HighlightDraft(0,3000,approved = true,effect = look)))
                val file = directory.resolve("project.properties")
                ProjectStore.write(file,project)
                assertEquals(project.reviewedAgain(),ProjectStore.read(file))
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun applyingCaptionThemeRetainsTheSelectedReferenceFrameAndPosition() {
        val frame = CaptionAppearance(layout = ReferenceLayout.PORTRAIT_CARD,positionY = .82f)
        for (preset in CaptionPreset.entries) {
            val themed = CaptionThemes.apply(preset,frame)
            assertEquals(frame.layout,themed.layout); assertEquals(frame.positionY,themed.positionY,0f)
        }
    }
    @Test fun renderStyleAndFrameContactSheetsForVisualReview() {
        val output = File("build/outputs/studio-previews").apply { mkdirs() }
        val frames = Bitmap.createBitmap(540,1032,Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frames); frameCanvas.drawColor(0xFF263244.toInt())
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12f }
        ReferenceLayout.entries.forEachIndexed { i,layout ->
            val tile = Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888)
            val canvas = Canvas(tile); canvas.drawColor(0xFF43637D.toInt())
            val overlay = makeCaptionOverlay("Your hook","Your #moment",39f,preset=CaptionPreset.HIGHLIGHT,
                appearance=CaptionAppearance(layout=layout,positionY=layout.captionY),bitmapScale=.25f)
            canvas.drawBitmap(overlay,0f,0f,null)
            frameCanvas.drawBitmap(tile,(i%3)*180f,(i/3)*344f,null)
            frameCanvas.drawText(layout.label,(i%3)*180f+5,(i/3)*344f+335,label)
            overlay.recycle();tile.recycle()
        }
        File(output,"style-pack-frames.png").outputStream().use { frames.compress(Bitmap.CompressFormat.PNG,100,it) };frames.recycle()
        val styles = Bitmap.createBitmap(720,660,Bitmap.Config.ARGB_8888)
        val styleCanvas = Canvas(styles);styleCanvas.drawColor(0xFF263244.toInt())
        CaptionPreset.entries.forEachIndexed { i,preset ->
            val bitmap=makeCaptionOverlay("","Hello world",42f,preset=preset,appearance=CaptionThemes.apply(preset,CaptionAppearance(positionY=.55f)),bitmapScale=.5f)
            val crop=Bitmap.createBitmap(bitmap,0,305,360,108)
            styleCanvas.drawBitmap(crop,(i%2)*360f,(i/2)*132f,null)
            styleCanvas.drawText(preset.label,(i%2)*360f+12,(i/2)*132f+125,label)
            crop.recycle();bitmap.recycle()
        }
        File(output,"style-pack-captions.png").outputStream().use { styles.compress(Bitmap.CompressFormat.PNG,100,it) };styles.recycle()
    }
}
