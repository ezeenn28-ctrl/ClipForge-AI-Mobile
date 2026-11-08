package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class SubtitleTimelineTest {
    private val segments = listOf(
        WhisperNative.Segment(10_000, 12_000, "hello"),
        WhisperNative.Segment(12_500, 14_000, "world")
    )

    @Test fun boundaryIsExclusiveAndGapIsBlank() {
        assertEquals(-1, SubtitleTimeline.activeSegmentIndex(segments, 9_999))
        assertEquals(0, SubtitleTimeline.activeSegmentIndex(segments, 10_000))
        assertEquals(0, SubtitleTimeline.activeSegmentIndex(segments, 11_999))
        assertEquals(-1, SubtitleTimeline.activeSegmentIndex(segments, 12_000))
        assertEquals(-1, SubtitleTimeline.activeSegmentIndex(segments, 12_499))
        assertEquals(1, SubtitleTimeline.activeSegmentIndex(segments, 12_500))
        assertEquals(-1, SubtitleTimeline.activeSegmentIndex(segments, 14_000))
    }

    @Test fun trimmedExportMapsBackToSourceTime() {
        assertEquals(12_500L, SubtitleTimeline.sourcePositionMs(10_000L, 2_500_000L))
        assertEquals(1, SubtitleTimeline.activeSegmentIndex(segments,
            SubtitleTimeline.sourcePositionMs(10_000L, 2_500_000L)))
    }

    @Test fun eachExportItemCanNormalizeItsOwnPresentationOrigin() {
        val first = ClipPresentationClock()
        val second = ClipPresentationClock()
        assertEquals(0L, first.relativeUs(-1))
        assertEquals(0L, first.relativeUs(0))
        assertEquals(1_000_000L, first.relativeUs(1_000_000))
        assertEquals(0L, second.relativeUs(20_000_000))
        assertEquals(62_000L, SubtitleTimeline.sourcePositionMs(60_000, second.relativeUs(22_000_000)))
    }

    @Test fun emptyAndInvalidSegmentsAreIgnored() {
        assertEquals(-1, SubtitleTimeline.activeSegmentIndex(emptyList(), 10_000))
        assertEquals(-1, SubtitleTimeline.activeSegmentIndex(
            listOf(WhisperNative.Segment(10_000, 10_000, "invalid")), 10_000))
    }
}
