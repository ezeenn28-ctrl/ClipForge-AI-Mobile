package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class MetadataBackupTest {
    @Test fun backupsRestoreEditsWithoutPermissionsApprovalOrAutomaticJobs() {
        val root = Files.createTempDirectory("backup-source").toFile()
        val destination = Files.createTempDirectory("backup-destination").toFile()
        try {
            ProjectStore.write(File(root, "projects/last-project.properties"), EditorProject("content://videos/1", 30_000, 0, 20_000,
                drafts = listOf(HighlightDraft(0, 20_000, approved = true))))
            SpeechJobStore.write(File(root, "speech/last-job.properties"), SpeechJob("job-1", "content://videos/1", 30_000, SpeechJobMode.FIRST_30, listOf(SpeechRange(0, 0, 30_000)), status = SpeechJobStatus.RUNNING))
            File(root, "models").mkdirs(); File(root, "models/private.bin").writeText("not part of backup")
            val bytes = ByteArrayOutputStream().also { MetadataBackup.create(root, it) }.toByteArray()
            val staged = MetadataBackup.stage(destination, ByteArrayInputStream(bytes))
            assertTrue(staged.hasProject); assertTrue(staged.hasSpeech)
            MetadataBackup.restore(destination, staged)
            assertFalse(ProjectStore.read(File(destination, "projects/last-project.properties"))!!.drafts.single().approved)
            assertEquals(SpeechJobStatus.PAUSED, SpeechJobStore.read(File(destination, "speech/last-job.properties"))!!.status)
            assertFalse(File(destination, "models/private.bin").exists())
        } finally { root.deleteRecursively(); destination.deleteRecursively() }
    }
    @Test fun rejectsZipTraversalBeforeChangingExistingFiles() {
        val root = Files.createTempDirectory("backup-traversal").toFile()
        try {
            val existing = File(root, "projects/last-project.properties").apply { parentFile.mkdirs(); writeText("keep") }
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip -> zip.putNextEntry(ZipEntry("../escape")); zip.write("bad".toByteArray()); zip.closeEntry() }
            try { MetadataBackup.stage(root, ByteArrayInputStream(bytes.toByteArray())); fail("Traversal accepted") }
            catch (_: IllegalArgumentException) {}
            assertEquals("keep", existing.readText())
        } finally { root.deleteRecursively() }
    }
}
