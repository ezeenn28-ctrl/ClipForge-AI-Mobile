package com.clipforge.mobile

import kotlin.math.abs

/** Integer-millisecond touch math, independent of playback and screen density. */
object TimelineTouch {
    const val MIN_CUT_MS = 100L
    fun snap(requested: Long, targets: List<Long>, toleranceMs: Long): Long =
        targets.filter { abs(it - requested) <= toleranceMs.coerceAtLeast(0) }
            .minWithOrNull(compareBy<Long> { abs(it - requested) }.thenBy { it }) ?: requested

    fun seek(clips: List<TimelineClip>, requested: Long, snapping: Boolean, toleranceMs: Long): Long {
        val total = TimelineModel.duration(clips)
        val boundaries = clips.indices.map { TimelineModel.offset(clips, it) } + total
        return (if (snapping) snap(requested, boundaries, toleranceMs) else requested).coerceIn(0, total)
    }

    fun trim(clip: TimelineClip, startEdge: Boolean, requested: Long, sourceDurationMs: Long,
             targets: List<Long>, snapping: Boolean, toleranceMs: Long): TimelineClip {
        val lower = if (startEdge) 0L else clip.startMs + MIN_CUT_MS
        val upper = if (startEdge) clip.endMs - MIN_CUT_MS else sourceDurationMs
        require(lower <= upper) { "Clip is too short to trim with handles" }
        val bounded = requested.coerceIn(lower, upper)
        val value = if (snapping) snap(bounded, targets.filter { it in lower..upper }, toleranceMs) else bounded
        val a = if (startEdge) value else clip.startMs
        val b = if (startEdge) clip.endMs else value
        return clip.trimmed(a, b)
    }
}

