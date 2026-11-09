package com.clipforge.mobile

import android.app.Activity
import android.os.Bundle
import androidx.media3.ui.PlayerView

/** Internal instrumentation host; not exported and has no launcher entry. */
class MediaHarnessActivity : Activity() {
    lateinit var preview: PlayerView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preview = layoutInflater.inflate(R.layout.video_preview, null) as PlayerView
        setContentView(preview)
    }
}
