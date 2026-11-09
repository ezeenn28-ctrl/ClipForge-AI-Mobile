package com.clipforge.mobile

import android.view.LayoutInflater
import android.view.TextureView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PreviewSurfaceTest {
    @Test fun staleViewReleaseCannotDetachNewPreviewSurface() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = ExoPlayer.Builder(context).build()
        try {
            val binding = PreviewSurfaceBinding(player)
            val first = LayoutInflater.from(context).inflate(R.layout.video_preview, null) as PlayerView
            val second = LayoutInflater.from(context).inflate(R.layout.video_preview, null) as PlayerView
            assertTrue(first.videoSurfaceView is TextureView)
            binding.attach(first); binding.attach(second); binding.release(first)
            assertNull(first.player); assertSame(player, second.player)
            binding.attach(second); assertSame(player, second.player)
            binding.release(second); assertNull(second.player)
        } finally { player.release() }
    }
}
