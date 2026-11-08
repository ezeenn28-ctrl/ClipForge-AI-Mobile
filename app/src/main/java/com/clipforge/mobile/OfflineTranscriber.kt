package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import java.io.File

object OfflineTranscriber {
    /** Only decode the requested <=30 seconds; never create a full-video PCM file. */
    fun range(context: Context, source: Uri, model: File, startMs: Long, endMs: Long, progress: (String, Int) -> Unit = { _, _ -> }): List<WhisperNative.Segment> {
        require(endMs > startMs && endMs - startMs <= 30_000) { "Select up to 30 seconds" }
        ensureAudioWorkspace(context.cacheDir, endMs - startMs)
        val wav = File.createTempFile("whisper_chunk_", ".wav", context.cacheDir)
        try {
            progress("Decoding requested audio window", -1)
            OfflineAudioPreparer.extractWav(context, source, wav, startMs, endMs)
            progress("Loading model / recognizing speech", -1)
            val segments = WhisperNative.transcribeSegments(model, wav, 0, endMs - startMs,
                WhisperNative.ProgressListener { progress("Recognizing speech", it.coerceIn(0, 99)) }).map {
                it.copy(startMs = it.startMs + startMs, endMs = it.endMs + startMs)
            }
            progress("Audio window complete", 100)
            return segments
        } finally { wav.delete() }
    }
}
