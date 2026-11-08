package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test

class HybridTranscriptionPlanTest {
    @Test fun emptyVideoHasNoChunks() {
        assertTrue(HybridTranscriptionPlan.fullVideo(0).isEmpty())
    }
    @Test fun fullVideoCoversEntireDurationWithoutGaps() {
        val chunks = HybridTranscriptionPlan.fullVideo(125_500)
        assertEquals(5, chunks.size)
        assertEquals(0L, chunks.first().startMs)
        assertEquals(125_500L, chunks.last().endMs)
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        assertTrue(chunks.all { it.endMs > it.startMs && it.endMs - it.startMs <= 30_000L })
    }
    @Test fun overlappingHighlightsMergeIntoSingleSchedule() {
        val drafts = listOf(HighlightDraft(10_000, 30_000), HighlightDraft(20_000, 50_000))
        val chunks = HybridTranscriptionPlan.highlights(drafts)
        assertEquals(2, chunks.size)
        assertEquals(10_000L, chunks.first().startMs)
        assertEquals(50_000L, chunks.last().endMs)
    }
}
