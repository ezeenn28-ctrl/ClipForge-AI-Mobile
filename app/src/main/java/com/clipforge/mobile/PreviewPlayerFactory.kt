package com.clipforge.mobile

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Preview stays on the normal codec/surface path. Export owns the GL effect pipeline. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PreviewPlayerFactory {
    fun create(context: Context, preferSoftware: Boolean = false): ExoPlayer {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        if (preferSoftware) renderers.setMediaCodecSelector { mime, secure, tunneling ->
            MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).sortedBy { !it.softwareOnly }
        }
        return ExoPlayer.Builder(context).setRenderersFactory(renderers).setReleaseTimeoutMs(1000).build()
    }
}
