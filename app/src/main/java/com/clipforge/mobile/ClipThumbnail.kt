package com.clipforge.mobile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private object ThumbnailFrames {
    val decodeLock = Mutex()
    val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
}

/** A bounded local source-frame preview; never uploads media or changes the clip. */
@Composable
fun rememberClipThumbnail(context: Context, source: Uri?, startMs: Long, enabled: Boolean): Bitmap? {
    val key = "$source:$startMs"
    var thumbnail by remember(key) { mutableStateOf(ThumbnailFrames.cache.get(key)) }
    LaunchedEffect(key, enabled) {
        if (source == null || !enabled || thumbnail != null) return@LaunchedEffect
        thumbnail = withContext(Dispatchers.IO) {
            // Serialize decoding so a 15-clip shortlist cannot start 15 codecs at once.
            ThumbnailFrames.decodeLock.withLock {
                ThumbnailFrames.cache.get(key) ?: runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context.applicationContext, source)
                        val frame = if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 180)
                        } else {
                            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
                            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
                            // API 26 lacks scaled decoding; skip large frames rather than risk memory pressure.
                            if (width <= 0 || height <= 0 || width * height > 1920L * 1080) null
                            else retriever.getFrameAtTime(startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                                val scale = minOf(160.0 / raw.width, 180.0 / raw.height, 1.0)
                                val small = Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true)
                                if (small !== raw) raw.recycle()
                                small
                            }
                        }
                        frame?.also { ThumbnailFrames.cache.put(key, it) }
                    } finally { retriever.release() }
                }.getOrNull()
            }
        }
    }
    return thumbnail
}
