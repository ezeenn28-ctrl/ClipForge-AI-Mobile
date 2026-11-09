package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HighlightProjectTest {
    @Test fun highlightWorkflowPreservesTheOriginalEditAndUsesThePrimaryTranscript() {
        val root = Files.createTempDirectory("highlight-project").toFile()
        try {
            val primaryCue = WhisperNative.Segment(0, 4000, "Primary speech")
            val original = EditorProject("content://video/primary", 30_000, 1000, 20_000,
                projectId = ProjectCatalog.newId(), projectName = "My finished edit",
                drafts = listOf(HighlightDraft(1000, 20_000, approved = true)),
                selectedDraft = 0, hook = "Saved hook", currentSegments = listOf(WhisperNative.Segment(40_000, 42_000, "Other video")),
                sourceSegments = listOf(primaryCue), currentTimelineId = "other",
                timelineClips = listOf(TimelineClip("other", 40_000, 45_000, source = "content://video/other", sourceDurationMs = 60_000)),
                selectedTimelineId = "other", timelineMode = true,
                music = MusicTrack("content://audio/music", "Music", 30_000),
                captionAppearance = CaptionAppearance(font = CaptionFont.SERIF))
            ProjectCatalog.save(root, original)
            val next = original.forHighlights(ProjectCatalog.newId(), "New highlights")
            ProjectCatalog.save(root, next)
            assertEquals(original.reviewedAgain(), ProjectCatalog.read(root, original.projectId))
            assertEquals(2, ProjectCatalog.list(root).entries.size)
            val saved = ProjectCatalog.read(root, next.projectId)
            assertEquals(listOf(primaryCue), saved.currentSegments)
            assertEquals(CaptionFont.SERIF, saved.captionAppearance.font)
            assertTrue(saved.timelineClips.isEmpty()); assertTrue(saved.drafts.isEmpty())
            assertNull(saved.music); assertEquals("", saved.currentTimelineId)
            assertEquals(0L, saved.startMs); assertEquals(30_000L, saved.endMs)
        } finally { root.deleteRecursively() }
    }
}
