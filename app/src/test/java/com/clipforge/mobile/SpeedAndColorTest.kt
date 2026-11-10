package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpeedAndColorTest {
    private val fast = TimelineClip("fast", 1000, 5000, speed = 2f,
        subtitles = listOf(WhisperNative.Segment(2000, 4000, "fast cue")))
    private val slow = TimelineClip("slow", 6000, 8000, speed = .5f,
        subtitles = listOf(WhisperNative.Segment(6500, 7500, "slow cue")))

    @Test fun outputClockMapsMixedSpeedsSeeksAndSourceCaptions() {
        val clips = listOf(fast, slow)
        assertEquals(6000L, TimelineModel.duration(clips))
        assertEquals(TimelineModel.Position(0, 500, 2000), TimelineModel.position(clips, 500))
        assertEquals(TimelineModel.Position(1, 1000, 6500), TimelineModel.position(clips, 3000))
        assertEquals(TimelineModel.Position(1, 4000, 8000), TimelineModel.position(clips, 6000))
        val cues = TimelineModel.subtitles(clips)
        assertEquals(listOf(500L, 3000L), cues.map { it.startMs })
        assertEquals(listOf(1500L, 5000L), cues.map { it.endMs })
    }
    @Test fun splittingTrimmingAndSpeedChangesKeepFadesInsideOutputDuration() {
        val clip = fast.copy(fadeInMs = 1000, fadeOutMs = 1000)
        val split = TimelineModel.split(listOf(clip), 1000, "right")
        assertEquals(listOf(1000L, 3000L), split.map { it.startMs })
        assertEquals(2000L, TimelineModel.duration(split))
        split.forEach { it.validate(8000) }
        assertEquals(500L, clip.trimmed(1000, 2000).fadeInMs)
        assertEquals(1000L, slow.copy(fadeInMs = 3000).withSpeed(2f).fadeInMs)
        assertEquals(.5f, TimelineModel.audioGain(clip, clip.outputOffsetMs(1000)), .001f)
    }
    @Test fun invalidSpeedAndNonfiniteColorCannotBeSaved() {
        for (speed in listOf(0f, .49f, 2.01f, Float.NaN, Float.POSITIVE_INFINITY)) {
            try { fast.copy(speed = speed).validate(8000); fail("Accepted speed $speed") } catch (_: IllegalArgumentException) {}
        }
        try { ColorAdjustments(warmth = Float.NaN).validate(); fail("Accepted NaN color") } catch (_: IllegalArgumentException) {}
    }
    @Test fun bothColorRepresentationsUseTheSameNormalizedCoefficients() {
        val color = ColorAdjustments(.1f, 1.2f, .7f, .5f)
        val preview = color.previewMatrix()
        val export = color.exportMatrix()
        for (row in 0..2) {
            for (column in 0..2) assertEquals(preview[row * 5 + column], export[column * 4 + row], .00001f)
            assertEquals(preview[row * 5 + 4] / 255f, export[12 + row], .00001f)
        }
        assertArrayEquals(floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f), ColorAdjustments().exportMatrix(), .00001f)
        val mono = color.coefficients(true)
        assertEquals(mono[0], mono[5], .00001f)
        assertEquals(mono[1], mono[6], .00001f)
    }
    @Test fun projectsRoundTripSpeedAndColorAndLegacyFieldsDefaultToNeutral() {
        val dir = Files.createTempDirectory("speed-color").toFile()
        try {
            val file = File(dir, "project.properties")
            val cut = fast.copy(color = ColorAdjustments(.2f, 1.1f, 1.2f, -.3f))
            val project = EditorProject("content://test/video", 8000, 0, 8000, timelineClips = listOf(cut), timelineMode = true)
            ProjectStore.write(file, project)
            assertEquals(cut, ProjectStore.read(file)!!.timelineClips.single())
            file.writeText(file.readText().lineSequence().filterNot { it.startsWith("timeline.0.speed=") || it.startsWith("timeline.0.color.") }.joinToString("\n"))
            val restored = ProjectStore.read(file)!!.timelineClips.single()
            assertEquals(1f, restored.speed, 0f); assertEquals(ColorAdjustments(), restored.color)
        } finally { dir.deleteRecursively() }
    }
}
