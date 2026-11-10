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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SoundPreviewTest {
    @Test fun shortEffectStartsAudiblyAndPausesOnSeekAway() {
        var gain = 0f; var playing = false; var position = 0L; var prepared = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "setVolume" -> { gain = args!![0] as Float; null }
                "setPlayWhenReady" -> { playing = args!![0] as Boolean; null }
                "pause" -> { playing = false; null }
                "seekTo" -> { position = args!![0] as Long; null }
                "getCurrentPosition" -> position
                "prepare" -> { prepared++; null }
                else -> null
            }
        } as Player
        val preview = SoundPreview(player)
        val event = TimedSound("pop", 1000, 220, SoundKind.POP, .3f)
        val assets = mapOf(SoundKind.POP to "file:///test/pop.wav")
        preview.tick(listOf(event), 500, false, assets)
        assertEquals(1, prepared); assertFalse(playing)
        preview.tick(listOf(event), 1000, true, assets)
        assertTrue(playing); assertEquals(.3f, gain, 0f); assertEquals(0L, position)
        preview.tick(listOf(event), 1100, false, assets)
        assertFalse(playing); assertEquals(100L, position)
        preview.tick(listOf(event), 1400, true, assets)
        assertFalse(playing)
    }
}
