package com.clipforge.mobile

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.math.abs

/** A separately synchronized effects player; original sound and music remain independent. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SoundPreview(private val player: Player) {
    private var loaded: String? = null
    private var mediaKey: Pair<String, Long>? = null
    fun tick(events: List<TimedSound>, positionMs: Long, playing: Boolean, assets: Map<SoundKind, String>) {
        val active = SoundTimeline.active(events, positionMs)
        val target = active ?: events.firstOrNull { it.startMs >= positionMs && it.startMs - positionMs <= 3000 }
        val uri = target?.let { assets[it.kind] }
        if (target == null || uri == null) { player.pause(); loaded = null; return }
        val key = uri to target.durationMs
        val changed = loaded != target.id
        if (mediaKey != key) {
            player.pause()
            player.setMediaItem(MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(target.durationMs).build()).build())
            player.prepare()
            mediaKey = key
        }
        if (changed) { player.seekTo(0); loaded = target.id }
        if (active == null) { player.pause(); return }
        val local = positionMs - active.startMs
        if (changed || abs(player.currentPosition - local) > 80) player.seekTo(local)
        // A 50 ms UI clock cannot apply a 20 ms attack envelope: it would mute
        // most of a short pop. The PCM asset already starts at zero amplitude.
        player.volume = active.volume
        player.playWhenReady = playing
    }
}
