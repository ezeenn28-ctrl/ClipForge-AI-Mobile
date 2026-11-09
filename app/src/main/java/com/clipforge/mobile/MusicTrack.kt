package com.clipforge.mobile

/** A single local music bed. No looping or uploads; silence outside its placed range. */
data class MusicTrack(
    val source: String, val name: String, val durationMs: Long,
    val startMs: Long = 0, val endMs: Long = durationMs, val timelineStartMs: Long = 0,
    val volume: Float = .25f, val fadeInMs: Long = 500, val fadeOutMs: Long = 500
) {
    fun validate() {
        require(source.startsWith("content://") || source.startsWith("file://")) { "Music must be a local file" }
        require(durationMs in 1..86_400_000 && Timecode.validTrim(startMs, endMs, durationMs)) { "Invalid music trim" }
        require(timelineStartMs in 0..21_600_000 && volume.isFinite() && volume in 0f..1f) { "Invalid music placement or volume" }
        require(fadeInMs in 0..10_000 && fadeOutMs in 0..10_000) { "Invalid music fades" }
    }
    fun activeDuration(sequenceMs: Long): Long = minOf(endMs - startMs, (sequenceMs - timelineStartMs).coerceAtLeast(0))
    fun envelope(sequenceMs: Long): TimelineClip? = activeDuration(sequenceMs).takeIf { it > 0 }?.let {
        TimelineClip("music", 0, it, volume = volume, fadeInMs = minOf(fadeInMs, it), fadeOutMs = minOf(fadeOutMs, it))
    }
    fun gain(sequencePositionMs: Long, sequenceMs: Long): Float {
        val envelope = envelope(sequenceMs) ?: return 0f
        val local = sequencePositionMs - timelineStartMs
        return if (local < 0 || local >= envelope.durationMs) 0f else TimelineModel.audioGain(envelope, local)
    }
}
