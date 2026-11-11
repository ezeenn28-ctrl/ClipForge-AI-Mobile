package com.clipforge.mobile

/** Explainable local speech ranking; does not predict views or use a cloud service. */
object TranscriptHighlights {
    fun detect(segments: List<WhisperNative.Segment>, durationMs: Long): List<HighlightDraft> {
        val speech = segments.filter { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= durationMs && it.text.isNotBlank() }
            .sortedBy { it.startMs }
        val candidates = speech.mapIndexedNotNull { index, anchor ->
            val previous = speech.getOrNull(index - 1)
            val continuation = Regex("(?i)^(but|because|so|then|he|she|it|they|pero|kasi|siya|kaya)\\b").containsMatchIn(anchor.text.trim())
            val first = if (continuation && previous != null && anchor.startMs - previous.endMs in 0..1500 &&
                anchor.endMs - previous.startMs <= 30_000) previous else anchor
            val limit = minOf(durationMs, first.startMs + 30_000)
            val boundary = speech.lastOrNull { it.startMs >= first.startMs && it.endMs <= limit && it.endMs - first.startMs >= 15_000 && it.text.trim().lastOrNull() in listOf('.', '!', '?', '…') }
            val lastSpeech = speech.lastOrNull { it.startMs >= first.startMs && it.endMs <= limit } ?: return@mapIndexedNotNull null
            val end = minOf(limit, maxOf(first.startMs + 15_000, (boundary ?: lastSpeech).endMs + 250))
            if (end - first.startMs < 15_000) return@mapIndexedNotNull null
            val group = speech.filter { it.startMs >= first.startMs && it.endMs <= end }
            if (group.isEmpty()) return@mapIndexedNotNull null
            val text = group.joinToString(" ") { it.text }.trim()
            val words = text.split(Regex("\\s+"))
            val hook = Regex("(?i)\\b(why|how|never|secret|wait|but|because|bakit|paano|pero|grabe)\\b").containsMatchIn(first.text)
            var cursor = first.startMs
            var covered = 0L
            var largestGap = 0L
            group.forEach { cue ->
                largestGap = maxOf(largestGap, cue.startMs - cursor)
                covered += (cue.endMs - maxOf(cursor, cue.startMs)).coerceAtLeast(0)
                cursor = maxOf(cursor, cue.endMs)
            }
            largestGap = maxOf(largestGap, end - cursor)
            val coverage = (covered.toDouble() / (end - first.startMs)).coerceIn(0.0, 1.0)
            val score = (coverage * 40 + minOf(words.distinct().size, 25) + (if (hook) 15 else 0) +
                (if (boundary != null) 12 else 0) - (if (largestGap > 3000) 15 else 0)).toInt().coerceIn(0, 100)
            val reasons = buildList {
                if (first != anchor) add("Includes preceding context")
                add("Speech coverage ${(coverage * 100).toInt()}%")
                if (hook) add("Opening question or contrast")
                add(if (boundary != null) "Ends at a sentence boundary" else "Review the ending for context")
                if (largestGap > 3000) add("Pause over 3 seconds; consider trimming")
            }
            score to HighlightDraft(first.startMs, end, caption = text, rankScore = score, reasons = reasons)
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
