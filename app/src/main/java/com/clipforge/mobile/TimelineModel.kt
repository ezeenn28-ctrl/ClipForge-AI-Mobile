package com.clipforge.mobile

import java.util.UUID
import kotlin.math.roundToLong

/** Non-destructive cuts from the current source. All subtitle times remain in source time. */
data class TimelineClip(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val hook: String = "",
    val caption: String = "",
    val captionStartMs: Long = 0,
    val captionEndMs: Long = 0,
    val effect: String = "None",
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val subtitles: List<WhisperNative.Segment> = emptyList(),
    val source: String = "",
    val sourceDurationMs: Long = 0,
    val sourceName: String = "",
    val visuals: ClipVisuals = ClipVisuals(),
    val speed: Float = 1f,
    val color: ColorAdjustments = ColorAdjustments(),
    val transition: CutTransition = CutTransition(),
    val sounds: List<ClipSound> = emptyList()
) {
    val sourceSpanMs: Long get() = endMs - startMs
    val durationMs: Long get() = (sourceSpanMs / speed.toDouble()).roundToLong().coerceAtLeast(1)
    fun outputOffsetMs(sourceOffsetMs: Long): Long = (sourceOffsetMs / speed.toDouble()).roundToLong().coerceIn(0, durationMs)
    fun sourceOffsetMs(outputOffsetMs: Long): Long = (outputOffsetMs * speed.toDouble()).roundToLong().coerceIn(0, sourceSpanMs)
    fun withSpeed(value: Float): TimelineClip {
        require(value.isFinite() && value in .5f..2f) { "Speed must be between 0.5x and 2x" }
        val changed = copy(speed = value)
        return changed.copy(fadeInMs = minOf(fadeInMs, changed.durationMs), fadeOutMs = minOf(fadeOutMs, changed.durationMs))
    }
    fun trimmed(from: Long, until: Long): TimelineClip {
        val changed = copy(startMs = from, endMs = until)
        return changed.copy(fadeInMs = minOf(fadeInMs, changed.durationMs), fadeOutMs = minOf(fadeOutMs, changed.durationMs))
    }
    fun resolvedSource(primary: String): String = source.ifBlank { primary }
    fun resolvedDuration(primaryDurationMs: Long): Long = if (source.isBlank()) primaryDurationMs else sourceDurationMs
    fun validate(primaryDurationMs: Long) {
        val sourceDurationMs = resolvedDuration(primaryDurationMs)
        require(source.isEmpty() || source.startsWith("content://") || source.startsWith("file://")) { "Timeline source must be local" }
        require(sourceDurationMs in 1..86_400_000) { "Invalid timeline source duration" }
        require(sounds.size <= 12 && sounds.map { it.id }.distinct().size == sounds.size) { "Up to 12 unique effects per clip" }
        sounds.forEach { it.validate(sourceDurationMs) }
        visuals.validate()
        require(speed.isFinite() && speed in .5f..2f) { "Invalid clip speed" }
        color.validate()
        transition.validate()
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid timeline clip ID" }
        require(startMs >= 0 && endMs > startMs && endMs <= sourceDurationMs) { "Invalid timeline trim" }
        require(effect in VideoLooks.names) { "Unknown timeline effect" }
        require(volume.isFinite() && volume in 0f..1f && fadeInMs in 0..durationMs && fadeOutMs in 0..durationMs) { "Invalid audio settings" }
        require(subtitles.size <= 20_000) { "Too many subtitle cues" }
        subtitles.forEach { require(it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= sourceDurationMs) { "Invalid timeline subtitle" } }
    }
}
object TimelineModel {
    data class Position(val index: Int, val localMs: Long, val sourceMs: Long)
    fun duration(clips: List<TimelineClip>): Long = clips.sumOf { it.durationMs }
    fun validate(clips: List<TimelineClip>, sourceDurationMs: Long) {
        require(clips.size <= 60 && clips.map { it.id }.distinct().size == clips.size) { "Timeline allows up to 60 unique cuts" }
        clips.forEach { it.validate(sourceDurationMs) }
        require(clips.sumOf { it.sounds.size } <= 120) { "Up to 120 sound effects per project" }
        require(duration(clips) <= 21_600_000) { "Timeline exceeds six hours" }
        require(clips.sumOf { it.subtitles.size } <= 50_000) { "Timeline subtitles exceed project limit" }
    }
    fun offset(clips: List<TimelineClip>, index: Int): Long = clips.take(index).sumOf { it.durationMs }
    /** A player tick can outlive the sequence that scheduled it during source replacement. */
    fun playerPosition(clips: List<TimelineClip>, mediaItemIndex: Int, sourceLocalMs: Long): Position? {
        if (clips.isEmpty()) return null
        val index = mediaItemIndex.coerceIn(0, clips.lastIndex)
        val clip = clips[index]
        val sourceLocal = sourceLocalMs.coerceIn(0, clip.sourceSpanMs)
        return Position(index, clip.outputOffsetMs(sourceLocal), clip.startMs + sourceLocal)
    }
    fun position(clips: List<TimelineClip>, compositionMs: Long): Position? {
        if (clips.isEmpty()) return null
        var cursor = compositionMs.coerceIn(0, duration(clips))
        clips.forEachIndexed { index, clip ->
            if (cursor < clip.durationMs || index == clips.lastIndex) return Position(index, cursor.coerceAtMost(clip.durationMs), clip.startMs + clip.sourceOffsetMs(cursor.coerceAtMost(clip.durationMs)))
            cursor -= clip.durationMs
        }
        return null
    }
    fun split(clips: List<TimelineClip>, compositionMs: Long, newId: String = UUID.randomUUID().toString()): List<TimelineClip> {
        val position = position(clips, compositionMs) ?: error("Add a clip before splitting")
        val clip = clips[position.index]
        require(position.localMs >= 100 && clip.durationMs - position.localMs >= 100) { "Place the playhead at least 0.1s inside a clip" }
        val visualSplit = clip.visuals.split(position.localMs, clip.durationMs)
        val left = clip.copy(endMs = position.sourceMs, sounds = clip.sounds.filter { it.sourceMs < position.sourceMs }, visuals = visualSplit.first, transition = CutTransition(), fadeInMs = minOf(clip.fadeInMs, clip.copy(endMs = position.sourceMs).durationMs), fadeOutMs = 0)
        val right = clip.copy(id = newId, sounds = clip.sounds.filter { it.sourceMs >= position.sourceMs }, startMs = position.sourceMs, visuals = visualSplit.second, fadeInMs = 0, fadeOutMs = minOf(clip.fadeOutMs, clip.copy(startMs = position.sourceMs).durationMs))
        return clips.take(position.index) + listOf(left, right) + clips.drop(position.index + 1)
    }
    fun move(clips: List<TimelineClip>, index: Int, delta: Int): List<TimelineClip> {
        val destination = index + delta
        if (index !in clips.indices || destination !in clips.indices) return clips
        return clips.toMutableList().also { val item = it.removeAt(index); it.add(destination, item) }
    }
    fun subtitles(clips: List<TimelineClip>): List<WhisperNative.Segment> = clips.flatMapIndexed { index, clip ->
        val offset = offset(clips, index)
        val cues = if (clip.subtitles.isNotEmpty()) clip.subtitles else if (clip.caption.isNotBlank()) listOf(
            WhisperNative.Segment(if (clip.captionEndMs > clip.captionStartMs) clip.captionStartMs else clip.startMs,
                if (clip.captionEndMs > clip.captionStartMs) clip.captionEndMs else clip.endMs, clip.caption)
        ) else emptyList()
        cues.mapNotNull { cue ->
            val start = maxOf(cue.startMs, clip.startMs)
            val end = minOf(cue.endMs, clip.endMs)
            val outputStart = clip.outputOffsetMs(start - clip.startMs)
            val outputEnd = clip.outputOffsetMs(end - clip.startMs)
            if (end <= start || outputEnd <= outputStart) null else cue.copy(startMs = offset + outputStart, endMs = offset + outputEnd)
        }
    }
    fun audioGain(clip: TimelineClip, localMs: Long): Float {
        val fadeIn = if (clip.fadeInMs > 0) (localMs.toDouble() / clip.fadeInMs).coerceIn(0.0, 1.0) else 1.0
        val fadeOut = if (clip.fadeOutMs > 0) ((clip.durationMs - localMs).toDouble() / clip.fadeOutMs).coerceIn(0.0, 1.0) else 1.0
        return (clip.volume * minOf(fadeIn, fadeOut)).toFloat()
    }
}

data class TimelineHistory(
    val clips: List<TimelineClip> = emptyList(),
    val past: List<List<TimelineClip>> = emptyList(),
    val future: List<List<TimelineClip>> = emptyList()
) {
    fun edit(next: List<TimelineClip>) = if (next == clips) this else TimelineHistory(next, (past + listOf(clips)).takeLast(30), emptyList())
    fun undo() = if (past.isEmpty()) this else TimelineHistory(past.last(), past.dropLast(1), listOf(clips) + future)
    fun redo() = if (future.isEmpty()) this else TimelineHistory(future.first(), (past + listOf(clips)).takeLast(30), future.drop(1))
}



