package com.clipforge.mobile

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewPlaybackTest {
    private class Fake {
        var count = 0; var index = 0; var position = 0L; var state = Player.STATE_IDLE
        var playing = false; var resets = 0; var prepares = 0
        var parameters = androidx.media3.common.PlaybackParameters.DEFAULT
        var listener: Player.Listener? = null
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> { listener = args!![0] as Player.Listener; null }
                "setPlaybackParameters" -> { parameters = args!![0] as androidx.media3.common.PlaybackParameters; null }
                "getMediaItemCount" -> count
                "getCurrentMediaItemIndex" -> index
                "getCurrentPosition" -> position
                "getPlaybackState" -> state
                "getPlayerError" -> null
                "getPlayWhenReady", "isPlaying" -> playing
                "pause" -> { playing = false; null }
                "play" -> { playing = true; null }
                "prepare" -> { prepares++; state = Player.STATE_READY; null }
                "setPlayWhenReady" -> { playing = args!![0] as Boolean; null }
                "setMediaItems" -> { count = (args!![0] as List<*>).size; index = args[1] as Int; position = args[2] as Long; resets++; null }
                "seekTo" -> { if (args!!.size == 2) { index = args[0] as Int; position = args[1] as Long } else position = args[0] as Long; null }
                else -> when (method.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; else -> null }
            }
        } as Player
    }
    @Test fun recompositionDoesNotResetImportedVideoOrCancelHighlightPlay() {
        val fake = Fake(); val preview = PreviewPlayback(fake.player)
        val source = listOf(PreviewPlayback.Source("content://media/primary"))
        preview.load(source, play = true)
        assertTrue(fake.playing)
        assertFalse(preview.load(source))
        assertTrue(fake.playing)
        fake.player.seekTo(30_000); preview.play(30_000, 50_000)
        assertFalse(preview.load(source))
        assertEquals(30_000L, fake.position)
        assertTrue(fake.playing); assertEquals(1, fake.resets)
    }
    @Test fun switchingFromTimelineToHighlightSurvivesLateReconciliation() {
        val fake = Fake(); val preview = PreviewPlayback(fake.player)
        preview.load(listOf(PreviewPlayback.Source("content://media/primary", 60_000, 80_000)))
        val source = listOf(PreviewPlayback.Source("content://media/primary"))
        preview.load(source, positionMs = 20_000)
        preview.play(20_000, 40_000)
        preview.load(source)
        assertTrue(fake.playing); assertEquals(20_000L, fake.position); assertEquals(2, fake.resets)
    }
    @Test fun playRestartsEndedSourceAndTrimBoundary() {
        val fake = Fake(); val preview = PreviewPlayback(fake.player)
        preview.load(listOf(PreviewPlayback.Source("content://media/primary")))
        fake.position = 90_000; fake.state = Player.STATE_ENDED
        preview.play(20_000, 40_000)
        assertEquals(20_000L, fake.position); assertTrue(fake.playing)
        fake.position = 40_000; fake.state = Player.STATE_READY
        preview.play(20_000, 40_000)
        assertEquals(20_000L, fake.position)
    }
    @Test fun sequenceEndAndExplicitRestartReturnToFirstCut() {
        val fake = Fake(); val preview = PreviewPlayback(fake.player)
        preview.load(listOf(PreviewPlayback.Source("content://media/first", 1000, 3000), PreviewPlayback.Source("content://media/second", 10_000, 15_000)))
        fake.index = 1; fake.position = 5000
        preview.play()
        assertEquals(0, fake.index); assertEquals(0L, fake.position)
        fake.index = 1; fake.position = 1000
        preview.play(restart = true)
        assertEquals(0, fake.index); assertEquals(0L, fake.position)
    }
    @Test fun speedOnlyChangesKeepDecoderPositionAndPlayRequestAndTransitionsSetPitch() {
        val fake = Fake(); val preview = PreviewPlayback(fake.player)
        val sources = listOf(PreviewPlayback.Source("content://one", 1000, 5000, 2f),
            PreviewPlayback.Source("content://two", 2000, 4000, .5f))
        preview.load(sources, play = true)
        assertEquals(2f, fake.parameters.speed, 0f)
        fake.position = 1200
        assertFalse(preview.load(sources.mapIndexed { i, s -> if (i == 0) s.copy(speed = 1.5f) else s }))
        assertEquals(1, fake.resets); assertEquals(1200L, fake.position); assertTrue(fake.playing)
        assertEquals(1.5f, fake.parameters.speed, 0f); assertEquals(1f, fake.parameters.pitch, 0f)
        fake.index = 1
        fake.listener!!.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertEquals(.5f, fake.parameters.speed, 0f); assertEquals(1f, fake.parameters.pitch, 0f)
    }
    @Test fun recoveryReloadsCurrentRangeWithoutExportingOrAutoPlaying() {
        val fake = Fake(); val preview = PreviewPlayback(fake.player)
        preview.load(listOf(PreviewPlayback.Source("content://media/primary", 30_000, 50_000)), play = true)
        fake.position = 5000
        preview.recover()
        assertEquals(2, fake.resets); assertEquals(5000L, fake.position); assertFalse(fake.playing)
        fake.state = Player.STATE_IDLE
        preview.play()
        assertEquals(3, fake.prepares); assertTrue(fake.playing)
    }
}

