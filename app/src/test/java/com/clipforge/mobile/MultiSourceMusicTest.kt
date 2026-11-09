package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MultiSourceMusicTest {
    @Test fun differentSourcesRetainOwnDurationsWhenReorderedAndSplit() {
        val first = TimelineClip("a", 1000, 5000)
        val second = TimelineClip("b", 180_000, 200_000, source = "content://media/second", sourceDurationMs = 240_000,
            subtitles = listOf(WhisperNative.Segment(181_000, 183_000, "Second video")))
        val clips = TimelineModel.move(listOf(first, second), 1, -1)
        TimelineModel.validate(clips, 10_000)
        val split = TimelineModel.split(clips, 10_000, "split")
        assertEquals(second.source, split[1].source)
        assertEquals(second.sourceDurationMs, split[1].sourceDurationMs)
        assertEquals(1000L, TimelineModel.subtitles(split).first().startMs)
        assertEquals("content://media/primary", first.resolvedSource("content://media/primary"))
    }
    @Test fun rejectsRemoteAndOutOfBoundsExtraSources() {
        for (clip in listOf(TimelineClip("a", 0, 2000, source = "https://example.com/a.mp4", sourceDurationMs = 3000),
            TimelineClip("a", 0, 4000, source = "content://media/a", sourceDurationMs = 3000))) {
            try { TimelineModel.validate(listOf(clip), 100_000); fail("Invalid source accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun musicOffsetAndTrimDoNotExtendTheSequenceAndFadesUseActualEnd() {
        val music = MusicTrack("content://audio/1", "Music", 60_000, 10_000, 40_000, 5000, .5f, 1000, 1000)
        music.validate()
        assertEquals(15_000L, music.activeDuration(20_000))
        assertEquals(0f, music.gain(4999, 20_000), .0001f)
        assertEquals(.25f, music.gain(5500, 20_000), .0001f)
        assertEquals(.5f, music.gain(10_000, 20_000), .0001f)
        assertEquals(.25f, music.gain(19_500, 20_000), .0001f)
        assertEquals(0f, music.gain(20_000, 20_000), .0001f)
        assertNull(music.envelope(4000))
    }
    @Test fun rejectsInvalidMusicAndRestoresCompleteProjectWithoutApproval() {
        try { MusicTrack("https://example.com/a", "", 1000).validate(); fail("Remote music accepted") } catch (_: IllegalArgumentException) {}
        try { MusicTrack("content://a", "", 1000, volume = Float.NaN).validate(); fail("NaN accepted") } catch (_: IllegalArgumentException) {}
        val directory = Files.createTempDirectory("multi-source").toFile()
        try {
            val clip = TimelineClip("extra", 100_000, 110_000, source = "content://videos/2", sourceDurationMs = 120_000,
                sourceName = "Second 🎥", subtitles = listOf(WhisperNative.Segment(100_000, 101_000, "Corrected")))
            val original = EditorProject("content://videos/1", 20_000, 0, 10_000, timelineClips = listOf(clip),
                timelineMode = true, selectedTimelineId = clip.id, currentTimelineId = clip.id, currentSegments = clip.subtitles,
                music = MusicTrack("content://audio/1", "Music", 60_000, 1000, 20_000, 2000))
            val file = File(directory, "project.properties")
            ProjectStore.write(file, original)
            assertEquals(original, ProjectStore.read(file))
        } finally { directory.deleteRecursively() }
    }
}
