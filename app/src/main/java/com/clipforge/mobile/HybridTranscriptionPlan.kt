package com.clipforge.mobile

/** A bounded, restartable schedule for offline transcription. This plans work only:
 * native Whisper inference must be connected before transcripts can be generated. */
data class TranscriptionChunk(val index: Int, val startMs: Long, val endMs: Long)

object HybridTranscriptionPlan {
    const val CHUNK_MS = 30_000L
    fun highlights(drafts: List<HighlightDraft>): List<TranscriptionChunk> =
        normalize(drafts.map { it.startMs to it.endMs })

    fun fullVideo(durationMs: Long): List<TranscriptionChunk> =
        if (durationMs <= 0) emptyList()
        else normalize(listOf(0L to durationMs))

    private fun normalize(intervals: List<Pair<Long, Long>>): List<TranscriptionChunk> {
        val sorted = intervals.filter { it.second > it.first && it.first >= 0 }
            .sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        for ((start, end) in sorted) {
            if (merged.isNotEmpty() && start <= merged.last().second) {
                val previous = merged.removeAt(merged.lastIndex)
                merged.add(previous.first to maxOf(previous.second, end))
            } else merged.add(start to end)
        }
        val chunks = mutableListOf<TranscriptionChunk>()
        for ((start, end) in merged) {
            var position = start
            while (position < end) {
                chunks.add(TranscriptionChunk(chunks.size, position, minOf(end, position + CHUNK_MS)))
                position += CHUNK_MS
            }
        }
        return chunks
    }
}
