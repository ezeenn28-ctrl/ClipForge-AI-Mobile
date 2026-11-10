package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LongVideoRecoveryTest {
    @Test fun twoHourScheduleSurvivesProcessLossAndBackupWithoutRepeatedWindows() {
        val root = Files.createTempDirectory("long-recovery").toFile()
        val restored = Files.createTempDirectory("long-restored").toFile()
        try {
            val file = File(root, "speech/last-job.properties")
            val request = SpeechJob("two-hours", "content://test/long", 7_200_000, SpeechJobMode.FULL_VIDEO,
                listOf(SpeechRange(0, 0, 7_200_000)))
            assertEquals(240, request.units().size)
            var calls = 0
            val paused = SpeechJobRunner.run(request, { calls == 17 }, { unit ->
                calls++; listOf(WhisperNative.Segment(unit.startMs, unit.endMs, "window $calls"))
            }, { SpeechJobStore.write(file, it) })
            assertEquals(SpeechJobStatus.PAUSED, paused.status)
            // Persist RUNNING to simulate sudden process loss before a pause checkpoint.
            SpeechJobStore.write(file, paused.copy(status = SpeechJobStatus.RUNNING))
            val bytes = java.io.ByteArrayOutputStream().also { MetadataBackup.create(root, it) }.toByteArray()
            MetadataBackup.restore(restored, MetadataBackup.stage(restored, bytes.inputStream()))
            val recovered = SpeechJobStore.read(File(restored, "speech/last-job.properties"))!!
            assertEquals(SpeechJobStatus.PAUSED, recovered.status)
            assertFalse(recovered.active)
            val starts = mutableListOf<Long>()
            val complete = SpeechJobRunner.run(recovered, { false }, { unit ->
                starts.add(unit.startMs); listOf(WhisperNative.Segment(unit.startMs, unit.endMs, "resumed"))
            }, { SpeechJobStore.write(File(restored, "speech/last-job.properties"), it) })
            assertEquals(223, starts.size); assertEquals(510_000L, starts.first()); assertEquals(7_170_000L, starts.last())
            assertEquals(240, complete.results.size); assertEquals(SpeechJobStatus.COMPLETED, complete.status)
            val cues = complete.groupedResults().getValue(0)
            assertEquals(240, cues.size); assertEquals(7_200_000L, cues.last().endMs)
            assertEquals(240, cues.map { it.startMs }.distinct().size)
            assertEquals(complete, SpeechJobStore.read(File(restored, "speech/last-job.properties")))
        } finally { root.deleteRecursively(); restored.deleteRecursively() }
    }
}
