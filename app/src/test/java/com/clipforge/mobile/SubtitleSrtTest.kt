package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class SubtitleSrtTest {
    @Test fun rebasesAndClipsCuesToApprovedTrim() {
        val srt = SubtitleSrt.render(listOf(
            WhisperNative.Segment(9_000, 11_000, "Hello"),
            WhisperNative.Segment(12_000, 15_000, "Kumusta"),
            WhisperNative.Segment(20_000, 21_000, "outside")
        ), 10_000, 14_000)
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nHello\n\n2\n00:00:02,000 --> 00:00:04,000\nKumusta\n", srt)
    }
    @Test fun emptyAndInvalidCuesProduceNoRecords() {
        assertEquals("", SubtitleSrt.render(listOf(WhisperNative.Segment(0, 0, "bad")), 0, 10_000))
    }
}
