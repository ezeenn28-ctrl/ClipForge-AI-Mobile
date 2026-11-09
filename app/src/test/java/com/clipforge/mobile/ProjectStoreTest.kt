package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectStoreTest {
    private fun project() = EditorProject("content://videos/1", 120_000, 10_000, 30_000,
        mode = "AI Draft", drafts = listOf(HighlightDraft(10_000, 30_000, approved = true, hook = "Kumusta 👋\n世界")),
        selectedDraft = 0, effect = "Monochrome", subtitleYellow = true, subtitleDraftIndex = 0,
        currentSegments = listOf(WhisperNative.Segment(10_000, 12_000, "Hello\tworld\n世界")),
        draftSegments = mapOf(0 to listOf(WhisperNative.Segment(10_000, 12_000, "Corrected text"))))

    @Test fun restoresUnicodeEditsAndTimingButNeverApproval() {
        val directory = Files.createTempDirectory("clipforge-project").toFile()
        try {
            val file = File(directory, "project.properties")
            val original = project()
            ProjectStore.write(file, original)
            assertEquals(original.reviewedAgain(), ProjectStore.read(file))
            assertFalse(ProjectStore.read(file)!!.drafts.single().approved)
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectedSavePreservesLastValidSnapshot() {
        val directory = Files.createTempDirectory("clipforge-project").toFile()
        try {
            val file = File(directory, "project.properties")
            ProjectStore.write(file, project())
            try { ProjectStore.write(file, project().copy(endMs = 5_000)); fail("Invalid trim accepted") }
            catch (_: IllegalArgumentException) {}
            assertEquals(project().reviewedAgain(), ProjectStore.read(file))
        } finally { directory.deleteRecursively() }
    }

    @Test fun partialWriteIsIgnoredAndCorruptPrimaryIsReported() {
        val directory = Files.createTempDirectory("clipforge-project").toFile()
        try {
            val file = File(directory, "project.properties")
            assertNull(ProjectStore.read(file))
            ProjectStore.write(file, project())
            File(directory, "project.properties.partial").writeText("incomplete")
            assertEquals(project().reviewedAgain(), ProjectStore.read(file))
            file.writeText("format=unknown")
            try { ProjectStore.read(file); fail("Corrupt project silently loaded") }
            catch (_: IllegalArgumentException) {}
        } finally { directory.deleteRecursively() }
    }

    @Test fun restoresCropAndReadsProjectsWithoutNewFramingField() {
        val directory = Files.createTempDirectory("clipforge-framing").toFile()
        try {
            val file = File(directory, "project.properties")
            ProjectStore.write(file, project().copy(fillFrame = true))
            assertTrue(ProjectStore.read(file)!!.fillFrame)
            file.writeText(file.readText().lineSequence().filterNot { it.startsWith("fillFrame=") }.joinToString("\n"))
            assertFalse(ProjectStore.read(file)!!.fillFrame)
        } finally { directory.deleteRecursively() }
    }

    @Test fun restoresTimelineAudioAndCorrectedSubtitlesWithoutApproval() {
        val directory = Files.createTempDirectory("clipforge-timeline").toFile()
        try {
            val file = File(directory, "project.properties")
            val clip = TimelineClip("cut-1", 10_000, 30_000, volume = .5f, fadeInMs = 1000,
                subtitles = listOf(WhisperNative.Segment(10_000, 12_000, "Corrected cue")))
            val original = project().copy(timelineClips = listOf(clip), timelineMode = true, selectedTimelineId = clip.id,
                lastSpeechJobId = "speech-1", lastSpeechUnits = 1)
            ProjectStore.write(file, original)
            assertEquals(original.reviewedAgain(), ProjectStore.read(file))
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectsRemoteSourcesAndInvalidSubtitleAssociation() {
        try { project().copy(source = "https://example.com/media.mp4").validate(); fail("Remote source accepted") }
        catch (_: IllegalArgumentException) {}
        try { project().copy(subtitleDraftIndex = 15).validate(); fail("Invalid association accepted") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun savesHighlightStyleAndClearsApprovalOnRestore() {
        val directory = Files.createTempDirectory("clipforge-highlight").toFile()
        try {
            val file = File(directory, "project.properties")
            ProjectStore.write(file, project().copy(captionPreset = "HIGHLIGHT"))
            val restored = ProjectStore.read(file)!!
            assertEquals("HIGHLIGHT", restored.captionPreset)
            assertFalse(restored.drafts.single().approved)
        } finally { directory.deleteRecursively() }
    }
}
