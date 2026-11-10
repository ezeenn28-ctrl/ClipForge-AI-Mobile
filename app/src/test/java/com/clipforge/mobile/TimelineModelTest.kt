package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class TimelineModelTest {
    private val a = TimelineClip("a", 10_000, 30_000, subtitles = listOf(WhisperNative.Segment(15_000, 25_000, "hello")))
    private val b = TimelineClip("b", 60_000, 80_000, subtitles = listOf(WhisperNative.Segment(60_000, 65_000, "next")))
    @Test fun splitAndReorderPreserveSourceTimeAndRebaseSubtitleTime() {
        val split = TimelineModel.split(listOf(a, b), 10_000, "right")
        assertEquals(listOf(10_000L, 20_000L, 60_000L), split.map { it.startMs })
        assertEquals(listOf(20_000L, 30_000L, 80_000L), split.map { it.endMs })
        val reordered = TimelineModel.move(split, 2, -1)
        val cues = TimelineModel.subtitles(reordered)
        assertEquals(listOf(5_000L, 10_000L, 30_000L), cues.map { it.startMs })
        assertEquals(listOf(10_000L, 15_000L, 35_000L), cues.map { it.endMs })
    }
    @Test fun playheadBoundarySelectsNextClipAndMapsToOriginalSource() {
        assertEquals(TimelineModel.Position(1, 0, 60_000), TimelineModel.position(listOf(a, b), 20_000))
        assertEquals(TimelineModel.Position(1, 20_000, 80_000), TimelineModel.position(listOf(a, b), 40_000))
        try { TimelineModel.split(listOf(a, b), 20_000); fail("Boundary split accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun undoRedoRestoresNonDestructiveCutsAndClearsRedoAfterNewEdit() {
        val initial = TimelineHistory(listOf(a))
        val edited = initial.edit(TimelineModel.split(initial.clips, 10_000, "split"))
        assertEquals(initial.clips, edited.undo().clips)
        assertEquals(edited.clips, edited.undo().redo().clips)
        assertTrue(edited.undo().edit(listOf(b)).future.isEmpty())
    }
    @Test fun fadesAreBoundedAndRemainAtOriginalEndsAfterSplitting() {
        val clip = a.copy(fadeInMs = 1000, fadeOutMs = 1000, volume = .5f)
        assertEquals(0f, TimelineModel.audioGain(clip, 0), .0001f)
        assertEquals(.25f, TimelineModel.audioGain(clip, 500), .0001f)
        assertEquals(.5f, TimelineModel.audioGain(clip, 5000), .0001f)
        assertEquals(0f, TimelineModel.audioGain(clip, 20_000), .0001f)
        val pieces = TimelineModel.split(listOf(clip), 10_000, "right")
        assertEquals(0L, pieces[0].fadeOutMs); assertEquals(0L, pieces[1].fadeInMs)
    }

    @Test fun pendingPlayerTickSurvivesEmptyAndReplacementSequence() {
        assertEquals(TimelineModel.Position(1, 1000, 61000),
            TimelineModel.playerPosition(listOf(a, b), 1, 1000))
        assertNull(TimelineModel.playerPosition(emptyList(), 1, 1000))
        val replacement = TimelineClip("replacement", 0, 8000)
        assertEquals(TimelineModel.Position(0, 1000, 1000),
            TimelineModel.playerPosition(listOf(replacement), 1, 1000))
    }

    @Test fun playerClockBoundsStaleIndicesAndMapsSourceTimeThroughSpeed() {
        val slow = a.withSpeed(.5f)
        assertEquals(TimelineModel.Position(0, 2000, 11000),
            TimelineModel.playerPosition(listOf(slow, b), -1, 1000))
        assertEquals(TimelineModel.Position(0, 0, 10000),
            TimelineModel.playerPosition(listOf(slow), 0, -100))
        assertEquals(TimelineModel.Position(1, 20000, 80000),
            TimelineModel.playerPosition(listOf(slow, b), Int.MAX_VALUE, Long.MAX_VALUE))
    }
}
