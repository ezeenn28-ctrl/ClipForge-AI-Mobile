package com.clipforge.mobile

/** Explainable local speech ranking; does not predict views or use a cloud service. */
object TranscriptHighlights {
    fun detect(segments: List<WhisperNative.Segment>, durationMs: Long): List<HighlightDraft> {
        val speech = segments.filter { it.startMs >= 0 && it.endMs > it.startMs && it.text.isNotBlank() }
            .sortedBy { it.startMs }
        val candidates = speech.mapNotNull { first ->
            val end = minOf(durationMs, first.startMs + 30_000)
            if (end - first.startMs < 15_000) return@mapNotNull null
            val group = speech.filter { it.startMs >= first.startMs && it.endMs <= end }
            if (group.isEmpty()) return@mapNotNull null
            val text = group.joinToString(" ") { it.text }.trim()
            val words = text.split(Regex("\\s+"))
            val hook = Regex("(?i)\\b(why|how|never|secret|wait|but|because|bakit|paano|pero|grabe)\\b").containsMatchIn(first.text)
            val coverage = group.sumOf { it.endMs - it.startMs }.coerceAtMost(30_000).toDouble() / 30_000
            val score = (coverage * 40 + minOf(words.distinct().size, 40) + if (hook) 20 else 0).toInt()
            score to HighlightDraft(first.startMs, end, caption = text)
        }.sortedByDescending { it.first }
        val selected = mutableListOf<HighlightDraft>()
        for ((_, draft) in candidates) {
            if (selected.none { minOf(it.endMs, draft.endMs) > maxOf(it.startMs, draft.startMs) }) {
                selected.add(draft)
                if (selected.size == 15) break
            }
        }
        return selected.sortedBy { it.startMs }
    }
}
