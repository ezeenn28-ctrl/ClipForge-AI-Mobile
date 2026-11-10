package com.clipforge.mobile

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters

/** One owner for playlist changes. Recomposition must not cancel a user's Play request. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PreviewPlayback(private val player: Player) {
    data class Source(val uri: String, val startMs: Long = 0, val endMs: Long? = null, val speed: Float = 1f) {
        fun mediaItem(): MediaItem = MediaItem.Builder().setUri(Uri.parse(uri)).apply {
            if (endMs != null) setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs).setEndPositionMs(endMs).build())
        }.build()
    }
    private var loaded: List<Source> = emptyList()
    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { applySpeed() }
        })
    }
    private fun applySpeed() {
        player.playbackParameters = PlaybackParameters(loaded.getOrNull(player.currentMediaItemIndex)?.speed ?: 1f, 1f)
    }
    fun load(sources: List<Source>, index: Int = 0, positionMs: Long = 0, play: Boolean = false): Boolean {
        require(sources.isNotEmpty())
        if (loaded == sources && player.mediaItemCount == sources.size) return false
        if (loaded.map { it.copy(speed = 1f) } == sources.map { it.copy(speed = 1f) } && player.mediaItemCount == sources.size) {
            loaded = sources.toList()
            applySpeed()
            return false
        }
        player.pause()
        loaded = sources.toList()
        player.setMediaItems(sources.map { it.mediaItem() }, index.coerceIn(sources.indices), positionMs.coerceAtLeast(0))
        applySpeed()
        player.prepare()
        player.playWhenReady = play
        return true
    }
    fun play(trimStartMs: Long = 0, trimEndMs: Long? = null, restart: Boolean = false) {
        if (player.mediaItemCount == 0) return
        val clipped = loaded.firstOrNull()?.endMs != null
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) player.prepare()
        if (clipped) {
            val current = loaded.getOrNull(player.currentMediaItemIndex)
            val atEnd = player.currentMediaItemIndex == loaded.lastIndex && current?.endMs != null && player.currentPosition >= current.endMs - current.startMs
            if (restart || atEnd || player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0)
        } else if (restart || player.playbackState == Player.STATE_ENDED || player.currentPosition < trimStartMs ||
            (trimEndMs != null && player.currentPosition >= trimEndMs)) player.seekTo(trimStartMs)
        applySpeed()
        player.play()
    }
    fun recover() {
        if (loaded.isEmpty()) return
        val sources = loaded
        val index = player.currentMediaItemIndex.coerceIn(sources.indices)
        val position = player.currentPosition.coerceAtLeast(0)
        loaded = emptyList()
        load(sources, index, position)
    }
}

