package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpeechJobTest {
    private fun request() = SpeechJob("job-1", "content://videos/1", 120_000, SpeechJobMode.FULL_VIDEO, listOf(SpeechRange(0, 0, 120_000)))
    @Test fun pauseAndResumeSkipsDurableWindowsWithoutDuplicatingSpeech() {
        var completed = 0
        val paused = SpeechJobRunner.run(request(), { completed == 2 }, { unit ->
            completed++; listOf(WhisperNative.Segment(unit.startMs, unit.endMs, "window $completed"))
        }, {})
        assertEquals(SpeechJobStatus.PAUSED, paused.status)
        assertEquals(2, paused.results.size)
        val resumedStarts = mutableListOf<Long>()
        val resumed = SpeechJobRunner.run(paused, { false }, { unit ->
            resumedStarts.add(unit.startMs); listOf(WhisperNative.Segment(unit.startMs, unit.endMs, "resumed"))
        }, {})
        assertEquals(listOf(60_000L, 90_000L), resumedStarts)
        assertEquals(4, resumed.groupedResults().getValue(0).size)
        assertEquals(SpeechJobStatus.COMPLETED, resumed.status)
    }
    @Test fun inferenceFailurePreservesCompletedWorkAndCanBeRetried() {
        val failed = SpeechJobRunner.run(request(), { false }, { unit ->
            if (unit.startMs == 30_000L) error("decoder failed")
            listOf(WhisperNative.Segment(unit.startMs, unit.endMs, "saved"))
        }, {})
        assertEquals(SpeechJobStatus.FAILED, failed.status)
        assertEquals(1, failed.results.size)
        assertEquals(SpeechJobStatus.COMPLETED, SpeechJobRunner.run(failed, { false }, { emptyList() }, {}).status)
    }
    @Test fun silentWindowsAreCheckpointedAndProcessRecoveryNeverAutoStarts() {
        val directory = Files.createTempDirectory("speech-job").toFile()
        try {
            val file = File(directory, "job.properties")
            val saved = request().copy(status = SpeechJobStatus.RUNNING, results = mapOf("0:0:30000" to emptyList()))
            SpeechJobStore.write(file, saved)
            val restored = SpeechJobStore.read(file)!!.recovered()
            assertEquals(SpeechJobStatus.PAUSED, restored.status)
            assertTrue("0:0:30000" in restored.results)
            assertFalse(restored.active)
        } finally { directory.deleteRecursively() }
    }
    @Test fun rejectsResultsOutsideTheirSourceWindow() {
        try {
            request().copy(results = mapOf("0:0:30000" to listOf(WhisperNative.Segment(0, 40_000, "bad")))).validate()
            fail("Out-of-window caption accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
