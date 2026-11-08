package com.clipforge.mobile

/** Maps decoded audio timestamps into an exact bounded source-time window. */
object AudioWindow {
    data class Slice(val firstFrame: Int, val endFrame: Int, val outputFrame: Long)
    fun slice(ptsUs: Long, frames: Int, sampleRate: Int, startUs: Long, endUs: Long): Slice {
        require(sampleRate > 0 && frames >= 0 && startUs >= 0 && endUs > startUs)
        fun ceilFrames(us: Long): Long = kotlin.math.ceil(us.toDouble() * sampleRate / 1_000_000).toLong()
        val first = ceilFrames(startUs - ptsUs).coerceIn(0, frames.toLong()).toInt()
        val end = ceilFrames(endUs - ptsUs).coerceIn(first.toLong(), frames.toLong()).toInt()
        val output = ((ptsUs - startUs).toDouble() * sampleRate / 1_000_000 + first).toLong().coerceAtLeast(0)
        return Slice(first, end, output)
    }
}
