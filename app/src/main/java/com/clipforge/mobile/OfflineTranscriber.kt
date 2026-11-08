package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import java.io.File

object OfflineTranscriber {
    /** Only decode the requested <=30 seconds; never create a full-video PCM file. */
    fun range(context: Context, source: Uri, model: File, startMs: Long, endMs: Long): List<WhisperNative.Segment> {
        require(endMs > startMs && endMs - startMs <= 30_000) { "Select up to 30 seconds" }
        ensureAudioWorkspace(context.cacheDir, endMs - startMs)
        val wav = File.createTempFile("whisper_chunk_", ".wav", context.cacheDir)
        try {
            OfflineAudioPreparer.extractWav(context, source, wav, startMs, endMs)
            return WhisperNative.transcribeSegments(model, wav, 0, endMs - startMs).map {
                it.copy(startMs = it.startMs + startMs, endMs = it.endMs + startMs)
            }
        } finally { wav.delete() }
    }
}
