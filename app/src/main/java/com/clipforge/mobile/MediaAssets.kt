package com.clipforge.mobile

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

object MediaAssets {
    data class Info(val uri: String, val name: String, val durationMs: Long)
    fun inspect(context: Context, uri: Uri, video: Boolean): Info {
        require(uri.scheme in listOf("file", "content")) { "Choose a local media file" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(duration in 1..86_400_000) { "Media must have a valid duration up to 24 hours" }
            val key = if (video) MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO else MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
            require(retriever.extractMetadata(key) == "yes") { if (video) "File has no video track" else "File has no audio track" }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment ?: "Local media" }.replace('\n', ' ').take(100)
            return Info(uri.toString(), name, duration)
        } finally { retriever.release() }
    }
    fun checkTimeline(context: Context, primary: String, durationMs: Long, clips: List<TimelineClip>, music: MusicTrack?) {
        clips.distinctBy { it.resolvedSource(primary) }.forEach { clip ->
            val info = inspect(context, Uri.parse(clip.resolvedSource(primary)), true)
            require(clips.filter { it.resolvedSource(primary) == info.uri }.all { it.endMs <= info.durationMs }) { "Trim extends beyond source: ${info.name}" }
            require(kotlin.math.abs(info.durationMs - clip.resolvedDuration(durationMs)) <= 1000) { "Source changed: ${info.name}. Relink the original video." }
        }
        clips.flatMap { it.sounds }.mapNotNull { it.asset }.distinctBy { it.id }.forEach { asset ->
            asset.validate()
            val info = inspect(context, Uri.parse(asset.source), false)
            require(kotlin.math.abs(info.durationMs - asset.durationMs) <= 100) { "Sound file changed: ${asset.name}. Import it again." }
        }
        music?.let {
            val info = inspect(context, Uri.parse(it.source), false)
            require(kotlin.math.abs(info.durationMs - it.durationMs) <= 1000) { "Music changed. Replace the music file." }
        }
    }
}

