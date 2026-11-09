package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ClipVisualsTest {
    @Test fun linearMotionInterpolatesAndClampsAtTheClipEnds() {
        val visual = ClipVisuals(end = MotionPoint(1.4f, .2f, -.2f), ease = false)
        assertEquals(MotionPoint(), visual.point(-100, 10_000))
        val middle = visual.point(5000, 10_000)
        assertEquals(1.2f, middle.scale, .0001f); assertEquals(.1f, middle.x, .0001f); assertEquals(-.1f, middle.y, .0001f)
        assertEquals(visual.end, visual.point(20_000, 10_000))
    }
    @Test fun easedMotionSlowsAtEndpointsAndSplitKeepsTheCutPosition() {
        val visual = ClipVisuals(end = MotionPoint(1.5f, .2f), fadeInMs = 400, fadeOutMs = 400)
        assertTrue(visual.point(1000, 10_000).scale < 1.05f)
        val clips = TimelineModel.split(listOf(TimelineClip("a", 10_000, 20_000, visuals = visual)), 4000, "b")
        assertEquals(visual.point(4000, 10_000), clips[0].visuals.end)
        assertEquals(clips[0].visuals.end, clips[1].visuals.start)
        assertEquals(0L, clips[0].visuals.fadeOutMs); assertEquals(0L, clips[1].visuals.fadeInMs)
    }
    @Test fun shortClipFadesAreBoundedAndDoNotChangeDuration() {
        val visual = ClipVisuals(fadeInMs = 400, fadeOutMs = 400)
        assertEquals(0f, visual.opacity(0, 100), .0001f)
        assertEquals(.5f, visual.opacity(25, 100), .0001f)
        assertEquals(1f, visual.opacity(50, 100), .0001f)
        assertEquals(.5f, visual.opacity(75, 100), .0001f)
        assertEquals(0f, visual.opacity(100, 100), .0001f)
    }
    @Test fun rejectsInvalidMotionAndRestoresVisualAndCaptionSettings() {
        try { MotionPoint(Float.NaN).validate(); fail("NaN accepted") } catch (_: IllegalArgumentException) {}
        try { MotionPoint(x = 2f).validate(); fail("Out-of-canvas motion accepted") } catch (_: IllegalArgumentException) {}
        val directory = Files.createTempDirectory("clipforge-visuals").toFile()
        try {
            val clip = TimelineClip("a", 0, 10_000, visuals = ClipVisuals(end = MotionPoint(1.3f), fadeOutMs = 400))
            val project = EditorProject("content://media/1", 20_000, 0, 10_000, timelineClips = listOf(clip), captionPreset = "CARD")
            val file = File(directory, "project.properties")
            ProjectStore.write(file, project)
            assertEquals(project, ProjectStore.read(file))
            file.writeText(file.readText().lineSequence().filterNot { it.startsWith("captionPreset=") || it.contains(".visual.") }.joinToString("\n"))
            val older = ProjectStore.read(file)!!
            assertEquals("BOLD", older.captionPreset); assertEquals(ClipVisuals(), older.timelineClips.single().visuals)
        } finally { directory.deleteRecursively() }
    }
}
