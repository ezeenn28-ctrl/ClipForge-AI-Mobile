package com.clipforge.mobile

/** Estimated word boundaries inside a recognizer cue; manual corrections remain authoritative. */
object CaptionTiming {
    fun words(cue: WhisperNative.Segment): List<WhisperNative.Segment> {
        val words = cue.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty() || cue.endMs <= cue.startMs || cue.endMs - cue.startMs < words.size) return emptyList()
        val weights = words.map { it.count(Char::isLetterOrDigit).coerceAtLeast(1).toLong() }
        val total = weights.sum()
        val span = cue.endMs - cue.startMs - words.size
        var cumulative = 0L
        return words.mapIndexed { i, text ->
            val start = cue.startMs + i + span * cumulative / total
            cumulative += weights[i]
            val end = cue.startMs + i + 1 + span * cumulative / total
            WhisperNative.Segment(start, end, text)
        }
    }
    fun activeWord(cue: WhisperNative.Segment, sourceMs: Long): Int =
        words(cue).indexOfFirst { sourceMs >= it.startMs && sourceMs < it.endMs }

    /** Convert a source timestamp to the selected cut's output clock, never its neighbor. */
    fun timelineSeek(clips: List<TimelineClip>, targetId: String, sourceMs: Long): Long? {
        val index = clips.indexOfFirst { it.id == targetId }
        if (index < 0) return null
        val clip = clips[index]
        val source = sourceMs.coerceIn(clip.startMs, clip.endMs - 1)
        val local = clip.outputOffsetMs(source - clip.startMs).coerceIn(0, clip.durationMs - 1)
        return TimelineModel.offset(clips, index) + local
    }

    fun replace(cues: List<WhisperNative.Segment>, index: Int, changed: WhisperNative.Segment, durationMs: Long): List<WhisperNative.Segment> {
        require(index in cues.indices && changed.text.length <= 2000) { "Invalid caption" }
        require(Timecode.validTrim(changed.startMs, changed.endMs, durationMs)) { "Enter valid caption times" }
        require(index == 0 || changed.startMs >= cues[index - 1].endMs) { "Caption overlaps the previous line" }
        require(index == cues.lastIndex || changed.endMs <= cues[index + 1].startMs) { "Caption overlaps the next line" }
        return cues.mapIndexed { i, cue -> if (i == index) changed else cue }
    }

    /** Explicit whole-word correction; preserves Taglish spelling and never translates. */
    fun correct(cues: List<WhisperNative.Segment>, find: String, replacement: String): List<WhisperNative.Segment> {
        require(find.isNotBlank() && find.length <= 80 && replacement.length <= 80) { "Enter a short correction" }
        val pattern = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(find.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return cues.map { it.copy(text = pattern.replace(it.text) { replacement }) }
    }
}
