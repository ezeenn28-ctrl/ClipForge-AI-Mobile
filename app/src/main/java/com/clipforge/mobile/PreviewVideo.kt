package com.clipforge.mobile

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/** Transfer the surface before releasing the old view, including fullscreen/tab changes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PreviewSurfaceBinding(private val player: Player) {
    private var active: PlayerView? = null
    fun attach(view: PlayerView) {
        if (active === view && view.player === player) return
        PlayerView.switchTargetView(player, active, view)
        active = view
    }
    fun release(view: PlayerView) {
        if (active === view) { view.player = null; active = null }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PreviewVideo(binding: PreviewSurfaceBinding, modifier: Modifier = Modifier) {
    AndroidView(factory = { context ->
        (LayoutInflater.from(context).inflate(R.layout.video_preview, null, false) as PlayerView).also(binding::attach)
    }, modifier = modifier, update = binding::attach, onReset = null, onRelease = binding::release)
}
