package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class ReferenceFeatureTest {
    private fun clip() = TimelineClip("a", 1000, 5000, sounds = listOf(ClipSound("pop", 2000, SoundKind.POP)))
    @Test fun hookGeneratorRequiresContextAndProducesThreeEditableOptions() {
        assertTrue(HookGenerator.suggestions("").isEmpty())
        val hooks = HookGenerator.suggestions("He brought a sword to court and surprised the judge.")
        assertEquals(3, hooks.size); assertTrue(hooks.first().contains("COURT"))
        assertTrue(hooks.all { it.length <= 65 })
    }
    @Test fun hookToneAndFilipinoContextChangeSuggestions() {
        val input = "Grabe yung nangyari bakit siya may espada"
        assertTrue(HookGenerator.suggestions(input).first().contains("😭"))
        assertNotEquals(HookGenerator.suggestions(input, HookTone.FUNNY), HookGenerator.suggestions(input, HookTone.STORY))
    }
    @Test fun hookHandlesUnicodeLongWordsAndControlCharacters() {
        for (tone in HookTone.entries) {
            val hooks = HookGenerator.suggestions("Hello\n" + "👋".repeat(150), tone)
            assertTrue(hooks.all { it.length <= 65 && !it.contains('\n') && !it.last().isHighSurrogate() })
        }
    }
    @Test fun soundAnchorsFollowSpeedButEffectPitchAndDurationStayOriginal() {
        val normal = SoundTimeline.events(listOf(clip())).single()
        val fast = SoundTimeline.events(listOf(clip().withSpeed(2f))).single()
        val slow = SoundTimeline.events(listOf(clip().withSpeed(.5f))).single()
        assertEquals(1000L, normal.startMs); assertEquals(500L, fast.startMs); assertEquals(2000L, slow.startMs)
        assertEquals(normal.durationMs, fast.durationMs); assertEquals(normal.durationMs, slow.durationMs)
    }
    @Test fun splitReorderAndUndoPreserveOneCopyOfEachSound() {
        val original = clip().copy(sounds = listOf(ClipSound("a", 1500, SoundKind.POP), ClipSound("b", 3000, SoundKind.IMPACT)))
        val split = TimelineModel.split(listOf(original), 2000, "right")
        assertEquals(listOf("a"), split[0].sounds.map { it.id }); assertEquals(listOf("b"), split[1].sounds.map { it.id })
        assertEquals(2000L, SoundTimeline.events(split)[1].startMs)
        val moved = TimelineModel.move(split, 1, -1)
        assertEquals(0L, SoundTimeline.events(moved).first().startMs)
        assertEquals(listOf(original), TimelineHistory(listOf(original)).edit(split).undo().clips)
    }
    @Test fun trimHidesOutsideSoundsWithoutDeletingTheirAnchors() {
        val trimmed = clip().trimmed(3000, 5000)
        assertEquals(1, trimmed.sounds.size); assertTrue(SoundTimeline.events(listOf(trimmed)).isEmpty())
        assertEquals(1, SoundTimeline.events(listOf(trimmed.trimmed(1000, 5000))).size)
    }
    @Test fun effectsAreBoundedAtNextCueAndEndOfClip() {
        val c = clip().copy(sounds = listOf(ClipSound("a", 2000, SoundKind.WHOOSH), ClipSound("b", 2200, SoundKind.POP), ClipSound("c", 4900, SoundKind.IMPACT)))
        val events = SoundTimeline.events(listOf(c))
        assertEquals(listOf(200L, 220L, 100L), events.map { it.durationMs })
        assertNull(SoundTimeline.active(events, 0)); assertEquals(events[0], SoundTimeline.active(events, 1000))
        assertEquals(events[1], SoundTimeline.active(events, 1200))
    }
    @Test fun invalidSoundCountsIdsTimingAndGainAreRejected() {
        for (bad in listOf(clip().copy(sounds = List(13) { ClipSound("x$it", 1500, SoundKind.POP) }),
            clip().copy(sounds = listOf(ClipSound("x", 6000, SoundKind.POP))),
            clip().copy(sounds = listOf(ClipSound("x", 1500, SoundKind.POP, Float.NaN))))) {
            try { bad.validate(5000); fail("Invalid SFX accepted") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun suggestedEffectsAreSparseAndStayInsideSelectedCut() {
        val c = clip().copy(subtitles = (0..9).map { WhisperNative.Segment(1000L + it * 300, 1200L + it * 300, "wow") })
        val sounds = SoundTimeline.suggest(c)
        assertTrue(sounds.size in 1..4)
        assertTrue(sounds.zipWithNext().all { (a,b) -> b.sourceMs - a.sourceMs >= 900 })
    }
    @Test fun originalWaveFilesHaveValidPcmHeadersAndBoundedAudibleSamples() {
        for (kind in SoundKind.entries) {
            val bytes = SoundAssets.wave(kind); val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4)); assertEquals(48000, b.getInt(24))
            assertEquals(1, b.getShort(22).toInt()); assertEquals(16, b.getShort(34).toInt())
            assertEquals(kind.durationMs * 96, (bytes.size - 44).toLong())
            var peak = 0
            for (i in 44 until bytes.size step 2) peak = maxOf(peak, kotlin.math.abs(b.getShort(i).toInt()))
            assertTrue(peak in 1000..15000); assertArrayEquals(bytes, SoundAssets.wave(kind))
        }
    }
    @Test fun assetPreparationReusesOriginalFiles() {
        val directory = Files.createTempDirectory("clipforge-sfx").toFile()
        try { assertEquals(SoundAssets.ensure(directory), SoundAssets.ensure(directory)); assertEquals(SoundKind.entries.size, directory.resolve("sound-effects").listFiles()!!.size) }
        finally { directory.deleteRecursively() }
    }
    @Test fun projectRestoresReferenceHookSoundsAndLegacyDefaults() {
        val directory = Files.createTempDirectory("clipforge-reference").toFile()
        try {
            val file = directory.resolve("project.properties")
            val project = EditorProject("content://videos/1", 5000, 0, 5000, timelineMode = true, timelineClips = listOf(clip().copy(hook = "My hook")),
                captionAppearance = CaptionAppearance(layout = ReferenceLayout.WHITE_CLASSIC))
            ProjectStore.write(file, project)
            assertEquals(project.reviewedAgain(), ProjectStore.read(file))
            file.writeText(file.readLines().filterNot { it.startsWith("caption.layout=") || it.contains(".sounds.") }.joinToString("\n"))
            val old = ProjectStore.read(file)!!
            assertEquals(ReferenceLayout.STANDARD, old.captionAppearance.layout); assertTrue(old.timelineClips.single().sounds.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
