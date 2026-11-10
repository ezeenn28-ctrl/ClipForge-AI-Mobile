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
                    session.load(listOf(PreviewPlayback.Source(landscape, 1000, 2500, 2f), PreviewPlayback.Source(portrait, 3000, 5000, .5f)), play = true)
                }
                await("second clip transition") { var result = false; scenario.onActivity { result = player.currentMediaItemIndex == 1 && player.currentPosition > 300 }; result }
                scenario.onActivity { assertEquals(.5f, player.playbackParameters.speed, 0f); assertEquals(1f, player.playbackParameters.pitch, 0f) }
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
            transformer = Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory()).addListener(object : Transformer.Listener {
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
        val clip = TimelineClip("caption", 0, 3000, caption = "HELLO #WORLD", captionEndMs = 3000, color = ColorAdjustments(brightness = .2f, warmth = .5f))
        val composition = TimelineExport.composition(Uri.fromFile(source), listOf(clip), true, false, false,
            39f, Color.WHITE, appearance = saved.captionAppearance)
        val output = File(context.cacheDir, "caption-appearance.mp4").apply { delete() }
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory()).addListener(object : Transformer.Listener {
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
                    val background = frame.getPixel(frame.width / 2, frame.height / 4)
                    assertEquals("Graded red channel", 64.0, Color.red(background).toDouble(), 12.0)
                    assertEquals("Graded green channel", 51.0, Color.green(background).toDouble(), 12.0)
                    assertEquals("Graded blue channel", 38.0, Color.blue(background).toDouble(), 12.0)
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

    @Test fun mixedSpeedExportKeepsOutputDurationAndVoicePitch() {
        val source = fixture("landscape.mp4")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "speed-output.mp4").apply { delete() }
        val wav = File(context.cacheDir, "speed-audio.wav")
        val clips = listOf(TimelineClip("fast", 1000, 3000, speed = 2f, subtitles = listOf(WhisperNative.Segment(1000, 2000, "TIMED TEST"))),
            TimelineClip("slow", 3000, 5000, speed = .5f, subtitles = listOf(WhisperNative.Segment(3000, 3500, "SLOW CUE"))))
        val music = fixture("music.wav")
        val composition = TimelineExport.composition(Uri.fromFile(source), clips, true, false, false, 39f, Color.YELLOW,
            MusicTrack(Uri.fromFile(music).toString(), "Synthetic music", 3000, timelineStartMs = 4500, volume = .4f, fadeInMs = 0, fadeOutMs = 0))
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, result: ExportResult) { done.countDown() }
                    override fun onError(c: Composition, result: ExportResult, error: ExportException) { failure.set(error); done.countDown() }
                }).build()
            transformer.start(composition, output.absolutePath)
        }
        try {
            assertTrue("Speed export timed out", done.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Speed export failed", it) }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Mixed speed duration: $duration", duration in 4800..5250)
                for (time in listOf(500_000L, 2_000_000L, 4_000_000L)) {
                    val frame = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    try { assertTrue("Speed export black at $time", hasPicture(frame)) } finally { frame.recycle() }
                }
                fun captionPixels(timeUs: Long): Int {
                    val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                    val frame = if (raw.width > raw.height && rotation in listOf(90, 270))
                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
                            .also { if (it !== raw) raw.recycle() } else raw
                    try {
                        var count = 0
                        for (y in frame.height * 7 / 10 until frame.height step 2) for (x in 0 until frame.width step 2) {
                            val c = frame.getPixel(x, y)
                            if (Color.red(c) > 170 && Color.green(c) > 170 && Color.blue(c) < 100) count++
                        }
                        return count
                    } finally { frame.recycle() }
                }
                assertTrue("Fast caption missing", captionPixels(250_000) > 50)
                assertTrue("Fast caption did not end", captionPixels(750_000) < 10)
                assertTrue("Slow caption missing", captionPixels(1_500_000) > 50)
                assertTrue("Slow caption did not end", captionPixels(2_500_000) < 10)
            } finally { retriever.release() }
            OfflineAudioPreparer.extractWav(context, Uri.fromFile(output), wav)
            val bytes = wav.readBytes()
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val rate = buffer.getInt(24)
            val samples = (bytes.size - 44) / 2
            for ((start, end) in listOf(200 to 800, 1800 to 3800)) {
                val first = start * rate / 1000
                val last = minOf(end * rate / 1000, samples)
                assertTrue("Missing retimed audio", last > first)
                var crossings = 0
                var previous = buffer.getShort(44 + first * 2)
                for (i in first + 1 until last) {
                    val sample = buffer.getShort(44 + i * 2)
                    if (previous <= 0 && sample > 0) crossings++
                    previous = sample
                }
                val frequency = crossings.toDouble() * rate / (last - first)
                assertEquals("Voice pitch changed at $start ms", 440.0, frequency, 15.0)
            }
            fun magnitude(startMs: Int, frequency: Double): Double {
                val first = startMs * rate / 1000
                val count = rate / 5
                assertTrue("Missing placed music audio", first + count < samples)
                var real = 0.0; var imaginary = 0.0
                for (i in 0 until count) {
                    val value = buffer.getShort(44 + (first + i) * 2).toDouble()
                    val phase = 2 * Math.PI * frequency * i / rate
                    real += value * kotlin.math.cos(phase); imaginary += value * kotlin.math.sin(phase)
                }
                return kotlin.math.sqrt(real * real + imaginary * imaginary) / count
            }
            assertTrue("Music entered before placement", magnitude(2000, 880.0) < 50)
            assertTrue("Music missing at output-clock placement", magnitude(4600, 880.0) > 100)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() }
            output.delete(); wav.delete()
        }
    }

    @Test fun previewColorGradesTextureAndResetLeavesLetterboxBlack() {
        val source = fixture("caption-canvas.mp4")
        ActivityScenario.launch(MediaHarnessActivity::class.java).use { scenario ->
            lateinit var player: ExoPlayer
            lateinit var window: android.view.Window
            var centerX = 0; var centerY = 0; var barX = 0
            scenario.onActivity { activity ->
                window = activity.window
                // Match the editor's black canvas, independent of the harness window theme.
                activity.preview.setBackgroundColor(Color.BLACK)
                activity.preview.layoutParams = android.widget.FrameLayout.LayoutParams(360, 360)
                player = PreviewPlayerFactory.create(activity, false)
                activity.preview.player = player
                PreviewPlayback(player).load(listOf(PreviewPlayback.Source(Uri.fromFile(source).toString())), play = true)
            }
            fun pixel(): Pair<Int, Int> {
                val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
                val done = CountDownLatch(1); val result = AtomicInteger(-1)
                try {
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        android.view.PixelCopy.request(window, bitmap, { code -> result.set(code); done.countDown() },
                            android.os.Handler(android.os.Looper.getMainLooper()))
                    }
                    assertTrue("Window copy timed out", done.await(5, TimeUnit.SECONDS))
                    assertEquals(android.view.PixelCopy.SUCCESS, result.get())
                    return bitmap.getPixel(centerX, centerY) to bitmap.getPixel(barX, centerY)
                } finally { bitmap.recycle() }
            }
            try {
                await("color fixture playback") { var ready = false; scenario.onActivity { ready = player.currentPosition > 400 }; ready }
                scenario.onActivity { activity ->
                    player.pause()
                    val position = IntArray(2)
                    activity.preview.getLocationInWindow(position)
                    centerX = position[0] + activity.preview.width / 2
                    centerY = position[1] + activity.preview.height / 2
                    barX = position[0] + 10
                    activity.preview.applyPreviewColor(ColorAdjustments(brightness = .2f, warmth = .5f))
                }
                await("graded texture") { val (center, _) = pixel(); Color.red(center) in 55..75 }
                val (center, bar) = pixel()
                assertEquals(51.0, Color.green(center).toDouble(), 10.0)
                assertEquals(38.0, Color.blue(center).toDouble(), 10.0)
                assertTrue("Letterbox was color graded", Color.red(bar) + Color.green(bar) + Color.blue(bar) < 20)
                scenario.onActivity { it.preview.applyPreviewColor(ColorAdjustments()) }
                await("neutral color reset") { val (c, _) = pixel(); Color.red(c) + Color.green(c) + Color.blue(c) < 20 }
            } finally { scenario.onActivity { it.preview.player = null; player.release() } }
        }
    }

    @Test fun dipTransitionRendersBlackAtBoundaryAndKeepsTheOriginalDuration() {
        val source = fixture("transition-cyan.mp4")
        val next = fixture("transition-red.mp4")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "boundary-transition.mp4").apply { delete() }
        val clips = listOf(TimelineClip("cyan", 0, 1000, transition = CutTransition(CutTransitionKind.DIP_BLACK)),
            TimelineClip("red", 0, 1000, source = Uri.fromFile(next).toString(), sourceDurationMs = 1000))
        val composition = TimelineExport.composition(Uri.fromFile(source), clips, true, false, true, 39f, Color.WHITE)
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, result: ExportResult) { done.countDown() }
                    override fun onError(c: Composition, result: ExportResult, error: ExportException) { failure.set(error); done.countDown() }
                }).build()
            transformer.start(composition, output.absolutePath)
        }
        try {
            assertTrue("Boundary export timed out", done.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Boundary export failed", it) }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("Transition changed sequence duration: $duration", duration in 1900..2100)
                fun center(time: Long): Int {
                    val frame = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    return try { frame.getPixel(frame.width / 2, frame.height / 2) } finally { frame.recycle() }
                }
                val before = center(500_000); val boundary = center(1_000_000); val after = center(1_500_000)
                assertTrue("First source missing", Color.green(before) + Color.blue(before) > 350)
                assertTrue("Cut boundary did not dip to black", Color.red(boundary) + Color.green(boundary) + Color.blue(boundary) < 40)
                assertTrue("Second source missing", Color.red(after) > 170 && Color.green(after) < 60)
            } finally { retriever.release() }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() }
            output.delete()
        }
    }

    @Test fun twoHourVideoDecodesBoundedAudioWindowsNearStartMiddleAndEnd() {
        val source = fixture("two-hours.mp4")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Fixture is not two hours: $duration", duration in 7_199_900..7_200_100)
        } finally { retriever.release() }
        for (start in listOf(0L, 3_600_000L, 7_199_000L)) {
            val wav = File(context.cacheDir, "long-window-$start.wav")
            try {
                OfflineAudioPreparer.extractWav(context, Uri.fromFile(source), wav, start, start + 1000)
                val bytes = wav.readBytes()
                val header = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val rate = header.getInt(24)
                val seconds = (bytes.size - 44).toDouble() / 2 / rate
                assertTrue("Incorrect window at $start: $seconds s", seconds in .9..1.1)
                assertTrue("Far seek created an unbounded WAV", bytes.size < 100_000)
            } finally { wav.delete() }
        }
    }

    @Test fun installedApplicationExposesItsAdaptiveLauncherIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.packageManager.getApplicationIcon(context.applicationInfo)
        assertTrue("Application launcher icon must be adaptive", icon is android.graphics.drawable.AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, 128, 128); icon.draw(android.graphics.Canvas(bitmap))
            var cyan = 0
            for (y in 0 until 128) for (x in 0 until 128) {
                val c = bitmap.getPixel(x, y)
                if (Color.green(c) > 150 && Color.blue(c) > 120 && Color.red(c) < 100) cyan++
            }
            assertTrue("Launcher mark is missing or clipped", cyan > 100)
        } finally { bitmap.recycle() }
    }

    @Test fun referenceFrameExportHasWhiteBandsHookAndTimedSoundEffects() {
        val source = fixture("landscape.mp4")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, "reference-sfx.mp4").apply { delete() }
        val wav = File(context.cacheDir, "reference-sfx.wav")
        val clip = TimelineClip("reference", 0, 4000, hook = "THE UNEXPECTED MOMENT",
            subtitles = listOf(WhisperNative.Segment(1500, 2000, "WAIT #WHAT")),
            sounds = listOf(ClipSound("pop", 1000, SoundKind.POP, .8f), ClipSound("impact", 2500, SoundKind.IMPACT, .8f)))
        val composition = TimelineExport.composition(Uri.fromFile(source), listOf(clip), true, false, true, 39f, Color.WHITE,
            preset = CaptionPreset.HIGHLIGHT, appearance = CaptionAppearance(positionY = .73f, layout = ReferenceLayout.WHITE_SQUARE),
            soundAssets = SoundAssets.ensure(context.filesDir))
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory())
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, result: ExportResult) { done.countDown() }
                    override fun onError(c: Composition, result: ExportResult, error: ExportException) { failure.set(error); done.countDown() }
                }).build()
            transformer.start(composition, output.absolutePath)
        }
        try {
            assertTrue("Reference export timed out", done.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Reference export failed", it) }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("SFX shortened the picture: $duration", duration in 3900..4200)
                for (time in listOf(500_000L, 1_750_000L, 3_500_000L)) {
                    val raw = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                    val frame = if (raw.width > raw.height && rotation in listOf(90,270))
                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true)
                            .also { if (it !== raw) raw.recycle() } else raw
                    try {
                        assertTrue("Portrait display expected", frame.height > frame.width)
                        for (y in listOf(frame.height / 20, frame.height * 19 / 20)) {
                            val c = frame.getPixel(frame.width / 2, y)
                            assertTrue("White reference bands disappeared at $time", Color.red(c) > 235 && Color.green(c) > 235 && Color.blue(c) > 235)
                        }
                        assertTrue("Video window is black", hasPicture(frame))
                        var ink = 0
                        for (y in frame.height / 10 until frame.height / 5 step 2)
                            for (x in frame.width / 10 until frame.width * 9 / 10 step 2) {
                                val c = frame.getPixel(x,y)
                                if (Color.red(c) + Color.green(c) + Color.blue(c) < 150) ink++
                            }
                        assertTrue("Hook missing before/between captions", ink > 100)
                    } finally { frame.recycle() }
                }
            } finally { retriever.release() }
            OfflineAudioPreparer.extractWav(context, Uri.fromFile(output), wav)
            val bytes = wav.readBytes()
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val rate = buffer.getInt(24)
            fun rms(fromMs: Int, toMs: Int): Double {
                val first = fromMs * rate / 1000
                val last = minOf(toMs * rate / 1000, (bytes.size - 44) / 2)
                assertTrue(last > first)
                var sum = 0.0
                for (i in first until last) { val value = buffer.getShort(44 + i * 2).toDouble(); sum += value * value }
                return kotlin.math.sqrt(sum / (last - first))
            }
            assertTrue("Unwanted audio before first effect", rms(200,800) < 50)
            assertTrue("Pop missing at 1 second", rms(1000,1150) > 150)
            assertTrue("Unwanted audio between effects", rms(1600,2200) < 50)
            assertTrue("Impact missing at 2.5 seconds", rms(2500,2800) > 150)
            assertTrue("Effect leaked past its end", rms(3300,3800) < 50)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() }
            output.delete(); wav.delete()
        }
    }

    @Test fun importedMp3PersistsAndMixesWithMusicAtTheChosenTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mp3 = fixture("sfx-import.mp3")
        val source = fixture("landscape.mp4")
        val music = fixture("music.wav")
        val asset = SoundLibrary.importSound(context, Uri.fromFile(mp3), "Imported test pop")
        assertTrue(File(Uri.parse(asset.source).path!!).isFile)
        val clip = TimelineClip("imported-sfx", 0, 4000,
            sounds = listOf(ClipSound("custom", 1000, SoundKind.POP, .8f, asset)))
        val projectFile = File(context.cacheDir, "custom-sfx.properties")
        ProjectStore.write(projectFile, EditorProject(Uri.fromFile(source).toString(), 8000, 0, 4000,
            timelineMode = true, timelineClips = listOf(clip)))
        val restored = ProjectStore.read(projectFile)!!.timelineClips
        assertEquals(asset, restored.single().sounds.single().asset)
        val composition = TimelineExport.composition(Uri.fromFile(source), restored, true, false, true, 39f, Color.WHITE,
            MusicTrack(Uri.fromFile(music).toString(), "Quiet test music", 3000, volume = .01f, fadeInMs = 0, fadeOutMs = 0))
        val output = File(context.cacheDir, "imported-sfx.mp4").apply { delete() }
        val wav = File(context.cacheDir, "imported-sfx-output.wav")
        val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer = Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory()).addListener(object : Transformer.Listener {
                override fun onCompleted(c: Composition, result: ExportResult) { done.countDown() }
                override fun onError(c: Composition, result: ExportResult, error: ExportException) { failure.set(error); done.countDown() }
            }).build()
            transformer.start(composition, output.absolutePath)
        }
        try {
            assertTrue("Imported SFX export timed out", done.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Imported SFX export failed", it) }
            OfflineAudioPreparer.extractWav(context, Uri.fromFile(output), wav)
            val bytes = wav.readBytes(); val data = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val rate = data.getInt(24)
            fun rms(from: Int, until: Int): Double {
                val first = from * rate / 1000; val last = minOf(until * rate / 1000, (bytes.size - 44) / 2)
                assertTrue(last > first); var energy = 0.0
                for (i in first until last) { val value = data.getShort(44 + i * 2).toDouble(); energy += value * value }
                return kotlin.math.sqrt(energy / (last - first))
            }
            assertTrue("SFX started before chosen time", rms(200,800) < 100)
            assertTrue("Imported SFX missing", rms(1100,1250) > 200)
            assertTrue("SFX did not end", rms(1800,2200) < 100)
            assertTrue("Unexpected trailing audio", rms(3300,3800) < 50)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() }
            output.delete(); wav.delete(); projectFile.delete()
        }
    }

    @Test fun neonFrameCaptionAndWarmLookMatchTheSharedRenderer() = styleExport(ReferenceLayout.NEON_SQUARE, CaptionPreset.NEON, "Warm film")
    @Test fun roundedPortraitStickerAndVintageLookMatchTheSharedRenderer() = styleExport(ReferenceLayout.PORTRAIT_CARD, CaptionPreset.STICKER, "Vintage")
    private fun styleExport(layout: ReferenceLayout, preset: CaptionPreset, look: String) {
        val source = fixture("transition-red.mp4")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appearance = CaptionThemes.apply(preset,CaptionAppearance(layout=layout,positionY=layout.captionY))
        val clip = TimelineClip("style",0,1000,hook="NEW LOOK",caption="HELLO #WORLD",captionEndMs=1000,effect=look)
        val output = File(context.cacheDir,"style-" + layout.name + ".mp4").apply { delete() }
        val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>(null)
        lateinit var transformer: Transformer
        val composition=TimelineExport.composition(Uri.fromFile(source),listOf(clip),true,false,true,39f,Color.WHITE,preset=preset,appearance=appearance)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            transformer=Transformer.Builder(context).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory()).addListener(object:Transformer.Listener {
                override fun onCompleted(c:Composition,result:ExportResult){done.countDown()}
                override fun onError(c:Composition,result:ExportResult,error:ExportException){failure.set(error);done.countDown()}
            }).build()
            transformer.start(composition,output.absolutePath)
        }
        try {
            assertTrue("Style export timeout",done.await(180,TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Style export failed",it) }
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.absolutePath)
                val raw=retriever.getFrameAtTime(500_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                val rotation=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt()?:0
                val frame=if(raw.width>raw.height && rotation in listOf(90,270))
                    Bitmap.createBitmap(raw,0,0,raw.width,raw.height,android.graphics.Matrix().apply{postRotate(rotation.toFloat())},true)
                        .also{if(it!==raw)raw.recycle()} else raw
                try {
                    fun at(x:Int,y:Int)=frame.getPixel(x*frame.width/720,y*frame.height/1280)
                    val matte=at(360,20)
                    for ((actual,expected) in listOf(Color.red(matte) to Color.red(layout.matteColor),Color.green(matte) to Color.green(layout.matteColor),Color.blue(matte) to Color.blue(layout.matteColor)))
                        assertEquals("Matte differs",expected.toDouble(),actual.toDouble(),12.0)
                    val center=at(360,640)
                    val grade=VideoLooks.color(look).coefficients()
                    val expected=floatArrayOf(grade[0]+grade[4],grade[5]+grade[9],grade[10]+grade[14])
                    for ((actual,value) in listOf(Color.red(center) to expected[0],Color.green(center) to expected[1],Color.blue(center) to expected[2]))
                        assertEquals("Video look differs from preview",value.coerceIn(0f,1f)*255.0,actual.toDouble(),15.0)
                    val corner=at(3,layout.videoTop.toInt()+3)
                    if(layout.cornerRadius>0) assertTrue("Rounded corner is not masked",Color.blue(corner)>210&&Color.green(corner)>210)
                    var white=0;var accent=0;var black=0
                    val start=(appearance.positionY*1280-70).toInt()
                    for(y in start until start+95 step 2)for(x in 40..680 step 2){
                        val c=at(x,y);val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
                        if(r>220&&g>220&&b>220)white++
                        if(r+g+b<120)black++
                        if(preset==CaptionPreset.NEON && r<110&&g>150&&b>170)accent++
                        if(preset==CaptionPreset.STICKER && r>170&&g in 30..160&&b>110)accent++
                    }
                    assertTrue("Caption/card white pixels missing",white>30)
                    assertTrue("Caption highlight missing",accent>15)
                    if(preset==CaptionPreset.STICKER)assertTrue("Sticker ink missing",black>20)
                } finally { frame.recycle() }
            } finally { retriever.release() }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { transformer.cancel() };output.delete()
        }
    }
}
