package com.clipforge.mobile

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.io.ByteArrayOutputStream

class StudioUpgradeTest {
    @Test fun campaignPresetAndBackupKeepReusableSettingsWithoutSourceMedia() {
        val root = Files.createTempDirectory("studio-presets").toFile()
        val restored = Files.createTempDirectory("studio-restored").toFile()
        try {
            val original = EditorProject("content://private/video", 10000, 0, 10000,
                hook = "Private hook", caption = "Private speech", hookTone = HookTone.STORY,
                campaignBrief = CampaignBrief(true, "@show #episode", "@show #episode", "15", "30", "1"),
                audioIntensity = AudioIntensity.MINIMAL,
                timelineClips = listOf(TimelineClip("cut", 0, 10000, cleanupStrength = .6f)),
                captionAppearance = CaptionAppearance(wordHighlight = true))
            val saved = CampaignPresets.save(root, "My campaign", original)
            assertEquals(original.campaignBrief, saved.campaignBrief)
            assertEquals(HookTone.STORY, saved.hookTone)
            assertTrue(saved.captionAppearance.wordHighlight)
            assertTrue(saved.timelineClips.isEmpty())
            assertTrue(saved.hook.isEmpty())
            assertTrue(saved.caption.isEmpty())
            assertNull(saved.music)
            assertFalse(saved.source.contains("private"))
            val bytes = ByteArrayOutputStream()
            MetadataBackup.create(root, bytes)
            MetadataBackup.restore(restored, MetadataBackup.stage(restored, bytes.toByteArray().inputStream()))
            assertEquals(saved, CampaignPresets.list(restored).single())
            CampaignPresets.remove(restored, saved.projectId)
            assertTrue(CampaignPresets.list(restored).isEmpty())
        } finally { root.deleteRecursively(); restored.deleteRecursively() }
    }

    @Test fun cleanupSurvivesProjectSaveAndDisabledModePreservesFragmentedStereo() {
        val root = Files.createTempDirectory("cleanup-save").toFile()
        try {
            val clip = TimelineClip("cut", 0, 1000, cleanupStrength = .7f)
            val p = EditorProject("content://video",1000,0,1000,timelineClips=listOf(clip))
            val file = java.io.File(root,"project.properties")
            ProjectStore.write(file,p)
            assertEquals(.7f,ProjectStore.read(file)!!.timelineClips.single().cleanupStrength,0f)
            val processor = SpeechCleanupProcessor { 0f }
            processor.configure(AudioProcessor.AudioFormat(22050,2,C.ENCODING_PCM_16BIT)); processor.flush()
            val input = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
                .putShort(12000).putShort(-16000).putShort(4000).putShort(-8000).array()
            processor.queueInput(ByteBuffer.wrap(input.copyOfRange(0,3)))
            assertEquals(0,processor.output.remaining())
            processor.queueInput(ByteBuffer.wrap(input.copyOfRange(3,8)))
            val output = ByteArray(8); processor.output.get(output)
            assertArrayEquals(input,output)
            processor.queueEndOfStream()
        } finally { root.deleteRecursively() }
    }

    @Test fun cleanupRejectsNonfiniteSamplesAndAttenuatesSteadyRumble() {
        val filter = VoiceCleanupFilter(22050)
        assertTrue(filter.sample(Float.NaN,1f).isFinite())
        var tail = 1f
        repeat(22050) { tail = filter.sample(.4f,1f) }
        assertTrue(kotlin.math.abs(tail)<.001f)
        assertEquals(.2f, filter.sample(.2f,0f),0f)
    }

    @Test fun twentyOneOriginalChoicesHaveDistinctIdsAndWaveforms() {
        val root = Files.createTempDirectory("music-choices").toFile()
        try {
            val tracks = MusicMood.entries.flatMap { mood -> (0..2).map { MusicLibrary.ensure(root,mood,it) } }
            assertEquals(21,tracks.size)
            assertEquals(21,tracks.map { it.libraryId }.distinct().size)
            val hashes = tracks.map { track ->
                val file = java.io.File(java.net.URI(track.source))
                val bytes = file.readBytes()
                assertEquals("RIFF",String(bytes,0,4))
                assertTrue(bytes.size>44)
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toList()
            }
            assertEquals(21,hashes.distinct().size)
        } finally { root.deleteRecursively() }
    }
}
