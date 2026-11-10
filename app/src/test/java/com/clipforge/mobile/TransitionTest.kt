package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TransitionTest {
    private val dip = CutTransition(CutTransitionKind.DIP_BLACK, 400)
    private val zoom = CutTransition(CutTransitionKind.ZOOM_CUT, 400)
    @Test fun dipIsContinuousAcrossTheCutWithoutChangingTimelineOrCaptions() {
        val a = TimelineClip("a", 1000, 3000, speed = 2f, transition = dip)
        val b = TimelineClip("b", 3000, 3500, speed = .5f)
        val clips = listOf(a, b)
        assertEquals(2000L, TimelineModel.duration(clips))
        assertEquals(1f, TimelineTransitions.frame(clips, 0, 0).opacity, 0f)
        assertEquals(.5f, TimelineTransitions.frame(clips, 0, 900).opacity, .0001f)
        assertEquals(0f, TimelineTransitions.frame(clips, 0, 1000).opacity, 0f)
        assertEquals(0f, TimelineTransitions.frame(clips, 1, 0).opacity, 0f)
        assertEquals(.5f, TimelineTransitions.frame(clips, 1, 100).opacity, .0001f)
        assertEquals(1f, TimelineTransitions.frame(clips, 1, 1000).opacity, 0f)
        assertEquals(TimelineModel.Position(1, 0, 3000), TimelineModel.position(clips, 1000))
    }
    @Test fun zoomDoesNotFadeVideoAndShortCutsBoundBothEdges() {
        val clip = TimelineClip("a", 0, 100)
        val center = TimelineTransitions.frame(clip, dip, dip, 50)
        assertEquals(1f, center.opacity, 0f)
        assertEquals(1.12f, TimelineTransitions.frame(clip, zoom, null, 0).scale, .0001f)
        assertEquals(1f, TimelineTransitions.frame(clip, zoom, zoom, 50).scale, .0001f)
        assertEquals(1f, TimelineTransitions.frame(clip, zoom, zoom, 0).opacity, 0f)
        assertEquals(TimelineTransitions.Frame(), TimelineTransitions.frame(listOf(clip.copy(transition = dip)), 0, 100))
    }
    @Test fun splitKeepsTransitionOnlyAtTheOriginalOutgoingBoundary() {
        val clip = TimelineClip("a", 0, 2000, transition = dip)
        val split = TimelineModel.split(listOf(clip), 1000, "right")
        assertEquals(CutTransition(), split.first().transition)
        assertEquals(dip, split.last().transition)
        assertEquals(listOf(clip), TimelineHistory(listOf(clip)).edit(split).undo().clips)
    }
    @Test fun projectRoundTripAndLegacyTransitionDefaults() {
        val root = Files.createTempDirectory("transition").toFile()
        try {
            val file = File(root, "p.properties")
            val clip = TimelineClip("a", 0, 2000, transition = zoom)
            ProjectStore.write(file, EditorProject("content://videos/1", 3000, 0, 3000, timelineClips = listOf(clip)))
            assertEquals(zoom, ProjectStore.read(file)!!.timelineClips.single().transition)
            file.writeText(file.readText().lineSequence().filterNot { it.startsWith("timeline.0.transition.") }.joinToString("\n"))
            assertEquals(CutTransition(), ProjectStore.read(file)!!.timelineClips.single().transition)
        } finally { root.deleteRecursively() }
    }
}
