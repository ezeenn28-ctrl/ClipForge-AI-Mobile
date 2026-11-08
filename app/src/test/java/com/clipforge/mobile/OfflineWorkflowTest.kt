package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineWorkflowTest {
    @Test fun checkpointRoundTripsUnicodeAndSanitizesRecordDelimiters() {
        val directory = Files.createTempDirectory("clipforge").toFile()
        try {
            val file = File(directory, "transcript.tsv")
            TranscriptStore.write(file, TranscriptStore.Snapshot(30_000, listOf(
                WhisperNative.Segment(0, 20_000, "Kumusta\tka?\n世界"))))
            val result = TranscriptStore.read(file)
            assertEquals(30_000L, result.throughMs)
            assertEquals("Kumusta ka? 世界", result.segments.single().text)
            assertFalse(File(directory, "transcript.tsv.partial").exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun interruptedTemporaryWriteDoesNotAdvanceCheckpoint() {
        val directory = Files.createTempDirectory("clipforge").toFile()
        try {
            val file = File(directory, "transcript.tsv")
            TranscriptStore.write(file, TranscriptStore.Snapshot(30_000, emptyList()))
            File(directory, "transcript.tsv.partial").writeText("incomplete")
            assertEquals(30_000L, TranscriptStore.read(file).throughMs)
            file.writeText("old malformed checkpoint")
            assertEquals(0L, TranscriptStore.read(file).throughMs)
        } finally { directory.deleteRecursively() }
    }

    @Test fun campaignRequiresWholeTagsAndManualVerification() {
        val rules = CampaignRules("@hardscope #loveandjustice")
        assertTrue(rules.issues("@HARDSCOPE #LOVEANDJUSTICE", 20_000, true, true).isEmpty())
        assertTrue(rules.issues("@hardscopefake #loveandjustice", 20_000, true, true).isNotEmpty())
        assertEquals(2, rules.issues("@hardscope #loveandjustice", 20_000, false, false).size)
    }

    @Test fun campaignRejectsInvalidDurationAndInsufficientSource() {
        assertTrue(CampaignRules(minimumSourceSeconds = 6).issues("", 5_000, true, true).size >= 2)
        assertTrue(CampaignRules(minimumSeconds = 40, maximumSeconds = 15).issues("", 20_000, true, true).isNotEmpty())
    }

    @Test fun longVideoProducesBoundedUnapprovedNonOverlappingCandidates() {
        val speech = (0 until 1440).map { i ->
            WhisperNative.Segment(i * 5_000L, i * 5_000L + 4_000, "Why does this happen? Here is the answer.")
        }
        val result = TranscriptHighlights.detect(speech, 7_200_000)
        assertEquals(15, result.size)
        result.forEach {
            assertFalse(it.approved)
            assertTrue(it.endMs - it.startMs in 15_000..30_000)
            assertTrue(it.endMs <= 7_200_000)
        }
        result.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
    }

    @Test fun noSpeechOrTooShortSourceDoesNotFabricateHighlights() {
        assertTrue(TranscriptHighlights.detect(emptyList(), 7_200_000).isEmpty())
        assertTrue(TranscriptHighlights.detect(listOf(WhisperNative.Segment(0, 1_000, "Hi")), 5_000).isEmpty())
    }
}
