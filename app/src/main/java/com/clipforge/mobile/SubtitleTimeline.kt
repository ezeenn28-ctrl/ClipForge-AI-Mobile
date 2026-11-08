package com.clipforge.mobile

/** Pure timestamp conversion shared by preview and exported bitmap overlays. */
object SubtitleTimeline {
    fun activeSegmentIndex(
        segments: List<WhisperNative.Segment>,
        sourcePositionMs: Long
    ): Int = segments.indexOfFirst { segment ->
        segment.endMs > segment.startMs &&
            sourcePositionMs >= segment.startMs &&
            sourcePositionMs < segment.endMs
    }

    fun sourcePositionMs(trimStartMs: Long, presentationTimeUs: Long): Long =
        trimStartMs + (presentationTimeUs.coerceAtLeast(0L) / 1_000L)
}
