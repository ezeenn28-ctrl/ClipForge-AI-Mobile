package com.clipforge.mobile

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaPipelineDeviceTest {
    private fun fixture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return File(instrumentation.targetContext.cacheDir, name).also { file ->
            instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }
    private fun await(label: String, timeoutSeconds: Int = 20, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
        while (System.nanoTime() < end) { if (condition()) return; Thread.sleep(50) }
        fail("Timed out: $label")
    }
    private fun hasPicture(bitmap: Bitmap): Boolean {
        var nonBlack = 0
        for (y in bitmap.height / 4 until bitmap.height * 3 / 4 step 8)
            for (x in bitmap.width / 4 until bitmap.width * 3 / 4 step 8) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) + Color.green(c) + Color.blue(c) > 60) nonBlack++
            }
        return nonBlack > 10
    }
    @Test fun realDecoderSurvivesHighlightsTimelineTransitionAndRestart() = exercisePreview(false)
    @Test fun compatibleDecoderSurvivesTheSameWorkflow() = exercisePreview(true)
    private fun signature(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
        return try { val pixels = IntArray(256); small.getPixels(pixels, 0, 16, 0, 0, 16, 16); pixels.contentHashCode() }
        finally { if (small !== bitmap) small.recycle() }
    }
    private fun exercisePreview(compatible: Boolean) {
        val landscape = fixture("landscape.mp4").toURI().toString()
        val portrait = fixture("portrait.mp4").toURI().toString()
        ActivityScenario.launch(MediaHarnessActivity::class.java).use { scenario ->
            lateinit var player: ExoPlayer
            lateinit var session: PreviewPlayback
            val error = AtomicReference<String?>(null)
            val frames = AtomicInteger(0)
            var firstPicture = 0
            scenario.onActivity { activity ->
                player = PreviewPlayerFactory.create(activity, compatible)
                activity.preview.player = player
                session = PreviewPlayback(player)
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { frames.incrementAndGet() }
                    override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error.set(e.errorCodeName + ":" + e.message) }
                })
                session.load(listOf(PreviewPlayback.Source(landscape)), play = true)
            }
            try {
                await("source playback") { var result = false; scenario.onActivity { result = player.currentPosition > 700 }; result }
                scenario.onActivity { activity ->
                    val bitmap = (activity.preview.videoSurfaceView as android.view.TextureView).bitmap!!
                    firstPicture = signature(bitmap); bitmap.recycle()
                    player.seekTo(2000); session.play(2000, 4000)
                }
                await("highlight playback") { var result = false; scenario.onActivity { result = player.currentPosition > 2400 }; result }
                scenario.onActivity {
                    session.load(listOf(PreviewPlayback.Source(landscape, 1000, 2500), PreviewPlayback.Source(portrait, 3000, 5000)), play = true)
                }
                await("second clip transition") { var result = false; scenario.onActivity { result = player.currentMediaItemIndex == 1 && player.currentPosition > 300 }; result }
                await("sequence completion") { var result = false; scenario.onActivity { result = player.playbackState == Player.STATE_ENDED }; result }
                assertNull(error.get()); assertTrue("No rendered video frames", frames.get() >= 1)
                scenario.onActivity { activity ->
                    val bitmap = (activity.preview.videoSurfaceView as android.view.TextureView).bitmap
                    assertNotNull(bitmap); assertTrue("Preview is black", hasPicture(bitmap!!))
                    assertNotEquals("Picture did not change across sources", firstPicture, signature(bitmap)); bitmap.recycle()
                    session.play(restart = true)
                }
                await("sequence restart") { var result = false; scenario.onActivity { result = player.currentMediaItemIndex == 0 && player.currentPosition > 200 }; result }
                assertNull(error.get())
            } finally { scenario.onActivity { it.preview.player = null; player.release() } }
        }
    }
    @Test fun realExportProducesPortraitVideoWithMixedAudioCaptionsAndEffects() {
        val first = fixture("landscape.mp4"); val second = fixture("portrait.mp4"); val music = fixture("music.wav")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "synthetic-pipeline.mp4").apply { delete() }
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        val clips = listOf(TimelineClip("a", 1000, 2500, subtitles = listOf(WhisperNative.Segment(1000, 2000, "Synthetic test")),
            visuals = ClipVisuals(end = MotionPoint(1.1f), fadeInMs = 100)),
            TimelineClip("b", 3000, 4500, source = Uri.fromFile(second).toString(), sourceDurationMs = 8000, effect = "Monochrome", visuals = ClipVisuals(fadeOutMs = 100)))
        val composition = TimelineExport.composition(Uri.fromFile(first), clips, true, false, false, 39f, Color.YELLOW,
            MusicTrack(Uri.fromFile(music).toString(), "Synthetic tone", 3000, timelineStartMs = 500), CaptionPreset.CARD)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                override fun onCompleted(c: Composition, result: ExportResult) { done.countDown() }
                override fun onError(c: Composition, result: ExportResult, error: ExportException) { failure.set(error); done.countDown() }
            }).build()
            transformer.start(composition, output.absolutePath)
        }
        try {
            assertTrue("Synthetic export timed out", done.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Synthetic export failed", it) }
            assertTrue(output.length() > 1000)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val encodedWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                val encodedHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                assertTrue("Unexpected rotation: $rotation", rotation in listOf(0, 90, 180, 270))
                // Transformer may encode portrait output sideways for encoder compatibility.
                // Validate the display canvas after MP4 rotation, not just the encoded frame.
                val swapsAxes = rotation == 90 || rotation == 270
                val displayWidth = if (swapsAxes) encodedHeight else encodedWidth
                val displayHeight = if (swapsAxes) encodedWidth else encodedHeight
                assertEquals("Display width ($encodedWidth x $encodedHeight, rotation=$rotation)", 1080, displayWidth)
                assertEquals("Display height ($encodedWidth x $encodedHeight, rotation=$rotation)", 1920, displayHeight)
                assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Unexpected duration: $duration", duration in 2700..3400)
                // The default retriever option chooses a sync frame, which can be
                // the intentionally black first frame of a fade-in. Decode near
                // the requested time so this checks visible content in both cuts.
                for (timeUs in listOf(750_000L, 1_000_000L, 2_000_000L)) {
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    try { assertTrue("Export is black at $timeUs us", hasPicture(frame)) }
                    finally { frame.recycle() }
                }
            } finally { retriever.release() }
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() }; output.delete() }
    }

    @Test fun savedCaptionAppearanceIsRenderedAtThePreviewPosition() {
        val source = fixture("caption-canvas.mp4")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = File(context.cacheDir, "caption-appearance.properties")
        val appearance = CaptionAppearance(CaptionFont.MONO, .45f, CaptionColor.PINK, CaptionColor.CYAN, CaptionAlignment.LEFT)
        ProjectStore.write(snapshot, EditorProject(Uri.fromFile(source).toString(), 3000, 0, 3000, captionAppearance = appearance))
        val saved = ProjectStore.read(snapshot)!!
        val clip = TimelineClip("caption", 0, 3000, caption = "HELLO #WORLD", captionEndMs = 3000)
        val composition = TimelineExport.composition(Uri.fromFile(source), listOf(clip), true, false, false,
            39f, Color.WHITE, appearance = saved.captionAppearance)
        val output = File(context.cacheDir, "caption-appearance.mp4").apply { delete() }
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                override fun onCompleted(c: Composition, result: ExportResult) { done.countDown() }
                override fun onError(c: Composition, result: ExportResult, error: ExportException) { failure.set(error); done.countDown() }
            }).build()
            transformer.start(composition, output.absolutePath)
        }
        try {
            assertTrue("Caption export timed out", done.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Caption export failed", it) }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val raw = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                val frame = if (raw.width > raw.height && rotation in listOf(90, 270)) {
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                        android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
                } else raw
                try {
                    assertTrue("Portrait display frame expected", frame.height > frame.width)
                    var pink = 0; var cyan = 0; var minX = frame.width; var maxY = -1
                    for (y in 0 until frame.height step 2) for (x in 0 until frame.width step 2) {
                        val c = frame.getPixel(x, y)
                        val isPink = Color.red(c) > 170 && Color.blue(c) > 110 && Color.green(c) in 30..160
                        val isCyan = Color.red(c) < 100 && Color.green(c) > 150 && Color.blue(c) > 170
                        if (isPink) pink++
                        if (isCyan) cyan++
                        if (isPink || isCyan) { minX = minOf(minX, x); maxY = maxOf(maxY, y) }
                    }
                    assertTrue("Text color missing from encoded video", pink > 20)
                    assertTrue("Highlight color missing from encoded video", cyan > 20)
                    val left = minX.toFloat() / frame.width
                    val bottom = maxY.toFloat() / frame.height
                    assertTrue("Caption left alignment differs from preview: $left", left in .045f.. .12f)
                    assertTrue("Caption position differs from preview: $bottom", bottom in .43f.. .48f)
                } finally { frame.recycle() }
            } finally { retriever.release() }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() }
            output.delete(); snapshot.delete()
        }
    }
}
