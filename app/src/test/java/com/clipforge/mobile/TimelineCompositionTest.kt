package com.clipforge.mobile

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TimelineCompositionTest {
    @Test fun exportUsesEachSourceAndAddsClippedMusicAfterAnAudioGap() {
        val music = MusicTrack("content://audio/1", "Music", 60_000, 10_000, 40_000, 5000)
        val composition = TimelineExport.composition(Uri.parse("content://videos/1"), listOf(
            TimelineClip("a", 1000, 5000), TimelineClip("b", 20_000, 40_000, source = "content://videos/2", sourceDurationMs = 60_000)),
            true, false, false, 39f, -1, music)
        assertEquals(2, composition.sequences.size)
        assertEquals(Uri.parse("content://videos/1"), composition.sequences[0].editedMediaItems[0].mediaItem.localConfiguration!!.uri)
        assertEquals(Uri.parse("content://videos/2"), composition.sequences[0].editedMediaItems[1].mediaItem.localConfiguration!!.uri)
        val audio = composition.sequences[1].editedMediaItems
        assertEquals(2, audio.size)
        assertEquals(5_000_000L, audio[0].durationUs)
        assertTrue(audio[1].removeVideo)
        assertEquals(29_000L, audio[1].mediaItem.clippingConfiguration.endPositionMs)
        assertTrue(composition.forceAudioTrack)
    }
}
