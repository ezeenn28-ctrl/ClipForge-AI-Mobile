package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class TimelineTouchTest {
    private val clip = TimelineClip("a", 10_000, 30_000, fadeInMs = 1000, fadeOutMs = 1000,
        subtitles = listOf(WhisperNative.Segment(12_000, 15_000, "Keep source clocks")))
    @Test fun snappingUsesNearestBoundaryWithDeterministicTieAndCanBeDisabled() {
        assertEquals(1000L, TimelineTouch.snap(1100, listOf(1200, 1000), 150))
        assertEquals(1400L, TimelineTouch.snap(1400, listOf(1200, 1000), 150))
        val clips = listOf(clip, clip.copy(id = "b"))
        assertEquals(20_000L, TimelineTouch.seek(clips, 20_080, true, 120))
        assertEquals(20_080L, TimelineTouch.seek(clips, 20_080, false, 120))
    }
    @Test fun trimCannotInvertOrEscapeSourceAndFadesAreClamped() {
        val start = TimelineTouch.trim(clip, true, 90_000, 100_000, emptyList(), false, 0)
        assertEquals(29_900L, start.startMs)
        assertEquals(100L, start.fadeInMs)
        assertEquals(100L, start.fadeOutMs)
        assertEquals(clip.subtitles, start.subtitles)
        assertEquals(100_000L, TimelineTouch.trim(clip, false, 200_000, 100_000, emptyList(), false, 0).endMs)
        assertEquals(0L, TimelineTouch.trim(clip, true, -1000, 100_000, emptyList(), false, 0).startMs)
    }
    @Test fun trimSnapsOnlyToLegalSourceTargetsAndUndoRestoresOriginal() {
        val changed = TimelineTouch.trim(clip, false, 15_070, 100_000, listOf(15_000, 10_000), true, 100)
        assertEquals(15_000L, changed.endMs)
        val history = TimelineHistory(listOf(clip)).edit(listOf(changed))
        assertEquals(listOf(clip), history.undo().clips)
        assertEquals(1, history.past.size)
        assertEquals(10_100L, TimelineTouch.trim(clip, false, 10_100, 100_000, listOf(10_000), true, 200).endMs)
    }
}
