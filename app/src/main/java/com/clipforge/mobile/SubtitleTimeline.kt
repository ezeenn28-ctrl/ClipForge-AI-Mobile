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

/** Normalize each export item's first frame to zero, independent of sequence offsets. */
class ClipPresentationClock {
    private var originUs: Long? = null
    fun relativeUs(presentationTimeUs: Long): Long {
        if (presentationTimeUs < 0) return 0
        if (originUs == null) originUs = presentationTimeUs
        return (presentationTimeUs - originUs!!).coerceAtLeast(0)
    }
}
