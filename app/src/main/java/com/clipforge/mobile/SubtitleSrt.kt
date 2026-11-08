package com.clipforge.mobile

object SubtitleSrt {
    fun render(segments: List<WhisperNative.Segment>, trimStartMs: Long, trimEndMs: Long): String {
        require(trimStartMs >= 0 && trimEndMs > trimStartMs)
        val clipped = segments.sortedBy { it.startMs }.mapNotNull {
            val start = maxOf(it.startMs, trimStartMs) - trimStartMs
            val end = minOf(it.endMs, trimEndMs) - trimStartMs
            if (end <= start || it.text.isBlank()) null
            else Triple(start, end, it.text.replace(Regex("[\r\n]+"), " "))
        }
        return clipped.mapIndexed { index, (start, end, text) ->
            "${index + 1}\n${timestamp(start)} --> ${timestamp(end)}\n$text\n"
        }.joinToString("\n")
    }

    private fun timestamp(ms: Long): String = java.lang.String.format(java.util.Locale.ROOT,
        "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
}
